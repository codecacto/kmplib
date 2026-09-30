package br.com.codecacto.kmplib.platform.print

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintJob
import android.print.PrintJobInfo
import android.print.PrintManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.ShareHandlerHolder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

private const val TAG = "PrintHandler"

/** Pasta do PDF em impressão, dentro do `cacheDir`. Apagado ao fim do diálogo. */
private const val PRINT_DIRECTORY = "kmplib_print"

/** Resíduo de impressão mais velho que isto é apagado (processo morto no meio do diálogo). */
private const val STALE_PRINT_FILE_MILLIS = 24L * 60L * 60L * 1000L

@Composable
actual fun rememberPrintHandler(): PrintHandler {
    val context = LocalContext.current
    return remember(context) { AndroidPrintHandler { context } }
}

actual fun getPrintHandler(): PrintHandler = AndroidPrintHandler { ShareHandlerHolder.currentActivity() }

/**
 * `PrintManager.print` com um `PrintDocumentAdapter` que entrega o PDF pronto, como a documentação
 * de impressão do Android recomenda para documento já gerado ("Print a document").
 *
 * O `PrintManager` **precisa** vir de uma `Activity` (o do `applicationContext` lança "Can print only
 * from an activity") — por isso o [rememberPrintHandler] pega o contexto da composição.
 */
internal class AndroidPrintHandler(private val contextProvider: () -> Context?) : PrintHandler {

    override val isPrintingAvailable: Boolean
        get() = contextProvider()?.getSystemService(Context.PRINT_SERVICE) != null

    override fun printPdf(
        pdf: ByteArray,
        jobName: String,
        paper: PrintPaper,
        colorMode: PrintColorMode,
        onResult: (PrintResult) -> Unit,
    ) {
        require(isPdf(pdf)) { "o conteúdo para imprimir não é um PDF" }
        val once = OnceResult(onResult)

        val activity = contextProvider()?.findActivity()
        if (activity == null) {
            once(PrintResult.Failed("sem Activity em primeiro plano para abrir o diálogo de impressão"))
            return
        }
        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            once(PrintResult.Failed("serviço de impressão indisponível neste aparelho"))
            return
        }

        val name = sanitizePrintJobName(jobName)
        val file = try {
            writePrintFile(activity, pdf)
        } catch (e: Exception) {
            AppLogger.e(TAG, "não foi possível preparar o PDF para impressão", e)
            once(PrintResult.Failed("não foi possível preparar o PDF", e))
            return
        }

        val info = try {
            readPdfInfo(file)
        } catch (e: Exception) {
            file.delete()
            AppLogger.e(TAG, "PDF ilegível para impressão", e)
            once(PrintResult.Failed("o PDF não pôde ser lido", e))
            return
        }

        val media = mediaSizeFor(paper, info.firstPageHeightPt)
        val attributes = PrintAttributes.Builder()
            .setMediaSize(media)
            .setColorMode(
                if (colorMode == PrintColorMode.MONOCHROME) {
                    PrintAttributes.COLOR_MODE_MONOCHROME
                } else {
                    PrintAttributes.COLOR_MODE_COLOR
                },
            )
            // O PDF já traz a própria margem de segurança; o sistema não acrescenta outra.
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        val adapter = PdfFilePrintAdapter(file, name, info.pageCount) { job ->
            file.delete()
            once(printResultFor(job?.phase()))
        }
        try {
            adapter.job = printManager.print(name, adapter, attributes)
        } catch (e: Exception) {
            file.delete()
            AppLogger.e(TAG, "o sistema recusou a impressão", e)
            once(PrintResult.Failed("o sistema recusou a impressão", e))
        }
    }

    private fun writePrintFile(context: Context, pdf: ByteArray): File {
        val dir = File(context.cacheDir, PRINT_DIRECTORY).apply { mkdirs() }
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { if (now - it.lastModified() > STALE_PRINT_FILE_MILLIS) it.delete() }
        val file = File(dir, "print-$now-${System.nanoTime()}.pdf")
        file.writeBytes(pdf)
        return file
    }
}

private class PdfInfo(val pageCount: Int, val firstPageHeightPt: Double)

/** Conta páginas e lê a altura da 1ª com o `PdfRenderer` da plataforma (em pontos). */
private fun readPdfInfo(file: File): PdfInfo {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(descriptor)
    try {
        val count = renderer.pageCount
        require(count > 0) { "PDF sem páginas" }
        val page = renderer.openPage(0)
        try {
            return PdfInfo(count, page.height.toDouble())
        } finally {
            page.close()
        }
    } finally {
        renderer.close()
    }
}

/**
 * `MediaSize` do papel. Os padrões usam o tamanho padrão do Android (a impressora os reconhece pelo
 * id); o resto — térmica, cartão — vira tamanho customizado, com o comprimento da bobina tirado da
 * página do PDF.
 */
private fun mediaSizeFor(paper: PrintPaper, firstPageHeightPt: Double): PrintAttributes.MediaSize = when (paper) {
    PrintPaper.A4 -> PrintAttributes.MediaSize.ISO_A4
    PrintPaper.A5 -> PrintAttributes.MediaSize.ISO_A5
    PrintPaper.LETTER -> PrintAttributes.MediaSize.NA_LETTER
    else -> {
        val (widthMm, heightMm) = resolvedPaperSizeMm(paper, firstPageHeightPt)!!
        PrintAttributes.MediaSize(customMediaId(paper, heightMm), paper.name, mmToMils(widthMm), mmToMils(heightMm))
    }
}

private fun PrintJob.phase(): PrintJobPhase? = when (info.state) {
    PrintJobInfo.STATE_CREATED -> PrintJobPhase.CREATED
    PrintJobInfo.STATE_QUEUED -> PrintJobPhase.QUEUED
    PrintJobInfo.STATE_STARTED -> PrintJobPhase.STARTED
    PrintJobInfo.STATE_BLOCKED -> PrintJobPhase.BLOCKED
    PrintJobInfo.STATE_COMPLETED -> PrintJobPhase.COMPLETED
    PrintJobInfo.STATE_FAILED -> PrintJobPhase.FAILED
    PrintJobInfo.STATE_CANCELED -> PrintJobPhase.CANCELED
    else -> null
}

/**
 * Entrega o PDF pronto ao spooler. O conteúdo não depende do papel escolhido (o sistema escala na
 * impressão), então o layout só informa a contagem de páginas; a escrita copia o arquivo inteiro
 * fora da thread principal e responde `ALL_PAGES` — o spooler recorta o intervalo que a pessoa pediu.
 */
private class PdfFilePrintAdapter(
    private val file: File,
    private val jobName: String,
    private val pageCount: Int,
    private val onFinished: (PrintJob?) -> Unit,
) : PrintDocumentAdapter() {

    @Volatile
    var job: PrintJob? = null

    private val main = Handler(Looper.getMainLooper())

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(printFileName(jobName))
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pageCount)
            .build()
        callback.onLayoutFinished(info, newAttributes != oldAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        thread(name = "kmplib-print-write") {
            try {
                FileInputStream(file).use { input ->
                    // O descritor é do spooler: gravamos e damos flush, sem fechar.
                    val output = FileOutputStream(destination.fileDescriptor)
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        if (cancellationSignal?.isCanceled == true) {
                            main.post { callback.onWriteCancelled() }
                            return@thread
                        }
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
                main.post { callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }
            } catch (e: Exception) {
                AppLogger.e(TAG, "falha ao entregar o PDF ao spooler", e)
                main.post { callback.onWriteFailed(e.message) }
            }
        }
    }

    override fun onFinish() {
        onFinished(job)
    }
}

/** Nome do arquivo que o "Salvar como PDF" do sistema sugere. */
private fun printFileName(jobName: String): String {
    val base = jobName.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifEmpty { "documento" }
    return if (base.endsWith(".pdf", ignoreCase = true)) base else "$base.pdf"
}

private class OnceResult(private val delegate: (PrintResult) -> Unit) : (PrintResult) -> Unit {
    private val done = AtomicBoolean(false)
    override fun invoke(result: PrintResult) {
        if (done.compareAndSet(false, true)) delegate(result)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
