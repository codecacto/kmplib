package br.com.codecacto.kmplib.core.storage

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * [BlobStore] sobre o **Application Support** do app (`NSApplicationSupportDirectory`).
 *
 * Escolha deliberada de diretório: **não** é `Caches` (o sistema purga sob pressão de espaço — a foto
 * sumiria em silêncio, que é o defeito que esta peça existe para corrigir) e **não** é `Documents`
 * (visível ao usuário no app Arquivos quando o app declara compartilhamento; fila de upload é estado
 * interno, não documento). O conteúdo **não** é marcado como excluído do backup: um binário que só
 * existe na fila é a única cópia do usuário.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosBlobStore(private val directoryName: String) : BlobStore {

    private val fileManager = NSFileManager.defaultManager

    private val directory: String by lazy {
        val root = NSSearchPathForDirectoriesInDomains(
            directory = NSApplicationSupportDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true,
        ).firstOrNull() as? String ?: ""
        "$root/$directoryName"
    }

    private fun pathOf(id: String): String? =
        if (isValidBlobId(id)) "$directory/$id" else null

    private fun ensureDirectory(): Boolean {
        if (fileManager.fileExistsAtPath(directory)) return true
        return fileManager.createDirectoryAtPath(
            path = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }

    override suspend fun write(id: String, bytes: ByteArray): Boolean {
        val path = pathOf(id) ?: run {
            AppLogger.w(TAG, "id de blob inválido — nada foi gravado.")
            return false
        }
        if (bytes.isEmpty()) return false
        if (!ensureDirectory()) {
            AppLogger.e(TAG, "não foi possível criar o diretório de blobs")
            return false
        }
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        // `atomically = true` grava num temporário e renomeia: processo morto no meio da escrita
        // nunca deixa um blob truncado no lugar de uma foto válida.
        val ok = data.writeToFile(path, atomically = true)
        if (!ok) AppLogger.e(TAG, "falha ao gravar blob")
        return ok
    }

    override suspend fun read(id: String): ByteArray? {
        val path = pathOf(id) ?: return null
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        val length = data.length.toInt()
        if (length == 0) return ByteArray(0)
        val out = ByteArray(length)
        out.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
        return out
    }

    override suspend fun exists(id: String): Boolean {
        val path = pathOf(id) ?: return false
        return fileManager.fileExistsAtPath(path)
    }

    override suspend fun sizeOf(id: String): Long {
        val path = pathOf(id) ?: return 0L
        val attrs = fileManager.attributesOfItemAtPath(path, null) ?: return 0L
        return (attrs[NSFileSize] as? NSNumber)?.longValue ?: 0L
    }

    override suspend fun delete(id: String): Boolean {
        val path = pathOf(id) ?: return false
        if (!fileManager.fileExistsAtPath(path)) return false
        return fileManager.removeItemAtPath(path, null)
    }

    override suspend fun ids(): List<String> {
        val nomes = fileManager.contentsOfDirectoryAtPath(directory, null) ?: return emptyList()
        return nomes.filterIsInstance<String>().filter { isValidBlobId(it) }
    }

    private companion object {
        const val TAG = "BlobStore"
    }
}

actual fun createBlobStore(directoryName: String): BlobStore = IosBlobStore(directoryName)
