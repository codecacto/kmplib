package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.core.storage.isValidBlobId
import br.com.codecacto.kmplib.firebase.storage.UploadItem
import br.com.codecacto.kmplib.firebase.storage.UploadStatus
import kotlinx.serialization.Serializable

/**
 * **Um upload que ainda não subiu** — a linha da fila persistente ([RestUploadOutbox]).
 *
 * É só **metadado**: os bytes vivem no disco, no [BlobStore][br.com.codecacto.kmplib.core.storage.BlobStore],
 * referenciados por [PendingUploadPart.blobId]. Este objeto é serializado no `payload_json` do MESMO
 * espelho `synced_entity` que guarda as escritas de domínio — a fila de fotos usa a mesma outbox, o
 * mesmo escopo de conta, o mesmo histórico de entrega e o mesmo vocabulário de estado
 * ([RestRowState]) que o resto do offline-first, em vez de uma fila paralela com regras próprias.
 *
 * @param id identificador do upload (chave da linha e prefixo dos blobs).
 * @param path caminho do endpoint, podendo conter [UPLOAD_OWNER_PLACEHOLDER] (`{owner}`) — trocado
 *   pelo **id do servidor** do registro dono no momento do envio.
 * @param parts partes nomeadas do multipart (1 no caso comum; 2+ para `full`+`thumb`).
 * @param ownerEntity nome da entidade dona no espelho (`RestCrudEntity.name`). **Em branco = sem
 *   dono**: o upload sobe assim que puder, e [ownerHandle] é usado como está.
 * @param ownerHandle handle do registro dono como o app o conhece — o id local com que ele nasceu
 *   **ou** o id do servidor. A tradução é da lib.
 * @param label rótulo semântico do arquivo para a UI ("anverso"/"reverso"/"front"/"back"), nunca
 *   "foto 1/2".
 * @param createdAtMillis quando entrou na fila — define a **ordem FIFO** de drenagem (o espelho
 *   deixa `updated_at` nulo nas linhas sujas, então a ordem não pode vir do SQL).
 * @param nextAttemptAtMillis instante a partir do qual vale tentar de novo (recuo exponencial). `0`
 *   = pode tentar agora.
 * @param method verbo do envio — ver [UploadMethod].
 */
@Serializable
data class PendingUpload(
    val id: String,
    val path: String,
    val parts: List<PendingUploadPart>,
    val ownerEntity: String = "",
    val ownerHandle: String = "",
    val label: String = "",
    val createdAtMillis: Long = 0L,
    val nextAttemptAtMillis: Long = 0L,
    val method: UploadMethod = UploadMethod.POST,
) {
    /** Nome do primeiro arquivo — o que a UI exibe quando não há [label]. */
    val fileName: String get() = parts.firstOrNull()?.fileName.orEmpty()

    /** Soma dos tamanhos das partes (diagnóstico: "12 MB aguardando envio"). */
    val totalBytes: Long get() = parts.sumOf { it.sizeBytes }

    /** `true` se o envio depende de um registro dono ter subido antes. */
    val hasOwner: Boolean get() = ownerEntity.isNotBlank() && ownerHandle.isNotBlank()
}

/**
 * Uma parte nomeada do multipart, com os bytes **no disco**.
 *
 * @param blobId chave do binário no [BlobStore][br.com.codecacto.kmplib.core.storage.BlobStore].
 * @param fileName nome do arquivo enviado ao servidor (`filename=` do `Content-Disposition`).
 * @param mimeType tipo do conteúdo (`image/jpeg`…).
 * @param fieldName nome do campo do formulário (`file`, `full`, `thumb`…).
 * @param sizeBytes tamanho gravado — só diagnóstico; a verdade é o arquivo.
 */
@Serializable
data class PendingUploadPart(
    val blobId: String,
    val fileName: String,
    val mimeType: String,
    val fieldName: String = DEFAULT_UPLOAD_FIELD_NAME,
    val sizeBytes: Long = 0L,
)

/**
 * Verbo do envio. **`POST` cria** um recurso novo (`POST /v1/items/{owner}/photos`); **`PUT`
 * substitui** o binário de um recurso que já existe (`PUT /v1/me/professionals/{id}/photo`).
 *
 * A distinção importa na retentativa: repetir um `POST` aceito duplicaria a foto, e é por isso que
 * uma resposta ilegível é tratada como falha **terminal**, nunca retentada sozinha.
 */
@Serializable
enum class UploadMethod { POST, PUT }

/** Campo default do multipart (o mesmo do [DomainApiClient.postMultipart]). */
const val DEFAULT_UPLOAD_FIELD_NAME: String = "file"

/**
 * Marcador do id do registro dono dentro do [PendingUpload.path]. Trocado pelo **id do servidor** no
 * instante do envio — nunca pelo id local, que o backend não conhece.
 */
const val UPLOAD_OWNER_PLACEHOLDER: String = "{owner}"

/** `true` se o caminho depende do id do dono para ser montado. */
fun uploadPathRequiresOwner(path: String): Boolean = path.contains(UPLOAD_OWNER_PLACEHOLDER)

/**
 * Caminho final do upload, com [UPLOAD_OWNER_PLACEHOLDER] trocado por [ownerId].
 *
 * Devolve `null` quando o caminho exige o dono e [ownerId] está em branco: `/v1/items//photos` é uma
 * URL que o servidor até aceita rotear e que sobe a foto no lugar errado (ou devolve um 404
 * enganoso). Melhor não enviar.
 */
fun resolveUploadPath(path: String, ownerId: String): String? {
    if (!uploadPathRequiresOwner(path)) return path
    if (ownerId.isBlank()) return null
    return path.replace(UPLOAD_OWNER_PLACEHOLDER, ownerId)
}

/** Id do blob de uma parte — derivado do id do upload, para ser único e válido como nome de arquivo. */
fun uploadBlobId(uploadId: String, partIndex: Int): String = "$uploadId-$partIndex"

/** `true` se [uploadId] pode gerar ids de blob válidos (vira nome de arquivo). */
fun isValidUploadId(uploadId: String): Boolean =
    uploadId.isNotBlank() && isValidBlobId(uploadBlobId(uploadId, 0))

/**
 * **Para onde este upload vai agora** — o veredito que separa "ainda não é hora" de "não tem mais
 * destino".
 *
 * A distinção é o coração da amarração de ordem: um upload cujo dono **ainda não subiu** não é uma
 * falha (não conta tentativa, não vira erro na tela); ele simplesmente espera o próximo ciclo. Foi o
 * que a fábrica já pagou caro no ADR-0006 do "Todos a Bordo": enviar a FK com o id local faz o
 * backend recusar por `FOREIGN KEY`, a recusa é **terminal** e o registro se perde para sempre.
 */
sealed interface UploadTarget {

    /** Pode subir: [path] já com o id do servidor do dono. */
    data class Ready(val path: String) : UploadTarget

    /**
     * O registro dono existe localmente mas **ainda não migrou** para o id do servidor. Não é erro:
     * o upload fica na fila, sem contar tentativa, e sobe assim que o dono for aceito.
     */
    data object WaitingOwner : UploadTarget

    /**
     * O registro dono **não existe mais** no espelho e nunca migrou — o upload perdeu o destino
     * (o item foi descartado, ou apagado no servidor). Vira falha **visível**, com o binário
     * preservado: quem decide descartar é o usuário, não a lib.
     */
    data object MissingOwner : UploadTarget
}

/**
 * **Recuo entre tentativas** de um upload que falhou de forma retentável.
 *
 * Sem recuo, um servidor instável (ou uma rede de feira/depósito) faria a fila girar em loop a cada
 * ciclo de sync, gastando bateria e dados do usuário sem convergir.
 *
 * @param baseDelayMillis atraso após a 1ª falha.
 * @param maxDelayMillis teto do atraso (o dobro para de dobrar aqui).
 * @param maxAttempts depois de tantas falhas **retentáveis**, o upload vira falha **visível**
 *   ([RestRowState.Failed] com [RestUploadOutbox.RETRY_EXHAUSTED_CODE]) em vez de retentar para
 *   sempre em silêncio — o binário continua no disco e o app oferece "tentar de novo". `0` = sem
 *   limite (retenta indefinidamente).
 */
data class UploadRetryPolicy(
    val baseDelayMillis: Long = 30_000L,
    val maxDelayMillis: Long = 30L * 60_000L,
    val maxAttempts: Int = 8,
) {
    init {
        require(baseDelayMillis >= 0L) { "baseDelayMillis não pode ser negativo." }
        require(maxDelayMillis >= baseDelayMillis) { "maxDelayMillis deve ser >= baseDelayMillis." }
        require(maxAttempts >= 0) { "maxAttempts não pode ser negativo (0 = sem limite)." }
    }

    /** `true` se [attempts] falhas retentáveis já esgotaram a política. */
    fun isExhausted(attempts: Int): Boolean = maxAttempts > 0 && attempts >= maxAttempts
}

/**
 * Atraso até a próxima tentativa, dado o número de falhas já ocorridas: dobra a cada falha, limitado
 * a [UploadRetryPolicy.maxDelayMillis].
 *
 * **Sem jitter, de propósito:** a fila é sequencial e de um aparelho só — não há efeito manada a
 * espalhar —, e um atraso determinístico é o que permite testar o recuo sem relógio real.
 */
fun uploadRetryDelayMillis(attempts: Int, policy: UploadRetryPolicy = UploadRetryPolicy()): Long {
    if (attempts <= 0) return 0L
    var delay = policy.baseDelayMillis
    repeat(attempts - 1) {
        if (delay >= policy.maxDelayMillis) return policy.maxDelayMillis
        delay *= 2
    }
    return delay.coerceAtMost(policy.maxDelayMillis)
}

/** Desfecho de [RestUploadOutbox.enqueue]. */
sealed interface UploadEnqueueResult {

    /** O binário está no disco e a linha está na fila — **a foto já está salva**. */
    data class Queued(val upload: PendingUpload) : UploadEnqueueResult

    /**
     * Nada foi enfileirado. **Precisa chegar à UI:** é o único momento em que ainda dá para pedir a
     * foto de novo, em vez de descobrir depois que ela nunca existiu.
     */
    data class Rejected(val reason: UploadRejectReason, val message: String) : UploadEnqueueResult

    /** O upload enfileirado, ou `null` se foi recusado. */
    val uploadOrNull: PendingUpload? get() = (this as? Queued)?.upload
}

/** Por que um [RestUploadOutbox.enqueue] foi recusado. */
enum class UploadRejectReason {
    /** Conteúdo vazio, id inválido, caminho com `{owner}` sem dono informado. */
    InvalidRequest,

    /** O disco recusou a escrita (sem espaço, sem permissão) — a foto **não** está guardada. */
    StorageFailed,
}

/**
 * Adapta a linha da fila persistente ao modelo de UI do upload em memória, para os composables
 * `UploadProgressItem`/`UploadQueueView` (ui/components) renderizarem esta fila sem mudança.
 *
 * **Sem progresso granular** (multipart não expõe fração de bytes) e **sem estado `UPLOADING`**: o
 * envio acontece dentro do ciclo de sync, não sob o olhar da tela. O que a UI mostra é o que
 * realmente importa aqui — "aguardando envio" ou "não foi possível enviar".
 */
fun RestRow<PendingUpload>.toUploadItem(): UploadItem = UploadItem(
    id = model.id,
    fileName = model.label.ifBlank { model.fileName },
    fraction = 0f,
    status = if (state.isFailed) UploadStatus.FAILED else UploadStatus.PENDING,
    errorMessage = (state as? RestRowState.Failed)?.message,
)
