package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * **Portão de consentimento LGPD bloqueante de terceiro** — consentimento explícito de uma pessoa
 * cujos dados serão tratados (ex.: o responsável/cliente de um laudo veicular), **separado** do
 * aceite de Termos/Privacidade do titular do app (esse é o [AppCheckbox] no onboarding).
 *
 * Padrão recorrente em captura de dados de terceiros (laudo, cadastro de terceiro, coleta de
 * assinatura com PII). Diferente do [AppCheckbox]:
 *  - é **visualmente destacado** (card/`Surface` tonal com borda), sinalizando um gate obrigatório;
 *  - traz um [title] semântico opcional ("Consentimento" por padrão) acima do texto;
 *  - sinaliza erro ([isError]) quando o fluxo tentou avançar sem o consentimento.
 *
 * **Não decide o fluxo:** o app é quem bloqueia o "Continuar" enquanto [checked] for `false`
 * (a lib só apresenta e reporta a marcação). Acessível: a superfície inteira é `toggleable` com
 * [Role.Checkbox], então leitores de tela anunciam estado + texto de forma unificada.
 *
 * ```kotlin
 * ConsentGate(
 *     checked = state.lgpdConsent,
 *     onCheckedChange = { onAction(Action.ConsentChanged(it)) },
 *     text = "Declaro que o terceiro identificado consente com o tratamento dos seus dados " +
 *         "(nome e documento) para fins do laudo, conforme a LGPD.",
 *     isError = state.consentMissing,
 * )
 * // ...
 * AppButton(enabled = state.canContinue) { ... }   // canContinue = nome.isNotBlank() && lgpdConsent
 * ```
 *
 * @param checked estado atual do consentimento.
 * @param onCheckedChange callback ao alternar.
 * @param text texto do consentimento (a declaração de tratamento de dados).
 * @param modifier modificador externo.
 * @param title título do gate exibido acima do texto (default "Consentimento"). `null` oculta.
 * @param enabled se o controle está habilitado.
 * @param isError realça o gate (borda/acento na cor de erro) quando o consentimento é exigido e falta.
 */
@Composable
fun ConsentGate(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    title: String? = "Consentimento",
    enabled: Boolean = true,
    isError: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = if (isError && !checked) scheme.error else scheme.primary
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange,
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                    enabled = enabled,
                    colors = CheckboxDefaults.colors(checkedColor = accent),
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp, end = 4.dp),
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun ConsentGatePreview() {
    AppTheme {
        ConsentGate(
            checked = false,
            onCheckedChange = {},
            text = "Declaro que o terceiro identificado consente com o tratamento dos seus dados " +
                "(nome e documento) para fins do laudo, conforme a LGPD.",
        )
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun ConsentGateErrorPreview() {
    AppTheme {
        ConsentGate(
            checked = false,
            onCheckedChange = {},
            text = "É necessário o consentimento do terceiro para prosseguir.",
            isError = true,
        )
    }
}
