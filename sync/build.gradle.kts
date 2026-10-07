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
            // O banner de sincronização respeita a cota do plano.
            api(project(":kmplib-monetization"))

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
        }

        iosMain.dependencies {
            implementation(libs.sqldelight.driver.native)
        }
    }
}
