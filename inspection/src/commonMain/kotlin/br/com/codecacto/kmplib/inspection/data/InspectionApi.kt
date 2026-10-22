package br.com.codecacto.kmplib.inspection.data

import br.com.codecacto.kmplib.inspection.capture.inspectionDeviceModel
import br.com.codecacto.kmplib.sync.rest.DomainApiClient
import br.com.codecacto.kmplib.sync.rest.DomainResult
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement

/**
 * Onde o motor está montado e como se identificar.
 *
 * @param basePath prefixo das rotas do DONO **depois** da base URL do [DomainApiClient]. O motor é montado
 *   DENTRO do módulo de cada produto no apps-api, então o prefixo é do produto:
 *   `"/minhavistoria/v1/inspection"`, `"/meusalugueis/v1/inspection"`. Default: o do `inspectionRoutes`.
 * @param deviceModel vai em `X-Device-Model` nas chamadas (a trilha do servidor grava o aparelho de cada
 *   ação). `null` = não manda.
 */
data class InspectionApiConfig(
    val basePath: String = DEFAULT_BASE_PATH,
    val deviceModel: String? = inspectionDeviceModel(),
) {
    init {
        require(basePath.startsWith("/") && !basePath.endsWith("/")) { "basePath deve começar com '/' e não terminar com '/'" }
    }

    companion object {
        /** O `basePath` default do `inspectionRoutes` da backlib. */
        const val DEFAULT_BASE_PATH: String = "/v1/inspection"
    }
}

/** Filtro de `GET /inspections`. */
data class InspectionListFilter(
    val objectId: String? = null,
    val subjectType: String? = null,
    val subjectId: String? = null,
    val status: InspectionStatus? = null,
    val context: InspectionContext? = null,
    val page: Int = 1,
    val size: Int = 20,
)

/**
 * **Cliente das rotas do DONO do `backlib-inspection`** (contrato v1, backlib ≥ 0.203.0), sobre o
 * [DomainApiClient] da kmplib — Bearer, renovação no 401, 402 → cota e o log de requisição vêm de lá; o
 * cliente sobe um nível e devolve [InspectionResult] com o erro TIPADO ([InspectionError]).
 *
 * ```kotlin
 * val api = InspectionApi(
 *     client = DomainApiClient(createHttpClient(), auth.asDomainTokenProvider(), "https://apps-api.codecacto.com.br"),
 *     config = InspectionApiConfig(basePath = "/minhavistoria/v1/inspection"),
 * )
 * when (val r = api.getInspection(id)) {
 *     is InspectionResult.Ok -> mostrar(r.value)
 *     is InspectionResult.Failed -> when (val e = r.error) {
 *         is InspectionError.QuotaExceeded -> abrirPaywall(e.quota)
 *         else -> mostrarErro(e.userMessage)
 *     }
 * }
 * ```
 *
 * O ENVIO de arquivo NÃO é por aqui: a mídia vai direto ao storage pela [InspectionMediaQueue] (que usa
 * [mediaIntent]/[mediaParts]/[completeMedia]). O código e o traço da assinatura vão pelo `SignaturePort`.
 * Ids nos caminhos são escapados; nada de URL assinada ou token passa em log.
 */
class InspectionApi(
    private val client: DomainApiClient,
    val config: InspectionApiConfig = InspectionApiConfig(),
) {
    private val json = InspectionJson
    private val base = config.basePath
    private val headers: Map<String, String> =
        config.deviceModel?.trim()?.take(120)?.takeIf { it.isNotEmpty() && it.all { c -> c.code in 0x20..0x7E } }
            ?.let { mapOf(DEVICE_MODEL_HEADER to it) } ?: emptyMap()

    // ── Objetos ──────────────────────────────────────────────────────────────────────────────────

    suspend fun listObjects(
        query: String? = null,
        subjectType: String? = null,
        page: Int = 1,
        size: Int = 20,
        archived: Boolean = false,
    ): InspectionResult<PageDto<ObjectDto>> =
        get(
            "/objects" + query("q" to query, "subjectType" to subjectType, "page" to page, "size" to size, "archived" to archived.takeIf { it }),
            PageDto.serializer(ObjectDto.serializer()),
        )

    suspend fun createObject(input: ObjectInput): InspectionResult<ObjectDto> =
        post("/objects", ObjectInput.serializer(), input, ObjectDto.serializer())

    /** Cria ou atualiza o objeto do sujeito do PRODUTO (`subjectId` obrigatório) — idempotente. */
    suspend fun upsertObjectBySubject(input: ObjectInput): InspectionResult<ObjectDto> {
        require(!input.subjectId.isNullOrBlank()) { "upsert exige subjectId" }
        return put("/objects/by-subject", ObjectInput.serializer(), input, ObjectDto.serializer())
    }

    suspend fun getObject(id: String): InspectionResult<ObjectDto> = get("/objects/${seg(id)}", ObjectDto.serializer())

    suspend fun updateObject(id: String, input: ObjectInput): InspectionResult<ObjectDto> =
        put("/objects/${seg(id)}", ObjectInput.serializer(), input, ObjectDto.serializer())

    /** Com vistoria, o servidor ARQUIVA em vez de apagar. */
    suspend fun deleteObject(id: String): InspectionResult<Unit> = delete("/objects/${seg(id)}")

    // ── Modelos e blocos ─────────────────────────────────────────────────────────────────────────

    /** Semeia os modelos prontos na 1ª leitura. */
    suspend fun listTemplates(subjectType: String? = null): InspectionResult<List<TemplateSummaryDto>> =
        get("/templates" + query("subjectType" to subjectType), ListSerializer(TemplateSummaryDto.serializer()))

    suspend fun createTemplate(input: TemplateInput): InspectionResult<TemplateDto> =
        post("/templates", TemplateInput.serializer(), input, TemplateDto.serializer())

    suspend fun getTemplate(id: String): InspectionResult<TemplateDto> = get("/templates/${seg(id)}", TemplateDto.serializer())

    suspend fun updateTemplate(id: String, input: TemplateInput): InspectionResult<TemplateDto> =
        put("/templates/${seg(id)}", TemplateInput.serializer(), input, TemplateDto.serializer())

    suspend fun deleteTemplate(id: String): InspectionResult<Unit> = delete("/templates/${seg(id)}")

    /** "Copiar e editar" um modelo pronto. */
    suspend fun duplicateTemplate(id: String, name: String? = null): InspectionResult<TemplateDto> =
        post("/templates/${seg(id)}/duplicate", TemplateDuplicateInput.serializer(), TemplateDuplicateInput(name), TemplateDto.serializer())

    suspend fun addTemplateGroup(templateId: String, input: TemplateGroupInput): InspectionResult<TemplateDto> =
        post("/templates/${seg(templateId)}/groups", TemplateGroupInput.serializer(), input, TemplateDto.serializer())

    suspend fun updateTemplateGroup(templateId: String, groupId: String, input: TemplateGroupUpdateInput): InspectionResult<TemplateDto> =
        patch("/templates/${seg(templateId)}/groups/${seg(groupId)}", TemplateGroupUpdateInput.serializer(), input, TemplateDto.serializer())

    suspend fun deleteTemplateGroup(templateId: String, groupId: String): InspectionResult<TemplateDto> =
        deleteFor("/templates/${seg(templateId)}/groups/${seg(groupId)}", TemplateDto.serializer())

    suspend fun reorderTemplateGroups(templateId: String, ids: List<String>): InspectionResult<TemplateDto> =
        put("/templates/${seg(templateId)}/groups-order", OrderInput.serializer(), OrderInput(ids), TemplateDto.serializer())

    suspend fun addTemplateItem(templateId: String, groupId: String, input: TemplateItemInput): InspectionResult<TemplateDto> =
        post("/templates/${seg(templateId)}/groups/${seg(groupId)}/items", TemplateItemInput.serializer(), input, TemplateDto.serializer())

    suspend fun updateTemplateItem(templateId: String, itemId: String, input: TemplateItemInput): InspectionResult<TemplateDto> =
        put("/templates/${seg(templateId)}/items/${seg(itemId)}", TemplateItemInput.serializer(), input, TemplateDto.serializer())

    suspend fun deleteTemplateItem(templateId: String, itemId: String): InspectionResult<TemplateDto> =
        deleteFor("/templates/${seg(templateId)}/items/${seg(itemId)}", TemplateDto.serializer())

    suspend fun reorderTemplateItems(templateId: String, groupId: String, ids: List<String>): InspectionResult<TemplateDto> =
        put("/templates/${seg(templateId)}/groups/${seg(groupId)}/items-order", OrderInput.serializer(), OrderInput(ids), TemplateDto.serializer())

    suspend fun listBlocks(subjectType: String? = null): InspectionResult<List<BlockDto>> =
        get("/blocks" + query("subjectType" to subjectType), ListSerializer(BlockDto.serializer()))

    suspend fun createBlock(input: BlockInput): InspectionResult<BlockDto> = post("/blocks", BlockInput.serializer(), input, BlockDto.serializer())

    suspend fun updateBlock(id: String, input: BlockInput): InspectionResult<BlockDto> =
        put("/blocks/${seg(id)}", BlockInput.serializer(), input, BlockDto.serializer())

    suspend fun deleteBlock(id: String): InspectionResult<Unit> = delete("/blocks/${seg(id)}")

    // ── Vistorias ────────────────────────────────────────────────────────────────────────────────

    suspend fun listInspections(filter: InspectionListFilter = InspectionListFilter()): InspectionResult<PageDto<InspectionSummaryDto>> =
        get(
            "/inspections" + query(
                "objectId" to filter.objectId, "subjectType" to filter.subjectType, "subjectId" to filter.subjectId,
                "status" to filter.status?.takeIf { it != InspectionStatus.UNKNOWN }?.name,
                "context" to filter.context?.takeIf { it != InspectionContext.UNKNOWN }?.name,
                "page" to filter.page, "size" to filter.size,
            ),
            PageDto.serializer(InspectionSummaryDto.serializer()),
        )

    /** Idempotente pelo `clientId` (gere-o no aparelho e repita o mesmo). 402 = cota. */
    suspend fun createInspection(input: CreateInspectionInput): InspectionResult<InspectionDto> =
        post("/inspections", CreateInspectionInput.serializer(), input, InspectionDto.serializer())

    suspend fun getInspection(id: String): InspectionResult<InspectionDto> = get("/inspections/${seg(id)}", InspectionDto.serializer())

    /** A SAÍDA nasce da entrada finalizada (`ENTRY_NOT_FINISHED`/`EXIT_FROM_ENTRY_ONLY` = 409/422). */
    suspend fun createExit(entryId: String, input: CreateExitInput): InspectionResult<InspectionDto> =
        post("/inspections/${seg(entryId)}/exit", CreateExitInput.serializer(), input, InspectionDto.serializer())

    suspend fun events(id: String): InspectionResult<List<EventDto>> =
        get("/inspections/${seg(id)}/events", ListSerializer(EventDto.serializer()))

    suspend fun updateHeader(id: String, answers: Map<String, JsonElement>): InspectionResult<InspectionDto> =
        put("/inspections/${seg(id)}/header", HeaderInput.serializer(), HeaderInput(answers), InspectionDto.serializer())

    suspend fun addGroup(id: String, input: GroupAddInput): InspectionResult<InspectionDto> =
        post("/inspections/${seg(id)}/groups", GroupAddInput.serializer(), input, InspectionDto.serializer())

    suspend fun duplicateGroup(id: String, groupId: String, name: String? = null): InspectionResult<InspectionDto> =
        post("/inspections/${seg(id)}/groups/${seg(groupId)}/duplicate", GroupDuplicateInput.serializer(), GroupDuplicateInput(name), InspectionDto.serializer())

    suspend fun renameGroup(id: String, groupId: String, name: String): InspectionResult<InspectionDto> =
        patch("/inspections/${seg(id)}/groups/${seg(groupId)}", GroupRenameInput.serializer(), GroupRenameInput(name), InspectionDto.serializer())

    suspend fun deleteGroup(id: String, groupId: String): InspectionResult<InspectionDto> =
        deleteFor("/inspections/${seg(id)}/groups/${seg(groupId)}", InspectionDto.serializer())

    /** Lote de itens (≤ [MAX_ITEMS_PER_PATCH]). Devolve a vistoria inteira já com o lote aplicado. */
    suspend fun patchItems(id: String, items: List<ItemPatch>): InspectionResult<InspectionDto> {
        require(items.size <= MAX_ITEMS_PER_PATCH) { "no máximo $MAX_ITEMS_PER_PATCH itens por lote" }
        return patch("/inspections/${seg(id)}/items", ItemsPatchInput.serializer(), ItemsPatchInput(items), InspectionDto.serializer())
    }

    /** Finaliza. 422 = [InspectionError.Incomplete] com as pendências. Idempotente. */
    suspend fun finish(id: String, finishedAtDevice: String? = null): InspectionResult<FinishResultDto> =
        post("/inspections/${seg(id)}/finish", FinishInput.serializer(), FinishInput(finishedAtDevice), FinishResultDto.serializer())

    /** Volta a rascunho; a versão travada do laudo continua verificável, a correção vira versão nova. */
    suspend fun reopen(id: String): InspectionResult<InspectionDto> = postEmpty("/inspections/${seg(id)}/reopen", InspectionDto.serializer())

    /** Voz → PROPOSTAS (a IA nunca decide; a pessoa confirma). 503 = IA indisponível. */
    suspend fun voice(id: String, transcript: String, groupId: String? = null): InspectionResult<VoiceSuggestionsDto> =
        post("/inspections/${seg(id)}/voice", VoiceInput.serializer(), VoiceInput(transcript, groupId), VoiceSuggestionsDto.serializer())

    // ── Mídia (a fila usa; a tela normalmente não) ──────────────────────────────────────────────

    /** Registra a intenção (idempotente pelo `sha256`) e devolve as instruções de envio. */
    suspend fun mediaIntent(inspectionId: String, input: MediaIntentInput): InspectionResult<MediaIntentDto> =
        post("/inspections/${seg(inspectionId)}/media", MediaIntentInput.serializer(), input, MediaIntentDto.serializer())

    /** URLs novas das partes (retomada). `null` = só as que faltam no storage. */
    suspend fun mediaParts(mediaId: String, partNumbers: List<Int>? = null): InspectionResult<MediaPartsDto> =
        post("/media/${seg(mediaId)}/parts", MediaPartsInput.serializer(), MediaPartsInput(partNumbers), MediaPartsDto.serializer())

    /** Fecha o envio: o servidor confere o SHA-256 (`MEDIA_HASH_MISMATCH` = 422; `MEDIA_NOT_UPLOADED` = 409). */
    suspend fun completeMedia(mediaId: String): InspectionResult<MediaDto> = postEmpty("/media/${seg(mediaId)}/complete", MediaDto.serializer())

    /** URL curta de leitura (só mídia CONFIRMADA). É credencial: não logue nem guarde. */
    suspend fun mediaUrl(mediaId: String, thumb: Boolean = false): InspectionResult<MediaUrlDto> =
        get("/media/${seg(mediaId)}/url" + query("thumb" to thumb.takeIf { it }), MediaUrlDto.serializer())

    /** Só em rascunho; prova de laudo assinado = 409 `MEDIA_IN_SIGNED_REPORT`. */
    suspend fun deleteMedia(mediaId: String): InspectionResult<Unit> = delete("/media/${seg(mediaId)}")

    // ── Comparação ───────────────────────────────────────────────────────────────────────────────

    suspend fun comparison(exitId: String): InspectionResult<ComparisonDto> =
        get("/inspections/${seg(exitId)}/comparison", ComparisonDto.serializer())

    suspend fun decideComparison(exitId: String, comparisonItemId: String, input: ComparisonDecisionInput): InspectionResult<ComparisonDto> =
        put("/inspections/${seg(exitId)}/comparison/items/${seg(comparisonItemId)}", ComparisonDecisionInput.serializer(), input, ComparisonDto.serializer())

    suspend fun exitProblems(exitId: String, includeNaturalWear: Boolean = false): InspectionResult<ExitProblemsDto> =
        get("/inspections/${seg(exitId)}/comparison/problems" + query("includeNaturalWear" to includeNaturalWear.takeIf { it }), ExitProblemsDto.serializer())

    // ── Convite de saída ─────────────────────────────────────────────────────────────────────────

    suspend fun listInvitations(entryId: String): InspectionResult<List<InvitationDto>> =
        get("/inspections/${seg(entryId)}/invitations", ListSerializer(InvitationDto.serializer()))

    suspend fun registerInvitation(entryId: String, input: InvitationInput): InspectionResult<InvitationDto> =
        post("/inspections/${seg(entryId)}/invitations", InvitationInput.serializer(), input, InvitationDto.serializer())

    suspend fun updateInvitation(invitationId: String, input: InvitationUpdateInput): InspectionResult<InvitationDto> =
        patch("/invitations/${seg(invitationId)}", InvitationUpdateInput.serializer(), input, InvitationDto.serializer())

    // ── Partes e laudo ───────────────────────────────────────────────────────────────────────────

    suspend fun addSigner(id: String, input: SignerInput): InspectionResult<SignerDto> =
        post("/inspections/${seg(id)}/signers", SignerInput.serializer(), input, SignerDto.serializer())

    /** Quem já assinou não sai (409 `SIGNER_HAS_SIGNED`). */
    suspend fun removeSigner(id: String, signerId: String): InspectionResult<Unit> = delete("/inspections/${seg(id)}/signers/${seg(signerId)}")

    suspend fun listReports(id: String): InspectionResult<List<ReportVersionDto>> =
        get("/inspections/${seg(id)}/reports", ListSerializer(ReportVersionDto.serializer()))

    /** Gera a versão de conteúdo (exige mídia toda confirmada; 503 sem gerador de PDF no servidor). */
    suspend fun generateReport(id: String): InspectionResult<ReportVersionDto> = postEmpty("/inspections/${seg(id)}/reports", ReportVersionDto.serializer())

    suspend fun sealReport(id: String, note: String? = null): InspectionResult<ReportVersionDto> =
        post("/inspections/${seg(id)}/reports/seal", SealInput.serializer(), SealInput(note), ReportVersionDto.serializer())

    /** URL curta do PDF da versão. Credencial: não logue. */
    suspend fun reportUrl(reportId: String): InspectionResult<MediaUrlDto> = get("/reports/${seg(reportId)}/url", MediaUrlDto.serializer())

    // ── Links públicos ───────────────────────────────────────────────────────────────────────────

    /** Link para a parte assinar no aparelho dela. A URL leva o token do link — compartilhe, não logue. */
    suspend fun issueSignLink(id: String, signerId: String, input: LinkIssueInput = LinkIssueInput()): InspectionResult<LinkIssuedDto> =
        post("/inspections/${seg(id)}/signers/${seg(signerId)}/sign-link", LinkIssueInput.serializer(), input, LinkIssuedDto.serializer())

    suspend fun issueAutoInspectionLink(id: String, input: LinkIssueInput = LinkIssueInput()): InspectionResult<LinkIssuedDto> =
        post("/inspections/${seg(id)}/auto-inspection-link", LinkIssueInput.serializer(), input, LinkIssuedDto.serializer())

    suspend fun issueContestLink(id: String, input: ContestLinkInput = ContestLinkInput()): InspectionResult<LinkIssuedDto> =
        post("/inspections/${seg(id)}/contest-link", ContestLinkInput.serializer(), input, LinkIssuedDto.serializer())

    suspend fun listLinks(id: String): InspectionResult<List<LinkDto>> = get("/inspections/${seg(id)}/links", ListSerializer(LinkDto.serializer()))

    suspend fun revokeLink(linkId: String): InspectionResult<Unit> = delete("/links/${seg(linkId)}")

    // ── Contestação ──────────────────────────────────────────────────────────────────────────────

    suspend fun listContestations(id: String): InspectionResult<List<ContestationDto>> =
        get("/inspections/${seg(id)}/contestations", ListSerializer(ContestationDto.serializer()))

    suspend fun decideContestation(contestationId: String, input: ContestationDecisionInput): InspectionResult<ContestationDto> =
        put("/contestations/${seg(contestationId)}", ContestationDecisionInput.serializer(), input, ContestationDto.serializer())

    // ── Transporte ───────────────────────────────────────────────────────────────────────────────

    internal suspend fun <T> get(path: String, out: KSerializer<T>): InspectionResult<T> =
        client.getJson(base + path, headers).toInspectionResult { json.decodeFromString(out, it) }

    internal suspend fun <I, T> post(path: String, inSer: KSerializer<I>, body: I, out: KSerializer<T>): InspectionResult<T> =
        client.postJson(base + path, json.encodeToString(inSer, body), headers).toInspectionResult { json.decodeFromString(out, it) }

    internal suspend fun <T> postEmpty(path: String, out: KSerializer<T>): InspectionResult<T> =
        client.postJson(base + path, "{}", headers).toInspectionResult { json.decodeFromString(out, it) }

    private suspend fun <I, T> put(path: String, inSer: KSerializer<I>, body: I, out: KSerializer<T>): InspectionResult<T> =
        client.putJson(base + path, json.encodeToString(inSer, body), headers).toInspectionResult { json.decodeFromString(out, it) }

    private suspend fun <I, T> patch(path: String, inSer: KSerializer<I>, body: I, out: KSerializer<T>): InspectionResult<T> =
        client.patchJson(base + path, json.encodeToString(inSer, body), headers).toInspectionResult { json.decodeFromString(out, it) }

    private suspend fun delete(path: String): InspectionResult<Unit> =
        when (val r = client.delete(base + path, headers)) {
            is DomainResult.Success -> InspectionResult.Ok(Unit)
            is DomainResult.Quota -> InspectionResult.Failed(InspectionError.QuotaExceeded(r))
            is DomainResult.Error -> InspectionResult.Failed(r.toInspectionError())
        }

    private suspend fun <T> deleteFor(path: String, out: KSerializer<T>): InspectionResult<T> =
        client.deleteJson(base + path, headers).toInspectionResult { json.decodeFromString(out, it) }

    companion object {
        /** Teto do lote de `PATCH …/items` no servidor. */
        const val MAX_ITEMS_PER_PATCH: Int = 500

        /** Cabeçalho com o modelo do aparelho (trilha). */
        const val DEVICE_MODEL_HEADER: String = "X-Device-Model"

        /** Escapa um id para segmento de caminho (o id vem do servidor/aparelho; não pode quebrar a rota). */
        internal fun seg(id: String): String {
            require(id.isNotBlank()) { "id vazio" }
            return id.encodeURLPathPart().replace("/", "%2F")
        }

        /** `?a=1&b=x` só com os parâmetros não nulos/não vazios. */
        internal fun query(vararg params: Pair<String, Any?>): String {
            val partes = params.mapNotNull { (k, v) ->
                val texto = v?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                "$k=${texto.encodeURLParameter()}"
            }
            return if (partes.isEmpty()) "" else "?" + partes.joinToString("&")
        }
    }
}
