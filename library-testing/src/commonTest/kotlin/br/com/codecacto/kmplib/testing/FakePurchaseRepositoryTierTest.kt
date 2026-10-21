package br.com.codecacto.kmplib.testing

import br.com.codecacto.kmplib.monetization.purchase.PurchaseErrorCode
import br.com.codecacto.kmplib.monetization.purchase.PurchaseResult
import br.com.codecacto.kmplib.monetization.purchase.StoreReplacementMode
import br.com.codecacto.kmplib.monetization.purchase.SubscriptionChange
import br.com.codecacto.kmplib.ui.screens.paywall.PaywallTierKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 2.316.0 — a escada de degraus e a troca de plano no dublê de QA. */
class FakePurchaseRepositoryTierTest {

    @Test
    fun `escada sai na convencao do provisionador e a lib a reconhece`() = runTest {
        val planos = FakePurchaseRepository.planosPorDegrau(listOf("v3", "v10"), app = "rondaki")
        assertEquals(listOf("v3_mensal", "v3_semestral", "v3_anual", "v10_mensal", "v10_semestral", "v10_anual"), planos.map { it.packageId })
        assertEquals("premium_v10_anual_rondaki", planos.last().storeProductId)
        assertEquals("R$ 19,90", planos.first().priceLabel)
        assertTrue(planos.all { PaywallTierKeys.of(it, listOf("v3", "v10")) != null })
    }

    @Test
    fun `troca a partir do produto ativo vira a assinatura nova e fica registrada`() = runTest {
        val loja = FakePurchaseRepository.jaAssinante(
            idDoProdutoNaLoja = "premium_v3_mensal_fake",
            planos = FakePurchaseRepository.planosPorDegrau(listOf("v3", "v10")),
        )
        val troca = SubscriptionChange("premium_v3_mensal_fake:mensal", StoreReplacementMode.CHARGE_PRORATED_PRICE)
        val r = loja.changeSubscription("v10_mensal", troca)

        assertIs<PurchaseResult.Success>(r)
        assertEquals("premium_v10_mensal_fake", loja.subscriptionState.first().productId)
        assertEquals(listOf("v10_mensal" to troca), loja.trocasDePlano)
    }

    @Test
    fun `troca a partir de produto que nao esta ativo e recusada`() = runTest {
        val loja = FakePurchaseRepository.jaAssinante(
            idDoProdutoNaLoja = "premium_v3_mensal_fake",
            planos = FakePurchaseRepository.planosPorDegrau(listOf("v3", "v10")),
        )
        val r = loja.changeSubscription("v10_mensal", SubscriptionChange("premium_v10_anual_fake", StoreReplacementMode.DEFERRED))
        assertIs<PurchaseResult.Error>(r)
        assertEquals(PurchaseErrorCode.CONFIGURATION_ERROR, r.code)
        assertTrue(loja.trocasDePlano.isEmpty())
    }
}
