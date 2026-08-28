package br.com.codecacto.kmplib.map.osm

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Impl Android dos mapas OSM: um `android.webkit.WebView` renderizando o HTML do
 * Leaflet (mesmos tiles OpenStreetMap da web). A bridge JS→Kotlin é um
 * `@JavascriptInterface` injetado com o nome `kmpBridge`.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun OsmMapWebView(
    spec: OsmMapSpec,
    modifier: Modifier,
    height: Dp,
    onMarkerClick: (String) -> Unit,
    onMapClick: ((OsmLatLng) -> Unit)?,
) {
    // Mantém os callbacks atuais sem recriar a bridge a cada recomposição.
    val markerClick = rememberUpdatedState(onMarkerClick)
    val mapClick = rememberUpdatedState(onMapClick)

    val bridge = remember {
        object {
            @JavascriptInterface
            fun postMessage(message: String) {
                when (val parsed = OsmLeafletHtml.parseBridgeMessage(message)) {
                    is OsmBridgeMessage.MarkerClick -> markerClick.value(parsed.id)
                    is OsmBridgeMessage.MapClick -> mapClick.value?.invoke(parsed.position)
                    null -> Unit
                }
            }
        }
    }

    val html = remember(spec) { OsmLeafletHtml.build(spec, withWebkitShim = false) }

    AndroidView(
        modifier = modifier.fillMaxWidth().height(height),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                addJavascriptInterface(bridge, "kmpBridge")
                loadDataWithBaseURL(
                    "https://tile.openstreetmap.org/",
                    html,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
        update = { webView ->
            // Recarrega quando a spec muda (novos pins / centro / modo).
            webView.loadDataWithBaseURL(
                "https://tile.openstreetmap.org/",
                html,
                "text/html",
                "utf-8",
                null,
            )
        },
    )
}
