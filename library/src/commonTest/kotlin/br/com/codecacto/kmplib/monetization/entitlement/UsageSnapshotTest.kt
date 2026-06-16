package br.com.codecacto.kmplib.monetization.entitlement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UsageSnapshotTest {

    @Test
    fun `limite -1 e ilimitado`() {
        val s = UsageSnapshot(feature = "f", contagem = 999, limite = -1)
        assertTrue(s.isUnlimited)
        assertNull(s.remaining)
        assertFalse(s.isExhausted)
        assertEquals(0f, s.fraction)
    }

    @Test
    fun `limite 0 nao quebra fraction`() {
        val s = UsageSnapshot(feature = "f", contagem = 0, limite = 0)
        assertFalse(s.isUnlimited)
        assertEquals(0f, s.fraction)
        assertTrue(s.isExhausted)
        assertEquals(0L, s.remaining)
    }

    @Test
    fun `uso parcial`() {
        val s = UsageSnapshot(feature = "f", contagem = 2, limite = 10)
        assertEquals(8L, s.remaining)
        assertFalse(s.isExhausted)
        assertEquals(0.2f, s.fraction)
    }

    @Test
    fun `uso esgotado`() {
        val s = UsageSnapshot(feature = "f", contagem = 10, limite = 10)
        assertEquals(0L, s.remaining)
        assertTrue(s.isExhausted)
        assertEquals(1f, s.fraction)
    }

    @Test
    fun `restante do servidor tem precedencia`() {
        val s = UsageSnapshot(feature = "f", contagem = 2, limite = 10, restante = 3)
        assertEquals(3L, s.remaining)
        assertFalse(s.isExhausted)
    }

    @Test
    fun `restante negativo do servidor vira zero e esgotado`() {
        val s = UsageSnapshot(feature = "f", contagem = 12, limite = 10, restante = -2)
        assertEquals(0L, s.remaining)
        assertTrue(s.isExhausted)
    }

    @Test
    fun `fraction nunca passa de 1`() {
        val s = UsageSnapshot(feature = "f", contagem = 20, limite = 10)
        assertEquals(1f, s.fraction)
    }
}
