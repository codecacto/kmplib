package br.com.codecacto.kmplib.ui.screens.paywall

import br.com.codecacto.kmplib.monetization.purchase.PurchasePackage
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaywallSavingsTest {

    private fun pkg(id: String, months: Int, micros: Long, currency: String = "BRL") = PurchasePackage(
        packageId = id,
        packageType = PurchasePackageType.OTHER,
        storeProductId = id,
        priceLabel = "x",
        priceAmountMicros = micros,
        currencyCode = currency,
        durationMonths = months,
    )

    private fun plan(id: String, months: Int) =
        PaywallPlan(id = id, name = id, priceLabel = "x", durationMonths = months)

    @Test
    fun `percentual arredonda para baixo`() {
        assertEquals(16, savingsPercent(10_000_000L, 8_333_333L))
        assertEquals(25, savingsPercent(10_000_000L, 7_500_000L))
    }

    @Test
    fun `plano igual ou mais caro nao economiza`() {
        assertNull(savingsPercent(10_000_000L, 10_000_000L))
        assertNull(savingsPercent(10_000_000L, 12_000_000L))
        assertNull(savingsPercent(0L, 1L))
    }

    @Test
    fun `sem mensal nao ha selo mas o preco por mes continua`() {
        val plans = listOf(plan("s", 6), plan("a", 12)).withStoreSavings(
            packages = listOf(pkg("s", 6, 60_000_000L), pkg("a", 12, 96_000_000L)),
            badgeLabel = { "E$it" },
            pricePerMonthLabel = { micros, _ -> "${micros / 1_000_000}" },
        )
        assertEquals(listOf(null, null), plans.map { it.badgeLabel })
        assertEquals(listOf("10", "8"), plans.map { it.pricePerMonthLabel })
    }

    @Test
    fun `moeda diferente da do mensal nao e comparada`() {
        val plans = listOf(plan("m", 1), plan("a", 12)).withStoreSavings(
            packages = listOf(pkg("m", 1, 10_000_000L, "BRL"), pkg("a", 12, 60_000_000L, "USD")),
            badgeLabel = { "E$it" },
        )
        assertNull(plans.last().badgeLabel)
    }

    @Test
    fun `plano sem pacote passa intacto e nada ligado devolve a mesma lista`() {
        val original = listOf(plan("m", 1), plan("fantasma", 12))
        val withBadge = original.withStoreSavings(listOf(pkg("m", 1, 10_000_000L)), badgeLabel = { "E$it" })
        assertEquals(original, withBadge)
        assertEquals(original, original.withStoreSavings(emptyList(), badgeLabel = null))
    }

    @Test
    fun `mensagem padrao poe o sinal no argumento`() {
        assertEquals("Economize 33%", PaywallMessages().savingsLabel(33))
    }
}
