package br.com.codecacto.kmplib.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import br.com.codecacto.kmplib.ui.theme.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Seletor de opções mutuamente exclusivas (single choice) no estilo Material 3.
 *
 * Útil para escolhas curtas e exclusivas como tipo de vaga (Coberta/Descoberta)
 * ou forma de pagamento (Pix/Cartão/Dinheiro).
 *
 * Usa [SingleChoiceSegmentedButtonRow] do Material 3; cores e shapes vêm do tema.
 * Cada segmento expõe [contentDescription] para acessibilidade.
 *
 * ```kotlin
 * SegmentedControl(
 *     options = listOf("Coberta", "Descoberta"),
 *     selectedIndex = tipo,
 *     onOptionSelected = { tipo = it }
 * )
 * ```
 *
 * @param options Rótulos das opções, na ordem de exibição.
 * @param selectedIndex Índice da opção atualmente selecionada.
 * @param onOptionSelected Callback com o índice da opção escolhida.
 * @param modifier Modificador externo.
 * @param enabled `false` desabilita todos os segmentos (ex.: formulário salvando).
 * @param optionContentDescriptions Descrições de acessibilidade por opção, quando o rótulo sozinho
 *   não basta. Numa **matriz** de opções (várias linhas com as mesmas 3 escolhas), "Ver" isolado não
 *   diz a que se refere — passe "Agenda, Ver" para o leitor de tela anunciar a linha. `null`
 *   (default) usa o próprio rótulo. Índices ausentes caem no rótulo.
 * @param testTag Id do GRUPO para automação (`tela-elemento`, ex.: `calculadora-unidade`), aplicado
 *   na linha e usado como prefixo do id de cada segmento. `null` (default) = sem id na linha e
 *   segmentos com o prefixo [OptionTestTags.SEGMENTED_GROUP]. Passe-o aqui em vez de
 *   `Modifier.testTag` — pelo modifier o componente não o enxerga e os segmentos ficam no prefixo
 *   genérico.
 * @param optionTestKeys Chave por opção para o id do segmento (`<testTag>-<chave>`, ex.:
 *   `listOf("arroba", "kg")` → `calculadora-unidade-kg`). `null` (default) = o índice
 *   (`calculadora-unidade-1`). Ver [OptionTestTags].
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    optionContentDescriptions: List<String>? = null,
    testTag: String? = null,
    optionTestKeys: List<String>? = null,
) {
    if (options.isEmpty()) return

    val optionTags = OptionTestTags.options(
        group = testTag ?: OptionTestTags.SEGMENTED_GROUP,
        count = options.size,
        keys = optionTestKeys,
    )
    SingleChoiceSegmentedButtonRow(
        modifier = if (testTag != null) modifier.testTag(testTag) else modifier
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            SegmentedButton(
                selected = selected,
                enabled = enabled,
                onClick = { onOptionSelected(index) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size
                ),
                // Selecionado preenchido com a cor primária (alto contraste em tema claro e escuro).
                // O default do Material (secondaryContainer) some em temas com container muito claro.
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                    activeBorderColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.testTag(optionTags[index]).semantics {
                    // O SegmentedButton já é `selectable` (Role.RadioButton); explícito para o
                    // contrato de automação (`selected: true` no Maestro) não depender do M3.
                    this.selected = selected
                    val base = optionContentDescriptions?.getOrNull(index)?.takeIf { it.isNotBlank() }
                        ?: label
                    contentDescription =
                        if (selected) "$base, selecionado" else base
                }
            ) {
                Text(text = label)
            }
        }
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun SegmentedControlPreview() {
    AppTheme {
        SegmentedControl(
            options = listOf("Coberta", "Descoberta"),
            selectedIndex = 0,
            onOptionSelected = {}
        )
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun SegmentedControlThreeOptionsPreview() {
    AppTheme {
        SegmentedControl(
            options = listOf("Pix", "Cartão", "Dinheiro"),
            selectedIndex = 1,
            onOptionSelected = {}
        )
    }
}
