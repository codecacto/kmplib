plugins {
    id("kmplib.module.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            // O encoder é Kotlin puro; o `QrCodeView` que o desenha é que precisa do Compose.
            api(project(":kmplib-ui"))
        }
    }
}
