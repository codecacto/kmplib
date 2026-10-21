package br.com.codecacto.kmplib.ui.screens.paywall

import br.com.codecacto.kmplib.monetization.entitlement.Plan
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackage
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackageType
import br.com.codecacto.kmplib.monetization.purchase.StoreReplacementMode
import br.com.codecacto.kmplib.monetization.purchase.SubscriptionChangeDirection
import br.com.codecacto.kmplib.monetization.purchase.normalizeStoreProductId
import br.com.codecacto.kmplib.monetization.purchase.resolveUpgradeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val V3 = PaywallTier("v3", "Até 3 vigias", order = 1, highlights = listOf("3 vigias"))
private val V10 = PaywallTier("v10", "Até 10 vigias", order = 2)
private val V25 = PaywallTier("v25", "Até 25 vigias", order = 3)
private val TIERS = listOf(V25, V3, V10) // fora de ordem de propósito

private fun tierPkg(tier: String, cycle: String, months: Int, micros: Long, viaProductOnly: Boolean = false) =
    PurchasePackage(
        packageId = if (viaProductOnly) "\$rc_custom_${tier}_$cycle" else "${tier}_$cycle",
        packageType = when (months) {
            1 -> PurchasePackageType.MONTHLY
            6 -> PurchasePackageType.SIX_MONTH
            else -> PurchasePackageType.ANNUAL
        },
        storeProductId = "premium_${tier}_${cycle}_rondaki:$cycle",
        priceLabel = "R$ ${micros / 1_000_000}",
        priceAmountMicros = micros,
        currencyCode = "BRL",
        durationMonths = months,
    )

/** A escada do provisionador (`degraus:` no `monetizacao.yaml`): regras puras de degrau e troca. */
class PaywallTierTest {

    @Test
    fun `package e productId da convencao do provisionador viram degrau e ciclo`() {
        val ids = TIERS.map { it.id }
        assertEquals(PaywallTierRef("v3", PaywallTierCycle.MONTHLY), PaywallTierKeys.fromPackageId("v3_mensal", ids))
        assertEquals(PaywallTierRef("v10", PaywallTierCycle.YEARLY), PaywallTierKeys.fromPackageId("V10_ANUAL", ids))
        assertEquals(
            PaywallTierRef("v25", PaywallTierCycle.SEMIANNUAL),
            PaywallTierKeys.fromProductId("premium_v25_semestral_rondaki:semestral", ids),
        )
        assertEquals(PaywallTierRef("v3", PaywallTierCycle.MONTHLY), PaywallTierKeys.fromProductId("premium_v3_mensal", ids))
    }

    @Test
    fun `id inteiro nunca casa por prefixo solto`() {
        val ids = listOf("v3")
        assertNull(PaywallTierKeys.fromPackageId("v30_mensal", ids))
        assertNull(PaywallTierKeys.fromProductId("premium_v30_mensal_app", ids))
        assertNull(PaywallTierKeys.fromProductId("premium_v3_mensalidade_app", ids))
        assertNull(PaywallTierKeys.fromPackageId("v3_trimestral", ids))
        assertNull(PaywallTierKeys.fromPackageId("\$rc_monthly", ids))
    }

    @Test
    fun `plano central pela chave ou pelo storeProductId`() {
        val ids = TIERS.map { it.id }
        val porChave = Plan(plano = "v10_semestral", nome = "Semestral", durationMonths = 6)
        val porProduto = Plan(plano = "qualquer", nome = "Anual", storeProductId = "premium_v25_anual_rondaki", durationMonths = 12)
        assertEquals(PaywallTierRef("v10", PaywallTierCycle.SEMIANNUAL), PaywallTierKeys.of(porChave, ids))
        assertEquals(PaywallTierRef("v25", PaywallTierCycle.YEARLY), PaywallTierKeys.of(porProduto, ids))
    }

    @Test
    fun `vitrine por degrau ordena degraus e ciclos e deriva o selo de cada degrau`() {
        val pacotes = listOf(
            tierPkg("v10", "anual", 12, 900_000_000), tierPkg("v3", "mensal", 1, 30_000_000),
            tierPkg("v10", "mensal", 1, 100_000_000), tierPkg("v3", "semestral", 6, 150_000_000),
            // v25 sem semestral e sem anual: o selo cai no mensal dele.
            tierPkg("v25", "mensal", 1, 200_000_000),
            // pacote fora da escada: não entra em degrau nenhum.
            tierPkg("x", "mensal", 1, 1_000_000).copy(packageId = "\$rc_monthly", storeProductId = "premium_mensal"),
        )
        val porDegrau = pacotes.toPaywallTierPlansFromStore(TIERS, highlights = { listOf("geral") })

        assertEquals(listOf("v3", "v10", "v25"), porDegrau.map { it.tier.id })
        val v3 = porDegrau[0].plans
        assertEquals(listOf(1, 6), v3.map { it.durationMonths })
        assertEquals(listOf("v3_semestral"), v3.filter { it.isRecommended }.map { it.id })
        assertEquals(listOf("3 vigias"), v3.first().highlights, "benefício do degrau vence o geral")
        assertTrue(v3.all { it.tierId == "v3" })
        val v10 = porDegrau[1].plans
        assertEquals(listOf(1, 12), v10.map { it.durationMonths })
        assertEquals(listOf("v10_anual"), v10.filter { it.isRecommended }.map { it.id })
        assertEquals(listOf("geral"), v10.first().highlights)
        assertEquals(listOf("v25_mensal"), porDegrau[2].plans.filter { it.isRecommended }.map { it.id })
    }

    @Test
    fun `pacote reconhecido so pelo productId tambem entra no degrau`() {
        val porDegrau = listOf(tierPkg("v3", "mensal", 1, 30_000_000, viaProductOnly = true))
            .toPaywallTierPlansFromStore(TIERS)
        assertEquals(1, porDegrau.first { it.tier.id == "v3" }.plans.size)
    }

    @Test
    fun `oferta central casa o plano com o pacote DO MESMO degrau`() {
        val pacotes = listOf(tierPkg("v3", "mensal", 1, 30_000_000), tierPkg("v10", "mensal", 1, 100_000_000))
        val central = listOf(
            Plan(plano = "v10_mensal", nome = "Mensal 10", durationMonths = 1, destaques = listOf("dez")),
            Plan(plano = "v3_mensal", nome = "Mensal 3", durationMonths = 1),
        )
        val porDegrau = central.toPaywallTierPlans(pacotes, TIERS)
        val v10 = porDegrau.first { it.tier.id == "v10" }.plans.single()
        assertEquals("v10_mensal", v10.id)
        assertEquals("R$ 100", v10.priceLabel)
        assertEquals(listOf("dez"), v10.highlights)
        assertEquals(listOf("3 vigias"), porDegrau.first { it.tier.id == "v3" }.plans.single().highlights)
        assertTrue(porDegrau.first { it.tier.id == "v25" }.plans.isEmpty())
    }

    @Test
    fun `degrau inicial segue a regra a partir do degrau atual`() {
        val disponiveis = listOf(V3, V10, V25)
        val regra = PaywallInitialTier.NEXT_ABOVE_CURRENT
        assertEquals("v3", initialPaywallTier(disponiveis, null, regra))
        assertEquals("v3", initialPaywallTier(disponiveis, "free", regra))
        assertEquals("v10", initialPaywallTier(disponiveis, "v3", regra))
        assertEquals("v25", initialPaywallTier(disponiveis, "V10", regra))
        assertEquals("v25", initialPaywallTier(disponiveis, "v25", regra), "no topo fica no topo")
        assertEquals("v10", initialPaywallTier(disponiveis, "v10", PaywallInitialTier.CURRENT))
        assertEquals("v3", initialPaywallTier(disponiveis, "v25", PaywallInitialTier.LOWEST))
        // Degrau atual fora da vitrine: o primeiro acima dele.
        assertEquals("v25", initialPaywallTier(listOf(V3, V25), "v10", regra, allTiers = TIERS))
        assertNull(initialPaywallTier(emptyList(), "v3", regra))
    }

    @Test
    fun `troca sobe desce ou muda o ciclo pela ordem dos degraus`() {
        val modos = PaywallTierChangeModes()
        val v3m = tierPkg("v3", "mensal", 1, 30_000_000)
        val v10m = tierPkg("v10", "mensal", 1, 100_000_000)
        val v3a = tierPkg("v3", "anual", 12, 300_000_000)
        fun ref(p: PurchasePackage) = PaywallTierKeys.of(p, TIERS.map { it.id })

        val sobe = decidePaywallTierChange(ref(v3m), ref(v10m)!!, TIERS, modos, v3m, v10m)
        assertEquals(SubscriptionChangeDirection.UPGRADE, sobe.direction)
        assertEquals(StoreReplacementMode.CHARGE_PRORATED_PRICE, sobe.replacementMode)

        val desce = decidePaywallTierChange(ref(v10m), ref(v3m)!!, TIERS, modos, v10m, v3m)
        assertEquals(SubscriptionChangeDirection.DOWNGRADE, desce.direction)
        assertEquals(StoreReplacementMode.DEFERRED, desce.replacementMode)

        val ciclo = decidePaywallTierChange(ref(v3m), ref(v3a)!!, TIERS, modos, v3m, v3a)
        assertEquals(SubscriptionChangeDirection.CROSSGRADE, ciclo.direction)
        assertEquals(StoreReplacementMode.DEFERRED, ciclo.replacementMode)

        val desconhecido = decidePaywallTierChange(null, ref(v10m)!!, TIERS, modos, null, v10m)
        assertNull(desconhecido.direction)
        assertEquals(StoreReplacementMode.WITH_TIME_PRORATION, desconhecido.replacementMode)
    }

    @Test
    fun `upgrade cujo preco por mes nao sobe cai para WITH_TIME_PRORATION`() {
        val v3Mensal = tierPkg("v3", "mensal", 1, 30_000_000)
        val v10AnualBarato = tierPkg("v10", "anual", 12, 240_000_000) // 20/mês < 30/mês
        val v10AnualCaro = tierPkg("v10", "anual", 12, 600_000_000) // 50/mês
        val pref = StoreReplacementMode.CHARGE_PRORATED_PRICE
        assertEquals(StoreReplacementMode.WITH_TIME_PRORATION, resolveUpgradeMode(pref, v3Mensal, v10AnualBarato))
        assertEquals(pref, resolveUpgradeMode(pref, v3Mensal, v10AnualCaro))
        assertEquals(StoreReplacementMode.WITH_TIME_PRORATION, resolveUpgradeMode(pref, null, v10AnualCaro))
        assertEquals(
            StoreReplacementMode.WITH_TIME_PRORATION,
            resolveUpgradeMode(pref, v3Mensal.copy(currencyCode = "USD"), v10AnualCaro),
        )
        assertEquals(StoreReplacementMode.CHARGE_FULL_PRICE, resolveUpgradeMode(StoreReplacementMode.CHARGE_FULL_PRICE, null, v10AnualBarato))
    }

    @Test
    fun `id de produto do Play perde o plano base para comparar`() {
        assertEquals("premium_v3_mensal_rondaki", normalizeStoreProductId("premium_v3_mensal_rondaki:mensal"))
        assertEquals("premium_v3_mensal_rondaki", normalizeStoreProductId(" premium_v3_mensal_rondaki "))
    }

    @Test
    fun `chip do degrau atual ganha o rotulo atual`() {
        val texts = PaywallTexts()
        assertEquals("Até 3 vigias (atual)", texts.tierLabel(PaywallTierOption("v3", "Até 3 vigias", isCurrent = true)))
        assertEquals("Até 3 vigias", texts.tierLabel(PaywallTierOption("v3", "Até 3 vigias")))
        assertEquals("paywall-degrau-v10", PaywallTestTags.degrau("v10"))
    }
}
