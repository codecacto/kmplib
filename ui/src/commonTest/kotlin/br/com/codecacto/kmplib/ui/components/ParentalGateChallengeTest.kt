package br.com.codecacto.kmplib.ui.components

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParentalGateChallengeTest {

    private val desafio = ParentalGateChallenge(listOf(7, 2, 9))

    @Test
    fun acertarASequenciaInteiraPassa() {
        assertEquals(ParentalGateCheck.INCOMPLETE, desafio.check(emptyList()))
        assertEquals(ParentalGateCheck.INCOMPLETE, desafio.check(listOf(7)))
        assertEquals(ParentalGateCheck.INCOMPLETE, desafio.check(listOf(7, 2)))
        assertEquals(ParentalGateCheck.PASSED, desafio.check(listOf(7, 2, 9)))
    }

    @Test
    fun errarQualquerDigitoReprovaNaHora() {
        assertEquals(ParentalGateCheck.FAILED, desafio.check(listOf(2)))
        assertEquals(ParentalGateCheck.FAILED, desafio.check(listOf(7, 9)))
        assertEquals(ParentalGateCheck.FAILED, desafio.check(listOf(7, 2, 1)))
        assertEquals(ParentalGateCheck.FAILED, desafio.check(listOf(7, 2, 9, 9)))
    }

    @Test
    fun sorteioUsaDigitosDistintosDeUmANoveEZeroEhSempreErrado() {
        repeat(200) { seed ->
            val d = ParentalGateChallenge.random(Random(seed))
            assertEquals(ParentalGateChallenge.DEFAULT_LENGTH, d.digits.size)
            assertEquals(d.digits.size, d.digits.toSet().size)
            assertTrue(d.digits.all { it in 1..9 })
            // O flow Maestro toca `portao-pais-digito-0` para provar a resposta errada.
            assertEquals(ParentalGateCheck.FAILED, d.check(listOf(0)))
        }
    }

    @Test
    fun sorteioVariaEntreAberturas() {
        val vistos = (0 until 50).map { ParentalGateChallenge.random(Random(it)).digits }.toSet()
        assertTrue(vistos.size > 40)
    }

    @Test
    fun desafioInvalidoNaoNasce() {
        assertFailsWith<IllegalArgumentException> { ParentalGateChallenge(emptyList()) }
        assertFailsWith<IllegalArgumentException> { ParentalGateChallenge(listOf(10)) }
        assertFailsWith<IllegalArgumentException> { ParentalGateChallenge.random(length = 10) }
    }

    @Test
    fun desafioSaiPorExtensoSemNenhumAlgarismo() {
        val texts = ParentalGateTexts(
            title = "t",
            instruction = "i",
            cancel = "c",
            numberWords = listOf("zero", "um", "dois", "três", "quatro", "cinco", "seis", "sete", "oito", "nove"),
        )
        val escrito = texts.spell(desafio)
        assertEquals("sete, dois, nove", escrito)
        assertFalse(escrito.any { it.isDigit() })
        assertFailsWith<IllegalArgumentException> { texts.copy(numberWords = listOf("um")) }
    }

    @Test
    fun idsDoPortaoSaoEstaveis() {
        assertEquals("portao-pais", ParentalGateTestTags.CONTAINER)
        assertEquals("portao-pais-desafio", ParentalGateTestTags.DESAFIO)
        assertEquals("portao-pais-btn-cancelar", ParentalGateTestTags.BTN_CANCELAR)
        assertEquals("portao-pais-digito-0", ParentalGateTestTags.digito(0))
        assertEquals("portao-pais-digito-7", ParentalGateTestTags.digito(7))
    }
}
