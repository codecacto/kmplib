package br.com.codecacto.kmplib.inspection.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ═══════════════════════════════════════════════════════════════════════════════════════════════════
//  ESPELHO do contrato HTTP v1 do `backlib-inspection` (≥ 0.203.0, `InspectionDtos.kt`) — rotas do DONO.
//  Instantes = ISO-8601 UTC em texto; dinheiro e leitura = decimal em TEXTO ("150.00", "1234.567"); ids =
//  UUID em texto. Campo ausente = nulo. Todo campo que o servidor pode omitir tem default aqui, e campo
//  desconhecido é ignorado ([InspectionJson]) — versão nova do servidor acrescenta sem quebrar o app.
//  Referência: skill backlib-catalog → references/backlib-inspection.md.
//
//  Regra de log: DTO com dado pessoal (nome, documento, contato, GPS, URL assinada) tem `toString`
//  redigido. Nada aqui é para log cru.
// ═══════════════════════════════════════════════════════════════════════════════════════════════════

@Serializable
data class PageDto<T>(val items: List<T> = emptyList(), val total: Long = 0, val page: Int = 1, val size: Int = 20)

// ── Objetos ────────────────────────────────────────────────────────────────────────────────────────

@Serializable
data class ObjectInput(
    val subjectType: String = SubjectTypes.PROPERTY,
    val subjectId: String? = null,
    val title: String,
    val address: String? = null,
    val unit: String? = null,
    val attributes: JsonObject = JsonObject(emptyMap()),
) {
    override fun toString(): String = "ObjectInput(subjectType=$subjectType, subjectId=${subjectId != null})"
}

@Serializable
data class ObjectDto(
    val id: String,
    val subjectType: String,
    val subjectId: String? = null,
    val title: String,
    val address: String? = null,
    val unit: String? = null,
    val attributes: JsonObject = JsonObject(emptyMap()),
    val createdAt: String,
    val updatedAt: String,
    val archivedAt: String? = null,
    val inspectionCount: Int = 0,
    val lastInspectionAt: String? = null,
) {
    override fun toString(): String = "ObjectDto(id=$id, subjectType=$subjectType, inspections=$inspectionCount)"
}

// ── Modelos ────────────────────────────────────────────────────────────────────────────────────────

@Serializable
data class TemplateSummaryDto(
    val id: String,
    val subjectType: String,
    val name: String,
    val description: String? = null,
    val origin: TemplateOrigin = TemplateOrigin.UNKNOWN,
    val seedKey: String? = null,
    val version: Int = 1,
    val resultAxis: ResultAxis = ResultAxis.CONFORMITY,
    val groupCount: Int = 0,
    val itemCount: Int = 0,
    val updatedAt: String,
)

@Serializable
data class TemplateDto(
    val id: String,
    val subjectType: String,
    val name: String,
    val description: String? = null,
    val origin: TemplateOrigin = TemplateOrigin.UNKNOWN,
    val seedKey: String? = null,
    val version: Int = 1,
    val resultAxis: ResultAxis = ResultAxis.CONFORMITY,
    /** FormSchema v1 do CABEÇALHO, ou ausente. */
    val headerSchema: JsonElement? = null,
    val groups: List<TemplateGroupDto> = emptyList(),
    val updatedAt: String,
)

@Serializable
data class TemplateGroupDto(
    val id: String,
    val name: String,
    val position: Int = 0,
    val duplicable: Boolean = false,
    val appliedBlocks: List<String> = emptyList(),
    val items: List<TemplateItemDto> = emptyList(),
)

@Serializable
data class TemplateItemDto(
    val id: String,
    val name: String,
    val position: Int = 0,
    val required: Boolean = false,
    val minPhotos: Int = 0,
    /** Perguntas próprias (FormSchema v1); ausente = sem campos extras. */
    val fieldSchema: JsonElement? = null,
    val defaultState: ItemState = ItemState.GOOD,
    val referenceValue: String? = null,
    val mediaTasks: List<MediaTaskDto> = emptyList(),
    /**
     * Pergunta "Funciona?" (backlib 0.204.0): `true` = pergunta Sim/Não/Não testado; `false` = não pergunta;
     * `null` = o servidor não definiu (cliente antigo/modelo anterior) — o app cai na regra de antes.
     */
    val asksFunctioning: Boolean? = null,
)

/**
 * Tarefa de mídia guiada do item. A captura que a cumpre manda o [id] em [MediaIntentInput.taskId];
 * [reading] = tarefa de LEITURA de medidor (a leitura vai em [ItemPatch.readings]).
 */
@Serializable
data class MediaTaskDto(
    val id: String,
    val kind: MediaKind,
    val label: String,
    val hint: String? = null,
    val required: Boolean = true,
    val reading: MeterUnit? = null,
    val maxDurationMs: Int? = null,
)

@Serializable
data class TemplateInput(
    val name: String,
    val subjectType: String = SubjectTypes.PROPERTY,
    val description: String? = null,
    val resultAxis: ResultAxis = ResultAxis.CONFORMITY,
    val headerSchema: JsonElement? = null,
)

@Serializable
data class TemplateDuplicateInput(val name: String? = null)

@Serializable
data class TemplateGroupInput(val name: String, val duplicable: Boolean = false, val blockIds: List<String> = emptyList())

@Serializable
data class TemplateGroupUpdateInput(val name: String? = null, val duplicable: Boolean? = null)

@Serializable
data class TemplateItemInput(
    val name: String,
    val required: Boolean = false,
    val minPhotos: Int = 0,
    val fieldSchema: JsonElement? = null,
    val defaultState: ItemState = ItemState.GOOD,
    val referenceValue: String? = null,
    val mediaTasks: List<MediaTaskDto> = emptyList(),
    /**
     * Pergunta "Funciona?" (backlib 0.204.0): `true` = pergunta Sim/Não/Não testado; `false` = não pergunta;
     * `null` = o servidor não definiu (cliente antigo/modelo anterior) — o app cai na regra de antes.
     */
    val asksFunctioning: Boolean? = null,
)

@Serializable
data class OrderInput(val ids: List<String>)

@Serializable
data class BlockItemDto(
    val name: String,
    val required: Boolean = false,
    val minPhotos: Int = 0,
    val fieldSchema: JsonElement? = null,
    val defaultState: ItemState = ItemState.GOOD,
    val referenceValue: String? = null,
    val mediaTasks: List<MediaTaskDto> = emptyList(),
    /**
     * Pergunta "Funciona?" (backlib 0.204.0): `true` = pergunta Sim/Não/Não testado; `false` = não pergunta;
     * `null` = o servidor não definiu (cliente antigo/modelo anterior) — o app cai na regra de antes.
     */
    val asksFunctioning: Boolean? = null,
)

@Serializable
data class BlockDto(
    val id: String,
    val subjectType: String,
    val seedKey: String? = null,
    val name: String,
    val items: List<BlockItemDto> = emptyList(),
    val updatedAt: String,
)

@Serializable
data class BlockInput(val name: String, val subjectType: String = SubjectTypes.PROPERTY, val items: List<BlockItemDto>)

// ── Vistorias ──────────────────────────────────────────────────────────────────────────────────────

@Serializable
data class TechnicalResponsibleDto(val name: String, val registry: String? = null) {
    override fun toString(): String = "TechnicalResponsibleDto(…)"
}

@Serializable
data class CreateInspectionInput(
    /** UUID gerado NO APARELHO ([br.com.codecacto.kmplib.core.util.newUuidV7]): repetir devolve a mesma vistoria. */
    val clientId: String,
    val objectId: String,
    val templateId: String,
    /** `ENTRY` ou `OTHER` — a saída nasce de `POST /inspections/{entryId}/exit`. */
    val context: InspectionContext = InspectionContext.ENTRY,
    val startedAtDevice: String? = null,
    val technicalResponsible: TechnicalResponsibleDto? = null,
    val reuseFromInspectionId: String? = null,
)

@Serializable
data class CreateExitInput(val clientId: String, val startedAtDevice: String? = null)

@Serializable
data class InspectionSummaryDto(
    val id: String,
    val clientId: String,
    val objectId: String,
    val objectTitle: String,
    val subjectType: String,
    val subjectId: String? = null,
    val templateName: String,
    val context: InspectionContext,
    val entryInspectionId: String? = null,
    val status: InspectionStatus,
    val performer: Performer = Performer.OWNER,
    val resultAxis: ResultAxis = ResultAxis.CONFORMITY,
    val periodoCota: String = "",
    val mediaTotal: Int = 0,
    val mediaConfirmed: Int = 0,
    val createdAt: String,
    val updatedAt: String,
    val finishedAtServer: String? = null,
) {
    override fun toString(): String = "InspectionSummaryDto(id=$id, context=$context, status=$status)"
}

@Serializable
data class InspectionDto(
    val id: String,
    val clientId: String,
    val objectId: String,
    val subjectType: String,
    val subjectId: String? = null,
    /** Snapshot do objeto gravado na vistoria (título, endereço, atributos). */
    val `object`: JsonObject = JsonObject(emptyMap()),
    val templateId: String? = null,
    val templateName: String = "",
    val context: InspectionContext,
    val entryInspectionId: String? = null,
    val reusedFromId: String? = null,
    val status: InspectionStatus,
    val performer: Performer = Performer.OWNER,
    val resultAxis: ResultAxis = ResultAxis.CONFORMITY,
    val headerSchema: JsonElement? = null,
    val headerAnswers: JsonObject = JsonObject(emptyMap()),
    val headerMedia: List<MediaDto> = emptyList(),
    val technicalResponsible: TechnicalResponsibleDto? = null,
    val periodoCota: String = "",
    val startedAtDevice: String? = null,
    val finishedAtDevice: String? = null,
    val finishedAtServer: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val groups: List<GroupDto> = emptyList(),
    val signers: List<SignerDto> = emptyList(),
    val reports: List<ReportVersionDto> = emptyList(),
    val mediaTotal: Int = 0,
    val mediaConfirmed: Int = 0,
) {
    /** Título do objeto no snapshot (`object.title`), quando veio. */
    val objectTitle: String?
        get() = (`object`["title"] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content

    /** Sem endereço, respostas nem partes no log. */
    override fun toString(): String =
        "InspectionDto(id=$id, context=$context, status=$status, groups=${groups.size}, media=$mediaConfirmed/$mediaTotal)"
}

@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    val position: Int = 0,
    val origin: GroupOrigin = GroupOrigin.TEMPLATE,
    val duplicable: Boolean = false,
    val templateGroupId: String? = null,
    val entryGroupId: String? = null,
    val items: List<ItemDto> = emptyList(),
    /** Mídia do CÔMODO (panorama, foto geral). */
    val media: List<MediaDto> = emptyList(),
)

@Serializable
data class ItemDto(
    val id: String,
    val groupId: String,
    val name: String,
    val position: Int = 0,
    val required: Boolean = false,
    val minPhotos: Int = 0,
    val fieldSchema: JsonElement? = null,
    val referenceValue: String? = null,
    val mediaTasks: List<MediaTaskDto> = emptyList(),
    /** Leituras de medidor por tarefa (`taskId` → leitura). */
    val readings: Map<String, MeterReadingDto> = emptyMap(),
    val quantity: Int? = null,
    val answers: JsonObject = JsonObject(emptyMap()),
    val state: ItemState = ItemState.GOOD,
    val result: ItemResult? = null,
    val functioning: ItemFunctioning? = null,
    val observation: String? = null,
    val voiceTranscript: String? = null,
    val entryItemId: String? = null,
    val updatedAt: String = "",
    val media: List<MediaDto> = emptyList(),
    /** Na SAÍDA: o item correspondente da ENTRADA (lado a lado, sobreposição de ângulo). */
    val entry: EntryItemDto? = null,
    /**
     * Pergunta "Funciona?" (backlib 0.204.0, do snapshot do modelo da vistoria): `true` = pergunta Sim/Não/Não testado; `false` = não pergunta;
     * `null` = o servidor não definiu (cliente antigo/modelo anterior) — o app cai na regra de antes.
     */
    val asksFunctioning: Boolean? = null,
) {
    override fun toString(): String = "ItemDto(id=$id, state=$state, result=$result, media=${media.size})"
}

@Serializable
data class EntryItemDto(
    val id: String,
    val state: ItemState = ItemState.GOOD,
    val functioning: ItemFunctioning? = null,
    val quantity: Int? = null,
    val observation: String? = null,
    val answers: JsonObject = JsonObject(emptyMap()),
    val readings: Map<String, MeterReadingDto> = emptyMap(),
    val media: List<MediaDto> = emptyList(),
) {
    override fun toString(): String = "EntryItemDto(id=$id, state=$state, media=${media.size})"
}

@Serializable
data class HeaderInput(val answers: Map<String, JsonElement>)

/**
 * Mudança de um item. Campo `null` = não mexe; para LIMPAR um opcional, ponha o nome em [clear]
 * ([ItemClearFields]). [answers] substitui as respostas inteiras; [readings] substitui só as chaves
 * enviadas (`null` no valor apaga a leitura daquela tarefa).
 */
@Serializable
data class ItemPatch(
    val id: String,
    val state: ItemState? = null,
    val result: ItemResult? = null,
    val functioning: ItemFunctioning? = null,
    val quantity: Int? = null,
    val observation: String? = null,
    val answers: Map<String, JsonElement>? = null,
    val voiceTranscript: String? = null,
    val readings: Map<String, MeterReadingDto?>? = null,
    val clear: List<String> = emptyList(),
) {
    /** Sem observação, respostas nem transcrição no log. */
    override fun toString(): String =
        "ItemPatch(id=$id, state=$state, result=$result, functioning=$functioning, clear=$clear)"
}

@Serializable
data class ItemsPatchInput(val items: List<ItemPatch>)

@Serializable
data class GroupAddInput(val name: String, val templateGroupId: String? = null)

@Serializable
data class GroupRenameInput(val name: String)

@Serializable
data class GroupDuplicateInput(val name: String? = null)

@Serializable
data class FinishInput(val finishedAtDevice: String? = null)

@Serializable
data class FinishResultDto(val status: InspectionStatus, val pendingMedia: Int = 0, val finishedAtServer: String)

/** Uma pendência do 422 `INSPECTION_INCOMPLETE` (`details.issues`). [reason]: ver [FinishIssueReasons]. */
@Serializable
data class FinishIssueDto(
    val itemId: String? = null,
    val groupName: String? = null,
    val itemName: String? = null,
    val headerField: String? = null,
    val reason: String,
    val taskId: String? = null,
    val required: Int? = null,
    val have: Int? = null,
)

/** Leitura de medidor: [value] decimal em TEXTO, com no máximo [MeterUnit.maxDecimals] casas. */
@Serializable
data class MeterReadingDto(
    val value: String,
    val unit: MeterUnit,
    /** Foto da MESMA tarefa e item. */
    val mediaId: String? = null,
    val meterNumber: String? = null,
    val readAt: String? = null,
)

// ── Mídia ──────────────────────────────────────────────────────────────────────────────────────────

@Serializable
data class ThumbIntentDto(val sizeBytes: Long, val contentType: String, val sha256: String? = null)

/**
 * Intenção de mídia. [sha256] é o do APARELHO na captura (`CapturedMedia.sha256`) — chave de idempotência
 * e prova. Alvo: UM de [itemId] · [groupId] · [headerField]. [thumb] só para VÍDEO.
 */
@Serializable
data class MediaIntentInput(
    val sha256: String,
    val sizeBytes: Long,
    val contentType: String,
    val kind: MediaKind,
    val source: MediaSource,
    val itemId: String? = null,
    val groupId: String? = null,
    val headerField: String? = null,
    val taskId: String? = null,
    val capturedAtDevice: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracyM: Double? = null,
    val deviceModel: String? = null,
    val durationMs: Int? = null,
    val thumb: ThumbIntentDto? = null,
) {
    /** GPS é dado pessoal: fora do log. */
    override fun toString(): String =
        "MediaIntentInput(kind=$kind, source=$source, sizeBytes=$sizeBytes, taskId=$taskId, gps=${lat != null})"
}

/** Para onde mandar o arquivo: **exatamente** [method], [url] e TODOS os [headers] (no R2, `If-None-Match: *`). */
@Serializable
data class UploadTargetDto(val method: String = "PUT", val url: String, val headers: Map<String, String> = emptyMap(), val expiresAt: String) {
    override fun toString(): String = "UploadTargetDto(method=$method, headers=${headers.keys.sorted()}, expiresAt=$expiresAt)"
}

@Serializable
data class UploadPartDto(
    val partNumber: Int,
    val sizeBytes: Long,
    val method: String = "PUT",
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val expiresAt: String,
) {
    override fun toString(): String = "UploadPartDto(partNumber=$partNumber, sizeBytes=$sizeBytes, expiresAt=$expiresAt)"
}

@Serializable
data class UploadInstructionsDto(
    /** [UploadModes.SINGLE] = um PUT em [single]; [UploadModes.MULTIPART] = um PUT por parte em [parts]. */
    val mode: String,
    val single: UploadTargetDto? = null,
    val parts: List<UploadPartDto>? = null,
    val partSizeBytes: Long? = null,
    val partCount: Int? = null,
    val thumb: UploadTargetDto? = null,
)

/** Resposta da intenção. [upload] ausente = a mídia já está CONFIRMADA (repetição). */
@Serializable
data class MediaIntentDto(val media: MediaDto, val upload: UploadInstructionsDto? = null)

@Serializable
data class MediaPartsInput(val partNumbers: List<Int>? = null)

@Serializable
data class MediaPartsDto(
    val parts: List<UploadPartDto> = emptyList(),
    val missingParts: List<Int> = emptyList(),
    val partSizeBytes: Long,
    val partCount: Int,
)

@Serializable
data class MediaDto(
    val id: String,
    val inspectionId: String,
    val itemId: String? = null,
    val groupId: String? = null,
    val headerField: String? = null,
    val taskId: String? = null,
    val contestationId: String? = null,
    val kind: MediaKind,
    val source: MediaSource,
    /** `true` só para captura pela CÂMERA do app. */
    val evidence: Boolean = false,
    val contentType: String = "",
    val sizeBytes: Long = 0,
    val sha256: String,
    val status: MediaStatus,
    val capturedAtDevice: String? = null,
    val receivedAtServer: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracyM: Double? = null,
    val deviceModel: String? = null,
    val durationMs: Int? = null,
    val hasThumb: Boolean = false,
    val uploadedBy: ActorKind = ActorKind.OWNER,
    val createdAt: String = "",
) {
    override fun toString(): String = "MediaDto(id=$id, kind=$kind, source=$source, status=$status, gps=${lat != null})"
}

/** URL curta de leitura (mídia ou PDF). É credencial: nunca em log. */
@Serializable
data class MediaUrlDto(val url: String, val expiresAt: String) {
    override fun toString(): String = "MediaUrlDto(expiresAt=$expiresAt)"
}

// ── Comparação ─────────────────────────────────────────────────────────────────────────────────────

@Serializable
data class ComparisonItemDto(
    val id: String,
    val exitItemId: String,
    val entryItemId: String? = null,
    val groupName: String,
    val itemName: String,
    val entryState: ItemState? = null,
    val exitState: ItemState,
    val exitResult: ItemResult? = null,
    /** Sugestão MECÂNICA. Quem decide é a pessoa. */
    val suggestedChanged: Boolean = false,
    val changed: Boolean? = null,
    val divergence: ItemDivergence? = null,
    val repairValue: String? = null,
    val referenceValue: String? = null,
    val note: String? = null,
    val decidedBy: String? = null,
    val decidedAt: String? = null,
)

@Serializable
data class ComparisonTotalsDto(val items: Int = 0, val decided: Int = 0, val pending: Int = 0, val changed: Int = 0, val repairTotal: String = "0.00")

@Serializable
data class ComparisonDto(
    val id: String,
    val entryId: String,
    val exitId: String,
    val generatedAt: String,
    val updatedAt: String,
    val items: List<ComparisonItemDto> = emptyList(),
    val totals: ComparisonTotalsDto = ComparisonTotalsDto(),
)

@Serializable
data class ExitProblemDto(
    val comparisonItemId: String,
    val exitItemId: String,
    val entryItemId: String? = null,
    val groupName: String,
    val itemName: String,
    val entryState: ItemState? = null,
    val exitState: ItemState,
    val entryFunctioning: ItemFunctioning? = null,
    val exitFunctioning: ItemFunctioning? = null,
    val entryQuantity: Int? = null,
    val exitQuantity: Int? = null,
    val exitResult: ItemResult? = null,
    val reasons: List<String> = emptyList(),
    val decided: Boolean = false,
    val divergence: ItemDivergence? = null,
    val repairValue: String? = null,
    val referenceValue: String? = null,
    val note: String? = null,
)

@Serializable
data class ExitProblemsDto(val entryId: String, val exitId: String, val items: List<ExitProblemDto> = emptyList(), val repairTotal: String = "0.00")

@Serializable
data class ComparisonDecisionInput(
    val changed: Boolean,
    val divergence: ItemDivergence? = null,
    val repairValue: String? = null,
    val note: String? = null,
)

// ── Voz → item ─────────────────────────────────────────────────────────────────────────────────────

@Serializable
data class VoiceInput(val transcript: String, val groupId: String? = null) {
    override fun toString(): String = "VoiceInput(length=${transcript.length}, groupId=$groupId)"
}

@Serializable
data class VoiceSuggestionDto(
    val groupId: String,
    val groupName: String,
    val itemId: String,
    val itemName: String,
    val state: ItemState? = null,
    val functioning: ItemFunctioning? = null,
    val observation: String? = null,
)

@Serializable
data class VoiceSuggestionsDto(val suggestions: List<VoiceSuggestionDto> = emptyList(), val provider: String = "")

// ── Partes, laudo e assinatura ─────────────────────────────────────────────────────────────────────

@Serializable
data class SignerInput(
    val role: SignerRole,
    val name: String,
    val document: String? = null,
    val email: String? = null,
    val phone: String? = null,
) {
    override fun toString(): String = "SignerInput(role=$role)"
}

@Serializable
data class SignerDto(
    val id: String,
    val role: SignerRole,
    val name: String,
    val document: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val createdAt: String = "",
) {
    override fun toString(): String = "SignerDto(id=$id, role=$role)"
}

@Serializable
data class SignatureDto(
    val id: String,
    val signerId: String,
    val role: SignerRole,
    val name: String,
    val method: SignatureMethod,
    val otpChannel: OtpChannelKind,
    val otpVerifiedAt: String? = null,
    val signedAt: String,
    val signedHash: String,
    val ip: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val hasTrace: Boolean = false,
) {
    override fun toString(): String = "SignatureDto(id=$id, signerId=$signerId, role=$role, method=$method)"
}

@Serializable
data class ReportVersionDto(
    val id: String,
    val version: Int,
    val kind: ReportKind,
    val contentVersionId: String? = null,
    val pdfSha256: String,
    val pdfSizeBytes: Long = 0,
    val contentSha256: String,
    val verifyCode: String,
    val verifyUrl: String,
    val locked: Boolean = false,
    val lockedAt: String? = null,
    val supersededAt: String? = null,
    val bilateralBasis: BilateralBasis? = null,
    val tenantSigned: Boolean? = null,
    val generatedAt: String,
    val signatures: List<SignatureDto> = emptyList(),
)

@Serializable
data class SealInput(val note: String? = null)

@Serializable
data class LinkIssueInput(val ttlHours: Int? = null, val secondFactor: String? = null, val sendEmail: Boolean = false)

@Serializable
data class ContestLinkInput(val deadlineHours: Int? = null, val secondFactor: String? = null)

/** Link emitido. [url] leva o TOKEN do link: compartilhe, nunca logue. */
@Serializable
data class LinkIssuedDto(val linkId: String, val url: String, val purpose: String, val expiresAt: String) {
    override fun toString(): String = "LinkIssuedDto(linkId=$linkId, purpose=$purpose, expiresAt=$expiresAt)"
}

@Serializable
data class LinkDto(
    val id: String,
    val purpose: String,
    val status: String,
    val expiresAt: String,
    val createdAt: String,
    val firstOpenedAt: String? = null,
    val answeredAt: String? = null,
    val revokedAt: String? = null,
)

// ── Contestação e convite ──────────────────────────────────────────────────────────────────────────

@Serializable
data class ContestationDto(
    val id: String,
    val itemId: String? = null,
    val groupName: String? = null,
    val itemName: String? = null,
    val text: String,
    val status: ContestationStatus,
    val deadlineAt: String,
    val response: String? = null,
    val decidedBy: String? = null,
    val decidedAt: String? = null,
    val createdAt: String,
    val media: List<MediaDto> = emptyList(),
) {
    override fun toString(): String = "ContestationDto(id=$id, status=$status)"
}

@Serializable
data class ContestationDecisionInput(val status: ContestationStatus, val response: String? = null)

@Serializable
data class InvitationInput(
    val channel: InvitationChannel,
    val recipient: String? = null,
    val sentAt: String? = null,
    val deadlineAt: String,
    val deliveredAt: String? = null,
    val deliveryProof: String? = null,
    val note: String? = null,
) {
    override fun toString(): String = "InvitationInput(channel=$channel, deadlineAt=$deadlineAt)"
}

@Serializable
data class InvitationUpdateInput(val deliveredAt: String? = null, val deliveryProof: String? = null, val attendance: Attendance? = null)

@Serializable
data class InvitationDto(
    val id: String,
    val entryInspectionId: String,
    val channel: InvitationChannel,
    val recipient: String? = null,
    val sentAt: String,
    val deliveredAt: String? = null,
    val deliveryProof: String? = null,
    val deadlineAt: String,
    val attendance: Attendance? = null,
    val note: String? = null,
    val expired: Boolean = false,
    val createdAt: String,
) {
    override fun toString(): String = "InvitationDto(id=$id, channel=$channel, expired=$expired)"
}

@Serializable
data class EventDto(
    val id: String,
    val type: String,
    val actorKind: ActorKind,
    val actor: String? = null,
    val device: String? = null,
    val occurredAtServer: String,
    val payload: JsonObject = JsonObject(emptyMap()),
) {
    override fun toString(): String = "EventDto(id=$id, type=$type, actorKind=$actorKind)"
}

/** Assinante na verificação pública do laudo (`GET /verify/{code}`). */
@Serializable
data class VerifySignerDto(
    val role: SignerRole,
    val name: String,
    val method: SignatureMethod,
    val otpChannel: OtpChannelKind,
    val signedAt: String,
)

/**
 * Resposta da verificação pública do laudo (backlib; campos da vistoria em si são ignorados). A marca do emissor
 * (0.204.0) é [issuerName]/[issuerColorHex] — ausentes = sem marca própria (mostrar o produto).
 */
@Serializable
data class VerifyDto(
    val authentic: Boolean,
    val code: String,
    val kind: ReportKind,
    val version: Int,
    val latestVersion: Int,
    val superseded: Boolean,
    val locked: Boolean,
    val pdfSha256: String,
    val contentSha256: String,
    val generatedAt: String,
    val bilateralBasis: BilateralBasis? = null,
    val tenantSigned: Boolean? = null,
    val signers: List<VerifySignerDto> = emptyList(),
    val matches: Boolean? = null,
    val pdfUrl: String? = null,
    val pdfUrlExpiresAt: String? = null,
    /** Nome de exibição de quem emitiu o laudo (0.204.0). */
    val issuerName: String? = null,
    /** Cor da marca do emissor, `#RRGGBB` (0.204.0). */
    val issuerColorHex: String? = null,
)
