package br.com.codecacto.kmplib.ui.components

import br.com.codecacto.kmplib.ui.components.ReorderAction.MOVE_DOWN
import br.com.codecacto.kmplib.ui.components.ReorderAction.MOVE_TO_BOTTOM
import br.com.codecacto.kmplib.ui.components.ReorderAction.MOVE_TO_TOP
import br.com.codecacto.kmplib.ui.components.ReorderAction.MOVE_UP
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ReorderableListTest {

    private val abcde = listOf("a", "b", "c", "d", "e")

    @Test
    fun `mover para baixo escorrega os do meio para cima`() {
        assertEquals(listOf("b", "c", "a", "d", "e"), reorderMove(abcde, 0, 2))
    }

    @Test
    fun `mover para cima escorrega os do meio para baixo`() {
        assertEquals(listOf("a", "e", "b", "c", "d"), reorderMove(abcde, 4, 1))
    }

    @Test
    fun `mesmo indice ou fora da lista devolve a mesma lista`() {
        assertSame(abcde, reorderMove(abcde, 2, 2))
        assertSame(abcde, reorderMove(abcde, -1, 2))
        assertSame(abcde, reorderMove(abcde, 0, 5))
    }

    @Test
    fun `acoes do primeiro do segundo do meio do penultimo e do ultimo`() {
        assertEquals(listOf(MOVE_DOWN, MOVE_TO_BOTTOM), reorderActionsFor(0, 5))
        assertEquals(listOf(MOVE_UP, MOVE_DOWN, MOVE_TO_BOTTOM), reorderActionsFor(1, 5))
        assertEquals(listOf(MOVE_TO_TOP, MOVE_UP, MOVE_DOWN, MOVE_TO_BOTTOM), reorderActionsFor(2, 5))
        assertEquals(listOf(MOVE_TO_TOP, MOVE_UP, MOVE_DOWN), reorderActionsFor(3, 5))
        assertEquals(listOf(MOVE_TO_TOP, MOVE_UP), reorderActionsFor(4, 5))
    }

    @Test
    fun `lista de um item ou indice invalido nao tem acao`() {
        assertEquals(emptyList(), reorderActionsFor(0, 1))
        assertEquals(emptyList(), reorderActionsFor(0, 0))
        assertEquals(emptyList(), reorderActionsFor(7, 5))
    }

    @Test
    fun `lista de dois itens so tem cima e baixo`() {
        assertEquals(listOf(MOVE_DOWN), reorderActionsFor(0, 2))
        assertEquals(listOf(MOVE_UP), reorderActionsFor(1, 2))
    }

    @Test
    fun `destino de cada acao`() {
        assertEquals(0, reorderTargetIndex(MOVE_TO_TOP, 3, 5))
        assertEquals(2, reorderTargetIndex(MOVE_UP, 3, 5))
        assertEquals(4, reorderTargetIndex(MOVE_DOWN, 3, 5))
        assertEquals(4, reorderTargetIndex(MOVE_TO_BOTTOM, 1, 5))
        assertEquals(0, reorderTargetIndex(MOVE_UP, 0, 5))
        assertEquals(4, reorderTargetIndex(MOVE_DOWN, 4, 5))
    }

    @Test
    fun `acao aplicada com reorderMove`() {
        val alvo = reorderTargetIndex(MOVE_TO_TOP, 3, abcde.size)
        assertEquals(listOf("d", "a", "b", "c", "e"), reorderMove(abcde, 3, alvo))
    }

    @Test
    fun `texto de posicao nos modelos da lib`() {
        assertEquals("Posição 2 de 5", formatReorderPosition("Posição %1\$d de %2\$d", 2, 5))
        assertEquals("Position 10 of 12", formatReorderPosition("Position %1\$d of %2\$d", 10, 12))
    }
}
