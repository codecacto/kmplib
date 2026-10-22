package br.com.codecacto.kmplib.inspection.data

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InspectionApiSessionTest {

    private val id = InspectionFixtures.INSPECTION_ID

    @Test
    fun `rotas vao para o prefixo do produto com o modelo do aparelho e ids escapados`() = runTest {
        val s = FakeInspectionServer()
        s.on("GET", "/inspections/$id", Reply(body = InspectionFixtures.ENTRY_DRAFT))
        val api = s.api()
        val r = api.getInspection(id)
        assertIs<InspectionResult.Ok<InspectionDto>>(r)
        val req = s.received.single()
        assertEquals("/inspections/$id", req.path)
        assertEquals("Pixel 8", req.headers[InspectionApi.DEVICE_MODEL_HEADER])
        assertEquals("Bearer tok", req.headers["Authorization"])
        api.getObject("a/b")
        assertEquals("/objects/a%2Fb", s.received.last().path)
    }

    @Test
    fun `filtro de lista vira query so com o que foi informado`() = runTest {
        val s = FakeInspectionServer()
        s.on("GET", "/inspections", Reply(body = """{"items":[],"total":0,"page":2,"size":10}"""))
        val r = s.api().listInspections(InspectionListFilter(subjectId = "kit 03", status = InspectionStatus.DRAFT, page = 2, size = 10))
        assertEquals(2, r.getOrNull()?.page)
        assertEquals("subjectId=kit%2003&status=DRAFT&page=2&size=10", s.received.single().query)
    }

    @Test
    fun `finalizar incompleto devolve as pendencias tipadas`() = runTest {
        val s = FakeInspectionServer()
        s.on("POST", "/inspections/$id/finish", Reply(422, InspectionFixtures.INCOMPLETE_BODY))
        val e = s.api().finish(id).errorOrNull()
        assertIs<InspectionError.Incomplete>(e)
        assertEquals(3, e.issues.size)
    }

    @Test
    fun `basePath invalido e recusado`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { InspectionApiConfig(basePath = "v1/inspection") }
        kotlin.test.assertFailsWith<IllegalArgumentException> { InspectionApiConfig(basePath = "/v1/inspection/") }
    }

    // ── Sessão ───────────────────────────────────────────────────────────────────────────────────

    private fun TestScope.sessao(s: FakeInspectionServer, store: InspectionPatchStore = InMemoryInspectionPatchStore()) =
        InspectionSession(id, s.api(), queue = null, patches = store, scope = backgroundScope, clock = { 1_761_134_400_000 })

    @Test
    fun `mudanca sem rede fica pendente no disco e aparece na hora`() = runTest {
        val s = FakeInspectionServer()
        s.on("GET", "/inspections/$id", Reply(body = InspectionFixtures.ENTRY_DRAFT))
        s.on("PATCH", "/inspections/$id/items", Reply(offline = true))
        val store = InMemoryInspectionPatchStore()
        val sess = sessao(s, store)
        sess.load()
        val r = sess.updateItem(ItemPatch(InspectionFixtures.ITEM_PAREDE, state = ItemState.POOR))
        assertTrue(r.isOk, "guardada no aparelho conta como Ok")
        assertEquals(1, store.load(id).size)
        assertEquals(ItemState.POOR, sess.state.value.inspection!!.groups[0].items[1].state)
        assertEquals(ItemState.FAIR, sess.state.value.server!!.groups[0].items[1].state)
        assertIs<InspectionError.Offline>(sess.state.value.error)
    }

    @Test
    fun `pendencias de outra execucao sobem juntas num lote so`() = runTest {
        val s = FakeInspectionServer()
        s.on("GET", "/inspections/$id", Reply(body = InspectionFixtures.ENTRY_DRAFT))
        s.on("PATCH", "/inspections/$id/items", Reply(body = InspectionFixtures.ENTRY_DRAFT))
        val store = InMemoryInspectionPatchStore()
        store.save(id, listOf(ItemPatch(InspectionFixtures.ITEM_PAREDE, state = ItemState.POOR)))
        val sess = sessao(s, store)
        sess.updateItem(ItemPatch(InspectionFixtures.ITEM_PAREDE, observation = "trincada"))
        val enviado = s.receivedAt("PATCH", "/inspections/$id/items").single().body
        assertTrue("\"state\":\"POOR\"" in enviado && "\"observation\":\"trincada\"" in enviado, enviado)
        assertTrue(store.load(id).isEmpty())
        assertFalse(sess.state.value.hasPendingChanges)
    }

    @Test
    fun `recusa definitiva descarta o lote e 409 mantem`() = runTest {
        val s = FakeInspectionServer()
        s.on("GET", "/inspections/$id", Reply(body = InspectionFixtures.ENTRY_DRAFT))
        s.onSeq(
            "PATCH", "/inspections/$id/items",
            Reply(409, """{"message":"Reabra","code":"INSPECTION_NOT_EDITABLE"}"""),
            Reply(400, """{"message":"Leitura inválida","code":"METER_READING_INVALID"}"""),
        )
        val store = InMemoryInspectionPatchStore()
        val sess = sessao(s, store)
        val r1 = sess.updateItem(ItemPatch(InspectionFixtures.ITEM_PISO, state = ItemState.POOR))
        assertIs<InspectionError.Conflict>(r1.errorOrNull())
        assertEquals(1, store.load(id).size, "409: a pessoa reabre ou descarta")
        val r2 = sess.sync()
        assertIs<InspectionError.Validation>(r2.errorOrNull())
        assertTrue(store.load(id).isEmpty(), "400: nunca seria aceita")
        assertTrue(s.receivedAt("GET", "/inspections/$id").isNotEmpty(), "recarrega depois da recusa")
    }

    @Test
    fun `finalizar guarda as pendencias e sem rede nao finaliza`() = runTest {
        val s = FakeInspectionServer()
        s.on("GET", "/inspections/$id", Reply(body = InspectionFixtures.ENTRY_DRAFT))
        s.on("POST", "/inspections/$id/finish", Reply(422, InspectionFixtures.INCOMPLETE_BODY))
        val sess = sessao(s)
        sess.load()
        val r = sess.finish()
        assertIs<InspectionError.Incomplete>(r.errorOrNull())
        assertEquals(3, sess.state.value.issues.size)
        assertTrue("finishedAtDevice" in s.receivedAt("POST", "/inspections/$id/finish").single().body)
        // Mudar algo zera as pendências do servidor (vão ser recalculadas).
        s.on("PATCH", "/inspections/$id/items", Reply(offline = true))
        sess.updateItem(ItemPatch(InspectionFixtures.ITEM_PISO, state = ItemState.NEW))
        assertTrue(sess.state.value.issues.isEmpty())
        val semRede = sess.finish()
        assertIs<InspectionError.Offline>(semRede.errorOrNull())
        assertEquals(1, s.receivedAt("POST", "/inspections/$id/finish").size, "não chamou o finish sem enviar as mudanças")
    }

    @Test
    fun `finalizar com sucesso atualiza o status`() = runTest {
        val s = FakeInspectionServer()
        s.on("GET", "/inspections/$id", Reply(body = InspectionFixtures.ENTRY_DRAFT))
        s.on("POST", "/inspections/$id/finish", Reply(body = """{"status":"FINISHED_LOCAL","pendingMedia":1,"finishedAtServer":"2026-10-22T12:20:00Z"}"""))
        val sess = sessao(s)
        sess.load()
        assertIs<InspectionResult.Ok<FinishResultDto>>(sess.finish())
        assertEquals(InspectionStatus.FINISHED_LOCAL, sess.state.value.inspection!!.status)
        assertNull(sess.state.value.error)
    }

    @Test
    fun `midia confirmada entra no alvo e conta uma vez`() {
        val insp = InspectionFixtures.entryDraft()
        val nova = InspectionJson.decodeFromString(MediaDto.serializer(), InspectionFixtures.mediaConfirmed("m-nova", "ab".repeat(32), 10))
        val com = insp.withMedia(nova)
        assertEquals(2, com.groups[0].items[0].media.size)
        assertEquals(insp.mediaTotal + 1, com.mediaTotal)
        assertEquals(insp.mediaConfirmed + 1, com.mediaConfirmed)
        val denovo = com.withMedia(nova)
        assertEquals(com.mediaTotal, denovo.mediaTotal)
        assertEquals(com.mediaConfirmed, denovo.mediaConfirmed)
        val sem = denovo.withoutMedia("m-nova")
        assertEquals(insp.mediaTotal, sem.mediaTotal)
        assertEquals(1, sem.groups[0].items[0].media.size)
    }
}
