package br.com.codecacto.kmplib.core.storage

import android.content.Context
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * Usa `noBackupFilesDir` (default, fora do backup) ou `filesDir` — **nunca** `cacheDir`: o Android
 * apaga o cache sob pressão de espaço, e um binário que ainda não subiu é a única cópia do usuário.
 * Toda operação roda em [Dispatchers.IO].
 *
 * @param legacyDirectory o diretório do **outro** modo de backup. Na primeira operação, o que houver
 *   nele é movido para [directory] — é o que faz a troca de `excludeFromBackup` (inclusive a mudança
 *   de default da 2.217.0) não perder a foto que estava na fila no momento da atualização.
 */
internal class FileBlobStore(
    private val directory: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val legacyDirectory: File? = null,
) : BlobStore {

    private val adoptionMutex = Mutex()

    @Volatile
    private var adopted: Boolean = legacyDirectory == null

    private fun fileOf(id: String): File? =
        if (isValidBlobId(id)) File(directory, id) else null

    private fun ensureDirectory(): Boolean =
        directory.isDirectory || directory.mkdirs()

    /** Move (uma vez por instância) o que estiver no diretório do outro modo de backup. */
    private suspend fun adoptLegacyOnce() {
        if (adopted) return
        adoptionMutex.withLock {
            if (adopted) return
            adopted = adoptLegacy()
        }
    }

    /**
     * @return `true` quando não há mais nada a adotar. Até a 2.217.0 devolvia `true` também com
     *   arquivo que NÃO saiu do diretório antigo — e nunca mais tentava: a foto ficava no diretório
     *   com backup, fora da limpeza e da varredura de órfãos. Agora `false` = tenta de novo na
     *   próxima operação, e enquanto isso [delete]/[ids]/[read] enxergam o diretório antigo.
     */
    private fun adoptLegacy(): Boolean {
        val legacy = legacyDirectory ?: return true
        if (!legacy.isDirectory) return true
        val arquivos = legacy.listFiles() ?: return true
        if (arquivos.isEmpty()) {
            legacy.delete()
            return true
        }
        if (!ensureDirectory()) {
            AppLogger.e(TAG, "não foi possível criar ${directory.path}; binários antigos ficam onde estão")
            return false
        }
        var pendente = false
        arquivos.forEach { origem ->
            if (!origem.isFile) return@forEach
            // Temporário de escrita interrompida: nunca foi um blob válido.
            if (origem.name.startsWith(TEMP_PREFIX)) {
                origem.delete()
                return@forEach
            }
            val destino = File(directory, origem.name)
            val ok = when {
                // Já existe no destino (gravado depois): a cópia antiga é a que sobra — e é
                // justamente a que está no diretório errado de backup.
                destino.exists() -> origem.delete()
                origem.renameTo(destino) -> true
                else -> runCatching {
                    origem.copyTo(destino, overwrite = false)
                    origem.delete()
                }.getOrDefault(false)
            }
            if (!ok) pendente = true
        }
        if (pendente) {
            AppLogger.w(TAG, "alguns binários não puderam ser movidos de diretório; nova tentativa na próxima abertura")
        } else {
            legacy.delete()
        }
        return !pendente
    }

    /** O mesmo blob no diretório do outro modo — só enquanto a adoção não terminou. */
    private fun legacyFileOf(id: String): File? =
        if (!adopted && isValidBlobId(id)) legacyDirectory?.let { File(it, id) } else null

    /** Blob a ler: o do diretório atual, ou o que ainda não saiu do antigo. */
    private fun existingFileOf(id: String): File? =
        fileOf(id)?.takeIf { it.isFile } ?: legacyFileOf(id)?.takeIf { it.isFile }

    override suspend fun write(id: String, bytes: ByteArray): Boolean = withContext(io) {
        adoptLegacyOnce()
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
        adoptLegacyOnce()
        val file = existingFileOf(id) ?: return@withContext null
        runCatching { file.readBytes() }.getOrElse {
            AppLogger.e(TAG, "falha ao ler blob", it)
            null
        }
    }

    override suspend fun exists(id: String): Boolean = withContext(io) {
        adoptLegacyOnce()
        existingFileOf(id) != null
    }

    override suspend fun sizeOf(id: String): Long = withContext(io) {
        adoptLegacyOnce()
        existingFileOf(id)?.length() ?: 0L
    }

    override suspend fun delete(id: String): Boolean = withContext(io) {
        adoptLegacyOnce()
        val file = fileOf(id) ?: return@withContext false
        val atual = runCatching { file.isFile && file.delete() }.getOrDefault(false)
        // A cópia que não conseguiu sair do diretório antigo também é desta conta/fila: sai junto.
        val antiga = legacyFileOf(id)?.let { runCatching { it.isFile && it.delete() }.getOrDefault(false) } ?: false
        atual || antiga
    }

    override suspend fun ids(): List<String> = withContext(io) {
        adoptLegacyOnce()
        val pastas = listOfNotNull(directory, legacyDirectory.takeIf { !adopted })
        pastas.flatMap { pasta ->
            pasta.listFiles()
                ?.filter { it.isFile && !it.name.startsWith(TEMP_PREFIX) }
                ?.map { it.name }
                .orEmpty()
        }.distinct()
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

/**
 * `excludeFromBackup = true` usa `noBackupFilesDir` — o diretório que o Android exclui do Auto
 * Backup **e** da transferência entre aparelhos por definição, sem depender de o app declarar
 * `dataExtractionRules`. O diretório do outro modo vira [FileBlobStore.legacyDirectory].
 */
actual fun createBlobStore(directoryName: String, excludeFromBackup: Boolean): BlobStore {
    val context = BlobStoreHolder.requireContext()
    val comBackup = File(context.filesDir, directoryName)
    val semBackup = File(context.noBackupFilesDir, directoryName)
    return if (excludeFromBackup) {
        FileBlobStore(directory = semBackup, legacyDirectory = comBackup)
    } else {
        FileBlobStore(directory = comBackup, legacyDirectory = semBackup)
    }
}
