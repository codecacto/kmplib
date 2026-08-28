plugins {
    id("kmplib.module.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            api(project(":kmplib-ui"))
            // Gravação e reprodução pedem permissão de microfone; TTS vive no platform.
            api(project(":kmplib-platform"))
        }
    }
}
