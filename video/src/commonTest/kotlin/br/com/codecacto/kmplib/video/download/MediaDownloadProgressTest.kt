package br.com.codecacto.kmplib.video.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaDownloadProgressTest {

    @Test
    fun `total desconhecido devolve nulo, nao zero`() {
        // `null` é o que faz a tela desenhar um anel indeterminado. Zero mentiria: uma barra parada
        // em 0% com o download andando é o jeito mais rápido de o usuário concluir que travou.
        assertNull(mediaDownloadPercent(downloadedBytes = 1_000, totalBytes = 0))
        assertNull(mediaDownloadPercent(downloadedBytes = 0, totalBytes = -1))
    }

    @Test
    fun `percentual comum`() {
        assertEquals(0, mediaDownloadPercent(0, 100))
        assertEquals(50, mediaDownloadPercent(50, 100))
        assertEquals(33, mediaDownloadPercent(1, 3))
    }

    @Test
    fun `nunca chega a 100 antes de terminar`() {
        // `Content-Length` menor que o real (resposta comprimida) faria a conta bater 100 com o
        // arquivo pela metade — e a tela ficaria em "100%" por minutos.
        assertEquals(99, mediaDownloadPercent(100, 100))
        assertEquals(99, mediaDownloadPercent(200, 100))
    }

    @Test
    fun `escolha de faixa por qualidade`() {
        val faixas = listOf(600_000, 1_200_000, 2_200_000, 4_500_000)
        assertEquals(600_000, selectDownloadBitrate(faixas, MediaDownloadQuality.Low))
        assertEquals(2_200_000, selectDownloadBitrate(faixas, MediaDownloadQuality.Standard))
        assertEquals(4_500_000, selectDownloadBitrate(faixas, MediaDownloadQuality.High))
    }

    @Test
    fun `curso gravado so em alta ainda baixa`() {
        // Todas as faixas passam do teto: "padrão" cai na MENOR em vez de não escolher nenhuma.
        // Baixar a aula grande é melhor do que não baixar.
        val soAlta = listOf(6_000_000, 9_000_000)
        assertEquals(6_000_000, selectDownloadBitrate(soAlta, MediaDownloadQuality.Standard))
    }

    @Test
    fun `manifesto sem bitrate declarado deixa a escolha com a plataforma`() {
        assertNull(selectDownloadBitrate(emptyList(), MediaDownloadQuality.Standard))
        assertNull(selectDownloadBitrate(listOf(0, -1), MediaDownloadQuality.High))
    }

    @Test
    fun `id de download segue a regra do BlobStore`() {
        assertEquals(true, isValidMediaDownloadId("aula-42"))
        assertEquals(true, isValidMediaDownloadId("aula_42.v2"))
        assertEquals(false, isValidMediaDownloadId("aula/42"))
        assertEquals(false, isValidMediaDownloadId(""))
        assertEquals(false, isValidMediaDownloadId(".."))
    }
}
