package br.com.codecacto.kmplib.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderViewTest {

    @Test
    fun fontSize_next_avancaUmPasso() {
        assertEquals(ReaderFontSize.MEDIUM, ReaderFontSize.SMALL.next())
        assertEquals(ReaderFontSize.LARGE, ReaderFontSize.MEDIUM.next())
        assertEquals(ReaderFontSize.EXTRA_LARGE, ReaderFontSize.LARGE.next())
    }

    @Test
    fun fontSize_next_saturaNoMaximo() {
        assertEquals(ReaderFontSize.EXTRA_LARGE, ReaderFontSize.EXTRA_LARGE.next())
    }

    @Test
    fun fontSize_previous_retrocedeUmPasso() {
        assertEquals(ReaderFontSize.LARGE, ReaderFontSize.EXTRA_LARGE.previous())
        assertEquals(ReaderFontSize.MEDIUM, ReaderFontSize.LARGE.previous())
        assertEquals(ReaderFontSize.SMALL, ReaderFontSize.MEDIUM.previous())
    }

    @Test
    fun fontSize_previous_saturaNoMinimo() {
        assertEquals(ReaderFontSize.SMALL, ReaderFontSize.SMALL.previous())
    }

    @Test
    fun fontSize_escalasNaFaixaDeAcessibilidade() {
        assertEquals(0.85f, ReaderFontSize.SMALL.scale)
        assertEquals(1.0f, ReaderFontSize.MEDIUM.scale)
        assertEquals(1.3f, ReaderFontSize.LARGE.scale)
        assertEquals(1.6f, ReaderFontSize.EXTRA_LARGE.scale)
    }

    @Test
    fun fontSize_fromScale_resolveOpassoMaisProximo() {
        assertEquals(ReaderFontSize.SMALL, ReaderFontSize.fromScale(0.9f))
        assertEquals(ReaderFontSize.MEDIUM, ReaderFontSize.fromScale(1.05f))
        assertEquals(ReaderFontSize.LARGE, ReaderFontSize.fromScale(1.35f))
        assertEquals(ReaderFontSize.EXTRA_LARGE, ReaderFontSize.fromScale(2.0f))
    }

    @Test
    fun toReaderBlocks_preservaNumeroETexto() {
        val blocks = listOf(
            1 to "O Senhor é o meu pastor",
            2 to "nada me faltará"
        ).toReaderBlocks()

        assertEquals(2, blocks.size)
        assertEquals(ReaderBlock(text = "O Senhor é o meu pastor", number = 1), blocks[0])
        assertEquals(ReaderBlock(text = "nada me faltará", number = 2), blocks[1])
    }

    @Test
    fun toReaderBlocks_listaVaziaRetornaVazio() {
        assertEquals(emptyList(), emptyList<Pair<Int, String>>().toReaderBlocks())
    }
}
