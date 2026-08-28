package br.com.codecacto.kmplib.ui.components.video

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
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
import androidx.compose.runtime.rememberUpdatedState
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

    // ── A TELA CHEIA É OUTRA TELA — e a 2.139.0 existe por causa disto ──────────────────────────
    //
    // A 2.138.x tentou promover a custom view do player para dentro da própria janela do app
    // (decorView, depois `android.R.id.content`), com paisagem forçada e imersivo. **Não funciona,
    // e o modo de falhar é violento:** `NullPointerException` em `FrameLayout.onMeasure`, com um
    // filho `null` no `content`.
    //
    // A causa não está no fullscreen: está no fato de o app ser RESPONSIVO. Telefone em paisagem
    // passa de `COMPACTA` para `MEDIA`, e um chassi que troca de composição por classe de janela
    // **reconstrói a árvore inteira** ao girar. O `AndroidView` do WebView é descartado (o
    // `onRelease` destrói a view que alimenta o vídeo), o composable sai da composição — e a custom
    // view, que estava pendurada fora da árvore, fica órfã no meio de um layout pass.
    //
    // Ou seja: promover tela cheia dentro da árvore do Compose só é seguro em app que não muda de
    // layout com a largura, que é o oposto do que a fábrica faz. O caminho certo é o que a lib já
    // tinha e que funciona há meses — a **janela do sistema** do [VideoLauncher], onde não há
    // composição nenhuma para disputar nem para desmontar.
    //
    // **O vídeo recomeça do zero**, e isso foi decidido com o fundador (22/ago/2026): *"se não tiver
    // como continuar o vídeo de onde parou, pode colocar do zero, não tem problema"*. Retomar a
    // posição exigiria falar com o player pelo IFrame API e devolver o instante à outra janela —
    // custo alto para um segundo de diferença.
    val player = rememberVideoLauncher()
    // `rememberUpdatedState` porque o `factory` do `AndroidView` roda UMA vez e captura o que
    // enxerga naquele instante: sem ele, o `WebChromeClient` ficaria preso à primeira `source`.
    val abrirEmTelaCheia by rememberUpdatedState<() -> Unit> {
        // **Abre PRIMEIRO, desliga depois.** A ordem inversa parecia mais limpa (parar o áudio
        // antes de sair), mas põe uma recomposição — que destrói o WebView de onde este callback
        // está sendo chamado — entre a decisão e a abertura. Abrir primeiro garante que o clique
        // sempre produz a outra tela; o áudio do embed morre em seguida, no `onRelease`, e a
        // sobreposição dura o tempo de um frame.
        source?.let { player.play(it) }
        tocando = false
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

                    // ⚠️ **OS DOIS métodos, sempre — é o que HABILITA o botão** (2.139.1).
                    //
                    // O WebView só considera que a página sabe fazer tela cheia quando o
                    // `WebChromeClient` implementa `onShowCustomView` **e** `onHideCustomView`.
                    // Com um só, o controle de expandir aparece no player e **o toque não produz
                    // efeito nenhum** — nem callback, nem erro, nem log. Foi o que aconteceu na
                    // 2.139.0, que tinha só o primeiro.
                    //
                    // O que o `onShowCustomView` faz aqui é recusar a promoção e abrir a janela do
                    // sistema: `onCustomViewHidden()` devolve o embed ao estado inline (sem isso o
                    // player fica achando que está expandido, e o botão inverte de sentido), e a
                    // tela cheia de verdade acontece no `VideoLauncher`.
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            callback?.onCustomViewHidden()
                            abrirEmTelaCheia()
                        }

                        // Nada a desfazer: nenhuma view foi promovida. O método existe porque a
                        // PRESENÇA dele é o que faz o WebView oferecer o botão — não o corpo.
                        override fun onHideCustomView() = Unit
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
