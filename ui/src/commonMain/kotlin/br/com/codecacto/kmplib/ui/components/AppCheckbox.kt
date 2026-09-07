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
 * ## A caixa alinha pela PRIMEIRA LINHA do rótulo, não pelo meio dele
 *
 * Rótulo de consentimento tem parágrafo, não três palavras — e com `CenterVertically` a caixa
 * descia para o meio de um bloco de seis linhas, longe do começo da frase que ela governa. O olho
 * lê de cima, e uma caixa solta no meio do texto parece pertencer à linha que está ao lado dela.
 *
 * O alinhamento é `Top` com **10dp** de folga no texto, e o número não é estético: o `Checkbox` do
 * Material desenha o seu *state layer* em **40dp** (`CheckboxTokens.StateLayerSize`), com o quadrado
 * no centro — logo a 20dp do topo —, e a primeira linha de `bodyMedium` (lineHeight 20sp) tem o
 * centro em ~10dp. Os 10dp fazem os dois centros coincidirem, então **rótulo de uma linha continua
 * parecendo centralizado**, exatamente como antes, e o de várias passa a começar junto da caixa.
 *
 * ⚠️ **Não são 48dp.** Foi o primeiro palpite (2.188.0) e deixou a caixa visivelmente ACIMA da
 * primeira linha: os 48dp do `minimumInteractiveComponentSize` expandem o **alvo de toque**, que é
 * maior que o desenho — usar esse número como se fosse a altura do componente empurra o texto 4dp
 * a mais para baixo. Quem manda aqui é o tamanho DESENHADO, não o tamanho tocável.
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
        verticalAlignment = Alignment.Top,
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
            // 10dp = centro do quadrado (20dp, metade do state layer de 40dp) menos o centro da
            // primeira linha (~10dp). NÃO usar os 48dp do alvo mínimo: ver o KDoc.
            modifier = Modifier.padding(top = 10.dp, end = 4.dp)
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

/** O caso que motivou o alinhamento no topo: rótulo de consentimento, com parágrafo. */
@Suppress("DEPRECATION")
@Preview
@Composable
private fun AppCheckboxRotuloLongoPreview() {
    AppTheme {
        AppCheckbox(
            checked = false,
            onCheckedChange = {},
            label = "Autorizo o contato de um especialista autorizado pelo telefone informado e " +
                "o compartilhamento dos meus dados com o parceiro responsável pelo atendimento, " +
                "para que ele possa apresentar as condições disponíveis para mim."
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
