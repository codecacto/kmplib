package br.com.codecacto.kmplib.core.currency

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoneyContractTest {

    @Test
    fun `casas decimais seguem a ISO 4217`() {
        assertEquals(2, Money.fractionDigitsOf("BRL"))
        assertEquals(2, Money.fractionDigitsOf("USD"))
        assertEquals(2, Money.fractionDigitsOf("EUR"))
        assertEquals(0, Money.fractionDigitsOf("JPY"))
        assertEquals(0, Money.fractionDigitsOf("KRW"))
        assertEquals(0, Money.fractionDigitsOf("CLP"))
        assertEquals(3, Money.fractionDigitsOf("KWD"))
        assertEquals(3, Money.fractionDigitsOf("IQD"))
        assertEquals(4, Money.fractionDigitsOf("CLF"))
        assertEquals(0, Money.fractionDigitsOf("XAU"))
        assertEquals(2, Money.Companion.fractionDigitsOf("ZZZ"))
    }

    @Test
    fun `amount sai com exatamente as casas da moeda`() {
        assertEquals("2880.00", Money(288000, "BRL").toAmountString())
        assertEquals("2880", Money(2880, "JPY").toAmountString())
        assertEquals("1.500", Money(1500, "KWD").toAmountString())
        assertEquals("0.05", Money(5, "USD").toAmountString())
        assertEquals("-0.50", Money(-50, "EUR").toAmountString())
        assertEquals("0.00", Money.zero("BRL").toAmountString())
        assertEquals("1.2345", Money(12345, "CLF").toAmountString())
        assertEquals("-92233720368547758.08", Money(Long.MIN_VALUE, "BRL").toAmountString())
        assertEquals("2880.00 BRL", Money(288000, "BRL").toString())
    }

    @Test
    fun `parse aceita o contrato e menos casas`() {
        assertEquals(Money(288000, "BRL"), Money.parse("2880.00", "BRL"))
        assertEquals(Money(288000, "BRL"), Money.parse("2880", "BRL"))
        assertEquals(Money(288050, "BRL"), Money.parse("2880.5", "BRL"))
        assertEquals(Money(2880, "JPY"), Money.parse("2880", "JPY"))
        assertEquals(Money(2880, "JPY"), Money.parse("2880.00", "JPY"))
        assertEquals(Money(-50, "USD"), Money.parse("-0.50", "USD"))
        assertEquals(Money(0, "USD"), Money.parse("-0.00", "USD"))
        assertEquals(Money(1500, "KWD"), Money.parse("1.5", "KWD"))
        assertEquals(Money(288000, "BRL"), Money.parse("2880.000", "BRL"))
        assertEquals(Money(5, "BRL"), Money.parse("000.05", "BRL"))
    }

    @Test
    fun `parse recusa o que nao e contrato`() {
        listOf("", " ", "2.880,00", "2,880.00", "2880,00", "1e3", "+10", "10.", ".5", "abc", "R$ 10", "2880.005")
            .forEach { assertNull(Money.parseOrNull(it, "BRL"), "deveria recusar \"$it\"") }
        assertNull(Money.parseOrNull("2880.5", "JPY"))
        assertNull(Money.parseOrNull("99999999999999999999", "BRL"))
        assertNull(Money.parseOrNull("10.00", "brl"))
        assertFailsWith<IllegalArgumentException> { Money.parse("x", "BRL") }
    }

    @Test
    fun `codigo de moeda malformado e recusado`() {
        assertFailsWith<IllegalArgumentException> { Money(1, "brl") }
        assertFailsWith<IllegalArgumentException> { Money(1, "BR") }
        assertFailsWith<IllegalArgumentException> { Money(1, "BRLL") }
        assertFailsWith<IllegalArgumentException> { Money(1, "B1L") }
        assertFailsWith<IllegalArgumentException> { Money.ofMajor(1, "R$") }
        assertTrue(isIsoCurrencyCodeFormat("BRL"))
        assertFalse(isIsoCurrencyCodeFormat(null))
        assertFalse(isIsoCurrencyCodeFormat("Brl"))
    }

    @Test
    fun `aritmetica exata na mesma moeda`() {
        val a = Money.parse("10.10", "BRL")
        val b = Money.parse("0.20", "BRL")
        assertEquals(Money.parse("10.30", "BRL"), a + b)
        assertEquals(Money.parse("9.90", "BRL"), a - b)
        assertEquals(Money.parse("-10.10", "BRL"), -a)
        assertEquals(Money.parse("30.30", "BRL"), a * 3)
        assertEquals(Money.parse("30.30", "BRL"), a * 3L)
        assertEquals(Money.ofMajor(2880, "BRL"), Money(288000, "BRL"))
        assertEquals(Money.ofMajor(2880, "JPY"), Money(2880, "JPY"))
        assertTrue(a > b)
        assertTrue(Money.zero("BRL").isZero)
        assertTrue((-a).isNegative)
        assertTrue(Money(288000, "BRL").isWhole)
        assertFalse(Money(288050, "BRL").isWhole)
        assertTrue(Money(7, "JPY").isWhole)
        assertNotEquals(Money(100, "BRL"), Money(100, "USD"))
        assertEquals(Money(100, "BRL").hashCode(), Money(100, "BRL").hashCode())
        assertFalse(Money(100, "BRL").equals("100"))
    }

    @Test
    fun `moedas diferentes nao se somam nem se comparam`() {
        assertFailsWith<IllegalArgumentException> { Money(1, "BRL") + Money(1, "USD") }
        assertFailsWith<IllegalArgumentException> { Money(1, "BRL") - Money(1, "USD") }
        assertFailsWith<IllegalArgumentException> { Money(1, "BRL").compareTo(Money(1, "USD")) }
    }

    @Test
    fun `estouro lanca em vez de dar a volta`() {
        assertFailsWith<ArithmeticException> { Money(Long.MAX_VALUE, "BRL") + Money(1, "BRL") }
        assertFailsWith<ArithmeticException> { Money(Long.MIN_VALUE, "BRL") - Money(1, "BRL") }
        assertFailsWith<ArithmeticException> { -Money(Long.MIN_VALUE, "BRL") }
        assertFailsWith<ArithmeticException> { Money(Long.MAX_VALUE, "BRL") * 2 }
        assertFailsWith<ArithmeticException> { Money(-1, "BRL") * Long.MIN_VALUE }
        assertFailsWith<ArithmeticException> { Money(Long.MIN_VALUE, "BRL") * -1L }
        assertFailsWith<ArithmeticException> { Money.ofMajor(Long.MAX_VALUE, "BRL") }
        assertEquals(Money(0, "BRL"), Money(0, "BRL") * Long.MAX_VALUE)
    }

    @Serializable
    private data class Pedido(val total: Money, val desconto: Money? = null)

    @Test
    fun `serializa no contrato da fabrica`() {
        val json = Json
        assertEquals(
            """{"amount":"2880.00","currency":"BRL"}""",
            json.encodeToString(Money.serializer(), Money(288000, "BRL")),
        )
        assertEquals(
            """{"amount":"2880","currency":"JPY"}""",
            json.encodeToString(Money.serializer(), Money(2880, "JPY")),
        )
        assertEquals(
            Money(288000, "BRL"),
            json.decodeFromString(Money.serializer(), """{"currency":"BRL","amount":"2880.00"}"""),
        )
        val pedido = Pedido(Money(1990, "USD"))
        val texto = json.encodeToString(Pedido.serializer(), pedido)
        assertEquals("""{"total":{"amount":"19.90","currency":"USD"}}""", texto)
        assertEquals(pedido, json.decodeFromString(Pedido.serializer(), texto))
    }

    @Test
    fun `desserializacao recusa fora do contrato`() {
        val json = Json
        listOf(
            """{"amount":2880.00,"currency":"BRL"}""",
            """{"amount":"2880,00","currency":"BRL"}""",
            """{"amount":"2880.001","currency":"BRL"}""",
            """{"amount":"10","currency":"brl"}""",
            """{"amount":"10"}""",
        ).forEach { entrada ->
            assertFailsWith<SerializationException>(entrada) { json.decodeFromString(Money.serializer(), entrada) }
        }
    }
}
