package br.com.codecacto.kmplib.core.network

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_t
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

@OptIn(ExperimentalForeignApi::class)
actual class ConnectivityObserver actual constructor() {
    private val _isOnline = MutableStateFlow(true)
    actual val isOnline: StateFlow<Boolean> = _isOnline

    private var monitor: nw_path_monitor_t? = null

    actual fun start() {
        val newMonitor = nw_path_monitor_create() ?: return
        monitor = newMonitor

        val queue = dispatch_queue_create("br.com.codecacto.kmplib.connectivity", null)
        nw_path_monitor_set_update_handler(newMonitor) { path ->
            _isOnline.value = path != null && nw_path_get_status(path) == nw_path_status_satisfied
        }
        nw_path_monitor_set_queue(newMonitor, queue)
        nw_path_monitor_start(newMonitor)
    }

    actual fun stop() {
        monitor?.let { nw_path_monitor_cancel(it) }
        monitor = null
    }

    /**
     * No-op no iOS: o `NWPathMonitor` empurra atualizações continuamente enquanto ativo,
     * então [isOnline] já reflete o estado corrente da rede sem releitura manual.
     */
    actual fun refresh() {
        // NWPathMonitor mantém isOnline sempre atualizado; nada a fazer.
    }
}
