@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package br.com.codecacto.kmplib.map.osm

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.UIKitView
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

/**
 * Impl iOS dos mapas OSM: um `WKWebView` (via [UIKitView] do Compose MP) renderizando
 * o MESMO HTML do Leaflet do Android — paridade web↔mobile sem chave de API.
 *
 * A bridge JS→Kotlin é um `WKScriptMessageHandler` registrado com o nome `kmpBridge`;
 * o HTML injeta um shim (`withWebkitShim = true`) que mapeia
 * `window.kmpBridge.postMessage` → `window.webkit.messageHandlers.kmpBridge`.
 *
 * **Funcional desde já** (não é placeholder): só requer build em host macOS, como
 * todo o restante do alvo iOS da lib.
 */
@Composable
internal actual fun OsmMapWebView(
    spec: OsmMapSpec,
    modifier: Modifier,
    height: Dp,
    onMarkerClick: (String) -> Unit,
    onMapClick: ((OsmLatLng) -> Unit)?,
) {
    val markerClick = rememberUpdatedState(onMarkerClick)
    val mapClick = rememberUpdatedState(onMapClick)

    val messageHandler = remember {
        object : NSObject(), WKScriptMessageHandlerProtocol {
            override fun userContentController(
                userContentController: WKUserContentController,
                didReceiveScriptMessage: WKScriptMessage,
            ) {
                val raw = didReceiveScriptMessage.body as? String ?: return
                when (val parsed = OsmLeafletHtml.parseBridgeMessage(raw)) {
                    is OsmBridgeMessage.MarkerClick -> markerClick.value(parsed.id)
                    is OsmBridgeMessage.MapClick -> mapClick.value?.invoke(parsed.position)
                    null -> Unit
                }
            }
        }
    }

    val html = remember(spec) { OsmLeafletHtml.build(spec, withWebkitShim = true) }

    UIKitView(
        modifier = modifier.fillMaxWidth().height(height),
        factory = {
            val controller = WKUserContentController()
            controller.addScriptMessageHandler(messageHandler, name = "kmpBridge")
            val config = WKWebViewConfiguration().apply { userContentController = controller }
            WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = config).apply {
                opaque = false
                loadHTMLString(html, baseURL = NSURL(string = "https://tile.openstreetmap.org/"))
            }
        },
        update = { webView ->
            webView.loadHTMLString(html, baseURL = NSURL(string = "https://tile.openstreetmap.org/"))
        },
    )
}
