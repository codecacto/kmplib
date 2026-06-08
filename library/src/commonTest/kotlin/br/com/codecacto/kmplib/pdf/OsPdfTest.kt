package br.com.codecacto.kmplib.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OsPdfTest {

    // --- OsPdfFormat.parseMoney ----------------------------------------------

    @Test
    fun parseMoney_dotDecimal() {
        assertEquals(320.0, OsPdfFormat.parseMoney("320.00"))
        assertEquals(1234.56, OsPdfFormat.parseMoney("1234.56"))
        assertEquals(120.5, OsPdfFormat.parseMoney("120.5"))
    }

    @Test
    fun parseMoney_commaDecimal_andCurrencySymbol() {
        assertEquals(320.0, OsPdfFormat.parseMoney("R$ 320,00"))
        assertEquals(1234.56, OsPdfFormat.parseMoney("1.234,56"))
    }

    @Test
    fun parseMoney_emptyOrInvalid_returnsZero() {
        assertEquals(0.0, OsPdfFormat.parseMoney(""))
        assertEquals(0.0, OsPdfFormat.parseMoney("   "))
        assertEquals(0.0, OsPdfFormat.parseMoney("abc"))
    }

    @Test
    fun parseMoney_negative() {
        assertEquals(-50.0, OsPdfFormat.parseMoney("-50.00"))
    }

    @Test
    fun parseMoney_integerOnly() {
        assertEquals(1000.0, OsPdfFormat.parseMoney("1000"))
        // Domínio sempre usa decimais explícitos ("1000.00"); um valor com
        // separador de milhar + decimal é resolvido pelo ÚLTIMO separador:
        assertEquals(1000.0, OsPdfFormat.parseMoney("1.000,00"))
    }

    // --- OsPdfFormat.money ----------------------------------------------------

    @Test
    fun money_formatsBRL() {
        assertEquals("R$ 320,00", OsPdfFormat.money("320.00"))
        assertEquals("R$ 1.234,56", OsPdfFormat.money("1234.56"))
        assertEquals("R$ 0,00", OsPdfFormat.money("0.00"))
    }

    // --- OsPdfFormat.isNonZero -----------------------------------------------

    @Test
    fun isNonZero() {
        assertFalse(OsPdfFormat.isNonZero("0.00"))
        assertFalse(OsPdfFormat.isNonZero(""))
        assertTrue(OsPdfFormat.isNonZero("10.00"))
        assertTrue(OsPdfFormat.isNonZero("0.01"))
    }

    // --- defaultOsPdfFileName -------------------------------------------------

    @Test
    fun defaultFileName_sanitizesAndAddsExtension() {
        val data = sampleData(number = "7", title = "Orcamento Mensal")
        assertEquals("Orcamento_Mensal-7.pdf", defaultOsPdfFileName(data))
    }

    @Test
    fun defaultFileName_handlesSpecialChars() {
        val data = sampleData(number = "OS/2026 #7", title = "Orçamento")
        val name = defaultOsPdfFileName(data)
        assertTrue(name.endsWith(".pdf"))
        assertFalse(name.contains('/'))
        assertFalse(name.contains('#'))
        assertFalse(name.contains(' '))
    }

    // --- Model: watermark default + total fonte de verdade --------------------

    @Test
    fun model_watermarkDefaultFalse() {
        val data = sampleData(number = "1", title = "OS")
        assertFalse(data.watermark)
    }

    @Test
    fun company_equalsConsidersLogoBytes() {
        val a = OsPdfCompany(name = "ACME", logoBytes = byteArrayOf(1, 2, 3))
        val b = OsPdfCompany(name = "ACME", logoBytes = byteArrayOf(1, 2, 3))
        val c = OsPdfCompany(name = "ACME", logoBytes = byteArrayOf(9, 9))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == c)
    }

    private fun sampleData(number: String, title: String) = OsPdfData(
        company = OsPdfCompany(name = "ACME Serviços", phone = "+5511999990000"),
        number = number,
        title = title,
        client = OsPdfClient(name = "João Silva"),
        items = listOf(
            OsPdfItem("Disjuntor", 1, "120.00", "120.00"),
            OsPdfItem("Mão de obra", 1, "200.00", "200.00"),
        ),
        total = "320.00",
    )
}
