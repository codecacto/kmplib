package br.com.codecacto.kmplib.pdf.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * O visualizador de PDF: **rolagem contínua, pinça para ampliar e "página X de Y"**.
 *
 * ```kotlin
 * val doc = rememberPdfViewerState(PdfSource.Url(material.url))
 *
 * Scaffold(topBar = { AppTopBar(title = material.nome, subtitle = doc.pageLabel) }) { inner ->
 *     PdfViewer(doc, Modifier.padding(inner).fillMaxSize())
 * }
 * ```
 *
 * ### Como cada plataforma desenha, e por quê
 * - **Android: `android.graphics.pdf.PdfRenderer`** — a API da própria plataforma, sem nenhuma
 *   dependência de terceiro. O Android não tem uma *view* de PDF pronta (a `androidx.pdf` ainda
 *   está em alpha), então a rolagem contínua é uma `LazyColumn` de páginas rasterizadas sob demanda.
 * - **iOS: PDFKit (`PDFView`)** — a view da Apple, que já traz rolagem contínua, pinça, seleção de
 *   texto e links. Reimplementá-la com `CGPDFDocument` seria trabalho para entregar menos.
 *
 * ### O zoom NÃO é um `graphicsLayer` por cima da lista
 * A pinça muda a **escala de rasterização** e as páginas são redesenhadas — texto continua nítido,
 * em vez de virar uma imagem esticada. E, principalmente, isso é o que mantém a **rolagem
 * funcionando enquanto ampliado**: um contêiner de zoom que consome o arrasto de um dedo (como o
 * `ZoomableBox` do `kmplib-ui` faz, corretamente, para uma foto) prenderia o leitor na página em
 * que ele ampliou. Aqui a vertical é da lista, a horizontal é do `horizontalScroll`, e a pinça só
 * captura gesto de dois dedos.
 *
 * @param onPageChange avisado quando a página visível muda (a partir de 1). Serve para o app
 *   guardar em que página o aluno parou.
 */
@Composable
expect fun PdfViewer(
    state: PdfViewerState,
    modifier: Modifier = Modifier,
    onPageChange: ((page: Int) -> Unit)? = null,
)

/** A escala mínima da pinça: a página inteira na largura da tela. */
const val PDF_MIN_ZOOM: Float = 1f

/**
 * A escala máxima da pinça.
 *
 * 4× e não mais: cada nível de zoom é um bitmap novo, e a memória cresce com o **quadrado** da
 * escala — uma página A4 a 8× numa tela de 1080 pontos passa de 300 MB e derruba o app.
 */
const val PDF_MAX_ZOOM: Float = 4f

/**
 * O passo do duplo toque, e a escala em que ler uma coluna de texto de A4 fica confortável no
 * telefone.
 */
const val PDF_DOUBLE_TAP_ZOOM: Float = 2f
