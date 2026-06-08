package br.com.codecacto.kmplib.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import br.com.codecacto.kmplib.ui.theme.AppColors

/**
 * Cor do pin para cada [MapMarkerStatus], resolvida a partir da paleta de
 * tema corrente ([AppColors.current]) — sem cores hardcoded na camada de produto.
 *
 * Mapeamento:
 * - [MapMarkerStatus.FREE] → `success` (verde)
 * - [MapMarkerStatus.OCCUPIED] → `primary` (cor de marca)
 * - [MapMarkerStatus.EXPIRING] → `warning` (âmbar)
 * - [MapMarkerStatus.EXPIRED] → `error` (vermelho)
 * - [MapMarkerStatus.INSTALLING] → `info` (azul)
 */
@Composable
fun MapMarkerStatus.color(): Color {
    val palette = AppColors.current
    return when (this) {
        MapMarkerStatus.FREE -> palette.success
        MapMarkerStatus.OCCUPIED -> palette.primary
        MapMarkerStatus.EXPIRING -> palette.warning
        MapMarkerStatus.EXPIRED -> palette.error
        MapMarkerStatus.INSTALLING -> palette.info
    }
}
