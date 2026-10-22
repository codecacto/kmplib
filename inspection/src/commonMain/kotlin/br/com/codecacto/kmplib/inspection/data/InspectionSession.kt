@file:OptIn(ExperimentalTime::class)

package br.com.codecacto.kmplib.inspection.data

import br.com.codecacto.kmplib.core.util.currentTimeMillis
import br.com.codecacto.kmplib.inspection.capture.CapturedMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * O que a [InspectionSession] sabe agora.
 *
 * @property server a vistoria como o servidor devolveu por último (`null` antes da 1ª carga).
 * @property pending mudanças de item ainda não aceitas pelo servidor (persistidas no disco).
 * @property queued mídias desta vistoria ainda no aparelho ([InspectionMediaQueue]).
 * @property error a última falha (carga, envio de mudança, finalizar) — a tela mostra e some no próximo acerto.
 * @property issues pendências do último 422 `INSPECTION_INCOMPLETE` (zera ao finalizar ou mudar algo).
 */
data class InspectionSessionState(
    val server: InspectionDto? = null,
    val pending: List<ItemPatch> = emptyList(),
    val queued: List<QueuedInspectionMedia> = emptyList(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val isFinishing: Boolean = false,
    val error: InspectionError? = null,
    val issues: List<FinishIssueDto> = emptyList(),
) {
    /** A vistoria VISÍVEL: o servidor + as mudanças pendentes por cima (otimista). */
    val inspection: InspectionDto? get() = server?.applyPatches(pending)

    val hasPendingChanges: Boolean get() = pending.isNotEmpty()

    override fun toString(): String =
        "InspectionSessionState(server=${server?.id}, pending=${pending.size}, queued=${queued.size}, error=${error?.serverCode ?: error?.httpStatus})"
}

/**
 * **Uma vistoria aberta no aparelho** — o "repositório" de uma tela de vistoria: carrega do motor, aplica as
 * mudanças de item na hora (otimista) e as guarda no DISCO até o servidor aceitar, entrega as capturas à
 * [InspectionMediaQueue] e finaliza. É o que o ViewModel do produto segura; os mapeadores (`mapping`)
 * transformam [state] nos `*State` das telas do kit.
 *
 * ### Offline
 * [updateItem] grava a mudança no [patches] ANTES de tentar a rede: sem sinal, ela fica pendente (a tela
 * já mostra o valor novo) e sobe no próximo [sync]. Mudanças do mesmo item se juntam ([mergeItemPatches]).
 * Recusa definitiva do servidor (400/422) **descarta** o lote e recarrega (a mudança nunca seria aceita);
 * 409 (a vistoria saiu de rascunho noutro aparelho) **mantém** — a pessoa reabre ou [discardPendingChanges].
 *
 * ### Mídia
 * [capture] entrega a captura do kit à fila durável; [start] passa a acompanhar a fila desta vistoria e
 * junta cada mídia CONFIRMADA ao [InspectionSessionState.server] sem recarregar a vistoria inteira.
 *
 * ```kotlin
 * val session = InspectionSession(id, api, queue, BlobInspectionPatchStore(blobs), viewModelScope)
 * session.start()                         // fila + mídia confirmada
 * session.load()                          // na abertura e no ON_RESUME
 * session.updateItem(ItemPatch(itemId, state = ItemState.POOR))
 * when (val r = session.finish()) { … InspectionError.Incomplete -> revisão com as pendências }
 * ```
 */
class InspectionSession(
    val inspectionId: String,
    private val api: InspectionApi,
    private val queue: InspectionMediaQueue? = null,
    private val patches: InspectionPatchStore = InMemoryInspectionPatchStore(),
    private val scope: CoroutineScope,
    private val clock: () -> Long = ::currentTimeMillis,
) {
    private val _state = MutableStateFlow(InspectionSessionState())
    val state: StateFlow<InspectionSessionState> = _state.asStateFlow()

    private val syncMutex = Mutex()
    private var jobs: List<Job> = emptyList()
    private var restored = false

    /** Passa a acompanhar a fila de mídia desta vistoria (idempotente). Pare com [close]. */
    fun start() {
        if (jobs.isNotEmpty()) return
        val q = queue ?: return
        jobs = listOf(
            scope.launch { q.observe(inspectionId).collect { l -> _state.update { it.copy(queued = l) } } },
            scope.launch { q.confirmed.filter { it.inspectionId == inspectionId }.collect { c -> onMediaConfirmed(c.media) } },
        )
    }

    fun close() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
    }

    /** Carrega (ou recarrega) do servidor, depois de mandar o que estiver pendente. */
    suspend fun load(): InspectionResult<InspectionDto> {
        restorePending()
        _state.update { it.copy(isLoading = true) }
        if (_state.value.pending.isNotEmpty()) sync()
        val r = api.getInspection(inspectionId)
        _state.update { s ->
            when (r) {
                is InspectionResult.Ok -> s.copy(server = r.value, isLoading = false, error = null)
                is InspectionResult.Failed -> s.copy(isLoading = false, error = r.error)
            }
        }
        return r
    }

    /**
     * Muda um item. Grava no disco, mostra na hora e tenta mandar. Devolve [InspectionResult.Ok] quando a
     * mudança está GUARDADA (enviada ou pendente por falta de rede); [InspectionResult.Failed] quando não
     * gravou no aparelho ou o servidor recusou de vez.
     */
    suspend fun updateItem(patch: ItemPatch): InspectionResult<Unit> {
        restorePending()
        val nova = appendItemPatch(_state.value.pending, patch)
        if (!patches.save(inspectionId, nova)) {
            return InspectionResult.Failed(InspectionError.Server(STORAGE_FAILED, null, STORAGE_FAILED_MESSAGE))
        }
        _state.update { it.copy(pending = nova, issues = emptyList()) }
        return when (val r = sync()) {
            is InspectionResult.Ok -> r
            is InspectionResult.Failed -> if (r.error.isRetryable) InspectionResult.Ok(Unit) else r
        }
    }

    /** Vários itens de uma vez ("Marcar todos como Conforme"). */
    suspend fun updateItems(list: List<ItemPatch>): InspectionResult<Unit> {
        restorePending()
        val nova = list.fold(_state.value.pending, ::appendItemPatch)
        if (!patches.save(inspectionId, nova)) {
            return InspectionResult.Failed(InspectionError.Server(STORAGE_FAILED, null, STORAGE_FAILED_MESSAGE))
        }
        _state.update { it.copy(pending = nova, issues = emptyList()) }
        return when (val r = sync()) {
            is InspectionResult.Ok -> r
            is InspectionResult.Failed -> if (r.error.isRetryable) InspectionResult.Ok(Unit) else r
        }
    }

    /**
     * Manda as mudanças pendentes (em lotes de até [InspectionApi.MAX_ITEMS_PER_PATCH]). Chame no `ON_RESUME`
     * e quando a rede voltar. Uma por vez.
     */
    suspend fun sync(): InspectionResult<Unit> = syncMutex.withLock {
        restorePending()
        var pend = _state.value.pending
        if (pend.isEmpty()) return InspectionResult.Ok(Unit)
        _state.update { it.copy(isSyncing = true) }
        try {
            while (pend.isNotEmpty()) {
                val lote = pend.take(InspectionApi.MAX_ITEMS_PER_PATCH)
                when (val r = api.patchItems(inspectionId, lote)) {
                    is InspectionResult.Ok -> {
                        // Só sai da fila o que foi no lote E não mudou de novo enquanto ia (mesmo objeto).
                        val atual = _state.value.pending
                        val restante = atual.filterNot { p -> lote.any { it === p } }
                        patches.save(inspectionId, restante)
                        _state.update { it.copy(server = r.value, pending = restante, error = null) }
                        pend = restante
                    }
                    is InspectionResult.Failed -> {
                        val e = r.error
                        if (e.isRetryable || e is InspectionError.Conflict || e is InspectionError.Unauthorized || e is InspectionError.QuotaExceeded) {
                            _state.update { it.copy(error = e) }
                            return InspectionResult.Failed(e)
                        }
                        // Recusa definitiva (400/422/404): o lote nunca seria aceito — sai, e a vistoria recarrega.
                        val restante = _state.value.pending.filterNot { p -> lote.any { it === p } }
                        patches.save(inspectionId, restante)
                        _state.update { it.copy(pending = restante, error = e) }
                        api.getInspection(inspectionId).onOk { dto -> _state.update { it.copy(server = dto) } }
                        return InspectionResult.Failed(e)
                    }
                }
            }
            InspectionResult.Ok(Unit)
        } finally {
            _state.update { it.copy(isSyncing = false) }
        }
    }

    /** Desiste das mudanças não enviadas (ex.: a vistoria foi finalizada noutro aparelho). */
    suspend fun discardPendingChanges() {
        patches.save(inspectionId, emptyList())
        _state.update { it.copy(pending = emptyList()) }
    }

    /** Entrega uma captura do kit à fila de mídia. */
    suspend fun capture(
        media: CapturedMedia,
        target: InspectionMediaTarget,
        durationMs: Int? = null,
        thumb: CapturedMedia? = null,
        wifiOnly: Boolean = false,
    ): InspectionEnqueueResult {
        val q = queue ?: return InspectionEnqueueResult.Rejected(InspectionEnqueueRejection.INVALID)
        return q.enqueue(inspectionId, media, target, durationMs, thumb, wifiOnly)
    }

    /**
     * Finaliza: manda o que está pendente e pede ao servidor. Sem rede, NÃO finaliza (o servidor precisa ter o
     * estado final). 422 → [InspectionError.Incomplete], e as pendências vão para [InspectionSessionState.issues].
     */
    suspend fun finish(): InspectionResult<FinishResultDto> {
        _state.update { it.copy(isFinishing = true) }
        try {
            val s = sync()
            if (s is InspectionResult.Failed) return s
            val r = api.finish(inspectionId, finishedAtDevice = Instant.fromEpochMilliseconds(clock()).toString())
            when (r) {
                is InspectionResult.Ok -> {
                    _state.update { st -> st.copy(server = st.server?.copy(status = r.value.status, finishedAtServer = r.value.finishedAtServer), issues = emptyList(), error = null) }
                }
                is InspectionResult.Failed -> {
                    val e = r.error
                    _state.update { it.copy(error = e, issues = (e as? InspectionError.Incomplete)?.issues ?: it.issues) }
                }
            }
            return r
        } finally {
            _state.update { it.copy(isFinishing = false) }
        }
    }

    /** Volta a rascunho (correção depois de finalizar/assinar — gera versão nova do laudo). */
    suspend fun reopen(): InspectionResult<InspectionDto> = replace { api.reopen(inspectionId) }

    suspend fun updateHeader(answers: Map<String, JsonElement>): InspectionResult<InspectionDto> =
        replace { api.updateHeader(inspectionId, answers) }

    suspend fun addGroup(name: String, templateGroupId: String? = null): InspectionResult<InspectionDto> =
        replace { api.addGroup(inspectionId, GroupAddInput(name, templateGroupId)) }

    suspend fun duplicateGroup(groupId: String, name: String? = null): InspectionResult<InspectionDto> =
        replace { api.duplicateGroup(inspectionId, groupId, name) }

    suspend fun renameGroup(groupId: String, name: String): InspectionResult<InspectionDto> =
        replace { api.renameGroup(inspectionId, groupId, name) }

    suspend fun deleteGroup(groupId: String): InspectionResult<InspectionDto> =
        replace { api.deleteGroup(inspectionId, groupId) }

    /** Remove uma mídia confirmada (rascunho). A da fila sai por [InspectionMediaQueue.discard]. */
    suspend fun deleteMedia(mediaId: String): InspectionResult<Unit> {
        val r = api.deleteMedia(mediaId)
        when (r) {
            is InspectionResult.Ok -> _state.update { it.copy(server = it.server?.withoutMedia(mediaId), error = null) }
            is InspectionResult.Failed -> _state.update { it.copy(error = r.error) }
        }
        return r
    }

    /** Voz → propostas da IA (a pessoa confirma; aplicar = [updateItems]). */
    suspend fun voice(transcript: String, groupId: String? = null): InspectionResult<VoiceSuggestionsDto> =
        api.voice(inspectionId, transcript, groupId)

    private suspend fun replace(call: suspend () -> InspectionResult<InspectionDto>): InspectionResult<InspectionDto> {
        val r = call()
        _state.update { s ->
            when (r) {
                is InspectionResult.Ok -> s.copy(server = r.value, error = null)
                is InspectionResult.Failed -> s.copy(error = r.error)
            }
        }
        return r
    }

    private suspend fun restorePending() {
        if (restored) return
        restored = true
        val salvas = patches.load(inspectionId)
        if (salvas.isNotEmpty()) _state.update { s -> s.copy(pending = salvas.fold(s.pending, ::appendItemPatch)) }
    }

    private fun onMediaConfirmed(media: MediaDto) {
        _state.update { s -> s.copy(server = s.server?.withMedia(media)) }
    }

    companion object {
        /** Sentinela: a mudança não gravou no aparelho (disco cheio). */
        const val STORAGE_FAILED: Int = -30
        const val STORAGE_FAILED_MESSAGE: String = "Não foi possível guardar a alteração no aparelho."
    }
}

/** A vistoria com [media] no lugar dela (troca a de mesmo id ou hash; senão entra no alvo). */
fun InspectionDto.withMedia(media: MediaDto): InspectionDto {
    fun List<MediaDto>.junta(): List<MediaDto> {
        val i = indexOfFirst { it.id == media.id || it.sha256 == media.sha256 }
        return if (i >= 0) toMutableList().also { it[i] = media } else this + media
    }
    val existia = (headerMedia + groups.flatMap { g -> g.media + g.items.flatMap { it.media } })
        .firstOrNull { it.id == media.id || it.sha256 == media.sha256 }
    val confirmouAgora = media.status == MediaStatus.CONFIRMED && existia?.status != MediaStatus.CONFIRMED
    val novo = when {
        media.itemId != null -> copy(groups = groups.map { g -> g.copy(items = g.items.map { if (it.id == media.itemId) it.copy(media = it.media.junta()) else it }) })
        media.groupId != null -> copy(groups = groups.map { g -> if (g.id == media.groupId) g.copy(media = g.media.junta()) else g })
        media.headerField != null -> copy(headerMedia = headerMedia.junta())
        else -> this
    }
    return novo.copy(
        mediaTotal = if (existia == null) mediaTotal + 1 else mediaTotal,
        mediaConfirmed = if (confirmouAgora) mediaConfirmed + 1 else mediaConfirmed,
    )
}

/** A vistoria sem a mídia [mediaId]. */
fun InspectionDto.withoutMedia(mediaId: String): InspectionDto {
    val alvo = (headerMedia + groups.flatMap { g -> g.media + g.items.flatMap { it.media } }).firstOrNull { it.id == mediaId } ?: return this
    return copy(
        headerMedia = headerMedia.filterNot { it.id == mediaId },
        groups = groups.map { g -> g.copy(media = g.media.filterNot { it.id == mediaId }, items = g.items.map { it.copy(media = it.media.filterNot { m -> m.id == mediaId }) }) },
        mediaTotal = (mediaTotal - 1).coerceAtLeast(0),
        mediaConfirmed = if (alvo.status == MediaStatus.CONFIRMED) (mediaConfirmed - 1).coerceAtLeast(0) else mediaConfirmed,
    )
}
