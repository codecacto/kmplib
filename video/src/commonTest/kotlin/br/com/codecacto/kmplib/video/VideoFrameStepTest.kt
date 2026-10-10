package br.com.codecacto.kmplib.video

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoFrameStepTest {

    @Test
    fun taxaAusenteOuAbsurdaCaiNoPadrao() {
        assertEquals(DEFAULT_VIDEO_FRAME_RATE, effectiveFrameRate(null))
        assertEquals(DEFAULT_VIDEO_FRAME_RATE, effectiveFrameRate(-1f))
        assertEquals(DEFAULT_VIDEO_FRAME_RATE, effectiveFrameRate(0f))
        assertEquals(DEFAULT_VIDEO_FRAME_RATE, effectiveFrameRate(Float.NaN))
        assertEquals(DEFAULT_VIDEO_FRAME_RATE, effectiveFrameRate(1000f))
        assertEquals(24f, effectiveFrameRate(24f))
    }

    @Test
    fun duracaoDeUmQuadro() {
        assertEquals(1000.0 / 30, frameDurationMillisOf(30f), 1e-9)
        assertEquals(1000.0 / 60, frameDurationMillisOf(60f), 1e-9)
        assertEquals(1000.0 / 30, frameDurationMillisOf(null), 1e-9)
    }

    @Test
    fun avancaUmQuadroParaOInicioArredondadoParaCima() {
        // 30 fps: quadro 1 começa em 33,33 ms; pedir 33 ms mostraria o quadro 0.
        assertEquals(34L, frameStepTargetOf(0L, 1, 30f, 10_000L))
        assertEquals(67L, frameStepTargetOf(34L, 1, 30f, 10_000L))
        assertEquals(100L, frameStepTargetOf(67L, 1, 30f, 10_000L))
    }

    @Test
    fun posicaoTruncadaPelaPlataformaNaoPrendeOPasso() {
        // O iOS informa 33 ms para o quadro que começa em 33,33 ms: o próximo passo é o quadro 2.
        assertEquals(67L, frameStepTargetOf(33L, 1, 30f, 10_000L))
    }

    @Test
    fun dezPassosNaoAcumulamErro() {
        var pos = 0L
        repeat(10) { pos = frameStepTargetOf(pos, 1, 30f, 10_000L) }
        assertEquals(334L, pos) // quadro 10 = 333,33 ms
        repeat(10) { pos = frameStepTargetOf(pos, -1, 30f, 10_000L) }
        assertEquals(0L, pos)
    }

    @Test
    fun voltaUmQuadro() {
        assertEquals(34L, frameStepTargetOf(67L, -1, 30f, 10_000L))
        assertEquals(0L, frameStepTargetOf(34L, -1, 30f, 10_000L))
    }

    @Test
    fun grampeiaNoInicio() {
        assertEquals(0L, frameStepTargetOf(0L, -1, 30f, 10_000L))
        assertEquals(0L, frameStepTargetOf(10L, -5, 30f, 10_000L))
    }

    @Test
    fun grampeiaNoUltimoQuadro() {
        // 1 s a 30 fps: o último quadro é o 29, em 966,67 ms.
        assertEquals(967L, frameStepTargetOf(990L, 1, 30f, 1_000L))
        assertEquals(967L, frameStepTargetOf(500L, 100, 30f, 1_000L))
    }

    @Test
    fun duracaoDesconhecidaSoGrampeiaPorBaixo() {
        assertEquals(100_034L, frameStepTargetOf(100_000L, 1, 30f, 0L))
    }

    @Test
    fun usaATaxaDoVideo() {
        // 24 fps: quadro = 41,67 ms.
        assertEquals(42L, frameStepTargetOf(0L, 1, 24f, 10_000L))
        // 60 fps: quadro = 16,67 ms.
        assertEquals(17L, frameStepTargetOf(0L, 1, 60f, 10_000L))
    }

    @Test
    fun zeroPassosVoltaAoInicioDoQuadroAtual() {
        assertEquals(34L, frameStepTargetOf(50L, 0, 30f, 10_000L))
    }
}
