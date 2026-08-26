package br.com.codecacto.kmplib.torch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TorchLevelTest {

    @Test
    fun clamp_prendeNaFaixaUtilizavel() {
        assertEquals(TorchLevel.MIN, TorchLevel.clamp(-3f))
        assertEquals(TorchLevel.MIN, TorchLevel.clamp(0f), "acender com 0 seria acender apagado")
        assertEquals(TorchLevel.MAX, TorchLevel.clamp(9f))
        assertEquals(0.5f, TorchLevel.clamp(0.5f))
    }

    @Test
    fun clamp_naoDeixaNaNPassar() {
        assertEquals(TorchLevel.MAX, TorchLevel.clamp(Float.NaN))
    }

    @Test
    fun toStep_nuncaDevolveZero() {
        // 0 é argumento INVÁLIDO em turnOnTorchWithStrengthLevel, não "apagado".
        assertEquals(1, TorchLevel.toStep(0f, levelCount = 5))
        assertEquals(1, TorchLevel.toStep(-1f, levelCount = 5))
    }

    @Test
    fun toStep_arredondaParaODegrauMaisProximo() {
        assertEquals(5, TorchLevel.toStep(1f, levelCount = 5))
        assertEquals(3, TorchLevel.toStep(0.5f, levelCount = 5))
        assertEquals(2, TorchLevel.toStep(0.37f, levelCount = 5))
    }

    @Test
    fun toStep_semNiveis_sempreUm() {
        assertEquals(1, TorchLevel.toStep(0.5f, levelCount = 1))
        assertEquals(1, TorchLevel.toStep(0.5f, levelCount = TorchCapabilities.CONTINUOUS))
    }

    @Test
    fun toFraction_ehOInversoDoDegrau() {
        assertEquals(1f, TorchLevel.toFraction(5, levelCount = 5))
        assertEquals(0.4f, TorchLevel.toFraction(2, levelCount = 5))
        assertEquals(1f, TorchLevel.toFraction(7, levelCount = 5), "degrau acima do teto é limitado")
    }

    @Test
    fun align_discreto_encaixaNoDegrauQueOHardwareAplica() {
        val caps = TorchCapabilities.resolve(true, levelCount = 5, platformSupportsIntensity = true)
        assertEquals(0.4f, TorchLevel.align(0.37f, caps), "o estado mostra a luz real, não o slider")
        assertEquals(1f, TorchLevel.align(1f, caps))
    }

    @Test
    fun align_continuo_preservaOValor() {
        val caps = iosTorchCapabilities(hasTorch = true)
        assertEquals(0.37f, TorchLevel.align(0.37f, caps))
    }

    @Test
    fun align_semIntensidade_ehSempreMaximo() {
        val caps = androidTorchCapabilities(hasFlashUnit = true, sdkInt = 30, maxLevel = 5)
        assertEquals(TorchLevel.MAX, TorchLevel.align(0.2f, caps))
    }

    @Test
    fun align_nuncaSaiDaFaixa() {
        val caps = iosTorchCapabilities(hasTorch = true)
        assertTrue(TorchLevel.align(50f, caps) <= TorchLevel.MAX)
        assertTrue(TorchLevel.align(-50f, caps) >= TorchLevel.MIN)
    }
}
