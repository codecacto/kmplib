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
 * Como o arquivo vai ao storage (2.312.0).
 *
 * - [MULTIPART]: várias partes, cada uma por `PUT` na sua URL pré-assinada, fechadas com as ETags no
 *   `complete` (contrato `VideoUploadDto`, [RestDirectUploadBackend]). Retoma parte a parte.
 * - [SINGLE_PUT]: **um** `PUT` do arquivo inteiro numa URL pré-assinada, com os **cabeçalhos
 *   assinados** que o servidor devolveu (`content-type`, `x-amz-checksum-sha256`…), e depois o
 *   `concluir` no nosso servidor ([RestSinglePutUploadBackend]). Para foto e vídeo curto (até ~100 MB):
 *   a retomada é repetir o `PUT`, que é idempotente na mesma chave.
 */
@Serializable
enum class DirectUploadMode {
    MULTIPART,
    SINGLE_PUT,
}

/**
 * A sessão aberta no servidor: no [DirectUploadMode.MULTIPART] (o `VideoUploadDto` do contrato), o id
 * do envio no storage, o tamanho de cada parte e as URLs pré-assinadas que ainda valem; no
 * [DirectUploadMode.SINGLE_PUT], uma "parte" só (o arquivo inteiro), a URL do bilhete e os
 * [signedHeaders] — monte com [DirectUploadSession.singlePut].
 *
 * @param uploadId id do envio multipart (vai nos caminhos `…/uploads/{uploadId}/parts|complete|abort`).
 * @param remoteId id do recurso criado no servidor (o `videoId`), quando o contrato devolve.
 * @param partSizeBytes tamanho de cada parte (a última pode ser menor) — decisão do SERVIDOR.
 * @param partCount quantas partes; a soma tem de dar o tamanho do arquivo.
 * @param partUrls URLs pré-assinadas de escrita, por número de parte (1-based). São credencial de
 *   curta duração: ficam no armazenamento privado do app e **nunca** saem em log nem em `toString`.
 * @param urlsExpireAtMillis quando as [partUrls] vencem (`null` = o servidor não disse).
 * @param mode multipart (default, o contrato de sempre) ou PUT único.
 * @param method método HTTP do envio ao storage (`PUT`; o do bilhete no PUT único).
 * @param signedHeaders cabeçalhos que a assinatura cobre e que vão **exatamente** assim no envio
 *   (`content-type`, `x-amz-checksum-sha256`…). `content-length` é conferido contra o arquivo e
 *   escrito pelo transporte. Não saem em `toString` (podem carregar token de sessão do storage).
 * @param alreadyReceived o servidor já tem o arquivo (repetição depois do fim): nada a enviar, só o
 *   `complete`.
 * @param partHeaders cabeçalhos assinados **de cada parte** (2.327.0), por número de parte — o
 *   contrato que assina por parte (`parts:[{partNumber, url, headers}]`, ex.: `x-amz-checksum-sha256`
 *   da parte). Somam-se aos [signedHeaders] da sessão, e o da parte vence o de mesmo nome (sem
 *   diferença de caixa). Mesmo sigilo dos [signedHeaders]: nunca em `toString`.
 */
@Serializable
data class DirectUploadSession(
    val uploadId: String,
    val remoteId: String? = null,
    val partSizeBytes: Long,
    val partCount: Int,
    val partUrls: Map<Int, String> = emptyMap(),
    val urlsExpireAtMillis: Long? = null,
    val mode: DirectUploadMode = DirectUploadMode.MULTIPART,
    val method: String = "PUT",
    val signedHeaders: Map<String, String> = emptyMap(),
    val alreadyReceived: Boolean = false,
    val partHeaders: Map<Int, Map<String, String>> = emptyMap(),
) {
    override fun toString(): String =
        "DirectUploadSession(mode=$mode, remoteId=${remoteId != null}, partSizeBytes=$partSizeBytes, partCount=$partCount, " +
            "partUrls=${partUrls.size}, signedHeaders=${signedHeaders.keys.sorted()}, partHeaders=${partHeaders.size}, " +
            "urlsExpireAtMillis=$urlsExpireAtMillis, alreadyReceived=$alreadyReceived)"

    companion object {
        /**
         * Sessão de **PUT único** para um arquivo de [sizeBytes] bytes: uma parte, do tamanho do arquivo,
         * na [url] do bilhete. [url] em branco só com [alreadyReceived] (nada a enviar).
         *
         * @param remoteId o id do recurso no servidor (a mídia) — vai no caminho do `complete`.
         */
        fun singlePut(
            remoteId: String,
            sizeBytes: Long,
            url: String?,
            method: String = "PUT",
            signedHeaders: Map<String, String> = emptyMap(),
            expiresAtMillis: Long? = null,
            alreadyReceived: Boolean = false,
        ): DirectUploadSession = DirectUploadSession(
            uploadId = remoteId,
            remoteId = remoteId,
            partSizeBytes = sizeBytes,
            partCount = 1,
            partUrls = url?.takeIf { it.isNotBlank() }?.let { mapOf(1 to it) } ?: emptyMap(),
            urlsExpireAtMillis = expiresAtMillis,
            mode = DirectUploadMode.SINGLE_PUT,
            method = method.ifBlank { "PUT" }.uppercase(),
            signedHeaders = signedHeaders,
            alreadyReceived = alreadyReceived,
        )
    }
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
 * @param sha256 SHA-256 (hex minúsculo) do arquivo NA FILA, quando pedido em
 *   [DirectUploadRequest.computeSha256] — calculado depois de o arquivo entrar na fila, então é o
 *   resumo dos bytes que de fato sobem. O backend o manda na declaração (o servidor confere no fim).
 * @param completedParts ETag de cada parte já aceita pelo storage — é o que faz a **retomada**: após
 *   o processo morrer, só sobe o que falta.
 * @param attempts falhas retentáveis seguidas (zera a cada avanço).
 * @param nextAttemptAtMillis antes disso, o envio espera (recuo exponencial).
 * @param pausedByUser a pessoa pausou ESTE envio ([DirectUploadOutbox.pauseJob], 2.327.0). Gravado com o
 *   resto do estado: matar o app não o despausa. Só volta com [DirectUploadOutbox.resumeJob].
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
    val sha256: String? = null,
    val createdAtMillis: Long,
    val status: DirectUploadStatus = DirectUploadStatus.QUEUED,
    val session: DirectUploadSession? = null,
    val completedParts: Map<Int, String> = emptyMap(),
    val attempts: Int = 0,
    val nextAttemptAtMillis: Long = 0L,
    val failure: DirectUploadFailure? = null,
    val pausedByUser: Boolean = false,
) {
    /** Bytes já aceitos pelo storage (soma das partes concluídas). */
    val uploadedBytes: Long get() = directUploadedBytes(this)

    /** Fração enviada, de 0 a 1 (`0` antes de haver sessão). */
    val progress: Float get() = if (sizeBytes <= 0L) 0f else (uploadedBytes.toDouble() / sizeBytes).toFloat().coerceIn(0f, 1f)

    /** Sem caminho, sem URL e sem metadado do app (pode ser dado pessoal). */
    override fun toString(): String =
        "DirectUploadJob(id=$id, kind=$kind, status=$status, sizeBytes=$sizeBytes, wifiOnly=$wifiOnly, " +
            "parts=${completedParts.size}/${session?.partCount ?: 0}, attempts=$attempts, failure=${failure?.code}, " +
            "pausedByUser=$pausedByUser)"
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
    /**
     * `true` = a fila calcula o SHA-256 do arquivo (em blocos, sem carregá-lo inteiro) depois de
     * trazê-lo para a área dela e o guarda em [DirectUploadJob.sha256] (2.312.0). Para o contrato que
     * declara o hash antes do envio e o servidor confere no fim (PUT único com
     * `x-amz-checksum-sha256`, RondAki §11.4).
     */
    val computeSha256: Boolean = false,
) {
    override fun toString(): String =
        "DirectUploadRequest(kind=$kind, contentType=$contentType, wifiOnly=$wifiOnly, moveSource=$moveSource, " +
            "computeSha256=$computeSha256)"
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
 * @param pausedByUser envios pausados pela pessoa ([DirectUploadOutbox.pauseJob]) que a passada pulou
 *   (2.327.0). **Não** pedem nova tentativa: quem os solta é o `resumeJob`.
 * @param queuePaused a fila inteira está pausada pela pessoa ([DirectUploadOutbox.pauseAll]) — nada
 *   subiu, e o agendador **não** reagenda (só o `resumeAll` solta).
 */
data class DirectUploadDrainSummary(
    val completed: Int = 0,
    val partsUploaded: Int = 0,
    val failed: Int = 0,
    val deferred: Int = 0,
    val waitingUnmetered: Int = 0,
    val paused: Boolean = false,
    val nextAttemptAtMillis: Long? = null,
    val pausedByUser: Int = 0,
    val queuePaused: Boolean = false,
) {
    /** `true` se ficou algo que o agendador precisa tentar de novo depois (pausa da pessoa não conta). */
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

/**
 * URLs pré-assinadas devolvidas por [DirectUploadBackend.presignParts].
 *
 * @param signedHeaders no PUT único, os cabeçalhos do bilhete novo (`null` = mantém os da sessão).
 * @param alreadyReceived o servidor respondeu que já tem o arquivo: nada mais a enviar.
 * @param partHeaders cabeçalhos assinados de cada parte reassinada (2.327.0). Substituem, para as partes
 *   de [urls], os que a sessão tinha (parte reassinada sem cabeçalho volta a não ter cabeçalho próprio).
 */
data class DirectUploadPartUrls(
    val urls: Map<Int, String>,
    val expiresAtMillis: Long?,
    val signedHeaders: Map<String, String>? = null,
    val alreadyReceived: Boolean = false,
    val partHeaders: Map<Int, Map<String, String>> = emptyMap(),
) {
    override fun toString(): String =
        "DirectUploadPartUrls(parts=${urls.keys.sorted()}, expiresAtMillis=$expiresAtMillis, " +
            "signedHeaders=${signedHeaders?.keys?.sorted()}, partHeaders=${partHeaders.size}, alreadyReceived=$alreadyReceived)"
}

/**
 * Quanto a [DirectUploadOutbox] envia ao mesmo tempo (2.327.0).
 *
 * - [files]: quantos ARQUIVOS sobem juntos (o mais antigo começa primeiro; quando um termina, o próximo
 *   da fila entra no lugar). `1` = um por vez, o comportamento de sempre.
 * - [partsPerFile]: quantas PARTES do mesmo arquivo multipart sobem juntas, cada uma na sua URL
 *   pré-assinada (janela deslizante: terminou uma, entra a próxima). O PUT único tem uma parte só.
 *
 * O teto de conexões simultâneas é `files × partsPerFile`. No 4G, mais que ~6 deixa de ganhar e passa
 * a disputar banda (cada parte demora mais e corre mais risco de cair no meio); os limites
 * ([MAX_FILES], [MAX_PARTS_PER_FILE]) existem para que um número de configuração não vire 64 conexões.
 * A ordem das partes no storage não importa: o `complete` leva a lista ordenada por número.
 */
data class DirectUploadConcurrency(
    val files: Int = 1,
    val partsPerFile: Int = 1,
) {
    init {
        require(files in 1..MAX_FILES) { "files fora de 1..$MAX_FILES" }
        require(partsPerFile in 1..MAX_PARTS_PER_FILE) { "partsPerFile fora de 1..$MAX_PARTS_PER_FILE" }
    }

    /** Teto de partes em voo na fila inteira. */
    val maxInFlightParts: Int get() = files * partsPerFile

    companion object {
        const val MAX_FILES: Int = 8
        const val MAX_PARTS_PER_FILE: Int = 8

        /** Um arquivo, uma parte por vez — o default (e o comportamento até a 2.326.0). */
        val Sequential: DirectUploadConcurrency = DirectUploadConcurrency()
    }
}

/**
 * Estado da FILA (não de um envio) que sobrevive ao processo (2.327.0): hoje, a pausa da pessoa
 * ([DirectUploadOutbox.pauseAll]). Gravado pelo [DirectUploadStorage.saveQueueState].
 */
@Serializable
data class DirectUploadQueueState(val pausedByUser: Boolean = false)

/** Parte aceita pelo storage, como vai no `complete` (`{partNumber, etag}`). */
@Serializable
data class DirectUploadCompletedPart(val partNumber: Int, val etag: String)

/** Redige uma URL pré-assinada para log: só esquema · host · caminho. */
@OptIn(KmpLibCoreInternalApi::class)
internal fun safeUrl(url: String?): String = redactMediaUrl(url)
