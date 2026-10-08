package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * `FormContainer(contentPadding = innerPadding)` — o padding do Scaffold tem de ser aplicado E
 * consumido; zero não pode acrescentar nó (todo formulário da fábrica passa por aqui).
 */
class FormContainerContentPaddingTest {

    @Test
    fun paddingZeroEZero() {
        assertTrue(PaddingValues(0.dp).isZero())
        assertTrue(PaddingValues().isZero())
    }

    @Test
    fun qualquerLadoNaoZeroNaoEZero() {
        assertFalse(PaddingValues(bottom = 56.dp).isZero())
        assertFalse(PaddingValues(top = 64.dp).isZero())
        assertFalse(PaddingValues(start = 1.dp).isZero())
        assertFalse(PaddingValues(end = 1.dp).isZero())
    }

    @Test
    fun paddingZeroNaoMexeNaCadeia() {
        val base = Modifier
        assertSame(base, base.scaffoldContentPadding(PaddingValues(0.dp)))
    }

    @Test
    fun paddingDoScaffoldAcrescentaPaddingEConsumo() {
        val base = Modifier
        val resultado = base.scaffoldContentPadding(PaddingValues(top = 64.dp, bottom = 116.dp))
        assertNotEquals<Modifier>(base, resultado)
        // padding + consumeWindowInsets = dois elementos na cadeia.
        assertEquals(2, resultado.foldIn(0) { n, _ -> n + 1 })
    }
}
