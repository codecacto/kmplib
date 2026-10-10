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

/**
 * `maxDimension`/`jpegQuality` configuráveis (2.278.0): o valor costuma vir de configuração, e um
 * número fora da faixa precisa virar um valor válido — não um seletor que fecha o app.
 */
class PickedImageEncodingRangeTest {

    @Test
    fun `teto dentro da faixa passa intacto`() {
        assertEquals(2048, coercePickedImageMaxDimension(2048))
        assertEquals(PICKED_IMAGE_MAX_DIMENSION, coercePickedImageMaxDimension(PICKED_IMAGE_MAX_DIMENSION))
    }

    @Test
    fun `teto acima de 4096 e preso no limite`() {
        assertEquals(PICKED_IMAGE_MAX_DIMENSION_LIMIT, coercePickedImageMaxDimension(10_000))
        assertEquals(4096, PICKED_IMAGE_MAX_DIMENSION_LIMIT)
    }

    @Test
    fun `teto zero ou negativo vira o minimo e nao quebra o encoder`() {
        assertEquals(PICKED_IMAGE_MIN_DIMENSION, coercePickedImageMaxDimension(0))
        assertEquals(PICKED_IMAGE_MIN_DIMENSION, coercePickedImageMaxDimension(-5))
    }

    @Test
    fun `qualidade e presa entre 1 e 100`() {
        assertEquals(1, coercePickedImageJpegQuality(0))
        assertEquals(100, coercePickedImageJpegQuality(150))
        assertEquals(PICKED_IMAGE_JPEG_QUALITY, coercePickedImageJpegQuality(PICKED_IMAGE_JPEG_QUALITY))
        assertEquals(85, PICKED_IMAGE_JPEG_QUALITY)
    }

    @Test
    fun `teto maior que o padrao reduz pelo teto pedido`() {
        assertEquals(2048 to 1536, scaledImageSize(4032, 3024, 2048))
        assertEquals(3024 to 4032, scaledImageSize(3024, 4032, 4096))
    }
}

/**
 * A amostragem da decodificação (2.278.0): decodificar perto do teto, nunca a foto inteira sem
 * necessidade, e nunca acima do orçamento de memória.
 */
class DecodeSampleSizeTest {

    @Test
    fun `imagem menor que o teto nao e amostrada`() {
        assertEquals(1, decodeSampleSize(800, 600, 1024))
    }

    @Test
    fun `amostra deixa o maior lado ainda maior ou igual ao teto`() {
        // 4032/2 = 2016 >= 1024 ; 4032/4 = 1008 < 1024 -> 2
        assertEquals(2, decodeSampleSize(4032, 3024, 1024))
        // 4000/2 = 2000 < 2048 -> 1
        assertEquals(1, decodeSampleSize(4000, 3000, 2048))
    }

    @Test
    fun `retrato e paisagem dao a mesma amostra`() {
        assertEquals(decodeSampleSize(4032, 3024, 1024), decodeSampleSize(3024, 4032, 1024))
    }

    @Test
    fun `foto de 48 MP com teto maximo cabe no orcamento de memoria`() {
        // 8000x6000 = 48 MP > orçamento (4096²): dobra para 4000x3000 = 12 MP.
        val amostra = decodeSampleSize(8000, 6000, 4096)
        assertEquals(2, amostra)
        val pixels = (8000L / amostra) * (6000L / amostra)
        assertTrue(pixels <= PICKED_IMAGE_DECODE_PIXEL_BUDGET, "amostrada com $pixels pixels")
    }

    @Test
    fun `foto de 12 MP com teto maximo e decodificada inteira`() {
        assertEquals(1, decodeSampleSize(4032, 3024, 4096))
    }

    @Test
    fun `medida invalida nao amostra`() {
        assertEquals(1, decodeSampleSize(0, 100, 1024))
        assertEquals(1, decodeSampleSize(100, 100, 0))
    }
}
