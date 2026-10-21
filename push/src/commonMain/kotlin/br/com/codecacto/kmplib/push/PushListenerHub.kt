package br.com.codecacto.kmplib.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Handle de um ouvinte registrado num [PushListenerHub]. [remove] (ou `close()`) tira o ouvinte;
 * chamar de novo não faz nada.
 */
class PushListenerRegistration internal constructor(
    private val onRemove: (PushListenerRegistration) -> Unit,
) : AutoCloseable {
    private var removed = false

    /** `true` enquanto o ouvinte ainda recebe eventos por este handle. */
    val isActive: Boolean get() = !removed

    /** Tira o ouvinte. Idempotente. */
    fun remove() {
        if (removed) return
        removed = true
        onRemove(this)
    }

    override fun close() = remove()
}

/**
 * Distribuidor de eventos de push para **vários** [PushNotificationListener] (2.314.0).
 *
 * Resolve as duas lacunas que o RondAki contornava no app com um ouvinte único global:
 *
 * 1. **Vários ouvintes.** Cada serviço criado registra o seu e recebe um [PushListenerRegistration]
 *    para sair; ninguém sobrescreve ninguém. O MESMO objeto ouvinte registrado duas vezes recebe
 *    cada evento **uma vez só** (o segundo registro só soma uma referência — o ouvinte sai quando o
 *    último handle for removido). Antes, no iOS, o último serviço criado roubava o push do anterior.
 * 2. **Toque que chega sem ouvinte** (app aberto a frio pelo push: o sistema entrega o toque antes
 *    de o app montar o seu serviço). O toque fica **pendente** — só o último — até alguém consumir:
 *    o próximo [addListener] com `receivePendingTap = true` o recebe em
 *    [PushNotificationListener.onNotificationClicked], ou o app o retira com [consumePendingTap].
 *    É entregue **uma vez só**. Notificação recebida em foreground sem ouvinte NÃO fica guardada
 *    (não houve gesto do usuário a honrar).
 *
 * Thread-safe: estado imutável trocado por CAS; os ouvintes são chamados fora da troca, na thread
 * de quem disparou o evento.
 *
 * O hub do processo é [PushEvents]; esta classe é pública para teste e para quem precisar de um
 * distribuidor isolado.
 */
@OptIn(ExperimentalAtomicApi::class)
class PushListenerHub : PushNotificationListener {

    private class Entry(val listener: PushNotificationListener, val handles: List<PushListenerRegistration>)

    private class State(val entries: List<Entry>, val pendingTap: Map<String, String>?)

    private val state = AtomicReference(State(emptyList(), null))
    private val _pendingTap = MutableStateFlow<Map<String, String>?>(null)

    /**
     * O toque guardado à espera de quem o consuma (`null` = nenhum). Observável para a UI decidir
     * quando navegar; retire com [consumePendingTap].
     */
    val pendingTap: StateFlow<Map<String, String>?> = _pendingTap.asStateFlow()

    /** Quantos ouvintes DISTINTOS recebem eventos agora. */
    val listenerCount: Int get() = state.load().entries.size

    /**
     * Registra [listener]. Com `receivePendingTap = true` (default) e um toque pendente, o toque é
     * entregue a este ouvinte logo depois do registro — e deixa de estar pendente.
     *
     * [NoOpPushNotificationListener] não é registrado (e nunca consome o toque pendente).
     */
    fun addListener(
        listener: PushNotificationListener,
        receivePendingTap: Boolean = true,
    ): PushListenerRegistration {
        lateinit var handle: PushListenerRegistration
        handle = PushListenerRegistration { removeHandle(it) }
        if (listener === NoOpPushNotificationListener) {
            handle.remove()
            return handle
        }
        var delivered: Map<String, String>?
        while (true) {
            val atual = state.load()
            val existente = atual.entries.firstOrNull { it.listener === listener }
            val entries = if (existente != null) {
                atual.entries.map { if (it === existente) Entry(listener, it.handles + handle) else it }
            } else {
                atual.entries + Entry(listener, listOf(handle))
            }
            delivered = if (receivePendingTap) atual.pendingTap else null
            val novo = State(entries, if (delivered != null) null else atual.pendingTap)
            if (state.compareAndSet(atual, novo)) break
        }
        if (delivered != null) {
            _pendingTap.value = null
            listener.onNotificationClicked(delivered)
        }
        return handle
    }

    /** Tira [listener] por inteiro (todos os handles dele). */
    fun removeListener(listener: PushNotificationListener) {
        while (true) {
            val atual = state.load()
            val novo = State(atual.entries.filterNot { it.listener === listener }, atual.pendingTap)
            if (state.compareAndSet(atual, novo)) return
        }
    }

    private fun removeHandle(handle: PushListenerRegistration) {
        while (true) {
            val atual = state.load()
            val entries = atual.entries.mapNotNull { entry ->
                if (handle !in entry.handles) {
                    entry
                } else {
                    val resto = entry.handles.filterNot { it === handle }
                    if (resto.isEmpty()) null else Entry(entry.listener, resto)
                }
            }
            if (state.compareAndSet(atual, State(entries, atual.pendingTap))) return
        }
    }

    /** Retira o toque pendente (entregue uma vez só): `null` se não havia. */
    fun consumePendingTap(): Map<String, String>? {
        while (true) {
            val atual = state.load()
            val tap = atual.pendingTap ?: return null
            if (state.compareAndSet(atual, State(atual.entries, null))) {
                _pendingTap.value = null
                return tap
            }
        }
    }

    /** Descarta o toque pendente sem entregá-lo (ex.: logout — não vale para a próxima pessoa). */
    fun clearPendingTap() {
        consumePendingTap()
    }

    private fun listeners(): List<PushNotificationListener> = state.load().entries.map { it.listener }

    override fun onNewToken(token: String) {
        listeners().forEach { it.onNewToken(token) }
    }

    override fun onPushNotification(title: String?, body: String?) {
        listeners().forEach { it.onPushNotification(title, body) }
    }

    override fun onPayloadData(data: Map<String, String>) {
        listeners().forEach { it.onPayloadData(data) }
    }

    override fun onPushNotificationWithPayloadData(title: String?, body: String?, data: Map<String, String>) {
        listeners().forEach { it.onPushNotificationWithPayloadData(title, body, data) }
    }

    /** Sem ouvinte, o toque fica pendente (substitui o anterior); com ouvinte, vai a todos. */
    override fun onNotificationClicked(data: Map<String, String>) {
        while (true) {
            val atual = state.load()
            if (atual.entries.isNotEmpty()) {
                atual.entries.forEach { it.listener.onNotificationClicked(data) }
                return
            }
            if (state.compareAndSet(atual, State(atual.entries, data))) {
                _pendingTap.value = data
                return
            }
        }
    }
}

/**
 * O hub de push **do processo** (2.314.0) — onde o iOS ([ApplePushBridge]) e o Android
 * ([attachKmpNotifier], KMPNotifier) despejam os eventos, e onde cada serviço/tela registra o seu
 * ouvinte.
 *
 * ```kotlin
 * // Tela/objeto que quer o toque do push — inclusive o que ABRIU o app a frio:
 * val registro = PushEvents.addListener(meuOuvinte)   // recebe o toque pendente, se houver
 * // ...
 * registro.remove()
 *
 * // Ou, sem ouvinte: navegar quando a pilha estiver pronta
 * PushEvents.consumePendingTap()?.let { data -> navegarPara(data) }
 * ```
 *
 * `createPushNotificationService(listener)` já registra o `listener` aqui; `release()` do serviço
 * o tira.
 */
object PushEvents {
    internal val hub = PushListenerHub()

    /** Ver [PushListenerHub.addListener]. */
    fun addListener(
        listener: PushNotificationListener,
        receivePendingTap: Boolean = true,
    ): PushListenerRegistration = hub.addListener(listener, receivePendingTap)

    /** Ver [PushListenerHub.removeListener]. */
    fun removeListener(listener: PushNotificationListener) = hub.removeListener(listener)

    /** Ver [PushListenerHub.pendingTap]. */
    val pendingTap: StateFlow<Map<String, String>?> get() = hub.pendingTap

    /** Ver [PushListenerHub.consumePendingTap]. */
    fun consumePendingTap(): Map<String, String>? = hub.consumePendingTap()

    /** Ver [PushListenerHub.clearPendingTap]. */
    fun clearPendingTap() = hub.clearPendingTap()

    /** Ouvintes distintos registrados agora. */
    val listenerCount: Int get() = hub.listenerCount
}
