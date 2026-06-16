package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.LocalIsCompact

/**
 * Estado de seleção em massa de uma [MultiSelectList], **derivado** (puro, sem Compose) a partir do
 * conjunto de ids selecionados e da lista total de ids. Útil para o header/topbar de seleção e
 * testável isoladamente.
 *
 * @property selectedCount quantidade de itens atualmente selecionados.
 * @property totalCount quantidade total de itens selecionáveis.
 * @property allSelected `true` quando todos os itens (e há ao menos um) estão selecionados.
 * @property noneSelected `true` quando nenhum item está selecionado.
 */
data class MultiSelectSummary(
    val selectedCount: Int,
    val totalCount: Int,
) {
    val allSelected: Boolean get() = totalCount > 0 && selectedCount >= totalCount
    val noneSelected: Boolean get() = selectedCount == 0
}

/**
 * Calcula o [MultiSelectSummary] a partir do conjunto de ids selecionados e do total de ids
 * **selecionáveis** (`enabledIds`). Conta apenas ids selecionados que ainda existem na lista
 * selecionável (ignora ids "fantasma" que sobraram no Set após a lista mudar). Lógica pura e
 * testável — sem Compose.
 *
 * @param selectedIds ids atualmente marcados.
 * @param enabledIds ids dos itens que podem ser selecionados (normalmente os não-desabilitados).
 */
fun multiSelectSummary(
    selectedIds: Set<String>,
    enabledIds: List<String>,
): MultiSelectSummary {
    val enabledSet = enabledIds.toHashSet()
    val effective = selectedIds.count { it in enabledSet }
    return MultiSelectSummary(selectedCount = effective, totalCount = enabledIds.size)
}

/**
 * Resolve o novo conjunto de ids após alternar um único item. Lógica pura (sem Compose) para o
 * consumidor que prefere derivar o próximo estado em vez de tratar no callback. Idempotente:
 * marcar→desmarcar→marcar volta ao estado anterior.
 */
fun toggleSelection(current: Set<String>, id: String): Set<String> =
    if (id in current) current - id else current + id

/**
 * Resolve o novo conjunto de ids ao acionar "selecionar todos / limpar". Se **todos** os
 * `enabledIds` já estão selecionados, limpa (remove todos); caso contrário, seleciona todos os
 * habilitados (preservando quaisquer ids já presentes). Lógica pura (sem Compose).
 */
fun toggleAllSelection(current: Set<String>, enabledIds: List<String>): Set<String> {
    val summary = multiSelectSummary(current, enabledIds)
    return if (summary.allSelected) {
        current - enabledIds.toSet()
    } else {
        current + enabledIds
    }
}

/**
 * Lista **genérica** de multi-seleção com **seleção em massa**, controlada (estado *hoisted*).
 * Componente-base para fluxos de "aplicação em lote" do ecossistema (ex.: aplicar uma vacinação a N
 * animais do Rebanho no MinhasVacinas, mover/excluir vários itens, atribuir em massa). **Não acopla
 * nenhum domínio** — o consumidor fornece os itens, a chave de cada um e renderiza a linha como
 * quiser via o slot [itemContent].
 *
 * O conjunto de selecionados é **controlado pelo chamador** (`Set<String>` *hoisted*): a lib não
 * guarda estado interno de seleção. O componente apenas:
 *  - desenha um **checkbox por linha** (área de toque = linha inteira, acessível) ao lado do
 *    conteúdo do consumidor;
 *  - oferece um **header de seleção** opcional com a contagem ("N selecionados") e a ação
 *    **selecionar todos / limpar** ([onToggleAll]);
 *  - dispara [onToggleItem] (id) ao tocar uma linha e [onToggleAll] na ação em massa.
 *
 * As cores vêm SEMPRE da camada de tema ([MaterialTheme.colorScheme]) — nunca `Color(...)`
 * hardcoded — e o componente é responsivo via [LocalIsCompact] (espaçamentos maiores no expandido).
 * Reaproveita o `Checkbox` do Material 3 (mesmo do [AppCheckbox]) e o [TextButton] temático — sem
 * duplicar primitivos.
 *
 * ### Exemplo (aplicação em lote — Rebanho)
 * ```kotlin
 * var selecionados by remember { mutableStateOf(emptySet<String>()) }
 * MultiSelectList(
 *     items = animais,
 *     key = { it.id },
 *     enabled = { !it.jaVacinado },                 // animais já vacinados não selecionáveis
 *     selectedIds = selecionados,
 *     onToggleItem = { id -> selecionados = toggleSelection(selecionados, id) },
 *     onToggleAll = {
 *         selecionados = toggleAllSelection(selecionados, animais.filterNot { it.jaVacinado }.map { it.id })
 *     },
 *     showHeader = true,
 *     selectedLabel = { n -> "$n selecionados" },
 *     header = { FilterChipRow(...) },              // filtros por faixa etária (slot)
 * ) { animal ->
 *     Row { Text("${animal.brinco}  ${animal.sexo}  ${animal.idadeLabel}") }
 * }
 * ```
 *
 * @param items itens a exibir, em ordem.
 * @param key extrai o id estável de cada item (chave da `LazyColumn`, payload de [onToggleItem] e
 *   base da seleção). Deve ser único por item.
 * @param selectedIds ids atualmente selecionados (estado controlado pelo chamador).
 * @param onToggleItem callback ao alternar um item (recebe o id). O chamador atualiza [selectedIds]
 *   (ex.: via [toggleSelection]).
 * @param itemContent slot que renderiza a **linha** do consumidor (à direita do checkbox). Recebe o
 *   item.
 * @param modifier modificador externo.
 * @param onToggleAll callback da ação "selecionar todos / limpar". `null` esconde a ação em massa do
 *   header (o checkbox por linha continua). O chamador decide selecionar/limpar (ex.: via
 *   [toggleAllSelection]).
 * @param enabled predicado que indica se um item é selecionável; itens com `false` aparecem
 *   esmaecidos, sem checkbox interativo, e **não entram** na contagem nem em "selecionar todos".
 *   Default: todos selecionáveis.
 * @param showHeader exibe o header/topbar de seleção (contagem + ação em massa). Default `true`.
 * @param selectedLabel formata o texto de contagem a partir do nº de selecionados (i18n pelo app).
 *   Default `"N selecionado(s)"`.
 * @param selectAllLabel rótulo da ação quando **nem todos** estão selecionados. Default
 *   `"Selecionar todos"`.
 * @param clearLabel rótulo da ação quando **todos** estão selecionados (vira "limpar"). Default
 *   `"Limpar seleção"`.
 * @param header slot opcional renderizado **acima** da lista e abaixo do header de seleção (ex.:
 *   `FilterChipRow` de faixa etária/lote). Recebe o [MultiSelectSummary] atual.
 * @param contentPadding padding em torno do conteúdo da `LazyColumn`.
 */
@Composable
fun <T> MultiSelectList(
    items: List<T>,
    key: (T) -> String,
    selectedIds: Set<String>,
    onToggleItem: (String) -> Unit,
    itemContent: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
    onToggleAll: (() -> Unit)? = null,
    enabled: (T) -> Boolean = { true },
    showHeader: Boolean = true,
    selectedLabel: (Int) -> String = { n -> if (n == 1) "1 selecionado" else "$n selecionados" },
    selectAllLabel: String = "Selecionar todos",
    clearLabel: String = "Limpar seleção",
    header: (@Composable (MultiSelectSummary) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
) {
    val isCompact = LocalIsCompact.current
    val enabledIds = items.filter(enabled).map(key)
    val summary = multiSelectSummary(selectedIds, enabledIds)

    Column(modifier = modifier.fillMaxWidth()) {
        if (showHeader) {
            MultiSelectHeader(
                summary = summary,
                onToggleAll = onToggleAll,
                selectedLabel = selectedLabel,
                selectAllLabel = selectAllLabel,
                clearLabel = clearLabel,
            )
        }
        header?.invoke(summary)

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding,
        ) {
            items(items, key = key) { item ->
                val id = key(item)
                MultiSelectRow(
                    selected = id in selectedIds,
                    enabled = enabled(item),
                    onToggle = { onToggleItem(id) },
                    rowVerticalPadding = if (isCompact) 8.dp else 12.dp,
                ) {
                    itemContent(item)
                }
            }
        }
    }
}

/** Header/topbar de seleção: contagem ("N selecionados") + ação "selecionar todos / limpar". */
@Composable
private fun MultiSelectHeader(
    summary: MultiSelectSummary,
    onToggleAll: (() -> Unit)?,
    selectedLabel: (Int) -> String,
    selectAllLabel: String,
    clearLabel: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = selectedLabel(summary.selectedCount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (onToggleAll != null && summary.totalCount > 0) {
            TextButton(onClick = onToggleAll) {
                Text(text = if (summary.allSelected) clearLabel else selectAllLabel)
            }
        }
    }
}

/** Uma linha selecionável: checkbox (alvo = linha inteira, acessível) + conteúdo do consumidor. */
@Composable
private fun MultiSelectRow(
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    rowVerticalPadding: Dp,
    content: @Composable () -> Unit,
) {
    val rowBackground = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        Color.Transparent
    }
    val contentAlpha = if (enabled) 1f else 0.45f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(rowBackground)
            .toggleable(
                value = selected,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .padding(horizontal = 12.dp, vertical = rowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // onCheckedChange = null: o toggle é tratado pela Row (evita nó semântico duplicado).
        Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
        Box(modifier = Modifier.weight(1f).alpha(contentAlpha)) {
            content()
        }
    }
}
