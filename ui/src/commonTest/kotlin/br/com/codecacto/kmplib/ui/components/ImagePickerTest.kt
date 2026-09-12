package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A conta da redução mora em `commonMain` justamente para Android e iOS **não** darem medidas
 * diferentes para a mesma foto — era esse desencontro que faria a proporção publicada pelo app
 * divergir da publicada pelo site.
 */
class ScaledImageSizeTest {

    @Test
    fun `imagem menor que o teto sai intacta`() {
        assertEquals(800 to 600, scaledImageSize(800, 600))
    }

    @Test
    fun `imagem exatamente no teto nao e reduzida`() {
        assertEquals(1024 to 1024, scaledImageSize(1024, 1024))
    }

    @Test
    fun `paisagem grande cabe no teto pelo maior lado`() {
        val (largura, altura) = scaledImageSize(4000, 3000)
        assertEquals(1024, largura)
        assertEquals(768, altura)
    }

    @Test
    fun `retrato grande cabe no teto pelo maior lado`() {
        val (largura, altura) = scaledImageSize(3000, 4000)
        assertEquals(768, largura)
        assertEquals(1024, altura)
    }

    @Test
    fun `a proporcao e preservada na reducao`() {
        val (largura, altura) = scaledImageSize(4032, 3024)
        val originais = 4032.0 / 3024.0
        val reduzidas = largura.toDouble() / altura.toDouble()
        assertTrue(
            kotlin.math.abs(originais - reduzidas) < 0.01,
            "proporção mudou: $originais -> $reduzidas",
        )
    }

    @Test
    fun `panorama extremo nao zera o lado curto`() {
        // 8000x7 truncaria para altura 0, e nenhum encoder aceita altura zero.
        val (largura, altura) = scaledImageSize(8000, 7)
        assertEquals(1024, largura)
        assertTrue(altura >= 1, "altura precisa ser ao menos 1, veio $altura")
    }

    @Test
    fun `medida invalida volta intacta em vez de inventar valor`() {
        assertEquals(0 to 0, scaledImageSize(0, 0))
        assertEquals(-1 to 10, scaledImageSize(-1, 10))
    }

    @Test
    fun `o teto e configuravel`() {
        assertEquals(320 to 240, scaledImageSize(4000, 3000, maxDimension = 320))
    }
}

class PickedImageTest {

    @Test
    fun `duas fotos com os mesmos bytes e a mesma medida sao iguais`() {
        val a = PickedImage(byteArrayOf(1, 2, 3), widthPx = 100, heightPx = 200)
        val b = PickedImage(byteArrayOf(1, 2, 3), widthPx = 100, heightPx = 200)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `a medida faz parte da identidade`() {
        val retrato = PickedImage(byteArrayOf(1, 2, 3), widthPx = 100, heightPx = 200)
        val paisagem = PickedImage(byteArrayOf(1, 2, 3), widthPx = 200, heightPx = 100)
        assertNotEquals(retrato, paisagem)
    }

    @Test
    fun `bytes diferentes nao sao a mesma foto`() {
        val a = PickedImage(byteArrayOf(1, 2, 3), widthPx = 100, heightPx = 200)
        val b = PickedImage(byteArrayOf(9, 9, 9), widthPx = 100, heightPx = 200)
        assertNotEquals(a, b)
    }

    @Test
    fun `o mime default e jpeg porque o seletor recodifica`() {
        assertEquals("image/jpeg", PickedImage(byteArrayOf(), 10, 10).mimeType)
    }
}
