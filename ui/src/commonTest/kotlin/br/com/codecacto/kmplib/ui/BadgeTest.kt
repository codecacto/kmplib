package br.com.codecacto.kmplib.ui

import br.com.codecacto.kmplib.ui.components.BadgeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes para Badge component
 */
class BadgeTest {

    @Test
    fun `BadgeStyle has all variants`() {
        val styles = BadgeStyle.entries
        assertEquals(3, styles.size)
        assertTrue(BadgeStyle.CIRCULAR in styles)
        assertTrue(BadgeStyle.PILL in styles)
        assertTrue(BadgeStyle.DOT in styles)
    }

    @Test
    fun `BadgeStyle CIRCULAR is default`() {
        // Verificar que CIRCULAR é o primeiro (padrão usado)
        assertEquals(BadgeStyle.CIRCULAR, BadgeStyle.entries.first())
    }
}
