package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A grade da [ImageGallery] fora do modo lazy (`scrollable = false`, 2.181.0).
 *
 * Este modo existe porque a grade lazy **estoura** dentro de uma coluna que já rola
 * (*"infinity maximum height"*) — e o que a substitui é esta divisão em linhas. Errar aqui não dá
 * erro de compilação: dá foto faltando na tela ou célula de tamanho diferente na última linha.
 */
class ImageGalleryTest {

    private fun fotos(quantidade: Int): List<GalleryItem> =
        (1..quantidade).map { GalleryItem(id = "f$it", model = null) }

    @Test
    fun `sete fotos em tres colunas viram tres linhas — a ultima com uma`() {
        val linhas = galeriaEmLinhas(fotos(7), colunas = 3)
        assertEquals(3, linhas.size)
        assertEquals(listOf(3, 3, 1), linhas.map { it.size })
    }

    @Test
    fun `nenhuma foto se perde no caminho`() {
        val itens = fotos(7)
        assertEquals(itens, galeriaEmLinhas(itens, colunas = 3).flatten())
    }

    @Test
    fun `grade exata nao inventa linha vazia no fim`() {
        val linhas = galeriaEmLinhas(fotos(6), colunas = 3)
        assertEquals(2, linhas.size)
        assertTrue(linhas.none { it.isEmpty() })
    }

    @Test
    fun `lista vazia nao desenha linha nenhuma`() {
        assertEquals(emptyList<List<GalleryItem>>(), galeriaEmLinhas(emptyList(), colunas = 3))
    }

    @Test
    fun `coluna zero ou negativa cai em uma coluna, em vez de estourar`() {
        // `chunked(0)` lança IllegalArgumentException — seria a tela caindo por causa de um
        // parâmetro que o app calculou (ex.: largura dividida por tamanho de célula).
        assertEquals(3, galeriaEmLinhas(fotos(3), colunas = 0).size)
        assertEquals(3, galeriaEmLinhas(fotos(3), colunas = -2).size)
    }

    @Test
    fun `uma coluna vira uma linha por foto`() {
        assertEquals(listOf(1, 1, 1, 1), galeriaEmLinhas(fotos(4), colunas = 1).map { it.size })
    }

    @Test
    fun `menos fotos que colunas cabe numa linha so`() {
        val linhas = galeriaEmLinhas(fotos(2), colunas = 3)
        assertEquals(1, linhas.size)
        assertEquals(2, linhas.first().size)
    }
}
