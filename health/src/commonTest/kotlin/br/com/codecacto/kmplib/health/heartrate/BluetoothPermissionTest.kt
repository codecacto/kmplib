package br.com.codecacto.kmplib.health.heartrate

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val G = BluetoothPermissionStatus.GRANTED
private val D = BluetoothPermissionStatus.DENIED
private val P = BluetoothPermissionStatus.PERMANENTLY_DENIED
private val N = BluetoothPermissionStatus.NOT_REQUESTED

class BluetoothPermissionTest {

    @Test
    fun `status do Android - concedida vence tudo`() {
        assertEquals(G, bluetoothPermissionStatusOf(allGranted = true, askedBefore = false, canExplain = null))
        assertEquals(G, bluetoothPermissionStatusOf(allGranted = true, askedBefore = true, canExplain = false))
    }

    @Test
    fun `status do Android - nunca pedida e NOT_REQUESTED mesmo sem poder explicar`() {
        // shouldShowRequestPermissionRationale = false antes do 1o pedido: nao e negacao definitiva.
        assertEquals(N, bluetoothPermissionStatusOf(allGranted = false, askedBefore = false, canExplain = false))
        assertEquals(N, bluetoothPermissionStatusOf(allGranted = false, askedBefore = false, canExplain = null))
    }

    @Test
    fun `status do Android - ja pedida separa negada de negada de vez`() {
        assertEquals(D, bluetoothPermissionStatusOf(allGranted = false, askedBefore = true, canExplain = true))
        assertEquals(P, bluetoothPermissionStatusOf(allGranted = false, askedBefore = true, canExplain = false))
        // Sem Activity para perguntar: conservador, ainda oferece "Permitir".
        assertEquals(D, bluetoothPermissionStatusOf(allGranted = false, askedBefore = true, canExplain = null))
    }

    @Test
    fun `resultado do dialogo do Android`() {
        assertEquals(G, bluetoothPermissionRequestResult(allGranted = true, canExplain = false))
        assertEquals(D, bluetoothPermissionRequestResult(allGranted = false, canExplain = true))
        assertEquals(P, bluetoothPermissionRequestResult(allGranted = false, canExplain = false))
    }

    @Test
    fun `dubles mantidos por apps compilam e respondem concedida pelo default da interface`() = runTest {
        val legacy = object : HeartRateMonitor {
            override val state = kotlinx.coroutines.flow.MutableStateFlow<HeartRateMonitorState>(HeartRateMonitorState.Idle)
            override fun scan() = kotlinx.coroutines.flow.emptyFlow<HeartRateDevice>()
            override fun measurements(device: HeartRateDevice) = kotlinx.coroutines.flow.emptyFlow<HeartRateMeasurement>()
        }
        assertEquals(G, legacy.permissionStatus())
        assertEquals(G, legacy.requestPermission())
    }

    @Test
    fun `simulado - default concedido - como antes`() = runTest {
        val monitor = createSimulatedHeartRateMonitor(SimulatedHeartRateSensor())
        assertEquals(G, monitor.permissionStatus())
        assertEquals(G, monitor.requestPermission())
    }

    @Test
    fun `simulado - pedido aplica a resposta configurada e libera o scan`() = runTest {
        val sensor = SimulatedHeartRateSensor()
        sensor.setPermission(N, afterRequest = G)
        val monitor = createSimulatedHeartRateMonitor(sensor)

        assertEquals(N, monitor.permissionStatus())
        assertTrue(monitor.scan().toList().isEmpty())
        assertEquals(HeartRateMonitorState.Unavailable(HeartRateUnavailableReason.PERMISSION_DENIED), monitor.state.value)

        assertEquals(G, monitor.requestPermission())
        assertEquals(G, monitor.permissionStatus())
        // O aviso de permissao deixado pelo scan some: a tela volta a poder procurar.
        assertEquals(HeartRateMonitorState.Idle, monitor.state.value)
    }

    @Test
    fun `simulado - negada continua negada e negada de vez nao abre dialogo`() = runTest {
        val sensor = SimulatedHeartRateSensor()
        sensor.setPermission(N, afterRequest = D)
        val monitor = createSimulatedHeartRateMonitor(sensor)
        assertEquals(D, monitor.requestPermission())

        sensor.setPermission(P, afterRequest = G)
        assertEquals(P, monitor.requestPermission())
        assertEquals(P, monitor.permissionStatus())
    }

    @Test
    fun `negada nao limpa o aviso de permissao e outro motivo nao e apagado`() = runTest {
        val sensor = SimulatedHeartRateSensor()
        sensor.setPermission(N, afterRequest = D)
        val monitor = createSimulatedHeartRateMonitor(sensor)
        monitor.scan().toList()
        assertEquals(D, monitor.requestPermission())
        assertEquals(HeartRateMonitorState.Unavailable(HeartRateUnavailableReason.PERMISSION_DENIED), monitor.state.value)

        val off = SimulatedHeartRateSensor().apply { setUnavailable(HeartRateUnavailableReason.BLUETOOTH_OFF) }
        val offMonitor = createSimulatedHeartRateMonitor(off)
        offMonitor.scan().toList()
        assertEquals(G, offMonitor.requestPermission())
        assertEquals(HeartRateMonitorState.Unavailable(HeartRateUnavailableReason.BLUETOOTH_OFF), offMonitor.state.value)
    }
}
