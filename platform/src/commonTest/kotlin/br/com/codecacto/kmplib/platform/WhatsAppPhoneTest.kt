package br.com.codecacto.kmplib.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WhatsAppPhoneTest {

    @Test
    fun `celular sem DDI ganha o 55`() {
        assertEquals("5565999998888", whatsAppPhoneNumber("65999998888"))
    }

    @Test
    fun `fixo sem DDI ganha o 55`() {
        assertEquals("556533334444", whatsAppPhoneNumber("6533334444"))
    }

    @Test
    fun `mascara e espacos saem`() {
        assertEquals("5565999998888", whatsAppPhoneNumber("(65) 99999-8888"))
        assertEquals("5565999998888", whatsAppPhoneNumber(" 65 9 9999 8888 "))
    }

    @Test
    fun `numero que ja tem o 55 fica como esta`() {
        assertEquals("5565999998888", whatsAppPhoneNumber("5565999998888"))
        assertEquals("5565999998888", whatsAppPhoneNumber("+55 (65) 99999-8888"))
        assertEquals("556533334444", whatsAppPhoneNumber("556533334444"))
    }

    @Test
    fun `DDD 55 sem DDI ainda ganha o 55`() {
        // 55 é DDD de Santa Maria/RS: 10-11 algarismos são sempre brasileiros sem DDI.
        assertEquals("5555999998888", whatsAppPhoneNumber("55999998888"))
        assertEquals("555533334444", whatsAppPhoneNumber("5533334444"))
    }

    @Test
    fun `prefixo de discagem com zero sai`() {
        assertEquals("5565999998888", whatsAppPhoneNumber("065999998888"))
        assertEquals("5565999998888", whatsAppPhoneNumber("0055 65 99999-8888"))
    }

    @Test
    fun `numero de outro pais com DDI fica como esta`() {
        assertEquals("351912345678", whatsAppPhoneNumber("+351 912 345 678"))
        assertEquals("14155552671", whatsAppPhoneNumber("+1 415 555 2671"))
        assertEquals("123456789012345", whatsAppPhoneNumber("+123456789012345"))
    }

    @Test
    fun `vazio nulo ou sem algarismo da null`() {
        assertNull(whatsAppPhoneNumber(null))
        assertNull(whatsAppPhoneNumber(""))
        assertNull(whatsAppPhoneNumber("   "))
        assertNull(whatsAppPhoneNumber("sem telefone"))
        assertNull(whatsAppPhoneNumber("000"))
    }

    @Test
    fun `curto ou longo demais da null`() {
        assertNull(whatsAppPhoneNumber("1234567"))
        assertNull(whatsAppPhoneNumber("99998888".dropLast(1)))
        assertNull(whatsAppPhoneNumber("1234567890123456"))
    }

    @Test
    fun `sem DDD da null`() {
        // Sem DDD não há como saber para onde ligar.
        assertNull(whatsAppPhoneNumber("99999-8888"))
        assertNull(whatsAppPhoneNumber("33334444"))
    }

    @Test
    fun `DDI explicito com mais ou zero zero aceita a partir de oito algarismos`() {
        assertEquals("12345678", whatsAppPhoneNumber("00 1234 5678"))
        assertNull(whatsAppPhoneNumber("+1234567"))
        assertNull(whatsAppPhoneNumber("+1234567890123456"))
    }

    @Test
    fun `DDI de outro pais sem mais com doze a quinze algarismos fica`() {
        assertEquals("351912345678", whatsAppPhoneNumber("351912345678"))
        assertEquals("123456789012345", whatsAppPhoneNumber("123456789012345"))
    }
}
