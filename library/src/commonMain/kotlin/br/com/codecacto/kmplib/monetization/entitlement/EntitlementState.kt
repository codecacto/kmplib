package br.com.codecacto.kmplib.monetization.entitlement

/**
 * Estado observavel de monetizacao para embutir no State MVI de uma tela (BaseViewModel).
 *
 * Combina:
 * - o **entitlement** (plano + features) lido da fonte de verdade (admin-api / [EntitlementRepository]);
 * - o sinal **premium** vindo do `PurchaseManager`/RevenueCat (assinatura ativa na loja);
 * - os **medidores de uso** ("X de Y") por feature, para a UI.
 *
 * A autoridade do limite e SEMPRE o servidor; os campos de uso aqui sao para UX. O `isPremium`
 * (RevenueCat) e um sinal otimista de loja — o entitlement efetivo so muda quando o admin-api o
 * grava (via webhook) e o app o rele.
 */
data class EntitlementState(
    val entitlement: Entitlement = Entitlement.FREE,
    /** Sinal de assinatura ativa na loja (RevenueCat). */
    val isPremium: Boolean = false,
    /** Medidores de uso por feature (chave = nome da feature). */
    val usage: Map<String, UsageSnapshot> = emptyMap(),
    /** Carregando entitlement/uso do servidor. */
    val isLoading: Boolean = false,
    /** Erro ao consultar a fonte de verdade (estado degradado — ver R1 do doc 03). */
    val error: String? = null,
    /**
     * Contexto do paywall quando uma acao consumivel foi negada (mapeado do 402 do admin-api).
     * Nulo => paywall fechado.
     */
    val paywall: QuotaExceeded? = null
) {
    /** Plano efetivo (do entitlement do servidor). */
    val plano: String get() = entitlement.plano

    /** Se a feature esta liberada pelo plano OU o usuario e premium na loja. */
    fun hasFeature(feature: String): Boolean = isPremium || entitlement.hasFeature(feature)

    /** Medidor de uma feature (ou null se ainda nao carregado). */
    fun usageOf(feature: String): UsageSnapshot? = usage[feature]

    /** Indica se o paywall deve estar visivel. */
    val isPaywallOpen: Boolean get() = paywall != null

    /** Copia atualizando o medidor de uma feature. */
    fun withUsage(snapshot: UsageSnapshot): EntitlementState =
        copy(usage = usage + (snapshot.feature to snapshot))

    /** Abre o paywall com o contexto do 402. */
    fun showingPaywall(quota: QuotaExceeded): EntitlementState = copy(paywall = quota)

    /** Fecha o paywall. */
    fun dismissingPaywall(): EntitlementState = copy(paywall = null)
}
