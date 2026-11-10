package br.com.codecacto.kmplib.platform.print

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.DesktopFiles
import java.awt.Desktop
import java.nio.file.Files

/**
 * Desktop (2.353.0): entrega o PDF ao verbo IMPRIMIR do SO (`Desktop.print` — o "Imprimir" do menu
 * de contexto do Explorer, que usa o leitor de PDF padrão e a impressora padrão). Papel e cor são
 * decididos no diálogo/driver do SO, não por [paper]/[colorMode]; `Sent` = entregue ao SO.
 * Sem verbo de impressão para PDF no SO = [isPrintingAvailable] `false`.
 */
internal object DesktopPrintHandler : PrintHandler {
    override val isPrintingAvailable: Boolean
        get() = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.PRINT)

    override fun printPdf(
        pdf: ByteArray,
        jobName: String,
        paper: PrintPaper,
        colorMode: PrintColorMode,
        onResult: (PrintResult) -> Unit,
    ) {
        if (!isPrintingAvailable) return onResult(PrintResult.Failed("impressão indisponível neste computador"))
        try {
            val nome = jobName.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").ifBlank { "documento" }
            val pasta = DesktopFiles.tempDirectory(br.com.codecacto.kmplib.platform.PRINT_TEMP_DIRECTORY)
                .resolve(java.util.UUID.randomUUID().toString())
            Files.createDirectories(pasta)
            val arquivo = pasta.resolve(if (nome.endsWith(".pdf", true)) nome else "$nome.pdf")
            Files.write(arquivo, pdf)
            Desktop.getDesktop().print(arquivo.toFile())
            onResult(PrintResult.Sent)
        } catch (e: Exception) {
            AppLogger.w("PrintHandler", "o SO recusou imprimir: ${e::class.simpleName}")
            onResult(PrintResult.Failed("nenhum aplicativo imprime PDF neste computador", e))
        }
    }
}

@Composable
actual fun rememberPrintHandler(): PrintHandler = remember { DesktopPrintHandler }

actual fun getPrintHandler(): PrintHandler = DesktopPrintHandler
