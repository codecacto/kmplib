package br.com.codecacto.kmplib.ui.components

import br.com.codecacto.kmplib.ui.components.KeyboardDismissTap.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyboardDismissTapTest {

    private val slop = 18f

    private fun next(
        pointerCount: Int = 1,
        trackedPointerPresent: Boolean = true,
        anyConsumed: Boolean = false,
        distanceFromDown: Float = 0f,
        released: Boolean = false,
    ) = KeyboardDismissTap.next(
        pointerCount = pointerCount,
        trackedPointerPresent = trackedPointerPresent,
        anyConsumed = anyConsumed,
        distanceFromDown = distanceFromDown,
        touchSlop = slop,
        released = released,
    )

    @Test
    fun `toque livre no fundo fecha o teclado`() {
        assertEquals(Verdict.Pending, KeyboardDismissTap.start(downConsumed = false))
        assertEquals(Verdict.Tap, next(released = true))
    }

    @Test
    fun `down consumido pelo campo de texto nao e conosco`() {
        assertEquals(Verdict.NotATap, KeyboardDismissTap.start(downConsumed = true))
    }

    @Test
    fun `up consumido por botao ou campo nao fecha`() {
        assertEquals(Verdict.NotATap, next(anyConsumed = true, released = true))
    }

    @Test
    fun `arrastar alem do slop e rolagem, nao toque`() {
        assertEquals(Verdict.NotATap, next(distanceFromDown = slop + 1f))
        assertEquals(Verdict.NotATap, next(distanceFromDown = slop + 1f, released = true))
    }

    @Test
    fun `tremida dentro do slop ainda e toque`() {
        assertEquals(Verdict.Pending, next(distanceFromDown = slop - 1f))
        assertEquals(Verdict.Tap, next(distanceFromDown = slop, released = true))
    }

    @Test
    fun `segundo dedo cancela`() {
        assertEquals(Verdict.NotATap, next(pointerCount = 2, released = true))
    }

    @Test
    fun `ponteiro acompanhado sumiu do evento cancela`() {
        assertEquals(Verdict.NotATap, next(trackedPointerPresent = false))
    }

    @Test
    fun `dedo ainda no vidro continua pendente`() {
        assertEquals(Verdict.Pending, next())
    }
}
