package br.com.codecacto.kmplib.ui.tour

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo

/**
 * Marca este elemento como alvo do tour guiado: o passo `TourStep(targetId = id, …)` o destaca.
 *
 * - Fora de um [ProductTourHost] não faz nada (custo zero) — pode ficar no código sempre.
 * - Na barra de baixo, marque o ITEM (`AppBottomNavBar` aceita `modifier` por item via o slot do
 *   app; no `NavigationBarItem` do Material, o `modifier` dele), não a barra inteira.
 * - Ids são do app e estáveis (`"nav-locacoes"`, `"btn-nova-locacao"`); o mesmo id em dois nós
 *   (celular e tablet) é permitido — vale o que estiver visível.
 *
 * Implementado como `Modifier.Node` (a forma recomendada do Compose para modificador com estado):
 * mede com `boundsInRoot` a cada reposicionamento, então o destaque acompanha rolagem e rotação.
 */
fun Modifier.tourTarget(id: String): Modifier = this then TourTargetElement(id)

private data class TourTargetElement(val id: String) : ModifierNodeElement<TourTargetNode>() {
    override fun create(): TourTargetNode = TourTargetNode(id)

    override fun update(node: TourTargetNode) {
        node.updateId(id)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "tourTarget"
        properties["id"] = id
    }
}

private class TourTargetNode(
    private var id: String,
) : Modifier.Node(), GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {

    private var registry: TourTargetRegistry? = null

    override fun onAttach() {
        registry = currentValueOf(LocalTourTargetRegistry)
    }

    override fun onDetach() {
        registry?.remove(this)
        registry = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        report(coordinates)
    }

    fun updateId(newId: String) {
        if (newId == id) return
        registry?.remove(this)
        id = newId
        // A posição pode não mudar com o id novo — reporta já, se o nó estiver posicionado.
        if (isAttached) runCatching { requireLayoutCoordinates() }.getOrNull()?.let(::report)
    }

    private fun report(coordinates: LayoutCoordinates) {
        val target = registry ?: return
        if (!coordinates.isAttached) return
        target.report(this, id, coordinates.boundsInRoot())
    }
}
