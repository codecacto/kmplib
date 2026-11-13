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

    private class Entry(
        val listener: PushNotificationListener,
        val handles: List<PushListenerRegistration>,
        val receivesTaps: Boolean,
        val receivesCampaignTaps: Boolean,
    ) {
        fun accepts(campaign: Boolean): Boolean = if (campaign) receivesCampaignTaps else receivesTaps
    }

    private class State(val entries: List<Entry>, val pendingTap: Map<String, String>?)

    private val state = AtomicReference(State(emptyList(), null))

    /**
     * Chamado UMA vez para todo toque que chega ao hub, antes de entregá-lo ou guardá-lo como
     * pendente (2.362.0 — é por aqui que o [br.com.codecacto.kmplib.push.central.PushOpenings] conta a
     * abertura, inclusive do toque a frio). Falha do observador nunca impede o toque.
     */
    @kotlin.concurrent.Volatile
    internal var tapObserver: ((Map<String, String>) -> Unit)? = null

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
     *
     * @param receiveTaps `false` (2.360.0) = ouvinte só de token/recebimento (ex.: o registro central
     *   do aparelho, `PushRegistration`): não recebe [PushNotificationListener.onNotificationClicked]
     *   e NÃO conta como "alguém para o toque" — sem nenhum ouvinte de toque, o toque continua
     *   ficando pendente para a tela que montar depois. Desde a 2.363.0 vale só para o toque que
     *   NÃO é de campanha (o push do próprio app); o de campanha é [receiveCampaignTaps].
     * @param receiveCampaignTaps (2.363.0) recebe o toque de **campanha do Nexus** (carga com
     *   `cc_acao`). Default `false`: só o [br.com.codecacto.kmplib.push.ui.PushTapHost] (que liga) o
     *   recebe, e o ouvinte do push operacional do app não "rouba" mais o toque que abriu o app a frio
     *   — sem ouvinte de campanha, ele fica pendente até o host ligar. Antes da 2.363.0 todo ouvinte
     *   com `receiveTaps = true` recebia também o de campanha (a armadilha pegou 5 apps).
     *
     * Sem nenhum dos dois, [receivePendingTap] é ignorado; com algum, o pendente só é entregue se for
     * do tipo que o ouvinte recebe.
     */
    fun addListener(
        listener: PushNotificationListener,
        receivePendingTap: Boolean = true,
        receiveTaps: Boolean = true,
        receiveCampaignTaps: Boolean = false,
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
                atual.entries.map {
                    if (it === existente) {
                        Entry(
                            listener,
                            it.handles + handle,
                            it.receivesTaps || receiveTaps,
                            it.receivesCampaignTaps || receiveCampaignTaps,
                        )
                    } else {
                        it
                    }
                }
            } else {
                atual.entries + Entry(listener, listOf(handle), receiveTaps, receiveCampaignTaps)
            }
            val pendente = atual.pendingTap
            delivered = if (
                receivePendingTap && pendente != null &&
                (if (pendente.isCampaignTap()) receiveCampaignTaps else receiveTaps)
            ) {
                pendente
            } else {
                null
            }
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
                    if (resto.isEmpty()) null else Entry(entry.listener, resto, entry.receivesTaps, entry.receivesCampaignTaps)
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

    /**
     * Sem ouvinte para ESTE tipo de toque, o toque fica pendente (substitui o anterior); com ouvinte,
     * vai a todos os que o recebem — o de campanha (`cc_acao`) a quem registrou com
     * `receiveCampaignTaps = true`; o do app a quem registrou com `receiveTaps = true` (2.363.0).
     */
    override fun onNotificationClicked(data: Map<String, String>) {
        tapObserver?.let { observer -> runCatching { observer(data) } }
        val campaign = data.isCampaignTap()
        while (true) {
            val atual = state.load()
            val tapReceivers = atual.entries.filter { it.accepts(campaign) }
            if (tapReceivers.isNotEmpty()) {
                tapReceivers.forEach { it.listener.onNotificationClicked(data) }
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
    internal val hub = PushListenerHub().apply {
        // 2.362.0: toda abertura de campanha do Nexus é contada (cc_campanha) — sem bloquear o toque.
        tapObserver = { data -> br.com.codecacto.kmplib.push.central.PushOpenings.onTap(data) }
    }

    /** Ver [PushListenerHub.addListener]. */
    fun addListener(
        listener: PushNotificationListener,
        receivePendingTap: Boolean = true,
        receiveTaps: Boolean = true,
        receiveCampaignTaps: Boolean = false,
    ): PushListenerRegistration = hub.addListener(listener, receivePendingTap, receiveTaps, receiveCampaignTaps)

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

/** Toque de campanha do Nexus = carga com `cc_acao` (mesma régua do `PushActions.isCampaign`). */
private fun Map<String, String>.isCampaignTap(): Boolean =
    br.com.codecacto.kmplib.push.action.PushActions.isCampaign(this)
