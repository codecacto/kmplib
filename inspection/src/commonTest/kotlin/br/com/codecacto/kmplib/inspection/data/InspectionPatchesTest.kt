package br.com.codecacto.kmplib.inspection.data

import br.com.codecacto.kmplib.core.storage.InMemoryBlobStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InspectionPatchesTest {

    @Test
    fun `a ultima palavra vence e limpar apaga o anterior`() {
        val a = ItemPatch("i", state = ItemState.FAIR, observation = "risco", result = ItemResult.CONFORM)
        val b = ItemPatch("i", state = ItemState.POOR, clear = listOf(ItemClearFields.OBSERVATION))
        val m = mergeItemPatches(a, b)
        assertEquals(ItemState.POOR, m.state)
        assertNull(m.observation)
        assertEquals(ItemResult.CONFORM, m.result)
        assertEquals(listOf(ItemClearFields.OBSERVATION), m.clear)
    }

    @Test
    fun `preencher depois de limpar tira o campo da limpeza`() {
        val a = ItemPatch("i", clear = listOf(ItemClearFields.RESULT, ItemClearFields.OBSERVATION))
        val b = ItemPatch("i", result = ItemResult.NON_CONFORM)
        val m = mergeItemPatches(a, b)
        assertEquals(ItemResult.NON_CONFORM, m.result)
        assertEquals(listOf(ItemClearFields.OBSERVATION), m.clear)
    }

    @Test
    fun `leituras se somam por tarefa e null apaga`() {
        val r1 = MeterReadingDto("10.5", MeterUnit.M3)
        val a = ItemPatch("i", readings = mapOf("agua" to r1))
        val b = ItemPatch("i", readings = mapOf("luz" to MeterReadingDto("200", MeterUnit.KWH), "agua" to null))
        val m = mergeItemPatches(a, b)
        assertEquals(setOf("agua", "luz"), m.readings!!.keys)
        assertNull(m.readings["agua"])
    }

    @Test
    fun `fila pendente tem uma entrada por item na ordem da primeira mudanca`() {
        var l = emptyList<ItemPatch>()
        l = appendItemPatch(l, ItemPatch("a", state = ItemState.FAIR))
        l = appendItemPatch(l, ItemPatch("b", state = ItemState.GOOD))
        l = appendItemPatch(l, ItemPatch("a", state = ItemState.POOR))
        assertEquals(listOf("a", "b"), l.map { it.id })
        assertEquals(ItemState.POOR, l[0].state)
    }

    @Test
    fun `aplicar o patch mostra o valor novo e limpa o pedido`() {
        val insp = InspectionFixtures.entryDraft()
        val parede = insp.groups[0].items[1]
        val novo = parede.applyPatch(
            ItemPatch(parede.id, state = ItemState.POOR, clear = listOf(ItemClearFields.OBSERVATION), answers = mapOf("cor" to JsonPrimitive("branca"))),
        )
        assertEquals(ItemState.POOR, novo.state)
        assertNull(novo.observation)
        assertEquals(JsonPrimitive("branca"), novo.answers["cor"])
        val vis = insp.applyPatches(listOf(ItemPatch(parede.id, result = ItemResult.CONFORM)))
        assertEquals(ItemResult.CONFORM, vis.groups[0].items[1].result)
        assertEquals(insp.groups[0].items[0], vis.groups[0].items[0])
    }

    @Test
    fun `patches sobrevivem no BlobStore e lista vazia apaga`() = runTest {
        val blobs = InMemoryBlobStore()
        val store = BlobInspectionPatchStore(blobs)
        val l = listOf(ItemPatch("a", state = ItemState.POOR, observation = "x"))
        assertTrue(store.save("insp-1", l))
        assertEquals(l, BlobInspectionPatchStore(blobs).load("insp-1"))
        store.save("insp-1", emptyList())
        assertTrue(blobs.ids().isEmpty())
        assertEquals(emptyList(), store.load("insp-1"))
    }
}
