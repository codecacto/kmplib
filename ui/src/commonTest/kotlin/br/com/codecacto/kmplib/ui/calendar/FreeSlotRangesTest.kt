package br.com.codecacto.kmplib.ui.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Regra pura da pista "+ Agendar" do `AppTimeGridScheduler` (`freeSlotHint`, 2.318.0). */
class FreeSlotRangesTest {

    private val window = BusinessWindow(480, 720) // 08:00–12:00

    @Test
    fun `sem ocupacao e sem disponibilidade declarada a janela inteira vira passos livres`() {
        val free = freeSlotRanges(window, 60, availableRanges = null, occupied = emptyList())
        assertEquals(listOf(480, 540, 600, 660), free.map { it.startMin })
        assertTrue(free.all { it.endMin - it.startMin == 60 })
    }

    @Test
    fun `evento e bloqueio tiram os passos que tocam`() {
        val free = freeSlotRanges(
            window,
            30,
            availableRanges = null,
            occupied = listOf(MinuteRange(540, 585), MinuteRange(660, 690)), // 09:00–09:45 e 11:00–11:30
        )
        // 09:00 e 09:30 caem (o evento invade o 09:30); 11:00 cai; 10:00 e 11:30 sobram.
        assertEquals(listOf(480, 510, 600, 630, 690), free.map { it.startMin })
    }

    @Test
    fun `evento que termina exatamente no inicio do passo nao o ocupa`() {
        val free = freeSlotRanges(window, 60, null, listOf(MinuteRange(480, 540)))
        assertEquals(listOf(540, 600, 660), free.map { it.startMin })
    }

    @Test
    fun `so oferece dentro do expediente e nunca passo parcial`() {
        val free = freeSlotRanges(
            window,
            30,
            availableRanges = listOf(WorkRange(520, 640)), // 08:40–10:40
            occupied = emptyList(),
        )
        // 08:30 começa fora; 10:30 termina 11:00, fora — sobram 09:00, 09:30, 10:00.
        assertEquals(listOf(540, 570, 600), free.map { it.startMin })
    }

    @Test
    fun `faixa de duracao zero ocupa o minuto em que comeca`() {
        val free = freeSlotRanges(window, 60, null, listOf(MinuteRange(600, 600)))
        assertEquals(listOf(480, 540, 660), free.map { it.startMin })
    }

    @Test
    fun `janela que nao comeca no multiplo alinha no proximo passo`() {
        val free = freeSlotRanges(BusinessWindow(495, 600), 30, null, emptyList())
        assertEquals(listOf(510, 540, 570), free.map { it.startMin })
    }

    @Test
    fun `passo invalido ou janela vazia nao gera pista`() {
        assertTrue(freeSlotRanges(window, 0, null, emptyList()).isEmpty())
        assertTrue(freeSlotRanges(BusinessWindow(600, 600), 30, null, emptyList()).isEmpty())
    }
}
