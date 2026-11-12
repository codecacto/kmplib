package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.sync.rest.StreamingMultipartPart
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSource
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import platform.Foundation.NSData
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSFileCoordinatorReadingWithoutChanges
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UniformTypeIdentifiers.UTType
import platform.posix.memcpy

private const val READ_TAG = "FilePicker"

/**
 * MIME pelo **Uniform Type Identifier** da extensão (`UTType.preferredMIMEType`) — a tabela do
 * próprio sistema, que conhece HEIC, Pages, Numbers, ZIP… A tabela de 11 extensões que havia no
 * seletor (até a 2.310.0) chamava todo o resto de `application/octet-stream`.
 */
internal fun mimeTypeForFileName(fileName: String): String {
    val extensao = fileName.substringAfterLast('.', "").lowercase()
    if (extensao.isEmpty()) return "application/octet-stream"
    return UTType.typeWithFilenameExtension(extensao)?.preferredMIMEType ?: "application/octet-stream"
}

/** Tamanho do arquivo local; `-1` quando o sistema não informa. */
@OptIn(ExperimentalForeignApi::class)
internal fun declaredFileSize(url: NSURL): Long {
    val path = url.path ?: return -1L
    val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, null) ?: return -1L
    return (attrs[NSFileSize] as? NSNumber)?.longValue ?: -1L
}

/**
 * Lê um arquivo escolhido no seletor de documentos, **com teto**: abre o escopo de segurança da URL,
 * confere o tamanho antes de materializar e lê por `NSFileCoordinator` (a leitura coordenada é o que
 * a Apple indica para arquivo de provedor — iCloud ainda não baixado é baixado aqui).
 */
@OptIn(ExperimentalForeignApi::class)
internal fun readPickedFile(url: NSURL, maxBytes: Long): FilePickResult {
    val fileName = url.lastPathComponent ?: "arquivo"
    var securityScoped = false
    return try {
        securityScoped = url.startAccessingSecurityScopedResource()

        val declarado = declaredFileSize(url)
        if (exceedsFilePickLimit(declarado, maxBytes)) {
            return FilePickResult.TooLarge(fileName, declarado, maxBytes)
        }

        var data: NSData? = null
        NSFileCoordinator(filePresenter = null).coordinateReadingItemAtURL(
            url = url,
            options = NSFileCoordinatorReadingWithoutChanges,
            error = null,
        ) { coordenada ->
            val alvo = coordenada ?: url
            // Provedor que não informou o tamanho antes da coordenação (arquivo ainda na nuvem):
            // confere de novo antes de criar o NSData.
            if (!exceedsFilePickLimit(declaredFileSize(alvo), maxBytes)) {
                data = NSData.dataWithContentsOfURL(alvo)
            }
        }
        val lido = data ?: run {
            val depois = declaredFileSize(url)
            return if (exceedsFilePickLimit(depois, maxBytes)) {
                FilePickResult.TooLarge(fileName, depois, maxBytes)
            } else {
                FilePickResult.Failed(fileName, FilePickFailure.Unreadable)
            }
        }

        val tamanho = lido.length.toLong()
        if (exceedsFilePickLimit(tamanho, maxBytes)) {
            return FilePickResult.TooLarge(fileName, tamanho, maxBytes)
        }
        val bytes = ByteArray(tamanho.toInt())
        if (tamanho > 0) memcpy(bytes.refTo(0), lido.bytes, lido.length)
        FilePickResult.Picked(
            FileData(
                name = fileName,
                mimeType = mimeTypeForFileName(fileName),
                data = bytes,
                size = bytes.size.toLong(),
            )
        )
    } catch (e: Exception) {
        // Só o tipo: o nome do arquivo é dado pessoal ("cnh-joao.pdf") e iria para log/GlitchTip.
        AppLogger.w(READ_TAG, "não foi possível ler o arquivo escolhido: ${e::class.simpleName}")
        FilePickResult.Failed(fileName, FilePickFailure.Unreadable)
    } finally {
        if (securityScoped) url.stopAccessingSecurityScopedResource()
    }
}

/**
 * Parte multipart em fluxo de um arquivo do seletor de documentos (2.356.0). Tamanho EXATO lido com o
 * escopo de segurança aberto (`null` quando o sistema não informa — arquivo do iCloud ainda não
 * baixado: aí sobe em `chunked`).
 */
internal fun pickedFileStreamingPart(url: NSURL, fieldName: String, fileName: String, mimeType: String): StreamingMultipartPart {
    val escopo = url.startAccessingSecurityScopedResource()
    val tamanho = try {
        declaredFileSize(url).takeIf { it >= 0L }
    } finally {
        if (escopo) url.stopAccessingSecurityScopedResource()
    }
    return StreamingMultipartPart(fieldName, fileName, mimeType, tamanho) { openPickedFileSource(url) }
}

/**
 * Abre o arquivo para leitura em fluxo: escopo de segurança aberto (fechado junto com a fonte) e
 * abertura dentro de `NSFileCoordinator` — o que a Apple indica para arquivo de provedor (o iCloud
 * baixa aqui). O descritor aberto dentro do bloco continua válido depois dele.
 */
@OptIn(ExperimentalForeignApi::class)
private fun openPickedFileSource(url: NSURL): RawSource {
    val escopo = url.startAccessingSecurityScopedResource()
    try {
        var fonte: RawSource? = null
        var falha: Throwable? = null
        NSFileCoordinator(filePresenter = null).coordinateReadingItemAtURL(
            url = url,
            options = NSFileCoordinatorReadingWithoutChanges,
            error = null,
        ) { coordenada ->
            val caminho = (coordenada ?: url).path
            if (caminho == null) {
                falha = IOException("arquivo sem caminho")
            } else {
                try {
                    fonte = SystemFileSystem.source(Path(caminho))
                } catch (e: Throwable) {
                    falha = e
                }
            }
        }
        val aberta = fonte ?: throw (falha as? IOException ?: IOException("arquivo indisponível", falha))
        return ScopedSource(aberta) { if (escopo) url.stopAccessingSecurityScopedResource() }
    } catch (e: Throwable) {
        if (escopo) url.stopAccessingSecurityScopedResource()
        throw e
    }
}

/** Repassa a leitura e, no fechamento, fecha o escopo de segurança (uma vez). */
private class ScopedSource(private val delegate: RawSource, private val onClose: () -> Unit) : RawSource {
    private var fechada = false
    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long = delegate.readAtMostTo(sink, byteCount)
    override fun close() {
        if (fechada) return
        fechada = true
        try { delegate.close() } finally { onClose() }
    }
}
