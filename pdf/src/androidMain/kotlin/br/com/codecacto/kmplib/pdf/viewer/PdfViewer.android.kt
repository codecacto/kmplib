package br.com.codecacto.kmplib.pdf.viewer

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Impl Android: `PdfRenderer` da plataforma + `LazyColumn`.
 *
 * O Android não oferece uma view de PDF (a `androidx.pdf` segue em alpha), então a rolagem contínua
 * é uma lista de páginas rasterizadas **sob demanda**: só a página que entra na tela vira bitmap, e
 * a que sai é descartada pela própria lista. Um material de 200 páginas abre igual a um de 3.
 */
@Composable
actual fun PdfViewer(
    state: PdfViewerState,
    modifier: Modifier,
    onPageChange: ((page: Int) -> Unit)?,
) {
    val context = LocalContext.current
    val bytes = state.bytes
    var documento by remember(bytes) { mutableStateOf<AndroidPdfDocument?>(null) }

    DisposableEffect(bytes) {
        onDispose { documento?.close() }
    }

    LaunchedEffect(bytes) {
        documento?.close()
        documento = null
        if (bytes == null) return@LaunchedEffect
        try {
            val aberto = AndroidPdfDocument.open(bytes, context.pdfTempDir())
            documento = aberto
            state.pageCount = aberto.pageCount
            state.currentPage = if (aberto.pageCount > 0) 1 else 0
            state.status = if (aberto.pageCount > 0) {
                PdfViewerStatus.Ready
            } else {
                PdfViewerStatus.Error(
                    kind = PdfViewerError.Corrupted,
                    message = state.texts.messageFor(PdfViewerError.Corrupted),
                    cause = "documento sem páginas",
                )
            }
        } catch (e: PdfLoadException) {
            state.status = PdfViewerStatus.Error(e.kind, state.texts.messageFor(e.kind), e.message)
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val doc = documento
        when {
            state.status is PdfViewerStatus.Error -> PdfErrorPanel(
                status = state.status as PdfViewerStatus.Error,
                retryLabel = state.texts.retry,
                onRetry = state::retry,
            )

            doc == null -> CircularProgressIndicator(Modifier.size(36.dp))

            else -> PdfPages(
                document = doc,
                state = state,
                onPageChange = onPageChange,
            )
        }
    }
}

@Composable
private fun PdfPages(
    document: AndroidPdfDocument,
    state: PdfViewerState,
    onPageChange: ((page: Int) -> Unit)?,
) {
    val listState = rememberLazyListState()
    val horizontal = rememberScrollState()
    var zoom by remember(document) { mutableFloatStateOf(PDF_MIN_ZOOM) }

    // A página "atual" é a primeira visível. Derivado para não recompor a cada pixel de rolagem.
    val paginaVisivel by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex + 1 }
    }
    LaunchedEffect(listState, onPageChange) {
        snapshotFlow { paginaVisivel }.collect { pagina ->
            state.currentPage = pagina.coerceIn(1, document.pageCount)
            onPageChange?.invoke(state.currentPage)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // ⚠️ A pinça consome **só o gesto de dois dedos**. Consumir o arrasto de um dedo (que é
            // o certo num visualizador de FOTO, e o que o `ZoomableBox` do `kmplib-ui` faz) travaria
            // a rolagem entre páginas assim que o leitor ampliasse — o defeito que este desenho evita.
            .pointerInput(document) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val evento = awaitPointerEvent(PointerEventPass.Initial)
                        if (evento.changes.count { it.pressed } > 1) {
                            val fator = evento.calculateZoom()
                            if (fator != 1f) {
                                zoom = (zoom * fator).coerceIn(PDF_MIN_ZOOM, PDF_MAX_ZOOM)
                                evento.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (evento.changes.any { it.pressed })
                }
            }
            .pointerInput(document) {
                detectTapGestures(
                    onDoubleTap = {
                        zoom = if (zoom > PDF_MIN_ZOOM + ZOOM_EPSILON) {
                            PDF_MIN_ZOOM
                        } else {
                            PDF_DOUBLE_TAP_ZOOM
                        }
                    },
                )
            },
    ) {
        val larguraViewportPx = constraints.maxWidth
        val larguraPaginaPx = (larguraViewportPx * zoom).toInt().coerceAtLeast(1)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(document.pageCount) { indice ->
                PdfPage(
                    document = document,
                    index = indice,
                    widthPx = larguraPaginaPx,
                    ratio = document.pageRatios.getOrElse(indice) { A4_RATIO },
                    horizontalScroll = horizontal,
                    zoomed = zoom > PDF_MIN_ZOOM + ZOOM_EPSILON,
                )
            }
        }
    }
}

@Composable
private fun PdfPage(
    document: AndroidPdfDocument,
    index: Int,
    widthPx: Int,
    ratio: Float,
    horizontalScroll: androidx.compose.foundation.ScrollState,
    zoomed: Boolean,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, document, index, widthPx) {
        value = document.renderPage(index, widthPx)
    }

    val conteudo = Modifier
        .width(with(androidx.compose.ui.platform.LocalDensity.current) { widthPx.toDp() })
        .aspectRatio(1f / ratio.coerceAtLeast(0.01f))
        .background(MaterialTheme.colorScheme.surface)

    Box(
        modifier = if (zoomed) {
            Modifier.fillMaxWidth().horizontalScroll(horizontalScroll)
        } else {
            Modifier.fillMaxWidth()
        },
    ) {
        val atual = bitmap
        if (atual == null) {
            // O espaço da página já está reservado pela proporção lida na abertura — sem isto a
            // lista daria altura zero a tudo e composaria o documento inteiro de uma vez.
            Box(conteudo, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
        } else {
            Image(
                bitmap = atual,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = conteudo,
            )
        }
    }
}

@Composable
private fun PdfErrorPanel(
    status: PdfViewerStatus.Error,
    retryLabel: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = status.message,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) { Text(retryLabel) }
    }
}

/**
 * O temporário do `PdfRenderer` vai para uma subpasta própria do `cacheDir`.
 *
 * ⚠️ Pasta nova em `cacheDir` **não** entra sozinha no `FileProvider` da lib (`kmplib_file_paths`
 * cobre `cache/photos`, `cache/videos` e `cache/shared_files`) — e não precisa: este arquivo nunca
 * é compartilhado, é insumo do renderizador e morre no `close()`.
 */
private fun Context.pdfTempDir(): java.io.File =
    java.io.File(cacheDir, "kmplib_pdfviewer").apply { mkdirs() }

/** Proporção de A4 (297 ÷ 210), usada só quando a página não informa a dela. */
private const val A4_RATIO = 1.414f

/** Folga de ponto flutuante: a pinça deixa 1.0000001 e "voltou ao normal" precisa contar como 1×. */
private const val ZOOM_EPSILON = 0.01f
