plugins {
    id("kmplib.module.compose")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            api(project(":kmplib-ui"))
            // O seletor de local começa na posição atual.
            api(project(":kmplib-location"))
            api(libs.ktor.client.core)
            api(libs.kotlinx.serialization.json)
            // Reconferir a permissão de localização ao voltar ao app (ponto azul do NativeMap).
            implementation(libs.androidx.lifecycle.runtime.compose)
        }

        androidMain.dependencies {
            // SDK oficial do Google Maps — nunca WebView, que é o padrão-ouro da casa para mapa.
            implementation(libs.maps.compose)
            implementation(libs.play.services.maps)
            // ClusterManager oficial para o agrupamento do NativeMap (android-maps-utils).
            implementation(libs.maps.compose.utils)
        }
    }
}
