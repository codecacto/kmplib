package br.com.codecacto.kmplib.monetization.entitlement

/**
 * Pedaco de estado MVI de entitlement, para **embutir** no `State` de uma tela que gateia recursos.
 *
 * Imutavel; use os helpers `with*`/`showingPaywall`/`dismissingPaywall` que retornam copias.
 *
 * @property entitlement Entitlement vigente (default [Entitlement.FREE]).
 * @property isPremium Espelho de `MonetizationManager.isPremium` (assinatura ativa via store).
 * @property usage Mapa feature -> [UsageSnapshot] para exibir "X de Y".
 * @property paywall Quota estourada que deve abrir o paywall; `null` = paywall fechado.
 */
data class EntitlementState(
    val entitlement: Entitlement = Entitlement.FREE,
    val isPremium: Boolean = false,
    val usage: Map<String, UsageSnapshot> = emptyMap(),
    val paywall: QuotaExceeded? = null,
) {
    /**
     * Se a feature [feature] esta liberada. **Premium vence:** assinante ativo tem acesso mesmo que
     * o entitlement (eventualmente desatualizado) ainda nao reflita. Caso contrario, segue o
     * entitlement do servidor.
     */
    fun hasFeature(feature: String): Boolean = isPremium || entitlement.hasFeature(feature)

    /** Se o paywall esta sendo exibido. */
    val showingPaywall: Boolean get() = paywall != null

    /** Retorna copia com o entitlement atualizado. */
    fun withEntitlement(entitlement: Entitlement): EntitlementState =
        copy(entitlement = entitlement)

    /** Retorna copia com o flag premium atualizado. */
    fun withPremium(premium: Boolean): EntitlementState = copy(isPremium = premium)

    /** Retorna copia com o snapshot de uso de uma feature mesclado no mapa. */
    fun withUsage(snapshot: UsageSnapshot): EntitlementState =
        copy(usage = usage + (snapshot.feature to snapshot))

    /** Retorna copia abrindo o paywall para a quota informada. */
    fun showingPaywall(quota: QuotaExceeded): EntitlementState = copy(paywall = quota)

    /** Retorna copia fechando o paywall. */
    fun dismissingPaywall(): EntitlementState = copy(paywall = null)
}
