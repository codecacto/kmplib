package br.com.codecacto.kmplib.core.network

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_constrained
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_t
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

/**
 * iOS: `NWPathMonitor` (API oficial de conectividade do Network.framework).
 *
 * **Idempotente por construção:** [start] com um monitor vivo é no-op (nunca cria dois monitores
 * nem duas filas); [stop] com o monitor já cancelado é no-op.
 *
 * [currentType] devolve o **último estado empurrado** pelo monitor (`null` antes do primeiro
 * update, ou quando o monitor nunca foi iniciado): o `NWPathMonitor` não expõe leitura síncrona
 * e a lib não inventa "offline" quando não sabe. Isso mantém o `refresh()` do
 * [ConnectivityObserver] inofensivo no iOS — o monitor já empurra o estado corrente.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual class PlatformConnectivityMonitor actual constructor() : NetworkTypeSource {

    private var monitor: nw_path_monitor_t? = null
    private var lastStatus: NetworkType? = null

    actual override fun start(onChange: (NetworkType) -> Unit) {
        if (monitor != null) return // já ativo: nunca cria um segundo monitor
        val newMonitor = nw_path_monitor_create() ?: return
        monitor = newMonitor

        val queue = dispatch_queue_create("br.com.codecacto.kmplib.connectivity", null)
        nw_path_monitor_set_update_handler(newMonitor) { path ->
            // Satisfeito + caro (celular/hotspot) ou restrito (Modo Dados Reduzidos) = tarifada (2.328.0).
            val tipo = classifyNetworkPath(
                satisfied = path != null && nw_path_get_status(path) == nw_path_status_satisfied,
                expensive = path != null && nw_path_is_expensive(path),
                constrained = path != null && nw_path_is_constrained(path),
            )
            lastStatus = tipo
            onChange(tipo)
        }
        nw_path_monitor_set_queue(newMonitor, queue)
        nw_path_monitor_start(newMonitor)
    }

    actual override fun stop() {
        val current = monitor ?: return // idempotente
        monitor = null
        lastStatus = null
        nw_path_monitor_cancel(current)
    }

    actual override fun currentType(): NetworkType? = lastStatus
}
