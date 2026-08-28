package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Checkbox acessível com rótulo clicável.
 *
 * A linha inteira (checkbox + label) é clicável e exposta para acessibilidade
 * via [Modifier.toggleable] com [Role.Checkbox], garantindo que leitores de tela
 * anunciem o estado de marcação e o texto do rótulo de forma unificada.
 *
 * Cores derivam de [MaterialTheme.colorScheme] (sem cores hardcoded).
 *
 * Uso típico: aceite de termos LGPD no onboarding.
 *
 * ```kotlin
 * AppCheckbox(
 *     checked = aceitouTermos,
 *     onCheckedChange = { aceitouTermos = it },
 *     label = "Li e aceito os termos de uso e a política de privacidade"
 * )
 * ```
 *
 * @param checked Estado atual de marcação.
 * @param onCheckedChange Callback disparado ao alternar o estado.
 * @param label Texto descritivo exibido ao lado do checkbox.
 * @param modifier Modificador externo aplicado à linha.
 * @param enabled Se o controle está habilitado para interação.
 */
@Composable
fun AppCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onCheckedChange
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // onCheckedChange = null: a interação é tratada pelo Row.toggleable acima,
        // evitando duplicidade de nós semânticos para o leitor de tela.
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            modifier = Modifier.padding(end = 4.dp)
        )
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun AppCheckboxPreview() {
    AppTheme {
        AppCheckbox(
            checked = true,
            onCheckedChange = {},
            label = "Li e aceito os termos de uso e a política de privacidade"
        )
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun AppCheckboxUncheckedPreview() {
    AppTheme {
        AppCheckbox(
            checked = false,
            onCheckedChange = {},
            label = "Aceito receber comunicações por e-mail"
        )
    }
}
