package br.com.codecacto.kmplib.health.heartrate

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedHeartRateSensorTest {

    @Test
    fun `scan anuncia o sensor e a FC sai no ritmo do intervalo`() = runTest {
        val sensor = SimulatedHeartRateSensor(initialBpm = 95, intervalMillis = 500)
        val monitor = createSimulatedHeartRateMonitor(sensor)
        val device = monitor.scan().first()
        assertEquals(sensor.device, device)

        val bpm = monitor.heartRate(device).take(3).toList()
        assertEquals(listOf(95, 95, 95), bpm)
        assertEquals(1_000, testScheduler.currentTime) // 3 notificações: 0, 500, 1000 ms
    }

    @Test
    fun `setBpm muda a proxima leitura e zero e cinta sem contato`() = runTest {
        val sensor = SimulatedHeartRateSensor(initialBpm = 80, intervalMillis = 1_000)
        val monitor = createSimulatedHeartRateMonitor(sensor)
        val readings = async { monitor.measurements(sensor.device).take(3).toList() }
        runCurrent()
        sensor.setBpm(0)
        advanceTimeBy(1_001)
        sensor.setBpm(260)
        val result = readings.await()
        assertEquals(listOf(80, 0, 260), result.map { it.bpm })
        assertEquals(SensorContact.NOT_DETECTED, result[1].sensorContact)
        assertTrue(result[2].isUsable)
    }

    @Test
    fun `dropLink derruba o link e o monitor reconecta sozinho`() = runTest {
        val sensor = SimulatedHeartRateSensor(initialBpm = 100, intervalMillis = 1_000)
        val monitor = createSimulatedHeartRateMonitor(sensor, HeartRateReconnectPolicy(delaysMillis = listOf(250)))
        val readings = async { monitor.heartRate(sensor.device).take(2).toList() }
        runCurrent()
        sensor.setBpm(120)
        sensor.dropLink()
        assertEquals(listOf(100, 120), readings.await())
        assertEquals(2, sensor.connections.value)
        assertEquals(250, testScheduler.currentTime)
    }

    @Test
    fun `bluetooth desligado derruba o link e para no Unavailable`() = runTest {
        val sensor = SimulatedHeartRateSensor()
        val monitor = createSimulatedHeartRateMonitor(sensor)
        val readings = async { monitor.heartRate(sensor.device).toList() }
        runCurrent()
        sensor.setUnavailable(HeartRateUnavailableReason.BLUETOOTH_OFF)
        assertEquals(listOf(72), readings.await())
        assertEquals(HeartRateMonitorState.Unavailable(HeartRateUnavailableReason.BLUETOOTH_OFF), monitor.state.value)

        sensor.setUnavailable(null)
        assertEquals(72, monitor.heartRate(sensor.device).first())
    }

    @Test
    fun `aparelho que nao e o simulado nao e sensor de FC`() = runTest {
        val sensor = SimulatedHeartRateSensor()
        val monitor = createSimulatedHeartRateMonitor(sensor)
        assertTrue(monitor.measurements(HeartRateDevice("outro", null)).toList().isEmpty())
        assertEquals(
            HeartRateMonitorState.Unavailable(HeartRateUnavailableReason.NOT_A_HEART_RATE_SENSOR),
            monitor.state.value,
        )
    }

    @Test
    fun `parametros invalidos sao recusados`() {
        assertFailsWith<IllegalArgumentException> { SimulatedHeartRateSensor(initialBpm = -1) }
        assertFailsWith<IllegalArgumentException> { SimulatedHeartRateSensor(initialBpm = 301) }
        assertFailsWith<IllegalArgumentException> { SimulatedHeartRateSensor(intervalMillis = 0) }
        assertFailsWith<IllegalArgumentException> { SimulatedHeartRateSensor().setBpm(400) }
    }
}
