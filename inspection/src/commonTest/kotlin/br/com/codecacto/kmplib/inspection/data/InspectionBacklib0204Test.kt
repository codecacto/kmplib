package br.com.codecacto.kmplib.inspection.data

import br.com.codecacto.kmplib.inspection.mapping.InspectionTemplateMapping
import br.com.codecacto.kmplib.inspection.model.EditItemDraft
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Espelho da backlib 0.204.0: "Funciona?" no modelo/item, groups-order e marca do emissor. */
class InspectionBacklib0204Test {

    private val id = InspectionFixtures.INSPECTION_ID

    @Test
    fun `item sem o sinal continua decodificando com null`() {
        val insp = InspectionJson.decodeFromString(InspectionDto.serializer(), InspectionFixtures.ENTRY_DRAFT)
        assertNull(insp.groups[0].items[0].asksFunctioning)
    }

    @Test
    fun `item e item de modelo leem asksFunctioning`() {
        val item = InspectionJson.decodeFromString(
            ItemDto.serializer(),
            """{"id":"i","groupId":"g","name":"Piso","state":"GOOD","updatedAt":"2026-10-23T10:00:00Z","media":[],"asksFunctioning":false}""",
        )
        assertEquals(false, item.asksFunctioning)
        val t = InspectionJson.decodeFromString(
            TemplateItemDto.serializer(),
            """{"id":"t","name":"Torneira","defaultState":"GOOD","asksFunctioning":true}""",
        )
        assertEquals(true, t.asksFunctioning)
        assertTrue(InspectionTemplateMapping.templateItem(t).asksFunctioning)
        val antigo = InspectionJson.decodeFromString(TemplateItemDto.serializer(), """{"id":"t","name":"Torneira","defaultState":"GOOD"}""")
        assertFalse(InspectionTemplateMapping.templateItem(antigo).asksFunctioning)
    }

    @Test
    fun `item novo so manda asksFunctioning quando liga ou ja tinha valor`() {
        val label = { k: String -> k }
        val desligado = InspectionTemplateMapping.itemInput(EditItemDraft(itemId = null, name = "Piso"), label)
        assertNull(desligado.asksFunctioning, "servidor sem a migracao recusa gravar a coluna")
        val ligado = InspectionTemplateMapping.itemInput(EditItemDraft(itemId = null, name = "Torneira", asksFunctioning = true), label)
        assertEquals(true, ligado.asksFunctioning)
        val base = InspectionJson.decodeFromString(TemplateItemDto.serializer(), """{"id":"t","name":"Torneira","defaultState":"GOOD","asksFunctioning":true}""")
        val desligou = InspectionTemplateMapping.itemInput(EditItemDraft(itemId = "t", name = "Torneira", asksFunctioning = false), label, base)
        assertEquals(false, desligou.asksFunctioning)
    }

    @Test
    fun `reordenar comodos manda PUT groups-order e a sessao troca a vistoria`() = runTest {
        val s = FakeInspectionServer()
        s.on("GET", "/inspections/$id", Reply(body = InspectionFixtures.ENTRY_DRAFT))
        s.on("PUT", "/inspections/$id/groups-order", Reply(body = InspectionFixtures.ENTRY_DRAFT))
        val ids = listOf(InspectionFixtures.GROUP_COZINHA, InspectionFixtures.GROUP_SALA)
        val r = s.api().reorderGroups(id, ids)
        assertTrue(r.isOk)
        val req = s.receivedAt("PUT", "/inspections/$id/groups-order").single()
        assertEquals("""{"ids":["${ids[0]}","${ids[1]}"]}""", req.body)
        val sess = InspectionSession(id, s.api(), queue = null, patches = InMemoryInspectionPatchStore(), scope = backgroundScope, clock = { 1L })
        sess.load()
        assertTrue(sess.reorderGroups(ids).isOk)
        assertEquals(2, s.receivedAt("PUT", "/inspections/$id/groups-order").size)
    }

    @Test
    fun `verificacao traz a marca do emissor e aceita a antiga sem ela`() {
        val base = """{"authentic":true,"code":"ABC","kind":"SEALED","version":1,"latestVersion":1,"superseded":false,"locked":true,
            "pdfSha256":"a","contentSha256":"b","generatedAt":"2026-10-23T10:00:00Z","signers":[],"inspection":{"x":1}"""
        val com = InspectionJson.decodeFromString(VerifyDto.serializer(), "$base,\"issuerName\":\"Studio Bella\",\"issuerColorHex\":\"#112233\"}")
        assertEquals("Studio Bella", com.issuerName)
        assertEquals("#112233", com.issuerColorHex)
        val sem = InspectionJson.decodeFromString(VerifyDto.serializer(), "$base}")
        assertNull(sem.issuerName)
        assertNull(sem.issuerColorHex)
    }
}
