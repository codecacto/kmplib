package br.com.codecacto.kmplib.video.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A posição que o pré-carregador congela no `rankingData` da Media3.
 *
 * O teste que importa aqui é [aEscadaMiraOVideoCertoDepoisDeRolar]: até a 2.198.0 a posição era o
 * índice **na janela composta**, que muda a cada rolagem, enquanto o `rankingData` do holder é
 * `final`. O resultado era a escada respondendo a distância de um item **para outro**.
 */
class FeedPreloadPositionsTest {

    private fun posicoesDe(vararg urls: String) = FeedPreloadPositions().also { it.update(urls.toList()) }

    // ------------------------------------------------------------------ a janela e suas posições

    @Test
    fun janelaInicialGanhaPosicoesContiguas() {
        val posicoes = FeedPreloadPositions()
        val atualizacao = posicoes.update(listOf("v0", "v1", "v2", "v3"))

        assertEquals(listOf(0, 1, 2, 3), atualizacao.slots.map { it.position })
        assertEquals(listOf("v0", "v1", "v2", "v3"), atualizacao.added.map { it.url })
        assertTrue(atualizacao.moved.isEmpty())
        assertTrue(atualizacao.removed.isEmpty())
    }

    @Test
    fun rolarParaBaixoPreservaAPosicaoDeQuemFicou() {
        val posicoes = posicoesDe("v0", "v1", "v2", "v3")

        val atualizacao = posicoes.update(listOf("v1", "v2", "v3", "v4"))

        // Quem continuou na tela NÃO se mexe — é isso que mantém verdadeiro o rankingData congelado.
        assertEquals(1, posicoes.positionOf("v1"))
        assertEquals(2, posicoes.positionOf("v2"))
        assertEquals(3, posicoes.positionOf("v3"))
        // O que entrou por baixo vem depois do último, nunca por cima de uma posição usada.
        assertEquals(4, posicoes.positionOf("v4"))
        assertEquals(listOf("v4"), atualizacao.added.map { it.url })
        assertEquals(listOf("v0"), atualizacao.removed)
        assertTrue(atualizacao.moved.isEmpty(), "rolar não reordena: não deveria haver remove+add")
    }

    @Test
    fun duasPosicoesNuncaColidemDepoisDeRolar() {
        // O defeito da 2.197.0/2.198.0 em uma linha: com o índice da janela, o item que entrava
        // embaixo recebia a MESMA posição que um item ainda vivo tinha congelado.
        val posicoes = FeedPreloadPositions()
        var janela = listOf("v0", "v1", "v2", "v3")
        posicoes.update(janela)

        repeat(6) { passo ->
            janela = janela.drop(1) + "v${passo + 4}"
            posicoes.update(janela)
            val ocupadas = janela.map { posicoes.positionOf(it) }
            assertEquals(ocupadas.distinct().size, ocupadas.size, "posição repetida na janela $janela")
        }
    }

    @Test
    fun aEscadaMiraOVideoCertoDepoisDeRolar() {
        // Cenário do bug, ponta a ponta: v3 entrou quando era o 4º da janela (posição 3) e continua
        // com esse rankingData congelado. Depois de rolar, ele é o vizinho imediato do que toca.
        val posicoes = FeedPreloadPositions()
        posicoes.update(listOf("v0", "v1", "v2", "v3"))
        posicoes.update(listOf("v1", "v2", "v3", "v4"))

        val posicaoAtual = posicoes.positionOf("v2")!! // v2 é o que está tocando agora

        fun alvoDe(url: String) = feedPreloadTargetFor(posicoes.positionOf(url)!! - posicaoAtual)

        // O de baixo do que toca ganha os 3 s — era ele que ficava sem nada.
        assertEquals(FeedPreloadTarget.Loaded(FEED_PRELOAD_NEXT_MILLIS), alvoDe("v3"))
        assertEquals(FeedPreloadTarget.Loaded(FEED_PRELOAD_AHEAD_MILLIS), alvoDe("v4"))
        // E o que já passou não gasta dado nenhum — era ele que recebia os 3 s.
        assertEquals(FeedPreloadTarget.None, alvoDe("v1"))
        assertEquals(FeedPreloadTarget.None, alvoDe("v2"))
    }

    @Test
    fun rolarParaCimaMantemContiguidade() {
        val posicoes = posicoesDe("v3", "v4", "v5")

        posicoes.update(listOf("v2", "v3", "v4"))

        // v3 não se mexe; quem apareceu por cima fica um antes — posição negativa é legítima,
        // porque o que a escada usa é a DIFERENÇA.
        assertEquals(posicoes.positionOf("v3")!! - 1, posicoes.positionOf("v2"))
        assertEquals(posicoes.positionOf("v3")!! + 1, posicoes.positionOf("v4"))
    }

    // ---------------------------------------------------------------------------- poda e limites

    @Test
    fun itemQueSaiDaJanelaEhPodado() {
        val posicoes = posicoesDe("v0", "v1")

        posicoes.update(listOf("v1", "v2"))

        assertNull(posicoes.positionOf("v0"))
    }

    @Test
    fun oMapaNaoCresceComARolagem() {
        // O `urlPorRanking` da 2.197.0 só era limpo no `reset()`: numa lista de 200 posts ele
        // terminava com 200 entradas para uma janela de 4.
        val posicoes = FeedPreloadPositions()
        var janela = listOf("v0", "v1", "v2", "v3")
        posicoes.update(janela)
        repeat(50) { passo ->
            janela = janela.drop(1) + "v${passo + 4}"
            posicoes.update(janela)
        }

        assertEquals(4, posicoes.size)
    }

    @Test
    fun listaNovaNaoReaproveitaPosicaoDeAntes() {
        // Feed trocado (pull-to-refresh): nenhuma URL conhecida, e as posições novas não podem
        // cair em cima das que acabaram de sair.
        val posicoes = posicoesDe("v0", "v1", "v2")
        val antigas = listOf("v0", "v1", "v2").map { posicoes.positionOf(it)!! }.toSet()

        posicoes.update(listOf("n0", "n1", "n2"))

        val novas = listOf("n0", "n1", "n2").map { posicoes.positionOf(it)!! }
        assertTrue(novas.none { it in antigas }, "posições novas $novas colidem com as antigas $antigas")
    }

    @Test
    fun reordenarMarcaComoMovido() {
        val posicoes = posicoesDe("v0", "v1", "v2")

        val atualizacao = posicoes.update(listOf("v0", "v2", "v1"))

        // v0 é a âncora e fica; os outros dois trocaram de lugar de verdade → remove + add.
        assertEquals(setOf("v1", "v2"), atualizacao.moved.map { it.url }.toSet())
        assertEquals(1, posicoes.positionOf("v2"))
        assertEquals(2, posicoes.positionOf("v1"))
    }

    @Test
    fun urlRepetidaOcupaUmSlotSo() {
        // Dois posts com o mesmo vídeo: o manager identifica por `MediaItem`, que é igual nos dois.
        val posicoes = FeedPreloadPositions()

        val atualizacao = posicoes.update(listOf("v0", "v1", "v0"))

        assertEquals(listOf("v0", "v1"), atualizacao.slots.map { it.url })
        assertEquals(2, posicoes.size)
    }

    @Test
    fun janelaVaziaRemoveTodos() {
        val posicoes = posicoesDe("v0", "v1")

        val atualizacao = posicoes.update(emptyList())

        assertEquals(setOf("v0", "v1"), atualizacao.removed.toSet())
        assertEquals(0, posicoes.size)
    }

    @Test
    fun clearEsqueceAJanela() {
        val posicoes = posicoesDe("v0", "v1")

        posicoes.clear()

        assertEquals(0, posicoes.size)
        assertNull(posicoes.positionOf("v0"))
    }
}
