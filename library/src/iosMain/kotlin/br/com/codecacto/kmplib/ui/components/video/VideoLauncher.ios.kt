@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package br.com.codecacto.kmplib.ui.components.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UIViewController
import platform.WebKit.WKAudiovisualMediaTypeNone
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

/**
 * Impl iOS: o vídeo é apresentado num `UIViewController` **modal em tela cheia**, fora da árvore do
 * Compose — mesma razão do Android (ver o KDoc de [VideoLauncher]).
 *
 * Arquivo nosso usa `AVPlayerViewController`, que é o player da Apple com os controles nativos.
 * YouTube usa `WKWebView` com o IFrame API oficial — e com os mesmos três detalhes de origem que o
 * Android: `origin=` na URL, base igual ao bundle id, e `referrerpolicy`.
 */
actual class VideoLauncher {

    actual fun play(source: VideoSource) {
        val raiz = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        val controller: UIViewController = when (source) {
            is VideoSource.YouTube -> ControladorDeYouTube(source.videoId)
            is VideoSource.File -> AVPlayerViewController().apply {
                player = AVPlayer(uRL = NSURL(string = source.url))
                player?.play()
            }

            is VideoSource.External -> return
        }
        controller.modalPresentationStyle = UIModalPresentationFullScreen
        raiz.presentViewController(controller, animated = true, completion = null)
    }
}

@Composable
actual fun rememberVideoLauncher(): VideoLauncher = remember { VideoLauncher() }

/** O `WKWebView` do YouTube em tela cheia, com um botão nativo de fechar por cima. */
private class ControladorDeYouTube(private val videoId: String) : UIViewController(null, null) {

    private var web: WKWebView? = null

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.blackColor

        val config = WKWebViewConfiguration().apply {
            // Sem isto o iOS assume o vídeo em tela cheia sozinho e o `playsinline` é ignorado —
            // é o que faz o player "sumir e voltar".
            allowsInlineMediaPlayback = true
            mediaTypesRequiringUserActionForPlayback = WKAudiovisualMediaTypeNone
        }
        val bundleId = NSBundle.mainBundle.bundleIdentifier ?: "app"
        val base = "https://$bundleId"

        val webView = WKWebView(frame = CGRectZero.readValue(), configuration = config).apply {
            opaque = false
            backgroundColor = UIColor.blackColor
            scrollView.scrollEnabled = false
            loadHTMLString(string = htmlDoYouTube(videoId, base), baseURL = NSURL(string = base))
        }
        web = webView
        webView.setTranslatesAutoresizingMaskIntoConstraints(false)
        view.addSubview(webView)
        webView.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor).active = true
        webView.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor).active = true
        webView.centerYAnchor.constraintEqualToAnchor(view.centerYAnchor).active = true
        webView.heightAnchor.constraintEqualToAnchor(webView.widthAnchor, 9.0 / 16.0).active = true

        adicionarBotaoDeFechar()
    }

    private fun adicionarBotaoDeFechar() {
        val botao = platform.UIKit.UIButton.buttonWithType(platform.UIKit.UIButtonTypeSystem)
        botao.setTitle("✕", forState = platform.UIKit.UIControlStateNormal)
        botao.setTitleColor(UIColor.whiteColor, forState = platform.UIKit.UIControlStateNormal)
        botao.titleLabel?.font = platform.UIKit.UIFont.systemFontOfSize(28.0)
        botao.accessibilityLabel = "Fechar o vídeo"
        botao.addTarget(this, platform.darwin.sel_registerName("fechar"), platform.UIKit.UIControlEventTouchUpInside)
        botao.setTranslatesAutoresizingMaskIntoConstraints(false)
        view.addSubview(botao)
        botao.topAnchor.constraintEqualToAnchor(view.safeAreaLayoutGuide.topAnchor, 8.0).active = true
        botao.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor, -16.0).active = true
        botao.widthAnchor.constraintEqualToConstant(48.0).active = true
        botao.heightAnchor.constraintEqualToConstant(48.0).active = true
    }

    @platform.darwin.ObjCAction
    fun fechar() {
        // Parar antes de sair: sem isto o áudio segue tocando por cima da tela anterior.
        web?.loadHTMLString(string = "", baseURL = null)
        dismissViewControllerAnimated(true, completion = null)
    }
}

private fun htmlDoYouTube(videoId: String, base: String): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <meta name="referrer" content="strict-origin-when-cross-origin">
        <style>
            * { margin: 0; padding: 0; }
            html, body { width: 100%; height: 100%; background: #000; }
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
