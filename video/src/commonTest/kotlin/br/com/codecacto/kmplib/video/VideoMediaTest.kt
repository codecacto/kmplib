package br.com.codecacto.kmplib.video

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoMediaTest {

    @Test
    fun `hls reconhecido pela extensao do caminho`() {
        assertEquals(VideoStreamKind.Hls, videoStreamKindOf("https://cdn.bunny.net/aula/playlist.m3u8"))
        assertEquals(VideoStreamKind.Hls, videoStreamKindOf("https://cdn.bunny.net/AULA/PLAYLIST.M3U8"))
    }

    @Test
    fun `a URL assinada continua sendo HLS`() {
        // O caso do produto: a query esconde a extensão de quem olha a string inteira, e o
        // manifesto cairia no extrator progressivo.
        val assinada = "https://cdn.bunny.net/aula/playlist.m3u8?token=abc123&expires=1788900000"
        assertEquals(VideoStreamKind.Hls, videoStreamKindOf(assinada))
    }

    @Test
    fun `mp4 e o resto sao progressivos`() {
        assertEquals(VideoStreamKind.Progressive, videoStreamKindOf("https://cdn/aula.mp4"))
        assertEquals(VideoStreamKind.Progressive, videoStreamKindOf("https://cdn/aula.mp4?x=1"))
        assertEquals(VideoStreamKind.Progressive, videoStreamKindOf("https://cdn/aula"))
    }

    @Test
    fun `fragmento tambem nao confunde`() {
        assertEquals(VideoStreamKind.Hls, videoStreamKindOf("https://cdn/a.m3u8#t=10"))
    }

    @Test
    fun `kind explicito vence a URL`() {
        val media = VideoMedia(url = "https://cdn/aula.mp4", kind = VideoStreamKind.Hls)
        assertEquals(VideoStreamKind.Hls, media.resolvedKind())
    }

    @Test
    fun `auto resolve pela URL`() {
        assertEquals(VideoStreamKind.Hls, VideoMedia(url = "https://cdn/a.m3u8").resolvedKind())
        assertEquals(VideoStreamKind.Progressive, VideoMedia(url = "https://cdn/a.mp4").resolvedKind())
    }
}
