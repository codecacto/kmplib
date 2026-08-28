package br.com.codecacto.kmplib.ads.router

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import br.com.codecacto.kmplib.ads.custom.CustomInterstitialAd

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
 * Quando provider for CUSTOM, renderiza [CustomInterstitialAd] como Dialog.
 * Quando provider for OFF, dispara `onDismiss` imediatamente.
 */
@Composable
fun ManagedInterstitialAd(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    val routing by AdRouter.routing.collectAsState()

    if (!show) return

    when (routing.interstitial) {
        AdProvider.CUSTOM -> {
            CustomInterstitialAd(
                show = true,
                onDismiss = onDismiss,
            )
        }
        AdProvider.OFF -> {
            LaunchedEffect(Unit) { onDismiss() }
        }
    }
}
