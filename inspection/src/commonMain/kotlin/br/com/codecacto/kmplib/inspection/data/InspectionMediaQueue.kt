@file:OptIn(ExperimentalTime::class)

package br.com.codecacto.kmplib.inspection.data

import br.com.codecacto.kmplib.core.crypto.sha256Hex
import br.com.codecacto.kmplib.core.storage.BlobStore
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.BuildInfo
import br.com.codecacto.kmplib.core.util.currentTimeMillis
import br.com.codecacto.kmplib.inspection.capture.CapturedMedia
import br.com.codecacto.kmplib.inspection.model.InspectionMediaKind
import br.com.codecacto.kmplib.inspection.model.InspectionMediaSource
import br.com.codecacto.kmplib.sync.direct.DIRECT_UPLOAD_NO_ETAG
import br.com.codecacto.kmplib.sync.direct.DirectUploadBackend
import br.com.codecacto.kmplib.sync.direct.DirectUploadCompletedPart
import br.com.codecacto.kmplib.sync.direct.DirectUploadEnqueueResult
import br.com.codecacto.kmplib.sync.direct.DirectUploadEvent
import br.com.codecacto.kmplib.sync.direct.DirectUploadFailure
import br.com.codecacto.kmplib.sync.direct.DirectUploadJob
import br.com.codecacto.kmplib.sync.direct.DirectUploadMode
import br.com.codecacto.kmplib.sync.direct.DirectUploadOutbox
import br.com.codecacto.kmplib.sync.direct.DirectUploadPartRequest
import br.com.codecacto.kmplib.sync.direct.DirectUploadPartResult
import br.com.codecacto.kmplib.sync.direct.DirectUploadPartTransport
import br.com.codecacto.kmplib.sync.direct.DirectUploadPartUrls
import br.com.codecacto.kmplib.sync.direct.DirectUploadRejectReason
import br.com.codecacto.kmplib.sync.direct.DirectUploadRequest
import br.com.codecacto.kmplib.sync.direct.DirectUploadScheduler
import br.com.codecacto.kmplib.sync.direct.DirectUploadSession
import br.com.codecacto.kmplib.sync.direct.DirectUploadStatus
import br.com.codecacto.kmplib.sync.direct.DirectUploadStorage
import br.com.codecacto.kmplib.sync.direct.createDirectUploadStorage
import br.com.codecacto.kmplib.sync.direct.createPlatformDirectUploadScheduler
import br.com.codecacto.kmplib.sync.direct.createPlatformDirectUploadTransport
import br.com.codecacto.kmplib.sync.direct.defaultStorageHttpClient
import br.com.codecacto.kmplib.sync.direct.directUploadDirectoryFor
import br.com.codecacto.kmplib.sync.direct.parseIsoInstantMillis
import br.com.codecacto.kmplib.sync.rest.DomainResult
import br.com.codecacto.kmplib.sync.rest.UploadRetryPolicy
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.content.ByteArrayContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Onde a mídia entra na vistoria (UM alvo, como o servidor exige). */
sealed interface InspectionMediaTarget {
    /** Mídia do item; [taskId] = a tarefa guiada que ela cumpre (o tipo tem de ser o da tarefa). */
    data class Item(val itemId: String, val taskId: String? = null) : InspectionMediaTarget

    /** Mídia do cômodo (panorama, foto geral). */
    data class Group(val groupId: String) : InspectionMediaTarget

    /** Pergunta do cabeçalho (ex.: foto do medidor). */
    data class Header(val field: String) : InspectionMediaTarget
}

/** Uma mídia da vistoria ainda no aparelho, a caminho do servidor. */
data class QueuedInspectionMedia(
    /** Id do envio na fila (`DirectUploadJob.id`) — o de [InspectionMediaQueue.retry]/[InspectionMediaQueue.discard]. */
    val jobId: String,
    /** O `CapturedMedia.id` (a miniatura local da tela). */
    val localId: String,
    val inspectionId: String,
    val target: InspectionMediaTarget?,
    val kind: MediaKind,
    val source: MediaSource,
    val sha256: String,
    /** Chave do original no `BlobStore` (miniatura local até confirmar). */
    val blobId: String?,
    val status: DirectUploadStatus,
    val progress: Float,
    val failure: DirectUploadFailure?,
    val wifiOnly: Boolean,
    val sizeBytes: Long,
) {
    val isFailed: Boolean get() = status == DirectUploadStatus.FAILED
    val isUploading: Boolean get() = status == DirectUploadStatus.UPLOADING || status == DirectUploadStatus.COMPLETING

    override fun toString(): String = "QueuedInspectionMedia(jobId=$jobId, kind=$kind, status=$status, failure=${failure?.code})"
}

/**
 * A mídia que o servidor CONFIRMOU (SHA-256 conferido) — vem de [InspectionMediaQueue.confirmed]. Case com a
 * captura pelo hash (`media.sha256 == CapturedMedia.sha256`): é a mesma chave que o servidor usa.
 */
data class ConfirmedInspectionMedia(val inspectionId: String, val media: MediaDto)

/** Desfecho de [InspectionMediaQueue.enqueue]. */
sealed interface InspectionEnqueueResult {
    data class Queued(val media: QueuedInspectionMedia) : InspectionEnqueueResult
    data class Rejected(val reason: InspectionEnqueueRejection) : InspectionEnqueueResult
}

enum class InspectionEnqueueRejection {
    /** Sem conta logada. */
    NO_ACCOUNT,

    /** O original não está no `BlobStore` (ou o armazenamento não é de arquivo). */
    SOURCE_MISSING,

    /** Não coube/gravou na fila (disco cheio). */
    STORAGE_FAILED,

    /** O arquivo no aparelho não confere com o SHA-256 da captura: a prova não sai daqui alterada. */
    HASH_MISMATCH,

    /** Tipo que o motor não recebe como arquivo (nota de voz vai por transcrição — `voice`). */
    UNSUPPORTED_KIND,

    /** Pedido malformado (id em branco, alvo inválido). */
    INVALID,
}

/**
 * **Fila DURÁVEL de mídia da vistoria** — o envio do kit ao motor (`backlib-inspection`), sobre a
 * [DirectUploadOutbox] da kmplib (`kmplib-sync`): sobrevive ao processo morto, retoma parte a parte,
 * acorda pelo WorkManager (Android) / sessão de fundo do `URLSession` (iOS), "só no Wi-Fi" por envio, e
 * só sobe com a MESMA conta que capturou.
 *
 * Caminho de cada mídia:
 * 1. [enqueue] COPIA o original do [BlobStore] (o `CapturedMedia` do `InspectionCaptureWriter`) para a área
 *    da fila e **confere o SHA-256** da cópia contra o da captura — o hash que vai ao servidor é o da
 *    CAPTURA, nunca recalculado depois (é a prova).
 * 2. `POST /inspections/{id}/media` (intenção, idempotente pelo hash) → `SINGLE` (um `PUT` com TODOS os
 *    cabeçalhos assinados — no R2, `If-None-Match: *`) ou `MULTIPART` (um `PUT` por parte; reassinatura
 *    por `POST /media/{mid}/parts`).
 * 3. `POST /media/{mid}/complete`: o servidor confere o hash. `MEDIA_NOT_UPLOADED` = reenvia o arquivo;
 *    `MEDIA_HASH_MISMATCH` (corrompeu no caminho) = reenvia com recuo, até o teto do [retry].
 *
 * Objeto que JÁ existe no storage (412 do R2, 409 do disco — o `PUT` anterior chegou e a resposta se
 * perdeu) conta como enviado: quem decide é o `complete`, que confere o hash.
 *
 * Vídeo com capa: passe [enqueue]`(thumb = …)`; a capa sobe antes do `complete` (sem ela o laudo mostra o
 * vídeo sem capa — nunca a capa de outro arquivo).
 *
 * ```kotlin
 * single(createdAtStart = true) {
 *     InspectionMediaQueue(api = get(), blobs = get(), accountId = { get<AuthRepository>().currentUser.value?.uid })
 *         .also { q -> launch { q.resume() } }
 * }
 * // Depois da câmera do kit:
 * queue.enqueue(inspectionId, captured, InspectionMediaTarget.Item(itemId))
 * ```
 * O original no [BlobStore] continua lá (a tela mostra a miniatura local); apague-o quando a confirmação
 * chegar ([confirmed]) e a pessoa liberar espaço (`StorageReclaimCard`).
 */
class InspectionMediaQueue(
    private val api: InspectionApi,
    private val blobs: BlobStore,
    accountId: () -> String?,
    val name: String = DEFAULT_NAME,
    storage: DirectUploadStorage = createDirectUploadStorage(directUploadDirectoryFor(name)),
    transport: DirectUploadPartTransport = createPlatformDirectUploadTransport(name),
    scheduler: DirectUploadScheduler = createPlatformDirectUploadScheduler(),
    retry: UploadRetryPolicy = UploadRetryPolicy(),
    storageHttp: HttpClient = defaultStorageHttpClient(),
    nowMillis: () -> Long = ::currentTimeMillis,
    /** Aceita alvo `http://` de loopback (servidor local de desenvolvimento). Default: só em build de depuração. */
    allowLoopbackHttp: Boolean = BuildInfo.isDebug,
) {
    private val backend = InspectionUploadBackend(api, blobs, storageHttp, allowLoopbackHttp)

    /** A fila por baixo (estado, eventos, agendamento). */
    val outbox: DirectUploadOutbox = DirectUploadOutbox(
        name = name, backend = backend, accountId = accountId, storage = storage,
        transport = ExistingObjectTolerantTransport(transport), scheduler = scheduler, retry = retry, nowMillis = nowMillis,
    )

    /** Toda mídia na fila (todas as contas), na ordem de envio. */
    val all: Flow<List<QueuedInspectionMedia>> = outbox.jobs.map { l -> l.mapNotNull { it.toQueued() } }

    /** A fila de UMA vistoria — o que a tela mostra como "na fila"/"enviando"/"falhou". */
    fun observe(inspectionId: String): Flow<List<QueuedInspectionMedia>> =
        outbox.observeTarget(KIND, inspectionId).map { l -> l.mapNotNull { it.toQueued() } }

    /** Mídias confirmadas pelo servidor (o `MediaDto` devolvido pelo `complete`). */
    val confirmed: Flow<ConfirmedInspectionMedia> = outbox.events
        .filterIsInstance<DirectUploadEvent.Completed>()
        .mapNotNull { e ->
            if (e.kind != KIND) return@mapNotNull null
            val dto = runCatching { InspectionJson.decodeFromString(MediaDto.serializer(), e.responseBody) }.getOrNull()
                ?: return@mapNotNull null
            ConfirmedInspectionMedia(inspectionId = e.targetId, media = dto)
        }

    /**
     * Enfileira uma captura do kit. O arquivo é COPIADO do [BlobStore] (o original fica para a miniatura
     * local) e o SHA-256 da cópia tem de bater com o da captura.
     *
     * @param durationMs duração do vídeo (obrigatória para VÍDEO no servidor).
     * @param thumb capa do vídeo (JPEG/PNG/WebP, ≤ 512 KB) — só para VÍDEO.
     * @param wifiOnly só envia em rede não tarifada (vídeo grande).
     */
    suspend fun enqueue(
        inspectionId: String,
        media: CapturedMedia,
        target: InspectionMediaTarget,
        durationMs: Int? = null,
        thumb: CapturedMedia? = null,
        wifiOnly: Boolean = false,
    ): InspectionEnqueueResult {
        if (inspectionId.isBlank() || !target.isValid()) return rejected(InspectionEnqueueRejection.INVALID)
        val kind = media.kind.toWire() ?: return rejected(InspectionEnqueueRejection.UNSUPPORTED_KIND)
        val path = blobs.localFilePath(media.blobId) ?: return rejected(InspectionEnqueueRejection.SOURCE_MISSING)
        val thumbDto = if (kind == MediaKind.VIDEO && thumb != null) {
            val bytes = blobs.read(thumb.blobId)
            if (bytes == null || bytes.isEmpty()) null else ThumbIntentDto(bytes.size.toLong(), thumb.mimeType, sha256Hex(bytes))
        } else {
            null
        }
        val intent = buildMediaIntent(media, kind, target, durationMs, thumbDto)
        val meta = buildMap {
            put(META_INSPECTION, inspectionId)
            put(META_INTENT, InspectionJson.encodeToString(MediaIntentInput.serializer(), intent))
            put(META_LOCAL_ID, media.id)
            put(META_BLOB_ID, media.blobId)
            if (thumbDto != null && thumb != null) put(META_THUMB_BLOB_ID, thumb.blobId)
        }
        val r = outbox.enqueue(
            DirectUploadRequest(
                sourcePath = path,
                kind = KIND,
                targetId = inspectionId,
                contentType = media.mimeType,
                fileName = media.blobId,
                durationSeconds = durationMs?.let { (it + 999) / 1000 },
                metadata = meta,
                wifiOnly = wifiOnly,
                moveSource = false,
                computeSha256 = true,
            ),
        )
        return when (r) {
            is DirectUploadEnqueueResult.Queued -> {
                if (r.job.sha256 != media.sha256.lowercase()) {
                    AppLogger.e(TAG, "cópia da captura ${media.id} não confere com o SHA-256 da captura; descartada")
                    outbox.discard(r.job.id)
                    rejected(InspectionEnqueueRejection.HASH_MISMATCH)
                } else {
                    r.job.toQueued()?.let { InspectionEnqueueResult.Queued(it) } ?: rejected(InspectionEnqueueRejection.INVALID)
                }
            }
            is DirectUploadEnqueueResult.Rejected -> rejected(
                when (r.reason) {
                    DirectUploadRejectReason.NO_ACCOUNT -> InspectionEnqueueRejection.NO_ACCOUNT
                    DirectUploadRejectReason.SOURCE_MISSING -> InspectionEnqueueRejection.SOURCE_MISSING
                    DirectUploadRejectReason.STORAGE_FAILED -> InspectionEnqueueRejection.STORAGE_FAILED
                    DirectUploadRejectReason.INVALID_REQUEST -> InspectionEnqueueRejection.INVALID
                },
            )
        }
    }

    /** "Tentar de novo" num envio que falhou. */
    suspend fun retry(jobId: String): Boolean = outbox.retry(jobId)

    /** Todos os que falharam desta vistoria. */
    suspend fun retryAll(inspectionId: String): Int =
        outbox.jobs.value.filter { it.kind == KIND && it.targetId == inspectionId && it.status == DirectUploadStatus.FAILED }
            .count { outbox.retry(it.id) }

    /** "Enviar com dados móveis": tira o só-Wi-Fi dos envios desta vistoria. */
    suspend fun allowMobileData(inspectionId: String): Int =
        outbox.jobs.value.filter { it.kind == KIND && it.targetId == inspectionId && it.wifiOnly }
            .count { outbox.setWifiOnly(it.id, false) }

    /** Desiste do envio (o original no [BlobStore] não é apagado aqui). */
    suspend fun discard(jobId: String): Boolean = outbox.discard(jobId)

    /** Abertura do app: recarrega a fila do disco e agenda o que falta. */
    suspend fun resume() = outbox.resume()

    /** Pede uma drenagem agora (rede voltou, `ON_RESUME`). */
    fun requestDrain() = outbox.requestDrain()

    /** Logout com descarte: tira da fila os envios da conta. */
    suspend fun purgeAccount(accountId: String): Int = outbox.purgeAccount(accountId)

    private fun rejected(r: InspectionEnqueueRejection) = InspectionEnqueueResult.Rejected(r)

    companion object {
        const val DEFAULT_NAME: String = "inspection-media"

        /** `DirectUploadJob.kind` das mídias da vistoria. */
        const val KIND: String = "inspection-media"

        internal const val META_INSPECTION = "inspectionId"
        internal const val META_INTENT = "intent"
        internal const val META_LOCAL_ID = "localId"
        internal const val META_BLOB_ID = "blobId"
        internal const val META_THUMB_BLOB_ID = "thumbBlobId"
        private const val TAG = "InspectionMediaQueue"
    }
}

/** A intenção de uma captura do kit. Função pura (testada). */
fun buildMediaIntent(
    media: CapturedMedia,
    kind: MediaKind,
    target: InspectionMediaTarget,
    durationMs: Int? = null,
    thumb: ThumbIntentDto? = null,
): MediaIntentInput = MediaIntentInput(
    sha256 = media.sha256.lowercase(),
    sizeBytes = media.sizeBytes,
    contentType = media.mimeType.lowercase(),
    kind = kind,
    source = if (media.origin == InspectionMediaSource.CAMERA) MediaSource.CAMERA else MediaSource.GALLERY,
    itemId = (target as? InspectionMediaTarget.Item)?.itemId,
    groupId = (target as? InspectionMediaTarget.Group)?.groupId,
    headerField = (target as? InspectionMediaTarget.Header)?.field,
    taskId = (target as? InspectionMediaTarget.Item)?.taskId,
    capturedAtDevice = Instant.fromEpochMilliseconds(media.capturedAtDeviceMillis).toString(),
    // GPS só da câmera (a galeria não ganha a posição de agora — o writer já não coleta).
    lat = media.location?.latitude,
    lng = media.location?.longitude,
    accuracyM = media.location?.accuracyMeters?.toDouble(),
    deviceModel = media.deviceModel.trim().take(120).ifEmpty { null },
    durationMs = if (kind == MediaKind.VIDEO) durationMs else null,
    thumb = if (kind == MediaKind.VIDEO) thumb else null,
)

private fun InspectionMediaKind.toWire(): MediaKind? = when (this) {
    InspectionMediaKind.PHOTO -> MediaKind.PHOTO
    InspectionMediaKind.VIDEO -> MediaKind.VIDEO
    InspectionMediaKind.VOICE -> null
}

private fun InspectionMediaTarget.isValid(): Boolean = when (this) {
    is InspectionMediaTarget.Item -> itemId.isNotBlank() && (taskId == null || taskId.isNotBlank())
    is InspectionMediaTarget.Group -> groupId.isNotBlank()
    is InspectionMediaTarget.Header -> field.isNotBlank()
}

internal fun DirectUploadJob.intentOrNull(): MediaIntentInput? =
    metadata[InspectionMediaQueue.META_INTENT]?.let { runCatching { InspectionJson.decodeFromString(MediaIntentInput.serializer(), it) }.getOrNull() }

internal fun DirectUploadJob.toQueued(): QueuedInspectionMedia? {
    if (kind != InspectionMediaQueue.KIND) return null
    val intent = intentOrNull() ?: return null
    val alvo = when {
        intent.itemId != null -> InspectionMediaTarget.Item(intent.itemId, intent.taskId)
        intent.groupId != null -> InspectionMediaTarget.Group(intent.groupId)
        intent.headerField != null -> InspectionMediaTarget.Header(intent.headerField)
        else -> null
    }
    return QueuedInspectionMedia(
        jobId = id,
        localId = metadata[InspectionMediaQueue.META_LOCAL_ID] ?: id,
        inspectionId = targetId,
        target = alvo,
        kind = intent.kind,
        source = intent.source,
        sha256 = intent.sha256,
        blobId = metadata[InspectionMediaQueue.META_BLOB_ID],
        status = status,
        progress = progress,
        failure = failure,
        wifiOnly = wifiOnly,
        sizeBytes = sizeBytes,
    )
}

/**
 * O [DirectUploadBackend] do motor: intenção → sessão (PUT único ou multipart), reassinatura, capa do
 * vídeo e conclusão com conferência do hash. Sem estado próprio: tudo o que precisa está no job
 * (persistido pela fila) — a intenção é idempotente pelo hash, então repeti-la devolve bilhetes novos.
 */
internal class InspectionUploadBackend(
    private val api: InspectionApi,
    private val blobs: BlobStore,
    private val storageHttp: HttpClient,
    private val allowLoopbackHttp: Boolean = false,
) : DirectUploadBackend {

    override suspend fun start(job: DirectUploadJob): DomainResult<DirectUploadSession> {
        val (inspectionId, intent) = context(job) ?: return invalidJob()
        return when (val r = api.mediaIntent(inspectionId, intent)) {
            is InspectionResult.Ok -> sessionOf(job, r.value, allowLoopbackHttp)
            is InspectionResult.Failed -> r.error.toDomain()
        }
    }

    override suspend fun presignParts(job: DirectUploadJob, session: DirectUploadSession, partNumbers: List<Int>): DomainResult<DirectUploadPartUrls> {
        if (session.mode == DirectUploadMode.SINGLE_PUT) {
            // Bilhete novo do PUT único = repetir a intenção (idempotente pelo hash).
            val (inspectionId, intent) = context(job) ?: return invalidJob()
            return when (val r = api.mediaIntent(inspectionId, intent)) {
                is InspectionResult.Ok -> {
                    val up = r.value.upload
                    val single = up?.single
                    when {
                        up == null -> DomainResult.Success(DirectUploadPartUrls(emptyMap(), null, alreadyReceived = true))
                        single == null -> DomainResult.Error(503, "Armazenamento indisponível agora.", serverCode = STORAGE_UNAVAILABLE)
                        !isAllowedSignedUploadUrl(single.url, allowLoopbackHttp) -> insecureTarget()
                        else -> DomainResult.Success(
                            DirectUploadPartUrls(mapOf(1 to single.url), parseIsoInstantMillis(single.expiresAt), signedHeaders = single.headers),
                        )
                    }
                }
                is InspectionResult.Failed -> r.error.toDomain()
            }
        }
        val mediaId = session.remoteId ?: session.uploadId
        return when (val r = api.mediaParts(mediaId, partNumbers)) {
            is InspectionResult.Ok -> if (r.value.parts.any { !isAllowedSignedUploadUrl(it.url, allowLoopbackHttp) }) insecureTarget() else DomainResult.Success(
                DirectUploadPartUrls(
                    urls = r.value.parts.associate { it.partNumber to it.url },
                    expiresAtMillis = r.value.parts.mapNotNull { parseIsoInstantMillis(it.expiresAt) }.minOrNull(),
                ),
            )
            is InspectionResult.Failed -> {
                val e = r.error
                if (e is InspectionError.Conflict && e.serverCode == InspectionErrorCodes.MEDIA_ALREADY_CONFIRMED) {
                    DomainResult.Success(DirectUploadPartUrls(emptyMap(), null, alreadyReceived = true))
                } else {
                    e.toDomain()
                }
            }
        }
    }

    override suspend fun complete(job: DirectUploadJob, session: DirectUploadSession, parts: List<DirectUploadCompletedPart>): DomainResult<String> {
        val mediaId = session.remoteId ?: session.uploadId
        job.metadata[InspectionMediaQueue.META_THUMB_BLOB_ID]?.let { thumbBlob ->
            if (!session.alreadyReceived) uploadThumb(job, thumbBlob)?.let { return it }
        }
        return when (val r = api.completeMedia(mediaId)) {
            is InspectionResult.Ok -> DomainResult.Success(InspectionJson.encodeToString(MediaDto.serializer(), r.value))
            is InspectionResult.Failed -> when (r.error.serverCode) {
                // O arquivo não chegou (ou chegou corrompido e o servidor apagou): a fila reenvia, com recuo.
                InspectionErrorCodes.MEDIA_NOT_UPLOADED, InspectionErrorCodes.MEDIA_HASH_MISMATCH ->
                    DomainResult.Error(DirectUploadOutbox.NOT_RECEIVED_CODE, r.error.userMessage, serverCode = r.error.serverCode)
                else -> r.error.toDomain()
            }
        }
    }

    /** Não há "abortar" no contrato: mídia registrada e não enviada fica na fila do servidor até ser removida pela tela. */
    override suspend fun abort(job: DirectUploadJob, session: DirectUploadSession): DomainResult<Unit> = DomainResult.Success(Unit)

    /** Sobe a capa do vídeo. `null` = seguiu (enviada, já existia, ou recusada sem remédio); erro = tentar depois. */
    private suspend fun uploadThumb(job: DirectUploadJob, thumbBlob: String): DomainResult.Error? {
        val (inspectionId, intent) = context(job) ?: return null
        val alvo = when (val r = api.mediaIntent(inspectionId, intent)) {
            is InspectionResult.Ok -> r.value.upload?.thumb?.takeIf { isAllowedSignedUploadUrl(it.url, allowLoopbackHttp) } ?: return null
            is InspectionResult.Failed -> return if (r.error.isRetryable) {
                DomainResult.Error(if (r.error is InspectionError.Offline) DomainResult.OFFLINE_CODE else r.error.httpStatus, r.error.userMessage)
            } else {
                null
            }
        }
        val bytes = blobs.read(thumbBlob) ?: return null
        return try {
            val resposta = storageHttp.request(alvo.url) {
                method = HttpMethod.parse(alvo.method.uppercase())
                val tipo = alvo.headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value
                alvo.headers.forEach { (k, v) ->
                    if (!k.equals("content-type", true) && !k.equals("content-length", true) && !k.equals("host", true)) header(k, v)
                }
                setBody(ByteArrayContent(bytes, tipo?.let { ContentType.parse(it) } ?: ContentType.Image.JPEG))
            }
            val s = resposta.status.value
            when {
                s in 200..299 || s == 409 || s == 412 -> null
                s == 408 || s == 429 || s >= 500 -> DomainResult.Error(s, "O armazenamento recusou a capa do vídeo.")
                else -> {
                    AppLogger.w(TAG, "capa do vídeo recusada pelo storage ($s); o vídeo segue sem capa")
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "capa do vídeo não subiu (${e::class.simpleName}); tentando depois")
            DomainResult.Error(DomainResult.OFFLINE_CODE, "Sem conexão.")
        }
    }

    private fun context(job: DirectUploadJob): Pair<String, MediaIntentInput>? {
        val inspectionId = job.metadata[InspectionMediaQueue.META_INSPECTION] ?: job.targetId
        val intent = job.intentOrNull() ?: return null
        return inspectionId to intent
    }

    private fun invalidJob(): DomainResult.Error = DomainResult.Error(INVALID_JOB, "Envio sem intenção legível.")

    companion object {
        private const val TAG = "InspectionUpload"
        const val STORAGE_UNAVAILABLE: String = "STORAGE_UNAVAILABLE"

        /** Sentinela: job sem a intenção (estado corrompido). Terminal. */
        const val INVALID_JOB: Int = -21
    }
}

/**
 * O alvo assinado pode receber o arquivo? Só `https` (2.321.0): a URL vem do servidor, mas um `http://` nela
 * mandaria a prova e os cabeçalhos assinados em claro. `http://` de loopback (`localhost`, `127.x`, `::1` e
 * `10.0.2.2` — o loopback do computador visto do emulador Android) só com [allowLoopbackHttp] (depuração).
 */
fun isAllowedSignedUploadUrl(url: String, allowLoopbackHttp: Boolean): Boolean {
    // Esquema EXPLÍCITO: o `Url` do Ktor completa texto sem esquema como `http://localhost/…`.
    val t = url.trim()
    if (!t.startsWith("https://", ignoreCase = true) && !t.startsWith("http://", ignoreCase = true)) return false
    val u = runCatching { Url(t) }.getOrNull() ?: return false
    if (u.host.isBlank()) return false
    return when (u.protocol.name.lowercase()) {
        "https" -> true
        "http" -> allowLoopbackHttp && (u.host == "localhost" || u.host.startsWith("127.") || u.host == "::1" || u.host == "[::1]" || u.host == "10.0.2.2")
        else -> false
    }
}

/** Sentinela/código de alvo recusado por não ser `https`: terminal (o servidor está mal configurado). */
const val UPLOAD_URL_INSECURE: String = "UPLOAD_URL_INSECURE"

internal fun insecureTarget(): DomainResult.Error =
    DomainResult.Error(InspectionError.INVALID_RESPONSE, "O servidor devolveu um endereço de envio inseguro.", serverCode = UPLOAD_URL_INSECURE)

/** Resposta da intenção → sessão da fila. Função pura (testada). */
internal fun sessionOf(job: DirectUploadJob, dto: MediaIntentDto, allowLoopbackHttp: Boolean = false): DomainResult<DirectUploadSession> {
    val up = dto.upload
    val id = dto.media.id
    if (up == null || dto.media.status == MediaStatus.CONFIRMED) {
        return DomainResult.Success(DirectUploadSession.singlePut(remoteId = id, sizeBytes = job.sizeBytes, url = null, alreadyReceived = true))
    }
    return when (up.mode) {
        UploadModes.SINGLE -> {
            val s = up.single ?: return DomainResult.Error(503, "Armazenamento indisponível agora.", serverCode = InspectionUploadBackend.STORAGE_UNAVAILABLE)
            if (!isAllowedSignedUploadUrl(s.url, allowLoopbackHttp)) return insecureTarget()
            DomainResult.Success(
                DirectUploadSession.singlePut(
                    remoteId = id, sizeBytes = job.sizeBytes, url = s.url, method = s.method,
                    signedHeaders = s.headers, expiresAtMillis = parseIsoInstantMillis(s.expiresAt),
                ),
            )
        }
        UploadModes.MULTIPART -> {
            val parts = up.parts.orEmpty()
            if (parts.any { !isAllowedSignedUploadUrl(it.url, allowLoopbackHttp) }) return insecureTarget()
            val partSize = up.partSizeBytes ?: return DomainResult.Error(InspectionError.INVALID_RESPONSE, InspectionError.INVALID_RESPONSE_MESSAGE)
            val count = up.partCount ?: return DomainResult.Error(InspectionError.INVALID_RESPONSE, InspectionError.INVALID_RESPONSE_MESSAGE)
            DomainResult.Success(
                DirectUploadSession(
                    uploadId = id,
                    remoteId = id,
                    partSizeBytes = partSize,
                    partCount = count,
                    partUrls = parts.associate { it.partNumber to it.url },
                    urlsExpireAtMillis = parts.mapNotNull { parseIsoInstantMillis(it.expiresAt) }.minOrNull(),
                    mode = DirectUploadMode.MULTIPART,
                    method = parts.firstOrNull()?.method ?: "PUT",
                    // Cabeçalhos comuns a todas as partes (S3/R2: nenhum além de host/tamanho).
                    signedHeaders = parts.map { it.headers }.reduceOrNull { a, b -> a.filter { (k, v) -> b[k] == v } }.orEmpty(),
                ),
            )
        }
        else -> DomainResult.Error(InspectionError.INVALID_RESPONSE, InspectionError.INVALID_RESPONSE_MESSAGE)
    }
}

/** O erro tipado de volta à régua da fila (`classifyRestFailure`): 402 cota, sem rede pausa, 5xx/429 recua, resto falha. */
internal fun InspectionError.toDomain(): DomainResult<Nothing> = when (this) {
    is InspectionError.QuotaExceeded -> quota
    is InspectionError.Offline -> DomainResult.Error(DomainResult.OFFLINE_CODE, userMessage)
    else -> DomainResult.Error(httpStatus, userMessage, serverCode = serverCode)
}

/**
 * Transporte que trata "objeto já existe" no PUT ÚNICO (412 do R2 com `If-None-Match: *`; 409 do storage em
 * disco) como enviado: a URL do motor é só-cria, e esse status só aparece quando um PUT anterior já gravou o
 * arquivo e a resposta se perdeu. O `complete` confere o hash — objeto errado não passa.
 */
internal class ExistingObjectTolerantTransport(private val delegate: DirectUploadPartTransport) : DirectUploadPartTransport {
    override suspend fun upload(request: DirectUploadPartRequest): DirectUploadPartResult {
        val r = delegate.upload(request)
        return if (!request.requireEtag && r is DirectUploadPartResult.Failed && (r.code == 412 || r.code == 409)) {
            DirectUploadPartResult.Uploaded(DIRECT_UPLOAD_NO_ETAG)
        } else {
            r
        }
    }

    override suspend fun cancelJob(jobId: String) = delegate.cancelJob(jobId)
}
