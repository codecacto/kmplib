@file:OptIn(ExperimentalTime::class)

package br.com.codecacto.kmplib.inspection.mapping

import br.com.codecacto.kmplib.core.storage.BlobStore
import br.com.codecacto.kmplib.inspection.InspectionReviewTexts
import br.com.codecacto.kmplib.inspection.data.ComparisonItemDto
import br.com.codecacto.kmplib.inspection.data.FinishIssueDto
import br.com.codecacto.kmplib.inspection.data.FinishIssueReasons
import br.com.codecacto.kmplib.inspection.data.GroupDto
import br.com.codecacto.kmplib.inspection.data.InspectionApi
import br.com.codecacto.kmplib.inspection.data.InspectionContext
import br.com.codecacto.kmplib.inspection.data.InspectionDto
import br.com.codecacto.kmplib.inspection.data.InspectionMediaTarget
import br.com.codecacto.kmplib.inspection.data.InspectionSessionState
import br.com.codecacto.kmplib.inspection.data.ItemDto
import br.com.codecacto.kmplib.inspection.data.ItemState
import br.com.codecacto.kmplib.inspection.data.MediaDto
import br.com.codecacto.kmplib.inspection.data.MediaKind
import br.com.codecacto.kmplib.inspection.data.MediaSource
import br.com.codecacto.kmplib.inspection.data.MediaStatus
import br.com.codecacto.kmplib.inspection.data.QueuedInspectionMedia
import br.com.codecacto.kmplib.inspection.data.ReportVersionDto
import br.com.codecacto.kmplib.inspection.data.ResultAxis
import br.com.codecacto.kmplib.inspection.data.VoiceSuggestionsDto
import br.com.codecacto.kmplib.inspection.fmt
import br.com.codecacto.kmplib.inspection.formatCoordinates
import br.com.codecacto.kmplib.inspection.model.EntrySnapshot
import br.com.codecacto.kmplib.inspection.model.ExitCompareState
import br.com.codecacto.kmplib.inspection.model.ExitCompareTab
import br.com.codecacto.kmplib.inspection.model.InspectionMediaKind
import br.com.codecacto.kmplib.inspection.model.InspectionMediaSource
import br.com.codecacto.kmplib.inspection.model.InspectionMediaThumb
import br.com.codecacto.kmplib.inspection.model.InspectionPhase
import br.com.codecacto.kmplib.inspection.model.InspectionRules
import br.com.codecacto.kmplib.inspection.model.ItemFilter
import br.com.codecacto.kmplib.inspection.model.ItemListState
import br.com.codecacto.kmplib.inspection.model.ItemRowState
import br.com.codecacto.kmplib.inspection.model.ItemSheetState
import br.com.codecacto.kmplib.inspection.model.MediaQueueEntry
import br.com.codecacto.kmplib.inspection.model.MediaQueueScreenState
import br.com.codecacto.kmplib.inspection.model.MediaQueueSummary
import br.com.codecacto.kmplib.inspection.model.MediaUploadStatus
import br.com.codecacto.kmplib.inspection.model.ReportProof
import br.com.codecacto.kmplib.inspection.model.ReportStatus
import br.com.codecacto.kmplib.inspection.model.ReportVersion
import br.com.codecacto.kmplib.inspection.model.ReportViewerState
import br.com.codecacto.kmplib.inspection.model.ReviewCounts
import br.com.codecacto.kmplib.inspection.model.ReviewPendency
import br.com.codecacto.kmplib.inspection.model.ReviewProblem
import br.com.codecacto.kmplib.inspection.model.ReviewState
import br.com.codecacto.kmplib.inspection.model.RoomListState
import br.com.codecacto.kmplib.inspection.model.RoomProgress
import br.com.codecacto.kmplib.inspection.model.VoiceCapturePhase
import br.com.codecacto.kmplib.inspection.model.VoiceCaptureState
import br.com.codecacto.kmplib.inspection.model.VoiceProposalRow
import br.com.codecacto.kmplib.sync.direct.DirectUploadStatus
import br.com.codecacto.kmplib.sync.direct.defaultStorageHttpClient
import br.com.codecacto.kmplib.ui.components.PhotoSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.CancellationException
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * De onde vem a imagem de cada miniatura: a do servidor (mídia CONFIRMADA) e a local (ainda na fila).
 * `null` = sem imagem ainda (o kit desenha o lugar vazio).
 */
interface InspectionPhotoResolver {
    fun server(media: MediaDto): PhotoSource?
    fun local(media: QueuedInspectionMedia): PhotoSource?

    companion object {
        /** Sem imagem (teste, prévia). */
        val None: InspectionPhotoResolver = object : InspectionPhotoResolver {
            override fun server(media: MediaDto): PhotoSource? = null
            override fun local(media: QueuedInspectionMedia): PhotoSource? = null
        }
    }
}

/**
 * O [InspectionPhotoResolver] padrão: a da fila sai do [BlobStore] (o original da captura); a do servidor
 * pede a URL curta (`GET /media/{id}/url`, miniatura quando há) e baixa SEM o Bearer — o storage não é o
 * nosso servidor. Cache de memória/disco é o do Coil (a chave é o id, nunca a URL assinada).
 */
class InspectionPhotoSources(
    private val api: InspectionApi,
    private val blobs: BlobStore,
    private val storageHttp: HttpClient = defaultStorageHttpClient(),
) : InspectionPhotoResolver {
    override fun server(media: MediaDto): PhotoSource? {
        if (media.status != MediaStatus.CONFIRMED) return null
        val imagem = media.kind == MediaKind.PHOTO || (media.kind == MediaKind.ATTACHMENT && media.contentType.startsWith("image/"))
        if (!imagem && !media.hasThumb) return null
        val thumb = media.hasThumb
        return PhotoSource.Loader(key = "insp-midia-${media.id}" + if (thumb) "-t" else "") { download(media.id, thumb) }
    }

    override fun local(media: QueuedInspectionMedia): PhotoSource? {
        if (media.kind != MediaKind.PHOTO && media.kind != MediaKind.ATTACHMENT) return null
        val blob = media.blobId ?: return null
        return PhotoSource.Loader(key = "insp-local-$blob") { blobs.read(blob) }
    }

    private suspend fun download(mediaId: String, thumb: Boolean): ByteArray? {
        val url = api.mediaUrl(mediaId, thumb).getOrNull()?.url ?: return null
        return try {
            val r = storageHttp.get(url)
            if (r.status.value in 200..299) r.readRawBytes() else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * **Mapeadores domínio → estado de tela do kit** — funções PURAS, uma por tela (testadas em
 * `InspectionMappersTest`). Entram o [InspectionSessionState] (servidor + mudanças pendentes + fila) e o que
 * só o produto sabe (rótulos montados, formatação de data na região do aparelho); sai o `*State` pronto.
 *
 * Contagens de "feito"/"faltam fotos" seguem a MESMA regra do `finish` do servidor
 * ([localIssues]): fotos de CÂMERA registradas (inclusive as que estão na fila do aparelho) contam;
 * galeria não. "Campos obrigatórios" (FormSchema) não é conferido aqui — o servidor diz no 422.
 */
object InspectionMappers {

    // ── Miniaturas ───────────────────────────────────────────────────────────────────────────────

    /**
     * As miniaturas de um alvo: as do servidor + as da fila, sem repetir (mesmo hash = a mesma mídia; a da
     * fila vence enquanto não confirmar, porque é ela que tem progresso/falha).
     */
    fun mediaThumbs(
        server: List<MediaDto>,
        queued: List<QueuedInspectionMedia>,
        photos: InspectionPhotoResolver,
        editable: Boolean,
        stamp: (Instant) -> String? = { null },
    ): List<InspectionMediaThumb> {
        val porHash = queued.associateBy { it.sha256 }
        val doServidor = server.mapNotNull { m ->
            val kind = m.kind.toKitKind() ?: return@mapNotNull null
            val local = porHash[m.sha256]
            if (local != null && m.status != MediaStatus.CONFIRMED) return@mapNotNull null
            InspectionMediaThumb(
                id = m.id,
                source = photos.server(m) ?: local?.let(photos::local),
                status = if (m.status == MediaStatus.CONFIRMED) MediaUploadStatus.CONFIRMED else MediaUploadStatus.QUEUED,
                kind = kind,
                origin = if (m.source == MediaSource.CAMERA) InspectionMediaSource.CAMERA else InspectionMediaSource.GALLERY,
                durationMillis = m.durationMs?.toLong(),
                stampLabel = m.capturedAtDevice?.let(::parseInstant)?.let(stamp),
                removable = editable,
            )
        }
        val confirmados = server.filter { it.status == MediaStatus.CONFIRMED }.map { it.sha256 }.toSet()
        val daFila = queued.filter { it.sha256 !in confirmados }.mapNotNull { q -> queuedThumb(q, photos, stamp) }
        return doServidor + daFila
    }

    private fun queuedThumb(q: QueuedInspectionMedia, photos: InspectionPhotoResolver, stamp: (Instant) -> String?): InspectionMediaThumb? {
        val kind = q.kind.toKitKind() ?: return null
        val status = when {
            q.status == DirectUploadStatus.FAILED -> MediaUploadStatus.FAILED
            q.isUploading -> MediaUploadStatus.UPLOADING
            else -> MediaUploadStatus.QUEUED
        }
        return InspectionMediaThumb(
            id = q.jobId,
            source = photos.local(q),
            status = status,
            kind = kind,
            origin = if (q.source == MediaSource.CAMERA) InspectionMediaSource.CAMERA else InspectionMediaSource.GALLERY,
            uploadProgress = if (status == MediaUploadStatus.UPLOADING) q.progress else null,
            stampLabel = null,
            // Da fila sempre sai (desistir do envio); o original no aparelho continua.
            removable = true,
        )
    }

    // ── Regras de completude (espelho do `finish` do servidor) ──────────────────────────────────

    /**
     * As pendências que o aparelho já sabe calcular: mínimo de fotos de câmera, tarefa guiada, leitura de
     * medidor e — na saída ou no eixo "Aceito" — resultado de item obrigatório. Item "Não se aplica" não cobra.
     */
    fun localIssues(inspection: InspectionDto, queued: List<QueuedInspectionMedia>): List<FinishIssueDto> {
        val pedeResultado = inspection.context == InspectionContext.EXIT || inspection.resultAxis == ResultAxis.ACCEPTANCE
        return inspection.groups.flatMap { g ->
            g.items.flatMap { item -> itemIssues(item, g.name, pedeResultado, queued) }
        }
    }

    private fun itemIssues(item: ItemDto, groupName: String, pedeResultado: Boolean, queued: List<QueuedInspectionMedia>): List<FinishIssueDto> {
        if (item.state == ItemState.NA) return emptyList()
        val out = ArrayList<FinishIssueDto>()
        if (item.required && pedeResultado && item.result == null) {
            out += FinishIssueDto(item.id, groupName, item.name, reason = FinishIssueReasons.RESULT_REQUIRED)
        }
        val have = evidenceCount(item, queued)
        if (have < item.minPhotos) {
            out += FinishIssueDto(item.id, groupName, item.name, reason = FinishIssueReasons.MIN_PHOTOS, required = item.minPhotos, have = have)
        }
        for (task in item.mediaTasks) {
            if (!task.required) continue
            val feita = item.media.any { it.taskId == task.id && it.evidence } ||
                queued.any { q -> (q.target as? InspectionMediaTarget.Item)?.let { it.itemId == item.id && it.taskId == task.id } == true && q.source == MediaSource.CAMERA }
            if (!feita) out += FinishIssueDto(item.id, groupName, item.name, reason = FinishIssueReasons.MEDIA_TASK, taskId = task.id)
            if (task.reading != null && item.readings[task.id] == null) {
                out += FinishIssueDto(item.id, groupName, item.name, reason = FinishIssueReasons.READING_REQUIRED, taskId = task.id)
            }
        }
        return out
    }

    /** Provas de câmera do item (servidor + fila, sem contar duas vezes o mesmo hash). */
    fun evidenceCount(item: ItemDto, queued: List<QueuedInspectionMedia>): Int {
        val hashes = item.media.filter { it.evidence }.map { it.sha256 }.toMutableSet()
        queued.forEach { q ->
            if (q.source == MediaSource.CAMERA && (q.target as? InspectionMediaTarget.Item)?.itemId == item.id) hashes += q.sha256
        }
        return hashes.size
    }

    // ── Cômodos ──────────────────────────────────────────────────────────────────────────────────

    /** Lista de cômodos. "Feito" = item sem pendência local (ver [localIssues]). */
    fun roomListState(state: InspectionSessionState, contextLabel: String): RoomListState {
        val insp = state.inspection ?: return RoomListState(contextLabel = contextLabel, isLoading = state.isLoading, errorMessage = state.error?.userMessage)
        val pendentes = localIssues(insp, state.queued).mapNotNull { it.itemId }.toSet()
        return RoomListState(
            contextLabel = contextLabel,
            rooms = insp.groups.sortedBy { it.position }.map { g ->
                RoomProgress(
                    id = g.id,
                    name = g.name,
                    done = g.items.count { it.id !in pendentes },
                    total = g.items.size,
                    hasMedia = g.media.isNotEmpty() || g.items.any { it.media.isNotEmpty() } ||
                        state.queued.any { q -> q.target.belongsTo(g) },
                )
            },
            isLoading = state.isLoading,
            errorMessage = state.error?.userMessage,
            readOnly = !insp.status.isEditable,
        )
    }

    // ── Itens do cômodo ──────────────────────────────────────────────────────────────────────────

    fun itemListState(
        state: InspectionSessionState,
        groupId: String,
        photos: InspectionPhotoResolver,
        filter: ItemFilter = ItemFilter.ALL,
        stamp: (Instant) -> String? = { null },
    ): ItemListState? {
        val insp = state.inspection ?: return null
        val g = insp.groups.firstOrNull { it.id == groupId } ?: return null
        val phase = insp.context.toPhase()
        val editavel = insp.status.isEditable
        return ItemListState(
            roomName = g.name,
            phase = phase,
            items = g.items.sortedBy { it.position }.map { itemRow(it, phase, state.queued) },
            roomMedia = mediaThumbs(g.media, state.queued.filter { (it.target as? InspectionMediaTarget.Group)?.groupId == g.id }, photos, editavel, stamp),
            filter = filter,
            isLoading = state.isLoading,
            errorMessage = state.error?.userMessage,
            readOnly = !editavel,
        )
    }

    /** Uma linha da lista de itens. */
    fun itemRow(item: ItemDto, phase: InspectionPhase, queued: List<QueuedInspectionMedia>): ItemRowState {
        val daFila = queued.filter { (it.target as? InspectionMediaTarget.Item)?.itemId == item.id }
        val fotos = (item.media.filter { it.kind == MediaKind.PHOTO || it.kind == MediaKind.ATTACHMENT }.map { it.sha256 } +
            daFila.filter { it.kind == MediaKind.PHOTO }.map { it.sha256 }).toSet().size
        val tarefasFoto = item.mediaTasks.count { it.required && it.kind == MediaKind.PHOTO }
        return ItemRowState(
            id = item.id,
            name = item.name,
            condition = if (phase == InspectionPhase.EXIT) (item.entry?.state ?: item.state).toCondition() else item.state.toCondition(),
            exitResult = item.result?.toExitResult(),
            photoCount = fotos,
            hasVoiceNote = !item.voiceTranscript.isNullOrBlank(),
            missingRequiredPhotos = (item.minPhotos - evidenceCount(item, queued)).coerceAtLeast(0),
            isGuidedTask = item.mediaTasks.any { it.required },
            guidedTaskRequiredPhotos = tarefasFoto,
            changedSinceEntry = changedSinceEntry(item),
        )
    }

    /** Na saída: estado, funcionamento ou quantidade diferentes dos da entrada (a mesma sugestão mecânica do servidor). */
    fun changedSinceEntry(item: ItemDto): Boolean {
        val e = item.entry ?: return false
        return e.state != item.state || (item.functioning != null && e.functioning != item.functioning) ||
            (item.quantity != null && e.quantity != item.quantity)
    }

    // ── Folha do item ────────────────────────────────────────────────────────────────────────────

    /**
     * A folha do item.
     *
     * @param asksFunctioning o item pergunta "Funciona?". O app deve ler [ItemDto.asksFunctioning] (backlib
     *   0.204.0, snapshot do modelo); o default já faz isso e, quando o servidor não definiu (`null`), cai na
     *   regra antiga "já tem resposta de funcionamento (aqui ou na entrada)".
     */
    fun itemSheetState(
        state: InspectionSessionState,
        itemId: String,
        photos: InspectionPhotoResolver,
        stamp: (Instant) -> String? = { null },
        asksFunctioning: (ItemDto) -> Boolean = { it.asksFunctioning ?: (it.functioning != null || it.entry?.functioning != null) },
    ): ItemSheetState? {
        val insp = state.inspection ?: return null
        val g = insp.groups.firstOrNull { gr -> gr.items.any { it.id == itemId } } ?: return null
        val ordenados = g.items.sortedBy { it.position }
        val i = ordenados.indexOfFirst { it.id == itemId }
        val item = ordenados[i]
        val editavel = insp.status.isEditable
        val daFila = state.queued.filter { (it.target as? InspectionMediaTarget.Item)?.itemId == item.id }
        val tarefasFoto = item.mediaTasks.count { it.required && it.kind == MediaKind.PHOTO }
        return ItemSheetState(
            itemId = item.id,
            title = item.name,
            roomName = g.name,
            position = i + 1,
            total = ordenados.size,
            condition = item.state.toCondition() ?: br.com.codecacto.kmplib.inspection.model.ItemCondition.GOOD,
            functioning = item.functioning?.toFunctioning(),
            asksFunctioning = asksFunctioning(item),
            note = item.observation.orEmpty(),
            media = mediaThumbs(item.media, daFila, photos, editavel, stamp),
            isGuidedTask = item.mediaTasks.any { it.required },
            minPhotos = maxOf(item.minPhotos, tarefasFoto),
            readOnly = !editavel,
            errorMessage = state.error?.userMessage,
            hasUnsavedChanges = state.pending.any { it.id == item.id },
            isSaving = state.isSyncing && state.pending.any { it.id == item.id },
        )
    }

    // ── Saída lado a lado ────────────────────────────────────────────────────────────────────────

    /**
     * A comparação de um item na saída. A divergência mora na COMPARAÇÃO do servidor ([comparison]); o
     * resultado e a observação, no item da saída.
     */
    fun exitCompareState(
        state: InspectionSessionState,
        itemId: String,
        photos: InspectionPhotoResolver,
        comparison: ComparisonItemDto? = null,
        tab: ExitCompareTab = ExitCompareTab.SIDE_BY_SIDE,
        stamp: (Instant) -> String? = { null },
    ): ExitCompareState? {
        val insp = state.inspection ?: return null
        val g = insp.groups.firstOrNull { gr -> gr.items.any { it.id == itemId } } ?: return null
        val item = g.items.first { it.id == itemId }
        val editavel = insp.status.isEditable
        val entrada = item.entry
        val fotoEntrada = entrada?.media?.filter { it.kind == MediaKind.PHOTO && it.evidence }
            ?.let { mediaThumbs(it, emptyList(), photos, editable = false, stamp) }?.firstOrNull()
        return ExitCompareState(
            itemId = item.id,
            title = item.name,
            roomName = g.name,
            tab = tab,
            entry = EntrySnapshot(photo = fotoEntrada, condition = entrada?.state?.toCondition(), note = entrada?.observation),
            exitPhotos = mediaThumbs(item.media, state.queued.filter { (it.target as? InspectionMediaTarget.Item)?.itemId == item.id }, photos, editavel, stamp),
            result = item.result?.toExitResult(),
            divergence = comparison?.divergence?.toDivergence(),
            note = item.observation.orEmpty(),
            isSaving = state.isSyncing && state.pending.any { it.id == item.id },
            readOnly = !editavel,
            errorMessage = state.error?.userMessage,
        )
    }

    // ── Revisão ──────────────────────────────────────────────────────────────────────────────────

    /**
     * A revisão. Pendências: as do último 422 do servidor ([InspectionSessionState.issues]) quando há; senão
     * as [localIssues]. [divergences] = divergência de cada item da saída (da comparação), por `exitItemId`.
     */
    fun reviewState(
        state: InspectionSessionState,
        title: String,
        texts: InspectionReviewTexts,
        taskLabel: (itemId: String, taskId: String) -> String? = { _, _ -> null },
        divergences: Map<String, ComparisonItemDto> = emptyMap(),
        showFinishDialog: Boolean = false,
    ): ReviewState? {
        val insp = state.inspection ?: return null
        val phase = insp.context.toPhase()
        val itens = insp.groups.flatMap { g -> g.items.map { g to it } }
        val todaMidia = insp.headerMedia + insp.groups.flatMap { g -> g.media + g.items.flatMap { it.media } }
        val hashes = todaMidia.map { it.sha256 }.toSet()
        val filaNova = state.queued.filter { it.sha256 !in hashes }
        val fotos = todaMidia.count { it.kind == MediaKind.PHOTO } + filaNova.count { it.kind == MediaKind.PHOTO }
        val videos = todaMidia.count { it.kind == MediaKind.VIDEO } + filaNova.count { it.kind == MediaKind.VIDEO }
        val issues = state.issues.ifEmpty { localIssues(insp, state.queued) }
        val taskLabelOf: (String, String) -> String = { item, task ->
            taskLabel(item, task) ?: itens.firstOrNull { it.second.id == item }?.second?.mediaTasks?.firstOrNull { it.id == task }?.label ?: task
        }
        return ReviewState(
            phase = phase,
            title = title,
            counts = ReviewCounts(rooms = insp.groups.size, items = itens.size, photos = fotos, videos = videos),
            pendencies = issues.mapIndexed { i, issue ->
                ReviewPendency(
                    id = "p$i-${issue.reason}-${issue.itemId ?: issue.headerField.orEmpty()}-${issue.taskId.orEmpty()}",
                    label = pendencyLabel(issue, texts, taskLabelOf),
                    itemId = issue.itemId,
                    roomId = issue.itemId?.let { id -> itens.firstOrNull { it.second.id == id }?.first?.id },
                )
            },
            conditionCounts = InspectionRules.conditionCounts(itens.map { (_, it) -> it.state.toCondition() }),
            resultCounts = InspectionRules.resultCounts(itens.map { (_, it) -> it.result?.toExitResult() }),
            problems = itens.mapNotNull { (g, it) ->
                val cond = it.state.toCondition()
                val res = it.result?.toExitResult()
                val problema = if (phase == InspectionPhase.EXIT) res != null && res.requiresDivergence else cond?.isProblem == true
                if (!problema) null else ReviewProblem(it.id, it.name, g.name, cond, res, divergences[it.id]?.divergence?.toDivergence())
            },
            isLoading = state.isLoading,
            showFinishDialog = showFinishDialog,
            isFinishing = state.isFinishing,
            finishError = state.error?.takeIf { state.issues.isEmpty() }?.userMessage,
        )
    }

    /** A frase de uma pendência ("Box de vidro · faltam 2 fotos"), no idioma da tela. */
    fun pendencyLabel(issue: FinishIssueDto, texts: InspectionReviewTexts, taskLabel: (itemId: String, taskId: String) -> String): String {
        val nome = listOfNotNull(issue.groupName, issue.itemName).joinToString(" · ").ifEmpty { issue.itemId.orEmpty() }
        val tarefa = { issue.itemId?.let { i -> issue.taskId?.let { t -> taskLabel(i, t) } } ?: issue.taskId.orEmpty() }
        return when (issue.reason) {
            FinishIssueReasons.MIN_PHOTOS -> {
                val faltam = ((issue.required ?: 0) - (issue.have ?: 0)).coerceAtLeast(1)
                if (faltam == 1) texts.issueMinPhotosOne.fmt(nome) else texts.issueMinPhotosOther.fmt(nome, faltam)
            }
            FinishIssueReasons.REQUIRED_FIELDS -> texts.issueRequiredFields.fmt(nome)
            FinishIssueReasons.RESULT_REQUIRED -> texts.issueResultRequired.fmt(nome)
            FinishIssueReasons.MEDIA_TASK -> texts.issueMediaTask.fmt(nome, tarefa())
            FinishIssueReasons.READING_REQUIRED -> texts.issueReading.fmt(nome, tarefa())
            FinishIssueReasons.HEADER_REQUIRED -> texts.issueHeader.fmt(issue.headerField.orEmpty())
            else -> nome
        }
    }

    // ── Laudo ────────────────────────────────────────────────────────────────────────────────────

    /**
     * O visualizador do laudo. Sem versão gerada: [ReportStatus.Generating] com o andamento da mídia (não é
     * erro). [selectedId] = a versão aberta (default: a mais nova). Datas formatadas pelo produto ([dateLabel]).
     */
    fun reportViewerState(
        inspection: InspectionDto,
        title: String,
        dateLabel: (Instant) -> String,
        selectedId: String? = null,
        showVersions: Boolean = false,
    ): ReportViewerState {
        val versoes = inspection.reports.sortedByDescending { it.version }
        val atual = versoes.firstOrNull { it.supersededAt == null } ?: versoes.firstOrNull()
        val aberta = versoes.firstOrNull { it.id == selectedId } ?: atual
        if (aberta == null) {
            return ReportViewerState(title = title, status = ReportStatus.Generating(inspection.mediaConfirmed, inspection.mediaTotal))
        }
        return ReportViewerState(
            title = title,
            status = ReportStatus.Ready,
            proof = reportProof(aberta, dateLabel),
            versions = versoes.map { v ->
                ReportVersion(
                    id = v.id,
                    label = "v${v.version}",
                    hash = v.pdfSha256,
                    createdAtLabel = parseInstant(v.generatedAt)?.let(dateLabel) ?: v.generatedAt,
                    isCurrent = v.id == atual?.id,
                )
            },
            isOutdated = aberta.supersededAt != null || (atual != null && aberta.id != atual.id),
            showVersions = showVersions,
        )
    }

    /** A prova de uma versão: hash do PDF, quantos assinaram, quando (a última) e onde (a 1ª com GPS). */
    fun reportProof(version: ReportVersionDto, dateLabel: (Instant) -> String): ReportProof {
        val assinaturas = version.signatures.sortedBy { it.signedAt }
        val ultima = assinaturas.lastOrNull()?.signedAt?.let(::parseInstant)
        val comGps = assinaturas.firstOrNull { it.lat != null && it.lng != null }
        return ReportProof(
            hash = version.pdfSha256,
            signedCount = assinaturas.size,
            signedAtLabel = ultima?.let(dateLabel),
            locationLabel = comGps?.let { formatCoordinates(it.lat!!, it.lng!!) },
            verifyUrl = version.verifyUrl,
        )
    }

    // ── Fila de mídia ────────────────────────────────────────────────────────────────────────────

    /** O resumo da fila (faixa e barra do laudo): o servidor + o que ainda está só no aparelho. */
    fun mediaQueueSummary(state: InspectionSessionState, offline: Boolean = false): MediaQueueSummary {
        val insp = state.inspection
        val conhecidos = insp?.let { i -> (i.headerMedia + i.groups.flatMap { g -> g.media + g.items.flatMap { it.media } }).map { it.sha256 }.toSet() }.orEmpty()
        val soNoAparelho = state.queued.count { it.sha256 !in conhecidos }
        return MediaQueueSummary(
            total = (insp?.mediaTotal ?: 0) + soNoAparelho,
            confirmed = insp?.mediaConfirmed ?: 0,
            failed = state.queued.count { it.status == DirectUploadStatus.FAILED },
            waitingWifi = state.queued.count { it.wifiOnly && it.status != DirectUploadStatus.FAILED },
            offline = offline,
        )
    }

    /** A tela da fila. Rótulo de cada entrada: "Cômodo · Item" (ou só o cômodo/cabeçalho). */
    fun mediaQueueScreenState(
        state: InspectionSessionState,
        photos: InspectionPhotoResolver,
        offline: Boolean = false,
        isRefreshing: Boolean = false,
        reclaimableBytes: Long? = null,
        headerLabel: String = "",
    ): MediaQueueScreenState {
        val insp = state.inspection
        fun rotulo(itemId: String?, groupId: String?): String {
            if (insp == null) return headerLabel
            itemId?.let { id ->
                insp.groups.forEach { g -> g.items.firstOrNull { it.id == id }?.let { return "${g.name} · ${it.name}" } }
            }
            groupId?.let { id -> insp.groups.firstOrNull { it.id == id }?.let { return it.name } }
            return headerLabel
        }
        fun entradaDaFila(q: QueuedInspectionMedia): MediaQueueEntry? {
            val t = queuedThumb(q, photos) { null } ?: return null
            val alvo = q.target
            return MediaQueueEntry(q.jobId, rotulo((alvo as? InspectionMediaTarget.Item)?.itemId, (alvo as? InspectionMediaTarget.Group)?.groupId), t)
        }
        val confirmadas = insp?.let { i ->
            i.groups.flatMap { g -> g.media.map { it to g.name } + g.items.flatMap { item -> item.media.map { it to "${g.name} · ${item.name}" } } } +
                i.headerMedia.map { it to headerLabel }
        }.orEmpty().filter { (m, _) -> m.status == MediaStatus.CONFIRMED }.mapNotNull { (m, rot) ->
            val t = mediaThumbs(listOf(m), emptyList(), photos, editable = false).firstOrNull() ?: return@mapNotNull null
            MediaQueueEntry(m.id, rot, t)
        }
        return MediaQueueScreenState(
            summary = mediaQueueSummary(state, offline),
            failed = state.queued.filter { it.status == DirectUploadStatus.FAILED }.mapNotNull(::entradaDaFila),
            pending = state.queued.filter { it.status != DirectUploadStatus.FAILED }.mapNotNull(::entradaDaFila),
            confirmed = confirmadas,
            isRefreshing = isRefreshing,
            reclaimableBytes = reclaimableBytes,
        )
    }

    // ── Voz → item ───────────────────────────────────────────────────────────────────────────────

    /** As propostas da IA para a pessoa CONFIRMAR. Item que não existe na vistoria = "não encontrei", nunca aplicado. */
    fun voiceProposals(state: InspectionSessionState, transcript: String, suggestions: VoiceSuggestionsDto): VoiceCaptureState {
        val insp = state.inspection
        val existentes = insp?.groups?.flatMap { g -> g.items.map { it.id } }?.toSet().orEmpty()
        return VoiceCaptureState(
            phase = VoiceCapturePhase.PROPOSALS,
            transcript = transcript,
            proposals = suggestions.suggestions.mapIndexed { i, s ->
                val achou = s.itemId in existentes
                VoiceProposalRow(
                    id = "$i-${s.itemId}",
                    itemName = s.itemName.takeIf { achou },
                    roomName = s.groupName.takeIf { achou },
                    condition = s.state?.toCondition(),
                    note = s.observation.orEmpty(),
                    matched = achou,
                )
            },
        )
    }

    private fun InspectionMediaTarget?.belongsTo(g: GroupDto): Boolean = when (this) {
        is InspectionMediaTarget.Group -> groupId == g.id
        is InspectionMediaTarget.Item -> g.items.any { it.id == itemId }
        else -> false
    }

    internal fun parseInstant(iso: String): Instant? = runCatching { Instant.parse(iso) }.getOrNull()
}
