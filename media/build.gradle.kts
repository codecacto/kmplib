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
            // `rememberAmbientSoundPlayer`/`AmbientSoundBackgroundPause` (2.228.0): pausar o
            // ambiente no ON_STOP — o ciclo de vida multiplataforma oficial do Compose.
            implementation(libs.androidx.lifecycle.runtime.compose)
        }

        androidMain.dependencies {
            // `AmbientSoundPlayer` (2.228.0): Media3/ExoPlayer, o player de áudio recomendado pelo
            // Android — laço sem emenda (`REPEAT_MODE_ONE`) e leitura da memória
            // (`ByteArrayDataSource`, artefato `media3-datasource`, declarado porque o código o nomeia).
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.datasource)
        }
    }
}
