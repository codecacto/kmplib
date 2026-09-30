package br.com.codecacto.kmplib.ui.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DrawingRenderTest {

    @Test
    fun recusaDimensaoNaoPositivaAntesDeAlocar() {
        assertFailsWith<IllegalArgumentException> { renderDrawingToPng(0, 100) {} }
        assertFailsWith<IllegalArgumentException> { renderDrawingToImageBitmap(100, -1) {} }
    }

    @Test
    fun referenciaDe360dp() {
        // 1080 px de largura = densidade 3: 16.sp no desenho = 48 px, como num telefone xxhdpi.
        assertEquals(3f, 1080 / DRAWING_REFERENCE_WIDTH_DP)
    }
}
