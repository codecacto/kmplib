plugins {
    id("kmplib.module")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            // Gera o arquivo e o entrega ao compartilhamento do sistema.
            api(project(":kmplib-platform"))
            api(libs.kotlinx.serialization.json)
        }
    }
}
