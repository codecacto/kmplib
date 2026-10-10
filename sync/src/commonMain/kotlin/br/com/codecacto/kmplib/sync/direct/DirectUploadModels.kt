package br.com.codecacto.kmplib.sync.direct

import br.com.codecacto.kmplib.core.util.KmpLibCoreInternalApi
import br.com.codecacto.kmplib.core.util.redactMediaUrl
import kotlinx.serialization.Serializable

/** Diretório default da fila de envio direto (dentro do armazenamento privado e fora do backup). */
const val DEFAULT_DIRECT_UPLOAD_DIRECTORY: String = "kmplib_direct_uploads"

/** Nome default da fila (vira o nome do trabalho no WorkManager e o id da sessão de fundo no iOS). */
const val DEFAULT_DIRECT_UPLOAD_OUTBOX_NAME: String = "default"

/**
 * Em que pé está um envio da [DirectUploadOutbox].
 *
 * A ordem é a do caminho feliz: [QUEUED] → [UPLOADING] → [COMPLETING] → (sai da fila). [FAILED] é a
 * recusa que **não** se resolve sozinha (4xx do servidor, cota, tentativas esgotadas): o arquivo
 * fica no aparelho até a pessoa mandar de novo ([DirectUploadOutbox.retry]) ou desistir
 * ([DirectUploadOutbox.discard]).
 */
@Serializable
enum class DirectUploadStatus {
    /** Na fila, sem sessão de envio aberta no servidor. */
    QUEUED,

    /** Sessão multipart aberta; partes subindo (ou esperando rede/vez). */
    UPLOADING,

    /** Todas as partes subiram; falta o servidor fechar o envio (`complete`). */
    COMPLETING,

    /** Recusado — parado até [DirectUploadOutbox.retry] ou [DirectUploadOutbox.discard]. */
    FAILED,
}

/**
 * A sessão multipart aberta no servidor (o `VideoUploadDto` do contrato): o id do envio no storage,
 * o tamanho de cada parte e as URLs pré-assinadas que ainda valem.
 *
 * @param uploadId id do envio multipart (vai nos caminhos `…/uploads/{uploadId}/parts|complete|abort`).
 * @param remoteId id do recurso criado no servidor (o `videoId`), quando o contrato devolve.
 * @param partSizeBytes tamanho de cada parte (a última pode ser menor) — decisão do SERVIDOR.
 * @param partCount quantas partes; a soma tem de dar o tamanho do arquivo.
 * @param partUrls URLs pré-assinadas de escrita, por número de parte (1-based). São credencial de
 *   curta duração: ficam no armazenamento privado do app e **nunca** saem em log nem em `toString`.
 * @param urlsExpireAtMillis quando as [partUrls] vencem (`null` = o servidor não disse).
 */
@Serializable
data class DirectUploadSession(
    val uploadId: String,
    val remoteId: String? = null,
    val partSizeBytes: Long,
    val partCount: Int,
    val partUrls: Map<Int, String> = emptyMap(),
    val urlsExpireAtMillis: Long? = null,
) {
    override fun toString(): String =
        "DirectUploadSession(remoteId=${remoteId != null}, partSizeBytes=$partSizeBytes, partCount=$partCount, " +
            "partUrls=${partUrls.size}, urlsExpireAtMillis=$urlsExpireAtMillis)"
}

/** Por que um envio parou ([DirectUploadStatus.FAILED]) ou a última falha retentável. */
@Serializable
data class DirectUploadFailure(
    /** Status HTTP, ou uma sentinela negativa de [DirectUploadOutbox] (arquivo sumiu, tentativas esgotadas…). */
    val code: Int,
    /** O `code` de negócio do servidor (`VIDEO_REQUEST_CLOSED`), quando veio. */
    val serverCode: String? = null,
    /** Frase para a tela — a do servidor em 4xx, a local no resto. Nunca com URL ou caminho. */
    val message: String? = null,
    /** `true` = recusa do plano (402): a saída é o paywall, não "tentar de novo". */
    val isQuota: Boolean = false,
)

/**
 * Um envio na fila — **persistido** a cada passo (por isso sobrevive ao processo morto).
 *
 * @param id id do envio no aparelho (gerado no cliente; vira nome de pasta). Use-o para correlacionar
 *   o envio com a tela que o originou.
 * @param accountId a conta que enfileirou. **Só essa conta envia**: no aparelho compartilhado, o
 *   vídeo de um não sobe com o token de outro ([DirectUploadOutbox] compara com a conta logada).
 * @param kind rótulo do app para escolher o caminho no [DirectUploadBackend] (`"student-video"`).
 * @param targetId o recurso do app a que o envio pertence (o `requestId` do pedido de vídeo).
 * @param contentType MIME do arquivo (`video/mp4`).
 * @param sizeBytes tamanho do arquivo no momento em que entrou na fila.
 * @param durationSeconds duração inteira, arredondada para cima, quando é mídia com tempo.
 * @param metadata campos extras do app, enviados ao servidor pelo backend (sem dado sensível: ficam
 *   no disco junto do envio).
 * @param wifiOnly só envia em rede não tarifada ("só no Wi-Fi").
 * @param completedParts ETag de cada parte já aceita pelo storage — é o que faz a **retomada**: após
 *   o processo morrer, só sobe o que falta.
 * @param attempts falhas retentáveis seguidas (zera a cada avanço).
 * @param nextAttemptAtMillis antes disso, o envio espera (recuo exponencial).
 */
@Serializable
data class DirectUploadJob(
    val id: String,
    val accountId: String,
    val kind: String,
    val targetId: String,
    val contentType: String,
    val sizeBytes: Long,
    val durationSeconds: Int? = null,
    val fileName: String,
    val metadata: Map<String, String> = emptyMap(),
    val wifiOnly: Boolean = false,
    val createdAtMillis: Long,
    val status: DirectUploadStatus = DirectUploadStatus.QUEUED,
    val session: DirectUploadSession? = null,
    val completedParts: Map<Int, String> = emptyMap(),
    val attempts: Int = 0,
    val nextAttemptAtMillis: Long = 0L,
    val failure: DirectUploadFailure? = null,
) {
    /** Bytes já aceitos pelo storage (soma das partes concluídas). */
    val uploadedBytes: Long get() = directUploadedBytes(this)

    /** Fração enviada, de 0 a 1 (`0` antes de haver sessão). */
    val progress: Float get() = if (sizeBytes <= 0L) 0f else (uploadedBytes.toDouble() / sizeBytes).toFloat().coerceIn(0f, 1f)

    /** Sem caminho, sem URL e sem metadado do app (pode ser dado pessoal). */
    override fun toString(): String =
        "DirectUploadJob(id=$id, kind=$kind, status=$status, sizeBytes=$sizeBytes, wifiOnly=$wifiOnly, " +
            "parts=${completedParts.size}/${session?.partCount ?: 0}, attempts=$attempts, failure=${failure?.code})"
}

/** O que enfileirar — entrada de [DirectUploadOutbox.enqueue]. */
data class DirectUploadRequest(
    /** Caminho ABSOLUTO do arquivo pronto (ex.: `PreparedVideo.path`). */
    val sourcePath: String,
    val kind: String,
    val targetId: String,
    val contentType: String,
    val fileName: String = "media",
    val durationSeconds: Int? = null,
    val metadata: Map<String, String> = emptyMap(),
    val wifiOnly: Boolean = false,
    /**
     * `true` (default) = o arquivo é **movido** para a área da fila: sai da pasta temporária (onde a
     * varredura de 1 h o apagaria) e passa a pertencer à fila, que o apaga ao concluir ou descartar.
     * `false` = copia e deixa o original com quem chamou.
     */
    val moveSource: Boolean = true,
) {
    override fun toString(): String =
        "DirectUploadRequest(kind=$kind, contentType=$contentType, wifiOnly=$wifiOnly, moveSource=$moveSource)"
}

/** Desfecho de [DirectUploadOutbox.enqueue]. */
sealed interface DirectUploadEnqueueResult {
    /** O arquivo está na área da fila e o envio está persistido — já pode fechar o app. */
    data class Queued(val job: DirectUploadJob) : DirectUploadEnqueueResult

    /** Nada entrou na fila. [reason] diz qual saída mostrar. */
    data class Rejected(val reason: DirectUploadRejectReason) : DirectUploadEnqueueResult
}

/** Por que [DirectUploadOutbox.enqueue] recusou. */
enum class DirectUploadRejectReason {
    /** Sem conta logada — não há a quem atribuir o envio. */
    NO_ACCOUNT,

    /** O arquivo não existe ou está vazio. */
    SOURCE_MISSING,

    /** Não coube/gravou na área da fila (disco cheio, permissão). */
    STORAGE_FAILED,

    /** Pedido malformado (id inválido, `kind`/`contentType` em branco, id repetido). */
    INVALID_REQUEST,
}

/**
 * Resumo de uma drenagem.
 *
 * @param completed envios concluídos (saíram da fila e do disco).
 * @param partsUploaded partes aceitas nesta passada.
 * @param failed envios que viraram [DirectUploadStatus.FAILED] nesta passada.
 * @param deferred envios esperando o recuo — voltam sozinhos.
 * @param waitingUnmetered envios "só no Wi-Fi" parados porque a rede é tarifada — quem os acorda é o
 *   agendamento de rede não tarifada, não uma nova tentativa nesta rede.
 * @param paused a passada parou: sem rede, conta trocada ou cancelada. Será retomada.
 * @param nextAttemptAtMillis o envio adiado mais próximo, para reagendar.
 */
data class DirectUploadDrainSummary(
    val completed: Int = 0,
    val partsUploaded: Int = 0,
    val failed: Int = 0,
    val deferred: Int = 0,
    val waitingUnmetered: Int = 0,
    val paused: Boolean = false,
    val nextAttemptAtMillis: Long? = null,
) {
    /** `true` se ficou algo que o agendador precisa tentar de novo depois. */
    val needsRetry: Boolean get() = paused || deferred > 0
}

/** Avisos da fila para a tela (a lista em si é [DirectUploadOutbox.jobs]). */
sealed interface DirectUploadEvent {
    val jobId: String

    /** O servidor aceitou o envio; [responseBody] é o corpo do `complete` (ex.: o `VideoRequestDto`). */
    data class Completed(override val jobId: String, val kind: String, val targetId: String, val responseBody: String) :
        DirectUploadEvent {
        override fun toString(): String = "Completed(jobId=$jobId, kind=$kind)"
    }

    /** O envio parou ([DirectUploadStatus.FAILED]). */
    data class Failed(override val jobId: String, val failure: DirectUploadFailure) : DirectUploadEvent
}

/** URLs pré-assinadas devolvidas por [DirectUploadBackend.presignParts]. */
data class DirectUploadPartUrls(val urls: Map<Int, String>, val expiresAtMillis: Long?) {
    override fun toString(): String = "DirectUploadPartUrls(parts=${urls.keys.sorted()}, expiresAtMillis=$expiresAtMillis)"
}

/** Parte aceita pelo storage, como vai no `complete` (`{partNumber, etag}`). */
@Serializable
data class DirectUploadCompletedPart(val partNumber: Int, val etag: String)

/** Redige uma URL pré-assinada para log: só esquema · host · caminho. */
@OptIn(KmpLibCoreInternalApi::class)
internal fun safeUrl(url: String?): String = redactMediaUrl(url)
