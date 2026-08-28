package br.com.codecacto.kmplib.ui.screens.paywall

import br.com.codecacto.kmplib.monetization.purchase.PurchasePackage
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fallback do paywall: monta a vitrine SO com os Packages da loja quando a oferta central nao pode
 * ser lida ([toPaywallPlansFromStore]). Regras mais restritivas que o caminho normal — sem a oferta
 * central nao ha como afirmar o que esta a venda.
 */
class PaywallStoreFallbackMapperTest {

    private fun pkg(
        packageId: String,
        durationMonths: Int?,
        priceLabel: String = "R$ 9,90",
        micros: Long = 9_900_000L,
        type: PurchasePackageType = PurchasePackageType.OTHER,
    ) = PurchasePackage(
        packageId = packageId,
        packageType = type,
        storeProductId = "store_$packageId",
        priceLabel = priceLabel,
        priceAmountMicros = micros,
        currencyCode = "BRL",
        durationMonths = durationMonths,
    )

    @Test
    fun monta_planos_da_loja_com_nome_canonico_ordem_e_selo() {
        val packages = listOf(
            pkg("\$rc_six_month", 6, "R$ 39,90", 39_900_000L),
            pkg("\$rc_monthly", 1, "R$ 9,90", 9_900_000L),
        )

        val result = packages.toPaywallPlansFromStore()

        // Ordem fixa Mensal -> Semestral, nome canonico derivado da duracao, preco da loja.
        assertEquals(listOf("Mensal", "Semestral"), result.map { it.name })
        assertEquals(listOf("\$rc_monthly", "\$rc_six_month"), result.map { it.id })
        assertEquals("R$ 39,90", result.last().priceLabel)
        // Selo = maior duracao exibida (semestral aqui), derivado, nunca hardcoded.
        assertEquals("\$rc_six_month", result.single { it.isRecommended }.id)
    }

    @Test
    fun omite_duracao_nao_canonica() {
        // O `$rc_three_month` residual do offering do Super 8 NAO pode virar plano: trimestral nao
        // existe no padrao da fabrica, e sem oferta central nao ha confirmacao de que esta ativo.
        val packages = listOf(
            pkg("\$rc_monthly", 1),
            pkg("\$rc_three_month", 3, "R$ 25,00", 25_000_000L),
            pkg("\$rc_lifetime", 1200, "R$ 199,00", 199_000_000L),
            pkg("sem_duracao", null, "R$ 1,00", 1_000_000L),
        )

        val result = packages.toPaywallPlansFromStore()

        assertEquals(listOf("\$rc_monthly"), result.map { it.id })
    }

    @Test
    fun omite_pacote_sem_preco_resolvido_ou_com_preco_zero() {
        val packages = listOf(
            pkg("\$rc_monthly", 1),
            pkg("sem_label", 6, priceLabel = "   ", micros = 39_900_000L),
            pkg("preco_zero", 12, priceLabel = "R$ 0,00", micros = 0L),
        )

        val result = packages.toPaywallPlansFromStore()

        assertEquals(listOf("\$rc_monthly"), result.map { it.id })
        assertTrue(result.none { it.priceLabel.isBlank() })
    }

    @Test
    fun aceita_nome_rotulo_e_destaques_do_app_i18n() {
        val packages = listOf(pkg("\$rc_annual", 12, "US$ 49.90", 49_900_000L))

        val result = packages.toPaywallPlansFromStore(
            planName = { months -> if (months == 12) "Yearly" else "?" },
            durationLabel = { months -> if (months == 12) "1 year" else null },
            highlights = { listOf("No ads") },
        )

        val plan = result.single()
        assertEquals("Yearly", plan.name)
        assertEquals("1 year", plan.durationLabel)
        assertEquals(listOf("No ads"), plan.highlights)
        assertTrue(plan.isRecommended)
    }

    @Test
    fun recommendedForcado_soEhHonradoSeOPlanoExiste() {
        val packages = listOf(
            pkg("\$rc_monthly", 1),
            pkg("\$rc_six_month", 6, "R$ 39,90", 39_900_000L),
        )

        assertEquals(
            "\$rc_monthly",
            packages.toPaywallPlansFromStore(recommendedDurationMonths = 1)
                .single { it.isRecommended }.id,
        )
        // Duracao inexistente na lista => cai no default (maior duracao elegivel).
        assertEquals(
            "\$rc_six_month",
            packages.toPaywallPlansFromStore(recommendedDurationMonths = 12)
                .single { it.isRecommended }.id,
        )
    }

    @Test
    fun lista_vazia_nao_inventa_plano() {
        assertTrue(emptyList<PurchasePackage>().toPaywallPlansFromStore().isEmpty())
    }

    @Test
    fun defaultPlanName_usa_os_tres_tipos_canonicos() {
        assertEquals("Mensal", defaultPlanName(1))
        assertEquals("Semestral", defaultPlanName(6))
        assertEquals("Anual", defaultPlanName(12))
        // Sem "trimestral" no vocabulario: nao-canonico e descrito com honestidade.
        assertEquals("3 meses", defaultPlanName(3))
    }
}
