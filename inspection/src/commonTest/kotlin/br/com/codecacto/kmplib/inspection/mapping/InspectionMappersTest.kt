package br.com.codecacto.kmplib.inspection.mapping

import br.com.codecacto.kmplib.inspection.InspectionReviewTexts
import br.com.codecacto.kmplib.inspection.data.ComparisonItemDto
import br.com.codecacto.kmplib.inspection.data.FinishIssueDto
import br.com.codecacto.kmplib.inspection.data.FinishIssueReasons
import br.com.codecacto.kmplib.inspection.data.InspectionContext
import br.com.codecacto.kmplib.inspection.data.InspectionFixtures
import br.com.codecacto.kmplib.inspection.data.InspectionJson
import br.com.codecacto.kmplib.inspection.data.InspectionMediaTarget
import br.com.codecacto.kmplib.inspection.data.InspectionSessionState
import br.com.codecacto.kmplib.inspection.data.ItemDivergence
import br.com.codecacto.kmplib.inspection.data.ItemFunctioning
import br.com.codecacto.kmplib.inspection.data.ItemPatch
import br.com.codecacto.kmplib.inspection.data.ItemResult
import br.com.codecacto.kmplib.inspection.data.ItemState
import br.com.codecacto.kmplib.inspection.data.MediaKind
import br.com.codecacto.kmplib.inspection.data.MediaSource
import br.com.codecacto.kmplib.inspection.data.QueuedInspectionMedia
import br.com.codecacto.kmplib.inspection.data.TemplateDto
import br.com.codecacto.kmplib.inspection.data.VoiceSuggestionDto
import br.com.codecacto.kmplib.inspection.data.VoiceSuggestionsDto
import br.com.codecacto.kmplib.inspection.model.Divergence
import br.com.codecacto.kmplib.inspection.model.EditItemDraft
import br.com.codecacto.kmplib.inspection.model.ExitResult
import br.com.codecacto.kmplib.inspection.model.Functioning
import br.com.codecacto.kmplib.inspection.model.InspectionPhase
import br.com.codecacto.kmplib.inspection.model.ItemCondition
import br.com.codecacto.kmplib.inspection.model.MediaQueueBannerKind
import br.com.codecacto.kmplib.inspection.model.InspectionRules
import br.com.codecacto.kmplib.inspection.model.MediaUploadStatus
import br.com.codecacto.kmplib.inspection.model.ReportStatus
import br.com.codecacto.kmplib.inspection.model.TemplateQuestion
import br.com.codecacto.kmplib.inspection.model.TemplateQuestionType
import br.com.codecacto.kmplib.inspection.ui.template.TemplateStandardQuestions
import br.com.codecacto.kmplib.sync.direct.DirectUploadFailure
import br.com.codecacto.kmplib.sync.direct.DirectUploadStatus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InspectionMappersTest {

    private val photos = InspectionMappers // atalho
    private val none = InspectionPhotoResolver.None
    private val entrada = InspectionFixtures.entryDraft()
    private val saida = InspectionFixtures.exitSigned()

    private fun queued(
        sha: String,
        target: InspectionMediaTarget?,
        status: DirectUploadStatus = DirectUploadStatus.UPLOADING,
        source: MediaSource = MediaSource.CAMERA,
        wifiOnly: Boolean = false,
    ) = QueuedInspectionMedia(
        jobId = "job-$sha".take(20), localId = "loc", inspectionId = entrada.id, target = target, kind = MediaKind.PHOTO,
        source = source, sha256 = sha, blobId = "insp-x.jpg", status = status, progress = 0.5f,
        failure = if (status == DirectUploadStatus.FAILED) DirectUploadFailure(500) else null, wifiOnly = wifiOnly, sizeBytes = 10,
    )

    @Test
    fun `valores de tela e do fio vao e voltam`() {
        ItemCondition.entries.forEach { assertEquals(it, it.toItemState().toCondition()) }
        ExitResult.entries.forEach { assertEquals(it, it.toItemResult().toExitResult()) }
        Functioning.entries.forEach { assertEquals(it, it.toItemFunctioning().toFunctioning()) }
        Divergence.entries.forEach { assertEquals(it, it.toItemDivergence().toDivergence()) }
        assertNull(ItemState.UNKNOWN.toCondition())
        assertEquals(ItemCondition.REGULAR, ItemState.FAIR.toCondition())
        assertEquals(InspectionPhase.EXIT, InspectionContext.EXIT.toPhase())
        assertEquals(InspectionPhase.ENTRY, InspectionContext.OTHER.toPhase())
    }

    @Test
    fun `pendencias locais seguem a regra do finish do servidor`() {
        val issues = InspectionMappers.localIssues(entrada, emptyList())
        // Piso: 1 de 2 fotos; Hidrômetro: tarefa + leitura; Pia é N/A; Paredes sem mínimo.
        assertEquals(
            setOf(
                FinishIssueReasons.MIN_PHOTOS to InspectionFixtures.ITEM_PISO,
                FinishIssueReasons.MEDIA_TASK to InspectionFixtures.ITEM_HIDROMETRO,
                FinishIssueReasons.READING_REQUIRED to InspectionFixtures.ITEM_HIDROMETRO,
            ),
            issues.map { it.reason to it.itemId }.toSet(),
        )
        // Uma foto de câmera na fila conta (o servidor também conta registrada); galeria não.
        val comFila = InspectionMappers.localIssues(
            entrada,
            listOf(
                queued("a".repeat(64), InspectionMediaTarget.Item(InspectionFixtures.ITEM_PISO)),
                queued("b".repeat(64), InspectionMediaTarget.Item(InspectionFixtures.ITEM_HIDROMETRO, "leitura"), source = MediaSource.GALLERY),
            ),
        )
        assertFalse(comFila.any { it.reason == FinishIssueReasons.MIN_PHOTOS })
        assertTrue(comFila.any { it.reason == FinishIssueReasons.MEDIA_TASK })
    }

    @Test
    fun `lista de comodos com feitos e somente leitura fora do rascunho`() {
        val st = InspectionMappers.roomListState(InspectionSessionState(server = entrada), "Apto 302 · Entrada")
        assertEquals(listOf("Sala", "Cozinha"), st.rooms.map { it.name })
        assertEquals(1, st.rooms[0].done) // Paredes ok, Piso falta foto
        assertEquals(1, st.rooms[1].done) // Pia N/A ok, Hidrômetro pendente
        assertFalse(st.readOnly)
        assertTrue(InspectionMappers.roomListState(InspectionSessionState(server = saida), "x").readOnly)
        assertTrue(InspectionMappers.roomListState(InspectionSessionState(isLoading = true), "x").isLoading)
    }

    @Test
    fun `linhas de item na entrada e na saida`() {
        val l = InspectionMappers.itemListState(InspectionSessionState(server = entrada), InspectionFixtures.GROUP_COZINHA, none)!!
        val hidro = l.items[0]
        assertTrue(hidro.isGuidedTask)
        assertEquals(1, hidro.guidedTaskRequiredPhotos)
        assertEquals(ItemCondition.NOT_APPLICABLE, l.items[1].condition)
        assertEquals(1, l.items[1].photoCount, "anexo da galeria aparece como foto")

        val piso = InspectionMappers.itemRow(entrada.groups[0].items[0], InspectionPhase.ENTRY, emptyList())
        assertEquals(1, piso.missingRequiredPhotos)

        val s = InspectionMappers.itemRow(saida.groups[0].items[0], InspectionPhase.EXIT, emptyList())
        assertEquals(ItemCondition.GOOD, s.condition, "na saída a linha mostra o estado da ENTRADA")
        assertEquals(ExitResult.NON_CONFORMING, s.exitResult)
        assertTrue(s.changedSinceEntry)
        assertTrue(InspectionRules.matches(s, br.com.codecacto.kmplib.inspection.model.ItemFilter.CHANGED, InspectionPhase.EXIT))
    }

    @Test
    fun `miniaturas juntam servidor e fila sem repetir o mesmo hash`() {
        val piso = entrada.groups[0].items[0]
        val q1 = queued(InspectionFixtures.SHA_PISO_1, InspectionMediaTarget.Item(piso.id)) // já confirmada no servidor
        val q2 = queued("c".repeat(64), InspectionMediaTarget.Item(piso.id), DirectUploadStatus.FAILED)
        val t = InspectionMappers.mediaThumbs(piso.media, listOf(q1, q2), none, editable = true)
        assertEquals(2, t.size)
        assertEquals(MediaUploadStatus.CONFIRMED, t[0].status)
        assertEquals(MediaUploadStatus.FAILED, t[1].status)
        assertEquals(q2.jobId, t[1].id, "o id da miniatura da fila é o do envio (Tentar de novo)")
        val enviando = InspectionMappers.mediaThumbs(emptyList(), listOf(queued("d".repeat(64), null)), none, true).single()
        assertEquals(MediaUploadStatus.UPLOADING, enviando.status)
        assertEquals(0.5f, enviando.uploadProgress)
    }

    @Test
    fun `folha do item com otimista pendente e somente leitura`() {
        val st = InspectionSessionState(server = entrada, pending = listOf(ItemPatch(InspectionFixtures.ITEM_PAREDE, state = ItemState.POOR)))
        val f = InspectionMappers.itemSheetState(st, InspectionFixtures.ITEM_PAREDE, none)!!
        assertEquals(ItemCondition.BAD, f.condition)
        assertEquals(2, f.position)
        assertEquals(2, f.total)
        assertTrue(f.asksFunctioning, "já tem resposta de funcionamento")
        assertEquals(Functioning.NOT_TESTED, f.functioning)
        assertTrue(f.hasUnsavedChanges)
        assertEquals("Mancha perto da janela", f.note)
        val hidro = InspectionMappers.itemSheetState(InspectionSessionState(server = entrada), InspectionFixtures.ITEM_HIDROMETRO, none)!!
        assertEquals(1, hidro.minPhotos, "tarefa de foto obrigatória conta como mínimo")
        assertNull(InspectionMappers.itemSheetState(InspectionSessionState(server = entrada), "nao-existe", none))
    }

    @Test
    fun `comparacao de saida com a foto da entrada e a divergencia da comparacao`() {
        val ci = ComparisonItemDto(
            id = "c1", exitItemId = InspectionFixtures.EXIT_ITEM_PISO, groupName = "Sala", itemName = "Piso",
            exitState = ItemState.POOR, divergence = ItemDivergence.DAMAGE,
        )
        val c = InspectionMappers.exitCompareState(InspectionSessionState(server = saida), InspectionFixtures.EXIT_ITEM_PISO, none, ci)!!
        assertEquals(ItemCondition.GOOD, c.entry.condition)
        assertNotNull(c.entry.photo)
        assertEquals(ExitResult.NON_CONFORMING, c.result)
        assertEquals(Divergence.DAMAGE, c.divergence)
        assertTrue(c.readOnly)
        assertFalse(c.divergenceMissing)
    }

    @Test
    fun `revisao com pendencias do servidor rotuladas e contagens`() {
        val issues = listOf(
            FinishIssueDto(InspectionFixtures.ITEM_PISO, "Sala", "Piso", reason = FinishIssueReasons.MIN_PHOTOS, required = 3, have = 1),
            FinishIssueDto(InspectionFixtures.ITEM_HIDROMETRO, "Cozinha", "Hidrômetro", reason = FinishIssueReasons.READING_REQUIRED, taskId = "leitura"),
            FinishIssueDto(headerField = "medidor", reason = FinishIssueReasons.HEADER_REQUIRED),
        )
        val r = InspectionMappers.reviewState(InspectionSessionState(server = entrada, issues = issues), "Entrada · Apto 302", InspectionReviewTexts())!!
        assertEquals(
            listOf("Sala · Piso · faltam 2 fotos", "Cozinha · Hidrômetro · falta a leitura: Foto do visor com a leitura", "Dados da vistoria · medidor"),
            r.pendencies.map { it.label },
        )
        assertEquals(InspectionFixtures.GROUP_SALA, r.pendencies[0].roomId)
        assertFalse(r.canFinish)
        assertEquals(2, r.counts.rooms)
        assertEquals(4, r.counts.items)
        assertEquals(2, r.counts.photos)
        assertEquals(1, r.conditionCounts[ItemCondition.REGULAR])
        assertEquals(listOf("Paredes"), r.problems.map { it.name })
        assertEquals(5, r.conditionCounts.size, "todos os estados presentes na legenda")
    }

    @Test
    fun `laudo gerando sem versao e pronto com prova da vigente`() {
        val g = InspectionMappers.reportViewerState(entrada, "Laudo", dateLabel = { it.toString() })
        val gerando = assertIs<ReportStatus.Generating>(g.status)
        assertEquals(1, gerando.uploaded)
        assertEquals(2, gerando.total)

        val p = InspectionMappers.reportViewerState(saida, "Laudo · Apto 302", dateLabel = { "D:$it" })
        assertEquals(ReportStatus.Ready, p.status)
        assertEquals("cccc", p.proof?.hash)
        assertEquals(1, p.proof?.signedCount)
        assertEquals("D:2026-10-22T13:58:00Z", p.proof?.signedAtLabel)
        assertEquals("−15,6000 −56,1000", p.proof?.locationLabel)
        assertEquals(listOf("v2", "v1"), p.versions.map { it.label })
        assertTrue(p.versions[0].isCurrent)
        assertFalse(p.isOutdated)
        assertTrue(InspectionMappers.reportViewerState(saida, "x", { "" }, selectedId = "r1").isOutdated)
    }

    @Test
    fun `fila resume servidor mais o que so esta no aparelho`() {
        val st = InspectionSessionState(
            server = entrada,
            queued = listOf(
                queued(InspectionFixtures.SHA_PISO_1, InspectionMediaTarget.Item(InspectionFixtures.ITEM_PISO)), // já conhecida
                queued("e".repeat(64), InspectionMediaTarget.Item(InspectionFixtures.ITEM_PISO), DirectUploadStatus.FAILED),
                queued("f".repeat(64), InspectionMediaTarget.Group(InspectionFixtures.GROUP_SALA), wifiOnly = true),
            ),
        )
        val sum = InspectionMappers.mediaQueueSummary(st)
        assertEquals(4, sum.total)
        assertEquals(1, sum.confirmed)
        assertEquals(1, sum.failed)
        assertEquals(1, sum.waitingWifi)
        assertEquals(MediaQueueBannerKind.FAILED, InspectionRules.queueBannerKind(sum))
        val tela = InspectionMappers.mediaQueueScreenState(st, none)
        assertEquals(listOf("Sala · Piso"), tela.failed.map { it.label })
        assertEquals(listOf("Sala · Piso", "Sala"), tela.pending.map { it.label })
        assertEquals(1, tela.confirmed.size)
    }

    @Test
    fun `voz propoe so o que existe e as aceitas viram patch`() {
        val sug = VoiceSuggestionsDto(
            listOf(
                VoiceSuggestionDto("g", "Sala", InspectionFixtures.ITEM_PAREDE, "Paredes", ItemState.POOR, ItemFunctioning.UNKNOWN, "trincada"),
                VoiceSuggestionDto("g", "Sala", "nao-existe", "Teto", ItemState.GOOD),
            ),
            provider = "gemini",
        )
        val v = InspectionMappers.voiceProposals(InspectionSessionState(server = entrada), "a parede está trincada", sug)
        assertTrue(v.proposals[0].matched)
        assertFalse(v.proposals[1].matched)
        assertNull(v.proposals[1].itemName)
        val patches = InspectionTemplateMapping.voicePatches(sug, v)
        assertEquals(1, patches.size)
        assertEquals(ItemState.POOR, patches[0].state)
        assertNull(patches[0].functioning, "UNKNOWN nunca é enviado")
        assertEquals("trincada", patches[0].observation)
    }

    @Test
    fun `modelo le perguntas da weblib e escreve singleChoice com slug`() {
        val tpl = InspectionJson.decodeFromString(
            TemplateDto.serializer(),
            """{"id":"t","subjectType":"PROPERTY","name":"Apto","origin":"SEED","version":1,"resultAxis":"CONFORMITY","updatedAt":"x",
               "groups":[{"id":"g1","name":"Sala","position":0,"items":[{"id":"i1","name":"Piso","position":0,"minPhotos":1,"defaultState":"GOOD",
               "fieldSchema":{"schemaVersion":1,"id":"item.i1","title":"Piso","sections":[{"id":"campos","title":"Piso","kind":"fields","questions":[
                 {"id":"color","type":"text","text":"Cor","required":false},
                 {"id":"q9","type":"singleChoice","text":"Tipo","required":true,"options":[{"value":"ceramica","label":"Cerâmica"},{"value":"madeira","label":"Madeira"}]}
               ]}]}}]}]}""",
        )
        val ed = InspectionTemplateMapping.templateEditorState(tpl)
        assertTrue(ed.isReadyMade)
        val q = ed.items.single().questions
        assertEquals(TemplateStandardQuestions.COLOR, q[0].standardKey)
        assertEquals(TemplateQuestionType.CHOICE, q[1].type)
        assertEquals(listOf("Cerâmica", "Madeira"), q[1].options)

        val input = InspectionTemplateMapping.itemInput(
            EditItemDraft(
                itemId = "i1", name = "Piso", minPhotos = 2, defaultCondition = ItemCondition.NEW,
                standardQuestions = setOf(TemplateStandardQuestions.QUANTITY, TemplateStandardQuestions.COLOR),
                customQuestions = listOf(TemplateQuestion("q9", "Tipo", TemplateQuestionType.CHOICE, options = listOf("Cerâmica", "ceramica", "Madeira"))),
            ),
            labelOfStandard = { it.uppercase() },
        )
        assertEquals(ItemState.NEW, input.defaultState)
        val perguntas = (input.fieldSchema as JsonObject)["sections"]!!.jsonArray[0].jsonObject["questions"] as JsonArray
        assertEquals(listOf("color", "quantity", "q9"), perguntas.map { (it.jsonObject["id"] as JsonPrimitive).content })
        val escolha = perguntas[2].jsonObject
        assertEquals("singleChoice", (escolha["type"] as JsonPrimitive).content)
        assertEquals(
            listOf("ceramica", "ceramica-2", "madeira"),
            escolha["options"]!!.jsonArray.map { (it.jsonObject["value"] as JsonPrimitive).content },
        )
        assertNull(InspectionTemplateMapping.itemFormSchema("x", "x", emptyList()))
    }
}
