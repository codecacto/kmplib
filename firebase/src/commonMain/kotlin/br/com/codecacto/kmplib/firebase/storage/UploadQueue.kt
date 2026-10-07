package br.com.codecacto.kmplib.firebase.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fila reativa de uploads, resiliente a rede ruim (canteiro de obra — NFR do MinhaObra).
 *
 * Reusa [StorageService.uploadBytesWithProgress] para cada item, expõe o agregado como
 * [StateFlow] de [UploadItem]s e oferece **retry** por item. NÃO acopla regra de negócio:
 * o app decide o `path`, os bytes (idealmente comprimidos via
 * [br.com.codecacto.kmplib.platform.ImageCompressor]) e quando enfileirar.
 *
 * Processamento **sequencial** (um upload por vez) por padrão — adequado a redes instáveis,
 * evita saturar a banda do canteiro. Chame [process] a partir de uma coroutine
 * (viewModelScope) e colete [items] na UI com `UploadQueueView`/`UploadProgressItem`.
 *
 * ## Limitação (não resolvida aqui): a fila é SÓ EM MEMÓRIA
 * Os bytes vivem num `mutableMapOf` e a fila num `MutableStateFlow` — **nada sobrevive ao processo
 * morrer**. Quem enfileira uma foto sem sinal e fecha o app perde a foto em silêncio. Para o
 * caminho REST-CRUD existe o substituto durável
 * ([RestUploadOutbox][br.com.codecacto.kmplib.sync.rest.RestUploadOutbox], 2.104.0: binários no
 * disco + fila no espelho `synced_entity` + participação no ciclo de sync). Esta variante, que fala
 * com o **Firebase Storage**, continua em memória: enquanto ela existir, enfileire só o que o
 * usuário está olhando subir, e prefira o Storage do backend de domínio para anexo offline.
 *
 * Exemplo:
 * ```kotlin
 * val queue = UploadQueue(storageService)
 * queue.enqueue(UploadRequest(id, "foto1.jpg", path, compressedBytes, "image/jpeg"))
 * viewModelScope.launch { queue.process() }
 * // na UI: val items by queue.items.collectAsState()
 * ```
 */
class UploadQueue(
    private val storageService: StorageService,
) {
    private val _items = MutableStateFlow<List<UploadItem>>(emptyList())
    val items: StateFlow<List<UploadItem>> = _items.asStateFlow()

    private val requests = mutableMapOf<String, UploadRequest>()

    /** `true` se todos os itens terminais E ao menos um item existe. */
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

    /** Remove um item da fila (e seu request). Não cancela um upload em curso. */
    fun remove(id: String) {
        requests.remove(id)
        _items.value = _items.value.filterNot { it.id == id }
    }

    /**
     * Processa todos os itens PENDING/FAILED em sequência. Idempotente: itens já COMPLETED
     * são pulados. Seguro chamar de novo após adicionar/retry.
     */
    suspend fun process() {
        // Snapshot de ids a processar no momento da chamada.
        val toProcess = _items.value
            .filter { it.status == UploadStatus.PENDING || it.status == UploadStatus.FAILED }
            .map { it.id }
        for (id in toProcess) {
            val req = requests[id] ?: continue
            uploadOne(req)
        }
    }

    /** Reenfileira (FAILED → PENDING) e processa um item específico. */
    suspend fun retry(id: String) {
        val req = requests[id] ?: return
        update(id) { it.copy(status = UploadStatus.PENDING, errorMessage = null, fraction = 0f) }
        uploadOne(req)
    }

    private suspend fun uploadOne(req: UploadRequest) {
        storageService.uploadBytesWithProgress(req.path, req.bytes, req.mimeType).collect { p ->
            when (p) {
                is UploadProgress.Started ->
                    update(req.id) { it.copy(status = UploadStatus.UPLOADING, fraction = 0f) }
                is UploadProgress.Uploading ->
                    update(req.id) { it.copy(status = UploadStatus.UPLOADING, fraction = p.fraction) }
                is UploadProgress.Completed ->
                    update(req.id) {
                        it.copy(status = UploadStatus.COMPLETED, fraction = 1f, downloadUrl = p.downloadUrl)
                    }
                is UploadProgress.Failed ->
                    update(req.id) {
                        it.copy(status = UploadStatus.FAILED, errorMessage = p.cause.message)
                    }
            }
        }
    }

    private fun update(id: String, transform: (UploadItem) -> UploadItem) {
        _items.value = _items.value.map { if (it.id == id) transform(it) else it }
    }
}
