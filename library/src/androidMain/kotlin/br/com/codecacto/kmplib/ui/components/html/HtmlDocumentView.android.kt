package br.com.codecacto.kmplib.ui.components.html

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Impl Android do [HtmlDocumentView]: `android.webkit.WebView`, o componente oficial da plataforma.
 *
 * Três coisas que este arquivo faz e que um `AndroidView { WebView(it) }` escrito na tela do app
 * quase nunca faz:
 *
 * 1. **Libera o WebView com a tela** (`onRelease`) — para o carregamento, desliga os clients, tira a
 *    view da hierarquia e chama `destroy()`. Um `WebView` esquecido continua vivo segurando o
 *    documento (dado pessoal) em memória e, no caso de um documento com mídia, continua tocando.
 * 2. **Aplica a política de navegação da lib** — a decisão é a mesma função pura consultada pelo
 *    iOS ([htmlLinkDecision]), então as duas plataformas não divergem no que é link, âncora ou
 *    esquema recusado.
 * 3. **Só reporta erro do frame principal.** `onReceivedError` dispara também para cada subrecurso
 *    (uma imagem que não carregou, um favicon inexistente); reportar tudo trocaria um documento
 *    perfeitamente legível por uma tela de erro.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun HtmlDocumentWebView(
    source: HtmlDocumentSource,
    allowJavaScript: Boolean,
    allowExternalNavigation: Boolean,
    zoom: Float,
    reloadKey: Int,
    onState: (HtmlDocumentState) -> Unit,
    onDecision: (HtmlLinkDecision) -> Unit,
    modifier: Modifier,
) {
    val currentSource by rememberUpdatedState(source)
    val currentAllowExternal by rememberUpdatedState(allowExternalNavigation)
    val currentOnState by rememberUpdatedState(onState)
    val currentOnDecision by rememberUpdatedState(onDecision)

    val client = remember {
        object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val url = request?.url?.toString().orEmpty()
                val decision = htmlLinkDecision(
                    url = url,
                    documentUrl = currentSource.documentUrl,
                    allowExternalNavigation = currentAllowExternal,
                    // O carregamento inicial não passa por aqui (loadUrl/loadData não chamam este
                    // gancho), então tudo o que chega é navegação disparada de DENTRO do documento.
                    isDocumentLoad = false,
                )
                currentOnDecision(decision)
                // `true` = a navegação NÃO acontece dentro do visualizador.
                return decision !is HtmlLinkDecision.Allow
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                currentOnState(HtmlDocumentState.Ready)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame != true) return
                currentOnState(
                    HtmlDocumentState.Failed(
                        HtmlDocumentError(
                            message = error?.description?.toString() ?: "Falha ao carregar",
                            code = error?.errorCode ?: 0,
                            url = request.url?.toString(),
                        ),
                    ),
                )
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                if (request?.isForMainFrame != true) return
                currentOnState(
                    HtmlDocumentState.Failed(
                        HtmlDocumentError(
                            message = errorResponse?.reasonPhrase ?: "Falha HTTP",
                            code = errorResponse?.statusCode ?: 0,
                            url = request.url?.toString(),
                        ),
                    ),
                )
            }
        }
    }

    val chromeClient = remember {
        object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress in 0..99) {
                    currentOnState(HtmlDocumentState.Loading(newProgress / 100f))
                }
            }
        }
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient = client
                webChromeClient = chromeClient
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = false
                // Cookie de terceiro desligado POR WEBVIEW — `setAcceptCookie` é global do processo
                // e desligá-lo aqui afetaria qualquer outro WebView do app.
                runCatching {
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                }
                applyHtmlDocumentSettings(this, allowJavaScript, zoom)
                webViewRef = this
            }
        },
        update = { webView ->
            // `update` roda a cada recomposição — aqui entra só o que é barato e idempotente.
            // Recarregar o documento daqui o faria reiniciar (perdendo a posição de leitura) a cada
            // mudança de estado da tela; a carga fica no efeito abaixo, com chave própria.
            applyHtmlDocumentSettings(webView, allowJavaScript, zoom)
        },
        onRelease = { webView ->
            webViewRef = null
            webView.stopLoading()
            webView.webChromeClient = null
            webView.loadUrl("about:blank")
            webView.clearHistory()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        },
    )

    // Carrega quando o documento aparece, quando a fonte muda e quando o usuário pede recarga.
    LaunchedEffect(webViewRef, source, reloadKey) {
        val webView = webViewRef ?: return@LaunchedEffect
        currentOnState(HtmlDocumentState.Loading())
        loadHtmlDocument(webView, source)
    }
}

/** Ajustes que valem no primeiro desenho e a cada mudança de [allowJavaScript]/[zoom]. */
private fun applyHtmlDocumentSettings(webView: WebView, allowJavaScript: Boolean, zoom: Float) {
    with(webView.settings) {
        javaScriptEnabled = allowJavaScript
        // Armazenamento e acesso local: um documento não precisa de nenhum deles, e cada um é um
        // caminho para conteúdo adulterado ler o que não deve.
        domStorageEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        mediaPlaybackRequiresUserGesture = true
        setGeolocationEnabled(false)
        // Zoom por gesto fica desligado: o tamanho segue o ajuste de fonte do app (textZoom), que é
        // acessibilidade de verdade — beliscar a tela não persiste entre documentos.
        builtInZoomControls = false
        displayZoomControls = false
        loadWithOverviewMode = true
        useWideViewPort = true
        textZoom = htmlDocumentZoomPercent(zoom)
    }
}

/** Carrega a fonte no WebView, com a autenticação de cada caso. */
private fun loadHtmlDocument(webView: WebView, source: HtmlDocumentSource) {
    when (source) {
        is HtmlDocumentSource.Html -> webView.loadDataWithBaseURL(
            source.baseUrl,
            source.html,
            "text/html",
            "utf-8",
            null,
        )

        is HtmlDocumentSource.Url -> if (source.headers.isEmpty()) {
            webView.loadUrl(source.url)
        } else {
            webView.loadUrl(source.url, source.headers)
        }
    }
}
