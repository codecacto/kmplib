package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testes da lógica pura do [OnboardingPager]: detecção de último slide, rótulo do botão primário,
 * visibilidade do "Pular" e clamps de navegação. A UI (pager/indicadores) é validada visualmente.
 */
class OnboardingPagerTest {

    private val texts = OnboardingTexts()

    @Test
    fun `ultimo slide detectado corretamente`() {
        assertFalse(onboardingIsLastPage(index = 0, total = 3))
        assertFalse(onboardingIsLastPage(index = 1, total = 3))
        assertTrue(onboardingIsLastPage(index = 2, total = 3))
    }

    @Test
    fun `slide unico e sempre o ultimo`() {
        assertTrue(onboardingIsLastPage(index = 0, total = 1))
    }

    @Test
    fun `total invalido trata como ultimo (nunca trava sem proximo)`() {
        assertTrue(onboardingIsLastPage(index = 0, total = 0))
        assertTrue(onboardingIsLastPage(index = 0, total = -1))
    }

    @Test
    fun `rotulo primario e proximo ate o penultimo e finish no ultimo`() {
        assertEquals(texts.next, onboardingPrimaryLabel(0, 3, texts))
        assertEquals(texts.next, onboardingPrimaryLabel(1, 3, texts))
        assertEquals(texts.finish, onboardingPrimaryLabel(2, 3, texts))
    }

    @Test
    fun `pular aparece so ate o penultimo slide`() {
        assertTrue(onboardingShowSkip(0, 3))
        assertTrue(onboardingShowSkip(1, 3))
        assertFalse(onboardingShowSkip(2, 3))
    }

    @Test
    fun `proximo indice avanca e clampa no ultimo`() {
        assertEquals(1, onboardingNextIndex(0, 3))
        assertEquals(2, onboardingNextIndex(1, 3))
        assertEquals(2, onboardingNextIndex(2, 3)) // já no último: não estoura
    }

    @Test
    fun `proximo indice nunca negativo mesmo com total zero`() {
        assertEquals(0, onboardingNextIndex(0, 0))
    }

    @Test
    fun `indice anterior recua e para em zero`() {
        assertEquals(1, onboardingPreviousIndex(2))
        assertEquals(0, onboardingPreviousIndex(1))
        assertEquals(0, onboardingPreviousIndex(0))
    }
}
