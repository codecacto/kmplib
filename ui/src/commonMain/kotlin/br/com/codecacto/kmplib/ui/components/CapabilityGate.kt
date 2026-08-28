package br.com.codecacto.kmplib.ui.components

import androidx.compose.runtime.Composable
import br.com.codecacto.kmplib.platform.PlatformCapability

/**
 * Renderiza [content] **apenas** onde a [capability] existe de verdade; caso contrário renderiza
 * [fallback] (por default, nada).
 *
 * Evita o "stub silencioso": em vez de exibir um botão "Exportar PDF" que lança exceção no iPhone,
 * o botão simplesmente não existe naquele alvo. Quando o `actual` iOS for implementado, o flag
 * vira `true` e a UI reaparece sem tocar no app.
 *
 * ```kotlin
 * CapabilityGate(PlatformCapability.PdfGeneration) {
 *     AppButton(text = "Exportar PDF", onClick = ::export)
 * }
 * ```
 *
 * Para *listas* de features vendidas (destaques do paywall, menu), use
 * [availableValues][br.com.codecacto.kmplib.platform.availableValues].
 */
@Composable
fun CapabilityGate(
    capability: PlatformCapability,
    fallback: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    if (capability.isAvailable) content() else fallback()
}
