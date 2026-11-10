package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.core.context.DesktopAppContext
import br.com.codecacto.kmplib.core.util.AppLogger
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/**
 * Peças comuns do alvo desktop (2.353.0) do `kmplib-platform`: o seletor NATIVO de arquivo
 * (`java.awt.FileDialog` — no Windows é o diálogo do Explorer, no macOS o `NSOpenPanel`), o tipo MIME
 * de um arquivo e a pasta temporária da lib.
 */
internal object DesktopFiles {
    private const val TAG = "DesktopFiles"

    private val isWindows: Boolean
        get() = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).startsWith("windows")

    /** A janela em foco (dono do diálogo, para ele abrir por cima dela), ou `null`. */
    private fun ownerFrame(): Frame? {
        var w = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
        while (w != null && w !is Frame) w = w.owner
        return w as? Frame
    }

    /**
     * Abre o seletor nativo e devolve os arquivos escolhidos (vazio = desistiu). Modal: chame da
     * thread de UI (o clique do Compose Desktop já está nela).
     */
    fun chooseFiles(title: String, mimeTypes: List<String>, multiple: Boolean): List<Path> {
        val dialog = FileDialog(ownerFrame(), title, FileDialog.LOAD)
        dialog.isMultipleMode = multiple
        val extensoes = extensionsFor(mimeTypes)
        if (extensoes != null) {
            // Windows filtra pelo padrão no campo do nome; macOS/Linux pelo FilenameFilter.
            if (isWindows) dialog.file = extensoes.joinToString(";") { "*.$it" }
            dialog.setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase(Locale.ROOT) in extensoes }
        }
        dialog.isVisible = true
        val escolhidos = dialog.files?.map { it.toPath() }.orEmpty()
        dialog.dispose()
        return escolhidos.filter { Files.isRegularFile(it) }
    }

    /** Abre o diálogo nativo de SALVAR com [suggestedName]; `null` = desistiu. */
    fun chooseSaveTarget(title: String, suggestedName: String): Path? {
        val dialog = FileDialog(ownerFrame(), title, FileDialog.SAVE)
        dialog.file = suggestedName
        dialog.isVisible = true
        val dir = dialog.directory
        val nome = dialog.file
        dialog.dispose()
        if (dir == null || nome == null) return null
        return File(dir, nome).toPath()
    }

    /** Tipo MIME de [path]: o do SO (`probeContentType`), senão pela extensão, senão `application/octet-stream`. */
    fun mimeTypeOf(path: Path): String =
        runCatching { Files.probeContentType(path) }.getOrNull()?.takeIf { it.contains('/') }
            ?: mimeTypeForExtension(path.fileName.toString().substringAfterLast('.', ""))
            ?: "application/octet-stream"

    /** Extensões aceitas pelos [mimeTypes]; `null` = qualquer arquivo (tipo curinga, lista vazia ou tipo desconhecido). */
    fun extensionsFor(mimeTypes: List<String>): Set<String>? {
        if (mimeTypes.isEmpty() || mimeTypes.any { it.trim() == "*/*" || it.isBlank() }) return null
        val todas = HashSet<String>()
        for (mime in mimeTypes) {
            val m = mime.lowercase(Locale.ROOT).substringBefore(';').trim()
            val doTipo = if (m.endsWith("/*")) {
                val prefixo = m.removeSuffix("*")
                EXTENSOES.filterValues { it.startsWith(prefixo) }.keys
            } else {
                EXTENSOES.filterValues { it == m }.keys
            }
            if (doTipo.isEmpty()) return null // tipo que a tabela não conhece: não esconde nada
            todas += doTipo
        }
        return todas
    }

    fun mimeTypeForExtension(ext: String): String? = EXTENSOES[ext.lowercase(Locale.ROOT)]

    /**
     * Lê [path] inteiro, respeitando o teto [maxBytes] (sem carregar o que passa dele).
     */
    fun readPicked(path: Path, maxBytes: Long): FilePickResult {
        val nome = path.fileName.toString()
        return try {
            val tamanho = Files.size(path)
            if (exceedsFilePickLimit(tamanho, maxBytes)) return FilePickResult.TooLarge(nome, tamanho, maxBytes)
            val bytes = Files.readAllBytes(path)
            FilePickResult.Picked(FileData(nome, mimeTypeOf(path), bytes, bytes.size.toLong()))
        } catch (_: OutOfMemoryError) {
            FilePickResult.Failed(nome, FilePickFailure.OutOfMemory)
        } catch (e: IOException) {
            AppLogger.w(TAG, "arquivo ilegível: ${e::class.simpleName}")
            FilePickResult.Failed(nome, FilePickFailure.Unreadable)
        } catch (e: SecurityException) {
            AppLogger.w(TAG, "arquivo sem permissão de leitura: ${e::class.simpleName}")
            FilePickResult.Failed(nome, FilePickFailure.Unreadable)
        }
    }

    /**
     * Pasta temporária da lib para [name] (`<java.io.tmpdir>/kmplib-<appId>/<name>`), criada sob
     * demanda. Por app, para dois apps da fábrica no mesmo PC não limparem um o arquivo do outro.
     */
    fun tempDirectory(name: String): Path {
        val app = DesktopAppContext.get()?.appId ?: "app"
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "kmplib-$app", name)
        Files.createDirectories(dir)
        return dir
    }

    /** Raiz das pastas temporárias da lib deste app. */
    fun tempRoot(): Path {
        val app = DesktopAppContext.get()?.appId ?: "app"
        return Path.of(System.getProperty("java.io.tmpdir"), "kmplib-$app")
    }

    private val EXTENSOES: Map<String, String> = mapOf(
        "pdf" to "application/pdf",
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif",
        "webp" to "image/webp", "heic" to "image/heic", "heif" to "image/heif", "bmp" to "image/bmp",
        "tif" to "image/tiff", "tiff" to "image/tiff", "svg" to "image/svg+xml",
        "mp4" to "video/mp4", "mov" to "video/quicktime", "m4v" to "video/x-m4v", "webm" to "video/webm",
        "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "wav" to "audio/wav", "ogg" to "audio/ogg",
        "txt" to "text/plain", "csv" to "text/csv", "json" to "application/json", "xml" to "application/xml",
        "html" to "text/html", "ics" to "text/calendar",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "zip" to "application/zip",
    )
}
