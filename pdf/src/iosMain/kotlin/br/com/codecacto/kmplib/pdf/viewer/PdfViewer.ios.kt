@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
@file:Suppress("ktlint:standard:no-wildcard-imports")

package br.com.codecacto.kmplib.pdf.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.*
import platform.PDFKit.*
import platform.UIKit.UIColor
import platform.darwin.NSObjectProtocol

internal actual suspend fun readLocalPdfFile(path: String): ByteArray? = withContext(Dispatchers.Default) {
    val dados = NSData.dataWithContentsOfFile(path) ?: return@withContext null
    dados.paraByteArray()
}

/**
 * Impl iOS: **PDFKit (`PDFView`)** — a view da própria Apple.
 *
 * Ela já traz, prontas e do jeito que o usuário de iPhone espera: rolagem contínua entre páginas,
 * pinça, duplo toque, seleção de texto, links clicáveis, busca e o mesmo comportamento do app
 * Arquivos. Rasterizar página a página com `CGPDFDocument` — que é o que o Android precisa fazer
 * por não ter view equivalente — daria mais código para entregar menos.
 *
 * `autoScales = true` é o que ajusta a página à largura em qualquer rotação; sem ele o documento
 * abre num zoom arbitrário.
 */
@Composable
actual fun PdfViewer(
    state: PdfViewerState,
    modifier: Modifier,
    onPageChange: ((page: Int) -> Unit)?,
) {
    val bytes = state.bytes
    val aoMudarPagina = rememberUpdatedState(onPageChange)

    val documento = remember(bytes) {
        bytes?.let { conteudo -> PDFDocument(data = conteudo.paraNSData()) }
    }

    DisposableEffect(documento, bytes) {
        if (bytes == null) {
            return@DisposableEffect onDispose { }
        }
        if (documento == null) {
            // `PDFDocument(data:)` devolve nulo para arquivo que não é PDF, truncado ou cifrado.
            state.status = PdfViewerStatus.Error(
                kind = PdfViewerError.Corrupted,
                message = state.texts.messageFor(PdfViewerError.Corrupted),
                cause = "PDFDocument(data:) devolveu nulo",
            )
            return@DisposableEffect onDispose { }
        }
        val paginas = documento.pageCount.toInt()
        state.pageCount = paginas
        state.currentPage = if (paginas > 0) 1 else 0
        state.status = if (paginas > 0) {
            PdfViewerStatus.Ready
        } else {
            PdfViewerStatus.Error(
                kind = PdfViewerError.Corrupted,
                message = state.texts.messageFor(PdfViewerError.Corrupted),
                cause = "documento sem páginas",
            )
        }
        onDispose { }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val status = state.status
        when {
            status is PdfViewerStatus.Error -> Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = status.message,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = state::retry) { Text(state.texts.retry) }
            }

            documento == null -> CircularProgressIndicator(Modifier.size(36.dp))

            else -> PdfKitView(
                document = documento,
                state = state,
                onPageChange = { pagina -> aoMudarPagina.value?.invoke(pagina) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PdfKitView(
    document: PDFDocument,
    state: PdfViewerState,
    onPageChange: (Int) -> Unit,
    modifier: Modifier,
) {
    // O PDFKit avisa a troca de página por NOTIFICAÇÃO, não por callback: é `PDFViewPageChanged`,
    // e é a única forma de saber em que página o leitor está sem consultar a view a cada quadro.
    var observador: NSObjectProtocol? = remember { null }

    DisposableEffect(document) {
        onDispose {
            observador?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
            observador = null
        }
    }

    UIKitView(
        modifier = modifier,
        factory = {
            PDFView().apply {
                setDocument(document)
                // Rolagem contínua, uma página abaixo da outra — o modo de leitura, e não o de
                // "virar página", que num material de estudo obriga a um gesto por página.
                // kPDFDisplayModeSinglePageContinuous = 1
                setDisplayMode(1L)
                // kPDFDisplayDirectionVertical = 0
                setDisplayDirection(0L)
                setAutoScales(true)
                setBackgroundColor(UIColor.grayColor)

                observador = NSNotificationCenter.defaultCenter.addObserverForName(
                    name = PDFViewPageChangedNotification,
                    `object` = this,
                    queue = NSOperationQueue.mainQueue,
                ) { _ ->
                    val atual = currentPage ?: return@addObserverForName
                    // `indexForPage` é 0-based; a tela conta a partir de 1.
                    val pagina = document.indexForPage(atual).toInt() + 1
                    state.currentPage = pagina
                    onPageChange(pagina)
                }
            }
        },
        onRelease = { view ->
            observador?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
            observador = null
            (view as? PDFView)?.setDocument(null)
        },
    )
}

/** `ByteArray` → `NSData`, sem cópia extra (o `usePinned` fixa o array durante a criação). */
private fun ByteArray.paraNSData(): NSData = usePinned { fixado ->
    NSData.create(bytes = fixado.addressOf(0), length = size.toULong())
}

/** `NSData` → `ByteArray`. */
private fun NSData.paraByteArray(): ByteArray {
    val tamanho = length.toInt()
    if (tamanho == 0) return ByteArray(0)
    val destino = ByteArray(tamanho)
    destino.usePinned { fixado ->
        platform.posix.memcpy(fixado.addressOf(0), bytes, length)
    }
    return destino
}
