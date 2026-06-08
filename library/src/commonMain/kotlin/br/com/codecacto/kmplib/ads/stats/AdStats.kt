package br.com.codecacto.kmplib.ads.stats

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Singleton de coleta de metricas (impressions/clicks) pra Custom Ads e AdMob.
 *
 * Inicializado uma vez no boot do app (depois de [AdRouter.initialize], pois
 * usa o mesmo `appId`). A partir dai, todo composable de ad — Custom ou
 * AdMob — vai disparar `recordImpression`/`recordClick` automaticamente.
 *
 * Gravacao e fire-and-forget num coroutine scope interno — nao bloqueia UI.
 * Erros sao logados via [AppLogger] e ignorados.
 *
 * Uso:
 * ```kotlin
 * AdStats.initialize(appId = "meu-app")
 * // a partir daqui, ManagedBannerAd / BannerAd / CustomBannerAd ja registram.
 * ```
 */
object AdStats {
    private const val TAG = "AdStats"

    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var _appId: String = ""
    private var _recorder: AdStatsRecorder = NoopAdStatsRecorder
    private var _enabled: Boolean = false

    /** AppId em uso (vazio se nao inicializado). */
    val appId: String get() = _appId

    /** Se a gravacao esta ativa. Quando false, todas as chamadas viram no-op. */
    val enabled: Boolean get() = _enabled

    /**
     * Inicializa o coletor.
     *
     * @param appId mesmo valor passado ao [AdRouter] e ao `CustomAdManager`.
     * @param recorder backend de gravacao. Default usa [FirestoreAdStatsRecorder].
     * @param scope coroutine scope. Default usa Dispatchers.Default.
     */
    fun initialize(
        appId: String,
        recorder: AdStatsRecorder = FirestoreAdStatsRecorder(),
        scope: CoroutineScope? = null,
    ) {
        scope?.let { this.scope = it }
        _appId = appId
        _recorder = recorder
        _enabled = true
        AppLogger.d(TAG, "AdStats inicializado para appId=$appId")
    }

    /** Reseta — util pra testes. */
    fun reset() {
        _appId = ""
        _recorder = NoopAdStatsRecorder
        _enabled = false
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    /** Conveniencia. */
    fun recordImpression(provider: AdProviderTag, format: AdFormat, adId: String? = null) {
        emit(provider, format, adId, AdEventType.IMPRESSION)
    }

    /** Conveniencia. */
    fun recordClick(provider: AdProviderTag, format: AdFormat, adId: String? = null) {
        emit(provider, format, adId, AdEventType.CLICK)
    }

    private fun emit(provider: AdProviderTag, format: AdFormat, adId: String?, type: AdEventType) {
        if (!_enabled) return
        val capturedAppId = _appId
        val capturedRecorder = _recorder
        scope.launch {
            try {
                capturedRecorder.record(provider, capturedAppId, format, adId, type)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Falha ao gravar stat (silencioso): $provider/$format/$type", e)
            }
        }
    }

    /**
     * Formata `now` como `YYYY-MM-DD` em **UTC**. Helper compartilhado pra recorders.
     *
     * Tem que ser UTC pra bater com o painel admin (`getUTCFullYear()` no TS).
     * Se usar timezone local, eventos perto da meia-noite cairiam em buckets
     * de dias diferentes entre lib e admin, e o agregado por dia fica errado.
     */
    internal fun todayKey(nowMillis: Long = currentTimeMillis()): String {
        val ldt = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(TimeZone.UTC)
        val y = ldt.year.toString().padStart(4, '0')
        val m = ldt.monthNumber.toString().padStart(2, '0')
        val d = ldt.dayOfMonth.toString().padStart(2, '0')
        return "$y-$m-$d"
    }
}
