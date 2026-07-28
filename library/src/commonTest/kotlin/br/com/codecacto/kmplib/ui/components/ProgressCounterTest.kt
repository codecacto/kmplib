package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testes do núcleo puro do contador operacional [CountProgress]/[ProgressCounter] — inclusive os
 * casos de borda que quebrariam a tela (total zero, contagem acima do total) e o anúncio acessível.
 */
class ProgressCounterTest {

    @Test
    fun `fracao proporcional a contagem`() {
        assertEquals(0.5f, CountProgress(6, 12).fraction)
        assertEquals(0f, CountProgress(0, 12).fraction)
        assertEquals(1f, CountProgress(12, 12).fraction)
    }

    @Test
    fun `total zero nao divide por zero`() {
        val p = CountProgress(0, 0)
        assertEquals(0f, p.fraction)
        assertTrue(p.isEmpty)
        assertFalse(p.isComplete)
        assertEquals(0, p.remaining)
    }

    @Test
    fun `contagem acima do total nao estoura a barra nem o restante`() {
        val p = CountProgress(15, 12)
        assertEquals(1f, p.fraction)
        assertEquals(0, p.remaining)
        assertTrue(p.isComplete)
    }

    @Test
    fun `restante e completude`() {
        val p = CountProgress(7, 12)
        assertEquals(5, p.remaining)
        assertFalse(p.isComplete)
        assertTrue(CountProgress(12, 12).isComplete)
    }

    @Test
    fun `texto curto do contador`() {
        assertEquals("7 de 12", progressCounterText(CountProgress(7, 12)))
    }

    @Test
    fun `texto curto respeita i18n`() {
        assertEquals(
            "7 of 12",
            progressCounterText(CountProgress(7, 12), ProgressCounterTexts(ofSeparator = "of")),
        )
    }

    @Test
    fun `anuncio acessivel inclui o rotulo`() {
        assertEquals(
            "7 de 12 embarcados",
            progressCounterAccessibilityText(CountProgress(7, 12), label = "embarcados"),
        )
    }

    @Test
    fun `anuncio sem rotulo nao deixa espaco sobrando`() {
        assertEquals("7 de 12", progressCounterAccessibilityText(CountProgress(7, 12), label = null))
        assertEquals("7 de 12", progressCounterAccessibilityText(CountProgress(7, 12), label = "  "))
    }

    @Test
    fun `tom muda ao completar`() {
        assertEquals(
            ProgressTone.Primary,
            progressCounterTone(CountProgress(7, 12)),
        )
        assertEquals(
            ProgressTone.Success,
            progressCounterTone(CountProgress(12, 12)),
        )
    }

    @Test
    fun `completeTone nulo mantem o tom base`() {
        assertEquals(
            ProgressTone.Info,
            progressCounterTone(CountProgress(12, 12), tone = ProgressTone.Info, completeTone = null),
        )
    }

    @Test
    fun `lista vazia nao e considerada completa`() {
        // "0 de 0" não deve pintar de verde como se a tarefa tivesse sido cumprida.
        assertEquals(ProgressTone.Primary, progressCounterTone(CountProgress(0, 0)))
    }
}
