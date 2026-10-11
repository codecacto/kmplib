package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * GAP-TEXTSTATE-SAVER-01 (2.290.0): o estado salvo do campo é só texto + seleção — nada do histórico
 * de desfazer, que é o que o `TextUndoManager.Saver` do Compose não consegue restaurar no iOS.
 */
class TextOnlyTextFieldStateSaverTest {

    private val scope = SaverScope { true }

    private fun salvar(state: TextFieldState): Any = with(TextOnlyTextFieldStateSaver) { scope.save(state) }

    /** Digitar e apagar — o caminho que enche a pilha de desfazer e derrubava o app no iOS. */
    private fun campoDigitadoEApagado(): TextFieldState = TextFieldState("").apply {
        edit { insert(0, "dipirona 500") }
        edit { delete(8, 12) }
        edit { insert(length, " 1g") }
    }

    @Test
    fun `salva so texto e selecao - tres valores - sem o desfazer`() {
        val estado = campoDigitadoEApagado()
        val salvo = salvar(estado)

        assertEquals(listOf("dipirona 1g", 11, 11), salvo)
        // Tipos que o Bundle e o registro de estado do iOS aceitam — nada de lista dentro de lista.
        (salvo as List<*>).forEach { assertTrue(it is String || it is Int, "tipo não salvável: $it") }
    }

    @Test
    fun `ida e volta preserva texto e selecao`() {
        val estado = TextFieldState("receita", TextRange(2, 5))
        val restaurado = assertNotNull(TextOnlyTextFieldStateSaver.restore(salvar(estado)))

        assertEquals("receita", restaurado.text.toString())
        assertEquals(TextRange(2, 5), restaurado.selection)
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Test
    fun `restaurado depois de digitar e apagar nao lanca e volta igual`() {
        val estado = campoDigitadoEApagado()
        val restaurado = assertNotNull(TextOnlyTextFieldStateSaver.restore(salvar(estado)))

        assertEquals(estado.text.toString(), restaurado.text.toString())
        assertEquals(estado.selection, restaurado.selection)
        // O desfazer recomeça vazio: nada a desfazer do que foi digitado antes de sair da tela.
        assertTrue(!restaurado.undoState.canUndo)
    }

    @Test
    fun `restaurado continua editavel`() {
        val restaurado = assertNotNull(TextOnlyTextFieldStateSaver.restore(listOf("abc", 3, 3)))
        restaurado.setTextAndPlaceCursorAtEnd("abcd")
        assertEquals("abcd", restaurado.text.toString())
        assertEquals(TextRange(4), restaurado.selection)
    }

    @Test
    fun `selecao fora do texto e recortada para dentro dele`() {
        val restaurado = assertNotNull(TextOnlyTextFieldStateSaver.restore(listOf("ab", 7, -3)))
        assertEquals(TextRange(2, 0), restaurado.selection)
        assertEquals(TextRange(0, 2), TextOnlyTextFieldStateSaver.coerceSelection("ab", -1, 99))
    }

    @Test
    fun `formato inesperado devolve nulo - o rememberSaveable cai no valor inicial - sem derrubar o app`() {
        assertNull(TextOnlyTextFieldStateSaver.restore("texto solto"))
        assertNull(TextOnlyTextFieldStateSaver.restore(emptyList<Any>()))
        assertNull(TextOnlyTextFieldStateSaver.restore(listOf("a", 1)))
        assertNull(TextOnlyTextFieldStateSaver.restore(listOf(1, 1, 1)))
        assertNull(TextOnlyTextFieldStateSaver.restore(listOf("a", "1", 1)))
        assertNull(TextOnlyTextFieldStateSaver.restore(listOf("a", 1, listOf(1))))
    }

    @Test
    fun `estado salvo pela versao anterior - Saver do Compose - e lido sem lancar`() {
        // Processo morto com a kmplib antiga e reaberto com a nova: o Bundle traz o formato do
        // `TextFieldState.Saver`. Ler esse formato não pode derrubar o app — ou volta o texto, ou nulo.
        val estado = campoDigitadoEApagado()
        val antigo = with(TextFieldState.Saver) { scope.save(estado) }
        val restaurado = antigo?.let { TextOnlyTextFieldStateSaver.restore(it) }
        if (restaurado != null) assertEquals(estado.text.toString(), restaurado.text.toString())
    }
}
