package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DigitBoxLogicTest {

    @Test
    fun digitarAcrescentaUmAlgarismo() {
        assertEquals(DigitBoxInput.Accepted("12"), applyDigitBoxInput("1", "12", 4))
    }

    @Test
    fun apagarVoltaUmaCaixa() {
        assertEquals(DigitBoxInput.Accepted("12"), applyDigitBoxInput("123", "12", 4))
        assertEquals(DigitBoxInput.Accepted(""), applyDigitBoxInput("1", "", 4))
    }

    @Test
    fun letraEPontuacaoSaoDescartadas() {
        assertEquals(DigitBoxInput.Accepted("12"), applyDigitBoxInput("12", "12a", 4))
        assertEquals(DigitBoxInput.Accepted("1234"), applyDigitBoxInput("", "12-34", 4))
    }

    @Test
    fun algarismoNaoAsciiNaoEntra() {
        // Dígito árabe-índico "١" não é algarismo 0-9: não vira número de urna.
        assertEquals(DigitBoxInput.Accepted("1"), applyDigitBoxInput("1", "1١", 4))
    }

    @Test
    fun colarNumeroCompletoNoCampoVazio() {
        assertEquals(DigitBoxInput.Accepted("1234"), applyDigitBoxInput("", "1234", 4))
    }

    @Test
    fun teclaAMaisNoCampoCheioEIgnorada() {
        assertEquals(DigitBoxInput.Ignored, applyDigitBoxInput("1234", "12345", 4))
    }

    @Test
    fun colarNumeroCompletoPorCimaDoAnteriorSubstitui() {
        assertEquals(DigitBoxInput.Accepted("5678"), applyDigitBoxInput("1234", "12345678", 4))
        assertEquals(DigitBoxInput.Accepted("5678"), applyDigitBoxInput("12", "125678", 4))
    }

    @Test
    fun colarMaisDoQueCabeEExcedenteSemCortar() {
        assertEquals(DigitBoxInput.Overflow("98765"), applyDigitBoxInput("", "98765", 4))
        assertEquals(DigitBoxInput.Overflow("98765"), applyDigitBoxInput("12", "1298765", 4))
    }

    @Test
    fun colarParcialQueEstouraContaOTotal() {
        assertEquals(DigitBoxInput.Overflow("12345"), applyDigitBoxInput("12", "12345", 4))
    }

    @Test
    fun substituicaoTotalMaiorQueOCampoEExcedente() {
        // Texto proposto não começa pelo atual (seleção inteira substituída) e passa do tamanho.
        assertEquals(DigitBoxInput.Overflow("98765"), applyDigitBoxInput("1234", "98765", 4))
    }

    @Test
    fun tamanhoInvalidoFalha() {
        assertFailsWith<IllegalArgumentException> { applyDigitBoxInput("", "1", 0) }
    }

    @Test
    fun faltamDigitos() {
        assertEquals(4, digitBoxMissingCount("", 4))
        assertEquals(2, digitBoxMissingCount("12", 4))
        assertEquals(0, digitBoxMissingCount("1234", 4))
        assertEquals(0, digitBoxMissingCount("12345", 4))
        assertEquals(3, digitBoxMissingCount("1-", 4))
    }

    @Test
    fun caixaDaVezEAProximaOuAUltima() {
        assertEquals(0, digitBoxActiveIndex("", 4))
        assertEquals(2, digitBoxActiveIndex("12", 4))
        assertEquals(3, digitBoxActiveIndex("1234", 4))
    }

    @Test
    fun modeloDeTextoTrocaOsNumeros() {
        assertEquals("2 de 4 dígitos preenchidos", formatDigitBoxTemplate("%1\$d de %2\$d dígitos preenchidos", 2, 4))
        assertEquals("Faltam 3 dígitos", formatDigitBoxTemplate("Faltam %1\$d dígitos", 3))
    }

    @Test
    fun textosDoApp() {
        val texts = DigitBoxTexts(
            missing = { "faltam $it" },
            tooMany = { l, p -> "$l/$p" },
            description = { "campo $it" },
            state = { f, l -> "$f de $l" },
        )
        assertEquals("faltam 2", texts.missing(2))
        assertEquals("4/5", texts.tooMany(4, 5))
        assertEquals("campo 4", texts.description(4))
        assertEquals("1 de 4", texts.state(1, 4))
        assertEquals(12, DigitBoxFieldDefaults.MAX_LENGTH)
    }
}
