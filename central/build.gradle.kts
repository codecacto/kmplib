plugins {
    id("kmplib.module.compose")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            api(project(":kmplib-ui"))
            // O diálogo de avaliação chama a loja pelo abridor de URL do platform.
            api(project(":kmplib-platform"))

            api(libs.ktor.client.core)
            api(libs.kotlinx.serialization.json)
        }

        androidMain.dependencies {
            // ProcessLifecycleOwner: o primeiro plano do PROCESSO, para o sinal de presença (2.326.0).
            // Versão = a que já resolvia transitivamente no releaseRuntimeClasspath.
            implementation(libs.androidx.lifecycle.process)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
    }
}
