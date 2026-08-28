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
            // A fila de upload sobe para o Firebase Storage.
            api(project(":kmplib-firebase"))
            // O banner de sincronização respeita a cota do plano.
            api(project(":kmplib-monetization"))

            api(libs.ktor.client.core)
            api(libs.kotlinx.serialization.json)
            // SqlDriver e SyncDatabase aparecem em createSyncDatabase/SqlDelightSyncStore.
            api(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }

        commonTest.dependencies {
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
