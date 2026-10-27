package br.com.codecacto.kmplib.platform

import kotlin.concurrent.AtomicInt
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_t
import platform.Network.nw_path_status_satisfied
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.darwin.dispatch_time

/**
 * iOS: resposta síncrona sobre o `NWPathMonitor` (Network.framework), a API que a Apple indica.
 *
 * **2.329.0 (correção):** até a 2.328.0 cada chamada fazia `SCNetworkReachabilityCreateWithName`
 * ("www.google.com") + `SCNetworkReachabilityGetFlags` — resolução de DNS SÍNCRONA na thread de
 * quem chama (o consumidor chama de dentro do `viewModelScope`, que no iOS é a principal: com rede
 * ruim a tela travava e o watchdog podia matar o app — Apple QA1693), um objeto CF vazado por
 * chamada e uma API depreciada no iOS 17.4. A assinatura não muda.
 *
 * Agora há UM monitor por processo, numa fila própria, iniciado na primeira chamada; a resposta é
 * o último caminho empurrado. Só a primeira chamada espera o primeiro empurrão (no máximo
 * [FIRST_UPDATE_TIMEOUT_MS], normalmente milissegundos). Sem resposta ainda, devolve `true` — a
 * mesma regra do `ConnectivityObserver`: não se inventa "offline" sem saber; a requisição em si
 * diz se a rede falhou.
 */
class IosNetworkChecker : NetworkChecker {
    override fun isAvailable(): Boolean = networkAvailableFrom(IosPathMonitor.currentOrAwait())
}

private const val FIRST_UPDATE_TIMEOUT_MS = 500L

@OptIn(ExperimentalForeignApi::class)
private object IosPathMonitor {
    private const val UNKNOWN = -1
    private const val OFFLINE = 0
    private const val ONLINE = 1

    private val status = AtomicInt(UNKNOWN)
    private val firstUpdate = dispatch_semaphore_create(0)
    private val monitor: nw_path_monitor_t? by lazy {
        val created = nw_path_monitor_create() ?: return@lazy null
        val queue = dispatch_queue_create("br.com.codecacto.kmplib.networkchecker", null)
        nw_path_monitor_set_update_handler(created) { path ->
            val online = path != null && nw_path_get_status(path) == nw_path_status_satisfied
            val anterior = status.getAndSet(if (online) ONLINE else OFFLINE)
            if (anterior == UNKNOWN) dispatch_semaphore_signal(firstUpdate)
        }
        nw_path_monitor_set_queue(created, queue)
        nw_path_monitor_start(created)
        created
    }

    /** `true`/`false` pelo último caminho; `null` se o monitor ainda não respondeu. */
    fun currentOrAwait(): Boolean? {
        if (monitor == null) return null
        if (status.value == UNKNOWN) {
            // O handler roda na fila própria do monitor, nunca na de quem chama: sem deadlock.
            dispatch_semaphore_wait(firstUpdate, dispatch_time(DISPATCH_TIME_NOW, FIRST_UPDATE_TIMEOUT_MS * 1_000_000L))
        }
        return when (status.value) {
            ONLINE -> true
            OFFLINE -> false
            else -> null
        }
    }
}
