package br.com.codecacto.kmplib.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RetryAfterTest {

    // Sun, 15 Nov 2026 14:00:00 GMT
    private val agora = 1_794_751_200_000L

    @Test
    fun `segundos inteiros`() {
        assertEquals(120L, parseRetryAfterSeconds("120", agora))
        assertEquals(0L, parseRetryAfterSeconds("0", agora))
        assertEquals(42L, parseRetryAfterSeconds("  42 ", agora))
    }

    @Test
    fun `ausente, vazio, negativo, fracionario e lixo viram null`() {
        assertNull(parseRetryAfterSeconds(null, agora))
        assertNull(parseRetryAfterSeconds("", agora))
        assertNull(parseRetryAfterSeconds("   ", agora))
        assertNull(parseRetryAfterSeconds("-5", agora))
        assertNull(parseRetryAfterSeconds("1.5", agora))
        assertNull(parseRetryAfterSeconds("amanhã", agora))
        assertNull(parseRetryAfterSeconds("99999999999999999999999", agora))
    }

    @Test
    fun `data HTTP IMF-fixdate no futuro vira os segundos que faltam`() {
        assertEquals(300L, parseRetryAfterSeconds("Sun, 15 Nov 2026 14:05:00 GMT", agora))
    }

    @Test
    fun `data HTTP com fracao de segundo arredonda para cima`() {
        assertEquals(300L, parseRetryAfterSeconds("Sun, 15 Nov 2026 14:05:00 GMT", agora + 500))
    }

    @Test
    fun `data HTTP no passado ou agora vira zero`() {
        assertEquals(0L, parseRetryAfterSeconds("Sun, 15 Nov 2026 13:00:00 GMT", agora))
        assertEquals(0L, parseRetryAfterSeconds("Sun, 15 Nov 2026 14:00:00 GMT", agora))
    }

    @Test
    fun `formas obsoletas da data HTTP tambem valem`() {
        assertEquals(300L, parseRetryAfterSeconds("Sunday, 15-Nov-26 14:05:00 GMT", agora))
        assertEquals(300L, parseRetryAfterSeconds("Sun Nov 15 14:05:00 2026", agora))
    }

    @Test
    fun `minutos para mostrar arredondam para cima, minimo 1`() {
        assertEquals(1L, retryAfterMinutesForDisplay(0))
        assertEquals(1L, retryAfterMinutesForDisplay(30))
        assertEquals(1L, retryAfterMinutesForDisplay(60))
        assertEquals(2L, retryAfterMinutesForDisplay(61))
        assertEquals(15L, retryAfterMinutesForDisplay(900))
    }
}
