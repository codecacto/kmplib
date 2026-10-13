package br.com.codecacto.kmplib.camera.guided

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Temporizador de disparo da câmera guiada (2.297.0, GAP-PT-M28). */
class GuidedCameraTimerTest {

    @Test
    fun temporizadorPresoEntreZeroEDez() {
        assertEquals(0, coerceCaptureTimerSeconds(-3))
        assertEquals(0, coerceCaptureTimerSeconds(0))
        assertEquals(5, coerceCaptureTimerSeconds(5))
        assertEquals(10, coerceCaptureTimerSeconds(10))
        assertEquals(MAX_CAPTURE_TIMER_SECONDS, coerceCaptureTimerSeconds(60))
    }

    @Test
    fun botaoPassaPorDesligadoCincoDezEVolta() {
        assertEquals(listOf(0, 5, 10), CAPTURE_TIMER_OPTIONS)
        assertEquals(5, nextCaptureTimer(0))
        assertEquals(10, nextCaptureTimer(5))
        assertEquals(0, nextCaptureTimer(10))
    }

    @Test
    fun valorForaDaListaVaiParaAProximaMaior() {
        assertEquals(5, nextCaptureTimer(3))
        assertEquals(10, nextCaptureTimer(7))
        assertEquals(0, nextCaptureTimer(-1))
    }

    @Test
    fun contagemDesceAteUmEDepoisDispara() {
        assertEquals(4, nextCountdown(5))
        assertEquals(1, nextCountdown(2))
        assertNull(nextCountdown(1))
        assertNull(nextCountdown(0))
    }

    @Test
    fun contagemDeCincoMostraCincoNumerosAntesDaFoto() {
        val mostrados = generateSequence(5) { nextCountdown(it) }.toList()
        assertEquals(listOf(5, 4, 3, 2, 1), mostrados)
    }

    @Test
    fun textosDoTemporizadorEmPortugues() {
        val texts = GuidedCameraTexts()
        assertEquals("Temporizador desligado", texts.timerLabel(0))
        assertEquals("Temporizador de 10 segundos", texts.timerLabel(10))
        assertEquals("A foto sai em 3", texts.countdown(3))
        assertEquals("Cancelar temporizador", texts.cancelTimer)
    }

    @Test
    fun idsDeAutomacao() {
        assertEquals("camera-guiada-btn-temporizador", GuidedCameraTestTags.TIMER)
        assertEquals("camera-guiada-contagem", GuidedCameraTestTags.COUNTDOWN)
    }
}
