@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package br.com.codecacto.kmplib.ui.components.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.WebKit.WKAudiovisualMediaTypeNone
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

/**
 * Impl iOS: `WKWebView` com o IFrame API oficial do YouTube, ou `AVPlayerViewController` para
 * arquivo nosso — montados só **depois do play**, como no Android.
 *
 * `allowsInlineMediaPlayback = true` é o que faz o vídeo tocar NO LUGAR: sem ele o iOS assume a
 * tela cheia sozinho e o `playsinline` da URL é ignorado — é o que fazia o player "sumir e voltar".
 */
@Composable
actual fun VideoPlayerInline(
    source: VideoSource?,
    modifier: Modifier,
    onExternal: () -> Unit,
    capa: @Composable (aoTocar: () -> Unit) -> Unit,
) {
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
        is VideoSource.YouTube -> UIKitView(
            modifier = modifier,
            factory = {
                val config = WKWebViewConfiguration().apply {
                    allowsInlineMediaPlayback = true
                    mediaTypesRequiringUserActionForPlayback = WKAudiovisualMediaTypeNone
                }
                // ⚠️ **A tela cheia aqui NÃO precisa de `onShowCustomView`** (o equivalente Android,
                // 2.138.0). No iOS o WebKit atende o botão de expandir sozinho, promovendo o vídeo
                // ao player nativo em tela cheia — é `allowsInlineMediaPlayback` que decide onde
                // ele COMEÇA, não onde ele pode chegar. Não há o que implementar deste lado; se um
                // dia o comportamento mudar, o caminho é `WKPreferences.elementFullscreenEnabled`.
                val bundleId = NSBundle.mainBundle.bundleIdentifier ?: "app"
                val base = "https://$bundleId"
                WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = config).apply {
                    opaque = false
                    backgroundColor = UIColor.blackColor
                    // A página do embed não rola: quem rola é a tela em volta, e um scroll dentro
                    // do outro faz o gesto disputar dono.
                    scrollView.scrollEnabled = false
                    loadHTMLString(string = htmlDoEmbedInline(source.videoId, base), baseURL = NSURL(string = base))
                } as UIView
            },
            // Parar antes de sair: sem isto o áudio segue tocando por cima da tela seguinte.
            onRelease = { view ->
                (view as? WKWebView)?.loadHTMLString(string = "", baseURL = null)
            },
        )

        is VideoSource.File -> UIKitView(
            modifier = modifier,
            factory = {
                val controller = AVPlayerViewController().apply {
                    player = AVPlayer(uRL = NSURL(string = source.url))
                    // `showsPlaybackControls` fica no default (ligado): sem controles, um player
                    // embutido não tem como pausar.
                    player?.play()
                }
                controller.view
            },
            onRelease = { },
        )

        is VideoSource.External -> onExternal()
    }
}

private fun htmlDoEmbedInline(videoId: String, base: String): String = """
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
