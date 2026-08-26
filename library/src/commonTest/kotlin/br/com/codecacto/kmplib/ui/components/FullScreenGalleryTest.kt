package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A página em que a galeria em tela cheia abre (2.153.0).
 *
 * O `rememberPagerState` **estoura** com uma `initialPage` fora da faixa, e o índice vem de uma
 * lista que pode ter encolhido entre o toque e a abertura — uma foto apagada, uma recarga do
 * perfil. Estes casos são exatamente os que derrubariam a tela.
 */
class FullScreenGalleryTest {

    @Test
    fun `abre na foto tocada`() {
        assertEquals(3, paginaInicialDaGaleria(indiceInicial = 3, total = 12))
    }

    @Test
    fun `indice acima do fim ancora na ultima, em vez de estourar`() {
        assertEquals(11, paginaInicialDaGaleria(indiceInicial = 40, total = 12))
    }

    @Test
    fun `indice negativo ancora na primeira`() {
        assertEquals(0, paginaInicialDaGaleria(indiceInicial = -2, total = 12))
    }

    // Lista vazia não desenha galeria nenhuma (o composable sai antes), mas a conta não pode
    // devolver -1 no caminho: seria o `coerceIn(0, -1)` que estoura por faixa invertida.
    @Test
    fun `lista vazia devolve zero em vez de faixa invertida`() {
        assertEquals(0, paginaInicialDaGaleria(indiceInicial = 0, total = 0))
        assertEquals(0, paginaInicialDaGaleria(indiceInicial = 5, total = 0))
    }

    @Test
    fun `uma foto so abre nela`() {
        assertEquals(0, paginaInicialDaGaleria(indiceInicial = 0, total = 1))
        assertEquals(0, paginaInicialDaGaleria(indiceInicial = 9, total = 1))
    }
}

/**
 * Quem fica com o arrasto: o zoom, ou o pager por fora (2.155.0)?
 *
 * ⚠️ Estes casos são o defeito que a 2.153.0 tinha em produção. O `detectTransformGestures`
 * consumia TODO arrasto — inclusive o de um dedo com a imagem em escala 1 —, então dentro da
 * galeria o dedo arrastava, a foto ficava parada e a página nunca virava: **deslizar não fazia
 * nada**. O primeiro teste abaixo é exatamente esse caso.
 */
class ZoomableBoxGestoTest {

    @Test
    fun `um dedo em escala 1 NAO e do zoom — o pager vira a pagina`() {
        assertEquals(false, gestoEDoZoom(dedos = 1, escalaAtual = 1f))
    }

    @Test
    fun `um dedo com a imagem ampliada e do zoom — arrastar move a IMAGEM`() {
        assertEquals(true, gestoEDoZoom(dedos = 1, escalaAtual = 2.5f))
    }

    // Ninguém usa dois dedos para virar página: pinça é sempre do zoom, mesmo partindo da escala 1.
    @Test
    fun `dois dedos sao sempre do zoom, mesmo em escala 1`() {
        assertEquals(true, gestoEDoZoom(dedos = 2, escalaAtual = 1f))
    }

    // Resíduo de ponto flutuante da pinça (1.0000001). Comparar com igualdade faria a imagem
    // "de volta ao normal" continuar capturando o arrasto para sempre.
    @Test
    fun `residuo de ponto flutuante nao conta como ampliada`() {
        assertEquals(false, gestoEDoZoom(dedos = 1, escalaAtual = 1.0000001f))
        assertEquals(false, gestoEDoZoom(dedos = 1, escalaAtual = 1.009f))
        assertEquals(true, gestoEDoZoom(dedos = 1, escalaAtual = 1.02f))
    }

    // Nenhum dedo pressionado é o fim do gesto; sem zoom, não há o que consumir.
    @Test
    fun `sem dedo e sem zoom nao e do zoom`() {
        assertEquals(false, gestoEDoZoom(dedos = 0, escalaAtual = 1f))
    }
}
