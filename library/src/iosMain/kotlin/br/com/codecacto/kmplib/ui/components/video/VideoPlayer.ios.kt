@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package br.com.codecacto.kmplib.ui.components.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.WebKit.WKAudiovisualMediaTypeNone
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

/**
 * Impl iOS do [VideoPlayer].
 *
 * ## YouTube: `WKWebView` com o IFrame Player API
 *
 * Mesmo motivo do Android: é o caminho **oficial** do Google para embutir YouTube em app, e ele roda
 * em contexto web por construção. Duas linhas decidem se toca ou não:
 *
 * - `allowsInlineMediaPlayback = true` — sem isto o iOS **assume o vídeo em tela cheia** no play, e
 *   o `playsinline` do iframe é ignorado. É o comportamento que faz o player "sumir" e voltar.
 * - `mediaTypesRequiringUserActionForPlayback = WKAudiovisualMediaTypeNone` — deixa o play do
 *   usuário chegar ao player em vez de ser barrado pela política de autoplay do WebKit.
 *
 * A **tela cheia** é a nativa do WebKit: o botão do próprio player a aciona, e o iOS a apresenta
 * sozinho — não há o pedido-à-aplicação que o Android faz, então aqui não há nada a implementar.
 *
 * ## Arquivo nosso: `AVPlayerViewController`
 *
 * O player da plataforma, com os controles nativos e o botão de tela cheia que a Apple já desenha.
 */
@Composable
internal actual fun VideoPlayerView(
    source: VideoSource,
    autoPlay: Boolean,
    modifier: Modifier,
) {
    when (source) {
        is VideoSource.YouTube -> {
            val webView = remember(source.videoId, autoPlay) {
                val config = WKWebViewConfiguration().apply {
                    allowsInlineMediaPlayback = true
                    mediaTypesRequiringUserActionForPlayback = WKAudiovisualMediaTypeNone
                }
                WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = config).apply {
                    opaque = false
                    scrollView.scrollEnabled = false
                    // `baseURL` do YouTube, e não `null`: o IFrame API recusa origem que não
                    // reconhece, e sem base o conteúdo carrega como `about:blank` — o player abre
                    // preto, sem erro nenhum.
                    loadHTMLString(
                        string = htmlDoYouTube(source.videoId, autoPlay),
                        baseURL = NSURL(string = "https://www.youtube-nocookie.com"),
                    )
                }
            }
            UIKitView(
                modifier = modifier,
                factory = { webView },
                // Parar no descarte: sem isto o áudio continua tocando depois de a tela sair.
                onRelease = { web ->
                    web.stopLoading()
                    web.loadHTMLString(string = "", baseURL = null)
                },
            )
        }

        is VideoSource.File -> {
            val controller = remember(source.url, autoPlay) {
                AVPlayerViewController().apply {
                    player = AVPlayer(uRL = NSURL(string = source.url))
                    if (autoPlay) player?.play()
                }
            }
            UIKitView(
                modifier = modifier,
                factory = { controller.view },
                onRelease = { controller.player?.pause() },
            )
        }

        // Filtrado no `VideoPlayer` do commonMain — aqui só para o `when` ser exaustivo.
        is VideoSource.External -> Unit
    }
}

private fun htmlDoYouTube(videoId: String, autoPlay: Boolean): String = """
    <!doctype html>
    <html>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
        <style>
          html, body { margin:0; padding:0; background:#000; height:100%; overflow:hidden; }
          iframe { border:0; width:100%; height:100%; display:block; }
        </style>
      </head>
      <body>
        <iframe
          src="https://www.youtube-nocookie.com/embed/$videoId?playsinline=1&rel=0&modestbranding=1&fs=1&autoplay=${if (autoPlay) 1 else 0}"
          allow="accelerometer; autoplay; encrypted-media; picture-in-picture"
          allowfullscreen></iframe>
      </body>
    </html>
""".trimIndent()
