package br.com.codecacto.kmplib.ui.components.video

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * A tela do vídeo — **uma Activity, sem Compose nenhum dentro**.
 *
 * É o desenho que funciona, e a razão está no que ele NÃO tem: nenhuma árvore de composição
 * disputando camada com a view de vídeo. Player embutido numa coluna rolável e player dentro de um
 * `Dialog` do Compose falham igual — piscam, ficam pretos, o áudio toca por baixo e os controles
 * não respondem, porque estão dentro do retângulo que não está sendo desenhado.
 *
 * Declarada no manifest da **lib** (não do app): `android:theme` translúcido, `configChanges`
 * completo para o giro não recriar a tela — recriar significa recomeçar o vídeo do zero — e
 * `exported="false"`, porque ninguém de fora tem o que abrir aqui.
 */
open class KmplibVideoActivity : ComponentActivity() {

    /**
     * `true` na variante compacta ([KmplibVideoCompactActivity]): janela translúcida, player em 16:9
     * no meio da tela, sem forçar paisagem e sem modo imersivo.
     *
     * É uma propriedade sobrescrita, e não um extra do Intent, porque quem decide o TAMANHO é a
     * janela — e a janela é escolhida no manifest, pelo tema. Ler um extra aqui daria um layout
     * compacto dentro de uma janela opaca de tela cheia: um cartão de vídeo com uma moldura preta
     * do tamanho do aparelho.
     */
    protected open val compacto: Boolean get() = false

    private var webView: WebView? = null
    private var viewEmTelaCheia: View? = null
    private var callbackDeTelaCheia: WebChromeClient.CustomViewCallback? = null
    private lateinit var containerDeTelaCheia: FrameLayout
    private lateinit var containerDoConteudo: FrameLayout

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val youtubeId = intent.getStringExtra(EXTRA_YOUTUBE_ID)
        val fileUrl = intent.getStringExtra(EXTRA_FILE_URL)
        if (youtubeId == null && fileUrl == null) {
            finish()
            return
        }

        if (!compacto) {
            // Paisagem e imersivo são do modo CHEIO: lá assistir é a tarefa. No compacto, virar o
            // aparelho e engolir as barras do sistema para tocar dois minutos de apresentação é
            // exatamente o que o fundador pediu para não acontecer.
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            entrarEmImersivo()
        } else {
            // Tocar fora fecha — é o gesto que todo mundo tenta primeiro num cartão sobre a tela.
            setFinishOnTouchOutside(true)
        }

        val raiz = FrameLayout(this).apply {
            // No compacto o fundo é um VÉU: a tela de onde a pessoa veio continua visível em volta
            // do player, que é o ponto do modo. Preto opaco aqui apagaria o contexto e o modo
            // compacto viraria o cheio com bordas.
            setBackgroundColor(if (compacto) Color.argb(190, 0, 0, 0) else Color.BLACK)
        }

        containerDoConteudo = FrameLayout(this)
        raiz.addView(containerDoConteudo, if (compacto) quadroCompacto() else ocuparTudo())

        // Container da tela cheia do PRÓPRIO player (o botão de expandir do YouTube). Nasce oculto:
        // é o `onShowCustomView` que o preenche.
        containerDeTelaCheia = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        raiz.addView(containerDeTelaCheia, ocuparTudo())

        if (youtubeId != null) {
            montarYouTube(youtubeId)
        } else {
            montarArquivo(fileUrl!!)
        }

        // Fechar é uma view NATIVA, e não parte do player: ela responde mesmo quando o embed não
        // carregou — rede fora, vídeo removido, id errado.
        val fechar = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            setPadding(24, 24, 24, 24)
            setColorFilter(Color.WHITE)
            contentDescription = "Fechar o vídeo"
            setOnClickListener { finish() }
        }
        // **O X vive na RAIZ, acima dos dois containers** (2.139.2) — antes ele era filho do
        // conteúdo, e o `onShowCustomView` escondia o conteúdo inteiro: dentro do fullscreen do
        // player, a única saída visível era o controle do próprio embed. Se ele falhasse, ou se a
        // pessoa não o encontrasse, não sobrava nada. Na raiz, o fechar está sempre lá.
        raiz.addView(
            fechar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = 48
                marginEnd = 16
            },
        )

        setContentView(raiz)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun montarYouTube(videoId: String) {
        // **A base é o PACOTE do app, e o `origin` do embed é igual a ela.** O IFrame API valida a
        // origem de quem pede: com uma base que ele não reconhece (ou `null`, que vira
        // `about:blank`), o player carrega e fica PRETO, sem erro nenhum.
        val base = "https://$packageName"
        val embed = "https://www.youtube-nocookie.com/embed/$videoId" +
            "?autoplay=1&rel=0&playsinline=1&origin=$base"

        val html = """
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
                <iframe src="$embed"
                    allow="autoplay; encrypted-media; fullscreen"
                    allowfullscreen
                    referrerpolicy="strict-origin-when-cross-origin"></iframe>
            </body>
            </html>
        """.trimIndent()

        val web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true

            // Tocar em "assistir no YouTube" ou no nome do canal SAI para o app do YouTube, em vez
            // de navegar dentro deste WebView — que viraria um navegador improvisado sem barra de
            // endereço nem botão de voltar.
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val url = request?.url?.toString() ?: return false
                    val saiDoPlayer = SAIDAS.any { url.contains(it) }
                    if (saiDoPlayer) {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        return true
                    }
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view == null) return
                    viewEmTelaCheia = view
                    callbackDeTelaCheia = callback
                    view.setBackgroundColor(Color.BLACK)
                    containerDeTelaCheia.addView(view, ocuparTudo())
                    containerDeTelaCheia.visibility = View.VISIBLE
                    containerDoConteudo.visibility = View.GONE
                    // **Aqui o compacto vira cheio, e é o único lugar em que isso acontece.** Quem
                    // tocou no botão de expandir do player pediu a tela toda — inclusive a
                    // paisagem, que no compacto não é forçada na abertura.
                    if (compacto) {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                    entrarEmImersivo()
                }

                /**
                 * **Sair do fullscreen do player FECHA esta tela** — no modo cheio (2.139.2).
                 *
                 * Aqui a Activity JÁ é a tela cheia: o player ocupa tudo desde que ela abriu. Ao
                 * tocar em expandir, o embed entra no seu próprio fullscreen e nada muda de tamanho
                 * (era tudo, continua tudo) — só o ícone vira "minimizar". Tocar nele devolvia ao
                 * container de baixo, do mesmo tamanho: **visualmente, nada acontecia**, e a pessoa
                 * ficava presa procurando a saída. Relato do fundador, palavra por palavra: *"se eu
                 * clico de novo no botão de tela cheia para minimizar, ele não faz nada; o ícone até
                 * volta"*.
                 *
                 * Fechar é a leitura certa do gesto: quem pede para minimizar uma tela que É o vídeo
                 * está pedindo para sair do vídeo.
                 *
                 * No modo **compacto** a conta é outra e o comportamento antigo continua: lá o
                 * player é um cartão no meio da tela, então sair do fullscreen tem para onde voltar.
                 */
                override fun onHideCustomView() {
                    if (compacto) sairDaTelaCheia() else finish()
                }
            }

            loadDataWithBaseURL(base, html, "text/html", "UTF-8", null)
        }
        webView = web
        containerDoConteudo.addView(web, ocuparTudo())
    }

    private fun montarArquivo(url: String) {
        val video = VideoView(this).apply {
            setVideoURI(Uri.parse(url))
            setMediaController(MediaController(this@KmplibVideoActivity).also { it.setAnchorView(this) })
            // Vídeo que acaba fecha a tela: deixar o último quadro congelado faz parecer travado.
            setOnCompletionListener { finish() }
            setOnPreparedListener { mp: MediaPlayer -> mp.start() }
        }
        containerDoConteudo.addView(
            video,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply { gravity = Gravity.CENTER },
        )
    }

    private fun sairDaTelaCheia() {
        if (compacto) {
            // Volta ao cartão: a orientação destrava e as barras do sistema voltam, porque a tela
            // de baixo — que continua visível em volta — é retrato.
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            sairDoImersivo()
        }
        viewEmTelaCheia?.let { containerDeTelaCheia.removeView(it) }
        viewEmTelaCheia = null
        callbackDeTelaCheia?.onCustomViewHidden()
        callbackDeTelaCheia = null
        containerDeTelaCheia.visibility = View.GONE
        containerDoConteudo.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        // Sem isto o áudio continua tocando depois de a tela fechar — o WebView sobrevive à
        // Activity enquanto o processo de renderização não for derrubado.
        webView?.apply {
            loadUrl("about:blank")
            stopLoading()
            webChromeClient = WebChromeClient()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    private fun ocuparTudo() = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )

    /**
     * O quadro do modo compacto: largura cheia menos um respiro, altura **16:9**, centrado.
     *
     * A altura é calculada e não deixada em `WRAP_CONTENT` porque quem está dentro é um `WebView` —
     * ele não tem altura natural, e sem medida vira uma faixa de poucos pixels ou a tela inteira,
     * conforme o momento em que o HTML termina de carregar.
     */
    private fun quadroCompacto(): FrameLayout.LayoutParams {
        val respiro = (16 * resources.displayMetrics.density).toInt()
        val largura = resources.displayMetrics.widthPixels - respiro * 2
        return FrameLayout.LayoutParams(largura, largura * 9 / 16).apply {
            gravity = Gravity.CENTER
        }
    }

    private fun sairDoImersivo() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    private fun entrarEmImersivo() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() {
        // Voltar estando em tela cheia do player VOLTA para o player, não fecha tudo.
        if (viewEmTelaCheia != null) {
            sairDaTelaCheia()
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    companion object {
        const val EXTRA_YOUTUBE_ID: String = "kmplib.video.youtubeId"
        const val EXTRA_FILE_URL: String = "kmplib.video.fileUrl"

        /** URLs que significam "quero sair do player" — vão para o app do YouTube. */
        private val SAIDAS = listOf(
            "youtube.com/watch",
            "youtu.be/",
            "youtube.com/channel",
            "youtube.com/@",
        )
    }
}

/**
 * A variante **compacta** — a mesma tela, noutra janela.
 *
 * Ela existe só para carregar um `android:theme` translúcido no manifest: translucidez é resolvida
 * quando o sistema cria a janela, antes do `onCreate`, então não há como ligá-la em runtime a partir
 * de um extra do Intent. Todo o comportamento está em [KmplibVideoActivity]; aqui só se diz qual dos
 * dois modos é.
 */
class KmplibVideoCompactActivity : KmplibVideoActivity() {
    override val compacto: Boolean get() = true
}
