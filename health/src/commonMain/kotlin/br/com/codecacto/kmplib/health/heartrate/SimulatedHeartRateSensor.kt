package br.com.codecacto.kmplib.health.heartrate

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Sensor de FC **simulado**, para o emulador do Android e o simulador do iOS (nenhum dos dois tem
 * Bluetooth LE) e para o teste do app. O [HeartRateMonitor] de [createSimulatedHeartRateMonitor] é o
 * MESMO motor do aparelho real — estado, reconexão, leitura da `0x2A37` —; só o rádio é de mentira, e
 * ele manda pacotes `0x2A37` no formato do Bluetooth SIG (FC, contato com a pele, intervalos RR).
 *
 * Não vai para build de loja: o app o escolhe no lugar de `createHeartRateMonitor()` por uma flag de
 * QA (ex.: `AutomationMode.isActive` do `kmplib-platform`). Nenhum dado real passa por aqui.
 *
 * ```kotlin
 * val sensor = SimulatedHeartRateSensor(initialBpm = 95)
 * val monitor = createSimulatedHeartRateMonitor(sensor)
 * sensor.setBpm(140)     // a próxima notificação já sai com 140
 * sensor.setBpm(0)       // cinta frouxa: contato não detectado, `heartRate()` não emite
 * sensor.dropLink()      // derruba o link; o monitor reconecta sozinho
 * sensor.setUnavailable(HeartRateUnavailableReason.BLUETOOTH_OFF)
 * ```
 *
 * @param device o aparelho anunciado no scan.
 * @param initialBpm FC das primeiras notificações (0 = sem contato).
 * @param intervalMillis intervalo entre notificações (uma cinta real manda ~1 por segundo).
 */
class SimulatedHeartRateSensor(
    val device: HeartRateDevice = HeartRateDevice(id = "simulated-hr", name = "Sensor simulado"),
    initialBpm: Int = 72,
    val intervalMillis: Long = 1_000,
) {
    init {
        require(initialBpm in 0..MAX_BPM) { "bpm fora de 0..$MAX_BPM" }
        require(intervalMillis > 0) { "intervalMillis deve ser positivo" }
    }

    private val bpm = MutableStateFlow(initialBpm)
    private val unavailable = MutableStateFlow<HeartRateUnavailableReason?>(null)
    private val linkGeneration = MutableStateFlow(0)
    private val _connections = MutableStateFlow(0)

    /** Quantas vezes o monitor conectou (a 1ª + cada reconexão) — para o teste conferir a reconexão. */
    val connections: StateFlow<Int> = _connections.asStateFlow()

    /** FC das próximas notificações; 0 = cinta sem contato com a pele. */
    fun setBpm(value: Int) {
        require(value in 0..MAX_BPM) { "bpm fora de 0..$MAX_BPM" }
        bpm.value = value
    }

    /** `null` = rádio pronto. Com um motivo, a próxima verificação do monitor o vê — e o link aberto cai,
     * como acontece ao desligar o Bluetooth com a cinta conectada. */
    fun setUnavailable(reason: HeartRateUnavailableReason?) {
        unavailable.value = reason
        if (reason != null) dropLink()
    }

    /** Derruba o link aberto (cinta fora de alcance). O monitor reconecta pela política dele. */
    fun dropLink() {
        linkGeneration.value += 1
    }

    internal val transport: BleHeartRateTransport = object : BleHeartRateTransport {
        override suspend fun unavailableReason(): HeartRateUnavailableReason? = unavailable.value

        override fun scan(): Flow<HeartRateDevice> = flow {
            emit(device)
            awaitCancellation() // um scan real só termina quando o coletor desiste
        }

        override fun connect(device: HeartRateDevice): Flow<BleLinkEvent> = flow {
            if (device.id != this@SimulatedHeartRateSensor.device.id) {
                throw HeartRateUnavailableException(HeartRateUnavailableReason.NOT_A_HEART_RATE_SENSOR)
            }
            val generation = linkGeneration.value
            _connections.value += 1
            emit(BleLinkEvent.Subscribed)
            while (true) {
                if (linkGeneration.value != generation) throw SimulatedLinkLostException()
                emit(BleLinkEvent.Notification(HeartRateMeasurementEncoder.encode(bpm.value)))
                val dropped = withTimeoutOrNull(intervalMillis) { linkGeneration.first { it != generation } }
                if (dropped != null) throw SimulatedLinkLostException()
            }
        }
    }

    private companion object {
        const val MAX_BPM = 300
    }
}

/** Monitor sobre o [sensor] simulado — ver [SimulatedHeartRateSensor]. */
fun createSimulatedHeartRateMonitor(
    sensor: SimulatedHeartRateSensor,
    policy: HeartRateReconnectPolicy = HeartRateReconnectPolicy(),
): HeartRateMonitor = DefaultHeartRateMonitor(sensor.transport, policy)

private class SimulatedLinkLostException : Exception("simulated link lost")

/**
 * Monta uma notificação `0x2A37` (o inverso de [HeartRateMeasurementParser]): contato suportado, e
 * detectado quando `bpm > 0`; FC em UINT8 até 255 e UINT16 acima; um intervalo RR coerente com a FC.
 */
internal object HeartRateMeasurementEncoder {
    fun encode(bpm: Int): ByteArray {
        val uint16 = bpm > 0xFF
        var flags = 0x04 or 0x10 // contato suportado + RR presente
        if (bpm > 0) flags = flags or 0x02
        if (uint16) flags = flags or 0x01
        val bytes = mutableListOf(flags.toByte())
        bytes += bpm.toByte()
        if (uint16) bytes += (bpm shr 8).toByte()
        if (bpm > 0) {
            val rr = (60.0 / bpm * 1024).roundToInt() // 1/1024 s
            bytes += rr.toByte()
            bytes += (rr shr 8).toByte()
        }
        return bytes.toByteArray()
    }
}
