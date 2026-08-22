package br.com.codecacto.kmplib.ui.components.video

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Impl Android: `WebView` com o **IFrame Player API oficial** do YouTube, ou `VideoView` para
 * arquivo nosso — montados só **depois do play**.
 *
 * Os três detalhes que decidem se o embed carrega são os mesmos da tela cheia (ver o KDoc do
 * `VideoLauncher`): `origin=` na URL, base igual ao pacote do app, e `referrerpolicy`.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun VideoPlayerInline(
    source: VideoSource?,
    modifier: Modifier,
    onExternal: () -> Unit,
    capa: @Composable (aoTocar: () -> Unit) -> Unit,
) {
    // O play é um estado da COMPOSIÇÃO, e não um parâmetro: quem toca é a capa, e o player nasce
    // no mesmo lugar em que ela estava.
    var tocando by remember(source) { mutableStateOf(false) }

    if (!tocando || source == null) {
        capa {
            when (source) {
                null, is VideoSource.External -> onExternal()
                else -> tocando = true
            }
        }
        return
    }

    when (source) {
        is VideoSource.YouTube -> AndroidView(
            modifier = modifier,
            factory = { ctx ->
                val base = "https://${ctx.packageName}"
                WebView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(Color.BLACK)
                    settings.javaScriptEnabled = true
                    // O gesto que montou o player JÁ é o gesto do usuário: sem isto o autoplay é
                    // recusado e a pessoa precisa tocar uma segunda vez, dentro do embed.
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.domStorageEnabled = true

                    // Tocar em "assistir no YouTube" ou no nome do canal SAI para o app do
                    // YouTube, em vez de navegar dentro deste WebView — que viraria um navegador
                    // improvisado, sem barra de endereço nem botão de voltar, no meio da página.
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (SAIDAS.any { url.contains(it) }) {
                                runCatching {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                                return true
                            }
                            return false
                        }
                    }

                    // **`WebChromeClient` vazio, e isso é deliberado.** Sem ele o botão de tela
                    // cheia do player não faz NADA — o embed pede `onShowCustomView` e ninguém
                    // atende. Com ele presente (mesmo sem tratar a view), o player desenha o
                    // controle e o próprio YouTube usa a API de fullscreen do HTML dentro do
                    // WebView. A tela cheia de verdade, com giro e barras escondidas, continua
                    // sendo a do `VideoLauncher`.
                    webChromeClient = WebChromeClient()

                    loadDataWithBaseURL(base, htmlDoEmbed(source.videoId, base), "text/html", "UTF-8", null)
                }
            },
            // **Sem isto o áudio continua tocando depois de a tela sair.** O WebView sobrevive à
            // composição enquanto o processo de renderização não for derrubado, e `about:blank`
            // antes do `destroy()` é o que interrompe a mídia em curso.
            onRelease = { web ->
                web.loadUrl("about:blank")
                web.stopLoading()
                web.webChromeClient = WebChromeClient()
                (web.parent as? ViewGroup)?.removeView(web)
                web.destroy()
            },
        )

        is VideoSource.File -> AndroidView(
            modifier = modifier,
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setVideoURI(Uri.parse(source.url))
                    setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                    setOnPreparedListener { it.start() }
                }
            },
            onRelease = { it.stopPlayback() },
        )

        // Não chega aqui: `External` nunca liga `tocando` (ver acima). O ramo existe pela
        // exaustividade do `when`, que é o que garante fonte nova não passar em silêncio.
        is VideoSource.External -> onExternal()
    }
}

/** URLs que significam "quero sair do player" — vão para o app do YouTube. */
private val SAIDAS = listOf("youtube.com/watch", "youtu.be/", "youtube.com/channel", "youtube.com/@")

private fun htmlDoEmbed(videoId: String, base: String): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <meta name="referrer" content="strict-origin-when-cross-origin">
        <style>
            * { margin: 0; padding: 0; }
            html, body { width: 100%; height: 100%; background: #000; overflow: hidden; }
            iframe { width: 100%; height: 100%; border: none; }
        </style>
    </head>
    <body>
        <iframe src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&rel=0&playsinline=1&origin=$base"
            allow="autoplay; encrypted-media; fullscreen"
            allowfullscreen
            referrerpolicy="strict-origin-when-cross-origin"></iframe>
    </body>
    </html>
""".trimIndent()
