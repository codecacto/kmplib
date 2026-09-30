@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package br.com.codecacto.kmplib.platform.print

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.topViewController
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGDataProviderCreateWithCFData
import platform.CoreGraphics.CGDataProviderRelease
import platform.CoreGraphics.CGPDFDocumentCreateWithProvider
import platform.CoreGraphics.CGPDFDocumentGetNumberOfPages
import platform.CoreGraphics.CGPDFDocumentGetPage
import platform.CoreGraphics.CGPDFDocumentRelease
import platform.CoreGraphics.CGPDFPageGetBoxRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.kCGPDFMediaBox
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.create
import platform.UIKit.UIDevice
import platform.UIKit.UIPrintInfo
import platform.UIKit.UIPrintInfoOutputType
import platform.UIKit.UIPrintInteractionController
import platform.UIKit.UIPrintInteractionControllerDelegateProtocol
import platform.UIKit.UIPrintPaper
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.UIViewController
import platform.darwin.NSObject

private const val TAG = "PrintHandler"

@Composable
actual fun rememberPrintHandler(): PrintHandler = remember { IosPrintHandler() }

actual fun getPrintHandler(): PrintHandler = IosPrintHandler()

/**
 * `UIPrintInteractionController` — o diálogo de impressão do iOS (AirPrint), com o PDF como
 * `printingItem`. O papel sai do delegado:
 * - `printInteractionController(_:choosePaper:)` → `UIPrintPaper.bestPaper(forPageSize:withPapersFrom:)`
 *   com a medida pedida — o jeito que a Apple documenta para papel que não é o padrão;
 * - `printInteractionController(_:cutLengthFor:)` → em impressora de BOBINA, o comprimento do corte
 *   é o da página do PDF (a térmica corta onde a colinha termina, não numa folha fixa).
 *
 * O controlador é compartilhado pelo sistema e mantém o delegado como referência FRACA — a sessão
 * fica retida aqui até o diálogo fechar.
 */
internal class IosPrintHandler : PrintHandler {

    override val isPrintingAvailable: Boolean
        get() = UIPrintInteractionController.isPrintingAvailable()

    override fun printPdf(
        pdf: ByteArray,
        jobName: String,
        paper: PrintPaper,
        colorMode: PrintColorMode,
        onResult: (PrintResult) -> Unit,
    ) {
        require(isPdf(pdf)) { "o conteúdo para imprimir não é um PDF" }
        if (activeSession != null) {
            onResult(PrintResult.Failed("já há um diálogo de impressão aberto"))
            return
        }
        val data = pdf.toNSData()
        if (!UIPrintInteractionController.isPrintingAvailable() || !UIPrintInteractionController.canPrintData(data)) {
            onResult(PrintResult.Failed("impressão indisponível neste aparelho"))
            return
        }
        val firstPage = firstPageSizePt(data)
        if (firstPage == null) {
            onResult(PrintResult.Failed("o PDF não pôde ser lido"))
            return
        }
        val target = resolvedPaperSizeMm(paper, firstPage.second)!!.let { (w, h) -> w * PT_PER_MM to h * PT_PER_MM }

        val controller = UIPrintInteractionController.sharedPrintController
        val info = UIPrintInfo.printInfo()
        info.jobName = sanitizePrintJobName(jobName)
        info.outputType = if (colorMode == PrintColorMode.MONOCHROME) {
            UIPrintInfoOutputType.UIPrintInfoOutputGrayscale
        } else {
            UIPrintInfoOutputType.UIPrintInfoOutputGeneral
        }
        controller.printInfo = info
        controller.printingItem = data
        controller.showsNumberOfCopies = true

        val session = PrintSession(target, onResult)
        activeSession = session
        controller.delegate = session

        val completion: (UIPrintInteractionController?, Boolean, NSError?) -> Unit = { ctrl, completed, error ->
            ctrl?.delegate = null
            activeSession = null
            val result = when {
                error != null -> PrintResult.Failed(error.localizedDescription)
                completed -> PrintResult.Sent
                else -> PrintResult.Cancelled
            }
            if (result is PrintResult.Failed) AppLogger.w(TAG, "impressão falhou: ${result.message}")
            session.onResult(result)
        }

        val presented = if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) {
            // No iPad o diálogo é um popover e precisa de âncora (centro da tela, sem botão nativo
            // para apontar numa tela Compose).
            val anchor = topViewController()?.view
            if (anchor == null) {
                false
            } else {
                val rect = anchor.bounds.useContents {
                    CGRectMake(origin.x + size.width / 2.0, origin.y + size.height / 2.0, 1.0, 1.0)
                }
                controller.presentFromRect(rect, inView = anchor, animated = true, completionHandler = completion)
            }
        } else {
            controller.presentAnimated(true, completionHandler = completion)
        }
        if (!presented) {
            controller.delegate = null
            activeSession = null
            onResult(PrintResult.Failed("o diálogo de impressão não pôde ser apresentado"))
        }
    }
}

/** Sessão em andamento — retém o delegado enquanto o diálogo está aberto. */
private var activeSession: PrintSession? = null

private const val PT_PER_MM = 72.0 / 25.4

private class PrintSession(
    private val targetSizePt: Pair<Double, Double>,
    val onResult: (PrintResult) -> Unit,
) : NSObject(), UIPrintInteractionControllerDelegateProtocol {

    override fun printInteractionControllerParentViewController(
        printInteractionController: UIPrintInteractionController,
    ): UIViewController? = topViewController()

    @ObjCSignatureOverride
    override fun printInteractionController(
        printInteractionController: UIPrintInteractionController,
        choosePaper: List<*>,
    ): UIPrintPaper = UIPrintPaper.bestPaperForPageSize(
        CGSizeMake(targetSizePt.first, targetSizePt.second),
        withPapersFromArray = choosePaper,
    )

    @ObjCSignatureOverride
    override fun printInteractionController(
        printInteractionController: UIPrintInteractionController,
        cutLengthForPaper: UIPrintPaper,
    ): Double = targetSizePt.second
}

/** Medida (pt) da 1ª página e contagem > 0, pelo CoreGraphics; `null` se não for um PDF legível. */
private fun firstPageSizePt(data: NSData): Pair<Double, Double>? {
    val cfData = CFBridgingRetain(data) ?: return null
    val provider = CGDataProviderCreateWithCFData(cfData.reinterpret())
    CFRelease(cfData)
    if (provider == null) return null
    val document = CGPDFDocumentCreateWithProvider(provider)
    CGDataProviderRelease(provider)
    if (document == null) return null
    try {
        if (CGPDFDocumentGetNumberOfPages(document) == 0uL) return null
        val page = CGPDFDocumentGetPage(document, 1u) ?: return null
        return CGPDFPageGetBoxRect(page, kCGPDFMediaBox).useContents { size.width to size.height }
    } finally {
        CGPDFDocumentRelease(document)
    }
}

private fun ByteArray.toNSData(): NSData =
    usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
