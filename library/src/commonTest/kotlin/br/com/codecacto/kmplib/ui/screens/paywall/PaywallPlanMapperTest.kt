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
        preco: String? = null,
    ) = Plan(
        plano = plano,
        nome = nome,
        preco = preco,
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
    fun omitsInactive_freeAndMissingStoreProductId() {
        val plans = listOf(
            plan("free", "Gratis", null, null),                                   // free
            plan("premium_mensal", "Mensal", "premium_mensal", 1, ativo = false), // inativo
            plan("premium_anual", "Anual", null, 12),                             // sem storeProductId
            plan("premium_semestral", "Semestral", "premium_semestral", 6),       // valido
        )
        val result = plans.toPaywallPlans(priceLabelProvider = { id ->
            if (id == "premium_semestral") "R$ 49,90" else "R$ 1,00"
        })
        assertEquals(listOf("premium_semestral"), result.map { it.id })
    }

    @Test
    fun duracaoDesconhecida_naoOmiteOPlano_masOrdenaPorUltimoESemSelo() {
        // Regra (2.69.0): duracao desconhecida = null -> visivel/assinavel, ultimo, inelegivel ao selo.
        val plans = listOf(
            plan("premium_x", "X", "premium_x", null),                      // sem durationMonths
            plan("premium_semestral", "Semestral", "premium_semestral", 6), // valido
        )
        val result = plans.toPaywallPlans(priceLabelProvider = { id ->
            if (id == "premium_semestral") "R$ 49,90" else "R$ 1,00"
        })
        assertEquals(listOf("premium_semestral", "premium_x"), result.map { it.id })
        assertEquals("premium_semestral", result.single { it.isRecommended }.id)
        assertNull(result.last().durationLabel)
    }

    @Test
    fun planoComPrecoZero_eTratadoComoGratis_eOmitido() {
        val plans = listOf(
            plan("basico", "Basico", "basico", 1, preco = "0.00"), // pago? nao: preco zero
            plan("premium_anual", "Anual", "premium_anual", 12, preco = "89.90"),
        )
        val result = plans.toPaywallPlans(priceLabelProvider = { "R$ 1,00" })
        assertEquals(listOf("premium_anual"), result.map { it.id })
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
    fun recommended_forcadoInexistente_caiNoDefaultMaiorDuracao() {
        // Se o recommendedStoreProductId sumiu da oferta (plano desligado no admin), o selo MIGRA
        // sozinho para a maior duracao elegivel — o paywall nunca fica sem selo por hardcode obsoleto.
        val plans = listOf(
            plan("premium_mensal", "Mensal", "premium_mensal", 1),
            plan("premium_anual", "Anual", "premium_anual", 12),
        )
        val result = plans.toPaywallPlans(
            priceLabelProvider = ::price,
            recommendedStoreProductId = "premium_semestral", // inexistente na lista exibida
        )
        assertEquals("premium_anual", result.single { it.isRecommended }.id)
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

    // ===== Selo derivado (2.69.0) — paridade com a familia de bugs achada na weblib 0.58.0 =====

    @Test
    fun packages_lifetimeResidual_naoRoubaOSeloDoAnual() {
        // Dado cru: catalogo manda um "lifetime" com durationMonths=1200; a loja diz que o Package
        // nao tem duracao. O plano aparece, ordena por ULTIMO, sem selo — o anual mantem o selo.
        val plans = listOf(
            plan("premium_mensal", "Mensal", "prod_mensal", 1),
            plan("premium_anual", "Anual", "prod_anual", 12),
            plan("premium_vitalicio", "Vitalicio", "prod_vitalicio", 1200, tipo = "LIFETIME"),
        )
        val packages = listOf(
            pkg("pkg_mensal", 1, "R$ 9,90", PurchasePackageType.MONTHLY),
            pkg("pkg_anual", 12, "R$ 89,90", PurchasePackageType.ANNUAL),
            pkg("pkg_lifetime", null, "R$ 499,00", PurchasePackageType.LIFETIME, storeProductId = "prod_vitalicio"),
        )
        val result = plans.toPaywallPlans(packages)

        assertEquals(listOf("pkg_mensal", "pkg_anual", "pkg_lifetime"), result.map { it.id })
        assertEquals("pkg_anual", result.single { it.isRecommended }.id)
        val lifetime = result.last()
        assertNull(lifetime.durationMonths, "lifetime nao tem duracao: null, nunca 1200")
        assertNull(lifetime.durationLabel, "sem duracao => sem rotulo mentiroso")
        assertEquals("R$ 499,00", lifetime.priceLabel, "continua visivel e assinavel")
    }

    @Test
    fun packages_intervaloNaoCanonico_trimestral_apareceSemSeloEPorUltimo() {
        // Package CUSTOM de 3 meses: nao existe trimestral na constituicao -> inelegivel ao selo.
        val plans = listOf(
            plan("premium_mensal", "Mensal", "prod_mensal", 1),
            plan("premium_tri", "Trimestral", "prod_tri", 3),
            plan("premium_semestral", "Semestral", "prod_semestral", 6),
        )
        val packages = listOf(
            pkg("pkg_mensal", 1, "R$ 9,90", PurchasePackageType.MONTHLY),
            pkg("pkg_tri", 3, "R$ 53,90", PurchasePackageType.OTHER, storeProductId = "prod_tri"),
            pkg("pkg_semestral", 6, "R$ 49,90", PurchasePackageType.SIX_MONTH),
        )
        val result = plans.toPaywallPlans(packages)

        assertEquals(listOf("pkg_mensal", "pkg_semestral", "pkg_tri"), result.map { it.id })
        assertEquals("pkg_semestral", result.single { it.isRecommended }.id)
        val tri = result.last()
        assertEquals("3 meses", tri.durationLabel, "rotulo honesto: nunca rebaixar para 'mensal'")
        assertEquals("R$ 53,90", tri.priceLabel)
    }

    @Test
    fun packages_nenhumElegivel_nenhumSelo() {
        // Só planos de duracao nao-canonica: melhor NENHUM selo que o selo errado.
        val plans = listOf(plan("premium_tri", "Trimestral", "prod_tri", 3))
        val packages = listOf(pkg("pkg_tri", 3, "R$ 53,90", storeProductId = "prod_tri"))
        val result = plans.toPaywallPlans(packages)

        assertEquals(1, result.size)
        assertTrue(result.none { it.isRecommended })
    }

    @Test
    fun packages_forcarDuracaoInelegivel_naoRoubaOSelo() {
        val plans = listOf(
            plan("premium_mensal", "Mensal", "prod_mensal", 1),
            plan("premium_anual", "Anual", "prod_anual", 12),
        )
        val packages = listOf(
            pkg("pkg_mensal", 1, "R$ 9,90", PurchasePackageType.MONTHLY),
            pkg("pkg_anual", 12, "R$ 89,90", PurchasePackageType.ANNUAL),
        )
        // Forcar uma duracao que nao existe/nao e elegivel -> cai no default (maior duracao elegivel).
        val result = plans.toPaywallPlans(packages, recommendedDurationMonths = 3)
        assertEquals("pkg_anual", result.single { it.isRecommended }.id)
    }

    // --- As 4 combinacoes VALIDAS da constituicao (mensal sempre presente) ---

    private fun combo(vararg months: Int): List<PaywallPlan> = withDerivedHighlight(
        months.map { m -> PaywallPlan(id = "p$m", name = "Plano $m", priceLabel = "R$ 1,00", durationMonths = m) }
    )

    @Test
    fun combinacoesValidas_seloVaiSempreParaAMaiorDuracao() {
        assertEquals("p1", combo(1).single { it.isRecommended }.id)                    // so mensal
        assertEquals("p6", combo(1, 6).single { it.isRecommended }.id)                 // mensal+semestral
        assertEquals("p12", combo(1, 12).single { it.isRecommended }.id)               // mensal+anual
        assertEquals("p12", combo(1, 6, 12).single { it.isRecommended }.id)            // os tres

        // Ordem fixa Mensal -> Semestral -> Anual, independente da ordem de entrada.
        assertEquals(listOf("p1", "p6", "p12"), combo(12, 1, 6).map { it.id })
    }

    @Test
    fun desligarOAnual_migraOSeloParaOSemestral_semTocarNoApp() {
        assertEquals("p12", combo(1, 6, 12).single { it.isRecommended }.id)
        assertEquals("p6", combo(1, 6).single { it.isRecommended }.id)
    }

    // --- withDerivedHighlight: contrato direto (o caso MinhaOS, que hardcodava o selo) ---

    @Test
    fun withDerivedHighlight_descartaOSeloQueVeioDeFora() {
        val plans = listOf(
            PaywallPlan(id = "mensal", name = "Mensal", priceLabel = "R$ 9,90", durationMonths = 1, isRecommended = true),
            PaywallPlan(id = "anual", name = "Anual", priceLabel = "R$ 89,90", durationMonths = 12, isRecommended = false),
        )
        val result = withDerivedHighlight(plans)
        assertEquals("anual", result.single { it.isRecommended }.id)
        assertTrue(result.first { it.id == "mensal" }.isRecommended.not())
    }

    @Test
    fun withDerivedHighlight_planoGratisNuncaLevaOSelo_mesmoEmpatandoEmDuracao() {
        val plans = listOf(
            PaywallPlan(id = "gratis", name = "Gratis", priceLabel = "R$ 0,00", durationMonths = 1, isFree = true),
            PaywallPlan(id = "mensal", name = "Mensal", priceLabel = "R$ 9,90", durationMonths = 1),
        )
        val result = withDerivedHighlight(plans)
        assertEquals("mensal", result.single { it.isRecommended }.id)
    }

    @Test
    fun withDerivedHighlight_soPlanoGratis_nenhumSelo() {
        val plans = listOf(
            PaywallPlan(id = "gratis", name = "Gratis", priceLabel = "R$ 0,00", durationMonths = 1, isFree = true),
        )
        assertTrue(withDerivedHighlight(plans).none { it.isRecommended })
    }

    @Test
    fun withDerivedHighlight_precoEmBrancoEInelegivel() {
        val plans = listOf(
            PaywallPlan(id = "mensal", name = "Mensal", priceLabel = "R$ 9,90", durationMonths = 1),
            PaywallPlan(id = "anual", name = "Anual", priceLabel = "  ", durationMonths = 12),
        )
        val result = withDerivedHighlight(plans)
        assertEquals("mensal", result.single { it.isRecommended }.id)
    }

    @Test
    fun withDerivedHighlight_forcarPlanoElegivel_respeitaOCaller() {
        val plans = listOf(
            PaywallPlan(id = "mensal", name = "Mensal", priceLabel = "R$ 9,90", durationMonths = 1),
            PaywallPlan(id = "anual", name = "Anual", priceLabel = "R$ 89,90", durationMonths = 12),
        )
        val result = withDerivedHighlight(plans, forcedPlanId = "mensal")
        assertEquals("mensal", result.single { it.isRecommended }.id)
    }

    @Test
    fun withDerivedHighlight_forcarPlanoInelegivel_caiNoDefault() {
        val plans = listOf(
            PaywallPlan(id = "gratis", name = "Gratis", priceLabel = "R$ 0,00", durationMonths = 1, isFree = true),
            PaywallPlan(id = "anual", name = "Anual", priceLabel = "R$ 89,90", durationMonths = 12),
        )
        val result = withDerivedHighlight(plans, forcedPlanId = "gratis")
        assertEquals("anual", result.single { it.isRecommended }.id)
    }

    @Test
    fun withDerivedHighlight_ordemEstavelParaNaoCanonicos_eListaVazia() {
        val plans = listOf(
            PaywallPlan(id = "lixo_a", name = "A", priceLabel = "R$ 1,00", durationMonths = 1200),
            PaywallPlan(id = "mensal", name = "Mensal", priceLabel = "R$ 9,90", durationMonths = 1),
            PaywallPlan(id = "lixo_b", name = "B", priceLabel = "R$ 1,00", durationMonths = null),
        )
        val result = withDerivedHighlight(plans)
        assertEquals(listOf("mensal", "lixo_a", "lixo_b"), result.map { it.id })
        assertEquals("mensal", result.single { it.isRecommended }.id)

        assertTrue(withDerivedHighlight(emptyList()).isEmpty())
    }

    @Test
    fun isHighlightEligible_contrato() {
        val base = PaywallPlan(id = "x", name = "X", priceLabel = "R$ 1,00", durationMonths = 12)
        assertTrue(base.isHighlightEligible)
        assertTrue(base.copy(isFree = true).isHighlightEligible.not())
        assertTrue(base.copy(priceLabel = "").isHighlightEligible.not())
        assertTrue(base.copy(durationMonths = 3).isHighlightEligible.not())
        assertTrue(base.copy(durationMonths = null).isHighlightEligible.not())
        assertTrue(base.copy(durationMonths = 1200).isHighlightEligible.not())
        assertTrue(base.copy(durationMonths = 1).isHighlightEligible)
        assertTrue(base.copy(durationMonths = 6).isHighlightEligible)
    }
}
