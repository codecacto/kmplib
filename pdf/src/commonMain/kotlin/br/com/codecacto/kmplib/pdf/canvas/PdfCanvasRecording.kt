package br.com.codecacto.kmplib.pdf.canvas

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

// =============================================================================================
// Pontos de entrada
// =============================================================================================

/**
 * Gera um PDF de **layout livre** e devolve os bytes. Roda fora da thread principal
 * (`Dispatchers.Default`): medir e desenhar texto é CPU pura, e um documento de algumas páginas sai
 * em milissegundos — o teto de 3 s de uma tela de pré-visualização fica muito longe.
 *
 * Android: `android.graphics.pdf.PdfDocument` (Skia). iOS: `UIGraphicsPDFRenderer` + CoreText. As
 * fontes são **embutidas** no arquivo nas duas plataformas.
 *
 * ```kotlin
 * val pdf: ByteArray = buildPdf {
 *     page(PdfPageSize.fixed(85.0, 55.0)) {                 // cartão de bolso, paisagem
 *         text("Colinha do Voto", 3.0, 3.0, PdfTextStyle(8.0, bold = true))
 *         val caixa = PdfTextStyle(14.0, bold = true, tabularNumbers = true)
 *         "1234".forEachIndexed { i, d ->
 *             rect(30.0 + i * 6.8, 10.0, 6.0, 7.0, stroke = PdfStroke(0.75))
 *             textInBox(d.toString(), 30.0 + i * 6.8, 10.0, 6.0, 7.0, caixa)
 *         }
 *         line(0.0, 50.0, 85.0, 50.0, PdfStroke(0.5, dash = PdfDash.CutLine))
 *     }
 *     page(PdfPageSize.THERMAL_58) {                        // bobina: altura = conteúdo + 3 mm
 *         var y = 5.0
 *         y = textBlock("CELULAR NÃO ENTRA NA CABINE", 5.0, y, 48.0, PdfTextStyle(9.0, bold = true))
 *     }
 * }
 * ```
 *
 * @throws PdfLayoutException conteúdo que não cabe (bobina acima do comprimento máximo).
 * @throws IllegalArgumentException documento sem página, medida inválida.
 */
suspend fun buildPdf(block: PdfDocumentScope.() -> Unit): ByteArray =
    withContext(Dispatchers.Default) { recordPdf(block).toByteArray() }

/**
 * Grava o layout **sem** gerar o arquivo — já com a altura final de cada página (útil para mostrar
 * "58 × 142 mm" antes de imprimir) — e gera depois com [RecordedPdf.toByteArray].
 *
 * Síncrono e usa a medição de texto da plataforma: chame fora da thread principal (ou use
 * [buildPdf], que já faz isso).
 */
fun recordPdf(block: PdfDocumentScope.() -> Unit): RecordedPdf =
    recordPdf(platformPdfTextMeasurer(), block)

internal fun recordPdf(measurer: PdfTextMeasurer, block: PdfDocumentScope.() -> Unit): RecordedPdf {
    val scope = PdfDocumentScope(measurer)
    scope.block()
    require(scope.pages.isNotEmpty()) { "o documento não tem nenhuma página — chame page { } ao menos uma vez" }
    return RecordedPdf(scope.pages.toList())
}

/** Layout gravado, pronto para virar arquivo. */
class RecordedPdf internal constructor(internal val recordedPages: List<RecordedPdfPage>) {

    /** Medida final de cada página, na ordem. */
    val pages: List<PdfPageInfo> =
        recordedPages.map { PdfPageInfo(it.widthPt.pointsToMm(), it.heightPt.pointsToMm()) }

    /** Gera o arquivo PDF. Síncrono — chame fora da thread principal. */
    fun toByteArray(): ByteArray = renderPdfPages(recordedPages)
}

// =============================================================================================
// Escopos
// =============================================================================================

/** Escopo do documento: cada [page] acrescenta uma página, com o tamanho que for. */
@PdfCanvasDsl
class PdfDocumentScope internal constructor(private val measurer: PdfTextMeasurer) {

    internal val pages = mutableListOf<RecordedPdfPage>()

    /** Acrescenta uma página de tamanho [size] — páginas do mesmo documento podem ter tamanhos diferentes. */
    fun page(size: PdfPageSize, block: PdfPageScope.() -> Unit) {
        val scope = PdfPageScope(size, measurer)
        scope.block()
        pages += scope.finish()
    }

    /** Atalho para página de medida fixa, em milímetros. */
    fun page(widthMm: Double, heightMm: Double, block: PdfPageScope.() -> Unit) =
        page(PdfPageSize.fixed(widthMm, heightMm), block)

    /** Mede um texto sem desenhar (para decidir o layout antes de abrir a página). */
    fun measureText(text: String, style: PdfTextStyle): PdfTextMetrics = measureWith(measurer, text, style)
}

/**
 * Escopo de UMA página. Coordenadas em milímetros a partir do canto superior esquerdo (ou da origem
 * deslocada por [offset]). As funções de texto devolvem o **y do fundo** do que desenharam, para
 * empilhar blocos sem conta à mão.
 */
@PdfCanvasDsl
class PdfPageScope internal constructor(
    private val size: PdfPageSize,
    private val measurer: PdfTextMeasurer,
) {
    private val ops = mutableListOf<PdfOp>()
    private var originXPt = 0.0
    private var originYPt = 0.0

    /** Fundo do conteúdo mais baixo já desenhado, em pt absolutos. */
    private var extentBottomPt = 0.0

    /** Largura da página. */
    val widthMm: Double get() = size.widthMm

    /** Altura da página, ou `null` quando ela é conforme o conteúdo (bobina). */
    val heightMm: Double? get() = (size.height as? PdfPageHeight.Fixed)?.heightMm

    /**
     * Ponto mais baixo desenhado até agora, **relativo à origem atual** — onde começa o próximo
     * bloco quando se empilha.
     */
    val contentBottomMm: Double get() = (extentBottomPt - originYPt).pointsToMm()

    /** Mede um texto de UMA linha no [style] dado. */
    fun measureText(text: String, style: PdfTextStyle): PdfTextMetrics = measureWith(measurer, text, style)

    /**
     * Uma linha de texto com o **topo da linha** em [yMm].
     *
     * Sem [widthMm], [xMm] é o ponto de ancoragem: início ([PdfTextAlign.Start]), centro ou fim.
     * Com [widthMm], o texto se alinha dentro de `[xMm, xMm + widthMm]` e, com
     * [PdfTextOverflow.Ellipsis], é cortado com "…" se não couber.
     *
     * Quebras de linha (`\n`) não são interpretadas aqui — use [textBlock].
     *
     * @return y do fundo da linha (topo + altura da linha).
     */
    fun text(
        text: String,
        xMm: Double,
        yMm: Double,
        style: PdfTextStyle,
        align: PdfTextAlign = PdfTextAlign.Start,
        widthMm: Double? = null,
        overflow: PdfTextOverflow = PdfTextOverflow.Ellipsis,
    ): Double {
        val font = measurer.fontMetrics(style)
        val topPt = yMm.mmToPoints()
        val baselinePt = topPt + font.ascentPt
        val boxWidthPt = widthMm?.mmToPoints()
        val content = if (boxWidthPt != null && overflow == PdfTextOverflow.Ellipsis) {
            ellipsize(text.singleLine(), boxWidthPt, style, measurer)
        } else {
            text.singleLine()
        }
        drawLinePt(content, xMm.mmToPoints(), baselinePt, boxWidthPt, style, align)
        markBottomPt(originYPt + baselinePt + font.descentPt)
        return (baselinePt + font.descentPt).pointsToMm()
    }

    /**
     * Parágrafo com **quebra por palavra** dentro de [widthMm]. Respeita `\n`; palavra maior que a
     * largura é partida por caractere. Passando de [maxLines], a última linha termina em "…".
     *
     * @param lineSpacing entrelinha como múltiplo da altura da linha (1,0 = linhas coladas).
     * @return y do fundo da última linha.
     */
    fun textBlock(
        text: String,
        xMm: Double,
        yMm: Double,
        widthMm: Double,
        style: PdfTextStyle,
        align: PdfTextAlign = PdfTextAlign.Start,
        lineSpacing: Double = 1.15,
        maxLines: Int = Int.MAX_VALUE,
    ): Double {
        require(widthMm > 0.0) { "largura do parágrafo precisa ser positiva" }
        require(lineSpacing > 0.0) { "entrelinha precisa ser positiva" }
        require(maxLines >= 1) { "maxLines precisa ser ≥ 1" }
        val font = measurer.fontMetrics(style)
        val widthPt = widthMm.mmToPoints()
        val lines = wrapText(text, widthPt, style, measurer, maxLines)
        val advancePt = font.lineHeightPt * lineSpacing
        var baselinePt = yMm.mmToPoints() + font.ascentPt
        lines.forEachIndexed { index, line ->
            if (index > 0) baselinePt += advancePt
            drawLinePt(line, xMm.mmToPoints(), baselinePt, widthPt, style, align)
        }
        val bottomPt = baselinePt + font.descentPt
        markBottomPt(originYPt + bottomPt)
        return bottomPt.pointsToMm()
    }

    /**
     * Uma linha de texto dentro da caixa ([xMm], [yMm], [widthMm], [heightMm]) — o caso do dígito na
     * caixa. [PdfVerticalAlign.Center] centraliza pela **altura de versal**, que é o centro ótico de
     * algarismos e maiúsculas. Texto mais largo que a caixa é cortado com "…".
     */
    fun textInBox(
        text: String,
        xMm: Double,
        yMm: Double,
        widthMm: Double,
        heightMm: Double,
        style: PdfTextStyle,
        align: PdfTextAlign = PdfTextAlign.Center,
        verticalAlign: PdfVerticalAlign = PdfVerticalAlign.Center,
    ) {
        require(widthMm > 0.0 && heightMm > 0.0) { "caixa de texto precisa de medida positiva" }
        val font = measurer.fontMetrics(style)
        val topPt = yMm.mmToPoints()
        val boxHeightPt = heightMm.mmToPoints()
        val baselinePt = when (verticalAlign) {
            PdfVerticalAlign.Top -> topPt + font.ascentPt
            PdfVerticalAlign.Center -> topPt + (boxHeightPt + font.capHeightPt) / 2.0
            PdfVerticalAlign.Bottom -> topPt + boxHeightPt - font.descentPt
        }
        val widthPt = widthMm.mmToPoints()
        drawLinePt(ellipsize(text.singleLine(), widthPt, style, measurer), xMm.mmToPoints(), baselinePt, widthPt, style, align)
        markBottomPt(originYPt + max(topPt + boxHeightPt, baselinePt + font.descentPt))
    }

    /** Segmento de reta. Com `stroke.dash`, tracejado (linha de corte: [PdfDash.CutLine]). */
    fun line(x1Mm: Double, y1Mm: Double, x2Mm: Double, y2Mm: Double, stroke: PdfStroke = PdfStroke()) {
        val x1 = originXPt + x1Mm.mmToPoints()
        val y1 = originYPt + y1Mm.mmToPoints()
        val x2 = originXPt + x2Mm.mmToPoints()
        val y2 = originYPt + y2Mm.mmToPoints()
        ops += PdfOp.Line(x1, y1, x2, y2, stroke)
        markBottomPt(max(y1, y2) + stroke.widthPt / 2.0)
    }

    /**
     * Retângulo. [stroke] `null` = sem contorno; [fill] `null` = sem preenchimento (os dois `null`
     * não desenha nada, mas ainda conta para a altura de uma bobina — ver [markExtent]).
     */
    fun rect(
        xMm: Double,
        yMm: Double,
        widthMm: Double,
        heightMm: Double,
        stroke: PdfStroke? = PdfStroke(),
        fill: PdfColor? = null,
        cornerRadiusMm: Double = 0.0,
    ) {
        require(widthMm >= 0.0 && heightMm >= 0.0) { "retângulo com medida negativa" }
        require(cornerRadiusMm >= 0.0) { "raio de canto negativo" }
        val x = originXPt + xMm.mmToPoints()
        val y = originYPt + yMm.mmToPoints()
        val h = heightMm.mmToPoints()
        if (stroke != null || fill != null) {
            ops += PdfOp.Rect(x, y, widthMm.mmToPoints(), h, stroke, fill, cornerRadiusMm.mmToPoints())
        }
        markBottomPt(y + h + (stroke?.widthPt ?: 0.0) / 2.0)
    }

    /**
     * Imagem PNG/JPEG na caixa dada. Bytes que não decodificam **não** derrubam o documento: a caixa
     * fica em branco (imagem é enfeite opcional; o papel tem de sair).
     */
    fun image(
        bytes: ByteArray,
        xMm: Double,
        yMm: Double,
        widthMm: Double,
        heightMm: Double,
        fit: PdfImageFit = PdfImageFit.Contain,
    ) {
        require(widthMm > 0.0 && heightMm > 0.0) { "caixa da imagem precisa de medida positiva" }
        val x = originXPt + xMm.mmToPoints()
        val y = originYPt + yMm.mmToPoints()
        val h = heightMm.mmToPoints()
        if (bytes.isNotEmpty()) ops += PdfOp.Image(bytes, x, y, widthMm.mmToPoints(), h, fit)
        markBottomPt(y + h)
    }

    /**
     * Desloca a origem por ([dxMm], [dyMm]) dentro de [block] — desenhar a mesma peça em várias
     * células (8 recortáveis numa A4) sem somar deslocamento em cada chamada.
     */
    fun offset(dxMm: Double, dyMm: Double, block: PdfPageScope.() -> Unit) {
        val previousX = originXPt
        val previousY = originYPt
        originXPt += dxMm.mmToPoints()
        originYPt += dyMm.mmToPoints()
        try {
            block()
        } finally {
            originXPt = previousX
            originYPt = previousY
        }
    }

    /**
     * Garante que a página vá **pelo menos** até [yMm] (relativo à origem atual), sem desenhar nada.
     * Só tem efeito em página conforme o conteúdo — para reservar um espaço em branco no fim.
     */
    fun markExtent(yMm: Double) = markBottomPt(originYPt + yMm.mmToPoints())

    // --- interno --------------------------------------------------------------------------------

    private fun markBottomPt(absoluteYPt: Double) {
        extentBottomPt = max(extentBottomPt, absoluteYPt)
    }

    /** Desenha uma linha já quebrada. [xPt]/[baselinePt] relativos à origem. */
    private fun drawLinePt(
        text: String,
        xPt: Double,
        baselinePt: Double,
        boxWidthPt: Double?,
        style: PdfTextStyle,
        align: PdfTextAlign,
    ) {
        if (text.isEmpty()) return
        val widthPt = measurer.widthPt(text, style)
        val leftPt = alignedLeftPt(xPt, widthPt, boxWidthPt, align)
        ops += PdfOp.Text(text, originXPt + leftPt, originYPt + baselinePt, style)
    }

    internal fun finish(): RecordedPdfPage {
        val widthPt = size.widthMm.mmToPoints()
        val heightPt = when (val h = size.height) {
            is PdfPageHeight.Fixed -> h.heightMm.mmToPoints()
            is PdfPageHeight.FitContent -> fitContentHeightPt(extentBottomPt, h)
        }
        return RecordedPdfPage(widthPt, heightPt, ops.toList())
    }
}

// =============================================================================================
// Regras puras (testadas em commonTest)
// =============================================================================================

/** Altura de uma página conforme o conteúdo. */
internal fun fitContentHeightPt(contentBottomPt: Double, spec: PdfPageHeight.FitContent): Double {
    val wantedMm = contentBottomPt.pointsToMm() + spec.bottomPaddingMm
    if (wantedMm > spec.maxHeightMm + 1e-6) {
        throw PdfLayoutException(
            "o conteúdo precisa de ${formatMm(wantedMm)} mm e a página vai até ${formatMm(spec.maxHeightMm)} mm",
        )
    }
    return max(wantedMm, spec.minHeightMm).mmToPoints()
}

private fun formatMm(value: Double): String {
    val tenths = kotlin.math.round(value * 10).toLong()
    return "${tenths / 10},${tenths % 10}"
}

/** x da borda esquerda do texto, dada a ancoragem. */
internal fun alignedLeftPt(xPt: Double, textWidthPt: Double, boxWidthPt: Double?, align: PdfTextAlign): Double =
    if (boxWidthPt == null) {
        when (align) {
            PdfTextAlign.Start -> xPt
            PdfTextAlign.Center -> xPt - textWidthPt / 2.0
            PdfTextAlign.End -> xPt - textWidthPt
        }
    } else {
        when (align) {
            PdfTextAlign.Start -> xPt
            PdfTextAlign.Center -> xPt + (boxWidthPt - textWidthPt) / 2.0
            PdfTextAlign.End -> xPt + boxWidthPt - textWidthPt
        }
    }

private const val ELLIPSIS = "…"

private fun String.singleLine(): String = replace('\n', ' ').replace('\r', ' ')

/** Corta [text] com "…" para caber em [maxWidthPt]; devolve inteiro se já cabe. */
internal fun ellipsize(text: String, maxWidthPt: Double, style: PdfTextStyle, measurer: PdfTextMeasurer): String {
    if (measurer.widthPt(text, style) <= maxWidthPt) return text
    // Busca binária pelo maior prefixo que cabe com a reticência.
    var low = 0
    var high = text.length
    while (low < high) {
        val mid = (low + high + 1) / 2
        val candidate = text.substring(0, mid).trimEnd() + ELLIPSIS
        if (measurer.widthPt(candidate, style) <= maxWidthPt) low = mid else high = mid - 1
    }
    if (low == 0) return if (measurer.widthPt(ELLIPSIS, style) <= maxWidthPt) ELLIPSIS else ""
    return text.substring(0, low).trimEnd() + ELLIPSIS
}

/**
 * Quebra [text] em linhas que cabem em [maxWidthPt]: por palavra, respeitando `\n`, partindo por
 * caractere a palavra maior que a linha. Acima de [maxLines], a última linha ganha "…".
 */
internal fun wrapText(
    text: String,
    maxWidthPt: Double,
    style: PdfTextStyle,
    measurer: PdfTextMeasurer,
    maxLines: Int = Int.MAX_VALUE,
): List<String> {
    val lines = mutableListOf<String>()
    for (paragraph in text.replace("\r\n", "\n").split('\n')) {
        val words = paragraph.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) {
            lines += ""
            continue
        }
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (measurer.widthPt(candidate, style) <= maxWidthPt) {
                current = candidate
                continue
            }
            if (current.isNotEmpty()) lines += current
            // Palavra sozinha maior que a linha: parte por caractere.
            var rest = word
            while (measurer.widthPt(rest, style) > maxWidthPt && rest.length > 1) {
                var cut = rest.length - 1
                while (cut > 1 && measurer.widthPt(rest.substring(0, cut), style) > maxWidthPt) cut--
                lines += rest.substring(0, cut)
                rest = rest.substring(cut)
            }
            current = rest
        }
        lines += current
    }
    if (lines.size <= maxLines) return lines
    val kept = lines.take(maxLines).toMutableList()
    kept[kept.lastIndex] = ellipsizeForced(kept.last(), maxWidthPt, style, measurer)
    return kept
}

/** Reticência no fim SEMPRE — a linha foi cortada por `maxLines`, mesmo que ela própria caiba. */
private fun ellipsizeForced(text: String, maxWidthPt: Double, style: PdfTextStyle, measurer: PdfTextMeasurer): String {
    var base = text.trimEnd()
    while (base.isNotEmpty() && measurer.widthPt(base + ELLIPSIS, style) > maxWidthPt) {
        base = base.dropLast(1).trimEnd()
    }
    return base + ELLIPSIS
}

/**
 * Retângulo de desenho de uma imagem [imageWidth]×[imageHeight] na caixa dada, conforme [fit].
 * Devolve `(x, y, largura, altura)`. Em [PdfImageFit.Cover] o retângulo passa da caixa — quem
 * desenha recorta pela caixa.
 */
internal fun imageDrawRect(
    imageWidth: Double,
    imageHeight: Double,
    boxX: Double,
    boxY: Double,
    boxWidth: Double,
    boxHeight: Double,
    fit: PdfImageFit,
): DoubleArray {
    if (imageWidth <= 0.0 || imageHeight <= 0.0) return doubleArrayOf(boxX, boxY, 0.0, 0.0)
    if (fit == PdfImageFit.Fill) return doubleArrayOf(boxX, boxY, boxWidth, boxHeight)
    val scale = if (fit == PdfImageFit.Contain) {
        min(boxWidth / imageWidth, boxHeight / imageHeight)
    } else {
        max(boxWidth / imageWidth, boxHeight / imageHeight)
    }
    val w = imageWidth * scale
    val h = imageHeight * scale
    return doubleArrayOf(boxX + (boxWidth - w) / 2.0, boxY + (boxHeight - h) / 2.0, w, h)
}

private fun measureWith(measurer: PdfTextMeasurer, text: String, style: PdfTextStyle): PdfTextMetrics {
    val font = measurer.fontMetrics(style)
    return PdfTextMetrics(
        widthMm = measurer.widthPt(text.singleLine(), style).pointsToMm(),
        ascentMm = font.ascentPt.pointsToMm(),
        descentMm = font.descentPt.pointsToMm(),
        capHeightMm = font.capHeightPt.pointsToMm(),
    )
}

// =============================================================================================
// Modelo gravado e fronteira com a plataforma
// =============================================================================================

/** Métrica de fonte em pt (positivos). */
internal data class PdfFontMetricsPt(val ascentPt: Double, val descentPt: Double, val capHeightPt: Double) {
    val lineHeightPt: Double get() = ascentPt + descentPt
}

/** Medição de texto — a da plataforma em produção, uma falsa nos testes. */
internal interface PdfTextMeasurer {
    fun widthPt(text: String, style: PdfTextStyle): Double
    fun fontMetrics(style: PdfTextStyle): PdfFontMetricsPt
}

/** Operação de desenho, em pontos absolutos da página (origem topo-esquerda). */
internal sealed interface PdfOp {
    data class Text(val text: String, val xPt: Double, val baselinePt: Double, val style: PdfTextStyle) : PdfOp
    data class Line(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val stroke: PdfStroke) : PdfOp
    data class Rect(
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
        val stroke: PdfStroke?,
        val fill: PdfColor?,
        val radius: Double,
    ) : PdfOp

    class Image(
        val bytes: ByteArray,
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
        val fit: PdfImageFit,
    ) : PdfOp
}

internal data class RecordedPdfPage(val widthPt: Double, val heightPt: Double, val ops: List<PdfOp>)

/** Medição da plataforma (uma instância por documento — não é compartilhada entre threads). */
internal expect fun platformPdfTextMeasurer(): PdfTextMeasurer

/** Gera o arquivo. */
internal expect fun renderPdfPages(pages: List<RecordedPdfPage>): ByteArray
