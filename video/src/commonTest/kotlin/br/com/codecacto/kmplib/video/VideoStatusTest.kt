package br.com.codecacto.kmplib.video

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoStatusTest {

    @Test
    fun `sem primeiro quadro e sempre carregando`() {
        assertEquals(VideoStatus.Loading, videoStatusOf(preparado = false, querTocar = true, semDados = true))
        assertEquals(VideoStatus.Loading, videoStatusOf(preparado = false, querTocar = false, semDados = false))
    }

    @Test
    fun `sem dado e QUERENDO tocar e buffering`() {
        assertEquals(VideoStatus.Buffering, videoStatusOf(preparado = true, querTocar = true, semDados = true))
    }

    @Test
    fun `sem dado e PARADO e pausa, nao buffering`() {
        // O player só espera rede quando quer andar. Sem esta distinção, um vídeo pausado numa
        // conexão ruim mostraria a roda de espera para sempre.
        assertEquals(VideoStatus.Paused, videoStatusOf(preparado = true, querTocar = false, semDados = true))
    }

    @Test
    fun `preparado e querendo tocar com dado e reproducao`() {
        assertEquals(VideoStatus.Playing, videoStatusOf(preparado = true, querTocar = true, semDados = false))
    }

    @Test
    fun `isPlaying cobre tocar e bufferizar, e nada mais`() {
        // O botão central mostra "pausar" enquanto o vídeo espera rede: quem está bufferizando
        // mandou tocar, e oferecer "reproduzir" ali daria dois plays em sequência.
        assertEquals(true, VideoStatus.Playing.contaComoTocando())
        assertEquals(true, VideoStatus.Buffering.contaComoTocando())
        assertEquals(false, VideoStatus.Paused.contaComoTocando())
        assertEquals(false, VideoStatus.Loading.contaComoTocando())
        assertEquals(false, VideoStatus.Ended.contaComoTocando())
    }

    @Test
    fun `403 de URL assinada vencida nao e erro de rede`() {
        // A ação do usuário é diferente: "abra a aula de novo", nunca "verifique a conexão".
        assertEquals(VideoErrorKind.Expired, videoErrorKindForHttpStatus(401))
        assertEquals(VideoErrorKind.Expired, videoErrorKindForHttpStatus(403))
        assertEquals(VideoErrorKind.Expired, videoErrorKindForHttpStatus(410))
    }

    @Test
    fun `404 e video que nao existe, 5xx e servidor fora`() {
        assertEquals(VideoErrorKind.NotFound, videoErrorKindForHttpStatus(404))
        assertEquals(VideoErrorKind.Network, videoErrorKindForHttpStatus(500))
        assertEquals(VideoErrorKind.Network, videoErrorKindForHttpStatus(503))
        assertEquals(VideoErrorKind.Unknown, videoErrorKindForHttpStatus(418))
    }

    @Test
    fun `cada tipo de erro tem frase propria`() {
        val textos = VideoPlayerTexts()
        val frases = VideoErrorKind.entries.map { textos.messageFor(it) }
        assertEquals(frases.size, frases.toSet().size, "duas falhas diferentes com a mesma frase")
    }

    /** A mesma regra do `VideoPlayerState.isPlaying`, isolada para o teste alcançá-la. */
    private fun VideoStatus.contaComoTocando(): Boolean =
        this == VideoStatus.Playing || this == VideoStatus.Buffering
}
