package br.com.codecacto.kmplib.health

import br.com.codecacto.kmplib.health.heartrate.HeartRateDevice
import br.com.codecacto.kmplib.health.heartrate.HeartRateMeasurement
import br.com.codecacto.kmplib.health.heartrate.HeartRateMonitorState
import br.com.codecacto.kmplib.health.heartrate.SensorContact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/** Dado de saúde (bpm, kcal, RR) e o que identifica o aparelho da pessoa não saem no `toString` — é ele
 * que vai parar no log e no GlitchTip quando alguém interpola o objeto. A igualdade continua por valor. */
class HealthToStringRedactionTest {

    private val device = HeartRateDevice(id = "AA:BB:CC:DD:EE:FF", name = "Polar H10 de Maria")

    private fun assertHides(text: String, vararg secrets: String) {
        secrets.forEach { assertFalse(text.contains(it), "\"$it\" vazou em: $text") }
    }

    @Test
    fun `resumo de FC e energia sem valores`() {
        assertHides(HeartRateSummary(avg = 137, max = 181).toString(), "137", "181")
        val energy = EnergyReading(kcal = 412.5)
        assertHides(energy.toString(), "412")
        assertEquals("EnergyReading(source=DEVICE)", energy.toString())
        val metrics = SessionMetrics(HealthMetric.Value(HeartRateSummary(137, 181)), HealthMetric.Value(EnergyReading(412.5)))
        assertHides(metrics.toString(), "137", "181", "412")
        assertHides(HealthMetric.Value(99).toString(), "99")
        assertHides(HeartRateStats(avg = 137.4, max = 181.0).toString(), "137", "181")
        assertHides(StoredWorkout(fromThisApp = false, externalId = "run-123").toString(), "run-123")
    }

    @Test
    fun `medicao sem bpm - energia e RR`() {
        val m = HeartRateMeasurement(bpm = 163, sensorContact = SensorContact.DETECTED, energyExpendedKj = 857, rrIntervalsMillis = listOf(368.1, 372.4))
        assertHides(m.toString(), "163", "857", "368", "372")
        assertEquals("HeartRateMeasurement(sensorContact=DETECTED, hasEnergy=true, rrIntervals=2)", m.toString())
    }

    @Test
    fun `estados do monitor sem bpm e sem o aparelho`() {
        assertHides(device.toString(), "AA:BB", "Polar", "Maria")
        assertEquals("Connected(hasBpm=true)", HeartRateMonitorState.Connected(device, 151).toString())
        assertEquals("Connected(hasBpm=false)", HeartRateMonitorState.Connected(device, null).toString())
        assertEquals("Connecting(attempt=2)", HeartRateMonitorState.Connecting(device, 2).toString())
        assertHides(HeartRateMonitorState.Lost(device).toString(), "AA:BB", "Polar")
    }

    @Test
    fun `igualdade continua por valor`() {
        assertEquals(HeartRateSummary(120, 150), HeartRateSummary(120, 150))
        assertNotEquals(HeartRateSummary(120, 150), HeartRateSummary(121, 150))
        assertEquals(HeartRateMonitorState.Connected(device, 151), HeartRateMonitorState.Connected(device, 151))
        assertNotEquals(HeartRateMonitorState.Connected(device, 151), HeartRateMonitorState.Connected(device, 152))
        assertNotEquals(device, device.copy(id = "11:22"))
    }
}
