package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Item exibível num [ThemeChipGrid]. Genérico — NÃO acoplado a "tema de salmo":
 * serve a qualquer grade de chips (categorias, tags, atalhos, filtros).
 *
 * @param id Identificador estável do chip (devolvido em `onChipClick`/usado em `selectedIds`).
 * @param label Texto exibido no chip.
 * @param leadingIcon Ícone opcional à esquerda do rótulo (ex.: ícone por tema).
 */
@Immutable
data class ChipItem(
    val id: String,
    val label: String,
    val leadingIcon: ImageVector? = null
)

/**
 * Grade de chips clicáveis distribuídos em **múltiplas linhas** (wrap/flow), responsiva.
 *
 * Diferente do [FilterChipRow] (LazyRow horizontal, single-select de filtro), o
 * [ThemeChipGrid] quebra os chips em várias linhas e tem dois modos:
 *
 * - **Ação (padrão):** cada chip é um botão de ação ([AssistChip]). Tocar dispara
 *   `onChipClick(id)` — ideal para *navegação por toque* (ex.: tela de Busca por Tema,
 *   onde tocar num tema leva à listagem de resultados).
 * - **Selecionável:** ao passar [selectedIds] não-vazio (ou simplesmente usá-lo para
 *   controlar estado), os chips viram [FilterChip] e refletem o estado de seleção —
 *   habilita reuso futuro como multi-select sem trocar de componente. Nesse modo,
 *   `onChipClick(id)` é o sinal de toggle (o chamador atualiza o conjunto selecionado).
 *
 * Responsividade: o número máximo de chips por linha cresce no modo largo
 * ([wideThreshold]); em telas estreitas o [FlowRow] já quebra naturalmente conforme
 * a largura disponível.
 *
 * Cores/shape vêm de [androidx.compose.material3.MaterialTheme] via tema da kmplib
 * (qualquer paleta do [AppTheme], sem cor hardcoded). Cada chip expõe
 * [contentDescription] para acessibilidade.
 *
 * Exemplo (Busca por Tema — navegação por toque):
 * ```kotlin
 * ThemeChipGrid(
 *     items = temas.map { ChipItem(id = it.id, label = it.nome) },
 *     onChipClick = { id -> navController.navigate(Route.Resultados(id)) }
 * )
 * ```
 *
 * Exemplo (multi-select):
 * ```kotlin
 * ThemeChipGrid(
 *     items = tags,
 *     selectedIds = selecionadas,
 *     onChipClick = { id ->
 *         selecionadas = if (id in selecionadas) selecionadas - id else selecionadas + id
 *     }
 * )
 * ```
 *
 * @param items Itens a renderizar como chips.
 * @param onChipClick Callback com o `id` do chip tocado (navegar ou alternar seleção).
 * @param modifier Modificador externo.
 * @param selectedIds Ids selecionados; quando não-vazio ativa o modo selecionável
 *   ([FilterChip]). Vazio (padrão) usa chips de ação ([AssistChip]).
 * @param maxItemsPerRowCompact Máximo de chips por linha em telas estreitas.
 * @param maxItemsPerRowWide Máximo de chips por linha em telas largas (>= [wideThreshold]).
 * @param wideThreshold Largura a partir da qual a grade é considerada "larga".
 * @param horizontalSpacing Espaço horizontal entre chips.
 * @param verticalSpacing Espaço vertical entre linhas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeChipGrid(
    items: List<ChipItem>,
    onChipClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedIds: Set<String> = emptySet(),
    maxItemsPerRowCompact: Int = 3,
    maxItemsPerRowWide: Int = 5,
    wideThreshold: Dp = 600.dp,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp
) {
    if (items.isEmpty()) return

    val selectable = selectedIds.isNotEmpty()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxPerRow =
            if (maxWidth >= wideThreshold) maxItemsPerRowWide else maxItemsPerRowCompact

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            maxItemsInEachRow = maxPerRow
        ) {
            items.forEach { item ->
                val selected = item.id in selectedIds
                val description =
                    if (selectable && selected) "${item.label}, selecionado" else item.label

                if (selectable) {
                    FilterChip(
                        selected = selected,
                        onClick = { onChipClick(item.id) },
                        label = { Text(item.label) },
                        leadingIcon = item.leadingIcon?.let { icon ->
                            {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        },
                        modifier = Modifier.semantics { contentDescription = description }
                    )
                } else {
                    AssistChip(
                        onClick = { onChipClick(item.id) },
                        label = { Text(item.label) },
                        leadingIcon = item.leadingIcon?.let { icon ->
                            {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                )
                            }
                        },
                        modifier = Modifier.semantics { contentDescription = description }
                    )
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun ThemeChipGridActionPreview() {
    AppTheme {
        ThemeChipGrid(
            items = listOf(
                ChipItem("ansiedade", "Ansiedade"),
                ChipItem("gratidao", "Gratidão"),
                ChipItem("protecao", "Proteção"),
                ChipItem("perdao", "Perdão"),
                ChipItem("luto", "Luto"),
                ChipItem("esperanca", "Esperança")
            ),
            onChipClick = {}
        )
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun ThemeChipGridSelectablePreview() {
    AppTheme {
        ThemeChipGrid(
            items = listOf(
                ChipItem("ansiedade", "Ansiedade"),
                ChipItem("gratidao", "Gratidão"),
                ChipItem("protecao", "Proteção"),
                ChipItem("perdao", "Perdão")
            ),
            selectedIds = setOf("gratidao", "perdao"),
            onChipClick = {}
        )
    }
}
