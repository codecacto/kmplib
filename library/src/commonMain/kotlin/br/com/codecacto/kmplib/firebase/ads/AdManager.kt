package br.com.codecacto.kmplib.firebase.ads

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gerenciador central de ads.
 *
 * Inicialize no app com [initialize] passando a [AdConfig].
 * Use [adsEnabled] para reagir a mudanças do Remote Config.
 */
object AdManager {
    private const val TAG = "AdManager"

    // Test Ad Unit IDs
    internal object TestIds {
        // Android
        const val ANDROID_BANNER = "ca-app-pub-3940256099942544/6300978111"
        const val ANDROID_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
        const val ANDROID_APP_OPEN = "ca-app-pub-3940256099942544/9257395921"

        // iOS
        const val IOS_BANNER = "ca-app-pub-3940256099942544/2435281174"
        const val IOS_INTERSTITIAL = "ca-app-pub-3940256099942544/4411468910"
        const val IOS_APP_OPEN = "ca-app-pub-3940256099942544/5575463023"
    }

    private val _adsEnabled = MutableStateFlow(true)
    val adsEnabled: StateFlow<Boolean> = _adsEnabled.asStateFlow()

    private var _config: AdConfig? = null
    val config: AdConfig? get() = _config

    var interstitial: InterstitialAdController? = null
        private set

    var appOpen: AppOpenAdController? = null
        private set

    private var _initialized = false

    /**
     * Inicializa o AdManager com a configuração fornecida.
     * Deve ser chamado uma vez no app (ex: LaunchedEffect no App.kt).
     */
    fun initialize(config: AdConfig) {
        if (_initialized) {
            AppLogger.w(TAG, "AdManager já inicializado")
            return
        }

        _config = config
        _initialized = true

        AppLogger.d(TAG, "Inicializando ads (testMode=${config.testMode})")

        // Inicializa SDK da plataforma
        initializeAdsPlatform()

        // Cria controllers
        interstitial = createInterstitialController()
        if (config.appOpen != null) {
            appOpen = createAppOpenController()
        }

        // Fetch Remote Config
        AdRemoteConfig.fetchAdsEnabled(config.remoteConfigKey) { enabled ->
            _adsEnabled.value = enabled
            AppLogger.d(TAG, "Remote Config: ads enabled = $enabled")
        }
    }

    /**
     * Retorna o ad unit ID correto (teste ou produção) para o banner.
     */
    fun getBannerAdUnitId(): String {
        val cfg = _config ?: return ""
        return if (cfg.testMode) {
            getTestBannerId()
        } else {
            getPlatformBannerId(cfg.banner)
        }
    }

    /**
     * Retorna o ad unit ID correto (teste ou produção) para o interstitial.
     */
    fun getInterstitialAdUnitId(): String {
        val cfg = _config ?: return ""
        return if (cfg.testMode) {
            getTestInterstitialId()
        } else {
            getPlatformInterstitialId(cfg.interstitial)
        }
    }

    /**
     * Retorna o ad unit ID correto (teste ou produção) para o app open.
     */
    fun getAppOpenAdUnitId(): String {
        val cfg = _config ?: return ""
        val appOpenIds = cfg.appOpen ?: return ""
        return if (cfg.testMode) {
            getTestAppOpenId()
        } else {
            getPlatformAppOpenId(appOpenIds)
        }
    }

    /**
     * Reseta o estado (útil para testes).
     */
    fun reset() {
        _initialized = false
        _config = null
        interstitial = null
        appOpen = null
        _adsEnabled.value = true
    }
}

// Expect functions para implementação por plataforma
internal expect fun initializeAdsPlatform()
internal expect fun createInterstitialController(): InterstitialAdController
internal expect fun createAppOpenController(): AppOpenAdController
internal expect fun getTestBannerId(): String
internal expect fun getTestInterstitialId(): String
internal expect fun getTestAppOpenId(): String
internal expect fun getPlatformBannerId(ids: AdUnitIds): String
internal expect fun getPlatformInterstitialId(ids: AdUnitIds): String
internal expect fun getPlatformAppOpenId(ids: AdUnitIds): String
