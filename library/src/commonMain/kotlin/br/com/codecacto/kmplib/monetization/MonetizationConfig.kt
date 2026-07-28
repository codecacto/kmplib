package br.com.codecacto.kmplib.monetization

import br.com.codecacto.kmplib.monetization.purchase.PurchaseConfig

/**
 * **Postura de monetizacao** do app — a resposta a tres perguntas, e nada alem disso.
 *
 * 1. [showsAds] — o tier gratuito e monetizado com **publicidade** (house ads)?
 * 2. [sellsSubscription] — existe **paywall** de assinatura?
 * 3. [hasFreeTier] — existe **tier gratuito** utilizavel (alguem usa o app sem pagar)?
 *
 * Quatro modos cobrem as combinacoes legais:
 *
 * | Modo             | ads   | assinatura | tier gratis | quem e                                   |
 * |------------------|-------|------------|-------------|------------------------------------------|
 * | [AdsOnly]        | sim   | nao        | sim         | app gratis sustentado por house ads      |
 * | [PremiumOnly]    | nao   | sim        | **nao**     | so paga: sem assinatura, nao ha produto  |
 * | [Freemium]       | sim   | sim        | sim         | gratis com ads + assinatura pra remover  |
 * | [FreemiumQuota]  | nao   | sim        | sim         | **default do ecossistema**: gratis com   |
 * |                  |       |            |             | limite de uso -> paywall, SEM anuncio    |
 *
 * ## Por que quatro modos nomeados e nao tres booleanos soltos
 *
 * As tres perguntas **nao sao ortogonais**: exibir anuncio pressupoe ter tier gratuito (nao ha a
 * quem exibir quando todo usuario e assinante). Um produto cartesiano de tres booleanos
 * representaria oito combinacoes, das quais varias sao ilegais (`ads sem tier gratis`,
 * `nada + nada`) — o oposto de "estado ilegal nao deve ser representavel". O que faltava nao era
 * dimensionalidade, era **uma combinacao legal**: gratuito COM tier gratis e paywall, mas SEM
 * publicidade.
 *
 * As tres perguntas sao `abstract` de proposito: quem acrescentar um modo e **obrigado pelo
 * compilador** a responde-las. Antes elas eram derivadas fora daqui, por `is`-check no
 * `MonetizationManager` — e um modo novo esquecido ali daria silencio (`hasPurchase = false`) em
 * vez de erro de compilacao.
 *
 * ## O que este tipo NAO faz
 *
 * Ele descreve **postura**, nunca mecanismo. Em particular, [FreemiumQuota] **nao liga nem conhece
 * mecanismo de quota**: o enforcement de limite de uso e **server-side** (admin-api / `backlib-quota`),
 * e o cliente so exibe "X de Y" e abre o paywall (ver `monetization/entitlement` e
 * `monetization/quota/OfflineQuotaGate`). Um app pode estar em [FreemiumQuota] e limitar o tier
 * gratuito por feature, por contagem ou por periodo — a lib nao opina.
 *
 * A publicidade em si (house ads via apps-api) tambem nao mora aqui: quais formatos aparecem e o
 * on/off por projeto sao do `AdRouter` (`ads/router`) + `CustomAdManager` (`ads/custom`), que o app
 * inicializa separadamente. Aqui so se decide **se o usuario pode ver qualquer anuncio**.
 */
sealed class MonetizationConfig {

    /**
     * Rotulo estavel do modo (`"ADS_ONLY"`, `"PREMIUM_ONLY"`, `"FREEMIUM"`, `"FREEMIUM_QUOTA"`),
     * para log e telemetria. Explicito de proposito: `this::class.simpleName` sai ofuscado sob R8.
     */
    abstract val modeName: String

    /** O tier gratuito e monetizado com publicidade (house ads)? */
    abstract val showsAds: Boolean

    /** O app vende assinatura (tem paywall)? */
    abstract val sellsSubscription: Boolean

    /**
     * Existe tier gratuito utilizavel? `false` significa "sem assinatura nao ha produto".
     *
     * Invariante: `showsAds` implica `hasFreeTier` — anuncio so existe onde existe usuario nao
     * pagante (coberto por teste).
     */
    abstract val hasFreeTier: Boolean

    /** Config de compra (RevenueCat), quando o modo vende assinatura; `null` quando nao vende. */
    abstract val purchaseConfig: PurchaseConfig?

    /**
     * Decisao corrente de exibir anuncio, dado o estado da assinatura. Regra pura (testavel) que o
     * [MonetizationManager] apenas aplica: assinante nunca ve anuncio, e modo sem ads nunca exibe.
     */
    fun shouldShowAds(isPremium: Boolean): Boolean = showsAds && !isPremium

    /** App gratuito sustentado por publicidade (house ads), sem opcao de assinatura. */
    data object AdsOnly : MonetizationConfig() {
        override val modeName: String = "ADS_ONLY"
        override val showsAds: Boolean = true
        override val sellsSubscription: Boolean = false
        override val hasFreeTier: Boolean = true
        override val purchaseConfig: PurchaseConfig? = null
    }

    /**
     * **So assinatura, sem tier gratuito.** Sem assinar, nao ha produto utilizavel.
     *
     * Se o seu app tem plano gratuito (mesmo que limitado), o modo correto e [FreemiumQuota] —
     * este aqui descreve "pague para usar".
     */
    data class PremiumOnly(val purchase: PurchaseConfig) : MonetizationConfig() {
        override val modeName: String get() = "PREMIUM_ONLY"
        override val showsAds: Boolean get() = false
        override val sellsSubscription: Boolean get() = true
        override val hasFreeTier: Boolean get() = false
        override val purchaseConfig: PurchaseConfig get() = purchase
    }

    /** Gratuito **com house ads** + assinatura para remover a publicidade. */
    data class Freemium(val purchase: PurchaseConfig) : MonetizationConfig() {
        override val modeName: String get() = "FREEMIUM"
        override val showsAds: Boolean get() = true
        override val sellsSubscription: Boolean get() = true
        override val hasFreeTier: Boolean get() = true
        override val purchaseConfig: PurchaseConfig get() = purchase
    }

    /**
     * **Default do ecossistema (`CLAUDE.md`): "freemium com limite de uso -> paywall".**
     *
     * Tier gratuito real, porem **limitado** (quota/feature, com enforcement server-side no
     * admin-api), assinatura para destravar, e **nenhuma publicidade** — house ad dentro da
     * ferramenta de trabalho de um profissional pagante e ruido, nao receita.
     *
     * Comportamento identico a [PremiumOnly] (`shouldShowAds = false`, `sellsSubscription = true`);
     * o que muda e a **verdade declarada**: aqui EXISTE plano gratuito. Configurar SaaS freemium
     * como [PremiumOnly] funciona por acidente e mente para o proximo a ler — que assume, com
     * razao, que o app nao tem tier gratuito.
     *
     * Este modo **nao liga mecanismo de quota nenhum**: quem limita e o servidor; o app so exibe o
     * consumo (`UsageMeter`) e abre o paywall no 402 (ver `monetization/entitlement`).
     */
    data class FreemiumQuota(val purchase: PurchaseConfig) : MonetizationConfig() {
        override val modeName: String get() = "FREEMIUM_QUOTA"
        override val showsAds: Boolean get() = false
        override val sellsSubscription: Boolean get() = true
        override val hasFreeTier: Boolean get() = true
        override val purchaseConfig: PurchaseConfig get() = purchase
    }
}
