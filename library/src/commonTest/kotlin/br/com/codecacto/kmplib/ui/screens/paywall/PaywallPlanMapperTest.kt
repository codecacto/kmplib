package br.com.codecacto.kmplib.ui.screens.paywall

import br.com.codecacto.kmplib.monetization.entitlement.Plan
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackage
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// O overload por `priceLabelProvider` (legado) esta @Deprecated; ainda testado para regressao.
@Suppress("DEPRECATION")
class PaywallPlanMapperTest {

    private fun plan(
        plano: String,
        nome: String,
        storeProductId: String?,
        durationMonths: Int?,
        tipo: String? = null,
        ativo: Boolean = true,
        destaques: List<String> = emptyList(),
    ) = Plan(
        plano = plano,
        nome = nome,
        storeProductId = storeProductId,
        durationMonths = durationMonths,
        tipo = tipo,
        ativo = ativo,
        destaques = destaques,
    )

    /** Preco sempre da loja: aqui um fake que devolve preco para os produtos conhecidos. */
    private val storePrices = mapOf(
        "premium_mensal" to "R$ 9,90",
        "premium_semestral" to "R$ 49,90",
        "premium_anual" to "R$ 89,90",
    )

    private fun price(id: String): String? = storePrices[id]

    @Test
    fun ordersByDurationAsc_mensalSemestralAnual() {
        // Entrada fora de ordem; o mapper deve ordenar 1 -> 6 -> 12.
        val plans = listOf(
            plan("premium_anual", "Anual", "premium_anual", 12),
            plan("premium_mensal", "Mensal", "premium_mensal", 1),
            plan("premium_semestral", "Semestral", "premium_semestral", 6),
        )
        val result = plans.toPaywallPlans(priceLabelProvider = ::price)
        assertEquals(listOf("premium_mensal", "premium_semestral", "premium_anual"), result.map { it.id })
    }

    @Test
    fun recommended_isLongestDuration_byDefault() {
        val plans = listOf(
            plan("premium_mensal", "Mensal", "premium_mensal", 1),
            plan("premium_semestral", "Semestral", "premium_semestral", 6),
            plan("premium_anual", "Anual", "premium_anual", 12),
        )
        val result = plans.toPaywallPlans(priceLabelProvider = ::price)
        assertEquals("premium_anual", result.single { it.isRecommended }.id)
    }

    @Test
    fun recommended_canBeForcedByCaller() {
        val plans = listOf(
            plan("premium_mensal", "Mensal", "premium_mensal", 1),
            plan("premium_anual", "Anual", "premium_anual", 12),
        )
        val result = plans.toPaywallPlans(
            priceLabelProvider = ::price,
            recommendedStoreProductId = "premium_mensal",
        )
        assertEquals("premium_mensal", result.single { it.isRecommended }.id)
    }

    @Test
    fun omitsPlan_withoutStorePrice() {
        // Plano sem preco da loja (semestral nao retornado) e OMITIDO — nada de "—" persistente.
        val plans = listOf(
            plan("premium_mensal", "Mensal", "premium_mensal", 1),
            plan("premium_semestral", "Semestral", "premium_semestral", 6),
        )
        val result = plans.toPaywallPlans(priceLabelProvider = { id ->
            if (id == "premium_mensal") "R$ 9,90" else null
        })
        assertEquals(listOf("premium_mensal"), result.map { it.id })
    }

    @Test
    fun omitsInactive_freeAndMissingFields() {
        val plans = listOf(
            plan("free", "Gratis", null, null),                                 // free
            plan("premium_mensal", "Mensal", "premium_mensal", 1, ativo = false), // inativo
            plan("premium_anual", "Anual", null, 12),                            // sem storeProductId
            plan("premium_x", "X", "premium_x", null),                           // sem durationMonths
            plan("premium_semestral", "Semestral", "premium_semestral", 6),      // valido
        )
        val result = plans.toPaywallPlans(priceLabelProvider = { id ->
            if (id == "premium_semestral") "R$ 49,90" else "R$ 1,00"
        })
        assertEquals(listOf("premium_semestral"), result.map { it.id })
    }

    @Test
    fun priceLabel_comesFromStore_andHighlightsAndDurationCarried() {
        val plans = listOf(
            plan("premium_semestral", "Semestral", "premium_semestral", 6, destaques = listOf("Sem anuncios", "Backup")),
        )
        val result = plans.toPaywallPlans(priceLabelProvider = ::price)
        val p = result.single()
        assertEquals("R$ 49,90", p.priceLabel)
        assertEquals("Semestral", p.name)
        assertEquals("6 meses", p.durationLabel)
        assertEquals(listOf("Sem anuncios", "Backup"), p.highlights)
    }

    @Test
    fun defaultDurationLabel_coversCanonicalTypes_noTrimestral() {
        assertEquals("1 mes", defaultDurationLabel(1))
        assertEquals("6 meses", defaultDurationLabel(6))
        assertEquals("1 ano", defaultDurationLabel(12))
    }

    @Test
    fun emptyInput_yieldsEmptyOutput() {
        val result = emptyList<Plan>().toPaywallPlans(priceLabelProvider = ::price)
        assertTrue(result.isEmpty())
    }

    @Test
    fun durationLabel_canBeNull_whenProviderReturnsNull() {
        val plans = listOf(plan("premium_mensal", "Mensal", "premium_mensal", 1))
        val result = plans.toPaywallPlans(
            priceLabelProvider = ::price,
            durationLabel = { null },
        )
        assertNull(result.single().durationLabel)
    }

    @Test
    fun omitsPlan_withBlankStoreProductId() {
        // storeProductId em branco ("") deve ser omitido — mesmo comportamento do nulo.
        // Garante que o filtro isNotBlank() e exercitado, nao so a comparacao com null.
        val plans = listOf(
            plan("premium_mensal", "Mensal", "", 1),          // storeProductId vazio -> omitido
            plan("premium_anual", "Anual", "premium_anual", 12), // valido
        )
        val result = plans.toPaywallPlans(priceLabelProvider = ::price)
        assertEquals(listOf("premium_anual"), result.map { it.id })
    }

    @Test
    fun recommended_noneHighlighted_whenRecommendedIdNotInFilteredList() {
        // Se recommendedStoreProductId nao aparece nos planos exibidos (ex.: foi filtrado
        // por inativo/sem preco), nenhum plano deve ser marcado como isRecommended.
        val plans = listOf(
            plan("premium_mensal", "Mensal", "premium_mensal", 1),
            plan("premium_anual", "Anual", "premium_anual", 12),
        )
        val result = plans.toPaywallPlans(
            priceLabelProvider = ::price,
            recommendedStoreProductId = "premium_semestral", // inexistente na lista exibida
        )
        assertTrue(result.none { it.isRecommended })
    }

    // ===== Overload gold-standard: correlacao Plan x PurchasePackage (Offerings/Packages) =====

    private fun pkg(
        packageId: String,
        durationMonths: Int?,
        priceLabel: String,
        type: PurchasePackageType = PurchasePackageType.OTHER,
        storeProductId: String = "store_$packageId",
    ) = PurchasePackage(
        packageId = packageId,
        packageType = type,
        storeProductId = storeProductId,
        priceLabel = priceLabel,
        priceAmountMicros = 0L,
        currencyCode = "BRL",
        durationMonths = durationMonths,
    )

    @Test
    fun packages_correlacionaPorDuracao_idEhPackageId_ePrecoDoPacote() {
        val plans = listOf(
            plan("premium_mensal", "Mensal", "prod_mensal", 1),
            plan("premium_semestral", "Semestral", "prod_semestral", 6, destaques = listOf("Sem anuncios")),
        )
        val packages = listOf(
            pkg("rc_monthly", 1, "R$ 9,90", PurchasePackageType.MONTHLY),
            pkg("rc_six_month", 6, "R$ 49,90", PurchasePackageType.SIX_MONTH),
        )
        val result = plans.toPaywallPlans(packages)
        assertEquals(listOf("rc_monthly", "rc_six_month"), result.map { it.id })
        val semestral = result.single { it.id == "rc_six_month" }
        assertEquals("R$ 49,90", semestral.priceLabel)
        assertEquals("Semestral", semestral.name)
        assertEquals("6 meses", semestral.durationLabel)
        assertEquals(listOf("Sem anuncios"), semestral.highlights)
    }

    @Test
    fun packages_ordenaMensalSemestralAnual_independenteDaOrdemDeEntrada() {
        val plans = listOf(
            plan("premium_anual", "Anual", "prod_anual", 12),
            plan("premium_mensal", "Mensal", "prod_mensal", 1),
            plan("premium_semestral", "Semestral", "prod_semestral", 6),
        )
        val packages = listOf(
            pkg("pkg_anual", 12, "R$ 89,90"),
            pkg("pkg_mensal", 1, "R$ 9,90"),
            pkg("pkg_semestral", 6, "R$ 49,90"),
        )
        val result = plans.toPaywallPlans(packages)
        assertEquals(listOf("pkg_mensal", "pkg_semestral", "pkg_anual"), result.map { it.id })
    }

    @Test
    fun packages_destaqueEhMaiorDuracaoPorDefault() {
        val plans = listOf(
            plan("premium_mensal", "Mensal", "prod_mensal", 1),
            plan("premium_semestral", "Semestral", "prod_semestral", 6),
            plan("premium_anual", "Anual", "prod_anual", 12),
        )
        val packages = listOf(
            pkg("pkg_mensal", 1, "R$ 9,90"),
            pkg("pkg_semestral", 6, "R$ 49,90"),
            pkg("pkg_anual", 12, "R$ 89,90"),
        )
        val result = plans.toPaywallPlans(packages)
        assertEquals("pkg_anual", result.single { it.isRecommended }.id)
    }

    @Test
    fun packages_destaquePodeSerForcadoPorDuracao() {
        val plans = listOf(
            plan("premium_mensal", "Mensal", "prod_mensal", 1),
            plan("premium_anual", "Anual", "prod_anual", 12),
        )
        val packages = listOf(
            pkg("pkg_mensal", 1, "R$ 9,90"),
            pkg("pkg_anual", 12, "R$ 89,90"),
        )
        val result = plans.toPaywallPlans(packages, recommendedDurationMonths = 1)
        assertEquals("pkg_mensal", result.single { it.isRecommended }.id)
    }

    @Test
    fun packages_omitePlanoSemPackageCorrespondente() {
        // Semestral do catalogo sem Package de 6 meses -> omitido (regra "sem preco = omite").
        val plans = listOf(
            plan("premium_mensal", "Mensal", "prod_mensal", 1),
            plan("premium_semestral", "Semestral", "prod_semestral", 6),
        )
        val packages = listOf(pkg("pkg_mensal", 1, "R$ 9,90"))
        val result = plans.toPaywallPlans(packages)
        assertEquals(listOf("pkg_mensal"), result.map { it.id })
    }

    @Test
    fun packages_omiteInativoFreeESemDuracao() {
        val plans = listOf(
            plan("free", "Gratis", "prod_free", null),                         // free
            plan("premium_mensal", "Mensal", "prod_mensal", 1, ativo = false), // inativo
            plan("premium_x", "X", "prod_x", null),                            // sem durationMonths
            plan("premium_anual", "Anual", "prod_anual", 12),                  // valido
        )
        val packages = listOf(
            pkg("pkg_mensal", 1, "R$ 9,90"),
            pkg("pkg_anual", 12, "R$ 89,90"),
        )
        val result = plans.toPaywallPlans(packages)
        assertEquals(listOf("pkg_anual"), result.map { it.id })
    }

    @Test
    fun packages_periodoCustom_casaPorDurationMonthsDerivada() {
        // Package CUSTOM/OTHER cuja durationMonths foi derivada do periodo (ex.: 6) casa com o plano.
        val plans = listOf(plan("premium_semestral", "Semestral", "prod_semestral", 6))
        val packages = listOf(pkg("pkg_custom_6m", 6, "R$ 49,90", PurchasePackageType.OTHER))
        val result = plans.toPaywallPlans(packages)
        assertEquals(listOf("pkg_custom_6m"), result.map { it.id })
        assertEquals("R$ 49,90", result.single().priceLabel)
    }

    @Test
    fun packages_durationLabelPodeSerNulo() {
        val plans = listOf(plan("premium_mensal", "Mensal", "prod_mensal", 1))
        val packages = listOf(pkg("pkg_mensal", 1, "R$ 9,90"))
        val result = plans.toPaywallPlans(packages, durationLabel = { null })
        assertNull(result.single().durationLabel)
    }

    @Test
    fun packages_entradaVaziaResultaVazio() {
        assertTrue(emptyList<Plan>().toPaywallPlans(emptyList()).isEmpty())
        val plans = listOf(plan("premium_mensal", "Mensal", "prod_mensal", 1))
        assertTrue(plans.toPaywallPlans(emptyList()).isEmpty())
    }
}
