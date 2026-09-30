package br.com.codecacto.kmplib.platform.print

import androidx.compose.runtime.Composable
import kotlin.math.roundToInt

/**
 * **Impressão de PDF pelo diálogo do sistema** (2.225.0).
 *
 * - Android: `PrintManager` + `PrintDocumentAdapter` — o diálogo de impressão do Android, com o
 *   papel já sugerido ([PrintPaper]) e "Salvar como PDF" sempre disponível.
 * - iOS: `UIPrintInteractionController`, escolhendo o papel pelo delegado
 *   (`printInteractionController(_:choosePaper:)`) e, em bobina, o comprimento do corte
 *   (`printInteractionController(_:cutLengthFor:)`).
 *
 * O papel é uma **sugestão**: quem manda é a impressora. Numa impressora de mesa, um PDF de 58 mm é
 * impresso na folha (escalado ou centralizado, conforme o sistema); numa térmica, sai na bobina.
 *
 * ```kotlin
 * val printer = rememberPrintHandler()
 * AppButton("Imprimir", enabled = pdf != null && printer.isPrintingAvailable) {
 *     printer.printPdf(pdf!!, jobName = "Colinha do Voto", paper = PrintPaper.THERMAL_58,
 *         colorMode = PrintColorMode.MONOCHROME) { result ->
 *         if (result is PrintResult.Failed) showSnackbar("Não foi possível imprimir")
 *     }
 * }
 * ```
 *
 * Salvar o arquivo: [rememberFileSaver]. Compartilhar: `getShareHandler().shareFile(bytes,
 * "colinha.pdf", "application/pdf")` (já existia).
 */
interface PrintHandler {

    /**
     * `false` quando o aparelho não imprime (iOS sem suporte a impressão). No Android é sempre
     * `true`: o diálogo do sistema oferece "Salvar como PDF" e a instalação de serviços de impressão.
     */
    val isPrintingAvailable: Boolean

    /**
     * Abre o diálogo de impressão do sistema para [pdf].
     *
     * @param jobName nome do trabalho na fila da impressora (e do arquivo, em "Salvar como PDF").
     * @param paper papel sugerido; [PrintPaper.isRoll] = bobina, com o comprimento tirado da 1ª
     *   página do PDF.
     * @param onResult chamado UMA vez, na thread principal, quando o diálogo fecha.
     * @throws IllegalArgumentException [pdf] não é um PDF (não começa com `%PDF-`).
     */
    fun printPdf(
        pdf: ByteArray,
        jobName: String,
        paper: PrintPaper = PrintPaper.A4,
        colorMode: PrintColorMode = PrintColorMode.COLOR,
        onResult: (PrintResult) -> Unit = {},
    )
}

/**
 * O handler da tela atual. **Prefira este** a [getPrintHandler]: no Android a impressão exige o
 * contexto de uma `Activity`, e aqui ele vem da composição.
 */
@Composable
expect fun rememberPrintHandler(): PrintHandler

/**
 * Handler fora da composição. Android: usa a `Activity` registrada por `kmpLibPlatformOnResume` —
 * sem ela, a impressão devolve [PrintResult.Failed].
 */
expect fun getPrintHandler(): PrintHandler

/** Modo de cor pedido ao sistema. Térmica só imprime preto: use [MONOCHROME]. */
enum class PrintColorMode { COLOR, MONOCHROME }

/** Como terminou o diálogo de impressão. */
sealed interface PrintResult {
    /** O trabalho foi para a fila (ou foi salvo como PDF). Não garante papel impresso. */
    data object Sent : PrintResult

    /** A pessoa fechou o diálogo sem imprimir. */
    data object Cancelled : PrintResult

    /** O sistema recusou ou o trabalho falhou. [message] é técnica, para log — não para a tela. */
    data class Failed(val message: String, val cause: Throwable? = null) : PrintResult
}

/**
 * Papel sugerido ao sistema.
 *
 * @property widthMm largura do papel.
 * @property heightMm altura; `null` = **bobina** (o comprimento vem da página do PDF).
 * @property name rótulo do papel no diálogo do Android (quando ele não é um tamanho padrão).
 */
data class PrintPaper(
    val widthMm: Double,
    val heightMm: Double?,
    val name: String,
) {
    init {
        require(widthMm > 0.0) { "largura do papel precisa ser positiva" }
        require(heightMm == null || heightMm > 0.0) { "altura do papel precisa ser positiva" }
        require(name.isNotBlank()) { "o papel precisa de nome" }
    }

    /** Bobina contínua (térmica). */
    val isRoll: Boolean get() = heightMm == null

    companion object {
        val A4 = PrintPaper(210.0, 297.0, "A4")
        val A5 = PrintPaper(148.0, 210.0, "A5")
        val LETTER = PrintPaper(215.9, 279.4, "Letter")

        /** Bobina térmica de 58 mm. */
        val THERMAL_58 = roll(58.0, "58 mm")

        /** Bobina térmica de 80 mm. */
        val THERMAL_80 = roll(80.0, "80 mm")

        /** Folha de medida própria (cartão, etiqueta). */
        fun sheet(widthMm: Double, heightMm: Double, name: String): PrintPaper = PrintPaper(widthMm, heightMm, name)

        /** Bobina de largura própria. */
        fun roll(widthMm: Double, name: String): PrintPaper = PrintPaper(widthMm, null, name)
    }
}

// =============================================================================================
// Regras puras (commonTest)
// =============================================================================================

/** Milímetros → milésimos de polegada (unidade do `MediaSize` do Android). */
internal fun mmToMils(mm: Double): Int = (mm / 25.4 * 1000.0).roundToInt()

/** Pontos PDF (1/72 pol.) → milésimos de polegada. */
internal fun pointsToMils(points: Double): Int = (points / 72.0 * 1000.0).roundToInt()

/**
 * Medida final do papel, em milímetros: a da folha, ou — em bobina — a largura da bobina com o
 * comprimento da página do PDF (convertido de pontos).
 */
internal fun resolvedPaperSizeMm(paper: PrintPaper, firstPageHeightPt: Double?): Pair<Double, Double>? {
    val height = paper.heightMm ?: firstPageHeightPt?.let { it / 72.0 * 25.4 } ?: return null
    return paper.widthMm to height
}

/** Id estável do `MediaSize` customizado (o Android exige id único e não vazio). */
internal fun customMediaId(paper: PrintPaper, heightMm: Double): String =
    "kmplib_${(paper.widthMm * 10).roundToInt()}x${(heightMm * 10).roundToInt()}"

/** O arquivo começa com a assinatura de PDF (`%PDF-`)? */
internal fun isPdf(bytes: ByteArray): Boolean {
    val signature = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D) // %PDF-
    if (bytes.size < signature.size) return false
    return signature.indices.all { bytes[it] == signature[it] }
}

/** Nome do trabalho de impressão: sem quebra de linha, nunca vazio. */
internal fun sanitizePrintJobName(name: String): String =
    name.replace(Regex("[\\r\\n\\t]+"), " ").trim().take(120).ifEmpty { "Documento" }

/** Estado de um trabalho de impressão no fim do diálogo — espelha o `PrintJobInfo` do Android. */
internal enum class PrintJobPhase { CREATED, QUEUED, STARTED, BLOCKED, COMPLETED, FAILED, CANCELED }

/**
 * Resultado a partir do estado do trabalho quando o diálogo fecha. `CREATED` = o diálogo fechou sem
 * o trabalho ter ido para a fila, ou seja, cancelado.
 */
internal fun printResultFor(phase: PrintJobPhase?, failureReason: String? = null): PrintResult = when (phase) {
    PrintJobPhase.QUEUED, PrintJobPhase.STARTED, PrintJobPhase.BLOCKED, PrintJobPhase.COMPLETED -> PrintResult.Sent
    PrintJobPhase.FAILED -> PrintResult.Failed(failureReason ?: "o trabalho de impressão falhou")
    PrintJobPhase.CANCELED, PrintJobPhase.CREATED, null -> PrintResult.Cancelled
}
