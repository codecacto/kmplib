@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package br.com.codecacto.kmplib.health.heartrate

import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBManager
import platform.CoreBluetooth.CBManagerAuthorization
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerAuthorizationDenied
import platform.CoreBluetooth.CBManagerAuthorizationNotDetermined
import platform.CoreBluetooth.CBManagerAuthorizationRestricted
import platform.CoreBluetooth.CBManagerStatePoweredOff
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateResetting
import platform.CoreBluetooth.CBManagerStateUnauthorized
import platform.CoreBluetooth.CBManagerStateUnknown
import platform.CoreBluetooth.CBManagerStateUnsupported
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.NSUUID
import platform.darwin.NSObject
import platform.posix.memcpy

private val SERVICE: CBUUID = CBUUID.UUIDWithString(HeartRateGattProfile.SERVICE_SHORT)
private val MEASUREMENT: CBUUID = CBUUID.UUIDWithString(HeartRateGattProfile.MEASUREMENT_SHORT)

/** Teto para o `CBCentralManager` sair de `Unknown` depois de criado (normalmente < 100 ms; na 1ª
 * vez é o tempo do diálogo de permissão do Bluetooth). */
private const val STATE_TIMEOUT_MS = 30_000L

private class BleLinkLostException : Exception("ble link lost")

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, this.length) }
    return bytes
}

/** `null` = `PoweredOn` (pronto). `Unknown`/`Resetting` que não saíram a tempo contam como desligado. */
private fun unavailableReasonFor(state: Long): HeartRateUnavailableReason? = when (state) {
    CBManagerStatePoweredOn -> null
    CBManagerStatePoweredOff -> HeartRateUnavailableReason.BLUETOOTH_OFF
    CBManagerStateUnauthorized -> HeartRateUnavailableReason.PERMISSION_DENIED
    CBManagerStateUnsupported -> HeartRateUnavailableReason.NOT_SUPPORTED
    else -> HeartRateUnavailableReason.BLUETOOTH_OFF
}

/** `CBManager.authorization` (iOS 13.1+) → o vocabulário da lib. No iOS negar é definitivo: o caminho de
 * volta são os Ajustes. `restricted` (controle parental/MDM) também — a pessoa não tem como permitir. */
private fun bluetoothPermissionStatusFor(authorization: CBManagerAuthorization): BluetoothPermissionStatus =
    when (authorization) {
        CBManagerAuthorizationAllowedAlways -> BluetoothPermissionStatus.GRANTED
        CBManagerAuthorizationDenied, CBManagerAuthorizationRestricted -> BluetoothPermissionStatus.PERMANENTLY_DENIED
        CBManagerAuthorizationNotDetermined -> BluetoothPermissionStatus.NOT_REQUESTED
        else -> BluetoothPermissionStatus.NOT_REQUESTED
    }

/** Intervalo da reconferência de `CBManager.authorization` enquanto o diálogo do sistema está aberto. */
private const val AUTHORIZATION_POLL_MS = 250L

/** Ganchos de uma conexão em andamento, por `identifier` do periférico. */
private class LinkHandlers(val onConnected: () -> Unit, val onLost: () -> Unit)

/** Delegado do `CBCentralManager` — um só para a lib inteira; roteia por periférico. Main queue. */
private class CentralDelegate : NSObject(), CBCentralManagerDelegateProtocol {
    val managerState = MutableStateFlow(CBManagerStateUnknown)
    var onDiscover: ((CBPeripheral) -> Unit)? = null

    /** Scan em andamento interrompido porque o rádio saiu de `PoweredOn` (Bluetooth desligado,
     * permissão revogada nos Ajustes) — sem isto o fluxo de scan ficaria aberto sem nunca emitir. */
    var onScanInterrupted: ((HeartRateUnavailableReason) -> Unit)? = null
    val links = mutableMapOf<String, LinkHandlers>()

    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        managerState.value = central.state
        if (central.state != CBManagerStatePoweredOn) {
            links.values.toList().forEach { it.onLost() }
            unavailableReasonFor(central.state)?.let { reason -> onScanInterrupted?.invoke(reason) }
        }
    }

    override fun centralManager(
        central: CBCentralManager,
        didDiscoverPeripheral: CBPeripheral,
        advertisementData: Map<Any?, *>,
        RSSI: NSNumber,
    ) {
        onDiscover?.invoke(didDiscoverPeripheral)
    }

    override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
        links[didConnectPeripheral.identifier.UUIDString]?.onConnected?.invoke()
    }

    @ObjCSignatureOverride
    override fun centralManager(central: CBCentralManager, didFailToConnectPeripheral: CBPeripheral, error: NSError?) {
        links[didFailToConnectPeripheral.identifier.UUIDString]?.onLost?.invoke()
    }

    @ObjCSignatureOverride
    override fun centralManager(central: CBCentralManager, didDisconnectPeripheral: CBPeripheral, error: NSError?) {
        links[didDisconnectPeripheral.identifier.UUIDString]?.onLost?.invoke()
    }
}

/** Delegado de UM periférico: descobre `0x180D` -> `0x2A37`, assina e repassa as notificações. */
private class PeripheralDelegate(
    private val onSubscribed: () -> Unit,
    private val onValue: (ByteArray) -> Unit,
    private val onFailure: (Throwable) -> Unit,
) : NSObject(), CBPeripheralDelegateProtocol {

    override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) {
        if (didDiscoverServices != null) return onFailure(BleLinkLostException())
        val service = peripheral.services.orEmpty().filterIsInstance<CBService>().firstOrNull { it.UUID == SERVICE }
            ?: return onFailure(HeartRateUnavailableException(HeartRateUnavailableReason.NOT_A_HEART_RATE_SENSOR))
        peripheral.discoverCharacteristics(listOf(MEASUREMENT), service)
    }

    @ObjCSignatureOverride
    override fun peripheral(peripheral: CBPeripheral, didDiscoverCharacteristicsForService: CBService, error: NSError?) {
        if (error != null) return onFailure(BleLinkLostException())
        val characteristic = didDiscoverCharacteristicsForService.characteristics.orEmpty()
            .filterIsInstance<CBCharacteristic>().firstOrNull { it.UUID == MEASUREMENT }
            ?: return onFailure(HeartRateUnavailableException(HeartRateUnavailableReason.NOT_A_HEART_RATE_SENSOR))
        peripheral.setNotifyValue(true, characteristic)
    }

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didUpdateNotificationStateForCharacteristic: CBCharacteristic,
        error: NSError?,
    ) {
        if (didUpdateNotificationStateForCharacteristic.UUID != MEASUREMENT) return
        when {
            error != null -> onFailure(BleLinkLostException())
            didUpdateNotificationStateForCharacteristic.isNotifying -> onSubscribed()
        }
    }

    @ObjCSignatureOverride
    override fun peripheral(peripheral: CBPeripheral, didUpdateValueForCharacteristic: CBCharacteristic, error: NSError?) {
        if (error != null || didUpdateValueForCharacteristic.UUID != MEASUREMENT) return
        val value = didUpdateValueForCharacteristic.value ?: return
        onValue(value.toByteArray())
    }
}

/**
 * Transporte iOS pelo **CoreBluetooth** (central BLE), por cinterop, sem Swift. Toda chamada ao
 * `CBCentralManager` roda na main queue (a fila em que ele foi criado, `queue = nil`) — por isso os
 * fluxos usam `flowOn(Dispatchers.Main)`.
 *
 * O app precisa no `Info.plist`: `NSBluetoothAlwaysUsageDescription` (sem ela o app é ENCERRADO ao
 * tocar no CoreBluetooth) e, para seguir recebendo FC com a tela bloqueada durante o treino,
 * `UIBackgroundModes` = `bluetooth-central`. O diálogo de permissão aparece na 1ª vez que o monitor é
 * usado (é a criação do `CBCentralManager` que o dispara) — ou, no momento que a tela escolher, em
 * `HeartRateMonitor.requestPermission()`.
 */
internal class IosBleHeartRateTransport : BleHeartRateTransport {

    private val delegate = CentralDelegate()
    private val central: CBCentralManager by lazy { CBCentralManager(delegate, null) }

    /** Periféricos vistos no scan — o `CBPeripheral` tem de ser o mesmo objeto para conectar. */
    private val discovered = mutableMapOf<String, CBPeripheral>()

    override suspend fun unavailableReason(): HeartRateUnavailableReason? = withContext(Dispatchers.Main) {
        when (CBManager.authorization) {
            CBManagerAuthorizationDenied, CBManagerAuthorizationRestricted ->
                return@withContext HeartRateUnavailableReason.PERMISSION_DENIED
        }
        central // cria o gerenciador (e, na 1ª vez, o diálogo de permissão)
        val state = withTimeoutOrNull(STATE_TIMEOUT_MS) {
            delegate.managerState.first { it != CBManagerStateUnknown && it != CBManagerStateResetting }
        } ?: delegate.managerState.value
        unavailableReasonFor(state)
    }

    override suspend fun permissionStatus(): BluetoothPermissionStatus = withContext(Dispatchers.Main) {
        bluetoothPermissionStatusFor(CBManager.authorization)
    }

    /**
     * A via oficial da Apple: não há chamada "pedir permissão" no CoreBluetooth — o diálogo nasce quando
     * o app cria o 1º `CBCentralManager`. A lib cria o gerenciador do monitor (o mesmo que o scan usa) e
     * espera a resposta: o `centralManagerDidUpdateState` chega quando a pessoa responde; se o estado se
     * resolver antes da autorização, reconfere a cada 250 ms até ela sair de `notDetermined`. Sem teto —
     * a pessoa pode demorar no diálogo —, e cancelável pelo escopo de quem chamou.
     */
    override suspend fun requestPermission(): BluetoothPermissionStatus = withContext(Dispatchers.Main) {
        val current = bluetoothPermissionStatusFor(CBManager.authorization)
        if (current != BluetoothPermissionStatus.NOT_REQUESTED) return@withContext current
        central // cria o gerenciador: é ISTO que abre o diálogo do sistema
        delegate.managerState.first { it != CBManagerStateUnknown && it != CBManagerStateResetting }
        while (CBManager.authorization == CBManagerAuthorizationNotDetermined) delay(AUTHORIZATION_POLL_MS)
        bluetoothPermissionStatusFor(CBManager.authorization)
    }

    override fun scan(): Flow<HeartRateDevice> = callbackFlow {
        delegate.onDiscover = { peripheral ->
            val id = peripheral.identifier.UUIDString
            discovered[id] = peripheral
            trySend(HeartRateDevice(id, peripheral.name))
        }
        delegate.onScanInterrupted = { reason -> close(HeartRateUnavailableException(reason)) }
        central.scanForPeripheralsWithServices(listOf(SERVICE), null)
        awaitClose {
            central.stopScan()
            delegate.onDiscover = null
            delegate.onScanInterrupted = null
        }
    }.flowOn(Dispatchers.Main)

    override fun connect(device: HeartRateDevice): Flow<BleLinkEvent> = callbackFlow {
        val peripheral = discovered[device.id]
            ?: central.retrievePeripheralsWithIdentifiers(listOf(NSUUID(uUIDString = device.id)))
                .filterIsInstance<CBPeripheral>().firstOrNull()
            ?: throw HeartRateUnavailableException(HeartRateUnavailableReason.NOT_A_HEART_RATE_SENSOR)

        val peripheralDelegate = PeripheralDelegate(
            onSubscribed = { trySend(BleLinkEvent.Subscribed) },
            onValue = { trySend(BleLinkEvent.Notification(it)) },
            onFailure = { close(it) },
        )
        // `CBPeripheral.delegate` é referência FRACA: `peripheralDelegate` fica vivo pela captura no
        // `awaitClose` abaixo enquanto o fluxo existir.
        peripheral.delegate = peripheralDelegate
        delegate.links[device.id] = LinkHandlers(
            onConnected = { peripheral.discoverServices(listOf(SERVICE)) },
            onLost = { close(BleLinkLostException()) },
        )
        central.connectPeripheral(peripheral, null)

        awaitClose {
            delegate.links.remove(device.id)
            central.cancelPeripheralConnection(peripheral)
            if (peripheral.delegate === peripheralDelegate) peripheral.delegate = null
        }
    }.flowOn(Dispatchers.Main)
}

actual fun createHeartRateMonitor(): HeartRateMonitor = DefaultHeartRateMonitor(IosBleHeartRateTransport())
