package br.com.codecacto.kmplib.video

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoTimeTest {

    @Test
    fun `relogio sem hora enquanto o video dura menos de uma hora`() {
        assertEquals("0:00", formatVideoTime(0))
        assertEquals("0:07", formatVideoTime(7_000))
        assertEquals("4:07", formatVideoTime(247_000))
        assertEquals("59:59", formatVideoTime(3_599_000))
    }

    @Test
    fun `a hora aparece so quando existe`() {
        assertEquals("1:00:00", formatVideoTime(3_600_000))
        assertEquals("1:04:07", formatVideoTime(3_847_000))
        assertEquals("10:00:00", formatVideoTime(36_000_000))
    }

    @Test
    fun `milissegundo quebrado nao arredonda o segundo para cima`() {
        // 4,9 s ainda é "0:04": o relógio de um player conta o segundo COMPLETO, senão a barra
        // mostra 0:05 antes de o quinto segundo existir.
        assertEquals("0:04", formatVideoTime(4_999))
    }

    @Test
    fun `negativo e desconhecido viram zero`() {
        assertEquals("0:00", formatVideoTime(-1))
        assertEquals("0:00", formatVideoTime(Long.MIN_VALUE))
    }

    @Test
    fun `avancar dez segundos perto do fim para na duracao`() {
        assertEquals(600_000, seekTargetOf(positionMillis = 595_000, deltaMillis = 10_000, durationMillis = 600_000))
    }

    @Test
    fun `voltar dez segundos no comeco para no zero`() {
        assertEquals(0, seekTargetOf(positionMillis = 4_000, deltaMillis = -10_000, durationMillis = 600_000))
    }

    @Test
    fun `duracao desconhecida so grampeia por baixo`() {
        assertEquals(15_000, seekTargetOf(positionMillis = 5_000, deltaMillis = 10_000, durationMillis = 0))
        assertEquals(0, seekTargetOf(positionMillis = 5_000, deltaMillis = -10_000, durationMillis = 0))
    }

    @Test
    fun `a fracao da barra nunca e NaN nem passa de um`() {
        assertEquals(0f, videoProgressOf(1_000, 0))
        assertEquals(0.5f, videoProgressOf(300_000, 600_000))
        assertEquals(1f, videoProgressOf(700_000, 600_000))
        assertEquals(0f, videoProgressOf(-10, 600_000))
    }
}
