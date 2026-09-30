package br.com.codecacto.kmplib.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InternationalPhoneTest {

    @Test
    fun `DDI por regiao vem do plano de numeracao`() {
        assertEquals(55, InternationalPhone.callingCodeFor("BR"))
        assertEquals(351, InternationalPhone.callingCodeFor("pt"))
        assertEquals(1, InternationalPhone.callingCodeFor("US"))
        assertEquals(1, InternationalPhone.callingCodeFor("CA"))
        assertNull(InternationalPhone.callingCodeFor("ZZ"))
    }

    @Test
    fun `regiao principal vem primeiro nos DDIs compartilhados e todas existem`() {
        assertEquals("US", InternationalPhone.regionsFor(1).first())
        assertTrue("CA" in InternationalPhone.regionsFor(1))
        assertEquals("GB", InternationalPhone.regionsFor(44).first())
        assertEquals(listOf("BR"), InternationalPhone.regionsFor(55))
        assertTrue(InternationalPhone.regionsFor(999).isEmpty())
        listOf(1, 7, 39, 44, 47, 61, 212, 262, 290, 358, 590, 599).forEach { ddi ->
            val principal = InternationalPhone.regionsFor(ddi).first()
            assertEquals(ddi, InternationalPhone.callingCodeFor(principal), "principal de +$ddi")
        }
    }

    @Test
    fun `tabela gerada cobre o mundo e respeita o teto E164`() {
        val regioes = InternationalPhone.supportedRegions
        assertTrue(regioes.size > 240)
        assertTrue(regioes.all { it.length == 2 && it.all { c -> c in 'A'..'Z' } })
        regioes.forEach { r ->
            val ddi = InternationalPhone.callingCodeFor(r)!!
            assertTrue(ddi in 1..999, r)
        }
    }

    @Test
    fun `filtro deixa mais so no comeco e no maximo 15 digitos`() {
        assertEquals("+351912345678", InternationalPhone.filterInput(" +351 912-345-678 "))
        assertEquals("5511", InternationalPhone.filterInput("55+11"))
        assertEquals("+" + "1".repeat(15), InternationalPhone.filterInput("+" + "1".repeat(20)))
        assertEquals("", InternationalPhone.filterInput("abc"))
    }

    @Test
    fun `com mais o DDI vem do numero em qualquer regiao`() {
        val p = InternationalPhone.parse("+351 912 345 678", defaultRegion = "BR")!!
        assertEquals(351, p.callingCode)
        assertEquals("912345678", p.nationalNumber)
        assertEquals("PT", p.region)
        assertEquals("+351912345678", p.e164)
        // +1 com região padrão canadense fica CA; sem, a principal (US).
        assertEquals("CA", InternationalPhone.parse("+1 604 555 0100", "CA")!!.region)
        assertEquals("US", InternationalPhone.parse("+1 604 555 0100", null)!!.region)
    }

    @Test
    fun `sem mais e numero nacional da regiao padrao e sem regiao nao da para saber`() {
        assertEquals("+351912345678", InternationalPhone.toE164("912 345 678", "PT"))
        assertNull(InternationalPhone.parse("912345678", null))
        assertNull(InternationalPhone.parse("+999 1234", null))
        assertNull(InternationalPhone.parse("", "PT"))
    }

    @Test
    fun `prefixo de tronco sai quando o numero passa do comprimento do pais`() {
        // Reino Unido: 0 + 10 dígitos.
        assertEquals("+447911123456", InternationalPhone.toE164("07911 123456", "GB"))
        // EUA: 1 + 10 dígitos.
        assertEquals("+14155550100", InternationalPhone.toE164("1 415 555 0100", "US"))
        assertEquals("+14155550100", InternationalPhone.toE164("415-555-0100", "US"))
        // Brasil: 0 + DDD + número.
        assertEquals("+5565999998888", InternationalPhone.toE164("0 65 99999-8888", "BR"))
        // Itália não tem prefixo de tronco: o 0 é do número.
        assertEquals("+390612345678", InternationalPhone.toE164("06 1234 5678", "IT"))
    }

    @Test
    fun `validacao por comprimento do pais e regra completa no Brasil`() {
        assertTrue(InternationalPhone.isValid("+351912345678", null))
        assertFalse(InternationalPhone.isValid("+35191234567", null))      // 8 dígitos em PT
        assertFalse(InternationalPhone.isValid("+3519123456789", null))    // 10 dígitos em PT
        assertTrue(InternationalPhone.isValid("+5565999998888", null))
        assertTrue(InternationalPhone.isValid("65 3322-1100", "BR"))
        assertFalse(InternationalPhone.isValid("+5520999998888", null))     // DDD 20 não existe
        assertFalse(InternationalPhone.isValid("+5565899998888", null))     // celular sem o 9
        assertFalse(InternationalPhone.isValid("+", null))
        assertFalse(InternationalPhone.isValid("+1", null))
    }

    @Test
    fun `exibicao por pais`() {
        assertEquals("+55 (65) 99999-8888", InternationalPhone.format("+5565999998888", null))
        assertEquals("+55 (65) 3322-1100", InternationalPhone.format("6533221100", "BR"))
        assertEquals("+1 415-555-0100", InternationalPhone.format("+14155550100", null))
        assertEquals("+351 912 345 678", InternationalPhone.format("+351912345678", null))
        assertEquals("+49 151 234 567 89", InternationalPhone.format("+4915123456789", null))
        assertEquals("abc", InternationalPhone.format("abc", null))
    }

    @Test
    fun `formatacao ao digitar nunca tira nem reordena caractere`() {
        val casos = listOf(
            "+" to null, "+3" to null, "+35" to null, "+351" to null, "+3519" to null,
            "+351912345678" to null, "+1415555" to null, "+5565999" to null,
            "9" to "PT", "912345" to "PT", "4155550100" to "US", "65999998888" to "BR", "1234567" to null,
        )
        casos.forEach { (bruto, regiao) ->
            val formatado = InternationalPhone.formatAsYouType(bruto, regiao)
            assertEquals(bruto, formatado.filter { it == '+' || it.isDigit() }, "\"$bruto\" → \"$formatado\"")
        }
        assertEquals("+351 912 345 678", InternationalPhone.formatAsYouType("+351912345678", null))
        assertEquals("+351", InternationalPhone.formatAsYouType("+351", null))
        assertEquals("415-555-01", InternationalPhone.formatAsYouType("41555501", "US"))
        assertEquals("(65) 99999-8888", InternationalPhone.formatAsYouType("65999998888", "BR"))
        assertEquals("", InternationalPhone.formatAsYouType("", "BR"))
    }
}
