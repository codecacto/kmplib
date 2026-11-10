package br.com.codecacto.kmplib.ui.components.html

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

/**
 * Desktop (2.353.0): o Compose Desktop não tem WebView oficial (só JCEF, um Chromium de ~150 MB que
 * não entra na lib como dependência de todo app). O documento reporta [HtmlDocumentState.Failed]
 * com a URL — a tela de erro do `HtmlDocumentView` oferece abrir no navegador. Lacuna no catálogo.
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
    LaunchedEffect(source, reloadKey) {
        onState(
            HtmlDocumentState.Failed(
                HtmlDocumentError(message = "Visualização embutida indisponível no computador.", url = source.documentUrl),
            ),
        )
    }
}
