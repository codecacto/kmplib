package br.com.codecacto.kmplib.pdf.canvas

import kotlin.concurrent.Volatile

/**
 * Tipos públicos do **PDF de layout livre** (2.225.0) — o desenho a mão livre que os geradores de
 * domínio (`OsPdf`, `ReciboPdf`…) não oferecem: página de medida própria em **milímetros**, texto,
 * linha (inclusive tracejada, para corte), retângulo, imagem e página de **comprimento variável**
 * (bobina térmica de 58/80 mm).
 *
 * Convenções, valendo para toda a API:
 * - **Geometria em milímetros**, origem no **canto superior esquerdo** da página, y crescendo para
 *   baixo — a mesma convenção de uma especificação de layout impressa.
 * - **Tipografia em pontos** (`sizePt`), como em qualquer especificação gráfica ("dígito 16 pt").
 * - **Espessura de traço em pontos** (`widthPt`); o padrão de tracejado, em milímetros.
 *
 * Ponto de entrada: [buildPdf] / [recordPdf].
 */

/** Marca de DSL: impede que um `page { }` dentro de outro enxergue o escopo de fora sem querer. */
@DslMarker
annotation class PdfCanvasDsl

/**
 * Cor RGBA com componentes em `0..1`. O miolo de impressão costuma ser só [Black] sobre [White].
 */
data class PdfColor(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float = 1f,
) {
    init {
        require(red in 0f..1f && green in 0f..1f && blue in 0f..1f && alpha in 0f..1f) {
            "componentes de cor precisam estar em 0..1 (r=$red g=$green b=$blue a=$alpha)"
        }
    }

    companion object {
        val Black = PdfColor(0f, 0f, 0f)
        val White = PdfColor(1f, 1f, 1f)

        /** Cor a partir de `0xRRGGBB` (opaca). */
        fun rgb(value: Int): PdfColor = PdfColor(
            red = ((value shr 16) and 0xFF) / 255f,
            green = ((value shr 8) and 0xFF) / 255f,
            blue = (value and 0xFF) / 255f,
        )

        /** Cor a partir de `0xAARRGGBB` — o mesmo literal de `Color(0x…)` do Compose. */
        fun argb(value: Long): PdfColor = PdfColor(
            red = ((value shr 16) and 0xFF) / 255f,
            green = ((value shr 8) and 0xFF) / 255f,
            blue = (value and 0xFF) / 255f,
            alpha = ((value shr 24) and 0xFF) / 255f,
        )
    }
}

/**
 * Padrão de tracejado: [onMm] de traço, [offMm] de vão, começando [phaseMm] adiante no padrão.
 * [CutLine] é a linha de corte usual (3 mm de traço, 2 mm de vão).
 */
data class PdfDash(
    val onMm: Double,
    val offMm: Double,
    val phaseMm: Double = 0.0,
) {
    init {
        require(onMm > 0.0 && offMm > 0.0) { "tracejado precisa de traço e vão positivos" }
        require(phaseMm >= 0.0) { "fase do tracejado não pode ser negativa" }
    }

    companion object {
        /** Linha de corte/dobra: 3 mm de traço, 2 mm de vão. */
        val CutLine = PdfDash(onMm = 3.0, offMm = 2.0)
    }
}

/**
 * Traço de linha ou contorno. [widthPt] em pontos (0,5 pt é a linha fina que ainda sai em qualquer
 * impressora; térmica pede ≥ 1 pt). [dash] `null` = linha contínua.
 */
data class PdfStroke(
    val widthPt: Double = 0.5,
    val color: PdfColor = PdfColor.Black,
    val dash: PdfDash? = null,
) {
    init {
        require(widthPt > 0.0) { "espessura do traço precisa ser positiva" }
    }
}

/**
 * Família de fonte do texto.
 *
 * - [SansSerif] / [Monospace] — fontes do SISTEMA (Roboto no Android, SF no iOS). Entram
 *   **embutidas** no PDF (o arquivo abre igual em qualquer leitor), mas o desenho muda de uma
 *   plataforma para a outra.
 * - [fromBytes] — fonte do APP (TTF/OTF, ex.: `Res.readBytes("font/Inter-Bold.ttf")`). É a saída
 *   quando o papel tem de sair **idêntico** no Android e no iOS. Guarde a instância (um `val` de
 *   topo ou `remember`): a fonte é carregada **uma vez por instância**.
 *
 * Negrito sem o arquivo [bold] = negrito **sintético** da plataforma (mais grosso, porém menos
 * bonito que o desenho de verdade); para tipografia de impressão, passe os dois arquivos.
 */
class PdfFontFamily private constructor(
    /** Nome para diagnóstico (log, `toString`). Não precisa ser o nome PostScript. */
    val name: String,
    internal val kind: Kind,
    internal val regular: ByteArray?,
    internal val bold: ByteArray?,
) {
    internal enum class Kind { SansSerif, Monospace, Custom }

    /**
     * Cache da fonte já carregada pela plataforma (Typeface / descritor CoreText), por instância.
     * Corrida benigna: duas threads podem carregar duas vezes; o resultado é o mesmo.
     */
    @Volatile
    internal var platformCache: Any? = null

    override fun toString(): String = "PdfFontFamily($name)"

    companion object {
        val SansSerif: PdfFontFamily = PdfFontFamily("sans-serif", Kind.SansSerif, null, null)
        val Monospace: PdfFontFamily = PdfFontFamily("monospace", Kind.Monospace, null, null)

        /**
         * Fonte do app, embutida no PDF. [regular] é obrigatório; [bold] é o arquivo do peso
         * negrito (sem ele, negrito sintético).
         */
        fun fromBytes(name: String, regular: ByteArray, bold: ByteArray? = null): PdfFontFamily {
            require(regular.isNotEmpty()) { "arquivo da fonte '$name' está vazio" }
            require(bold == null || bold.isNotEmpty()) { "arquivo negrito da fonte '$name' está vazio" }
            return PdfFontFamily(name, Kind.Custom, regular, bold)
        }
    }
}

/**
 * Estilo de uma linha de texto.
 *
 * @param sizePt corpo da fonte em pontos.
 * @param tabularNumbers algarismos de **largura fixa** (`tnum`): "1111" e "8888" ocupam o mesmo
 *   espaço — é o que alinha números em coluna e em caixas de dígito.
 */
data class PdfTextStyle(
    val sizePt: Double,
    val bold: Boolean = false,
    val color: PdfColor = PdfColor.Black,
    val font: PdfFontFamily = PdfFontFamily.SansSerif,
    val tabularNumbers: Boolean = false,
) {
    init {
        require(sizePt > 0.0) { "corpo da fonte precisa ser positivo" }
    }
}

/** Alinhamento horizontal do texto. */
enum class PdfTextAlign { Start, Center, End }

/** Alinhamento vertical do texto dentro de uma caixa ([PdfPageScope.textInBox]). */
enum class PdfVerticalAlign {
    /** Topo da linha (ascendente) colado no topo da caixa. */
    Top,

    /**
     * Centro **ótico** das maiúsculas e algarismos (altura de versal), não da linha inteira — é o
     * que deixa um dígito no meio da caixa. A linha inteira (com descendentes) pareceria alta.
     */
    Center,

    /** Linha de base apoiada no fundo da caixa, menos a descendente. */
    Bottom,
}

/** O que fazer com texto de UMA linha que não cabe na largura dada. */
enum class PdfTextOverflow {
    /** Desenha inteiro, passando da largura. */
    Visible,

    /** Corta e termina com "…". */
    Ellipsis,
}

/** Como uma imagem ocupa a caixa pedida. */
enum class PdfImageFit {
    /** Inteira, sem deformar, centralizada; pode sobrar margem. */
    Contain,

    /** Preenche a caixa sem deformar; o excedente é recortado. */
    Cover,

    /** Estica para a caixa exata (deforma se a proporção for outra). */
    Fill,
}

/**
 * Métrica de texto em milímetros, para layout.
 *
 * @property widthMm largura do texto.
 * @property ascentMm da linha de base até o topo da linha (positivo).
 * @property descentMm da linha de base até o fundo da linha (positivo).
 * @property capHeightMm altura das maiúsculas/algarismos acima da linha de base.
 */
data class PdfTextMetrics(
    val widthMm: Double,
    val ascentMm: Double,
    val descentMm: Double,
    val capHeightMm: Double,
) {
    /** Altura de UMA linha (ascendente + descendente), sem entrelinha extra. */
    val lineHeightMm: Double get() = ascentMm + descentMm
}

/** Altura da página. */
sealed interface PdfPageHeight {
    /** Altura fixa: folha, cartão, etiqueta. */
    data class Fixed(val heightMm: Double) : PdfPageHeight {
        init {
            require(heightMm > 0.0) { "altura da página precisa ser positiva" }
        }
    }

    /**
     * Altura **conforme o conteúdo** — bobina térmica. A página termina [bottomPaddingMm] abaixo do
     * ponto mais baixo desenhado (o "branco" que o cortador da impressora precisa), nunca menor que
     * [minHeightMm]. Conteúdo que passaria de [maxHeightMm] **falha** (`PdfLayoutException`) em vez
     * de ser cortado: o que fica fora do papel costuma ser justamente o rodapé.
     */
    data class FitContent(
        val bottomPaddingMm: Double = 0.0,
        val minHeightMm: Double = 1.0,
        val maxHeightMm: Double = MAX_PAGE_SIDE_MM,
    ) : PdfPageHeight {
        init {
            require(bottomPaddingMm >= 0.0) { "folga inferior não pode ser negativa" }
            require(minHeightMm > 0.0 && maxHeightMm >= minHeightMm) { "faixa de altura inválida" }
            require(maxHeightMm <= MAX_PAGE_SIDE_MM) {
                "página maior que $MAX_PAGE_SIDE_MM mm (limite do formato PDF)"
            }
        }
    }
}

/**
 * Tamanho de página. Use os prontos ([A4], [A5], [LETTER], [THERMAL_58], [THERMAL_80]) ou
 * [fixed]/[roll] para medida própria (cartão 85 × 55, tira 70 × 297, etiqueta…).
 */
data class PdfPageSize(
    val widthMm: Double,
    val height: PdfPageHeight,
) {
    init {
        require(widthMm > 0.0 && widthMm <= MAX_PAGE_SIDE_MM) { "largura da página fora de 0..$MAX_PAGE_SIDE_MM mm" }
        if (height is PdfPageHeight.Fixed) {
            require(height.heightMm <= MAX_PAGE_SIDE_MM) { "altura da página acima de $MAX_PAGE_SIDE_MM mm" }
        }
    }

    companion object {
        val A4 = fixed(210.0, 297.0)
        val A5 = fixed(148.0, 210.0)
        val LETTER = fixed(215.9, 279.4)

        /** Bobina térmica de 58 mm, comprimento conforme o conteúdo + 3 mm para o cortador. */
        val THERMAL_58 = roll(58.0)

        /** Bobina térmica de 80 mm, comprimento conforme o conteúdo + 3 mm para o cortador. */
        val THERMAL_80 = roll(80.0)

        fun fixed(widthMm: Double, heightMm: Double): PdfPageSize =
            PdfPageSize(widthMm, PdfPageHeight.Fixed(heightMm))

        fun roll(widthMm: Double, bottomPaddingMm: Double = 3.0): PdfPageSize =
            PdfPageSize(widthMm, PdfPageHeight.FitContent(bottomPaddingMm = bottomPaddingMm))
    }
}

/** Página já resolvida (altura final conhecida). */
data class PdfPageInfo(val widthMm: Double, val heightMm: Double)

/** O layout pedido não cabe na página (ex.: bobina que passaria do comprimento máximo). */
class PdfLayoutException(message: String) : IllegalStateException(message)

/**
 * Maior lado de página do formato PDF: 14 400 unidades (200 polegadas) — ISO 32000-1, anexo C.
 */
const val MAX_PAGE_SIDE_MM: Double = 5080.0

/** Milímetro → ponto PDF (1 pt = 1/72 in). */
internal const val PT_PER_MM: Double = 72.0 / 25.4

internal fun Double.mmToPoints(): Double = this * PT_PER_MM
internal fun Double.pointsToMm(): Double = this / PT_PER_MM
