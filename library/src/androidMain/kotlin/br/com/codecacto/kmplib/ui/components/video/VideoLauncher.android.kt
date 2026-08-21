package br.com.codecacto.kmplib.ui.components.video

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Impl Android: abre a [KmplibVideoActivity], declarada no manifest da própria lib. */
actual class VideoLauncher(private val context: Context) {

    actual fun play(source: VideoSource) {
        val intent = when (source) {
            is VideoSource.YouTube -> Intent(context, KmplibVideoActivity::class.java)
                .putExtra(KmplibVideoActivity.EXTRA_YOUTUBE_ID, source.videoId)

            is VideoSource.File -> Intent(context, KmplibVideoActivity::class.java)
                .putExtra(KmplibVideoActivity.EXTRA_FILE_URL, source.url)

            // Quem chama decide (o normal é abrir no navegador) — ver `videoSourceOf`.
            is VideoSource.External -> return
        }
        // `NEW_TASK` porque o `Context` pode não ser de Activity (aplicação, serviço). Sem ele o
        // Android recusa o start com uma exceção que só aparece em produção.
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
actual fun rememberVideoLauncher(): VideoLauncher {
    val context = LocalContext.current
    return remember(context) { VideoLauncher(context) }
}
