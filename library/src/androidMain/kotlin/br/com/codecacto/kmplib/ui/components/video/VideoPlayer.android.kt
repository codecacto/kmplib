package br.com.codecacto.kmplib.ui.components.video

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri

/**
 * Impl Android do [VideoPlayer].
 *
 * ## YouTube: `WebView` com o IFrame Player API
 *
 * É o caminho **oficial** do Google para embutir YouTube em app Android desde que a *YouTube Android
 * Player API* foi descontinuada — não é o atalho, é o SDK do fornecedor, e ele roda em contexto web
 * por construção. Três ajustes que fazem a diferença entre "toca" e "retângulo preto":
 *
 * 1. `javaScriptEnabled` — o IFrame API **é** JavaScript. Sem isso o embed nunca inicializa.
 * 2. `mediaPlaybackRequiresUserGesture = false` — sem isto o `playsinline` do iframe é ignorado em
 *    parte dos aparelhos e o play do usuário não chega ao player.
 * 3. `WebChromeClient.onShowCustomView` — **é ele que dá a tela cheia**. Sem sobrescrevê-lo, o botão
 *    de expandir do player aparece, a pessoa toca, e não acontece nada: o WebView pede à aplicação
 *    uma view em tela cheia e, sem ninguém atendendo, o pedido morre ali.
 *
 * ## Arquivo nosso: `VideoView`
 *
 * `.mp4`/`.m3u8` servidos por nós tocam no player da plataforma, com os controles do sistema. Sem
 * dependência nova: `VideoView` cobre o caso "um vídeo de apresentação" sem trazer o Media3 inteiro
 * para dentro de todo app da fábrica.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun VideoPlayerView(
    source: VideoSource,
    autoPlay: Boolean,
    modifier: Modifier,
) {
    when (source) {
        is VideoSource.YouTube -> AndroidView(
            modifier = modifier,
            factory = { context -> criarWebViewDoYouTube(context, source.videoId, autoPlay) },
            // O WebView do YouTube segura mídia e um processo de renderização: solto, continua
            // TOCANDO depois de a tela sair. `destroy()` na liberação não é higiene, é o conserto
            // do áudio que persegue quem voltou para a lista.
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
            factory = { context ->
                VideoView(context).apply {
                    setVideoURI(source.url.toUri())
                    setMediaController(MediaController(context).also { it.setAnchorView(this) })
                    if (autoPlay) start()
                }
            },
            onRelease = { it.stopPlayback() },
        )

        // Filtrado no `VideoPlayer` do commonMain — aqui só para o `when` ser exaustivo.
        is VideoSource.External -> Unit
    }
}

private fun criarWebViewDoYouTube(context: Context, videoId: String, autoPlay: Boolean): WebView =
    WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.domStorageEnabled = true
        // Fundo PRETO, não transparente. Com `0x00000000` o WebView pede composição por camada, e
        // dentro de uma árvore Compose o vídeo desapareceu por trás do conteúdo — o sintoma é
        // "pisca, fica preto e o áudio toca por baixo".
        setBackgroundColor(0xFF000000.toInt())
        // Sem isto, um WebView aninhado num container rolável do Compose fica com a rolagem
        // disputada e o player pisca a cada gesto.
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = WebView.OVER_SCROLL_NEVER

        // `WebViewClient` vazio, mas presente: sem ele todo clique dentro do player (o "assistir no
        // YouTube", o card de fim de vídeo) sai do WebView e abre o navegador do sistema.
        webViewClient = WebViewClient()
        webChromeClient = FullscreenChromeClient(context)

        // `youtube-nocookie.com`: o domínio de privacidade reforçada do próprio YouTube — não grava
        // cookie de rastreamento enquanto o vídeo não é iniciado. Mesmo player, mesma API.
        val html = """
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

        // `baseUrl` do YouTube, e não `null`: o IFrame API **recusa** requisições cuja origem ele
        // não reconhece, e um `loadData` sem base carrega como `about:blank` — o player abre e fica
        // preto, sem erro nenhum na tela.
        loadDataWithBaseURL(
            "https://www.youtube-nocookie.com",
            html,
            "text/html",
            "utf-8",
            null,
        )
    }

/**
 * O que atende o pedido de **tela cheia** do player.
 *
 * O WebView não vira tela cheia sozinho: ele entrega uma view e pede que a aplicação a mostre por
 * cima de tudo. Aqui ela vai para o `decorView` da Activity, a orientação é liberada para paisagem
 * enquanto durar, e tudo volta ao lugar no `onHideCustomView` — inclusive a orientação, que é o
 * detalhe que costuma ficar preso e deixa o app deitado depois do vídeo.
 */
private class FullscreenChromeClient(private val context: Context) : WebChromeClient() {

    private var viewEmTelaCheia: View? = null
    private var callback: CustomViewCallback? = null
    private var orientacaoAnterior: Int? = null

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        val activity = context as? Activity ?: return callback.onCustomViewHidden()
        if (viewEmTelaCheia != null) return callback.onCustomViewHidden()

        viewEmTelaCheia = view
        this.callback = callback
        orientacaoAnterior = activity.requestedOrientation

        (activity.window.decorView as FrameLayout).addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    override fun onHideCustomView() {
        val activity = context as? Activity ?: return
        viewEmTelaCheia?.let { (activity.window.decorView as FrameLayout).removeView(it) }
        viewEmTelaCheia = null
        orientacaoAnterior?.let { activity.requestedOrientation = it }
        orientacaoAnterior = null
        callback?.onCustomViewHidden()
        callback = null
    }
}
