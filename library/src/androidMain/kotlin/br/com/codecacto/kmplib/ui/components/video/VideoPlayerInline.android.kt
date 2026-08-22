package br.com.codecacto.kmplib.ui.components.video

import android.annotation.SuppressLint
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import android.view.View
import android.content.pm.ActivityInfo
import android.content.ContextWrapper
import android.content.Context
import android.app.Activity
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

    // A tela cheia do player. `remember` sem chave: ela precisa sobreviver às recomposições do
    // vídeo, e o que a encerra é o `onHideCustomView` — nunca uma troca de estado do Compose.
    val contexto = LocalContext.current
    var telaCheia by remember { mutableStateOf(TelaCheia()) }
    val emTelaCheia = telaCheia.view != null

    // **Voltar SAI da tela cheia, e não da tela.** Sem isto o gesto de voltar navegaria para trás
    // com o vídeo ainda expandido por cima do decor — a pessoa sairia da página e continuaria
    // vendo o player.
    BackHandler(enabled = emTelaCheia) {
        telaCheia.view?.let {
            val activity = contexto.activity()
            (activity?.window?.decorView as? ViewGroup)?.removeView(it)
            telaCheia.callback?.onCustomViewHidden()
            activity?.requestedOrientation = telaCheia.orientacaoAnterior
            activity?.sairDoImersivo()
            telaCheia = TelaCheia()
        }
    }

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

                    // **A TELA CHEIA do player, atendida de verdade** (2.138.0).
                    //
                    // O botão de expandir do YouTube pede `onShowCustomView`, entregando uma view
                    // que precisa ocupar a tela toda. Um `WebChromeClient` vazio faz o controle
                    // aparecer e **não fazer nada** — o pedido cai no chão.
                    //
                    // A view vai para o **decorView da Activity**, e não para a árvore do Compose:
                    // aqui dentro o player está confinado ao retângulo de 16:9, e é justamente daí
                    // que ele quer sair. No decor ela fica por cima de tudo, sem disputar camada
                    // com nenhuma composição — que é o mesmo motivo de a tela cheia do
                    // `VideoLauncher` ser uma Activity.
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            val activity = ctx.activity() ?: return
                            if (view == null || telaCheia.view != null) return
                            telaCheia = TelaCheia(
                                view = view,
                                callback = callback,
                                orientacaoAnterior = activity.requestedOrientation,
                            )
                            view.setBackgroundColor(Color.BLACK)
                            (activity.window.decorView as ViewGroup).addView(
                                view,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                ),
                            )
                            activity.requestedOrientation =
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            activity.entrarEmImersivo()
                        }

                        override fun onHideCustomView() {
                            val activity = ctx.activity() ?: return
                            val atual = telaCheia
                            atual.view?.let { (activity.window.decorView as ViewGroup).removeView(it) }
                            // Avisar o player que a tela cheia acabou: sem o callback ele continua
                            // achando que está expandido e o botão inverte de sentido.
                            atual.callback?.onCustomViewHidden()
                            activity.requestedOrientation = atual.orientacaoAnterior
                            activity.sairDoImersivo()
                            telaCheia = TelaCheia()
                        }
                    }

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

/**
 * O que precisa ser lembrado enquanto o player está expandido.
 *
 * A **orientação anterior** entra aqui porque restaurá-la é o que devolve o aparelho ao retrato
 * quando a pessoa sai da tela cheia — fixar `SENSOR_LANDSCAPE` e não desfazer deixaria a página
 * deitada depois do vídeo.
 */
private data class TelaCheia(
    val view: View? = null,
    val callback: WebChromeClient.CustomViewCallback? = null,
    val orientacaoAnterior: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
)

/**
 * A `Activity` por trás de um `Context` do Compose.
 *
 * O `LocalContext` costuma ser um `ContextWrapper` (tema aplicado), e não a Activity direto — por
 * isso a cadeia. Um `as? Activity` seco devolve `null` na maioria dos apps, e a tela cheia
 * silenciosamente não abriria.
 */
private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private fun Activity.entrarEmImersivo() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

private fun Activity.sairDoImersivo() {
    WindowCompat.setDecorFitsSystemWindows(window, true)
    WindowInsetsControllerCompat(window, window.decorView)
        .show(WindowInsetsCompat.Type.systemBars())
}
