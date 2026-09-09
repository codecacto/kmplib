package br.com.codecacto.kmplib.pdf.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

internal actual suspend fun readLocalPdfFile(path: String): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val arquivo = File(path)
        if (!arquivo.exists() || !arquivo.canRead()) null else arquivo.readBytes()
    }.getOrNull()
}

/**
 * Um PDF aberto pelo **`PdfRenderer` da plataforma** — a API nativa do Android, sem dependência de
 * terceiro (é a mesma que o `PdfRasterizer` da lib já usa).
 *
 * Três coisas que o `PdfRenderer` exige e que definem este desenho:
 *
 * 1. **Ele lê de um arquivo `seekable`**, nunca de um `InputStream`. Por isso os bytes vão para um
 *    temporário no `cacheDir` — apagado no [close].
 * 2. **Ele NÃO é reentrante**: só uma página aberta por vez, e `openPage` de duas corrotinas ao
 *    mesmo tempo estoura com `IllegalStateException`. Numa `LazyColumn` isso acontece o tempo todo
 *    (várias páginas entram na tela juntas), então todo acesso passa pelo [mutex].
 * 3. **Renderizar é trabalho de CPU** — sempre fora da thread principal.
 *
 * As proporções de cada página são lidas **na abertura** e guardadas: sem elas a `LazyColumn` daria
 * altura zero a todos os itens, composaria o documento inteiro de uma vez e só então descobriria o
 * tamanho de cada um.
 */
internal class AndroidPdfDocument private constructor(
    private val arquivo: File,
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    /** Altura ÷ largura de cada página, na ordem. */
    val pageRatios: List<Float>,
) {
    private val mutex = Mutex()
    private var fechado = false

    val pageCount: Int get() = pageRatios.size

    /** Rasteriza a página [index] com [widthPx] de largura. `null` se falhar ou já estiver fechado. */
    suspend fun renderPage(index: Int, widthPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (fechado || index !in 0 until pageCount) return@withLock null
            runCatching {
                renderer.openPage(index).use { pagina ->
                    val largura = widthPx.coerceIn(1, MAX_RENDER_WIDTH_PX)
                    val altura = (largura * pagina.height.toFloat() / pagina.width.toFloat())
                        .toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888)
                    // PDF com transparência sobre fundo escuro fica ilegível — papel é branco.
                    bitmap.eraseColor(Color.WHITE)
                    pagina.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap()
                }
            }.onFailure {
                AppLogger.e(PDF_VIEWER_TAG, "Falha ao rasterizar a página $index: ${it.message}")
            }.getOrNull()
        }
    }

    fun close() {
        fechado = true
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
        runCatching { arquivo.delete() }
    }

    companion object {
        /**
         * Abre o documento. Lança [PdfLoadException] com o motivo — arquivo que não é PDF, PDF
         * cifrado (o `PdfRenderer` recusa) ou falha de disco.
         */
        suspend fun open(bytes: ByteArray, cacheDir: File): AndroidPdfDocument =
            withContext(Dispatchers.IO) {
                val arquivo = try {
                    File.createTempFile("kmplib_pdfviewer_", ".pdf", cacheDir).apply {
                        writeBytes(bytes)
                    }
                } catch (e: Exception) {
                    throw PdfLoadException(PdfViewerError.Io, e.message ?: "falha ao gravar o temporário")
                }

                try {
                    val descriptor =
                        ParcelFileDescriptor.open(arquivo, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(descriptor)
                    val proporcoes = (0 until renderer.pageCount).map { indice ->
                        renderer.openPage(indice).use { it.height.toFloat() / it.width.toFloat() }
                    }
                    AndroidPdfDocument(arquivo, descriptor, renderer, proporcoes)
                } catch (e: Exception) {
                    arquivo.delete()
                    throw PdfLoadException(
                        PdfViewerError.Corrupted,
                        e.message ?: "documento ilegível",
                    )
                }
            }
    }
}

/**
 * Teto da largura de rasterização.
 *
 * A memória de um bitmap cresce com o **quadrado** da escala: uma página a 4× numa tela de 1080
 * pontos já pede ~75 MB. O grampo aqui é a última linha de defesa contra o `OutOfMemoryError` em
 * aparelho modesto — o outro é o [PDF_MAX_ZOOM].
 */
private const val MAX_RENDER_WIDTH_PX = 4096
