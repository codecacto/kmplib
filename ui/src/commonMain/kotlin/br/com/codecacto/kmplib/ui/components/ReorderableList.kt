package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_reorder_move_down
import br.com.codecacto.kmplib.generated.resources.kmplib_reorder_move_to_bottom
import br.com.codecacto.kmplib.generated.resources.kmplib_reorder_move_to_top
import br.com.codecacto.kmplib.generated.resources.kmplib_reorder_move_up
import br.com.codecacto.kmplib.generated.resources.kmplib_reorder_position
import kotlinx.coroutines.launch
import br.com.codecacto.kmplib.ui.locale.kmpStringResource
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.ReorderableColumn as LibReorderableColumn
import sh.calvin.reorderable.ReorderableListItemScope as LibColumnItemScope

// ---------------------------------------------------------------------------------------------
// Núcleo puro (testado)
// ---------------------------------------------------------------------------------------------

/**
 * A lista com o item de [from] levado para [to] — os demais escorregam uma casa. É exatamente o que
 * o `onMove` do [ReorderableList] pede ao app: `itens = reorderMove(itens, from, to)`.
 *
 * Índice fora da lista ou `from == to` devolve a MESMA lista (nada a mover). Pura e testada.
 */
fun <T> reorderMove(list: List<T>, from: Int, to: Int): List<T> {
    if (from == to || from !in list.indices || to !in list.indices) return list
    return list.toMutableList().apply { add(to, removeAt(from)) }
}

/** Movimentos que a acessibilidade oferece a um item (o leitor de tela não arrasta). */
enum class ReorderAction { MOVE_TO_TOP, MOVE_UP, MOVE_DOWN, MOVE_TO_BOTTOM }

/**
 * Quais [ReorderAction] fazem sentido para o item em [index] numa lista de [size]: o primeiro não
 * sobe, o último não desce, e "para o início/fim" só aparece quando pula mais de uma casa (senão
 * duplicaria "para cima/baixo" no menu do leitor de tela). Ordem = ordem do menu. Pura e testada.
 */
fun reorderActionsFor(index: Int, size: Int): List<ReorderAction> {
    if (size < 2 || index !in 0 until size) return emptyList()
    return buildList {
        if (index > 1) add(ReorderAction.MOVE_TO_TOP)
        if (index > 0) add(ReorderAction.MOVE_UP)
        if (index < size - 1) add(ReorderAction.MOVE_DOWN)
        if (index < size - 2) add(ReorderAction.MOVE_TO_BOTTOM)
    }
}

/** Índice de destino de [action] partindo de [index] numa lista de [size]. Pura e testada. */
fun reorderTargetIndex(action: ReorderAction, index: Int, size: Int): Int = when (action) {
    ReorderAction.MOVE_TO_TOP -> 0
    ReorderAction.MOVE_UP -> (index - 1).coerceAtLeast(0)
    ReorderAction.MOVE_DOWN -> (index + 1).coerceAtMost(size - 1)
    ReorderAction.MOVE_TO_BOTTOM -> size - 1
}

/**
 * Traduz o `(from, to)` que o motor do arrasto soltou (índices em [keysWhenDragged], a ordem de quando
 * o arrasto começou) para índices na lista ATUAL, pela CHAVE. A animação de soltar leva um instante, e
 * a lista pode ter mudado nele: índice velho moveria o item errado. Sem o item, ou sem nada a mover,
 * `null`. Pura e testada.
 */
internal fun <K> reorderSettleIndices(keysWhenDragged: List<K>, currentKeys: List<K>, from: Int, to: Int): Pair<Int, Int>? {
    val fromKey = keysWhenDragged.getOrNull(from) ?: return null
    val toKey = keysWhenDragged.getOrNull(to) ?: return null
    val fromNow = currentKeys.indexOf(fromKey)
    val toNow = currentKeys.indexOf(toKey)
    return if (fromNow < 0 || toNow < 0 || fromNow == toNow) null else fromNow to toNow
}

/** Troca `%1$d`/`%2$d` do modelo de posição ("Posição %1$d de %2$d"). */
internal fun formatReorderPosition(template: String, position: Int, total: Int): String =
    template.replace("%1\$d", position.toString()).replace("%2\$d", total.toString())

// ---------------------------------------------------------------------------------------------
// Textos
// ---------------------------------------------------------------------------------------------

/**
 * Textos do [ReorderableList] — todos de ACESSIBILIDADE (nada aparece escrito na tela). Default
 * [rememberReorderableListTexts] nos 4 idiomas da lib; para trocar um, `.copy(…)`.
 *
 * @param position "Posição 2 de 5" — anunciado como estado do item, para quem não vê a ordem.
 */
@Immutable
data class ReorderableListTexts(
    val moveUp: String,
    val moveDown: String,
    val moveToTop: String,
    val moveToBottom: String,
    val position: (position: Int, total: Int) -> String,
) {
    fun labelFor(action: ReorderAction): String = when (action) {
        ReorderAction.MOVE_TO_TOP -> moveToTop
        ReorderAction.MOVE_UP -> moveUp
        ReorderAction.MOVE_DOWN -> moveDown
        ReorderAction.MOVE_TO_BOTTOM -> moveToBottom
    }
}

@Composable
fun rememberReorderableListTexts(): ReorderableListTexts {
    val up = kmpStringResource(Res.string.kmplib_reorder_move_up)
    val down = kmpStringResource(Res.string.kmplib_reorder_move_down)
    val top = kmpStringResource(Res.string.kmplib_reorder_move_to_top)
    val bottom = kmpStringResource(Res.string.kmplib_reorder_move_to_bottom)
    val position = kmpStringResource(Res.string.kmplib_reorder_position)
    return remember(up, down, top, bottom, position) {
        ReorderableListTexts(
            moveUp = up,
            moveDown = down,
            moveToTop = top,
            moveToBottom = bottom,
            position = { p, t -> formatReorderPosition(position, p, t) },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Componente
// ---------------------------------------------------------------------------------------------

/**
 * Escopo do conteúdo de cada item do [ReorderableList]. Dá a **alça de arrasto** — que arrasta
 * **de imediato**, sem long-press (é o que o Material indica para a alça visível).
 */
interface ReorderableListItemScope {
    /** Alça padrão (ícone `DragHandle`, alvo de 48 dp). Arrasta ao primeiro movimento. */
    @Composable
    fun DragHandle(modifier: Modifier = Modifier)

    /** Transforma QUALQUER elemento em alça (para quem desenha a própria). */
    fun Modifier.dragHandle(): Modifier
}

/**
 * **Lista reordenável por arrasto** (2.221.0 — ER-04): segurar o item (long-press) e arrastar; ou
 * arrastar pela alça ([ReorderableListItemScope.DragHandle]) sem esperar. A lista rola sozinha
 * quando o item chega à borda, os vizinhos abrem espaço animados e o aparelho vibra ao pegar, a
 * cada troca e ao soltar.
 *
 * Construída sobre o **Reorderable** (`sh.calvin.reorderable`), a biblioteca multiplataforma de
 * referência para reordenar `LazyColumn` em Compose — o `foundation` não traz arrasto de lista, e
 * implementar autoscroll + troca por deslocamento à mão é o que ela já resolve e testa.
 *
 * **Acessível sem arrasto:** cada item expõe ações de acessibilidade — **Mover para cima**, **Mover
 * para baixo**, **para o início** e **para o fim** (só as que fazem sentido naquela posição) — e
 * anuncia "Posição 2 de 5". No TalkBack/VoiceOver, o menu de ações do item faz o que o dedo faz.
 *
 * **Contrato do [onMove]:** aplique a troca **síncrona** na lista que você passa em [items]
 * (`itens = reorderMove(itens, from, to)` num `MutableStateFlow`/`mutableStateListOf`). O arrasto
 * chama [onMove] a cada casa atravessada — **não persista ali**. Persista em [onReorderFinished],
 * chamado uma vez ao soltar (se algo mudou) e depois de cada ação de acessibilidade.
 *
 * @param items a lista na ordem atual (fonte de verdade é do app).
 * @param key chave **estável e única** do item (o id, nunca o índice) — é por ela que o arrasto se
 *   orienta; chave repetida quebra a `LazyColumn`.
 * @param onMove leva o item de `from` para `to` (índices em [items]).
 * @param onReorderFinished a ordem assentou — hora de salvar.
 * @param enabled `false` desliga arrasto e ações (ex.: enquanto salva).
 * @param dragOnLongPress `true` (default) = segurar o item em qualquer ponto começa o arrasto;
 *   `false` = só pela alça (use quando o item tem long-press próprio).
 * @param header itens FIXOS antes da lista (não reordenáveis), na mesma rolagem. Desde 2.318.0.
 * @param footer itens FIXOS depois da lista — o botão "+ Adicionar" que rola junto com ela, em vez
 *   de ficar preso fora da lista. Desde 2.318.0.
 * @param itemContent o item; `isDragging` para elevar/realçar o que está sendo arrastado.
 */
@Composable
fun <T> ReorderableList(
    items: List<T>,
    key: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    onReorderFinished: () -> Unit = {},
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    enabled: Boolean = true,
    dragOnLongPress: Boolean = true,
    texts: ReorderableListTexts = rememberReorderableListTexts(),
    header: (LazyListScope.() -> Unit)? = null,
    footer: (LazyListScope.() -> Unit)? = null,
    itemContent: @Composable ReorderableListItemScope.(item: T, index: Int, isDragging: Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentItems by rememberUpdatedState(items)
    val currentKey by rememberUpdatedState(key)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnFinished by rememberUpdatedState(onReorderFinished)
    var movedDuringDrag by remember { mutableStateOf(false) }

    val reorderState = rememberReorderableLazyListState(state) { from, to ->
        // Os índices do LazyListItemInfo são da LazyColumn; a verdade é a CHAVE — mapeia para [items].
        val list = currentItems
        val fromIndex = list.indexOfFirst { currentKey(it) == from.key }
        val toIndex = list.indexOfFirst { currentKey(it) == to.key }
        if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
            currentOnMove(fromIndex, toIndex)
            movedDuringDrag = true
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
    ) {
        header?.invoke(this)
        itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
            ReorderableItem(reorderState, key = key(item), enabled = enabled) { isDragging ->
                val itemScope = this
                val onStart: (Offset) -> Unit = {
                    movedDuringDrag = false
                    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                }
                val onStop: () -> Unit = {
                    haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                    if (movedDuringDrag) {
                        movedDuringDrag = false
                        currentOnFinished()
                    }
                }
                val size = items.size
                val actions = if (enabled) {
                    reorderActionsFor(index, size).map { action ->
                        CustomAccessibilityAction(texts.labelFor(action)) {
                            val target = reorderTargetIndex(action, index, size)
                            currentOnMove(index, target)
                            currentOnFinished()
                            // O item pode ter saído da área visível: leva a lista até ele, para o
                            // foco do leitor de tela não cair num item fora da tela.
                            val visible = state.layoutInfo.visibleItemsInfo
                            if (visible.none { it.index == target }) {
                                scope.launch { state.animateScrollToItem(target) }
                            }
                            true
                        }
                    }
                } else {
                    emptyList()
                }
                val positionText = texts.position(index + 1, size)
                val itemModifier = Modifier
                    .semantics(mergeDescendants = true) {
                        customActions = actions
                        stateDescription = positionText
                    }
                    .then(
                        if (dragOnLongPress) {
                            with(itemScope) {
                                Modifier.longPressDraggableHandle(
                                    enabled = enabled,
                                    onDragStarted = onStart,
                                    onDragStopped = onStop,
                                )
                            }
                        } else {
                            Modifier
                        },
                    )
                val listScope = ReorderableListItemScopeImpl(
                    delegate = itemScope,
                    enabled = enabled,
                    onStart = onStart,
                    onStop = onStop,
                )
                Box(itemModifier) {
                    listScope.itemContent(item, index, isDragging)
                }
            }
        }
        footer?.invoke(this)
    }
}

private class ReorderableListItemScopeImpl(
    private val delegate: ReorderableCollectionItemScope,
    private val enabled: Boolean,
    private val onStart: (Offset) -> Unit,
    private val onStop: () -> Unit,
) : ReorderableListItemScope {

    override fun Modifier.dragHandle(): Modifier = with(delegate) {
        this@dragHandle.draggableHandle(enabled = enabled, onDragStarted = onStart, onDragStopped = onStop)
    }

    @Composable
    override fun DragHandle(modifier: Modifier) {
        ReorderDragHandleIcon(modifier.dragHandle())
    }
}

/** O desenho da alça, igual nas duas listas: [modifier] já traz o gesto. */
@Composable
private fun ReorderDragHandleIcon(modifier: Modifier) {
    // A alça não é um alvo para o leitor de tela: quem não vê usa as ações do ITEM. Sem isto,
    // ela viraria uma parada de foco que não faz nada ao toque duplo.
    Box(
        modifier = modifier
            .size(48.dp)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Coluna reordenável (dentro de um item de outra lista)
// ---------------------------------------------------------------------------------------------

/**
 * **Lista curta reordenável DENTRO de uma rolagem que já existe** (2.339.0) — exercícios de um bloco,
 * perguntas de uma seção: o item de uma `LazyColumn` ou de um [ReorderableList], ou uma coluna com
 * `verticalScroll`. Uma lista lazy não pode morar aí (altura infinita), e por isso o [ReorderableList]
 * só serve como lista de nível superior. Mesma alça, mesmo escopo, mesmas ações de acessibilidade
 * ("Mover para cima"… e "Posição 2 de 5") e mesmos textos; o aparelho vibra ao pegar, a cada troca e
 * ao soltar. Construída sobre o `ReorderableColumn` do Reorderable, o modo dele para coluna não-lazy.
 *
 * ```kotlin
 * ReorderableColumn(
 *     items = bloco.itens,
 *     key = { it.id },
 *     onMove = { de, para -> onAction(Action.Mover(bloco.id, de, para)) },
 *     onReorderFinished = { onAction(Action.Salvar) },
 *     verticalArrangement = Arrangement.spacedBy(12.dp),
 * ) { item, _, arrastando ->
 *     Row(verticalAlignment = Alignment.CenterVertically) {
 *         Text(item.nome, Modifier.weight(1f))
 *         DragHandle()
 *     }
 * }
 * ```
 *
 * **O que muda em relação ao [ReorderableList]:**
 * - [onMove] é chamado **uma vez, ao soltar** (nunca por casa atravessada: durante o arrasto o motor
 *   só desloca o desenho), com `from != to` em [items]. Aplique a troca síncrona ali; [onReorderFinished]
 *   vem logo depois, e também após cada ação de acessibilidade.
 * - **Sem autoscroll.** O motor não conversa com a rolagem de fora: é para lista que cabe na tela. Lista
 *   longa = [ReorderableList], na rolagem dela.
 * - **Só pela alça por default** ([dragOnLongPress] `false`): o item costuma ter campo de texto (segurar
 *   seleciona texto) e está dentro de outro item arrastável.
 * - O estado `remember` de cada item é **recriado** quando ele muda de posição, porque cada posição é
 *   um nó próprio no motor. O dado do item mora no estado do app; o que não mora (foco, rolagem interna)
 *   recomeça.
 *
 * **Dentro de um [ReorderableList]:** passe `dragOnLongPress = false` na lista de fora. Com o long-press
 * dela ligado, segurar a alça de dentro sem mexer o dedo pegaria o item de fora.
 *
 * @param items a lista na ordem atual (fonte de verdade é do app).
 * @param key chave **estável e única** do item (o id, nunca o índice).
 * @param onMove leva o item de `from` para `to` (índices em [items]), ao soltar.
 * @param onReorderFinished a ordem assentou — hora de salvar.
 * @param enabled `false` desliga arrasto e ações (ex.: tela só leitura).
 * @param dragOnLongPress `true` = segurar o item em qualquer ponto também começa o arrasto.
 * @param itemContent o item; `isDragging` para realçar o que está sendo arrastado.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    onReorderFinished: () -> Unit = {},
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    enabled: Boolean = true,
    dragOnLongPress: Boolean = false,
    texts: ReorderableListTexts = rememberReorderableListTexts(),
    itemContent: @Composable ReorderableListItemScope.(item: T, index: Int, isDragging: Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val currentItems by rememberUpdatedState(items)
    val currentKey by rememberUpdatedState(key)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnFinished by rememberUpdatedState(onReorderFinished)

    // O motor refaz o estado de arrasto quando a lista muda por igualdade e guarda os callbacks da
    // primeira composição daquela lista. Daí a lista de CHAVES: digitar num campo do item muda o item
    // (e não pode derrubar um arrasto), mover ou acrescentar muda a chave (e refaz). E, pela mesma
    // razão, os callbacks só leem o estado atualizado.
    val keys = items.map(key)
    LibReorderableColumn(
        list = keys,
        onSettle = { from, to ->
            val now = currentItems.map { currentKey(it) }
            reorderSettleIndices(keys, now, from, to)?.let { (de, para) ->
                currentOnMove(de, para)
                currentOnFinished()
            }
        },
        modifier = modifier,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        onMove = { haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) },
    ) { index, itemKey, isDragging ->
        val item = items.getOrNull(index) ?: return@LibReorderableColumn
        // Sem isto o `remember` do item ficaria na POSIÇÃO e, ao soltar, passaria ao vizinho.
        key(itemKey) {
            ReorderableItem {
                val itemScope = this
                val onStart: (Offset) -> Unit = {
                    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                }
                val onStop: (Float) -> Unit = {
                    haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                }
                val size = items.size
                val actions = if (enabled) {
                    reorderActionsFor(index, size).map { action ->
                        CustomAccessibilityAction(texts.labelFor(action)) {
                            currentOnMove(index, reorderTargetIndex(action, index, size))
                            currentOnFinished()
                            true
                        }
                    }
                } else {
                    emptyList()
                }
                val positionText = texts.position(index + 1, size)
                val itemModifier = Modifier
                    .semantics(mergeDescendants = true) {
                        customActions = actions
                        stateDescription = positionText
                    }
                    .then(
                        if (dragOnLongPress) {
                            with(itemScope) {
                                Modifier.longPressDraggableHandle(
                                    enabled = enabled,
                                    onDragStarted = onStart,
                                    onDragStopped = onStop,
                                )
                            }
                        } else {
                            Modifier
                        },
                    )
                val columnScope = ReorderableColumnItemScopeImpl(itemScope, enabled, onStart, onStop)
                Box(itemModifier) {
                    columnScope.itemContent(item, index, isDragging)
                }
            }
        }
    }
}

private class ReorderableColumnItemScopeImpl(
    private val delegate: LibColumnItemScope,
    private val enabled: Boolean,
    private val onStart: (Offset) -> Unit,
    private val onStop: (Float) -> Unit,
) : ReorderableListItemScope {

    override fun Modifier.dragHandle(): Modifier = with(delegate) {
        this@dragHandle.draggableHandle(enabled = enabled, onDragStarted = onStart, onDragStopped = onStop)
    }

    @Composable
    override fun DragHandle(modifier: Modifier) {
        ReorderDragHandleIcon(modifier.dragHandle())
    }
}
