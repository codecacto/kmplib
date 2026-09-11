package br.com.codecacto.kmplib.video.feed

import androidx.compose.ui.unit.IntRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FeedVideoPolicyTest {

    private val tela = IntRect(0, 0, 1000, 2000)

    // ------------------------------------------------------------------------- visibilidade

    @Test
    fun itemInteiroDentroDaAreaEhUm() {
        assertEquals(1f, feedVideoVisibleFraction(IntRect(0, 100, 1000, 900), tela))
    }

    @Test
    fun itemForaDaAreaEhZero() {
        assertEquals(0f, feedVideoVisibleFraction(IntRect(0, 2100, 1000, 2900), tela))
        assertEquals(0f, feedVideoVisibleFraction(IntRect(0, -900, 1000, -100), tela))
    }

    @Test
    fun itemEncostadoNaBordaEhZeroENaoUmPixelDeVisibilidade() {
        assertEquals(0f, feedVideoVisibleFraction(IntRect(0, 2000, 1000, 2800), tela))
    }

    @Test
    fun metadeParaBaixoEhMeio() {
        assertEquals(0.5f, feedVideoVisibleFraction(IntRect(0, 1600, 1000, 2400), tela))
    }

    @Test
    fun metadeParaOLadoContaIgualAMetadeParaBaixo() {
        // A conta é por ÁREA: carrossel horizontal também tira o vídeo da vez.
        assertEquals(0.5f, feedVideoVisibleFraction(IntRect(500, 0, 1500, 800), tela))
    }

    @Test
    fun itemSemAreaEhZeroENuncaNaN() {
        assertEquals(0f, feedVideoVisibleFraction(IntRect(0, 100, 0, 100), tela))
        assertEquals(0f, feedVideoVisibleFraction(IntRect(0, 100, 1000, 100), tela))
    }

    // --------------------------------------------------------------------- quem tem a vez

    private fun c(key: String, fracao: Float, top: Float = 0f) = FeedVideoCandidate(key, fracao, top)

    @Test
    fun abaixoDoLimiteNinguemToca() {
        assertNull(pickFeedVideoToPlay(listOf(c("a", 0.59f)), current = null))
    }

    @Test
    fun exatamenteNoLimiteToca() {
        assertEquals("a", pickFeedVideoToPlay(listOf(c("a", 0.6f)), current = null))
    }

    @Test
    fun oMaisVisivelGanha() {
        val candidatos = listOf(c("a", 0.7f, top = 0f), c("b", 0.95f, top = 800f))
        assertEquals("b", pickFeedVideoToPlay(candidatos, current = null))
    }

    @Test
    fun empateSemNinguemTocandoGanhaODeCima() {
        val candidatos = listOf(c("baixo", 1f, top = 900f), c("cima", 1f, top = 100f))
        assertEquals("cima", pickFeedVideoToPlay(candidatos, current = null))
    }

    @Test
    fun empateMantemOQueJaToca() {
        val candidatos = listOf(c("cima", 1f, top = 100f), c("baixo", 1f, top = 900f))
        assertEquals("baixo", pickFeedVideoToPlay(candidatos, current = "baixo"))
    }

    @Test
    fun oAtualSegueDentroDaMargemDeTroca() {
        // Os dois se cruzando na rolagem: 0,75 contra 0,70 não troca de dono.
        val candidatos = listOf(c("atual", 0.70f), c("novo", 0.75f, top = 500f))
        assertEquals("atual", pickFeedVideoToPlay(candidatos, current = "atual"))
    }

    @Test
    fun oNovoTomaAVezQuandoPassaDaMargem() {
        val candidatos = listOf(c("atual", 0.62f), c("novo", 0.9f, top = 500f))
        assertEquals("novo", pickFeedVideoToPlay(candidatos, current = "atual"))
    }

    @Test
    fun oAtualPerdeAVezAoCairAbaixoDoLimite() {
        val candidatos = listOf(c("atual", 0.5f), c("novo", 0.61f, top = 500f))
        assertEquals("novo", pickFeedVideoToPlay(candidatos, current = "atual"))
    }

    @Test
    fun oAtualQueSaiuDaListaNaoPrendeAVez() {
        assertEquals("b", pickFeedVideoToPlay(listOf(c("b", 0.8f)), current = "sumiu"))
    }

    @Test
    fun oLimiteEAMargemSaoParametros() {
        assertEquals("a", pickFeedVideoToPlay(listOf(c("a", 0.3f)), current = null, threshold = 0.25f))
        val candidatos = listOf(c("atual", 0.70f), c("novo", 0.75f, top = 1f))
        assertEquals("novo", pickFeedVideoToPlay(candidatos, current = "atual", switchMargin = 0f))
    }

    // ---------------------------------------------------------------- quem fica preparado

    @Test
    fun preparaOAtivoPrimeiroEDepoisOMaisVisivel() {
        val candidatos = listOf(c("a", 0.2f, 0f), c("b", 1f, 100f), c("c", 0.4f, 200f))
        assertEquals(listOf("b", "c"), feedVideosToPrepare(candidatos, active = "b", poolSize = 2))
    }

    @Test
    fun itemForaDaTelaNuncaEhPreparado() {
        val candidatos = listOf(c("a", 1f), c("fora", 0f, 900f))
        assertEquals(listOf("a"), feedVideosToPrepare(candidatos, active = "a", poolSize = 2))
    }

    @Test
    fun poolDeUmDesligaOPreCarregamento() {
        val candidatos = listOf(c("a", 1f), c("b", 0.5f, 900f))
        assertEquals(listOf("a"), feedVideosToPrepare(candidatos, active = "a", poolSize = 1))
    }

    @Test
    fun semAtivoPreparaOsMaisVisiveis() {
        val candidatos = listOf(c("a", 0.3f), c("b", 0.5f, 900f), c("c", 0.1f, 1800f))
        assertEquals(listOf("b", "a"), feedVideosToPrepare(candidatos, active = null, poolSize = 2))
    }

    // ------------------------------------------------------------------- quem fica com qual

    private val fontes = mapOf("a" to "u-a", "b" to "u-b", "c" to "u-c")

    @Test
    fun itemQueJaTinhaPlayerFicaComEle() {
        val r = assignFeedVideoSlots(listOf("b", "a"), mapOf("a" to 1, "b" to 0), listOf("u-b", "u-a"), 2, fontes::getValue)
        assertEquals(mapOf("b" to 0, "a" to 1), r)
    }

    @Test
    fun itemNovoPrefereOPlayerQueJaTemAMesmaFonte() {
        // "c" saiu da vez e voltou: o player 1 ainda está com o vídeo dele carregado.
        val r = assignFeedVideoSlots(listOf("a", "c"), mapOf("a" to 0), listOf("u-a", "u-c"), 2, fontes::getValue)
        assertEquals(1, r["c"])
    }

    @Test
    fun itemNovoPrefereOPlayerVazio() {
        val r = assignFeedVideoSlots(listOf("c"), emptyMap(), listOf("u-a", null), 2, fontes::getValue)
        assertEquals(mapOf("c" to 1), r)
    }

    @Test
    fun poolQueAindaNaoNasceuContaComoVazio() {
        val r = assignFeedVideoSlots(listOf("a", "b"), emptyMap(), emptyList(), 2, fontes::getValue)
        assertEquals(mapOf("a" to 0, "b" to 1), r)
    }

    @Test
    fun nuncaPassaDoTamanhoDoPool() {
        val r = assignFeedVideoSlots(listOf("a", "b", "c"), emptyMap(), emptyList(), 2, fontes::getValue)
        assertEquals(2, r.size)
        assertEquals(setOf(0, 1), r.values.toSet())
    }

    @Test
    fun semLivreVazioReciclaOPlayerDeQuemSaiu() {
        val r = assignFeedVideoSlots(listOf("c"), mapOf("a" to 0), listOf("u-a"), 1, fontes::getValue)
        assertEquals(mapOf("c" to 0), r)
    }

    // --------------------------------------------------------------------------- proporção

    @Test
    fun proporcaoDentroDaReguaFicaComoEsta() {
        assertEquals(1f, feedMediaAspectRatio(1080, 1080))
        assertEquals(0.8f, feedMediaAspectRatio(1080, 1350))
    }

    @Test
    fun proporcaoForaDaReguaEhLimitada() {
        assertEquals(FEED_MEDIA_MIN_ASPECT_RATIO, feedMediaAspectRatio(1080, 1920)) // 9:16 vira 4:5
        assertEquals(FEED_MEDIA_MAX_ASPECT_RATIO, feedMediaAspectRatio(3000, 1000)) // 3:1 vira 1,91:1
    }

    @Test
    fun semMedidaVira4Por5() {
        assertEquals(0.8f, feedMediaAspectRatio(null, 1080))
        assertEquals(0.8f, feedMediaAspectRatio(1080, 0))
        assertEquals(0.8f, feedMediaAspectRatio(-1, 1080))
    }

    // -------------------------------------------------------------------------- configuração

    @Test
    fun configInvalidaFalhaAlto() {
        assertFailsWith<IllegalArgumentException> { FeedVideoConfig(maxPlayers = 0) }
        assertFailsWith<IllegalArgumentException> { FeedVideoConfig(playThreshold = 0f) }
        assertFailsWith<IllegalArgumentException> { FeedVideoConfig(playThreshold = 1.2f) }
        assertFailsWith<IllegalArgumentException> { FeedVideoConfig(switchMargin = -0.1f) }
    }
}
