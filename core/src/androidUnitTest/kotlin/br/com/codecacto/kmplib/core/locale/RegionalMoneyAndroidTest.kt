package br.com.codecacto.kmplib.core.locale

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O texto EXATO por região, no formatador oficial (`NumberFormat.getCurrencyInstance` + `Currency`).
 * Espaço não separável (U+00A0/U+202F) é normalizado — o CLDR troca o tipo de espaço entre versões.
 */
class RegionalMoneyAndroidTest {
    private lateinit var anterior: Locale

    @BeforeTest fun guarda() { anterior = Locale.getDefault() }

    @AfterTest fun devolve() { Locale.setDefault(anterior) }

    private fun em(tag: String, bloco: () -> String): String {
        Locale.setDefault(Locale.forLanguageTag(tag))
        return bloco().replace(' ', ' ').replace(' ', ' ')
    }

    @Test
    fun `real no Brasil com e sem centavos`() {
        assertEquals("R$ 2.880,00", em("pt-BR") { RegionalFormat.formatMoney(288000, "BRL") })
        assertEquals("R$ 2.880", em("pt-BR") { RegionalFormat.formatMoney(288000, "BRL", showCentsWhenWhole = false) })
        assertEquals("R$ 2.880,50", em("pt-BR") { RegionalFormat.formatMoney(288050, "BRL", showCentsWhenWhole = false) })
        assertEquals("R$ 0,05", em("pt-BR") { RegionalFormat.formatMoney(5, "BRL") })
        assertEquals("R$", em("pt-BR") { RegionalFormat.currencySymbol("BRL") })
    }

    @Test
    fun `dolar nos EUA e no Brasil`() {
        assertEquals("$2,880.00", em("en-US") { RegionalFormat.formatMoney(288000, "USD") })
        assertEquals("$2,880", em("en-US") { RegionalFormat.formatMoney(288000, "USD", false) })
        assertEquals("$", em("en-US") { RegionalFormat.currencySymbol("USD") })
        assertEquals("US$ 2.880,00", em("pt-BR") { RegionalFormat.formatMoney(288000, "USD") })
    }

    @Test
    fun `euro na Alemanha e iene no Japao`() {
        assertEquals("2.880,00 €", em("de-DE") { RegionalFormat.formatMoney(288000, "EUR") })
        val iene = em("ja-JP") { RegionalFormat.formatMoney(2880, "JPY") }
        assertEquals("2,880", iene.drop(1))
        assertEquals(iene, em("ja-JP") { RegionalFormat.formatMoney(2880, "JPY", showCentsWhenWhole = true) })
    }

    @Test
    fun `dinar do Kuwait com tres casas e valor enorme exato`() {
        assertEquals("KWD 1.500", em("en-US") { RegionalFormat.formatMoney(1500, "KWD") }.replace("KWD1", "KWD 1"))
        assertEquals("R$ 92.233.720.368.547.758,07", em("pt-BR") { RegionalFormat.formatMoney(Long.MAX_VALUE, "BRL") })
    }

    @Test
    fun `nomes no idioma pedido`() {
        assertEquals("Real brasileiro", Currencies.name("BRL", "pt-BR"))
        assertEquals("Brazilian Real", Currencies.name("BRL", "en"))
        assertEquals("Euro", Currencies.name("EUR", "pt-BR"))
    }
}
