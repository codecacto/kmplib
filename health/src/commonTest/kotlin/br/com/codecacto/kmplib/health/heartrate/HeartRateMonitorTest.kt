package br.com.codecacto.kmplib.health.heartrate

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class LinkLost : Exception("link lost")

/** Rádio de mentira: cada `connect()` consome o próximo roteiro da fila; sem roteiro, o link falha. */
private class FakeTransport : BleHeartRateTransport {
    var reason: HeartRateUnavailableReason? = null
    var reasonsSequence: ArrayDeque<HeartRateUnavailableReason?>? = null
    var scanFlow: Flow<HeartRateDevice> = flowOf()
    val links = ArrayDeque<Flow<BleLinkEvent>>()
    var connects = 0

    override suspend fun unavailableReason(): HeartRateUnavailableReason? =
        reasonsSequence?.removeFirstOrNull() ?: reason

    var permission = BluetoothPermissionStatus.GRANTED
    var permissionAfterRequest = BluetoothPermissionStatus.GRANTED
    var permissionRequests = 0

    override suspend fun permissionStatus(): BluetoothPermissionStatus = permission

    override suspend fun requestPermission(): BluetoothPermissionStatus {
        permissionRequests++
        permission = permissionAfterRequest
        return permission
    }

    override fun scan(): Flow<HeartRateDevice> = scanFlow

    override fun connect(device: HeartRateDevice): Flow<BleLinkEvent> = flow {
        connects++
        emitAll(links.removeFirstOrNull() ?: flow { throw LinkLost() })
    }
}

private val STRAP = HeartRateDevice("AA:BB", "Cinta")

private fun note(vararg values: Int) = BleLinkEvent.Notification(ByteArray(values.size) { values[it].toByte() })

/** Link que assina, manda as notificações e cai. */
private fun linkThenDrop(vararg notifications: BleLinkEvent.Notification): Flow<BleLinkEvent> = flow {
    emit(BleLinkEvent.Subscribed)
    notifications.forEach { emit(it) }
    throw LinkLost()
}

@OptIn(ExperimentalCoroutinesApi::class)
class HeartRateMonitorTest {

    private fun TestScope.recordStates(monitor: HeartRateMonitor): MutableList<HeartRateMonitorState> {
        val states = mutableListOf<HeartRateMonitorState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { monitor.state.collect { states += it } }
        return states
    }

    @Test
    fun `estado inicial e Idle`() {
        assertEquals(HeartRateMonitorState.Idle, DefaultHeartRateMonitor(FakeTransport()).state.value)
    }

    @Test
    fun `scan sem bluetooth termina sem emitir e diz o motivo`() = runTest {
        val transport = FakeTransport().apply { reason = HeartRateUnavailableReason.BLUETOOTH_OFF }
        val monitor = DefaultHeartRateMonitor(transport)
        assertTrue(monitor.scan().toList().isEmpty())
        assertEquals(HeartRateMonitorState.Unavailable(HeartRateUnavailableReason.BLUETOOTH_OFF), monitor.state.value)
    }

    @Test
    fun `scan emite cada sensor uma vez e volta a Idle`() = runTest {
        val other = HeartRateDevice("CC:DD", null)
        val transport = FakeTransport().apply { scanFlow = flowOf(STRAP, STRAP, other, STRAP) }
        val monitor = DefaultHeartRateMonitor(transport)
        val states = recordStates(monitor)
        assertEquals(listOf(STRAP, other), monitor.scan().toList())
        assertTrue(HeartRateMonitorState.Scanning in states)
        assertEquals(HeartRateMonitorState.Idle, monitor.state.value)
    }

    @Test
    fun `scan cancelado pelo coletor volta a Idle`() = runTest {
        val transport = FakeTransport().apply {
            scanFlow = flow {
                emit(STRAP)
                kotlinx.coroutines.awaitCancellation()
            }
        }
        val monitor = DefaultHeartRateMonitor(transport)
        assertEquals(STRAP, monitor.scan().first())
        assertEquals(HeartRateMonitorState.Idle, monitor.state.value)
    }

    @Test
    fun `scan interrompido pela plataforma por permissao vira Unavailable`() = runTest {
        val transport = FakeTransport().apply {
            scanFlow = flow { throw HeartRateUnavailableException(HeartRateUnavailableReason.PERMISSION_DENIED) }
        }
        val monitor = DefaultHeartRateMonitor(transport)
        assertTrue(monitor.scan().toList().isEmpty())
        assertEquals(HeartRateMonitorState.Unavailable(HeartRateUnavailableReason.PERMISSION_DENIED), monitor.state.value)
    }

    @Test
    fun `falha generica do scan encerra sem erro e volta a Idle`() = runTest {
        val transport = FakeTransport().apply {
            scanFlow = flow {
                emit(STRAP)
                throw LinkLost()
            }
        }
        val monitor = DefaultHeartRateMonitor(transport)
        assertEquals(listOf(STRAP), monitor.scan().toList())
        assertEquals(HeartRateMonitorState.Idle, monitor.state.value)
    }

    @Test
    fun `excecao do coletor do scan sobe intacta`() = runTest {
        val transport = FakeTransport().apply { scanFlow = flowOf(STRAP) }
        val monitor = DefaultHeartRateMonitor(transport)
        assertFailsWith<IllegalStateException> { monitor.scan().collect { error("tela quebrou") } }
    }

    @Test
    fun `conectado sem leitura e sem dado - nunca zero - e a FC atualiza o estado`() = runTest {
        val transport = FakeTransport()
        transport.links += flow {
            emit(BleLinkEvent.Subscribed)
            emit(note(0x06, 72))
            emit(note(0x04, 0)) // cinta frouxa: medida emitida, mas não vira bpm de tela
            emit(note(0x01)) // truncado: descartado
            emit(note(0x06, 90))
            kotlinx.coroutines.awaitCancellation()
        }
        val monitor = DefaultHeartRateMonitor(transport)
        val states = recordStates(monitor)
        val measurements = monitor.measurements(STRAP).take(3).toList()

        assertEquals(listOf(72, 0, 90), measurements.map { it.bpm })
        assertTrue(HeartRateMonitorState.Connecting(STRAP, 0) in states)
        assertTrue(HeartRateMonitorState.Connected(STRAP, null) in states)
        // Leitura inutilizável mantém o último bpm bom (o estado não passa por null nem por 0).
        val connected = states.filterIsInstance<HeartRateMonitorState.Connected>().map { it.lastBpm }
        assertEquals(listOf(null, 72, 90), connected)
        // Cancelar a coleta desliga tudo.
        assertEquals(HeartRateMonitorState.Idle, monitor.state.value)
    }

    @Test
    fun `heartRate so entrega bpm utilizavel`() = runTest {
        val transport = FakeTransport()
        transport.links += flowOf(BleLinkEvent.Subscribed, note(0x06, 70), note(0x04, 65), note(0x06, 0), note(0x06, 80))
        val monitor = DefaultHeartRateMonitor(transport)
        assertEquals(listOf(70, 80), monitor.heartRate(STRAP).take(2).toList())
    }

    @Test
    fun `link que caiu depois de mandar FC reconecta com a primeira espera e zera a conta`() = runTest {
        val transport = FakeTransport()
        transport.links += linkThenDrop(note(0x06, 100))
        transport.links += linkThenDrop() // falha sem dado: conta 1
        transport.links += linkThenDrop(note(0x06, 110)) // dado: zera
        transport.links += flowOf(BleLinkEvent.Subscribed, note(0x06, 120))
        val policy = HeartRateReconnectPolicy(delaysMillis = listOf(100, 1_000), maxConsecutiveFailures = 5)
        val monitor = DefaultHeartRateMonitor(transport, policy)
        val states = recordStates(monitor)

        assertEquals(listOf(100, 110, 120), monitor.heartRate(STRAP).take(3).toList())
        // 1ª queda (com dado) → espera 100; 2ª (sem dado, falha 1) → 100; 3ª (com dado, zerou) → 100.
        assertEquals(300, testScheduler.currentTime)
        assertEquals(4, transport.connects)
        assertTrue(HeartRateMonitorState.Connecting(STRAP, 1) in states)
    }

    @Test
    fun `falhas seguidas sem dado esperam pela escada e desistem em Lost`() = runTest {
        val transport = FakeTransport() // nenhum roteiro: toda conexão falha
        val policy = HeartRateReconnectPolicy(delaysMillis = listOf(100, 200), maxConsecutiveFailures = 2)
        val monitor = DefaultHeartRateMonitor(transport, policy)
        val states = recordStates(monitor)

        assertTrue(monitor.measurements(STRAP).toList().isEmpty())
        assertEquals(HeartRateMonitorState.Lost(STRAP), monitor.state.value)
        assertEquals(3, transport.connects)
        assertEquals(300, testScheduler.currentTime) // 100 antes da 2ª, 200 antes da 3ª
        assertTrue(HeartRateMonitorState.Connecting(STRAP, 2) in states)
    }

    @Test
    fun `sensor sem a caracteristica 0x2A37 para de tentar`() = runTest {
        val transport = FakeTransport()
        transport.links += flow { throw HeartRateUnavailableException(HeartRateUnavailableReason.NOT_A_HEART_RATE_SENSOR) }
        val monitor = DefaultHeartRateMonitor(transport)
        assertTrue(monitor.measurements(STRAP).toList().isEmpty())
        assertEquals(
            HeartRateMonitorState.Unavailable(HeartRateUnavailableReason.NOT_A_HEART_RATE_SENSOR),
            monitor.state.value,
        )
        assertEquals(1, transport.connects)
    }

    @Test
    fun `bluetooth desligado no meio para a reconexao`() = runTest {
        val transport = FakeTransport().apply {
            reasonsSequence = ArrayDeque(listOf(null, HeartRateUnavailableReason.BLUETOOTH_OFF))
        }
        transport.links += linkThenDrop(note(0x06, 88))
        val monitor = DefaultHeartRateMonitor(transport)
        assertEquals(listOf(88), monitor.heartRate(STRAP).toList())
        assertEquals(HeartRateMonitorState.Unavailable(HeartRateUnavailableReason.BLUETOOTH_OFF), monitor.state.value)
    }

    @Test
    fun `sem permissao nao chega a conectar`() = runTest {
        val transport = FakeTransport().apply { reason = HeartRateUnavailableReason.PERMISSION_DENIED }
        val monitor = DefaultHeartRateMonitor(transport)
        assertTrue(monitor.measurements(STRAP).toList().isEmpty())
        assertEquals(0, transport.connects)
        assertEquals(HeartRateMonitorState.Unavailable(HeartRateUnavailableReason.PERMISSION_DENIED), monitor.state.value)
    }

    @Test
    fun `excecao do coletor sobe intacta e nao vira reconexao`() = runTest {
        val transport = FakeTransport()
        transport.links += flowOf(BleLinkEvent.Subscribed, note(0x06, 70))
        val monitor = DefaultHeartRateMonitor(transport)
        assertFailsWith<IllegalStateException> { monitor.measurements(STRAP).collect { error("tela quebrou") } }
        assertEquals(1, transport.connects)
        assertEquals(HeartRateMonitorState.Idle, monitor.state.value)
    }

    @Test
    fun `permissao vai ao transporte - um pedido por chamada`() = runTest {
        val transport = FakeTransport().apply {
            permission = BluetoothPermissionStatus.NOT_REQUESTED
            permissionAfterRequest = BluetoothPermissionStatus.PERMANENTLY_DENIED
        }
        val monitor = DefaultHeartRateMonitor(transport)
        assertEquals(BluetoothPermissionStatus.NOT_REQUESTED, monitor.permissionStatus())
        assertEquals(0, transport.permissionRequests)
        assertEquals(BluetoothPermissionStatus.PERMANENTLY_DENIED, monitor.requestPermission())
        assertEquals(1, transport.permissionRequests)
        assertEquals(HeartRateMonitorState.Idle, monitor.state.value)
    }

    @Test
    fun `politica de reconexao repete a ultima espera e recusa configuracao invalida`() {
        val policy = HeartRateReconnectPolicy(delaysMillis = listOf(10, 20, 30))
        assertEquals(10, policy.delayBefore(0))
        assertEquals(10, policy.delayBefore(1))
        assertEquals(20, policy.delayBefore(2))
        assertEquals(30, policy.delayBefore(3))
        assertEquals(30, policy.delayBefore(99))
        assertEquals(listOf<Long>(1_000, 2_000, 5_000, 10_000), HeartRateReconnectPolicy().delaysMillis)
        assertFailsWith<IllegalArgumentException> { HeartRateReconnectPolicy(delaysMillis = emptyList()) }
        assertFailsWith<IllegalArgumentException> { HeartRateReconnectPolicy(delaysMillis = listOf(-1)) }
        assertFailsWith<IllegalArgumentException> { HeartRateReconnectPolicy(maxConsecutiveFailures = -1) }
    }
}
