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
