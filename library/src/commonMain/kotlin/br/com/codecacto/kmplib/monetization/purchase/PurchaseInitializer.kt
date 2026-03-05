package br.com.codecacto.kmplib.monetization.purchase

/**
 * Inicializador do RevenueCat por plataforma.
 */
internal expect object PurchaseInitializer {
    /**
     * Configura o RevenueCat SDK com a API key da plataforma.
     *
     * @param config Configuracao de compras
     * @param userId ID opcional do usuario
     */
    fun initialize(config: PurchaseConfig, userId: String? = null)
}
