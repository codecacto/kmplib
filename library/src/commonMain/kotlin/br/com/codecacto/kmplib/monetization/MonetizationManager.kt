package br.com.codecacto.kmplib.monetization

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.monetization.purchase.ConsumablePurchaseResult
import br.com.codecacto.kmplib.monetization.purchase.PurchaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Orquestrador central de monetizacao.
 *
 * Ponto unico de entrada para configurar ads e/ou assinaturas.
 * Combina automaticamente o estado de Remote Config e premium
 * para decidir se ads devem ser exibidos.
 *
 * Uso no app:
 * ```kotlin
 * MonetizationManager.initialize(
 *     MonetizationConfig.FreemiumQuota(
 *         purchase = PurchaseConfig(...)
 *     )
 * )
 * ```
 *
 * A publicidade em si (house ads) e governada por `AdRouter`/`CustomAdManager`; aqui so se decide se
 * o usuario e premium. [shouldShowAds] = "deve exibir qualquer anuncio" (true quando NAO premium).
 *
 * A **postura** (tem ads? vende assinatura? tem tier gratuito?) e derivada do proprio
 * [MonetizationConfig], nao de `is`-check aqui: modo novo na lib obriga o compilador a responder as
 * tres perguntas, em vez de este objeto responder `false` em silencio.
 */
object MonetizationManager {
    private const val TAG = "MonetizationManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var _config: MonetizationConfig? = null
    private val _initialized = MutableStateFlow(false)

    private val _isPremium = MutableStateFlow(false)
    private val _shouldShowAds = MutableStateFlow(false)

    /** Configuracao atual. */
    val config: MonetizationConfig? get() = _config

    /** Se o modo atual monetiza o tier gratuito com publicidade (house ads). */
    val hasAds: Boolean
        get() = _config?.showsAds == true

    /** Se o modo atual vende assinatura (tem paywall). */
    val hasPurchase: Boolean
        get() = _config?.sellsSubscription == true

    /**
     * Se o modo atual tem **tier gratuito** utilizavel.
     *
     * `false` so em `PremiumOnly` ("pague para usar"). Distinto de [hasPurchase]: um SaaS freemium
     * com limite de uso tem os dois `true` (ver [MonetizationConfig.FreemiumQuota]).
     */
    val hasFreeTier: Boolean
        get() = _config?.hasFreeTier == true

    /**
     * Se o usuario e premium.
     * - AdsOnly: sempre false (o modo nao vende assinatura)
     * - demais modos: segue o estado da assinatura
     */
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    /**
     * Se ads (house ads) devem ser exibidos — [MonetizationConfig.shouldShowAds] aplicado ao estado
     * corrente da assinatura.
     * - AdsOnly: sempre true (gratuito)
     * - PremiumOnly / FreemiumQuota: sempre false (modos sem publicidade)
     * - Freemium: !isPremium
     */
    val shouldShowAds: StateFlow<Boolean> = _shouldShowAds.asStateFlow()

    /**
     * Inicializa o sistema de monetizacao.
     *
     * @param config Modo de monetizacao ([MonetizationConfig.AdsOnly], [MonetizationConfig.PremiumOnly],
     *   [MonetizationConfig.Freemium] ou [MonetizationConfig.FreemiumQuota])
     * @param userId ID opcional do usuario para o RevenueCat
     */
    fun initialize(config: MonetizationConfig, userId: String? = null) {
        if (_initialized.value) {
            AppLogger.w(TAG, "MonetizationManager ja inicializado")
            return
        }

        _config = config
        _isPremium.value = false
        // Valor inicial, antes do primeiro estado de assinatura chegar: o modo decide.
        _shouldShowAds.value = config.shouldShowAds(isPremium = false)

        config.purchaseConfig?.let { purchase ->
            PurchaseManager.initialize(purchase, userId)
            PurchaseManager.subscriptionState.onEach { info ->
                _isPremium.value = info.isActive
                _shouldShowAds.value = config.shouldShowAds(info.isActive)
            }.launchIn(scope)
        }

        AppLogger.d(TAG, "Modo: ${config.modeName}")

        _initialized.value = true
    }

    /**
     * Compra um produto CONSUMIVEL (one-time / pay-per-action).
     *
     * Para cobranca POR ACAO via loja (IAP nao-renovavel): devolve a transacao da loja
     * (transactionId/productId/store) para o app enviar a admin-api validar e liberar a acao.
     * NAO depende de entitlement e NAO altera [isPremium].
     */
    suspend fun purchaseConsumable(productId: String): ConsumablePurchaseResult =
        PurchaseManager.purchaseConsumable(productId)

    /** Reseta o estado (util para testes). */
    fun reset() {
        _config = null
        _initialized.value = false
        _isPremium.value = false
        _shouldShowAds.value = false
        PurchaseManager.reset()
    }
}
