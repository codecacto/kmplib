package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.firebase.storage.UploadItem
import br.com.codecacto.kmplib.firebase.storage.UploadRequest
import br.com.codecacto.kmplib.firebase.storage.UploadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fila reativa de **uploads multipart autenticados para o backend REST-CRUD** — a variante do
 * [UploadQueue][br.com.codecacto.kmplib.firebase.storage.UploadQueue] (Firebase Storage) para apps que
 * guardam anexos no **backend de domínio** (`POST /v1/.../anexos`, bytes com Bearer Firebase), sem
 * Firebase Storage. Faz par com o resto do bloco REST-CRUD offline-first (piloto MinhasHoras).
 *
 * Reusa os MESMOS modelos de UI ([UploadItem]/[UploadStatus]/[UploadRequest]) do upload de Storage, de
 * modo que os composables `UploadProgressItem`/`UploadQueueView` (ui/components) renderizam esta fila
 * sem mudança. O transporte é o [DomainApiClient.postMultipart] (multipart, campo `file`).
 *
 * **Sem progresso granular:** `MultiPartFormDataContent` não expõe fração de bytes enviados como o
 * `uploadBytesWithProgress` do Storage — o item vai de `UPLOADING` (fração indeterminada) direto para
 * `COMPLETED`/`FAILED`. Processamento **sequencial** (um por vez), adequado a redes instáveis. Chame
 * [process] de uma coroutine (viewModelScope) e colete [items] na UI.
 *
 * Exemplo:
 * ```kotlin
 * val queue = RestUploadQueue(domainApi)
 * queue.enqueue(UploadRequest(id, "comprovante.jpg", "/v1/lancamentos/$lid/anexos", bytes, "image/jpeg"))
 * viewModelScope.launch { queue.process() }
 * ```
 *
 * ## DEPRECIADA na 2.104.0 — a fila **não sobrevive ao processo**
 *
 * Os bytes ficam num `mutableMapOf` e a fila num `MutableStateFlow`: **memória pura**. Quem cadastra
 * um registro com anexo **sem sinal** e fecha o app perde o anexo em silêncio — o registro sobe
 * depois, sem ele. Use o [RestUploadOutbox], que grava os binários no disco, a fila no MESMO espelho
 * `synced_entity` (com escopo de conta e histórico de entrega) e participa do ciclo do
 * [RestCrudSyncEngine].
 *
 * Migração (a fila em memória não precisa ser convertida — o que estiver nela já é volátil):
 * ```kotlin
 * // antes
 * val queue = RestUploadQueue(api) { id, body -> ... }
 * queue.enqueue(UploadRequest(id, "comprovante.jpg", "/v1/lancamentos/$lid/anexos", bytes, "image/jpeg"))
 * viewModelScope.launch { queue.process() }
 *
 * // depois — sobe sozinha no ciclo de sync, e sobrevive a fechar o app
 * val outbox = RestUploadOutbox(api, store) { upload, body -> ... }
 * outbox.enqueue(
 *     bytes = bytes, fileName = "comprovante.jpg", mimeType = "image/jpeg",
 *     path = "/v1/lancamentos/{owner}/anexos",
 *     ownerEntity = LancamentoCrud.name, ownerHandle = lancamentoId,
 * )
 * ```
 *
 * @param api cliente REST de domínio (Bearer Firebase + refresh 401).
 * @param onUploaded callback opcional com o corpo bruto da resposta 2xx de cada item (para o app
 *   decodificar o anexo criado e gravar no espelho). Recebe `(uploadId, responseBody)`.
 */
@Deprecated(
    message = "Fila em memória: o anexo se perde se o processo morrer. Use RestUploadOutbox " +
        "(fila persistente em SQLDelight + binários no disco, participante do RestCrudSyncEngine).",
    level = DeprecationLevel.WARNING,
)
class RestUploadQueue(
    private val api: DomainApiClient,
    private val onUploaded: (suspend (uploadId: String, responseBody: String) -> Unit)? = null,
) {
    private val _items = MutableStateFlow<List<UploadItem>>(emptyList())
    val items: StateFlow<List<UploadItem>> = _items.asStateFlow()

    private val requests = mutableMapOf<String, UploadRequest>()

    /** Requests de múltiplas partes (ex.: foto-prova `full`+`thumb`), enfileirados via [enqueueParts]. */
    private val multipartRequests = mutableMapOf<String, MultipartUploadRequest>()

    /** `true` se todos os itens são terminais E existe ao menos um item. */
    val isAllDone: Boolean
        get() = _items.value.isNotEmpty() && _items.value.all { it.isTerminal }

    /** Adiciona um item à fila (estado inicial PENDING). */
    fun enqueue(request: UploadRequest) {
        requests[request.id] = request
        _items.value = _items.value + UploadItem(
            id = request.id,
            fileName = request.fileName,
            status = UploadStatus.PENDING,
        )
    }

    /** Adiciona vários itens de uma vez. */
    fun enqueueAll(reqs: List<UploadRequest>) = reqs.forEach(::enqueue)

    /**
     * Enfileira um upload de **múltiplas partes** (ex.: foto-prova `full`+`thumb` num único request
     * multipart). Estado inicial PENDING; processado por [process]/[retry] via
     * [DomainApiClient.postMultipartParts].
     */
    fun enqueueParts(request: MultipartUploadRequest) {
        multipartRequests[request.id] = request
        _items.value = _items.value + UploadItem(
            id = request.id,
            fileName = request.fileName,
            status = UploadStatus.PENDING,
        )
    }

    /** Remove um item da fila (e seu request). Não cancela um upload em curso. */
    fun remove(id: String) {
        requests.remove(id)
        multipartRequests.remove(id)
        _items.value = _items.value.filterNot { it.id == id }
    }

    /** Processa todos os itens PENDING/FAILED em sequência. Idempotente. */
    suspend fun process() {
        val toProcess = _items.value
            .filter { it.status == UploadStatus.PENDING || it.status == UploadStatus.FAILED }
            .map { it.id }
        for (id in toProcess) uploadById(id)
    }

    /** Reenfileira (FAILED → PENDING) e processa um item específico. */
    suspend fun retry(id: String) {
        if (requests[id] == null && multipartRequests[id] == null) return
        update(id) { it.copy(status = UploadStatus.PENDING, errorMessage = null, fraction = 0f) }
        uploadById(id)
    }

    private suspend fun uploadById(id: String) {
        multipartRequests[id]?.let { return uploadParts(it) }
        requests[id]?.let { return uploadOne(it) }
    }

    private suspend fun uploadParts(req: MultipartUploadRequest) {
        update(req.id) { it.copy(status = UploadStatus.UPLOADING, fraction = 0f) }
        finish(req.id, api.postMultipartParts(req.path, req.parts))
    }

    private suspend fun uploadOne(req: UploadRequest) {
        update(req.id) { it.copy(status = UploadStatus.UPLOADING, fraction = 0f) }
        val result = api.postMultipart(
            path = req.path,
            fileBytes = req.bytes,
            fileName = req.fileName,
            mimeType = req.mimeType ?: "application/octet-stream",
        )
        finish(req.id, result)
    }

    private suspend fun finish(id: String, result: DomainResult<String>) {
        when (result) {
            is DomainResult.Success -> {
                update(id) { it.copy(status = UploadStatus.COMPLETED, fraction = 1f) }
                onUploaded?.invoke(id, result.data)
            }
            is DomainResult.Quota ->
                update(id) { it.copy(status = UploadStatus.FAILED, errorMessage = "Limite do plano atingido.") }
            is DomainResult.Error ->
                update(id) { it.copy(status = UploadStatus.FAILED, errorMessage = result.message) }
        }
    }

    private fun update(id: String, transform: (UploadItem) -> UploadItem) {
        _items.value = _items.value.map { if (it.id == id) transform(it) else it }
    }
}

/**
 * Request de upload multipart de **múltiplas partes nomeadas** (ex.: foto-prova `full`+`thumb`).
 * Complementa o [UploadRequest] (parte única) para a fila [RestUploadQueue].
 */
class MultipartUploadRequest(
    val id: String,
    val fileName: String,
    val path: String,
    val parts: List<MultipartPart>,
)
