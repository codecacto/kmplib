plugins {
    id("kmplib.module.compose")
    // `MediaDownloadRecord` é persistido como JSON numa chave de preferências — o que o
    // subsistema nativo NÃO guarda (a que curso a aula pertence, até quando o direito vale).
    alias(libs.plugins.kotlinSerialization)
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
            // A CAPA do vídeo de feed (2.196.0): a imagem remota que fica por cima da superfície
            // até o primeiro quadro — é o que evita a tela preta. Mesmo carregador do `kmplib-ui`,
            // então o cache de imagem é o mesmo do resto do app.
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
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
            // Vídeo de FEED (2.196.0): `PlayerSurface` em `TextureView` + `rememberPresentationState`
            // (a capa sai no primeiro quadro) + `resizeWithContentScale` (corte/encaixe). É a
            // integração Compose oficial da Media3 — ver `FeedVideoSurface.android.kt`.
            implementation(libs.androidx.media3.ui.compose)

            // ------------------------------------------------------------------------------
            // Download offline (2.191.0)
            // ------------------------------------------------------------------------------
            //
            // `DownloadManager`, `DownloadService`, `DownloadHelper` e `PlatformScheduler` já vêm
            // do `media3-exoplayer` acima. Os dois abaixo chegariam por transitividade, e são
            // declarados assim mesmo porque o CÓDIGO NOMEIA os tipos deles (`SimpleCache`,
            // `CacheDataSource`, `StandaloneDatabaseProvider`): depender de transitividade para um
            // tipo que se escreve é o que quebra no dia em que a Media3 reorganizar os artefatos.
            implementation(libs.androidx.media3.datasource)
            implementation(libs.androidx.media3.database)
        }
    }
}
