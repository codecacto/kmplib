package br.com.codecacto.kmplib.ui.components

import androidx.compose.ui.unit.Constraints
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A regra que deixou `ScrollableFillBox`, `ErrorState` e `FormContainer` tolerantes a rolagem
 * aninhada (2.242.1): só se aplica `verticalScroll` próprio com ALTURA LIMITADA.
 *
 * O que o Compose recusa — abortando o app — é `verticalScroll` medido com `maxHeight` infinito, o
 * que acontece dentro de `Column(Modifier.verticalScroll)`, de item de `LazyColumn` e com
 * `verticalScroll` no `modifier` do próprio componente. 68 casos em 21 apps na varredura de 02/out.
 */
class ScrollsItselfTest {

    @Test
    fun `corpo de tela - altura limitada - rola por conta propria`() {
        assertTrue(scrollsItself(Constraints(maxWidth = 360, maxHeight = 640)))
    }

    @Test
    fun `altura fixa tambem e limitada`() {
        assertTrue(scrollsItself(Constraints.fixed(width = 360, height = 320)))
    }

    @Test
    fun `dentro de outra rolagem - altura sem teto - nao aplica a propria rolagem`() {
        assertFalse(scrollsItself(Constraints(maxWidth = 360, maxHeight = Constraints.Infinity)))
    }

    @Test
    fun `item de lista com minimo e sem teto continua sem teto`() {
        assertFalse(scrollsItself(Constraints(minHeight = 48, maxWidth = 360, maxHeight = Constraints.Infinity)))
    }

    @Test
    fun `largura sem teto nao decide nada - so a altura importa`() {
        assertTrue(scrollsItself(Constraints(maxWidth = Constraints.Infinity, maxHeight = 640)))
    }
}
