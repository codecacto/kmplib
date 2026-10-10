package br.com.codecacto.kmplib.platform.haptics

import br.com.codecacto.kmplib.platform.haptics.HapticSegment.Pause
import br.com.codecacto.kmplib.platform.haptics.HapticSegment.Vibrate
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HapticsTest {

    @Test
    fun padraoValidaEntrada() {
        assertFailsWith<IllegalArgumentException> { VibrationPattern(emptyList()) }
        assertFailsWith<IllegalArgumentException> { VibrationPattern.of(Pause(100)) }
        assertFailsWith<IllegalArgumentException> { VibrationPattern.of(Vibrate(0)) }
        assertFailsWith<IllegalArgumentException> { VibrationPattern.of(Vibrate(-1)) }
        assertFailsWith<IllegalArgumentException> { VibrationPattern.of(Vibrate(100, 0f)) }
        assertFailsWith<IllegalArgumentException> { VibrationPattern.of(Vibrate(100, 1.1f)) }
        assertFailsWith<IllegalArgumentException> { VibrationPattern.of(Vibrate(30_001)) }
        assertFailsWith<IllegalArgumentException> { VibrationPattern.pulses(0) }
        assertEquals(30_000, VibrationPattern.of(Vibrate(30_000)).totalDurationMillis)
    }

    @Test
    fun presetsSaoValidosEAlertaTemTresPulsos() {
        listOf(VibrationPattern.Tick, VibrationPattern.Confirm, VibrationPattern.Alert).forEach {
            assertTrue(it.totalDurationMillis in 1..VibrationPattern.MAX_TOTAL_MILLIS)
        }
        assertEquals(3, VibrationPattern.Alert.segments.count { it is Vibrate })
        assertEquals(300 * 3 + 200 * 2L, VibrationPattern.Alert.totalDurationMillis)
    }

    @Test
    fun waveformAlternaParadoEVibrandoComecandoParado() {
        val p = VibrationPattern.waveform(0, 400, 200, 400)
        assertEquals(listOf(Pause(0), Vibrate(400), Pause(200), Vibrate(400)), p.segments)
    }

    @Test
    fun amplitudeDescartaTrechoVazioEMapeiaIntensidade() {
        val w = VibrationPattern.of(Pause(0), Vibrate(100, 1f), Pause(50), Vibrate(80, 0.5f), Vibrate(10, 0.001f))
            .toAmplitudeWaveform()
        assertContentEquals(longArrayOf(100, 50, 80, 10), w.timings)
        assertContentEquals(intArrayOf(255, 0, 128, 1), w.amplitudes)
    }

    @Test
    fun ligaDesligaComecaParadoESomaVizinhos() {
        val t = VibrationPattern.of(Vibrate(100), Vibrate(50, 0.3f), Pause(20), Pause(30), Vibrate(70))
            .toOnOffTimings()
        assertContentEquals(longArrayOf(0, 150, 50, 70), t)

        val comPausaInicial = VibrationPattern.of(Pause(500), Vibrate(100)).toOnOffTimings()
        assertContentEquals(longArrayOf(500, 100), comPausaInicial)
    }

    @Test
    fun eventosCoreHapticsTemInicioRelativo() {
        val eventos = VibrationPattern.pulses(count = 3, onMillis = 300, gapMillis = 200).timedVibrations()
        assertEquals(
            listOf(TimedVibration(0, 300, 1f), TimedVibration(500, 300, 1f), TimedVibration(1000, 300, 1f)),
            eventos,
        )
    }

    @Test
    fun fallbackDeImpactoRepeteDentroDoTrecho() {
        val agenda = VibrationPattern.of(Vibrate(250, 0.7f), Pause(100), Vibrate(30)).impactSchedule()
        assertEquals(listOf(0L to 0.7f, 100L to 0.7f, 200L to 0.7f, 350L to 1f), agenda)
    }

    @Test
    fun configuracaoDoSistemaDecidePorUso() {
        assertFalse(systemAllowsVibration(HapticUsage.NOTIFICATION, RingerMode.SILENT, touchHapticsEnabled = true))
        assertTrue(systemAllowsVibration(HapticUsage.NOTIFICATION, RingerMode.VIBRATE, touchHapticsEnabled = false))
        assertTrue(systemAllowsVibration(HapticUsage.NOTIFICATION, RingerMode.NORMAL, touchHapticsEnabled = true))
        assertTrue(systemAllowsVibration(HapticUsage.ALARM, RingerMode.SILENT, touchHapticsEnabled = false))
        assertFalse(systemAllowsVibration(HapticUsage.TOUCH, RingerMode.NORMAL, touchHapticsEnabled = false))
        assertTrue(systemAllowsVibration(HapticUsage.TOUCH, RingerMode.NORMAL, touchHapticsEnabled = true))
    }
}
