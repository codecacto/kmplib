package br.com.codecacto.kmplib.video.download

import br.com.codecacto.kmplib.core.prefs.AppPreferences
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.video.VideoStreamKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * O que a **lib** precisa lembrar de um download entre uma execução do app e a próxima.
 *
 * Não é o andamento: quem guarda bytes baixados e retomada é o subsistema da plataforma (o índice
 * do `DownloadManager` do Media3; o pacote parcial do `AVAssetDownloadTask`). O que **não** existe
 * em nenhum dos dois é o que o produto precisa: qual curso é este item, até quando o direito vale,
 * qual era o título. Sem isso, a tela de Downloads sobrevive a um reinício mostrando uma lista de
 * identificadores sem nome — e `removeGroup("curso-7")` não teria como saber o que apagar.
 *
 * @param localPath onde a mídia foi parar. **Só o iOS usa**: lá o `.movpkg` fica num caminho que o
 *   sistema escolhe e que **precisa ser relido do disco na volta**, senão o arquivo baixado existe
 *   e o app não sabe onde. No Android é `null` — o dono dos bytes é o cache do Media3, endereçado
 *   pela chave de cache, não por caminho.
 * @param pausedByUser a pausa **é do produto, não do sistema**, e por isso é persistida aqui. No
 *   Android o Media3 também a guarda (`stopReason`); no iOS não há onde, e sem este campo tudo o
 *   que estava pausado voltaria baixando na próxima abertura do app.
 */
@Serializable
data class MediaDownloadRecord(
    val id: String,
    val url: String,
    val kind: VideoStreamKind = VideoStreamKind.Auto,
    val title: String? = null,
    val groupId: String? = null,
    val quality: MediaDownloadQuality = MediaDownloadQuality.Standard,
    val estimatedBytes: Long = 0L,
    val expiresAtMillis: Long? = null,
    val completed: Boolean = false,
    val pausedByUser: Boolean = false,
    val localPath: String? = null,
    val createdAtMillis: Long = 0L,
) {
    /** O pedido de origem, para reenfileirar depois de uma renovação de URL ou de um reinício. */
    fun toRequest(): MediaDownloadRequest = MediaDownloadRequest(
        id = id,
        url = url,
        kind = kind,
        title = title,
        groupId = groupId,
        quality = quality,
        estimatedBytes = estimatedBytes,
        expiresAtMillis = expiresAtMillis,
    )
}

/** Cria o registro inicial de um pedido. */
fun MediaDownloadRequest.toRecord(nowMillis: Long): MediaDownloadRecord = MediaDownloadRecord(
    id = id,
    url = url,
    kind = kind,
    title = title,
    groupId = groupId,
    quality = quality,
    estimatedBytes = estimatedBytes,
    expiresAtMillis = expiresAtMillis,
    createdAtMillis = nowMillis,
)

/** Onde os [MediaDownloadRecord] moram. Trocável para teste — ver [InMemoryMediaDownloadStore]. */
interface MediaDownloadStore {
    suspend fun all(): List<MediaDownloadRecord>
    suspend fun get(id: String): MediaDownloadRecord?
    suspend fun put(record: MediaDownloadRecord)
    suspend fun remove(id: String)
    suspend fun clear()
}

/** Implementação de memória — testes e quem não quer durabilidade. */
class InMemoryMediaDownloadStore(inicial: List<MediaDownloadRecord> = emptyList()) : MediaDownloadStore {
    private val registros = LinkedHashMap<String, MediaDownloadRecord>()

    init {
        inicial.forEach { registros[it.id] = it }
    }

    override suspend fun all(): List<MediaDownloadRecord> = registros.values.toList()
    override suspend fun get(id: String): MediaDownloadRecord? = registros[id]
    override suspend fun put(record: MediaDownloadRecord) {
        registros[record.id] = record
    }

    override suspend fun remove(id: String) {
        registros.remove(id)
    }

    override suspend fun clear() = registros.clear()
}

/**
 * Store durável sobre o [AppPreferences] da lib (SharedPreferences no Android, `NSUserDefaults` no
 * iOS).
 *
 * **A lista inteira vai num JSON, numa chave só** — de propósito. Uma chave por item pareceria mais
 * "banco de dados" e traria o problema que um banco resolve e as preferências não: escrita
 * parcial. Se o processo morre entre gravar o item e gravar o índice, sobra registro órfão sem
 * ninguém para achá-lo. Com um documento só a gravação é atômica, e a escala é a certa: uma tela de
 * Downloads tem dezenas de itens, não milhares.
 *
 * JSON ilegível (formato antigo, valor corrompido) **não derruba o app**: vira lista vazia com o
 * motivo no log. O pior caso é a tela de Downloads perder os nomes — e não o app não abrir.
 */
class PreferencesMediaDownloadStore(
    private val prefs: AppPreferences,
    private val key: String = DEFAULT_KEY,
) : MediaDownloadStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun all(): List<MediaDownloadRecord> {
        val cru = prefs.getString(key, "")
        if (cru.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<MediaDownloadRecord>>(cru) }
            .getOrElse {
                AppLogger.w(DOWNLOAD_TAG, "Registro de downloads ilegível — recomeçando vazio: ${it.message}")
                emptyList()
            }
    }

    override suspend fun get(id: String): MediaDownloadRecord? = all().firstOrNull { it.id == id }

    override suspend fun put(record: MediaDownloadRecord) {
        val atual = all().filterNot { it.id == record.id }
        gravar(atual + record)
    }

    override suspend fun remove(id: String) {
        gravar(all().filterNot { it.id == id })
    }

    override suspend fun clear() {
        prefs.remove(key)
    }

    private suspend fun gravar(registros: List<MediaDownloadRecord>) {
        runCatching { prefs.setString(key, json.encodeToString(registros)) }
            .onFailure { AppLogger.e(DOWNLOAD_TAG, "Falha ao gravar o registro de downloads: ${it.message}") }
    }

    companion object {
        /** A chave única em que a lista inteira é gravada. */
        const val DEFAULT_KEY: String = "kmplib_media_downloads"
    }
}

/** A etiqueta de log do subsistema de download. */
internal const val DOWNLOAD_TAG = "KmpLibMediaDownload"
