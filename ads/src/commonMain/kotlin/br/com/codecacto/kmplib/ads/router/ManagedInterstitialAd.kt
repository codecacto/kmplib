package br.com.codecacto.kmplib.ads.router

import androidx.compose.runtime.Composable
import br.com.codecacto.kmplib.ads.AdDefaults
import br.com.codecacto.kmplib.ads.custom.CustomAd
import br.com.codecacto.kmplib.ads.custom.CustomInterstitialAd
import br.com.codecacto.kmplib.ads.custom.InterstitialAdHost
import br.com.codecacto.kmplib.ads.custom.InterstitialCloseMode
import kotlin.time.Duration

/**
 * Interstitial gerenciado pelo [AdRouter]. Despacha em runtime entre house ads (CUSTOM) ou nada
 * (OFF), baseado na config remota do backend central (campo `interstitial`).
 *
 * Padrao de uso:
 * ```kotlin
 * var showAd by remember { mutableStateOf(false) }
 *
 * Button(onClick = { showAd = true }) { Text("Mostrar") }
 *
 * ManagedInterstitialAd(
 *     show = showAd,
 *     onDismiss = { showAd = false }
 * )
 * ```
 *
 * Quando provider for CUSTOM, renderiza o mesmo Dialog do [CustomInterstitialAd].
 * Quando provider for OFF, dispara `onDismiss` sem exibir nada.
 *
 * **Primeira carga (2.236.0):** pedido feito antes de o roteamento E a lista de anuncios chegarem do
 * apps-api (o intersticial "ao abrir", no primeiro frame) espera as duas cargas ate
 * [firstLoadTimeout], sem desenhar nada por cima da tela. Antes, o roteamento ainda no `defaults`
 * ou a lista ainda vazia faziam o pedido ser descartado na hora — e a sessao perdia a abertura.
 * O `off` publicado no painel e respeitado (a espera e justamente para le-lo), premium nunca ve
 * anuncio, e exibicao pulada nao conta impressao nem chama [onShown].
 *
 * @param onShown chamado so quando o anuncio aparece de fato — o lugar de contar frequencia.
 * @param firstLoadTimeout teto da espera pela primeira carga. `Duration.ZERO` = decidir na hora
 *   (para intersticial que segura uma navegacao no `onDismiss`).
 */
@Composable
fun ManagedInterstitialAd(
    show: Boolean,
    onDismiss: () -> Unit,
    onShown: ((CustomAd) -> Unit)? = null,
    firstLoadTimeout: Duration = AdDefaults.INTERSTITIAL_FIRST_LOAD_TIMEOUT,
) {
    InterstitialAdHost(
        show = show,
        onDismiss = onDismiss,
        closeMode = InterstitialCloseMode.IMMEDIATE,
        onAdClick = null,
        onShown = onShown,
        firstLoadTimeout = firstLoadTimeout,
        withRouting = true,
    )
}
