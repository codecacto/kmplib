package br.com.codecacto.kmplib.sync.direct

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.serialization.json.Json

/**
 * Onde a [DirectUploadOutbox] guarda cada envio: o estado (JSON) e o arquivo. Uma pasta por envio,
 * `<raiz>/<id>/job.json` + `<raiz>/<id>/media` (+ os trechos `part-N` que o iOS envia em segundo plano).
 *
 * O default é [createDirectUploadStorage]; a interface existe para teste e para o app que precise
 * de outro lugar.
 */
interface DirectUploadStorage {
    /** Todos os envios no disco, de **todas** as contas (a fila filtra pela conta logada). */
    suspend fun loadAll(): List<DirectUploadJob>

    /** Grava o estado de [job] por inteiro (nunca meio JSON). */
    suspend fun save(job: DirectUploadJob): Boolean

    /**
     * Traz o arquivo [sourcePath] para a pasta do envio [jobId] — **movendo** ([move] = `true`) ou
     * copiando. Devolve o tamanho, ou `null` se falhou (nada fica pela metade).
     */
    suspend fun importMedia(jobId: String, sourcePath: String, move: Boolean): Long?

    /** Caminho absoluto do arquivo do envio (para o transporte ler). */
    fun mediaPath(jobId: String): String

    /** Caminho do trecho da parte [partNumber] (iOS: o arquivo que a sessão de fundo envia). */
    fun partPath(jobId: String, partNumber: Int): String

    /** `true` se o arquivo do envio ainda está lá, do tamanho esperado. */
    suspend fun hasMedia(jobId: String, expectedSize: Long): Boolean

    /** Apaga a pasta inteira do envio (estado, arquivo, trechos). Idempotente. */
    suspend fun delete(jobId: String): Boolean

    /** Tamanho de um arquivo qualquer (`-1` se não existe) — para dizer "arquivo sumiu" × "disco cheio". */
    suspend fun sizeOf(path: String): Long

    /** Pastas de envio sem `job.json` legível — resíduo de processo morto no meio do `enqueue`. */
    suspend fun orphanIds(): List<String>
}

/**
 * Cria o [DirectUploadStorage] da plataforma em [directoryName], dentro do armazenamento privado e
 * **fora do backup** (ver [platformDirectUploadRoot]).
 *
 * @throws IllegalStateException no Android sem `initKmpLibSync(context)`.
 */
fun createDirectUploadStorage(directoryName: String = DEFAULT_DIRECT_UPLOAD_DIRECTORY): DirectUploadStorage {
    require(isValidDirectUploadId(directoryName)) { "nome de diretório inválido" }
    val raiz = platformDirectUploadRoot(directoryName)
        ?: throw IllegalStateException("fila de envio: armazenamento indisponível (chame initKmpLibSync no Android)")
    return FileDirectUploadStorage(raiz, platformDirectUploadFiles())
}

/** [DirectUploadStorage] sobre [DirectUploadFiles] — uma pasta por envio. */
internal class FileDirectUploadStorage(
    private val root: String,
    private val files: DirectUploadFiles,
    private val json: Json = DirectUploadJson,
) : DirectUploadStorage {

    private fun dir(jobId: String) = joinPath(root, jobId)
    private fun jobFile(jobId: String) = joinPath(dir(jobId), JOB_FILE)

    override suspend fun loadAll(): List<DirectUploadJob> =
        files.list(root).filter(::isValidDirectUploadId).mapNotNull { id ->
            val texto = files.readText(jobFile(id)) ?: return@mapNotNull null
            runCatching { json.decodeFromString(DirectUploadJob.serializer(), texto) }
                .onFailure { AppLogger.w(TAG, "estado de envio ilegível; ignorado até a varredura") }
                .getOrNull()
                ?.takeIf { it.id == id }
        }

    override suspend fun save(job: DirectUploadJob): Boolean {
        if (!isValidDirectUploadId(job.id)) return false
        if (!files.mkdirs(dir(job.id))) return false
        return files.writeTextAtomic(jobFile(job.id), json.encodeToString(DirectUploadJob.serializer(), job))
    }

    override suspend fun importMedia(jobId: String, sourcePath: String, move: Boolean): Long? {
        if (!isValidDirectUploadId(jobId)) return null
        val tamanho = files.size(sourcePath)
        if (tamanho <= 0L) return null
        if (!files.mkdirs(dir(jobId))) return null
        val destino = mediaPath(jobId)
        val ok = if (move) files.move(sourcePath, destino) else files.copy(sourcePath, destino)
        if (!ok || files.size(destino) != tamanho) {
            // Nada pela metade: um arquivo truncado subiria um vídeo corrompido que o servidor só
            // recusaria no fim. Se a cópia/movimento falhou, o original segue com quem chamou.
            if (files.exists(sourcePath)) files.deleteRecursively(destino)
            return null
        }
        return tamanho
    }

    override fun mediaPath(jobId: String): String = joinPath(dir(jobId), MEDIA_FILE)

    override fun partPath(jobId: String, partNumber: Int): String = joinPath(dir(jobId), "part-$partNumber")

    override suspend fun hasMedia(jobId: String, expectedSize: Long): Boolean = files.size(mediaPath(jobId)) == expectedSize

    override suspend fun sizeOf(path: String): Long = files.size(path)

    override suspend fun delete(jobId: String): Boolean =
        isValidDirectUploadId(jobId) && files.deleteRecursively(dir(jobId))

    override suspend fun orphanIds(): List<String> =
        files.list(root).filter(::isValidDirectUploadId).filter { id ->
            val texto = files.readText(jobFile(id))
            texto == null || runCatching { json.decodeFromString(DirectUploadJob.serializer(), texto) }.isFailure
        }

    private companion object {
        const val TAG = "DirectUploadStorage"
        const val JOB_FILE = "job.json"
        const val MEDIA_FILE = "media"
    }
}

internal val DirectUploadJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
