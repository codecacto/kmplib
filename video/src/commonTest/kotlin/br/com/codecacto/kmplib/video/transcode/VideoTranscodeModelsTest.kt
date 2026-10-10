package br.com.codecacto.kmplib.video.transcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoTranscodeModelsTest {

    @Test
    fun semCorteEhOVideoInteiro() {
        assertEquals(ResolvedVideoTrim(0, 60_000), resolveVideoTrim(null, 60_000))
        assertEquals(ResolvedVideoTrim(0, 0), resolveVideoTrim(null, 0))
    }

    @Test
    fun corteValidoEFimGrampeadoNaDuracao() {
        assertEquals(ResolvedVideoTrim(5_000, 20_000), resolveVideoTrim(VideoTrim(5_000, 20_000), 60_000))
        assertEquals(ResolvedVideoTrim(5_000, 60_400), resolveVideoTrim(VideoTrim(5_000, 61_000), 60_400))
        assertEquals(ResolvedVideoTrim(5_000, 60_000), resolveVideoTrim(VideoTrim(5_000), 60_000))
        assertEquals(15_000, resolveVideoTrim(VideoTrim(5_000, 20_000), 60_000)?.durationMillis)
    }

    @Test
    fun corteImpossivelDevolveNulo() {
        assertNull(resolveVideoTrim(VideoTrim(-1, 10_000), 60_000))
        assertNull(resolveVideoTrim(VideoTrim(10_000, 10_000), 60_000))
        assertNull(resolveVideoTrim(VideoTrim(10_000, 5_000), 60_000))
        assertNull(resolveVideoTrim(VideoTrim(60_000), 60_000))
        assertNull(resolveVideoTrim(VideoTrim(59_800), 60_000)) // sobra 200 ms < mínimo
    }

    @Test
    fun corteComDuracaoDesconhecidaSoConfereOProprioTrecho() {
        assertEquals(ResolvedVideoTrim(1_000, 5_000), resolveVideoTrim(VideoTrim(1_000, 5_000), 0))
        assertEquals(ResolvedVideoTrim(1_000, 0), resolveVideoTrim(VideoTrim(1_000), 0))
    }

    @Test
    fun medidaReduzOLadoMenorA720MantendoAProporcao() {
        assertEquals(VideoDimensions(1280, 720), targetVideoDimensions(1920, 1080, 720))
        assertEquals(VideoDimensions(720, 1280), targetVideoDimensions(1080, 1920, 720))
        assertEquals(VideoDimensions(1280, 720), targetVideoDimensions(3840, 2160, 720))
        // 4:3 em pé
        assertEquals(VideoDimensions(720, 960), targetVideoDimensions(3024, 4032, 720))
    }

    @Test
    fun medidaNuncaAmpliaESaiPar() {
        assertEquals(VideoDimensions(640, 480), targetVideoDimensions(640, 480, 720))
        assertEquals(VideoDimensions(720, 1280), targetVideoDimensions(720, 1280, 720))
        assertEquals(VideoDimensions(640, 360), targetVideoDimensions(639, 361, 720))
        // 1366 × 768 → 768 > 720: 1280,6 × 720 → par 1280 × 720
        assertEquals(VideoDimensions(1280, 720), targetVideoDimensions(1366, 768, 720))
    }

    @Test
    fun medidaInvalidaDevolveNulo() {
        assertNull(targetVideoDimensions(0, 1080, 720))
        assertNull(targetVideoDimensions(1920, -1, 720))
        assertNull(targetVideoDimensions(1920, 1080, 0))
    }

    @Test
    fun perfilPadraoCabeEm15MegaPorMinutoSemAudio() {
        val bytes = estimatedTranscodeBytes(60_000, VideoTranscodeProfile.H264_720P, withAudio = false)
        // 2 Mbps × 60 s ÷ 8 = 15.000.000 + 2% de contêiner.
        assertEquals(15_300_000L, bytes)
        assertTrue(bytes <= 16L * 1024 * 1024)
    }

    @Test
    fun estimativaSomaOAudioQuandoHa() {
        val semAudio = estimatedTranscodeBytes(10_000, VideoTranscodeProfile.H264_720P, withAudio = false)
        val comAudio = estimatedTranscodeBytes(10_000, VideoTranscodeProfile.H264_720P, withAudio = true)
        assertEquals(96_000L * 10 / 8 * 102 / 100, comAudio - semAudio)
        assertEquals(0L, estimatedTranscodeBytes(0, VideoTranscodeProfile.H264_720P, withAudio = true))
    }

    @Test
    fun perfilPadraoEh720p2Mbps30fps() {
        val p = VideoTranscodeProfile.H264_720P
        assertEquals(720, p.maxShortSidePx)
        assertEquals(2_000_000, p.videoBitrate)
        assertEquals(30, p.frameRate)
        assertEquals(1f, p.keyFrameIntervalSeconds)
    }

    @Test
    fun perfilRecusaValorAbsurdo() {
        assertFailsWith<IllegalArgumentException> { VideoTranscodeProfile(maxShortSidePx = 10) }
        assertFailsWith<IllegalArgumentException> { VideoTranscodeProfile(videoBitrate = 1_000) }
        assertFailsWith<IllegalArgumentException> { VideoTranscodeProfile(frameRate = 0) }
        assertFailsWith<IllegalArgumentException> { VideoTranscodeProfile(keyFrameIntervalSeconds = 0f) }
        assertFailsWith<IllegalArgumentException> { VideoTranscodeProfile(thumbnailMaxDimension = 10) }
        VideoTranscodeProfile(thumbnailMaxDimension = 0) // sem miniatura é válido
    }

    @Test
    fun descarteDeQuadroPara30fps() {
        assertTrue(shouldKeepVideoFrame(0, null, 30))
        // Fonte a 60 fps (16.667 µs): mantém um sim, um não.
        assertFalse(shouldKeepVideoFrame(16_667, 0, 30))
        assertTrue(shouldKeepVideoFrame(33_333, 0, 30))
        // Fonte a 29,97 fps (33.367 µs) não perde quadro.
        assertTrue(shouldKeepVideoFrame(33_367, 0, 30))
        // Folga de 5%: 31,7 ms ainda passa.
        assertTrue(shouldKeepVideoFrame(31_700, 0, 30))
        assertTrue(shouldKeepVideoFrame(100, 0, 0))
    }

    @Test
    fun fonteReconheceUriECaminho() {
        assertTrue(VideoTranscodeSource.of("content://media/external/video/1").isUri)
        assertTrue(VideoTranscodeSource.of("file:///data/x.mp4").isUri)
        assertFalse(VideoTranscodeSource.of("/var/mobile/tmp/x.mov").isUri)
        assertFalse(VideoTranscodeSource.fromPath("/x").isUri)
        assertTrue(VideoTranscodeSource.fromUri("file:///x").isUri)
    }

    @Test
    fun toStringNaoVazaCaminho() {
        val fonte = VideoTranscodeSource.fromPath("/data/user/0/app/cache/joao-silva-agachamento.mp4")
        assertFalse("joao" in fonte.toString())
        val pronto = PreparedVideo(
            path = "/data/cache/kmplib_video_prepared/joao.mp4",
            uri = "file:///data/cache/kmplib_video_prepared/joao.mp4",
            sizeBytes = 1, durationMillis = 59_001, widthPx = 720, heightPx = 1280, hasAudio = false,
            thumbnailJpeg = null, thumbnailWidthPx = 0, thumbnailHeightPx = 0,
        )
        assertFalse("joao" in pronto.toString())
        assertEquals(60, pronto.durationSeconds)
        assertEquals("video/mp4", pronto.mimeType)
    }
}
