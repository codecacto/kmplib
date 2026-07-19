package br.com.codecacto.kmplib.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpokenNumberParserTest {

    @Test
    fun digitos_simples() {
        assertEquals(420.0, SpokenNumberParser.parse("420"))
        assertEquals(420.0, SpokenNumberParser.parse("420 quilos"))
        assertEquals(26.0, SpokenNumberParser.parse("26 arrobas"))
    }

    @Test
    fun digitos_com_decimal_virgula_e_ponto() {
        assertEquals(391.3, SpokenNumberParser.parse("391,3"))
        assertEquals(391.3, SpokenNumberParser.parse("391.3"))
        assertEquals(1234.5, SpokenNumberParser.parse("1.234,5"))
    }

    @Test
    fun decimal_falado_virgula() {
        assertEquals(420.5, SpokenNumberParser.parse("420 vírgula 5"))
        assertEquals(391.3, SpokenNumberParser.parse("391 ponto 3"))
    }

    @Test
    fun por_extenso_centenas_e_dezenas() {
        assertEquals(420.0, SpokenNumberParser.parse("quatrocentos e vinte"))
        assertEquals(123.0, SpokenNumberParser.parse("cento e vinte e três"))
        assertEquals(100.0, SpokenNumberParser.parse("cem"))
        assertEquals(305.0, SpokenNumberParser.parse("trezentos e cinco"))
    }

    @Test
    fun por_extenso_com_unidade_falada_ignorada() {
        assertEquals(500.0, SpokenNumberParser.parse("quinhentos quilos"))
        assertEquals(30.0, SpokenNumberParser.parse("trinta arrobas"))
    }

    @Test
    fun por_extenso_milhares() {
        assertEquals(1000.0, SpokenNumberParser.parse("mil"))
        assertEquals(1200.0, SpokenNumberParser.parse("mil e duzentos"))
        assertEquals(2500.0, SpokenNumberParser.parse("dois mil e quinhentos"))
    }

    @Test
    fun por_extenso_decimal_meia() {
        assertEquals(420.5, SpokenNumberParser.parse("quatrocentos e vinte vírgula meia"))
    }

    @Test
    fun texto_sem_numero_retorna_null() {
        assertNull(SpokenNumberParser.parse(""))
        assertNull(SpokenNumberParser.parse("bom dia"))
        assertNull(SpokenNumberParser.parse("   "))
    }

    @Test
    fun parseToDisplay_inteiro_sem_decimais() {
        assertEquals("420", SpokenNumberParser.parseToDisplay("420"))
        assertEquals("420", SpokenNumberParser.parseToDisplay("quatrocentos e vinte"))
    }

    @Test
    fun parseToDisplay_decimal_com_separador_br() {
        assertEquals("391,3", SpokenNumberParser.parseToDisplay("391,3"))
        assertEquals("420,5", SpokenNumberParser.parseToDisplay("420 vírgula 5"))
    }

    @Test
    fun parseToDisplay_separador_customizado() {
        assertEquals("391.3", SpokenNumberParser.parseToDisplay("391,3", decimalSeparator = '.'))
    }

    @Test
    fun digitos_tem_prioridade_sobre_extenso() {
        // Se houver algarismos, usa-os (mais confiável que o extenso concorrente).
        assertEquals(420.0, SpokenNumberParser.parse("peso 420 quatrocentos"))
    }

    @Test
    fun formatNumber_arredonda_duas_casas() {
        assertEquals("12,35", SpokenNumberParser.formatNumber(12.346, ','))
        assertTrue(SpokenNumberParser.formatNumber(12.0, ',') == "12")
    }
}
