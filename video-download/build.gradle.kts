plugins {
    id("kmplib.module")
    // `MediaDownloadRecord` é persistido como JSON numa chave de preferências — o que o
    // subsistema nativo NÃO guarda (a que curso a aula pertence, até quando o direito vale).
    alias(libs.plugins.kotlinSerialization)
}

// =================================================================================================
// `kmplib-video-download` (2.212.0) — BAIXAR vídeo para ver sem internet. OPT-IN.
// =================================================================================================
//
// Saiu do `kmplib-video` porque traz um foreground service `dataSync` no manifesto (o
// `KmplibDownloadService` do Media3): com ele no player, todo app que só toca vídeo herdava o
// serviço e as permissões `FOREGROUND_SERVICE`/`FOREGROUND_SERVICE_DATA_SYNC`, e a Play barrava o
// bundle no formulário de declaração — sem resposta verdadeira, porque o app não baixa nada.
//
// Só o app que BAIXA declara este módulo. O umbrella (`br.com.codecacto:kmplib`) NÃO o inclui, de
// propósito: se incluísse, o split não resolveria nada para quem usa o umbrella.
//
// Os pacotes não mudaram (`br.com.codecacto.kmplib.video.download`): o app que já importava
// `MediaDownloadManager` só acrescenta a dependência.

kotlin {
    sourceSets {
        // Usa o cache do player (`Media3Cache`) e o `Context` registrado por `initKmpLibVideo` —
        // públicos só para esta ponte. Ver `KmpLibVideoInternalApi`.
        all { languageSettings.optIn("br.com.codecacto.kmplib.video.KmpLibVideoInternalApi") }

        commonMain.dependencies {
            // `VideoMedia` (o que `offlineMediaFor` devolve), `VideoStreamKind`, `VideoErrorKind`
            // são tipos da API pública deste módulo → api().
            api(project(":kmplib-video"))
            api(project(":kmplib-core"))
            implementation(libs.kotlinx.serialization.json)
        }

        androidMain.dependencies {
            // `DownloadManager`, `DownloadService`, `DownloadHelper` e `PlatformScheduler` vêm do
            // `media3-exoplayer`; o código os nomeia, então a dependência é declarada aqui.
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.exoplayer.hls)
            implementation(libs.androidx.media3.datasource)
            implementation(libs.androidx.media3.database)
        }
    }
}
