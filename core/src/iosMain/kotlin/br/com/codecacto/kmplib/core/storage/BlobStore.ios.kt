package br.com.codecacto.kmplib.core.storage

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
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
 * interno, não documento).
 *
 * **Backup (2.217.0):** com `excludeFromBackup = true` (default) o **diretório** recebe
 * `NSURLIsExcludedFromBackupKey` — a marcação no diretório cobre todo arquivo dentro dele, inclusive
 * o gravado por uma versão anterior da lib. O atributo é aplicado nos dois sentidos: desligar o flag
 * devolve o diretório ao backup. O caminho não muda, então nada precisa ser movido.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosBlobStore(
    private val directoryName: String,
    private val excludeFromBackup: Boolean = true,
) : BlobStore {

    private val fileManager = NSFileManager.defaultManager

    private val directory: String by lazy {
        val root = NSSearchPathForDirectoriesInDomains(
            directory = NSApplicationSupportDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true,
        ).firstOrNull() as? String ?: ""
        "$root/$directoryName"
    }

    /** O atributo de backup já foi aplicado ao diretório existente nesta instância? */
    private var backupAttributeApplied = false

    private fun pathOf(id: String): String? {
        applyBackupAttributeOnce()
        return if (isValidBlobId(id)) "$directory/$id" else null
    }

    private fun ensureDirectory(): Boolean {
        if (fileManager.fileExistsAtPath(directory)) {
            applyBackupAttributeOnce()
            return true
        }
        val criado = fileManager.createDirectoryAtPath(
            path = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        if (criado) applyBackupAttributeOnce()
        return criado
    }

    /**
     * Marca o diretório (se já existir) dentro/fora do backup. Roda na primeira operação de cada
     * instância — e não só ao criar o diretório — porque o diretório de quem atualizou a lib já existe,
     * com fotos dentro, e é exatamente ele que precisa sair do iCloud.
     */
    private fun applyBackupAttributeOnce() {
        if (backupAttributeApplied) return
        if (!fileManager.fileExistsAtPath(directory)) return
        val ok = NSURL.fileURLWithPath(directory, isDirectory = true).setResourceValue(
            NSNumber(bool = excludeFromBackup),
            NSURLIsExcludedFromBackupKey,
            null,
        )
        if (!ok && excludeFromBackup) {
            AppLogger.e(TAG, "não foi possível marcar o diretório de blobs como excluído do backup do iCloud")
        }
        backupAttributeApplied = ok
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
        applyBackupAttributeOnce()
        val nomes = fileManager.contentsOfDirectoryAtPath(directory, null) ?: return emptyList()
        return nomes.filterIsInstance<String>().filter { isValidBlobId(it) }
    }

    private companion object {
        const val TAG = "BlobStore"
    }
}

actual fun createBlobStore(directoryName: String, excludeFromBackup: Boolean): BlobStore =
    IosBlobStore(directoryName, excludeFromBackup)
