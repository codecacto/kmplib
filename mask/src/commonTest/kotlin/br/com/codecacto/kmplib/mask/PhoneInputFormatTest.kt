package br.com.codecacto.kmplib.mask

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PhoneInputFormatTest {

    @Test
    fun `Brasil, regiao nula ou desconhecida ficam no caminho brasileiro`() {
        assertSame(PhoneInputFormat.Brazil, PhoneInputFormat.forRegion("BR"))
        assertSame(PhoneInputFormat.Brazil, PhoneInputFormat.forRegion(null))
        assertSame(PhoneInputFormat.Brazil, PhoneInputFormat.forRegion(" "))
        assertSame(PhoneInputFormat.Brazil, PhoneInputFormat.forRegion("ZZ"))
        assertTrue(PhoneInputFormat.Brazil.isBrazilian)
        assertEquals("PT", PhoneInputFormat.forRegion("pt").region)
        assertFalse(PhoneInputFormat.forRegion("PT").isBrazilian)
        assertEquals(PhoneInputFormat.forRegion("PT"), PhoneInputFormat.forRegion("pt"))
    }

    @Test
    fun `modo brasileiro e exatamente o de antes`() {
        val br = PhoneInputFormat.Brazil
        assertEquals(filterPhoneInput("(65) 99999-8888 123"), br.filter("(65) 99999-8888 123"))
        assertTrue(br.visualTransformation is PhoneVisualTransformation)
        assertTrue(br.isValid("65999998888"))
        assertFalse(br.isValid("+351912345678"))
        assertEquals("65999998888", br.toSubmitValue("(65) 99999-8888"))
    }

    @Test
    fun `modo internacional aceita mais, valida pelo pais e envia E164`() {
        val pt = PhoneInputFormat.forRegion("PT")
        assertEquals("+351912345678", pt.filter("+351 912 345 678"))
        assertTrue(pt.isValid("912345678"))
        assertTrue(pt.isValid("+5565999998888"))
        assertFalse(pt.isValid("91234"))
        assertEquals("+351912345678", pt.toSubmitValue("912345678"))
        assertEquals("+5565999998888", pt.toSubmitValue("+5565999998888"))
        assertEquals("123", pt.toSubmitValue(" 123 "))
    }

    @Test
    fun `valor salvo volta ao formato de digitacao do modo`() {
        assertEquals("65999998888", PhoneInputFormat.Brazil.fromStoredValue("+5565999998888"))
        assertEquals("65999998888", PhoneInputFormat.Brazil.fromStoredValue("(65) 99999-8888"))
        assertEquals("", PhoneInputFormat.Brazil.fromStoredValue(null))
        assertEquals("+351912345678", PhoneInputFormat.forRegion("PT").fromStoredValue("+351 912 345 678"))
    }

    @Test
    fun `mascara internacional mapeia o cursor nos dois sentidos`() {
        val bruto = "+351912345678"
        val t = InternationalPhoneVisualTransformation(null).filter(AnnotatedString(bruto))
        assertEquals("+351 912 345 678", t.text.text)
        val m = t.offsetMapping
        assertEquals(0, m.originalToTransformed(0))
        assertEquals(4, m.originalToTransformed(4))      // depois de "+351"
        assertEquals(6, m.originalToTransformed(5))      // pulou o espaço
        assertEquals(t.text.length, m.originalToTransformed(bruto.length))
        for (o in 0..t.text.length) {
            val original = m.transformedToOriginal(o)
            assertTrue(original in 0..bruto.length)
        }
        assertEquals(bruto.length, m.transformedToOriginal(t.text.length))
    }

    @Test
    fun `mascara internacional e comparavel por regiao`() {
        assertEquals(InternationalPhoneVisualTransformation("PT"), InternationalPhoneVisualTransformation("PT"))
        assertEquals(
            InternationalPhoneVisualTransformation("PT").hashCode(),
            InternationalPhoneVisualTransformation("PT").hashCode(),
        )
    }
}
