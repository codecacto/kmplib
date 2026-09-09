plugins {
    id("kmplib.module.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            // `KeepScreenOn` — a tela não pode apagar no meio de uma aula. Já existe no platform;
            // reimplementá-la aqui seria um `expect/actual` duplicado de janela.
            api(project(":kmplib-platform"))
            // Baixa e interpreta a legenda EXTERNA (arquivo .vtt/.srt). A embutida no HLS é
            // resolvida pela plataforma; ver `VideoSubtitleTrack`.
            api(libs.ktor.client.core)
            // LifecycleEventEffect — pausar ao ir para o segundo plano, liberar ao sair da tela.
            implementation(libs.androidx.lifecycle.runtime.compose)
        }

        androidMain.dependencies {
            // ==============================================================================
            // Media3/ExoPlayer — o padrão-ouro do Android para reprodução de vídeo
            // ==============================================================================
            //
            // Não é escolha de conveniência: o `android.widget.VideoView` + `MediaPlayer` (que é o
            // que o `KmplibVideoActivity` do `kmplib-ui` usa para o caso "abrir um mp4 solto") não
            // toca HLS de forma confiável, não expõe velocidade de reprodução, não deixa escolher
            // faixa de legenda e não tem `MediaSession`. Media3 é o que a documentação do Android
            // indica para player de aplicativo desde que o ExoPlayer foi absorvido pelo Jetpack.
            implementation(libs.androidx.media3.exoplayer)
            // Extrator de HLS — artefato SEPARADO. Sem ele um `.m3u8` falha em runtime com
            // `UnrecognizedInputFormatException`, e o build fica verde.
            implementation(libs.androidx.media3.exoplayer.hls)
            // `PlayerView` com `useController = false`: a superfície, a razão de aspecto e o
            // `SubtitleView` (legenda EMBUTIDA) são dele; os controles são nossos, em Compose.
            implementation(libs.androidx.media3.ui)
            // `MediaSession` — metadados e comandos de transporte (fone, Bluetooth, tela de
            // bloqueio). Ver o KDoc de `VideoPlayerConfig.mediaSession`.
            implementation(libs.androidx.media3.session)
        }
    }
}
