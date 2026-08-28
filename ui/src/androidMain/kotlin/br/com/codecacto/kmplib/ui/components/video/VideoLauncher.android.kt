package br.com.codecacto.kmplib.ui.components.video

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Impl Android: abre a [KmplibVideoActivity], declarada no manifest da própria lib. */
actual class VideoLauncher(private val context: Context) {

    actual fun play(source: VideoSource, compact: Boolean) {
        // **Duas Activities, e não um `setTheme` em runtime.** Translucidez é atributo da JANELA:
        // ela é resolvida quando o sistema cria a janela, antes de o `onCreate` rodar. Trocar o
        // tema depois disso dá uma janela opaca com um tema translúcido declarado — fundo preto
        // atrás do player, exatamente o que o modo compacto existe para evitar.
        val tela = if (compact) KmplibVideoCompactActivity::class.java else KmplibVideoActivity::class.java
        val intent = when (source) {
            is VideoSource.YouTube -> Intent(context, tela)
                .putExtra(KmplibVideoActivity.EXTRA_YOUTUBE_ID, source.videoId)

            is VideoSource.File -> Intent(context, tela)
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
