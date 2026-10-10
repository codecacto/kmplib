plugins {
    id("kmplib.module.compose")
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

// Banco local do offline-first. Schema agnóstico de domínio (synced_entity + sync_cursor) —
// nenhuma tabela de produto entra aqui.
sqldelight {
    databases {
        create("SyncDatabase") {
            packageName.set("br.com.codecacto.kmplib.sync.db")
            // SQLite 3.38 habilita o UPSERT (ON CONFLICT DO UPDATE) que o espelho usa.
            dialect(libs.sqldelight.dialect.sqlite)
        }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            api(project(":kmplib-ui"))
            // SEM kmplib-firebase (2.260.0): a fila REST só usa os modelos neutros de upload
            // (UploadItem/UploadStatus/UploadRequest), que moram no kmplib-core. Depender do módulo
            // Firebase levava firebase-analytics a todo app com banco local.
            // SEM kmplib-monetization (2.267.0): o sync nunca usou símbolo do módulo de compra — a cota
            // (402) chega como `DomainResult.Quota(QuotaExceeded)`, que mora no kmplib-core. A aresta
            // antiga só arrastava RevenueCat e a permissão BILLING para todo app com banco local, e
            // impedia o app que NÃO PODE vender (ex.: o app do aluno do App do Personal, Apple 3.1.3)
            // de usar a outbox. Quem usa paywall/compra declara `kmplib-monetization` direto.

            api(libs.ktor.client.core)
            api(libs.kotlinx.serialization.json)
            // SqlDriver e SyncDatabase aparecem em createSyncDatabase/SqlDelightSyncStore.
            api(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }

        commonTest.dependencies {
            // Só no teste: trava o registro do LocalRepository num grafo core+sync (sem firebase),
            // e as duas armadilhas do Koin que o KDoc descreve (LocalRepositoryKoinTest).
            implementation(libs.koin.core)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }

        androidMain.dependencies {
            implementation(libs.sqldelight.driver.android)
            // DirectUploadOutbox (2.287.0): o Worker que drena a fila com o app fechado. `implementation`:
            // nenhum tipo do WorkManager aparece na API pública.
            implementation(libs.androidx.work.runtime)
        }

        iosMain.dependencies {
            implementation(libs.sqldelight.driver.native)
        }
    }
}
