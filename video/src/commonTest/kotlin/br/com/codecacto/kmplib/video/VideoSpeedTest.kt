package br.com.codecacto.kmplib.video

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoSpeedTest {

    @Test
    fun `as seis velocidades da fabrica na ordem do menu`() {
        assertEquals(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f), VIDEO_SPEEDS)
    }

    @Test
    fun `rotulo com virgula decimal e sem zero a toa`() {
        assertEquals("0,5×", formatVideoSpeed(0.5f))
        assertEquals("0,75×", formatVideoSpeed(0.75f))
        assertEquals("1×", formatVideoSpeed(1f))
        assertEquals("1,25×", formatVideoSpeed(1.25f))
        assertEquals("1,5×", formatVideoSpeed(1.5f))
        assertEquals("2×", formatVideoSpeed(2f))
    }

    @Test
    fun `centesimo abaixo de dez mantem o zero`() {
        // 1,05× tem de sair "1,05" e nunca "1,5" — é a diferença entre 5% e 50% mais rápido.
        assertEquals("1,05×", formatVideoSpeed(1.05f))
    }

    @Test
    fun `ruido de ponto flutuante nao vaza para o rotulo`() {
        assertEquals("1,25×", formatVideoSpeed(1.2499999f))
        assertEquals("1×", formatVideoSpeed(0.99999994f))
    }

    @Test
    fun `o ciclo passa pelas seis e volta ao comeco`() {
        var atual = 0.5f
        val visitadas = mutableListOf(atual)
        repeat(5) {
            atual = nextVideoSpeed(atual)
            visitadas += atual
        }
        assertEquals(VIDEO_SPEEDS, visitadas)
        assertEquals(0.5f, nextVideoSpeed(2f))
    }

    @Test
    fun `velocidade fora da lista entra no ciclo pela mais proxima acima`() {
        assertEquals(1.5f, nextVideoSpeed(1.1f))
    }
}
