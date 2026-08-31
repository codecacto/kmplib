package br.com.codecacto.kmplib.ads

import br.com.codecacto.kmplib.ads.custom.CustomAd
import kotlin.test.Test
import kotlin.test.assertEquals

class BannerSizeTest {

    @Test
    fun `a caixa segue a proporcao da arte que veio, nao a do tamanho pedido`() {
        // Pediu o grande e recebeu o comum (nao havia arte grande): desenhar em 3:1 cortaria a 6:1.
        assertEquals(6f, BannerSize.aspectRatioOf(CustomAd.FORMAT_BANNER, fallback = BannerSize.LARGE))
        assertEquals(3f, BannerSize.aspectRatioOf(CustomAd.FORMAT_BANNER_LARGE, fallback = BannerSize.STANDARD))
    }

    @Test
    fun `formato em branco cai no tamanho que o app pediu`() {
        // O backend pode omitir `format`; sem palpite melhor, vale a intencao do app.
        assertEquals(3f, BannerSize.aspectRatioOf("", fallback = BannerSize.LARGE))
        assertEquals(6f, BannerSize.aspectRatioOf("desconhecido", fallback = BannerSize.STANDARD))
    }

    @Test
    fun `a cadeia de fallback desce, nunca sobe`() {
        // Descer e seguro: a caixa segue a arte que veio, entao sai um banner mais baixo e inteiro.
        // Subir seria pedir uma peca mais alta do que o app reservou na tela.
        assertEquals(
            listOf(CustomAd.FORMAT_BANNER_SQUARE, CustomAd.FORMAT_BANNER_LARGE, CustomAd.FORMAT_BANNER),
            BannerSize.fallbackChain(BannerSize.SQUARE),
        )
        assertEquals(
            listOf(CustomAd.FORMAT_BANNER_LARGE, CustomAd.FORMAT_BANNER),
            BannerSize.fallbackChain(BannerSize.LARGE),
        )
        assertEquals(listOf(CustomAd.FORMAT_BANNER), BannerSize.fallbackChain(BannerSize.STANDARD))
    }

    @Test
    fun `o quadrado e 1 para 1`() {
        assertEquals(1f, BannerSize.aspectRatioOf(CustomAd.FORMAT_BANNER_SQUARE, fallback = BannerSize.STANDARD))
    }

    @Test
    fun `cada tamanho pede o seu formato`() {
        assertEquals(CustomAd.FORMAT_BANNER, BannerSize.STANDARD.format)
        assertEquals(CustomAd.FORMAT_BANNER_LARGE, BannerSize.LARGE.format)
        assertEquals(CustomAd.FORMAT_BANNER_SQUARE, BannerSize.SQUARE.format)
    }
}
