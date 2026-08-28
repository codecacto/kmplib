package br.com.codecacto.kmplib.ui.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Testes da geração de slots pura — par mobile de `slots.test.ts` da weblib. */
class CalendarSlotsTest {

    @Test
    fun `gera slots do inicio ao fim respeitando a duracao`() {
        val slots = generateTimeSlots(GenerateSlotsOptions(startMin = 540, endMin = 660, stepMin = 30, slotMin = 30))
        assertEquals(listOf(540, 570, 600, 630), slots.map { it.startMin })
        assertTrue(slots.all { it.available })
    }

    @Test
    fun `slot maior que o passo so entra se o FIM couber na janela`() {
        // 09:00–10:00, combo 45min, passo 15 → 09:00, 09:15 (09:30 terminaria 10:15 > fim)
        val slots = generateTimeSlots(GenerateSlotsOptions(startMin = 540, endMin = 600, stepMin = 15, slotMin = 45))
        assertEquals(listOf(540, 555), slots.map { it.startMin })
        assertTrue(slots.all { it.endMin == it.startMin + 45 })
    }

    @Test
    fun `marca ocupado o slot que sobrepoe uma faixa busy`() {
        val slots = generateTimeSlots(
            GenerateSlotsOptions(
                startMin = 540, endMin = 660, stepMin = 30, slotMin = 30,
                busy = listOf(MinuteRange(570, 600)), // 09:30–10:00
            ),
        )
        val byStart = slots.associateBy { it.startMin }
        assertTrue(byStart[540]!!.available)
        assertFalse(byStart[570]!!.available)
        assertEquals("Ocupado", byStart[570]!!.reason)
        assertTrue(byStart[600]!!.available) // encosta, não sobrepõe
    }

    @Test
    fun `aplica lead time a partir de nowMin`() {
        val slots = generateTimeSlots(
            GenerateSlotsOptions(
                startMin = 540, endMin = 720, stepMin = 30, slotMin = 30,
                nowMin = 555, // 09:15
                leadMin = 60, // só a partir de 10:15
            ),
        )
        val byStart = slots.associateBy { it.startMin }
        assertFalse(byStart[540]!!.available) // 09:00 passou
        assertFalse(byStart[600]!!.available) // 10:00 < 10:15
        assertTrue(byStart[630]!!.available) // 10:30 ≥ 10:15
        assertEquals("Indisponível", byStart[540]!!.reason)
    }

    @Test
    fun `passo invalido retorna lista vazia`() {
        assertEquals(emptyList(), generateTimeSlots(GenerateSlotsOptions(startMin = 540, endMin = 660, stepMin = 0)))
    }

    @Test
    fun `availableSlots filtra so os livres`() {
        val slots = generateTimeSlots(
            GenerateSlotsOptions(
                startMin = 540, endMin = 660, stepMin = 30, slotMin = 30,
                busy = listOf(MinuteRange(540, 570)),
            ),
        )
        assertEquals(listOf(570, 600, 630), availableSlots(slots).map { it.startMin })
    }
}
