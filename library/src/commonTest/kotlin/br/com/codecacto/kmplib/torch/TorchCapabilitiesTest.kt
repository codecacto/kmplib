package br.com.codecacto.kmplib.torch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TorchCapabilitiesTest {

    @Test
    fun semFlash_naoTemNadaDisponivel() {
        val caps = TorchCapabilities.resolve(hasTorch = false, levelCount = 8, platformSupportsIntensity = true)
        assertEquals(TorchCapabilities.NONE, caps)
        assertFalse(caps.hasTorch)
        assertFalse(caps.supportsIntensity)
    }

    @Test
    fun tetoDeUmNivel_naoEhIntensidadeVariavel() {
        val caps = TorchCapabilities.resolve(hasTorch = true, levelCount = 1, platformSupportsIntensity = true)
        assertTrue(caps.hasTorch)
        assertFalse(caps.supportsIntensity, "um nível só não é intensidade variável")
        assertEquals(0, caps.sliderSteps)
    }

    @Test
    fun tetoMaiorQueUm_comSuporteDaPlataforma_habilitaIntensidade() {
        val caps = TorchCapabilities.resolve(hasTorch = true, levelCount = 5, platformSupportsIntensity = true)
        assertTrue(caps.supportsIntensity)
        assertEquals(5, caps.levelCount)
        assertEquals(4, caps.sliderSteps)
        assertFalse(caps.isContinuous)
    }

    @Test
    fun faixaContinua_ehIntensidadeSemDegraus() {
        val caps = TorchCapabilities.resolve(
            hasTorch = true,
            levelCount = TorchCapabilities.CONTINUOUS,
            platformSupportsIntensity = true,
        )
        assertTrue(caps.supportsIntensity)
        assertTrue(caps.isContinuous)
        assertEquals(0, caps.sliderSteps, "faixa contínua não tem degraus no slider")
    }

    // ---- Android: a decisão depende de VERSÃO DO SO **e** de teto de níveis ---------------------

    @Test
    fun android_antesDo13_naoTemIntensidade_mesmoComTetoAlto() {
        val caps = androidTorchCapabilities(hasFlashUnit = true, sdkInt = 32, maxLevel = 5)
        assertTrue(caps.hasTorch)
        assertFalse(caps.supportsIntensity, "turnOnTorchWithStrengthLevel só existe na API 33+")
    }

    @Test
    fun android_no13_comTetoAlto_temIntensidade() {
        val caps = androidTorchCapabilities(hasFlashUnit = true, sdkInt = ANDROID_VARIABLE_TORCH_MIN_SDK, maxLevel = 5)
        assertTrue(caps.supportsIntensity)
        assertEquals(5, caps.levelCount)
    }

    @Test
    fun android_no13_comTetoUm_naoTemIntensidade() {
        val caps = androidTorchCapabilities(hasFlashUnit = true, sdkInt = 34, maxLevel = 1)
        assertFalse(caps.supportsIntensity, "FLASH_INFO_STRENGTH_MAXIMUM_LEVEL == 1 é liga/desliga")
    }

    @Test
    fun android_tetoAusenteOuInvalido_viraUmNivel() {
        val caps = androidTorchCapabilities(hasFlashUnit = true, sdkInt = 34, maxLevel = 0)
        assertFalse(caps.supportsIntensity)
        assertEquals(1, caps.levelCount)
    }

    @Test
    fun android_semFlash_naoTemLanterna() {
        val caps = androidTorchCapabilities(hasFlashUnit = false, sdkInt = 34, maxLevel = 5)
        assertFalse(caps.hasTorch)
    }

    // ---- iOS: contínuo em toda versão suportada ------------------------------------------------

    @Test
    fun ios_comLed_temIntensidadeContinua() {
        val caps = iosTorchCapabilities(hasTorch = true)
        assertTrue(caps.supportsIntensity)
        assertTrue(caps.isContinuous)
    }

    @Test
    fun ios_semLed_naoTemNada() {
        assertEquals(TorchCapabilities.NONE, iosTorchCapabilities(hasTorch = false))
    }
}
