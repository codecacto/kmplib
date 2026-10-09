package br.com.codecacto.kmplib.health.heartrate

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import br.com.codecacto.kmplib.health.HealthContextHolder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

private val SERVICE: UUID = UUID.fromString(HeartRateGattProfile.SERVICE_UUID)
private val MEASUREMENT: UUID = UUID.fromString(HeartRateGattProfile.MEASUREMENT_UUID)
private val CLIENT_CONFIG: UUID = UUID.fromString(HeartRateGattProfile.CLIENT_CONFIG_UUID)

/** O link caiu (desconexão, falha de GATT). Mensagem sem dado do aparelho. */
private class BleLinkLostException(status: Int) : Exception("ble link lost ($status)")

/**
 * Transporte Android pela API oficial `android.bluetooth` (central BLE + GATT), sem biblioteca de
 * terceiros:
 * - scan filtrado pelo serviço `0x180D` (`ScanFilter`), `SCAN_MODE_LOW_LATENCY` só enquanto a tela de
 *   escolha coleta;
 * - `connectGatt(…, autoConnect = false, TRANSPORT_LE)` → `discoverServices` → `setCharacteristicNotification`
 *   + escrita do CCCD `0x2902` (`writeDescriptor(desc, value)` no Android 13+, a forma antiga abaixo);
 * - `onCharacteristicChanged` nas DUAS assinaturas (a de 3 parâmetros só existe no 13+, e no 12- só a
 *   antiga é chamada);
 * - cancelar a coleta faz `disconnect()` + `close()` — sem `close()` o Android segura o cliente GATT
 *   e, depois de ~7, recusa conexões novas até reiniciar o Bluetooth.
 *
 * Permissão é do app: Android 12+ `BLUETOOTH_SCAN` (com `neverForLocation`, já no manifesto da lib) e
 * `BLUETOOTH_CONNECT`; até o 11, `ACCESS_FINE_LOCATION` para o scan.
 */
internal class AndroidBleHeartRateTransport(private val context: Context) : BleHeartRateTransport {

    private val manager: BluetoothManager? = context.getSystemService(BluetoothManager::class.java)

    override suspend fun unavailableReason(): HeartRateUnavailableReason? {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return HeartRateUnavailableReason.NOT_SUPPORTED
        }
        val adapter = manager?.adapter ?: return HeartRateUnavailableReason.NOT_SUPPORTED
        if (!hasPermissions()) return HeartRateUnavailableReason.PERMISSION_DENIED
        if (!adapter.isEnabled) return HeartRateUnavailableReason.BLUETOOTH_OFF
        return null
    }

    private fun hasPermissions(): Boolean {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return needed.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    @SuppressLint("MissingPermission") // conferida em unavailableReason(); SecurityException tratada
    override fun scan(): Flow<HeartRateDevice> = callbackFlow {
        val scanner = manager?.adapter?.bluetoothLeScanner
            ?: throw HeartRateUnavailableException(HeartRateUnavailableReason.BLUETOOTH_OFF)
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(HeartRateDevice(result.device.address, result.scanRecord?.deviceName))
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                close(BleLinkLostException(errorCode))
            }
        }
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(filters, settings, callback)
        } catch (_: SecurityException) {
            throw HeartRateUnavailableException(HeartRateUnavailableReason.PERMISSION_DENIED)
        }
        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (_: SecurityException) {
                // permissão revogada com o scan aberto: o sistema já parou o scan.
            } catch (_: IllegalStateException) {
                // Bluetooth desligado no meio: idem.
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun connect(device: HeartRateDevice): Flow<BleLinkEvent> = callbackFlow {
        val adapter = manager?.adapter ?: throw HeartRateUnavailableException(HeartRateUnavailableReason.NOT_SUPPORTED)
        val remote: BluetoothDevice = try {
            adapter.getRemoteDevice(device.id)
        } catch (_: IllegalArgumentException) {
            throw HeartRateUnavailableException(HeartRateUnavailableReason.NOT_A_HEART_RATE_SENSOR)
        }

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    if (!gatt.discoverServices()) close(BleLinkLostException(-1))
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                    close(BleLinkLostException(status))
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val characteristic = gatt.getService(SERVICE)?.getCharacteristic(MEASUREMENT)
                if (status != BluetoothGatt.GATT_SUCCESS || characteristic == null) {
                    close(HeartRateUnavailableException(HeartRateUnavailableReason.NOT_A_HEART_RATE_SENSOR))
                    return
                }
                val descriptor = characteristic.getDescriptor(CLIENT_CONFIG)
                if (!gatt.setCharacteristicNotification(characteristic, true) || descriptor == null) {
                    close(BleLinkLostException(-2))
                    return
                }
                if (!writeNotificationDescriptor(gatt, descriptor)) close(BleLinkLostException(-3))
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (descriptor.uuid != CLIENT_CONFIG) return
                if (status == BluetoothGatt.GATT_SUCCESS) trySend(BleLinkEvent.Subscribed) else close(BleLinkLostException(status))
            }

            // Android 13+.
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                if (characteristic.uuid == MEASUREMENT) trySend(BleLinkEvent.Notification(value.copyOf()))
            }

            // Android 12 e anteriores (no 13+ o sistema chama só a de cima).
            @Deprecated("Substituída pela de 3 parâmetros no Android 13")
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
                val value = characteristic.value ?: return
                if (characteristic.uuid == MEASUREMENT) trySend(BleLinkEvent.Notification(value.copyOf()))
            }
        }

        val gatt = try {
            remote.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (_: SecurityException) {
            throw HeartRateUnavailableException(HeartRateUnavailableReason.PERMISSION_DENIED)
        } ?: throw BleLinkLostException(-4)

        awaitClose {
            try {
                gatt.disconnect()
            } catch (_: SecurityException) {
                // permissão revogada: o sistema já derrubou o link.
            }
            gatt.close()
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun writeNotificationDescriptor(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor): Boolean {
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = value
            gatt.writeDescriptor(descriptor)
        }
    }
}

actual fun createHeartRateMonitor(): HeartRateMonitor =
    DefaultHeartRateMonitor(AndroidBleHeartRateTransport(HealthContextHolder.requireContext()))
