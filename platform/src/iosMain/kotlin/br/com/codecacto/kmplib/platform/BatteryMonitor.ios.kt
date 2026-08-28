package br.com.codecacto.kmplib.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryLevelDidChangeNotification
import platform.UIKit.UIDeviceBatteryStateDidChangeNotification
import platform.UIKit.UIDeviceBatteryState
import platform.darwin.NSObjectProtocol

/** Cria o monitor de bateria do iOS. */
actual fun createBatteryMonitor(): BatteryMonitor = IosBatteryMonitor()

/**
 * **Padrão-ouro do iOS: `UIDevice` com `isBatteryMonitoringEnabled`.**
 *
 * O monitoramento é uma flag **global do processo** e vem desligada — com ela desligada,
 * `batteryLevel` devolve `-1`. Por isso [release] a desliga de volta: deixá-la ligada mantém o
 * sistema publicando notificações que ninguém mais escuta.
 *
 * As mudanças chegam por `UIDeviceBatteryLevelDidChange` (a cada 1%) e
 * `UIDeviceBatteryStateDidChange` (entrou/saiu do carregador) — as duas notificações
 * documentadas, entregues na fila principal.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS):** o build Kotlin/Native iOS não roda no servidor Linux.
 */
internal class IosBatteryMonitor : BatteryMonitor {

    private val device = UIDevice.currentDevice
    private val _status = MutableStateFlow(BatteryStatus.UNAVAILABLE)
    override val status: StateFlow<BatteryStatus> = _status.asStateFlow()

    private var observers: List<NSObjectProtocol> = emptyList()
    private var released = false

    init {
        device.batteryMonitoringEnabled = true
        val center = NSNotificationCenter.defaultCenter
        observers = listOf(
            UIDeviceBatteryLevelDidChangeNotification,
            UIDeviceBatteryStateDidChangeNotification,
        ).map { name ->
            center.addObserverForName(
                name = name,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _ -> readNow() }
        }
        readNow()
    }

    override fun refresh() {
        if (released) return
        readNow()
    }

    override fun release() {
        if (released) return
        released = true
        val center = NSNotificationCenter.defaultCenter
        observers.forEach { center.removeObserver(it) }
        observers = emptyList()
        device.batteryMonitoringEnabled = false
    }

    private fun readNow() {
        val state = device.batteryState
        val charging = state == UIDeviceBatteryState.UIDeviceBatteryStateCharging ||
            state == UIDeviceBatteryState.UIDeviceBatteryStateFull
        _status.value = BatteryStatus.fromIosLevel(device.batteryLevel, isCharging = charging)
    }
}
