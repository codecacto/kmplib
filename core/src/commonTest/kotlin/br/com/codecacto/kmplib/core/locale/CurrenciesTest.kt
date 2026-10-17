package br.com.codecacto.kmplib.core.locale

import br.com.codecacto.kmplib.core.currency.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Independente da região de quem roda o teste: confere algarismos, presença e regra, não o texto exato. */
class CurrenciesTest {

    @Test
    fun `moeda padrao por regiao`() {
        assertEquals("BRL", defaultCurrencyForRegion("BR"))
        assertEquals("BRL", defaultCurrencyForRegion("br"))
        assertEquals("USD", defaultCurrencyForRegion("US"))
        assertEquals("EUR", defaultCurrencyForRegion("PT"))
        assertEquals("EUR", defaultCurrencyForRegion("DE"))
        assertEquals("GBP", defaultCurrencyForRegion("GB"))
        assertEquals("JPY", defaultCurrencyForRegion("JP"))
    }

    @Test
    fun `regiao desconhecida cai no USD e nunca no BRL`() {
        assertEquals(FALLBACK_CURRENCY, "USD")
        assertEquals("USD", defaultCurrencyForRegion(null))
        assertEquals("USD", defaultCurrencyForRegion(""))
        assertEquals("USD", defaultCurrencyForRegion("419"))
        assertEquals("USD", defaultCurrencyForRegion("BRA"))
        assertEquals("USD", defaultCurrencyForRegion("AQ")) // Antártida: sem moeda própria
    }

    @Test
    fun `validacao de codigo exige formato e moeda conhecida`() {
        assertTrue(isValidCurrencyCode("BRL"))
        assertTrue(isValidCurrencyCode("JPY"))
        assertTrue(isValidCurrencyCode("EUR"))
        assertFalse(isValidCurrencyCode("brl"))
        assertFalse(isValidCurrencyCode("ZZZ"))
        assertFalse(isValidCurrencyCode("R$"))
        assertFalse(isValidCurrencyCode(""))
        assertFalse(isValidCurrencyCode(null))
    }

    @Test
    fun `formatMoney respeita a escala de cada moeda`() {
        val brl = RegionalFormat.formatMoney(288000, "BRL")
        assertEquals("288000", brl.filter { it.isDigit() })
        assertTrue(RegionalFormat.decimalSeparator() in brl, brl)
        assertEquals("2880", RegionalFormat.formatMoney(288000, "BRL", showCentsWhenWhole = false).filter { it.isDigit() })
        // Valor com fração mantém as casas mesmo sem centavos em inteiros.
        assertEquals("288050", RegionalFormat.formatMoney(288050, "BRL", showCentsWhenWhole = false).filter { it.isDigit() })
        // JPY: 0 casas — 2880 unidades menores = 2.880 ienes.
        assertEquals("2880", RegionalFormat.formatMoney(2880, "JPY").filter { it.isDigit() })
        assertEquals("1500", RegionalFormat.formatMoney(1500, "KWD").filter { it.isDigit() })
        assertTrue(RegionalFormat.formatMoney(-50, "USD").contains('-') || RegionalFormat.formatMoney(-50, "USD").contains('('))
        assertEquals("92233720368547758.07".filter { it.isDigit() }, RegionalFormat.formatMoney(Long.MAX_VALUE, "BRL").filter { it.isDigit() })
        assertEquals(RegionalFormat.formatMoney(288000, "BRL"), Money(288000, "BRL").format())
        assertEquals(RegionalFormat.formatMoney(288000, "BRL", false), RegionalFormat.formatMoney(Money(288000, "BRL"), false))
    }

    @Test
    fun `codigo malformado lanca e desconhecido nao derruba a tela`() {
        assertFailsWith<IllegalArgumentException> { RegionalFormat.formatMoney(1, "brl") }
        assertFailsWith<IllegalArgumentException> { RegionalFormat.currencySymbol("R$") }
        val desconhecida = RegionalFormat.formatMoney(-288050, "ZZZ")
        assertTrue(desconhecida.startsWith("-ZZZ "), desconhecida)
        assertEquals("288050", desconhecida.filter { it.isDigit() })
        assertEquals("ZZZ 2880", RegionalFormat.formatMoney(288000, "ZZZ", false).replace(RegionalFormat.groupingSeparator().toString(), ""))
        assertEquals("ZZZ", RegionalFormat.currencySymbol("ZZZ"))
    }

    @Test
    fun `simbolo existe para as moedas comuns`() {
        listOf("BRL", "USD", "EUR", "GBP", "JPY").forEach {
            assertTrue(RegionalFormat.currencySymbol(it).isNotBlank(), it)
        }
        assertTrue("R$" in RegionalFormat.currencySymbol("BRL") || RegionalFormat.currencySymbol("BRL") == "BRL")
    }

    @Test
    fun `lista de moedas em circulacao com nome e sem historicas`() {
        val todas = Currencies.all("pt-BR")
        val codigos = todas.map { it.code }
        assertTrue("BRL" in codigos && "USD" in codigos && "EUR" in codigos && "JPY" in codigos)
        assertFalse("XAU" in codigos)
        assertFalse("BRZ" in codigos) // cruzeiro, histórica
        assertEquals(codigos.size, codigos.toSet().size)
        assertTrue(todas.all { it.name.isNotBlank() && it.name.first().isUpperCase() || !it.name.first().isLetter() })
        assertNotNull(Currencies.info("brl", "pt-BR"))
        assertNull(Currencies.info("ZZZ", "pt-BR"))
        assertNull(Currencies.name("ZZZ", "pt-BR"))
        assertNotNull(Currencies.name("EUR", "en"))
    }

    @Test
    fun `preferidas vem primeiro com a da regiao na frente`() {
        assertEquals(listOf("JPY", "BRL", "USD", "EUR", "GBP"), Currencies.preferredCodes("JP"))
        assertEquals(listOf("EUR", "BRL", "USD", "GBP"), Currencies.preferredCodes("PT"))
        assertEquals(listOf("BRL", "USD", "EUR", "GBP"), Currencies.preferredCodes("BR"))
        assertEquals(COMMON_CURRENCIES, Currencies.preferredCodes(null))
        assertEquals(COMMON_CURRENCIES, Currencies.preferredCodes("419"))

        val lista = listOf(
            CurrencyInfo("ARS", "Peso argentino", "ARS"),
            CurrencyInfo("BRL", "Real brasileiro", "R$"),
            CurrencyInfo("EUR", "Euro", "€"),
            CurrencyInfo("JPY", "Iene japonês", "¥"),
            CurrencyInfo("USD", "Dólar americano", "US$"),
        )
        assertEquals(
            listOf("JPY", "BRL", "USD", "EUR", "ARS"),
            Currencies.ordered("JP", lista, Currencies.preferredCodes("JP")).map { it.code },
        )
        assertEquals(listOf("BRL", "USD", "EUR", "ARS", "JPY"), Currencies.ordered(null, lista, COMMON_CURRENCIES).map { it.code })
    }

    @Test
    fun `busca por pedaco de nome codigo e simbolo sem acento`() {
        val lista = listOf(
            CurrencyInfo("BRL", "Real brasileiro", "R$"),
            CurrencyInfo("EUR", "Euro", "€"),
            CurrencyInfo("JPY", "Iene japonês", "¥"),
            CurrencyInfo("USD", "Dólar americano", "US$"),
        )
        assertEquals(listOf("BRL"), Currencies.search("real", lista).map { it.code })
        assertEquals(listOf("EUR"), Currencies.search("eur", lista).map { it.code })
        assertEquals(listOf("JPY"), Currencies.search("japones", lista).map { it.code })
        assertEquals(listOf("USD"), Currencies.search("dolar amer", lista).map { it.code })
        assertEquals(listOf("BRL", "USD"), Currencies.search("$", lista).map { it.code })
        assertEquals(lista, Currencies.search("  ", lista))
        assertTrue(Currencies.search("xyzw", lista).isEmpty())
    }
}
