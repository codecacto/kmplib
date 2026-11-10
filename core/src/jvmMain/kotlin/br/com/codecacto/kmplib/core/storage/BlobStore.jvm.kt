package br.com.codecacto.kmplib.core.storage

import br.com.codecacto.kmplib.core.context.DesktopAppContext
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Desktop (2.353.0): `<pasta de dados do app>/<directoryName>` — `%LOCALAPPDATA%` no Windows, que é
 * LOCAL (não itinerante no perfil do domínio). Por isso [excludeFromBackup] não muda a pasta aqui:
 * não há "backup em nuvem do app" no desktop do qual sair. Escrita atômica e durável.
 */
actual fun createBlobStore(directoryName: String, excludeFromBackup: Boolean): BlobStore =
    DesktopFileBlobStore(DesktopAppContext.appDataDirectory().resolve(directoryName))

internal class DesktopFileBlobStore(
    private val directory: Path,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : BlobStore {

    private fun fileOf(id: String): Path? = if (isValidBlobId(id)) directory.resolve(id) else null

    override suspend fun write(id: String, bytes: ByteArray): Boolean = withContext(io) {
        val file = fileOf(id) ?: run {
            AppLogger.w(TAG, "id de blob inválido — nada foi gravado.")
            return@withContext false
        }
        if (bytes.isEmpty()) return@withContext false
        runCatching { writeFileDurably(file, bytes); true }.getOrElse {
            AppLogger.e(TAG, "falha ao gravar blob", it)
            false
        }
    }

    override suspend fun read(id: String): ByteArray? = withContext(io) {
        val file = fileOf(id)?.takeIf { it.isRegularFile() } ?: return@withContext null
        runCatching { Files.readAllBytes(file) }.getOrElse {
            AppLogger.e(TAG, "falha ao ler blob", it)
            null
        }
    }

    override suspend fun exists(id: String): Boolean = withContext(io) { fileOf(id)?.isRegularFile() == true }

    override suspend fun sizeOf(id: String): Long = withContext(io) {
        fileOf(id)?.takeIf { it.isRegularFile() }?.let { runCatching { Files.size(it) }.getOrDefault(0L) } ?: 0L
    }

    override suspend fun delete(id: String): Boolean = withContext(io) {
        val file = fileOf(id) ?: return@withContext false
        runCatching { Files.deleteIfExists(file) }.getOrDefault(false)
    }

    override suspend fun ids(): List<String> = withContext(io) {
        if (!Files.isDirectory(directory)) return@withContext emptyList()
        runCatching {
            directory.listDirectoryEntries()
                .filter { it.isRegularFile() && !it.name.startsWith("~") }
                .map { it.name }
        }.getOrDefault(emptyList())
    }

    override suspend fun localFilePath(id: String): String? = withContext(io) {
        fileOf(id)?.takeIf { it.isRegularFile() }?.toAbsolutePath()?.toString()
    }

    private companion object {
        const val TAG = "BlobStore"
    }
}
