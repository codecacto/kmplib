package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.floor

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
 * Colunas efetivas da grade dada a densidade escolhida e a largura disponível (dp).
 *
 * Em telas de **celular** (~[referenceWidthDp]) devolve exatamente [density]`.columns` — a densidade
 * do usuário é respeitada como base. Em telas **grandes (tablet)** a grade GANHA colunas na proporção
 * da largura, mantendo os tiles num tamanho confortável em vez de esticá-los. Nunca devolve menos que
 * [density]`.columns` (telas estreitas não colapsam a escolha). Pura e testável.
 *
 * Ex. (celular ~400dp): One→1, Two→2, Three→3. (Tablet ~800dp): One→2, Two→4, Three→6.
 */
fun effectiveGridColumns(
    density: GridDensity,
    availableWidthDp: Int,
    referenceWidthDp: Int = 400,
    maxColumns: Int = 8,
): Int {
    if (availableWidthDp <= 0) return density.columns
    val targetTileWidthDp = referenceWidthDp.toFloat() / density.columns
    val fit = floor(availableWidthDp / targetTileWidthDp).toInt()
    return fit.coerceIn(density.columns, maxColumns)
}

/**
 * Grade acessível de densidade variável (1/2/3 colunas), reutilizável por qualquer app do tipo
 * "prancha/board/launcher" acessível (CAA, atalhos, respostas rápidas).
 *
 * A densidade é a **escolha do usuário** (hoisted fora do componente, ex.: [SegmentedControl] na top
 * bar) e vale como **base**: em telas de celular a grade usa exatamente [GridDensity.columns]; em
 * **tablet** ela ganha colunas proporcionalmente à largura (ver [effectiveGridColumns]), pra não
 * deixar os botões gigantes/esticados. No modo 1 coluna (celular) o alvo fica em largura cheia.
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
    BoxWithConstraints(modifier = modifier) {
        val columns = effectiveGridColumns(density, maxWidth.value.toInt())
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
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
}
