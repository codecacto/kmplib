package br.com.codecacto.kmplib.pdf

import kotlinx.serialization.Serializable

/** Alinhamento do conteúdo de uma faixa do timbrado ([PdfLetterhead]). */
@Serializable
enum class PdfLetterheadAlign {
    /** Imagem à esquerda e o texto ao lado dela, alinhado à esquerda. */
    LEFT,

    /** Imagem centralizada e o texto centralizado embaixo dela. */
    CENTER,

    /**
     * Imagem à direita e o texto ao lado dela (à esquerda da imagem), alinhado à direita — espelho
     * de [LEFT] (2.378.0).
     */
    RIGHT,
}

/**
 * **Timbrado** do PDF (2.377.0): cabeçalho e rodapé próprios do emissor (a clínica, o escritório),
 * desenhados em **toda página** do documento — imagem (PNG/JPEG) e/ou texto em cada faixa.
 *
 * Cada faixa tem **altura máxima fixa** ([PdfLetterheadLayout.HEADER_MAX_HEIGHT] e
 * [PdfLetterheadLayout.FOOTER_MAX_HEIGHT]); a imagem se encaixa sem distorcer (nunca ampliada além
 * de 1 px = 1 pt) e o texto quebra por palavra até o limite de linhas, com "…" no que sobra. A área
 * do conteúdo encolhe para caber entre as duas faixas: **o conteúdo nunca passa por baixo delas**.
 *
 * Imagem fora do formato (nem PNG nem JPEG), vazia, ilegível ou maior que
 * [PdfLetterheadLayout.MAX_IMAGE_BYTES] é **ignorada** (a faixa fica só com o texto) — o PDF sai do
 * mesmo jeito. A decodificação é sub-amostrada (Android `inSampleSize`, iOS ImageIO) até no máximo
 * [PdfLetterheadLayout.MAX_DECODE_PX] no maior lado: um PNG de 8000 px não estoura a memória.
 *
 * ```kotlin
 * val timbrado = PdfLetterhead(headerImage = logoPng, headerText = "Clínica Royal · CRM 1234", footerText = "Rua X, 100 · (65) 3000-0000")
 * exportReportSummaryPdf(summary, company, texts, generatedAt, letterhead = timbrado)
 * ```
 *
 * @property replacesDefaultHeader `true` = o timbrado **substitui** o bloco da empresa do cabeçalho
 *   padrão (logo, nome e contatos de [DocumentPdfCompany]); o título e o período continuam. Só vale
 *   quando o cabeçalho do timbrado tem imagem ou texto — sem isso, o cabeçalho padrão fica.
 */
@Serializable
data class PdfLetterhead(
    /** Imagem do cabeçalho em PNG ou JPEG (bytes do arquivo). */
    val headerImage: ByteArray? = null,
    /** Texto do cabeçalho; `\n` força quebra. Até [PdfLetterheadLayout.HEADER_MAX_LINES] linhas. */
    val headerText: String? = null,
    val headerAlign: PdfLetterheadAlign = PdfLetterheadAlign.LEFT,
    val replacesDefaultHeader: Boolean = false,
    /** Imagem do rodapé em PNG ou JPEG (bytes do arquivo). */
    val footerImage: ByteArray? = null,
    /** Texto do rodapé; `\n` força quebra. Até [PdfLetterheadLayout.FOOTER_MAX_LINES] linhas. */
    val footerText: String? = null,
    val footerAlign: PdfLetterheadAlign = PdfLetterheadAlign.CENTER,
) {
    /** O cabeçalho tem algo a desenhar (imagem não vazia ou texto não branco). */
    val hasHeader: Boolean get() = headerImage.hasBytes() || !headerText.isNullOrBlank()

    /** O rodapé tem algo a desenhar (imagem não vazia ou texto não branco). */
    val hasFooter: Boolean get() = footerImage.hasBytes() || !footerText.isNullOrBlank()

    /** O timbrado tem alguma faixa. Timbrado vazio = PDF idêntico ao sem timbrado. */
    val isEmpty: Boolean get() = !hasHeader && !hasFooter

    /** O bloco da empresa do cabeçalho padrão sai (ver [replacesDefaultHeader]). */
    val hidesDefaultHeader: Boolean get() = replacesDefaultHeader && hasHeader

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PdfLetterhead) return false
        return headerImage.contentEqualsNullable(other.headerImage) &&
            headerText == other.headerText &&
            headerAlign == other.headerAlign &&
            replacesDefaultHeader == other.replacesDefaultHeader &&
            footerImage.contentEqualsNullable(other.footerImage) &&
            footerText == other.footerText &&
            footerAlign == other.footerAlign
    }

    override fun hashCode(): Int {
        var result = headerImage?.contentHashCode() ?: 0
        result = 31 * result + (headerText?.hashCode() ?: 0)
        result = 31 * result + headerAlign.hashCode()
        result = 31 * result + replacesDefaultHeader.hashCode()
        result = 31 * result + (footerImage?.contentHashCode() ?: 0)
        result = 31 * result + (footerText?.hashCode() ?: 0)
        result = 31 * result + footerAlign.hashCode()
        return result
    }

    /** Sem os bytes das imagens (só o tamanho) — log não vira despejo de arquivo. */
    override fun toString(): String =
        "PdfLetterhead(headerImage=${headerImage?.size?.let { "$it bytes" }}, headerText=$headerText, " +
            "headerAlign=$headerAlign, replacesDefaultHeader=$replacesDefaultHeader, " +
            "footerImage=${footerImage?.size?.let { "$it bytes" }}, footerText=$footerText, footerAlign=$footerAlign)"
}

private fun ByteArray?.hasBytes(): Boolean = this != null && this.isNotEmpty()

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean =
    if (this == null) other == null else other != null && this.contentEquals(other)

/** Formato da imagem do timbrado, pela assinatura do arquivo (não pela extensão). */
internal enum class LetterheadImageFormat { PNG, JPEG }

/** Retângulo em pt, origem no topo-esquerda da página (a mesma do `PdfDocument` e do UIKit). */
internal data class LetterheadRect(val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * Uma faixa do timbrado já medida, em coordenadas da FAIXA (y = 0 no topo dela) — o renderer só
 * soma o topo da faixa na página.
 */
internal data class LetterheadBandLayout(
    val height: Float,
    /** Onde a imagem entra (já com a proporção dela); `null` = sem imagem. */
    val image: LetterheadRect?,
    /** Linhas do texto já quebradas e cortadas. */
    val lines: List<String>,
    /** Baseline de cada linha, relativa ao topo da faixa. */
    val baselines: List<Float>,
    /**
     * X da âncora do texto: a borda esquerda ([align] LEFT), o centro ([align] CENTER) ou a borda
     * direita ([align] RIGHT).
     */
    val textAnchorX: Float,
    val align: PdfLetterheadAlign,
    val fontSize: Float,
)

/**
 * A página com timbrado: onde cada faixa entra e entre que y o conteúdo pode desenhar. Em pt.
 */
internal data class LetterheadPageFrame(
    val header: LetterheadBandLayout?,
    val headerTop: Float,
    val footer: LetterheadBandLayout?,
    val footerTop: Float,
    /** Primeiro y livre para o conteúdo, em toda página. */
    val contentTop: Float,
    /** Último y que o conteúdo pode ocupar, em toda página. */
    val contentBottom: Float,
    /** Linha fina sob o cabeçalho (`null` sem cabeçalho). */
    val headerSeparatorY: Float?,
    /** Linha fina sobre o rodapé (`null` sem rodapé). */
    val footerSeparatorY: Float?,
    /** Baseline do texto de rodapé do documento ("Gerado em…"), dentro da margem inferior. */
    val documentFooterBaseline: Float,
)

/** As medidas de uma faixa (cabeçalho ou rodapé). */
internal data class LetterheadBandSpec(
    val maxHeight: Float,
    val fontSize: Float,
    val lineHeight: Float,
    val maxLines: Int,
    /** Teto da imagem em altura (antes do que o texto ocupa). */
    val imageMaxHeight: Float,
    /** Teto da imagem em largura quando o texto vai ao lado dela (LEFT). */
    val sideImageMaxWidth: Float,
)

/**
 * A regra de layout do timbrado — escrita uma vez, em código comum, para Android e iOS darem a
 * mesma página. O renderer de cada plataforma só mede texto (`Paint.measureText` / CoreText) e
 * decodifica a imagem.
 */
object PdfLetterheadLayout {
    /** Altura máxima do cabeçalho do timbrado (pt). */
    const val HEADER_MAX_HEIGHT: Float = 72f

    /** Altura máxima do rodapé do timbrado (pt). */
    const val FOOTER_MAX_HEIGHT: Float = 44f

    /** Linhas de texto no cabeçalho. */
    const val HEADER_MAX_LINES: Int = 3

    /** Linhas de texto no rodapé. */
    const val FOOTER_MAX_LINES: Int = 2

    /** Maior arquivo de imagem aceito (bytes). Acima disso a imagem é ignorada. */
    const val MAX_IMAGE_BYTES: Int = 8 * 1024 * 1024

    /** Maior lado da imagem decodificada (px): a decodificação é sub-amostrada até aqui. */
    const val MAX_DECODE_PX: Int = 1600

    /** Pixels por pt da imagem embutida (≈ 216 dpi): nítida no papel, sem inflar o arquivo. */
    internal const val RENDER_SCALE: Float = 3f

    /** Distância entre a faixa e o conteúdo; a linha separadora fica no meio dela. */
    internal const val BAND_GAP: Float = 12f

    /** Espaço entre a imagem e o texto da mesma faixa. */
    internal const val INNER_GAP: Float = 8f

    /** Menor altura que a imagem ainda ganha numa faixa CENTER com texto cheio. */
    internal const val MIN_IMAGE_HEIGHT: Float = 16f

    internal val HEADER = LetterheadBandSpec(
        maxHeight = HEADER_MAX_HEIGHT,
        fontSize = 9.5f,
        lineHeight = 12f,
        maxLines = HEADER_MAX_LINES,
        imageMaxHeight = HEADER_MAX_HEIGHT,
        sideImageMaxWidth = 200f,
    )

    internal val FOOTER = LetterheadBandSpec(
        maxHeight = FOOTER_MAX_HEIGHT,
        fontSize = 8f,
        lineHeight = 10f,
        maxLines = FOOTER_MAX_LINES,
        imageMaxHeight = FOOTER_MAX_HEIGHT,
        sideImageMaxWidth = 160f,
    )

    /** PNG (`89 50 4E 47 0D 0A 1A 0A`) ou JPEG (`FF D8 FF`); qualquer outra coisa = `null`. */
    internal fun detectFormat(bytes: ByteArray?): LetterheadImageFormat? {
        if (bytes == null || bytes.size < 4) return null
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        if (bytes.size >= png.size && png.indices.all { bytes[it] == png[it] }) return LetterheadImageFormat.PNG
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return LetterheadImageFormat.JPEG
        return null
    }

    /** A imagem pode ir à decodificação: PNG/JPEG e dentro de [MAX_IMAGE_BYTES]. */
    internal fun isAcceptedImage(bytes: ByteArray?): Boolean =
        bytes != null && bytes.size <= MAX_IMAGE_BYTES && detectFormat(bytes) != null

    /**
     * `inSampleSize` (potência de 2) para decodificar [widthPx]×[heightPx] com o maior lado ainda
     * ≥ [maxPx] — a redução exata vem depois, sem perder nitidez (técnica oficial do Android,
     * "Loading large bitmaps efficiently").
     */
    internal fun sampleSize(widthPx: Int, heightPx: Int, maxPx: Int = MAX_DECODE_PX): Int {
        if (widthPx <= 0 || heightPx <= 0 || maxPx <= 0) return 1
        val longest = maxOf(widthPx, heightPx)
        var sample = 1
        while (longest / (sample * 2) >= maxPx) sample *= 2
        return sample
    }

    /** Reduz [widthPx]×[heightPx] proporcionalmente até o maior lado caber em [maxPx]; nunca amplia. */
    internal fun boundedSize(widthPx: Int, heightPx: Int, maxPx: Int = MAX_DECODE_PX): Pair<Int, Int> {
        if (widthPx <= 0 || heightPx <= 0) return widthPx to heightPx
        val longest = maxOf(widthPx, heightPx)
        if (longest <= maxPx) return widthPx to heightPx
        val ratio = maxPx.toFloat() / longest
        return (widthPx * ratio).toInt().coerceAtLeast(1) to (heightPx * ratio).toInt().coerceAtLeast(1)
    }

    /**
     * Tamanho exibido (pt) da imagem de [widthPx]×[heightPx] dentro de [maxWidth]×[maxHeight]:
     * proporção mantida, **nunca maior que 1 px = 1 pt** (logo pequeno não vira borrão).
     */
    internal fun fitImage(widthPx: Int, heightPx: Int, maxWidth: Float, maxHeight: Float): Pair<Float, Float> {
        if (widthPx <= 0 || heightPx <= 0 || maxWidth <= 0f || maxHeight <= 0f) return 0f to 0f
        val scale = minOf(maxWidth / widthPx, maxHeight / heightPx, 1f)
        return widthPx * scale to heightPx * scale
    }

    /** Pixels da imagem embutida para exibir em [displayWidth]×[displayHeight] pt (≤ a decodificada). */
    internal fun renderPixels(displayWidth: Float, displayHeight: Float, decodedWidth: Int, decodedHeight: Int): Pair<Int, Int> {
        val w = kotlin.math.ceil(displayWidth * RENDER_SCALE).toInt().coerceIn(1, maxOf(1, decodedWidth))
        val h = kotlin.math.ceil(displayHeight * RENDER_SCALE).toInt().coerceIn(1, maxOf(1, decodedHeight))
        return w to h
    }

    /**
     * Quebra [text] por palavra em [maxWidth] (pt), no máximo [maxLines] linhas; `\n` força quebra,
     * espaços repetidos viram um. Palavra mais larga que a linha é partida por letra. O que não
     * coube termina com "…" na última linha. Texto branco = lista vazia.
     */
    internal fun wrapText(text: String?, maxWidth: Float, maxLines: Int, measure: (String) -> Float): List<String> {
        if (text.isNullOrBlank() || maxLines <= 0 || maxWidth <= 0f) return emptyList()
        val all = ArrayList<String>()
        text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach { paragraph ->
            var line = ""
            for (word in paragraph.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (measure(candidate) <= maxWidth) {
                    line = candidate
                    continue
                }
                if (line.isNotEmpty()) all.add(line)
                // Palavra sozinha maior que a linha: parte por letra.
                var rest = word
                while (measure(rest) > maxWidth && rest.length > 1) {
                    var cut = rest.length - 1
                    while (cut > 1 && measure(rest.substring(0, cut)) > maxWidth) cut--
                    all.add(rest.substring(0, cut))
                    rest = rest.substring(cut)
                }
                line = rest
            }
            if (line.isNotEmpty()) all.add(line)
        }
        if (all.size <= maxLines) return all
        val kept = all.subList(0, maxLines).toMutableList()
        kept[maxLines - 1] = ellipsize(kept[maxLines - 1], maxWidth, measure)
        return kept
    }

    /** [line] + "…" cortando letras do fim até caber em [maxWidth]. */
    internal fun ellipsize(line: String, maxWidth: Float, measure: (String) -> Float): String {
        var end = line.length
        while (end > 0 && measure(line.substring(0, end).trimEnd() + "…") > maxWidth) end--
        return line.substring(0, end).trimEnd() + "…"
    }

    /**
     * Mede uma faixa: imagem de [imageWidthPx]×[imageHeightPx] (`null` = sem imagem) e/ou [text] em
     * [width] pt. A altura nunca passa de [LetterheadBandSpec.maxHeight]. Faixa sem nada = `null`.
     */
    internal fun layoutBand(
        spec: LetterheadBandSpec,
        align: PdfLetterheadAlign,
        left: Float,
        width: Float,
        imageWidthPx: Int?,
        imageHeightPx: Int?,
        text: String?,
        measure: (String) -> Float,
    ): LetterheadBandLayout? {
        val hasImage = imageWidthPx != null && imageHeightPx != null && imageWidthPx > 0 && imageHeightPx > 0
        val hasText = !text.isNullOrBlank()
        if (!hasImage && !hasText) return null

        return when (align) {
            PdfLetterheadAlign.LEFT -> {
                val (imgW, imgH) = if (hasImage) {
                    fitImage(
                        imageWidthPx, imageHeightPx,
                        maxWidth = if (hasText) minOf(spec.sideImageMaxWidth, width / 2f) else width,
                        maxHeight = spec.imageMaxHeight,
                    )
                } else 0f to 0f
                val textX = if (imgW > 0f) left + imgW + INNER_GAP else left
                val lines = wrapText(text, left + width - textX, spec.maxLines, measure)
                val textH = lines.size * spec.lineHeight
                val height = minOf(maxOf(imgH, textH), spec.maxHeight)
                val textTop = (height - textH) / 2f
                LetterheadBandLayout(
                    height = height,
                    image = if (imgW > 0f) LetterheadRect(left, (height - imgH) / 2f, imgW, imgH) else null,
                    lines = lines,
                    baselines = baselines(lines.size, textTop, spec),
                    textAnchorX = textX,
                    align = PdfLetterheadAlign.LEFT,
                    fontSize = spec.fontSize,
                )
            }
            PdfLetterheadAlign.RIGHT -> {
                val (imgW, imgH) = if (hasImage) {
                    fitImage(
                        imageWidthPx, imageHeightPx,
                        maxWidth = if (hasText) minOf(spec.sideImageMaxWidth, width / 2f) else width,
                        maxHeight = spec.imageMaxHeight,
                    )
                } else 0f to 0f
                val right = left + width
                val textRight = if (imgW > 0f) right - imgW - INNER_GAP else right
                val lines = wrapText(text, textRight - left, spec.maxLines, measure)
                val textH = lines.size * spec.lineHeight
                val height = minOf(maxOf(imgH, textH), spec.maxHeight)
                val textTop = (height - textH) / 2f
                LetterheadBandLayout(
                    height = height,
                    image = if (imgW > 0f) LetterheadRect(right - imgW, (height - imgH) / 2f, imgW, imgH) else null,
                    lines = lines,
                    baselines = baselines(lines.size, textTop, spec),
                    textAnchorX = textRight,
                    align = PdfLetterheadAlign.RIGHT,
                    fontSize = spec.fontSize,
                )
            }
            PdfLetterheadAlign.CENTER -> {
                val lines = wrapText(text, width, spec.maxLines, measure)
                val textH = lines.size * spec.lineHeight
                val gap = if (hasImage && lines.isNotEmpty()) INNER_GAP / 2f else 0f
                val (imgW, imgH) = if (hasImage) {
                    val room = maxOf(MIN_IMAGE_HEIGHT, spec.maxHeight - textH - gap)
                    fitImage(imageWidthPx, imageHeightPx, width, minOf(spec.imageMaxHeight, room))
                } else 0f to 0f
                val height = minOf(imgH + gap + textH, spec.maxHeight)
                LetterheadBandLayout(
                    height = height,
                    image = if (imgW > 0f) LetterheadRect(left + (width - imgW) / 2f, 0f, imgW, imgH) else null,
                    lines = lines,
                    baselines = baselines(lines.size, imgH + gap, spec),
                    textAnchorX = left + width / 2f,
                    align = PdfLetterheadAlign.CENTER,
                    fontSize = spec.fontSize,
                )
            }
        }
    }

    private fun baselines(count: Int, top: Float, spec: LetterheadBandSpec): List<Float> =
        List(count) { i -> top + i * spec.lineHeight + (spec.lineHeight + spec.fontSize) / 2f - spec.fontSize * 0.2f }

    /**
     * A página: o cabeçalho encostado na margem de cima, o rodapé na de baixo, e o conteúdo entre
     * os dois com [BAND_GAP] de folga (a linha separadora no meio da folga). Sem faixa, o limite
     * daquele lado é a própria margem — como no PDF sem timbrado.
     */
    internal fun pageFrame(
        pageHeight: Float,
        margin: Float,
        header: LetterheadBandLayout?,
        footer: LetterheadBandLayout?,
    ): LetterheadPageFrame {
        val headerTop = margin
        val contentTop = if (header != null) headerTop + header.height + BAND_GAP else margin
        val footerTop = if (footer != null) pageHeight - margin - footer.height else pageHeight - margin
        val contentBottom = if (footer != null) footerTop - BAND_GAP else pageHeight - margin
        return LetterheadPageFrame(
            header = header,
            headerTop = headerTop,
            footer = footer,
            footerTop = footerTop,
            contentTop = contentTop,
            contentBottom = contentBottom,
            headerSeparatorY = header?.let { headerTop + it.height + BAND_GAP / 2f },
            footerSeparatorY = footer?.let { footerTop - BAND_GAP / 2f },
            documentFooterBaseline = pageHeight - margin / 2f + 3f,
        )
    }
}
