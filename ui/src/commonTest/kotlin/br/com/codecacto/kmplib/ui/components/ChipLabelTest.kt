package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 2.373.0 — o chip da fileira rolável mostra o nome inteiro ("Bianca Toledo", nunca "Bianca Toled…"). */
class ChipLabelTest {

    @Test
    fun `nome de pessoa comum sai inteiro`() {
        assertEquals("Bianca Toledo", ChipLabel.display("Bianca Toledo"))
        assertEquals("Maria Aparecida dos Santos Silva", ChipLabel.display("Maria Aparecida dos Santos Silva"))
    }

    @Test
    fun `espaco sobrando nas pontas e repetido no meio some`() {
        assertEquals("Bianca Toledo", ChipLabel.display("  Bianca   Toledo \n"))
    }

    @Test
    fun `exatamente no limite sai inteiro`() {
        val label = "a".repeat(ChipLabel.MAX_CHARS)
        assertEquals(label, ChipLabel.display(label))
    }

    @Test
    fun `acima do limite corta no fim de uma palavra com reticencias`() {
        val label = "Maria Aparecida dos Santos Silva Toledo"
        val shown = ChipLabel.display(label)
        assertEquals("Maria Aparecida dos Santos…", shown)
        assertTrue(shown.length <= ChipLabel.MAX_CHARS + 1)
        assertTrue(label.startsWith(shown.removeSuffix("…")))
    }

    @Test
    fun `palavra unica longa demais e cortada nela mesma`() {
        val shown = ChipLabel.display("x".repeat(50), maxChars = 10)
        assertEquals("xxxxxxxxx…", shown)
        assertEquals(10, shown.length)
    }

    @Test
    fun `limite configuravel e minimo valido`() {
        assertEquals("Bianca…", ChipLabel.display("Bianca Toledo", maxChars = 10))
        assertFailsWith<IllegalArgumentException> { ChipLabel.display("abc", maxChars = 1) }
    }
}
