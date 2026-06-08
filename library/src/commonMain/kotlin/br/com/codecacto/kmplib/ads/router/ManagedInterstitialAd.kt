package br.com.codecacto.kmplib.ads.router

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import br.com.codecacto.kmplib.ads.custom.CustomInterstitialAd
import br.com.codecacto.kmplib.firebase.ads.AdManager

/**
 * Interstitial gerenciado pelo [AdRouter]. Despacha em runtime entre AdMob,
 * Custom Ads ou nada, baseado na config remota
 * (campo `interstitial` no doc `app_ad_configs/{appId}`).
 *
 * Padrao de uso:
 * ```kotlin
 * var showAd by remember { mutableStateOf(false) }
 *
 * Button(onClick = { showAd = true }) { Text("Mostrar") }
 *
 * ManagedInterstitialAd(
 *     show = showAd,
 *     placementId = "after_level",
 *     onDismiss = { showAd = false }
 * )
 * ```
 *
 * Quando provider for ADMOB, usa `AdManager.interstitial` (deve estar
 * pre-carregado via `controller.load()`). Se nao houver controller ou nao
 * estiver carregado, dispara `onDismiss` imediatamente — mesmo contrato do
 * `InterstitialAdController.show()`.
 *
 * Quando provider for CUSTOM, renderiza [CustomInterstitialAd] como Dialog.
 *
 * Quando provider for OFF, dispara `onDismiss` imediatamente.
 */
@Composable
fun ManagedInterstitialAd(
    show: Boolean,
    placementId: String? = null,
    onDismiss: () -> Unit,
) {
    val routing by AdRouter.routing.collectAsState()

    if (!show) return

    when (routing.interstitial) {
        AdProvider.ADMOB -> {
            // AdMob mostra um dialog nativo full-screen, nao precisa renderizar nada.
            // Disparamos show() apenas uma vez por ciclo "show=true".
            val controller = remember { AdManager.interstitial }
            LaunchedEffect(Unit) {
                if (controller != null && controller.isLoaded()) {
                    controller.show(onDismissed = {
                        onDismiss()
                        // Pre-carrega o proximo automaticamente
                        controller.load()
                    })
                } else {
                    onDismiss()
                }
            }
        }
        AdProvider.CUSTOM -> {
            CustomInterstitialAd(
                show = true,
                placementId = placementId,
                onDismiss = onDismiss,
            )
        }
        AdProvider.OFF -> {
            LaunchedEffect(Unit) { onDismiss() }
        }
    }
}
