plugins {
    id("kmplib.module.compose")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            api(project(":kmplib-ui"))
            // Quem é premium não vê anúncio — a checagem é a do módulo de compras.
            api(project(":kmplib-monetization"))
            api(project(":kmplib-platform"))

            api(libs.ktor.client.core)
            api(libs.kotlinx.serialization.json)
            // Imagem remota do criativo.
            api(libs.coil.compose)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
    }
}
