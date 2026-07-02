package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Densidade da grade acessível de comunicação — **escolha explícita do usuário**, persistida pelo
 * app (não derivada de breakpoint/`LocalIsCompact`).
 *
 * - [One]: um alvo enorme por vez (máxima acessibilidade / baixa visão / motricidade reduzida).
 * - [Two]: grade de 2 colunas.
 * - [Three]: grade de 3 colunas (mais itens visíveis).
 *
 * [columns] é o número de colunas correspondente — chave de layout do [DensityGrid], pura e testável.
 */
enum class GridDensity(val columns: Int) {
    One(1),
    Two(2),
    Three(3)
}

/**
 * Grade acessível de densidade variável (1/2/3 colunas), reutilizável por qualquer app do tipo
 * "prancha/board/launcher" acessível (CAA, atalhos, respostas rápidas).
 *
 * O número de colunas é **exatamente** [density] `.columns` — a densidade é uma preferência do
 * usuário (hoisted fora do componente, ex.: [SegmentedControl] na top bar), **independente** do
 * breakpoint. No modo 1 coluna os alvos ficam naturalmente enormes (largura cheia).
 *
 * Filosofia data + slot, igual às listas existentes da lib ([MultiSelectList]/`TimelineList`): você
 * fornece os `items`, uma `key` estável e o `itemContent` (normalmente um [CommunicationTile]).
 *
 * @param items dados a exibir.
 * @param density densidade escolhida pelo usuário → nº de colunas.
 * @param key identidade estável de cada item (recomposição/scroll eficientes).
 * @param modifier modificador do container.
 * @param contentPadding padding interno do conteúdo rolável (default 16dp).
 * @param header slot opcional renderizado no topo, ocupando a largura inteira (todas as colunas) —
 *   ex.: faixa urgente de "respostas rápidas" ou título de categoria.
 * @param itemContent composable de cada célula (o alvo de toque).
 */
@Composable
fun <T> DensityGrid(
    items: List<T>,
    density: GridDensity,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    header: (@Composable () -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(density.columns),
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (header != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                header()
            }
        }
        items(items = items, key = { key(it) }) { item ->
            itemContent(item)
        }
    }
}
