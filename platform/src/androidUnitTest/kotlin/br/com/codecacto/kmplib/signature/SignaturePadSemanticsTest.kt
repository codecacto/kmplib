package br.com.codecacto.kmplib.signature

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsModifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Trava a acessibilidade do [SignaturePad] (2.262.4): o quadro era um `Canvas` só com
 * `pointerInput` — sem nome, o leitor de tela não o anunciava e, no iOS, o nó (e o `testTag` do
 * app junto) podia sumir da árvore. Caso ReciboFácil `8a8c1cf`.
 */
@OptIn(ExperimentalComposeUiApi::class)
class SignaturePadSemanticsTest {

    private val texts = signaturePadTexts("pt-BR")

    private fun Modifier.configs(): List<SemanticsConfiguration> =
        foldIn(emptyList<SemanticsConfiguration>()) { acc, e ->
            val m = e as? SemanticsModifier
            if (m != null) acc + listOf(m.semanticsConfiguration) else acc
        }

    private fun raiz(isEmpty: Boolean, app: Modifier = Modifier, onClear: () -> Unit = {}, onUndo: () -> Unit = {}) =
        signaturePadRootModifier(app, "Assinatura do emitente", isEmpty, texts, onClear, onUndo)

    @Test
    fun `vazio - nome e estado sem assinatura, sem acoes`() {
        val c = raiz(isEmpty = true).configs().single()
        assertEquals(listOf("Assinatura do emitente"), c.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals("Sem assinatura", c.getOrNull(SemanticsProperties.StateDescription))
        assertNull(c.getOrNull(SemanticsActions.CustomActions))
    }

    @Test
    fun `assinado - estado assinado e acoes limpar e desfazer que chamam o estado`() {
        var limpou = 0
        var desfez = 0
        val c = raiz(isEmpty = false, onClear = { limpou++ }, onUndo = { desfez++ }).configs().single()
        assertEquals("Assinado", c.getOrNull(SemanticsProperties.StateDescription))
        val acoes = assertNotNull(c.getOrNull(SemanticsActions.CustomActions))
        assertEquals(listOf("Limpar assinatura", "Desfazer último traço"), acoes.map { it.label })
        assertTrue(acoes[0].action())
        assertTrue(acoes[1].action())
        assertEquals(1, limpou)
        assertEquals(1, desfez)
    }

    @Test
    fun `semantica da lib vem ANTES do modifier do app, e o testTag do app fica na cadeia`() {
        val doApp = Modifier.testTag("assinatura-emitente")
        val elementos = raiz(isEmpty = true, app = doApp).foldIn(emptyList<Modifier.Element>()) { acc, e -> acc + e }
        assertEquals(2, elementos.size)
        // 1º: a semântica da lib (nome do quadro); 2º: o modifier do app, intacto.
        val primeiro = elementos[0] as SemanticsModifier
        assertEquals(
            listOf("Assinatura do emitente"),
            primeiro.semanticsConfiguration.getOrNull(SemanticsProperties.ContentDescription),
        )
        assertTrue(elementos[1] === doApp, "o modifier do app tem de vir depois, sem ser trocado")
    }

    @Test
    fun `o estado de verdade do pad chega na semantica`() {
        val state = SignaturePadState()
        fun estado() = signaturePadRootModifier(Modifier, "x", state.isEmpty, texts, state::clear, state::undo)
            .configs().single().getOrNull(SemanticsProperties.StateDescription)
        assertEquals("Sem assinatura", estado())
        state.strokes.add(mutableListOf(Offset2D(1f, 1f), Offset2D(2f, 2f)))
        assertEquals("Assinado", estado())
        val limpar = signaturePadRootModifier(Modifier, "x", state.isEmpty, texts, state::clear, state::undo)
            .configs().single().getOrNull(SemanticsActions.CustomActions)!!.first()
        limpar.action()
        assertTrue(state.isEmpty)
    }
}
