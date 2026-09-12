package br.com.codecacto.kmplib.video.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedVideoPreloadTest {

    // ------------------------------------------------------------------------ a escada

    @Test
    fun oProximoGanhaTresSegundos() {
        assertEquals(FeedPreloadTarget.Loaded(3_000L), feedPreloadTargetFor(distance = 1))
    }

    @Test
    fun oSegundoEOTerceiroGanhamUmSegundo() {
        assertEquals(FeedPreloadTarget.Loaded(1_000L), feedPreloadTargetFor(distance = 2))
        assertEquals(FeedPreloadTarget.Loaded(1_000L), feedPreloadTargetFor(distance = 3))
    }

    @Test
    fun oRestoDaJanelaVaiSoParaODisco() {
        assertEquals(FeedPreloadTarget.Cached(5_000L), feedPreloadTargetFor(distance = 4))
        assertEquals(FeedPreloadTarget.Cached(5_000L), feedPreloadTargetFor(distance = 5))
    }

    @Test
    fun foraDaJanelaNaoPreCarrega() {
        // Sem teto, uma lista de 200 posts baixaria os 200 em segundo plano.
        assertEquals(FeedPreloadTarget.None, feedPreloadTargetFor(distance = 6))
        assertEquals(FeedPreloadTarget.None, feedPreloadTargetFor(distance = 50))
    }

    @Test
    fun oQueJaPassouNuncaEhPreCarregado() {
        // Só para frente: o vídeo que a pessoa passou tem chance pequena de ser revisto.
        assertEquals(FeedPreloadTarget.None, feedPreloadTargetFor(distance = -1))
        assertEquals(FeedPreloadTarget.None, feedPreloadTargetFor(distance = -5))
    }

    @Test
    fun oQueTocaNaoEhPreCarregado() {
        assertEquals(FeedPreloadTarget.None, feedPreloadTargetFor(distance = 0))
    }

    @Test
    fun pausadoDesligaAEscadaInteira() {
        (-3..8).forEach { distancia ->
            assertEquals(
                FeedPreloadTarget.None,
                feedPreloadTargetFor(distancia, paused = true),
                "distância $distancia deveria estar suspensa",
            )
        }
    }

    // ------------------------------------------------------------------- fome do da vez

    @Test
    fun bufferConfortavelNaoCancela() {
        assertFalse(shouldCancelFeedPreload(bufferedAheadMillis = 8_000L, buffering = false))
    }

    @Test
    fun bufferAbaixoDeCincoSegundosCancela() {
        assertTrue(shouldCancelFeedPreload(bufferedAheadMillis = 4_999L, buffering = false))
        assertTrue(shouldCancelFeedPreload(bufferedAheadMillis = 0L, buffering = false))
    }

    @Test
    fun exatamenteCincoSegundosAindaNaoCancela() {
        assertFalse(shouldCancelFeedPreload(bufferedAheadMillis = 5_000L, buffering = false))
    }

    @Test
    fun buffenrandoCancelaMesmoSemNumero() {
        assertTrue(shouldCancelFeedPreload(bufferedAheadMillis = null, buffering = true))
        // O número pode até estar bom: se o player parou para esperar dado, é fome.
        assertTrue(shouldCancelFeedPreload(bufferedAheadMillis = 30_000L, buffering = true))
    }

    @Test
    fun semNumeroENaoBufferandoNaoEhFome() {
        // `null` = a plataforma não sabe informar. Tratar isso como fome desligaria o
        // pré-carregamento para sempre em quem não reporta buffer.
        assertFalse(shouldCancelFeedPreload(bufferedAheadMillis = null, buffering = false))
    }

    // ------------------------------------------------------------------ chave do cache

    @Test
    fun aChaveIgnoraOTokenDaUrlAssinada() {
        // O mesmo vídeo, dois tokens: uma entrada só no cache.
        val a = feedVideoCacheKey("https://cdn.exemplo.com/posts/42.mp4?token=abc&exp=1")
        val b = feedVideoCacheKey("https://cdn.exemplo.com/posts/42.mp4?token=xyz&exp=2")
        assertEquals(a, b)
        assertEquals("https://cdn.exemplo.com/posts/42.mp4", a)
    }

    @Test
    fun aChaveIgnoraOFragmento() {
        assertEquals(
            "https://cdn.exemplo.com/posts/42.mp4",
            feedVideoCacheKey("https://cdn.exemplo.com/posts/42.mp4#t=10"),
        )
    }

    @Test
    fun videosDiferentesTemChavesDiferentes() {
        assertTrue(
            feedVideoCacheKey("https://cdn.exemplo.com/posts/42.mp4?t=1") !=
                feedVideoCacheKey("https://cdn.exemplo.com/posts/43.mp4?t=1"),
        )
    }

    @Test
    fun urlSemQueryFicaComoEsta() {
        assertEquals(
            "https://cdn.exemplo.com/posts/42.m3u8",
            feedVideoCacheKey("https://cdn.exemplo.com/posts/42.m3u8"),
        )
    }

    @Test
    fun urlQueEhSoQueryNaoViraChaveVazia() {
        // Chave vazia colidiria TODO vídeo numa entrada só do cache.
        assertEquals("?token=abc", feedVideoCacheKey("?token=abc"))
    }

    @Test
    fun oItemDePreloadDerivaAChaveSozinho() {
        val item = FeedPreloadItem(
            url = "https://cdn.exemplo.com/posts/7.mp4?token=abc",
            kind = br.com.codecacto.kmplib.video.VideoStreamKind.Auto,
        )
        assertEquals("https://cdn.exemplo.com/posts/7.mp4", item.cacheKey)
    }
}
