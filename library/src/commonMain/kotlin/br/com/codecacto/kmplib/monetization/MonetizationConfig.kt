package br.com.codecacto.kmplib.monetization

import br.com.codecacto.kmplib.monetization.purchase.PurchaseConfig

/**
 * Configuracao de monetizacao do app.
 *
 * Tres modos disponiveis:
 * - [AdsOnly] - App gratuito com publicidade (house ads), sem opcao de assinatura
 * - [PremiumOnly] - App pago por assinatura, sem publicidade
 * - [Freemium] - Gratuito com ads; assinante premium nao ve publicidade
 *
 * A partir da kmplib 2.38.0 a publicidade e composta apenas por **house ads** (anuncios proprios via
 * apps-api). Nao existe mais configuracao de AdMob aqui — quais formatos aparecem (banner/interstitial)
 * e on/off por projeto sao decididos pelo `AdRouter` (`ads/router`) + `CustomAdManager` (`ads/custom`),
 * que o app inicializa separadamente. A monetizacao so decide se o usuario e premium (e portanto se
 * deve ver qualquer anuncio).
 */
sealed class MonetizationConfig {

    /** App so com publicidade (house ads), sem opcao de assinatura. */
    data object AdsOnly : MonetizationConfig()

    /** App so com assinatura, sem publicidade. */
    data class PremiumOnly(val purchase: PurchaseConfig) : MonetizationConfig()

    /** Gratuito com ads (house ads) + opcao de pagar pra remover. */
    data class Freemium(
        val purchase: PurchaseConfig
    ) : MonetizationConfig()
}
