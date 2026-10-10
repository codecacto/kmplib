@file:OptIn(ExperimentalAtomicApi::class)

package br.com.codecacto.kmplib.sync.direct

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.currentTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** De que rede a fila precisa para andar. */
enum class DirectUploadNetworkNeed {
    /** Qualquer conexão. */
    ANY,

    /** Só rede não tarifada (Wi-Fi) — algum envio na fila é "só no Wi-Fi". */
    UNMETERED,
}

/**
 * Quem acorda a fila para drenar. O default da plataforma ([createPlatformDirectUploadScheduler]):
 * - **Android: WorkManager** — trabalho único por fila e por rede (`kmplib-direct-upload-<nome>-any`
 *   e `…-unmetered`), com restrição de rede (`CONNECTED`/`UNMETERED`) e recuo do próprio WorkManager.
 *   Roda com o app fechado, sobrevive a reboot (o WorkManager reagenda) e respeita a economia de
 *   bateria do sistema. Sem serviço em primeiro plano: a execução de até 10 min do WorkManager basta,
 *   porque o progresso é gravado **por parte** — se o sistema interromper, perde-se no máximo a parte
 *   em voo, e a próxima execução continua dali.
 * - **iOS: no processo**, dentro de `beginBackgroundTask` (os segundos que o sistema concede ao sair
 *   do app); o upload em si segue na sessão de segundo plano do `URLSession`, e é ela que reabre o app
 *   para a etapa seguinte.
 */
interface DirectUploadScheduler {
    /**
     * Pede uma drenagem de [outbox] quando houver a rede [need].
     *
     * @param urgent `true` quando a pessoa acabou de agir (enfileirar, "tentar de novo"): passa na
     *   frente de um recuo em curso. `false` (abertura do app, reagendamento) não atropela o que já
     *   está agendado.
     */
    fun schedule(outbox: DirectUploadOutbox, need: DirectUploadNetworkNeed, urgent: Boolean)

    /** Cancela o que estiver agendado para [outbox] (não apaga nada da fila). */
    fun cancel(outbox: DirectUploadOutbox) {}
}

/** O agendador recomendado da plataforma (ver [DirectUploadScheduler]). */
expect fun createPlatformDirectUploadScheduler(): DirectUploadScheduler

/** `true` se a rede ativa é tarifada (dados móveis/hotspot); `null` se a plataforma não sabe dizer aqui. */
internal expect fun platformIsActiveNetworkMetered(): Boolean?

/**
 * Filas vivas no processo, por nome — é como o `Worker` do WorkManager (criado pelo sistema, sem
 * acesso ao Koin do app) acha a fila que deve drenar. A fila se registra ao ser construída.
 */
object DirectUploadOutboxRegistry {
    private val filas = AtomicReference<Map<String, DirectUploadOutbox>>(emptyMap())

    internal fun register(outbox: DirectUploadOutbox) {
        while (true) {
            val atual = filas.load()
            if (atual[outbox.name] != null && atual[outbox.name] !== outbox) {
                AppLogger.w(TAG, "fila '${outbox.name}' recriada no mesmo processo; a mais nova assume")
            }
            if (filas.compareAndSet(atual, atual + (outbox.name to outbox))) return
        }
    }

    /** A fila [name] deste processo, ou `null` se o app ainda não a construiu. */
    fun get(name: String): DirectUploadOutbox? = filas.load()[name]

    private const val TAG = "DirectUploadOutbox"
}

/**
 * [DirectUploadScheduler] **no processo**: um consumidor por fila, que drena a cada pedido (pedidos
 * seguidos se fundem) e se reagenda sozinho para o envio adiado mais próximo. É o default do iOS,
 * embrulhado em `beginBackgroundTask` ([runInBackgroundWindow]); serve também a teste.
 *
 * Não acorda o app fechado — no iOS quem faz isso é a sessão de segundo plano do `URLSession`, que
 * reabre o app ao terminar uma parte e chama [DirectUploadOutbox.requestDrain].
 *
 * @param scope onde as drenagens rodam (default: escopo de processo, `Dispatchers.Default`).
 * @param pausedRetryMillis espera antes de tentar de novo uma passada que parou sem rede.
 * @param runInBackgroundWindow embrulha cada drenagem (iOS: pede ao sistema os segundos de 2º plano).
 */
class InProcessDirectUploadScheduler(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val pausedRetryMillis: Long = 30_000L,
    private val nowMillis: () -> Long = ::currentTimeMillis,
    private val runInBackgroundWindow: suspend (suspend () -> Unit) -> Unit = { it() },
) : DirectUploadScheduler {

    private val canais = AtomicReference<Map<String, Channel<Unit>>>(emptyMap())

    override fun schedule(outbox: DirectUploadOutbox, need: DirectUploadNetworkNeed, urgent: Boolean) {
        canalDe(outbox).trySend(Unit)
    }

    private fun canalDe(outbox: DirectUploadOutbox): Channel<Unit> {
        canais.load()[outbox.name]?.let { return it }
        val novo = Channel<Unit>(Channel.CONFLATED)
        while (true) {
            val atual = canais.load()
            atual[outbox.name]?.let { return it }
            if (canais.compareAndSet(atual, atual + (outbox.name to novo))) break
        }
        scope.launch {
            for (sinal in novo) {
                var resumo: DirectUploadDrainSummary? = null
                try {
                    runInBackgroundWindow { resumo = (DirectUploadOutboxRegistry.get(outbox.name) ?: outbox).drainNow() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e("DirectUploadScheduler", "drenagem da fila de envio lançou", e)
                }
                val r = resumo ?: continue
                if (!r.needsRetry) continue
                val espera = r.nextAttemptAtMillis?.let { (it - nowMillis()).coerceAtLeast(1_000L) } ?: pausedRetryMillis
                scope.launch {
                    delay(espera)
                    novo.trySend(Unit)
                }
            }
        }
        return novo
    }
}
