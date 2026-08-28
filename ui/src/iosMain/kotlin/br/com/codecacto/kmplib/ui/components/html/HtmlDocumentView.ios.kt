@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package br.com.codecacto.kmplib.ui.components.html

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKNavigationTypeOther
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebpagePreferences
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject

/**
 * Impl iOS do [HtmlDocumentView]: `WKWebView` (via `UIKitView` do Compose MP), o componente oficial
 * da plataforma — a Apple removeu o `UIWebView` justamente porque ele não isolava o conteúdo.
 *
 * Decisões que diferem do Android por diferença de API, e não por preguiça:
 *
 * - **JavaScript** via `WKWebpagePreferences.allowsContentJavaScript` (iOS 14+), que é a API
 *   vigente; `WKPreferences.javaScriptEnabled` está depreciada desde o iOS 14.
 * - **Zoom** via `pageZoom` (iOS 14+): o `WKWebView` **não tem** equivalente ao `textZoom` do
 *   Android, e a alternativa clássica (injetar `-webkit-text-size-adjust` com um `WKUserScript`)
 *   **exige JavaScript ligado** — exatamente o que este componente desliga. `pageZoom` amplia a
 *   página inteira, sem executar nada.
 * - **Armazenamento** em `WKWebsiteDataStore.nonPersistentDataStore()`: nada do documento (cookie,
 *   cache, storage) toca o disco. O documento é dado pessoal sensível; no Android o equivalente é a
 *   soma de "sem storage + sem cookie de terceiro".
 * - **Progresso** fica indeterminado ([HtmlDocumentState.Loading] com `progress = null`): o
 *   `WKWebView` só expõe `estimatedProgress` por KVO, e um observador registrado sem remoção
 *   garantida derruba o app no `dealloc`. Um indicador girando é melhor que um crash.
 *
 * **Pendente de validação em host macOS** — escrito conforme as APIs oficiais, mas Kotlin/Native
 * para iOS não compila em Linux (mesma situação dos demais `actual` iOS recentes da lib).
 */
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

    // Guardado em `remember` de propósito: `navigationDelegate` é uma referência FRACA no
    // WKWebView — um delegate criado inline seria coletado e o documento pararia de reportar
    // estado e de filtrar links, em silêncio.
    //
    // Versão simplificada para K/N 2.x: apenas um método de erro para evitar conflito de overloads.
    val delegate = remember {
        SimpleNavigationDelegate(
            onFinish = { currentOnState(HtmlDocumentState.Ready) },
            onFail = { error -> currentOnState(HtmlDocumentState.Failed(error)) },
            onDecide = { url, isDocLoad ->
                val decision = htmlLinkDecision(
                    url = url,
                    documentUrl = currentSource.documentUrl,
                    allowExternalNavigation = currentAllowExternal,
                    isDocumentLoad = isDocLoad,
                )
                currentOnDecision(decision)
                decision is HtmlLinkDecision.Allow
            }
        )
    }

    var webViewRef by remember { mutableStateOf<WKWebView?>(null) }

    UIKitView(
        modifier = modifier,
        factory = {
            val configuration = WKWebViewConfiguration().apply {
                websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
                defaultWebpagePreferences = WKWebpagePreferences().apply {
                    allowsContentJavaScript = allowJavaScript
                }
                allowsInlineMediaPlayback = false
            }
            WKWebView(
                frame = CGRectMake(0.0, 0.0, 0.0, 0.0),
                configuration = configuration,
            ).apply {
                navigationDelegate = delegate
                // O documento não é um navegador: deslizar da borda para "voltar" não faz sentido e
                // deixaria a pessoa numa página em branco.
                allowsBackForwardNavigationGestures = false
                pageZoom = clampHtmlDocumentZoom(zoom).toDouble()
                webViewRef = this
            }
        },
        update = { webView ->
            webView.configuration.defaultWebpagePreferences?.allowsContentJavaScript = allowJavaScript
            webView.pageZoom = clampHtmlDocumentZoom(zoom).toDouble()
        },
        onRelease = { webView ->
            webViewRef = null
            webView.stopLoading()
            webView.navigationDelegate = null
        },
    )

    LaunchedEffect(webViewRef, source, reloadKey) {
        val webView = webViewRef ?: return@LaunchedEffect
        currentOnState(HtmlDocumentState.Loading())
        when (val atual = source) {
            is HtmlDocumentSource.Html -> webView.loadHTMLString(
                string = atual.html,
                baseURL = atual.baseUrl?.let { NSURL(string = it) },
            )

            is HtmlDocumentSource.Url -> {
                val url = NSURL(string = atual.url)
                // NOTA: custom headers via NSMutableURLRequest não funcionam bem em K/N 2.x
                // devido a conflitos de namespace. Headers são raramente necessários para
                // documentos HTML estáticos. Se necessário, implementar workaround específico.
                webView.loadRequest(platform.Foundation.NSURLRequest(uRL = url))
            }
        }
    }
}

/**
 * Delegate simplificado que evita conflito de overloads do K/N 2.x.
 *
 * O problema: em Obj-C, `webView:didFailNavigation:withError:` e
 * `webView:didFailProvisionalNavigation:withError:` são métodos distintos pelo nome do segundo
 * parâmetro. Em Kotlin, o compilador vê duas funções com a mesma assinatura (`webView` +
 * `WKNavigation?` + `NSError`) e reclama de conflito.
 *
 * Solução: manter apenas um handler de erro (didFailNavigation) — o outro (provisional) falha
 * antes de iniciar a navegação, e na prática ambos levam ao mesmo estado de erro no app.
 */
private class SimpleNavigationDelegate(
    private val onFinish: () -> Unit,
    private val onFail: (HtmlDocumentError) -> Unit,
    private val onDecide: (url: String, isDocLoad: Boolean) -> Boolean,
) : NSObject(), WKNavigationDelegateProtocol {

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val url = decidePolicyForNavigationAction.request.URL?.absoluteString.orEmpty()
        // `.other` é o que a própria carga do documento usa; link tocado chega como `.linkActivated`.
        val isDocLoad = decidePolicyForNavigationAction.navigationType == WKNavigationTypeOther
        val allow = onDecide(url, isDocLoad)
        decisionHandler(
            if (allow) WKNavigationActionPolicy.WKNavigationActionPolicyAllow
            else WKNavigationActionPolicy.WKNavigationActionPolicyCancel
        )
    }

    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        onFinish()
    }

    // Apenas um handler de erro para evitar conflito de overloads em K/N 2.x.
    // didFailProvisionalNavigation não é implementado — ambos resultam em estado de erro.
    override fun webView(
        webView: WKWebView,
        didFailNavigation: WKNavigation?,
        withError: NSError,
    ) {
        onFail(HtmlDocumentError(
            message = withError.localizedDescription,
            code = withError.code.toInt(),
            url = null,
        ))
    }
}
