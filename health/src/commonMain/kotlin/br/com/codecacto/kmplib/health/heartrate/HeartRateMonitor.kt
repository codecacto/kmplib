package br.com.codecacto.kmplib.health.heartrate

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion

/**
 * Um sensor que anuncia o serviço `0x180D` — cinta peitoral, ou relógio em modo "transmitir FC"
 * (Garmin, Amazfit, Polar, Huawei, Xiaomi Band). [id] é o endereço no Android e o `identifier` do
 * `CBPeripheral` no iOS (estável por aparelho, não é o MAC). [name] é o nome anunciado, quando há.
 * Identifica o aparelho da pessoa: não vai para log nem para o servidor.
 */
data class HeartRateDevice(val id: String, val name: String?) {
    /** Sem o id e o nome (identificam o aparelho da pessoa — o nome anunciado costuma trazer o dela). */
    override fun toString(): String = "HeartRateDevice(<redigido>)"
}

enum class HeartRateUnavailableReason {
    /** O aparelho não tem Bluetooth LE. */
    NOT_SUPPORTED,

    /** Bluetooth desligado. */
    BLUETOOTH_OFF,

    /** Sem permissão (Android 12+: `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`; até o 11: localização; iOS:
     * Bluetooth negado nos Ajustes). Quem pede a permissão é o app. */
    PERMISSION_DENIED,

    /** Conectou, mas o aparelho não tem a característica `0x2A37`. */
    NOT_A_HEART_RATE_SENSOR,
}

sealed interface HeartRateMonitorState {
    /** Nada em andamento (estado inicial e depois de parar de coletar — o rádio fica desligado). */
    data object Idle : HeartRateMonitorState

    data class Unavailable(val reason: HeartRateUnavailableReason) : HeartRateMonitorState

    data object Scanning : HeartRateMonitorState

    /** Conectando; [attempt] > 0 = reconectando depois de o link cair. */
    data class Connecting(val device: HeartRateDevice, val attempt: Int) : HeartRateMonitorState {
        override fun toString(): String = "Connecting(attempt=$attempt)"
    }

    /** Conectado e assinando. [lastBpm] `null` = **sem dado ainda** (a cinta leva alguns segundos para
     * mandar a primeira leitura válida) — a tela mostra "aguardando", nunca "0 bpm". */
    data class Connected(val device: HeartRateDevice, val lastBpm: Int?) : HeartRateMonitorState {
        /** Sem o bpm (dado de saúde): só se já há leitura. */
        override fun toString(): String = "Connected(hasBpm=${lastBpm != null})"
    }

    /** Desistiu de reconectar depois de [HeartRateReconnectPolicy.maxConsecutiveFailures] falhas. */
    data class Lost(val device: HeartRateDevice) : HeartRateMonitorState {
        override fun toString(): String = "Lost"
    }
}

/**
 * Espera entre tentativas de reconexão: `delaysMillis[n]` antes da tentativa n+1 (a última se repete).
 * Falhas CONSECUTIVAS — um link que chegou a mandar FC zera a conta.
 */
data class HeartRateReconnectPolicy(
    val delaysMillis: List<Long> = listOf(1_000, 2_000, 5_000, 10_000),
    val maxConsecutiveFailures: Int = 30,
) {
    init {
        require(delaysMillis.isNotEmpty() && delaysMillis.all { it >= 0 }) { "delaysMillis vazio ou negativo" }
        require(maxConsecutiveFailures >= 0) { "maxConsecutiveFailures negativo" }
    }

    fun delayBefore(attempt: Int): Long = delaysMillis[(attempt - 1).coerceIn(0, delaysMillis.lastIndex)]
}

/**
 * FC ao vivo por Bluetooth LE, perfil padrão `0x180D` (RF-REL-03). Fluxos FRIOS: coletar liga o rádio,
 * cancelar a coleta desliga (BLE desligado ao fim do treino, H6). Um sensor por vez.
 *
 * ```kotlin
 * val monitor = createHeartRateMonitor()
 * val sensor = monitor.scan().first()               // ou a lista para o aluno escolher
 * monitor.heartRate(sensor).collect { bpm -> … }    // reconecta sozinho; cancelar desconecta
 * ```
 *
 * Nenhum valor de FC passa por log. Permissões e textos de uso são do APP (ver
 * `kmplib-catalog/references/health.md`).
 */
interface HeartRateMonitor {
    val state: StateFlow<HeartRateMonitorState>

    /** Sensores anunciando `0x180D`, cada um uma vez. Sem Bluetooth/permissão, termina sem emitir e
     * deixa [state] em [HeartRateMonitorState.Unavailable]. */
    fun scan(): Flow<HeartRateDevice>

    /** Cada notificação válida do sensor, já interpretada (inclui RR e contato). */
    fun measurements(device: HeartRateDevice): Flow<HeartRateMeasurement>

    /** Só os bpm utilizáveis ([HeartRateMeasurement.isUsable]). */
    fun heartRate(device: HeartRateDevice): Flow<Int> =
        measurements(device).filter { it.isUsable }.map { it.bpm }

    /**
     * Status da permissão de Bluetooth, SEM pedir nada — para a tela decidir entre a explicação
     * ([BluetoothPermissionStatus.NOT_REQUESTED]), o "Permitir" ([BluetoothPermissionStatus.DENIED]) e o
     * "Abrir Ajustes" ([BluetoothPermissionStatus.PERMANENTLY_DENIED]).
     *
     * - Android 12+: `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` (as duas já no manifesto da lib); Android 11 e
     *   anteriores: `ACCESS_FINE_LOCATION` (o scan BLE exige; o APP declara — ver o manifesto da lib).
     * - iOS: `CBManager.authorization` (não cria o `CBCentralManager`, então não abre diálogo).
     *
     * O default (`GRANTED`) existe só para não quebrar dublês mantidos por apps; as implementações da lib
     * sobrescrevem.
     */
    suspend fun permissionStatus(): BluetoothPermissionStatus = BluetoothPermissionStatus.GRANTED

    /**
     * Pede a permissão de Bluetooth pela via oficial de cada plataforma e devolve o status final. Já
     * concedida ou negada de vez: devolve na hora, sem diálogo (negada de vez = levar aos Ajustes).
     *
     * - Android: `ActivityResultRegistry` + `RequestMultiplePermissions` na Activity registrada em
     *   `HealthActivityHolder.setActivity` (sem Activity, devolve o status atual sem abrir nada).
     * - iOS: o diálogo nasce ao criar o `CBCentralManager` — é isso que a lib faz, e espera a resposta.
     *   Exige `NSBluetoothAlwaysUsageDescription` no `Info.plist` (sem ela o iOS ENCERRA o app).
     *
     * Chamar antes de [scan] é o caminho recomendado: o [scan] sem permissão termina vazio com
     * [HeartRateUnavailableReason.PERMISSION_DENIED] (e, no iOS, dispara o diálogo por conta própria).
     */
    suspend fun requestPermission(): BluetoothPermissionStatus = permissionStatus()
}

expect fun createHeartRateMonitor(): HeartRateMonitor

/** O que o transporte BLE da plataforma entrega na conexão. */
internal sealed interface BleLinkEvent {
    /** Notificação da `0x2A37` assinada — o link está pronto. */
    data object Subscribed : BleLinkEvent

    class Notification(val bytes: ByteArray) : BleLinkEvent
}

/** O transporte não pode operar (Bluetooth desligado, sem permissão...). */
internal class HeartRateUnavailableException(val reason: HeartRateUnavailableReason) : Exception(reason.name)

/**
 * Porta do rádio: Android (`BluetoothGatt`) e iOS (CoreBluetooth). Sem regra — reconexão, parsing e
 * estado ficam em [DefaultHeartRateMonitor].
 */
internal interface BleHeartRateTransport {
    /** `null` = pronto para operar. */
    suspend fun unavailableReason(): HeartRateUnavailableReason?

    /** Ver [HeartRateMonitor.permissionStatus]. */
    suspend fun permissionStatus(): BluetoothPermissionStatus

    /** Ver [HeartRateMonitor.requestPermission]. */
    suspend fun requestPermission(): BluetoothPermissionStatus

    fun scan(): Flow<HeartRateDevice>

    /** Conecta, assina a `0x2A37` e emite [BleLinkEvent]. Termina (normalmente ou com exceção) quando o
     * link cai; [HeartRateUnavailableException] quando não há como tentar de novo. Cancelar desconecta. */
    fun connect(device: HeartRateDevice): Flow<BleLinkEvent>
}

internal class DefaultHeartRateMonitor(
    private val transport: BleHeartRateTransport,
    private val policy: HeartRateReconnectPolicy = HeartRateReconnectPolicy(),
) : HeartRateMonitor {

    private val _state = MutableStateFlow<HeartRateMonitorState>(HeartRateMonitorState.Idle)
    override val state: StateFlow<HeartRateMonitorState> = _state.asStateFlow()

    override suspend fun permissionStatus(): BluetoothPermissionStatus = transport.permissionStatus()

    /** Concedida agora: um `Unavailable(PERMISSION_DENIED)` deixado por um scan anterior volta a `Idle`
     * — a tela não fica presa no aviso de permissão depois de a pessoa permitir. */
    override suspend fun requestPermission(): BluetoothPermissionStatus {
        val status = transport.requestPermission()
        if (status == BluetoothPermissionStatus.GRANTED &&
            _state.value == HeartRateMonitorState.Unavailable(HeartRateUnavailableReason.PERMISSION_DENIED)
        ) {
            _state.value = HeartRateMonitorState.Idle
        }
        return status
    }

    override fun scan(): Flow<HeartRateDevice> = flow {
        val reason = transport.unavailableReason()
        if (reason != null) {
            _state.value = HeartRateMonitorState.Unavailable(reason)
            return@flow
        }
        _state.value = HeartRateMonitorState.Scanning
        val seen = HashSet<String>()
        // `catch` só pega o que vem do RÁDIO (upstream): exceção do coletor sobe intacta
        // (transparência de exceção do Flow).
        var failure: Throwable? = null
        transport.scan()
            .catch { failure = it }
            .collect { device -> if (seen.add(device.id)) emit(device) }
        val stopped = failure
        if (stopped is HeartRateUnavailableException) _state.value = HeartRateMonitorState.Unavailable(stopped.reason)
        // Outra falha do scan da plataforma (Android `onScanFailed`): o fluxo termina sem erro e o
        // estado volta a `Idle` — chamar `scan()` de novo é a nova tentativa.
    }.onCompletion { if (_state.value == HeartRateMonitorState.Scanning) _state.value = HeartRateMonitorState.Idle }

    override fun measurements(device: HeartRateDevice): Flow<HeartRateMeasurement> = flow {
        var failures = 0
        while (true) {
            val reason = transport.unavailableReason()
            if (reason != null) {
                _state.value = HeartRateMonitorState.Unavailable(reason)
                return@flow
            }
            _state.value = HeartRateMonitorState.Connecting(device, failures)
            var receivedData = false
            // Só a falha do LINK (upstream) leva à reconexão; exceção do coletor sobe intacta.
            var linkFailure: Throwable? = null
            transport.connect(device)
                .catch { linkFailure = it }
                .collect { event ->
                    when (event) {
                        BleLinkEvent.Subscribed -> {
                            val last = (_state.value as? HeartRateMonitorState.Connected)?.lastBpm
                            _state.value = HeartRateMonitorState.Connected(device, last)
                        }
                        is BleLinkEvent.Notification -> {
                            val measurement = HeartRateMeasurementParser.parse(event.bytes) ?: return@collect
                            receivedData = true
                            failures = 0
                            val bpm = if (measurement.isUsable) measurement.bpm else null
                            val previous = (_state.value as? HeartRateMonitorState.Connected)?.lastBpm
                            _state.value = HeartRateMonitorState.Connected(device, bpm ?: previous)
                            emit(measurement)
                        }
                    }
                }
            val failure = linkFailure
            if (failure is HeartRateUnavailableException) {
                _state.value = HeartRateMonitorState.Unavailable(failure.reason)
                return@flow
            }
            // Link caiu (fora de alcance, cinta desligada, erro de GATT) ou terminou: reconecta.
            if (!receivedData) failures++
            if (failures > policy.maxConsecutiveFailures) {
                _state.value = HeartRateMonitorState.Lost(device)
                return@flow
            }
            _state.value = HeartRateMonitorState.Connecting(device, failures.coerceAtLeast(1))
            delay(policy.delayBefore(failures.coerceAtLeast(1)))
        }
    }.onCompletion { cause ->
        val terminal = _state.value is HeartRateMonitorState.Unavailable || _state.value is HeartRateMonitorState.Lost
        if (cause != null || !terminal) _state.value = HeartRateMonitorState.Idle
    }
}
