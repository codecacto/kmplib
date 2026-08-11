package br.com.codecacto.kmplib.core.storage

import android.content.Context
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference

/**
 * Holder do Context do [BlobStore] (mesmo padrão do [SyncDatabaseHolder]
 * [br.com.codecacto.kmplib.sync.SyncDatabaseHolder]). Inicializado por `KmpLib.init(context)` /
 * `KmpLib.initSync(context)`.
 */
object BlobStoreHolder {
    private var contextRef: WeakReference<Context>? = null

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    internal fun requireContext(): Context = contextRef?.get()
        ?: error(
            "BlobStore: Context não inicializado. Chame KmpLib.init(context) " +
                "(ou KmpLib.initSync(context)) no Application.onCreate().",
        )
}

/**
 * [BlobStore] sobre o **armazenamento interno privado** do app.
 *
 * Usa `filesDir`, **não** `cacheDir`: o Android apaga o cache sob pressão de espaço, e um binário que
 * ainda não subiu é a única cópia do usuário. Toda operação roda em [Dispatchers.IO].
 */
internal class FileBlobStore(
    private val directory: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : BlobStore {

    private fun fileOf(id: String): File? =
        if (isValidBlobId(id)) File(directory, id) else null

    private fun ensureDirectory(): Boolean =
        directory.isDirectory || directory.mkdirs()

    override suspend fun write(id: String, bytes: ByteArray): Boolean = withContext(io) {
        val file = fileOf(id) ?: run {
            AppLogger.w(TAG, "id de blob inválido — nada foi gravado.")
            return@withContext false
        }
        if (bytes.isEmpty()) return@withContext false
        runCatching {
            if (!ensureDirectory()) error("não foi possível criar ${directory.path}")
            // Grava em arquivo temporário e renomeia: um processo morto no meio da escrita nunca
            // deixa um blob truncado ocupando o lugar de uma foto válida.
            val temp = File(directory, "$TEMP_PREFIX$id")
            temp.writeBytes(bytes)
            if (!temp.renameTo(file)) {
                file.writeBytes(bytes)
                temp.delete()
            }
            true
        }.getOrElse {
            AppLogger.e(TAG, "falha ao gravar blob", it)
            false
        }
    }

    override suspend fun read(id: String): ByteArray? = withContext(io) {
        val file = fileOf(id) ?: return@withContext null
        runCatching { if (file.isFile) file.readBytes() else null }.getOrElse {
            AppLogger.e(TAG, "falha ao ler blob", it)
            null
        }
    }

    override suspend fun exists(id: String): Boolean = withContext(io) {
        fileOf(id)?.isFile == true
    }

    override suspend fun sizeOf(id: String): Long = withContext(io) {
        fileOf(id)?.takeIf { it.isFile }?.length() ?: 0L
    }

    override suspend fun delete(id: String): Boolean = withContext(io) {
        val file = fileOf(id) ?: return@withContext false
        runCatching { file.isFile && file.delete() }.getOrDefault(false)
    }

    override suspend fun ids(): List<String> = withContext(io) {
        directory.listFiles()
            ?.filter { it.isFile && !it.name.startsWith(TEMP_PREFIX) }
            ?.map { it.name }
            .orEmpty()
    }

    private companion object {
        const val TAG = "BlobStore"

        /**
         * Prefixo do arquivo temporário da escrita atômica. É `~` de propósito: o caractere **não**
         * é aceito por [isValidBlobId], então um temporário nunca colide com um blob real nem some
         * da listagem por engano.
         */
        const val TEMP_PREFIX = "~"
    }
}

actual fun createBlobStore(directoryName: String): BlobStore =
    FileBlobStore(File(BlobStoreHolder.requireContext().filesDir, directoryName))
