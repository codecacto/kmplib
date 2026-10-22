package br.com.codecacto.kmplib.inspection.model

import androidx.compose.runtime.Immutable
import br.com.codecacto.kmplib.ui.components.PhotoSource

/*
 * ESTADO DE TELA do Laudo Kit (§5 da especificação: "o kit recebe dados prontos e emite eventos;
 * nunca chama o backend"). Estes tipos são a FRONTEIRA entre o kit e o produto:
 *
 *   domínio (backlib-inspection → kmplib, rodada seguinte) ──mapeador──▶ *State ──▶ componente
 *   componente ──*Event──▶ ViewModel do produto ──▶ domínio / fila / rede
 *
 * Nada aqui conhece DTO, rota ou banco; os nomes são genéricos (Room = agrupador, Item = item
 * inspecionado). O mapeador do domínio para estes estados é uma função pura por tela.
 */

/** Fase da vistoria: entrada (estado por item) ou saída (resultado e divergência por item). */
enum class InspectionPhase { ENTRY, EXIT }

/** Uma miniatura de mídia no item ou no cômodo (§2.9). */
@Immutable
data class InspectionMediaThumb(
    val id: String,
    /** A imagem (miniatura local, `PhotoSource.authenticated` do servidor…); `null` = ainda sem imagem. */
    val source: PhotoSource?,
    val status: MediaUploadStatus = MediaUploadStatus.QUEUED,
    val kind: InspectionMediaKind = InspectionMediaKind.PHOTO,
    /** [InspectionMediaSource.GALLERY] = anexo (etiqueta "Anexo"), nunca prova. */
    val origin: InspectionMediaSource = InspectionMediaSource.CAMERA,
    /** Progresso do envio `0..1` quando [status] = enviando; `null` = desconhecido. */
    val uploadProgress: Float? = null,
    /** Duração (vídeo/voz), em milissegundos. */
    val durationMillis: Long? = null,
    /** Carimbo curto já formatado (`14/09/25 09:12`), mostrado na faixa da foto. */
    val stampLabel: String? = null,
    /** Pode ser removida (rascunho). Mídia confirmada de vistoria assinada nunca. */
    val removable: Boolean = false,
) {
    /** O id basta para log: nada de caminho local nem URL (podem ter token). */
    override fun toString(): String = "InspectionMediaThumb(id=$id, status=$status, kind=$kind, origin=$origin)"
}

// ───────────── Cômodos (§2.5) ─────────────

/** Andamento de um cômodo: [done] de [total] itens avaliados. */
@Immutable
data class RoomProgress(
    val id: String,
    val name: String,
    val done: Int,
    val total: Int,
    /** Tem mídia (remover pede confirmação dizendo que as fotos saem junto). */
    val hasMedia: Boolean = done > 0,
) {
    val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    val isComplete: Boolean get() = total > 0 && done >= total
    val isStarted: Boolean get() = done > 0
    val remaining: Int get() = (total - done).coerceAtLeast(0)
}

/** A lista de cômodos de uma vistoria. */
@Immutable
data class RoomListState(
    /** "Apto 302 · Entrada" — o objeto e a fase, montados pelo produto. */
    val contextLabel: String,
    val rooms: List<RoomProgress> = emptyList(),
    val isLoading: Boolean = false,
    /** Falha de gravação local / leitura — mostrada com "Tentar de novo". */
    val errorMessage: String? = null,
    /** Vistoria assinada: sem reordenar, sem menu, sem adicionar. */
    val readOnly: Boolean = false,
) {
    val readyCount: Int get() = rooms.count { it.isComplete }
    val overallFraction: Float get() = if (rooms.isEmpty()) 0f else readyCount.toFloat() / rooms.size
}

sealed interface RoomListEvent {
    data object Back : RoomListEvent
    data object Review : RoomListEvent
    data object Add : RoomListEvent
    data object Retry : RoomListEvent
    data class Open(val roomId: String) : RoomListEvent
    /** Arrastando: [from] → [to] na lista exibida. */
    data class Move(val from: Int, val to: Int) : RoomListEvent
    /** Soltou o arrasto: hora de gravar a ordem nova. */
    data object MoveFinished : RoomListEvent
    data class Duplicate(val roomId: String) : RoomListEvent
    data class Rename(val roomId: String, val newName: String) : RoomListEvent
    /** Já confirmado pela pessoa no diálogo. */
    data class Remove(val roomId: String) : RoomListEvent
}

// ───────────── Itens do cômodo (§2.6) ─────────────

/** Uma linha da lista de itens. */
@Immutable
data class ItemRowState(
    val id: String,
    val name: String,
    /** Estado na entrada (ou o da entrada, na saída). */
    val condition: ItemCondition? = ItemCondition.GOOD,
    /** Resultado na saída; `null` = ainda não avaliado. */
    val exitResult: ExitResult? = null,
    val photoCount: Int = 0,
    val hasVoiceNote: Boolean = false,
    /** Fotos obrigatórias que ainda faltam (0 = cumprido ou sem mínimo). */
    val missingRequiredPhotos: Int = 0,
    /** Item de **tarefa guiada** (D5): vem antes dos itens do modelo, fundo `brandSoft`, chip "Tarefa". */
    val isGuidedTask: Boolean = false,
    val guidedTaskRequiredPhotos: Int = 0,
    /** Na saída: o estado mudou em relação à entrada (filtro "Só o que mudou"). */
    val changedSinceEntry: Boolean = false,
) {
    /** Com problema (filtro e revisão): na entrada, Regular/Ruim; na saída, resultado ≠ Conforme. */
    fun isProblem(phase: InspectionPhase): Boolean = when (phase) {
        InspectionPhase.ENTRY -> condition?.isProblem == true
        InspectionPhase.EXIT -> exitResult != null && exitResult != ExitResult.CONFORMING
    }

    /** Pendente: falta foto obrigatória, ou (saída) ainda sem resultado. */
    fun isPending(phase: InspectionPhase): Boolean =
        missingRequiredPhotos > 0 || (phase == InspectionPhase.EXIT && exitResult == null && !isGuidedTask)
}

/** Filtro da lista de itens (pílulas). Entrada: Todos · Com problema · Sem foto. Saída: Todos · Só o que mudou · Pendentes. */
enum class ItemFilter { ALL, WITH_PROBLEM, WITHOUT_PHOTO, CHANGED, PENDING }

@Immutable
data class ItemListState(
    val roomName: String,
    val phase: InspectionPhase = InspectionPhase.ENTRY,
    val items: List<ItemRowState> = emptyList(),
    val roomMedia: List<InspectionMediaThumb> = emptyList(),
    val filter: ItemFilter = ItemFilter.ALL,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val readOnly: Boolean = false,
    val isMarkingRoomReady: Boolean = false,
)

sealed interface ItemListEvent {
    data object Back : ItemListEvent
    data object Menu : ItemListEvent
    data object Retry : ItemListEvent
    data class OpenItem(val itemId: String) : ItemListEvent
    data class Filter(val filter: ItemFilter) : ItemListEvent
    data object AddRoomPhoto : ItemListEvent
    data object AddRoomVideo : ItemListEvent
    data object AddRoomVoice : ItemListEvent
    data class OpenRoomMedia(val mediaId: String) : ItemListEvent
    data object RoomReady : ItemListEvent
    /** Saída: "Marcar todos como Conforme" — já confirmado no diálogo. */
    data object MarkAllConforming : ItemListEvent
}

// ───────────── Folha do item (§2.7) ─────────────

@Immutable
data class ItemSheetState(
    val itemId: String,
    val title: String,
    val roomName: String,
    /** Posição 1-based no cômodo ("item 4 de 14"). */
    val position: Int,
    val total: Int,
    val condition: ItemCondition = ItemCondition.GOOD,
    /** `null` = o item não pergunta "Funciona?" (o chip do editor desliga). */
    val functioning: Functioning? = null,
    val asksFunctioning: Boolean = false,
    val note: String = "",
    val media: List<InspectionMediaThumb> = emptyList(),
    /** Tarefa guiada (D5): sem Estado/Funciona?; "Salvar" só depois do mínimo de fotos. */
    val isGuidedTask: Boolean = false,
    val minPhotos: Int = 0,
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    /** Prévia do editor de modelo / laudo assinado: tudo desenhado, nada editável. */
    val readOnly: Boolean = false,
    /** Erro de gravação, mostrado colado ao botão. */
    val errorMessage: String? = null,
    /** Pedir confirmação de descarte (o produto liga ao receber [ItemSheetEvent.Close] com mudanças). */
    val confirmDiscard: Boolean = false,
) {
    /** Fotos que contam para o mínimo: as da câmera (galeria é anexo, não prova). */
    val cameraPhotoCount: Int
        get() = media.count { it.kind == InspectionMediaKind.PHOTO && it.origin == InspectionMediaSource.CAMERA }

    val missingPhotos: Int get() = (minPhotos - cameraPhotoCount).coerceAtLeast(0)

    /** "Salvar" ligado? Na tarefa guiada, só com o mínimo cumprido. */
    val canSave: Boolean get() = !readOnly && !isSaving && (!isGuidedTask || missingPhotos == 0)
}

sealed interface ItemSheetEvent {
    data class ConditionChanged(val condition: ItemCondition) : ItemSheetEvent
    data class FunctioningChanged(val functioning: Functioning) : ItemSheetEvent
    data class NoteChanged(val note: String) : ItemSheetEvent
    data object TakePhoto : ItemSheetEvent
    data object RecordVideo : ItemSheetEvent
    data object RecordVoice : ItemSheetEvent
    data object AttachFromGallery : ItemSheetEvent
    data class OpenMedia(val mediaId: String) : ItemSheetEvent
    data class RetryMedia(val mediaId: String) : ItemSheetEvent
    /** Já confirmado no diálogo. */
    data class RemoveMedia(val mediaId: String) : ItemSheetEvent
    data object Save : ItemSheetEvent
    /** Tocou no "X": o produto decide (com mudança → `confirmDiscard = true`). */
    data object Close : ItemSheetEvent
    data object ConfirmDiscard : ItemSheetEvent
    data object DismissDiscard : ItemSheetEvent
}

// ───────────── Saída lado a lado (§2.8) ─────────────

enum class ExitCompareTab { ENTRY, EXIT, SIDE_BY_SIDE }

/** O que a entrada registrou do item (foto do cache local, estado, observação). */
@Immutable
data class EntrySnapshot(
    val photo: InspectionMediaThumb?,
    val condition: ItemCondition?,
    val note: String? = null,
    val isPhotoLoading: Boolean = false,
)

@Immutable
data class ExitCompareState(
    val itemId: String,
    val title: String,
    val roomName: String,
    val tab: ExitCompareTab = ExitCompareTab.SIDE_BY_SIDE,
    val entry: EntrySnapshot,
    val exitPhotos: List<InspectionMediaThumb> = emptyList(),
    val result: ExitResult? = null,
    val divergence: Divergence? = null,
    val note: String = "",
    /** Mostrar o erro "Escolha a divergência" (depois de tentar salvar). */
    val showDivergenceError: Boolean = false,
    val isSaving: Boolean = false,
    val readOnly: Boolean = false,
    val errorMessage: String? = null,
) {
    val divergenceVisible: Boolean get() = result?.requiresDivergence == true
    val divergenceMissing: Boolean get() = divergenceVisible && divergence == null
}

sealed interface ExitCompareEvent {
    data object Back : ExitCompareEvent
    data class SelectTab(val tab: ExitCompareTab) : ExitCompareEvent
    data class ResultChanged(val result: ExitResult) : ExitCompareEvent
    data class DivergenceChanged(val divergence: Divergence) : ExitCompareEvent
    data class NoteChanged(val note: String) : ExitCompareEvent
    /** Abre a câmera com a foto da entrada sobreposta. */
    data object TakeExitPhoto : ExitCompareEvent
    data class OpenEntryPhoto(val mediaId: String) : ExitCompareEvent
    data object OpenExitPhotos : ExitCompareEvent
    data object Save : ExitCompareEvent
}

// ───────────── Revisão (§2.11) ─────────────

@Immutable
data class ReviewPendency(
    val id: String,
    /** "Box de vidro · faltam 2 fotos" — montado pelo produto (ou por [InspectionRules.pendencies]). */
    val label: String,
    val itemId: String? = null,
    val roomId: String? = null,
)

@Immutable
data class ReviewProblem(
    val itemId: String,
    val name: String,
    val roomName: String,
    val condition: ItemCondition? = null,
    val result: ExitResult? = null,
    val divergence: Divergence? = null,
)

@Immutable
data class ReviewCounts(val rooms: Int, val items: Int, val photos: Int, val videos: Int)

@Immutable
data class ReviewState(
    val phase: InspectionPhase,
    /** "Entrada · Apto 302". */
    val title: String,
    val counts: ReviewCounts,
    val pendencies: List<ReviewPendency> = emptyList(),
    val conditionCounts: Map<ItemCondition, Int> = emptyMap(),
    val resultCounts: Map<ExitResult, Int> = emptyMap(),
    val problems: List<ReviewProblem> = emptyList(),
    val isLoading: Boolean = false,
    val showFinishDialog: Boolean = false,
    val isFinishing: Boolean = false,
    val finishError: String? = null,
) {
    val canFinish: Boolean get() = pendencies.isEmpty() && !isLoading && !isFinishing
}

sealed interface ReviewEvent {
    data object Back : ReviewEvent
    data class GoToPendency(val pendencyId: String) : ReviewEvent
    data class OpenProblem(val itemId: String) : ReviewEvent
    /** Tocou em "Finalizar": o produto abre o diálogo (`showFinishDialog = true`). */
    data object Finish : ReviewEvent
    data object ConfirmFinish : ReviewEvent
    data object DismissFinish : ReviewEvent
}

// ───────────── Laudo (§2.12) ─────────────

sealed interface ReportStatus {
    /** Ainda subindo mídia / gerando: "Faltam 14 mídias · 177 de 191 enviadas". Não é erro. */
    data class Generating(val uploaded: Int, val total: Int) : ReportStatus {
        val remaining: Int get() = (total - uploaded).coerceAtLeast(0)
        val fraction: Float get() = if (total <= 0) 0f else (uploaded.toFloat() / total).coerceIn(0f, 1f)
    }

    data object Ready : ReportStatus

    data class Failed(val message: String? = null) : ReportStatus
}

/** O que prova o laudo: hash, assinaturas, quando e onde, e o link de verificação. */
@Immutable
data class ReportProof(
    val hash: String,
    val signedCount: Int,
    /** `03/03/2025 10:52` (formatado pelo produto na região do aparelho). */
    val signedAtLabel: String?,
    /** `−23,5505 −46,6333` ([br.com.codecacto.kmplib.inspection.formatCoordinates]) ou "Sem localização". */
    val locationLabel: String?,
    val verifyUrl: String,
)

@Immutable
data class ReportVersion(
    val id: String,
    /** "v1", "v2". */
    val label: String,
    val hash: String,
    val createdAtLabel: String,
    val isCurrent: Boolean,
)

@Immutable
data class ReportViewerState(
    /** "Laudo · Apto 302 · v1". */
    val title: String,
    val status: ReportStatus,
    val proof: ReportProof? = null,
    val versions: List<ReportVersion> = emptyList(),
    /** Esta versão foi substituída: aviso âmbar "Há uma versão mais recente". */
    val isOutdated: Boolean = false,
    val showVersions: Boolean = false,
)

sealed interface ReportViewerEvent {
    data object Back : ReportViewerEvent
    data object Retry : ReportViewerEvent
    data object SharePdf : ReportViewerEvent
    data object CopyVerifyLink : ReportViewerEvent
    data object OpenVersions : ReportViewerEvent
    data object DismissVersions : ReportViewerEvent
    data class OpenVersion(val versionId: String) : ReportViewerEvent
    data object CreateNewVersion : ReportViewerEvent
}

// ───────────── Fila de mídia (§2.16) ─────────────

/** Resumo da fila de envio para a faixa e para a barra do laudo. */
@Immutable
data class MediaQueueSummary(
    val total: Int,
    val confirmed: Int,
    val failed: Int = 0,
    /** Vídeos esperando Wi-Fi (só-Wi-Fi para vídeo). */
    val waitingWifi: Int = 0,
    val offline: Boolean = false,
) {
    val isDone: Boolean get() = total > 0 && confirmed >= total
}

/** O que a faixa mostra — derivado por [InspectionRules.queueBannerKind]. */
enum class MediaQueueBannerKind { UPLOADING, WAITING_WIFI, OFFLINE, FAILED, DONE, HIDDEN }

@Immutable
data class MediaQueueEntry(
    val id: String,
    /** "Quarto 1 · Foto 4". */
    val label: String,
    val thumb: InspectionMediaThumb,
)

@Immutable
data class MediaQueueScreenState(
    val summary: MediaQueueSummary,
    val failed: List<MediaQueueEntry> = emptyList(),
    val pending: List<MediaQueueEntry> = emptyList(),
    val confirmed: List<MediaQueueEntry> = emptyList(),
    val isRefreshing: Boolean = false,
    /** Bytes já confirmados no servidor que podem sair do aparelho (`null` = não calculado). */
    val reclaimableBytes: Long? = null,
)

sealed interface MediaQueueEvent {
    data object Back : MediaQueueEvent
    data object Refresh : MediaQueueEvent
    data object RetryAll : MediaQueueEvent
    data class Retry(val entryId: String) : MediaQueueEvent
    data object SendWithMobileData : MediaQueueEvent
    data object ReclaimStorage : MediaQueueEvent
    data object Open : MediaQueueEvent
}
