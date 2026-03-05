package br.com.codecacto.kmplib.monetization

import br.com.codecacto.kmplib.firebase.ads.AdConfig
import br.com.codecacto.kmplib.monetization.purchase.PurchaseConfig

/**
 * Configuracao de monetizacao do app.
 *
 * Tres modos disponiveis:
 * - [AdsOnly] - App gratuito com publicidade, sem opcao de assinatura
 * - [PremiumOnly] - App pago por assinatura, sem publicidade
 * - [Freemium] - Gratuito com ads; assinante premium nao ve publicidade
 */
sealed class MonetizationConfig {

    /** App so com publicidade, sem opcao de assinatura. */
    data class AdsOnly(val ads: AdConfig) : MonetizationConfig()

    /** App so com assinatura, sem publicidade. */
    data class PremiumOnly(val purchase: PurchaseConfig) : MonetizationConfig()

    /** Gratuito com ads + opcao de pagar pra remover. */
    data class Freemium(
        val ads: AdConfig,
        val purchase: PurchaseConfig
    ) : MonetizationConfig()
}
