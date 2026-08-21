package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Campo de escolha em **menu suspenso ancorado** — o "spinner" que todo mundo conhece do Android.
 *
 * ## Quando usar este, e quando usar o [AppPickerField]
 *
 * Os dois resolvem escolha única a partir de uma lista; o que muda é o tamanho e a ordem da lista.
 *
 * | | [AppDropdownField] (este) | [AppPickerField] (sheet) |
 * |---|---|---|
 * | Lista | curta ou média, **em ordem conhecida** (alfabética, cronológica) | longa, em que a pessoa **procura** |
 * | Gesto | abre colado ao campo, escolhe e fecha | abre a metade de baixo da tela |
 * | Custo | nenhum: o campo continua à vista | cobre o formulário |
 *
 * Bairro de uma cidade, mês, turno, categoria com oito itens: **spinner**. UF (27), país, categoria
 * do catálogo inteiro: **sheet** — ali a lista precisa de espaço e de rolagem confortável.
 *
 * O menu nasce com a **largura do campo** (medida em runtime): menu mais estreito que o campo é o
 * detalhe que faz um formulário parecer improvisado. Ele rola sozinho a partir de [maxMenuHeight].
 *
 * O campo é **somente leitura** — quem edita é o menu. Deixar digitar traria de volta o problema que
 * o componente resolve: valor livre que o servidor recusa.
 */
@Composable
fun AppDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<PickerOption>,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helperText: String? = null,
    errorMessage: String? = null,
    enabled: Boolean = true,
    maxMenuHeight: Dp = 320.dp,
) {
    val selecionada = options.firstOrNull { it.value == value }

    AncoraDeMenu(
        modifier = modifier,
        enabled = enabled,
        label = label,
        texto = selecionada?.label.orEmpty(),
        placeholder = placeholder,
        helperText = helperText,
        errorMessage = errorMessage,
        maxMenuHeight = maxMenuHeight,
    ) { fechar ->
        options.forEach { opcao ->
            DropdownMenuItem(
                text = { Text(opcao.label) },
                onClick = {
                    onValueChange(opcao.value)
                    fechar()
                },
                trailingIcon = if (opcao.value == value) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * O mesmo spinner, com **múltipla escolha**: cada linha tem caixa de marcação e o menu **continua
 * aberto** enquanto a pessoa marca — fechar a cada toque obrigaria a reabrir uma vez por item.
 *
 * Existe para substituir a parede de chips ([AppMultiSelect]) quando as opções passam de meia dúzia:
 * vinte chips em `FlowRow` viram um bloco alto e ilegível, e a ordem alfabética se perde no
 * empacotamento das linhas.
 *
 * [lockedValues] são itens **sempre marcados e não desmarcáveis** — o caso típico é o item que a
 * própria regra do produto inclui (o bairro onde a pessoa mora, na lista dos que ela acompanha).
 * Eles aparecem marcados e sem alvo de toque, em vez de sumirem: some da lista quem não existe, não
 * quem já está garantido.
 *
 * O campo resume a seleção ("Centro, Jardim Aurora +2"), porque o valor precisa ser conferível com o
 * menu fechado.
 */
@Composable
fun AppMultiDropdownField(
    values: Set<String>,
    onToggle: (String) -> Unit,
    options: List<PickerOption>,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helperText: String? = null,
    errorMessage: String? = null,
    enabled: Boolean = true,
    lockedValues: Set<String> = emptySet(),
    maxMenuHeight: Dp = 320.dp,
    /** Quantos nomes cabem no campo antes de virar "+N". */
    maxLabelsInField: Int = 2,
) {
    val marcados = values + lockedValues
    val resumo = dropdownFieldSummary(
        labels = options.filter { it.value in marcados }.map { it.label },
        maxLabels = maxLabelsInField,
    )

    AncoraDeMenu(
        modifier = modifier,
        enabled = enabled,
        label = label,
        texto = resumo,
        placeholder = placeholder,
        helperText = helperText,
        errorMessage = errorMessage,
        maxMenuHeight = maxMenuHeight,
    ) { _ ->
        options.forEach { opcao ->
            val travado = opcao.value in lockedValues
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Checkbox(
                            checked = opcao.value in marcados,
                            // O toque é da LINHA inteira: caixa com clique próprio cria dois alvos
                            // para a mesma ação, e o menor deles é o que a pessoa erra.
                            onCheckedChange = null,
                            enabled = !travado,
                        )
                        Text(opcao.label)
                    }
                },
                enabled = !travado,
                onClick = { if (!travado) onToggle(opcao.value) },
            )
        }
    }
}

/**
 * O campo somente-leitura + o menu ancorado nele, com a largura medida do campo.
 *
 * A medida existe porque o `DropdownMenu` do Material 3 se dimensiona pelo conteúdo: sem ela, uma
 * lista de nomes curtos abre um menu estreito, deslocado do campo — o que lê como componente
 * quebrado, não como estilo.
 */
@Composable
private fun AncoraDeMenu(
    modifier: Modifier,
    enabled: Boolean,
    label: String?,
    texto: String,
    placeholder: String?,
    helperText: String?,
    errorMessage: String?,
    maxMenuHeight: Dp,
    itens: @Composable (fechar: () -> Unit) -> Unit,
) {
    var aberto by remember { mutableStateOf(false) }
    var larguraDoCampo by remember { mutableStateOf(0.dp) }
    val densidade = LocalDensity.current

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned {
                    larguraDoCampo = with(densidade) { it.size.width.toDp() }
                }
                .clickable(enabled = enabled, role = Role.DropdownList) { aberto = true }
                .semantics {
                    contentDescription = listOfNotNull(label, texto.ifBlank { null })
                        .joinToString(": ")
                },
        ) {
            AppTextField(
                value = texto,
                onValueChange = {},
                label = label,
                placeholder = placeholder,
                helperText = helperText,
                errorMessage = errorMessage,
                // Desabilitado de propósito: é o que impede o teclado de abrir e a digitação livre.
                enabled = false,
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            )
        }

        DropdownMenu(
            expanded = aberto,
            onDismissRequest = { aberto = false },
            modifier = Modifier
                .width(larguraDoCampo)
                .heightIn(max = maxMenuHeight),
        ) {
            itens { aberto = false }
        }
    }
}

/**
 * O texto que resume a seleção múltipla no campo fechado: os primeiros [maxLabels] nomes e, se
 * sobrar, quantos ficaram de fora ("Centro, Jardim Aurora +2").
 *
 * Lógica pura, fora do composable, porque é ela que decide se a pessoa consegue conferir o que
 * marcou **sem reabrir o menu** — e isso se prova com teste, não olhando a tela.
 */
fun dropdownFieldSummary(labels: List<String>, maxLabels: Int = 2): String = when {
    labels.isEmpty() -> ""
    maxLabels < 1 -> "${labels.size}"
    labels.size <= maxLabels -> labels.joinToString(", ")
    else -> labels.take(maxLabels).joinToString(", ") + " +${labels.size - maxLabels}"
}
