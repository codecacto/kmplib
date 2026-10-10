package br.com.codecacto.kmplib.core.util

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UuidV7Test {

    @Test
    fun formatoCanonicoVersaoEVariante() {
        val id = newUuidV7(nowMillis = 1_700_000_000_000L, random = Random(1))
        assertTrue(isCanonicalUuid(id), id)
        assertEquals('7', id[14])
        assertTrue(id[19] in "89ab", "variante: ${id[19]}")
        assertEquals(id, id.lowercase())
    }

    @Test
    fun instanteGravadoVoltaInteiro() {
        val agora = 1_728_561_234_567L
        assertEquals(agora, uuidV7TimestampMillis(newUuidV7(agora, Random(7))))
    }

    @Test
    fun ordenaPorCriacao() {
        val a = newUuidV7(1_000L, Random(1))
        val b = newUuidV7(1_001L, Random(2))
        val c = newUuidV7(2_000_000_000_000L, Random(3))
        assertEquals(listOf(a, b, c), listOf(c, a, b).sorted())
    }

    @Test
    fun aleatorioDiferenteNoMesmoMilissegundo() {
        val ids = (1..500).map { newUuidV7(5_000L) }.toSet()
        assertEquals(500, ids.size)
    }

    @Test
    fun recusaInstanteNegativoELeituraDeNaoV7() {
        assertFailsWith<IllegalArgumentException> { newUuidV7(-1L) }
        assertNull(uuidV7TimestampMillis("123e4567-e89b-42d3-a456-426614174000"))
        assertNull(uuidV7TimestampMillis("nao-e-uuid"))
        assertFalse(isCanonicalUuid("123e4567e89b42d3a456426614174000"))
        assertTrue(isCanonicalUuid("123E4567-E89B-42D3-A456-426614174000"))
    }
}
