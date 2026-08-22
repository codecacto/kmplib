package br.com.codecacto.kmplib.ui.components.video

import android.annotation.SuppressLint
import androidx.compose.runtime.DisposableEffect
import android.view.Gravity
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
    val emTelaCheia = telaCheia.container != null

    // **Voltar SAI da tela cheia, e não da tela.** Sem isto o gesto de voltar navegaria para trás
    // com o vídeo ainda expandido por cima do decor — a pessoa sairia da página e continuaria
    // vendo o player.
    BackHandler(enabled = emTelaCheia) {
        telaCheia = telaCheia.encerrar(contexto.activity())
    }

    // Sair da tela com o player expandido não pode deixar o container pendurado no `content` nem o
    // aparelho travado em paisagem — e é fácil acontecer: basta o gesto do sistema, ou uma
    // notificação que leva a outra tela.
    DisposableEffect(Unit) {
        onDispose { telaCheia.encerrar(contexto.activity()) }
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
                    // ⚠️ **Esconder o conteúdo original NÃO é detalhe: é o que evita a tela preta**
                    // (2.138.1). A primeira versão só empilhava a custom view sobre o `decorView`,
                    // e o resultado foi vídeo com ÁUDIO e imagem preta — o `WebView` de 16:9
                    // continuava vivo e visível por baixo, e as duas superfícies de vídeo disputam
                    // a mesma composição de hardware. Quem ganha é a de baixo, que está recortada.
                    //
                    // O desenho abaixo é o mesmo da `KmplibVideoActivity`, que funciona há meses:
                    // um container preto ocupando tudo, a custom view dentro dele, e **os irmãos
                    // escondidos** enquanto durar. Ir para `android.R.id.content` (e não para o
                    // decor) é o que torna esses irmãos alcançáveis — no decor eles são as barras
                    // do sistema, não a tela do app.
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            val activity = ctx.activity() ?: return
                            if (view == null || telaCheia.container != null) return

                            val content = activity.findViewById<ViewGroup>(android.R.id.content)
                            val escondidos = (0 until content.childCount)
                                .map { content.getChildAt(it) }
                                .filter { it.visibility == View.VISIBLE }
                            escondidos.forEach { it.visibility = View.INVISIBLE }

                            val container = FrameLayout(activity).apply {
                                setBackgroundColor(Color.BLACK)
                                addView(
                                    view,
                                    FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    ).apply { gravity = Gravity.CENTER },
                                )
                            }
                            content.addView(
                                container,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                ),
                            )

                            telaCheia = TelaCheia(
                                container = container,
                                callback = callback,
                                escondidos = escondidos,
                                orientacaoAnterior = activity.requestedOrientation,
                            )
                            activity.requestedOrientation =
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            activity.entrarEmImersivo()
                        }

                        override fun onHideCustomView() {
                            telaCheia = telaCheia.encerrar(ctx.activity())
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
    /** O container preto que embrulha a custom view. `null` = não está em tela cheia. */
    val container: FrameLayout? = null,
    val callback: WebChromeClient.CustomViewCallback? = null,
    /**
     * Os irmãos que estavam visíveis no `content` e foram escondidos — para devolvê-los ao sair.
     *
     * Guardar a LISTA, e não um "escondi tudo", é o que evita acender ao sair algo que já estava
     * apagado antes de o vídeo expandir.
     */
    val escondidos: List<View> = emptyList(),
    val orientacaoAnterior: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
) {

    /** Desfaz tudo, na ordem inversa. Idempotente: chamar sem tela cheia não faz nada. */
    fun encerrar(activity: Activity?): TelaCheia {
        val atual = container ?: return this
        (atual.parent as? ViewGroup)?.removeView(atual)
        escondidos.forEach { it.visibility = View.VISIBLE }
        // Avisar o player que a tela cheia acabou: sem o callback ele continua achando que está
        // expandido, e o botão inverte de sentido.
        callback?.onCustomViewHidden()

        // **A orientação volta ao que ERA, e `UNSPECIFIED` vira `USER`.** Restaurar o valor cru
        // funciona quando a Activity tinha uma orientação fixa (um app retrato-só volta ao
        // retrato). Quando ela era `UNSPECIFIED` — o caso de quem não declara nada no manifest —
        // devolver `UNSPECIFIED` deixa a decisão num estado indefinido logo depois de um
        // `SENSOR_LANDSCAPE` forçado, e o aparelho fica deitado. `USER` diz explicitamente "quem
        // manda daqui em diante é o usuário e o sensor", que é o que a pessoa espera ao fechar o
        // vídeo.
        activity?.requestedOrientation = when (orientacaoAnterior) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED -> ActivityInfo.SCREEN_ORIENTATION_USER
            else -> orientacaoAnterior
        }
        activity?.sairDoImersivo()
        return TelaCheia()
    }
}

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
