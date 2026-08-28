package br.com.codecacto.kmplib.ui.components.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A classificação da URL é a única parte do player que roda sem tela — e é a parte que decide se o
 * vídeo toca embutido ou joga a pessoa para fora do app. Ela é coberta aqui, nas duas plataformas.
 */
class VideoSourceTest {

    @Test
    fun `reconhece as quatro formas de link do YouTube`() {
        val esperado = "dQw4w9WgXcQ"
        listOf(
            "https://www.youtube.com/watch?v=$esperado",
            "https://youtube.com/watch?v=$esperado&t=42s",
            "https://youtu.be/$esperado",
            "https://www.youtube.com/embed/$esperado",
            "https://www.youtube.com/shorts/$esperado",
            "https://www.youtube-nocookie.com/embed/$esperado?rel=0",
        ).forEach { url ->
            assertEquals(VideoSource.YouTube(esperado), videoSourceOf(url), "falhou em: $url")
        }
    }

    @Test
    fun `o id vem do parametro v, mesmo com outros antes dele`() {
        assertEquals(
            "abcdefghijk",
            youTubeIdOf("https://www.youtube.com/watch?list=PL123&v=abcdefghijk&index=2"),
        )
    }

    @Test
    fun `pagina do YouTube que nao e video nao vira player`() {
        // Sem esta trava, uma busca ou um canal abriria um player preto que nunca carrega.
        assertNull(youTubeIdOf("https://www.youtube.com/results?search_query=neurocorex"))
        assertNull(youTubeIdOf("https://www.youtube.com/@umcanal"))
    }

    @Test
    fun `arquivo de midia toca no player nativo`() {
        assertEquals(
            VideoSource.File("https://cdn.exemplo.com/aula.mp4"),
            videoSourceOf("https://cdn.exemplo.com/aula.mp4"),
        )
        assertEquals(
            VideoSource.File("https://cdn.exemplo.com/live.m3u8?token=x"),
            videoSourceOf("https://cdn.exemplo.com/live.m3u8?token=x"),
        )
    }

    @Test
    fun `o que nao sabemos tocar vira External, para abrir no navegador`() {
        assertEquals(
            VideoSource.External("https://vimeo.com/12345"),
            videoSourceOf("https://vimeo.com/12345"),
        )
    }

    @Test
    fun `vazio, nulo e nao-http nao viram video`() {
        assertNull(videoSourceOf(null))
        assertNull(videoSourceOf(""))
        assertNull(videoSourceOf("   "))
        // Pendência de conteúdo ("o parceiro ainda não mandou o link") não pode virar player.
        assertNull(videoSourceOf("a definir"))
        assertNull(videoSourceOf("javascript:alert(1)"))
    }
}
