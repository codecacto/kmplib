package br.com.codecacto.kmplib.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import br.com.codecacto.kmplib.ui.theme.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Toggle on/off (Switch) estilizado pelas cores do tema, par do [AppCheckbox].
 *
 * Wrapper fino sobre o [Switch] do Material 3 que aplica as cores de
 * [MaterialTheme.colorScheme] (qualquer paleta do [AppTheme]), sem cores
 * hardcoded. Para uma linha completa com rótulo (ex.: linha de Configurações),
 * componha com um `Row` no app ou com o futuro `SettingsRow`.
 *
 * Uso típico: toggle "Notificação diária" em telas de ajustes.
 *
 * ```kotlin
 * AppSwitch(
 *     checked = notificacaoDiaria,
 *     onCheckedChange = { notificacaoDiaria = it }
 * )
 * ```
 *
 * @param checked Estado atual (ligado/desligado).
 * @param onCheckedChange Callback disparado ao alternar o estado.
 * @param modifier Modificador externo aplicado ao switch.
 * @param enabled Se o controle está habilitado para interação.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun AppSwitchCheckedPreview() {
    AppTheme {
        AppSwitch(checked = true, onCheckedChange = {})
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun AppSwitchUncheckedPreview() {
    AppTheme {
        AppSwitch(checked = false, onCheckedChange = {})
    }
}
