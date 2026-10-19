package br.com.codecacto.kmplib.ui.tour

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tela de 400 × 800 px com barra de status de 40 px e barra de gestos de 20 px. */
class TourBalloonPlacementTest {

    private val screen = IntRect(0, 40, 400, 780)
    private val balloon = IntSize(300, 200)
    private val gap = 12
    private val margin = 10
    private val arrowInset = 25

    private fun place(target: IntRect?, size: IntSize = balloon, bounds: IntRect = screen) =
        placeTourBalloon(target, size, bounds, gap, margin, arrowInset)

    private fun assertInside(p: TourBalloonPlacement, size: IntSize = balloon, bounds: IntRect = screen) {
        assertTrue(p.offset.x >= bounds.left + margin, "saiu pela esquerda: $p")
        assertTrue(p.offset.x + size.width <= bounds.right - margin, "saiu pela direita: $p")
        assertTrue(p.offset.y >= bounds.top + margin, "saiu por cima: $p")
        assertTrue(p.offset.y + size.height <= bounds.bottom - margin, "saiu por baixo: $p")
    }

    @Test
    fun `sem alvo fica centralizado e sem seta`() {
        val p = place(null)
        assertEquals(TourBalloonSide.CENTER, p.side)
        assertEquals(IntOffset(50, 40 + (740 - 200) / 2), p.offset)
        assertNull(p.arrowCenterX)
    }

    @Test
    fun `alvo no topo poe o balao abaixo com a seta no centro do alvo`() {
        val target = IntRect(150, 60, 250, 110) // botão da top bar
        val p = place(target)
        assertEquals(TourBalloonSide.BELOW, p.side)
        assertEquals(110 + gap, p.offset.y)
        assertEquals(50, p.offset.x) // centrado: 200 - 150
        assertEquals(150, p.arrowCenterX) // centro do alvo (200) - x (50)
        assertInside(p)
    }

    @Test
    fun `item da bottom bar poe o balao acima`() {
        val target = IntRect(300, 700, 400, 780) // última aba, colada na barra de gestos
        val p = place(target)
        assertEquals(TourBalloonSide.ABOVE, p.side)
        assertEquals(700 - gap - 200, p.offset.y)
        assertInside(p)
        // Alvo na direita: balão preso na margem direita, seta deslocada para o alvo.
        assertEquals(400 - margin - 300, p.offset.x)
        assertEquals(260, p.arrowCenterX) // centro do alvo (350) - x (90)
    }

    @Test
    fun `alvo na quina esquerda prende o balao na margem e a seta no limite`() {
        val target = IntRect(0, 50, 20, 70)
        val p = place(target)
        assertEquals(margin, p.offset.x)
        assertEquals(arrowInset, p.arrowCenterX) // centro (10) - x (10) = 0 → preso no inset
        assertInside(p)
    }

    @Test
    fun `alvo enorme no meio cobre e perde a seta mas nao sai da tela`() {
        val target = IntRect(0, 150, 400, 700) // lista inteira
        val p = place(target)
        assertInside(p)
        assertNull(p.arrowCenterX)
    }

    @Test
    fun `balao mais largo que a tela encosta na margem esquerda`() {
        val wide = IntSize(500, 120)
        val p = place(IntRect(100, 100, 140, 140), wide)
        assertEquals(margin, p.offset.x)
        assertEquals(TourBalloonSide.BELOW, p.side)
    }

    @Test
    fun `balao mais alto que a tela encosta no topo seguro`() {
        val tall = IntSize(300, 2000)
        val p = place(IntRect(100, 400, 140, 440), tall)
        assertEquals(40 + margin, p.offset.y)
    }

    @Test
    fun `prefere abaixo quando cabe dos dois lados`() {
        val p = place(IntRect(100, 380, 200, 420))
        assertEquals(TourBalloonSide.BELOW, p.side)
        assertNotNull(p.arrowCenterX)
    }
}
