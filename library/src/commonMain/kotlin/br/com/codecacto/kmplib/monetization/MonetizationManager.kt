package br.com.codecacto.kmplib.monetization

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.firebase.ads.AdManager
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
 *     MonetizationConfig.Freemium(
 *         ads = AdConfig(...),
 *         purchase = PurchaseConfig(...)
 *     )
 * )
 * ```
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

    /** Se o modo atual inclui ads. */
    val hasAds: Boolean
        get() = _config is MonetizationConfig.AdsOnly || _config is MonetizationConfig.Freemium

    /** Se o modo atual inclui purchase/assinatura. */
    val hasPurchase: Boolean
        get() = _config is MonetizationConfig.PremiumOnly || _config is MonetizationConfig.Freemium

    /**
     * Se o usuario e premium.
     * - AdsOnly: sempre false
     * - PremiumOnly / Freemium: depende do estado da assinatura
     */
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    /**
     * Se ads devem ser exibidos.
     * - AdsOnly: segue Remote Config
     * - PremiumOnly: sempre false
     * - Freemium: Remote Config && !isPremium
     */
    val shouldShowAds: StateFlow<Boolean> = _shouldShowAds.asStateFlow()

    /**
     * Inicializa o sistema de monetizacao.
     *
     * @param config Modo de monetizacao (AdsOnly, PremiumOnly, ou Freemium)
     * @param userId ID opcional do usuario para o RevenueCat
     */
    fun initialize(config: MonetizationConfig, userId: String? = null) {
        if (_initialized.value) {
            AppLogger.w(TAG, "MonetizationManager ja inicializado")
            return
        }

        _config = config

        when (config) {
            is MonetizationConfig.AdsOnly -> {
                AdManager.initialize(config.ads)
                _isPremium.value = false
                // shouldShowAds segue o Remote Config
                AdManager.adsEnabled.onEach { enabled ->
                    _shouldShowAds.value = enabled
                }.launchIn(scope)
                AppLogger.d(TAG, "Modo: ADS_ONLY")
            }
            is MonetizationConfig.PremiumOnly -> {
                PurchaseManager.initialize(config.purchase, userId)
                _shouldShowAds.value = false
                // isPremium segue o estado da assinatura
                PurchaseManager.subscriptionState.onEach { info ->
                    _isPremium.value = info.isActive
                }.launchIn(scope)
                AppLogger.d(TAG, "Modo: PREMIUM_ONLY")
            }
            is MonetizationConfig.Freemium -> {
                AdManager.initialize(config.ads)
                PurchaseManager.initialize(config.purchase, userId)
                // isPremium segue o estado da assinatura
                PurchaseManager.subscriptionState.onEach { info ->
                    _isPremium.value = info.isActive
                    // Recalcula shouldShowAds
                    _shouldShowAds.value = AdManager.adsEnabled.value && !info.isActive
                }.launchIn(scope)
                // shouldShowAds tambem reage ao Remote Config
                AdManager.adsEnabled.onEach { enabled ->
                    _shouldShowAds.value = enabled && !_isPremium.value
                }.launchIn(scope)
                // Valor inicial
                _shouldShowAds.value = true
                AppLogger.d(TAG, "Modo: FREEMIUM")
            }
        }

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
        AdManager.reset()
        PurchaseManager.reset()
    }
}
