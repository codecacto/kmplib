package br.com.codecacto.kmplib.ads.custom

import br.com.codecacto.kmplib.ads.AdLoadState
import br.com.codecacto.kmplib.ads.router.AdProvider
import br.com.codecacto.kmplib.ads.router.AdRouting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random
import kotlin.time.Duration

/** O que fazer com um pedido de intersticial (2.236.0). */
internal sealed interface InterstitialDecision {
    /** Exibir [ad]. Só aqui a impressão é contada. */
    data class Show(val ad: CustomAd) : InterstitialDecision

    /** Não exibir. Nenhuma impressão é contada; o app recebe `onDismiss`, nunca `onShown`. */
    data class Skip(val reason: InterstitialSkipReason) : InterstitialDecision
}

internal enum class InterstitialSkipReason {
    /** Premium, ou a monetização não exibe anúncio. */
    ADS_DISABLED,

    /** O painel publicou `interstitial: off`. */
    ROUTING_OFF,

    /** A carga chegou e não há criativo de intersticial. */
    NO_AD,

    /** A primeira carga não chegou dentro do teto. */
    TIMEOUT,
}

/**
 * Decide um pedido de intersticial **esperando a primeira carga** dos anúncios — e, quando
 * [routing] vem, também a do roteamento — até [timeout].
 *
 * Antes da 2.236.0 a decisão era tomada no primeiro frame: com a lista do apps-api ainda vazia, o
 * pedido "ao abrir" não achava anúncio e a sessão perdia a abertura. Regras:
 *
 * - premium / sem anúncio no modo ([showAds] `false`) → pula **na hora** e também se virar `false`
 *   durante a espera; nunca espera para depois exibir a quem paga;
 * - com [routing]: espera a primeira carga dele (até lá, o valor é só o `defaults` do app) e pula
 *   se o painel publicou `off`;
 * - assim que houver criativo de intersticial, exibe (não precisa esperar mais nada);
 * - carga resolvida sem criativo → pula; o teto estourou → pula;
 * - fonte nunca inicializada ([AdLoadState.IDLE]) não é esperada: nada vai chegar, e esperar só
 *   atrasaria o `onDismiss` do app. Inicialize `CustomAdManager`/`AdRouter` no bootstrap.
 *
 * Não toca em UI nem em estatística: quem chama conta a impressão só no [InterstitialDecision.Show].
 */
internal suspend fun decideInterstitial(
    showAds: StateFlow<Boolean>,
    ads: StateFlow<List<CustomAd>>,
    adsLoad: StateFlow<AdLoadState>,
    timeout: Duration,
    routing: StateFlow<AdRouting>? = null,
    routingLoad: StateFlow<AdLoadState>? = null,
    random: Random = Random.Default,
): InterstitialDecision {
    if (!showAds.value) return InterstitialDecision.Skip(InterstitialSkipReason.ADS_DISABLED)

    val routingFlow: Flow<Pair<AdRouting, AdLoadState>?> =
        if (routing != null && routingLoad != null) combine(routing, routingLoad) { r, l -> r to l }
        else flowOf(null)

    val decided = withTimeoutOrNull(timeout) {
        combine(showAds, ads, adsLoad, routingFlow) { show, list, load, rt ->
            evaluate(show, list, load, rt, random)
        }.first { it != null }
    }
    return decided
        ?: if (!showAds.value) InterstitialDecision.Skip(InterstitialSkipReason.ADS_DISABLED)
        else InterstitialDecision.Skip(InterstitialSkipReason.TIMEOUT)
}

/** `null` = ainda não dá para decidir; continuar esperando. */
private fun evaluate(
    show: Boolean,
    list: List<CustomAd>,
    load: AdLoadState,
    routing: Pair<AdRouting, AdLoadState>?,
    random: Random,
): InterstitialDecision? {
    if (!show) return InterstitialDecision.Skip(InterstitialSkipReason.ADS_DISABLED)
    if (routing != null) {
        val (config, routingLoad) = routing
        if (routingLoad == AdLoadState.LOADING) return null
        if (config.interstitial == AdProvider.OFF) {
            return InterstitialDecision.Skip(InterstitialSkipReason.ROUTING_OFF)
        }
    }
    selectAd(list, format = CustomAd.FORMAT_INTERSTITIAL, random = random)
        ?.let { return InterstitialDecision.Show(it) }
    return if (load == AdLoadState.LOADING) null else InterstitialDecision.Skip(InterstitialSkipReason.NO_AD)
}
