package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile

/**
 * Ponto **único** de registro do que o app faz quando o usuário toca num botão de notificação.
 *
 * ### Onde registrar: no `Application`, nunca numa `Activity`
 * Uma ação de notificação chega com o app em **background ou completamente morto**. No Android o
 * broadcast sobe o processo, roda `Application.onCreate()` e só então entrega o evento; se o handler
 * estivesse amarrado a uma tela, ele **não existiria** justamente na hora em que é preciso — e a
 * ação do usuário se perderia sem erro nenhum.
 *
 * ```kotlin
 * class MyApplication : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         KmpLib.init(this)
 *         startKoin { ... }
 *         NotificationActions.setHandler { event ->
 *             when (event.actionId) {
 *                 "MARK_TAKEN" -> doseRepository.marcarComoTomada(event.data["doseId"].orEmpty())
 *                 else -> AppLogger.w("App", "ação desconhecida: ${event.actionId}")
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * No iOS o registro é o mesmo, feito no bootstrap compartilhado (o `initKoin()`/`AppBootstrap` que
 * o `AppDelegate` chama) — a API é comum de propósito.
 *
 * ### Evento que chega antes do handler não se perde
 * Se o app ainda não registrou o handler (bootstrap assíncrono, Koin subindo), o evento fica numa
 * fila de até [MAX_PENDING_EVENTS] e é entregue assim que [setHandler] for chamado. **Não é
 * persistente**: vale dentro do processo. Se o processo morrer antes do registro, o evento se perde
 * — daí a regra de registrar no `Application`, cedo, e não depois de um `await` qualquer.
 *
 * ### Ações de adiar não passam por aqui
 * [NotificationActionKind.SNOOZE] é executado dentro da lib (reagenda o mesmo id). O handler só vê
 * ações de domínio ([NotificationActionKind.APP]).
 */
object NotificationActions {

    private const val TAG = "NotificationActions"

    /** Teto da fila de eventos ainda não entregues. Além disso, os **mais antigos** são descartados. */
    const val MAX_PENDING_EVENTS: Int = 32

    /**
     * Corte de tempo do handler: **8 s**.
     *
     * O Android concede ~10 s a um `BroadcastReceiver` (mesmo com `goAsync()`) antes de considerar o
     * app travado; ficamos abaixo disso para encerrar o broadcast por conta própria, com log, em vez
     * de deixar o sistema matá-lo. Gravar um registro local leva milissegundos — quem estoura esse
     * tempo está tentando falar com a rede dentro da ação, que é trabalho do sync depois.
     */
    const val HANDLER_TIMEOUT_MILLIS: Long = 8_000L

    @Volatile
    private var handler: NotificationActionHandler? = null

    private val mutex = Mutex()
    private val pending = ArrayDeque<NotificationActionEvent>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** `true` quando há handler registrado — útil para logar no bootstrap ("falhe alto"). */
    val hasHandler: Boolean get() = handler != null

    /**
     * Registra (ou substitui) o handler das ações de notificação e **entrega o que estava na fila**.
     *
     * Idempotente do ponto de vista do app: chamar duas vezes só troca o handler.
     */
    fun setHandler(handler: NotificationActionHandler?) {
        this.handler = handler
        if (handler != null) scope.launch { drainPending() }
    }

    /** Remove o handler (o app deixa de tratar ações; a lib volta a enfileirar). */
    fun clearHandler() = setHandler(null)

    /** Eventos ainda não entregues (diagnóstico/teste). */
    suspend fun pendingEvents(): List<NotificationActionEvent> = mutex.withLock { pending.toList() }

    /** Esvazia a fila sem entregar nada (teste). */
    suspend fun reset() {
        handler = null
        mutex.withLock { pending.clear() }
    }

    /**
     * Entrega um evento ao handler — chamado pelas camadas de plataforma da lib (o
     * `NotificationActionReceiver` no Android, o delegate/bridge no iOS).
     *
     * Devolve `true` se o handler tratou o evento; `false` se ele foi enfileirado por não haver
     * handler (ou se o handler estourou o tempo/lançou). **Nunca lança** — uma exceção aqui, dentro
     * de um receiver, derrubaria o processo do app.
     */
    suspend fun dispatch(event: NotificationActionEvent): Boolean {
        val current = handler
        if (current == null) {
            enqueue(event)
            AppLogger.w(
                TAG,
                "Ação '${event.actionId}' (id=${event.notificationId}) chegou sem handler registrado — " +
                    "enfileirada. Registre NotificationActions.setHandler(...) no Application.",
            )
            return false
        }
        return deliver(current, event)
    }

    private suspend fun deliver(
        target: NotificationActionHandler,
        event: NotificationActionEvent,
    ): Boolean = try {
        val finished = withTimeoutOrNull(HANDLER_TIMEOUT_MILLIS) {
            target.onNotificationAction(event)
            true
        }
        if (finished == null) {
            AppLogger.e(
                TAG,
                "Handler da ação '${event.actionId}' passou de ${HANDLER_TIMEOUT_MILLIS}ms e foi cortado",
            )
            false
        } else {
            true
        }
    } catch (e: Exception) {
        // A ação do usuário já aconteceu; falhar aqui não pode custar o processo do app.
        AppLogger.e(TAG, "Handler da ação '${event.actionId}' lançou", e)
        false
    }

    private suspend fun enqueue(event: NotificationActionEvent) = mutex.withLock {
        while (pending.size >= MAX_PENDING_EVENTS) pending.removeFirst()
        pending.addLast(event)
    }

    private suspend fun drainPending() {
        while (true) {
            val current = handler ?: return
            val next = mutex.withLock { pending.removeFirstOrNull() } ?: return
            deliver(current, next)
        }
    }
}
