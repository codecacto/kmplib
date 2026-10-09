package br.com.codecacto.kmplib.ui.questionnaire

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * **Fila de salvamento incremental das respostas** — o par do [QuestionnaireRunnerState], como o
 * `useAnswerQueue` é o par do `QuestionnaireRunner` na weblib. Vive no ViewModel:
 *
 * ```kotlin
 * private val fila = QuestionnaireAnswerQueue(viewModelScope) { lote -> api.salvarRespostas(id, lote) }
 * // evento Answered → fila.enqueue(evento.item)
 * // evento Finished → evento.cleared.forEach { fila.enqueue(QuestionnaireAnswerItem(it, null)) }
 * //                   if (!fila.flush()) avisar(textos.unsavedAnswers) else concluir()
 * ```
 *
 * O lote vai no formato da weblib (`[{questionId, value}]`), e `save` deve ser **idempotente por
 * pergunta** — reenviar o mesmo valor não pode doer, porque é o que a retentativa faz.
 *
 * ## As quatro garantias (as mesmas da weblib — é onde toda cópia manual erra)
 * 1. **A tela não espera a rede**: a resposta já está no estado do runner; aqui só se envia.
 * 2. **O que falhou volta para a fila** e é reenviado sozinho, com espera crescente (0,5 s → 8 s),
 *    **sem sobrescrever um valor mais novo** que entrou durante o envio. A retentativa é automática
 *    porque o toque que consertaria a fila é justamente o que pode não vir.
 * 3. **Uma requisição de cada vez** — dois lotes simultâneos fazem o servidor responder progressos
 *    divergentes. Enquanto um lote está no ar, o que chega se junta (por pergunta, o mais novo vence)
 *    e vai no lote seguinte: digitar um texto não vira uma requisição por tecla.
 * 4. **[flush] antes de concluir** — concluir com resposta pendente congela um resultado incompleto.
 *
 * Quem a pessoa vê como "não salvo" é [pending] (> 0) e [error] (a última falha; `null` quando a fila
 * escoou) — use `QuestionnaireTexts.unsavedAnswers` como aviso do runner.
 *
 * Fila na memória do processo: sobrevive à rotação (mora no ViewModel), não à morte do processo.
 *
 * @param scope onde os envios e a retentativa rodam (`viewModelScope`).
 * @param retryInitialDelay espera da primeira retentativa; dobra a cada falha até [retryMaxDelay].
 * @param save manda o lote. Lançar exceção = falhou (volta para a fila).
 */
class QuestionnaireAnswerQueue(
    private val scope: CoroutineScope,
    private val retryInitialDelay: Duration = 500.milliseconds,
    private val retryMaxDelay: Duration = 8.seconds,
    private val save: suspend (List<QuestionnaireAnswerItem>) -> Unit,
) {
    private val queued = MutableStateFlow<Map<String, QuestionnaireAnswerItem>>(emptyMap())
    private val inFlight = MutableStateFlow<List<QuestionnaireAnswerItem>>(emptyList())
    private val sending = Mutex()
    private var attempts = 0
    private var retryJob: Job? = null

    private val _pending = MutableStateFlow(0)

    /** Quantas perguntas têm resposta que o servidor ainda não confirmou (na fila ou no lote no ar). */
    val pending: StateFlow<Int> = _pending.asStateFlow()

    private val _saving = MutableStateFlow(false)

    /** Há um lote no ar agora. */
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _error = MutableStateFlow<Throwable?>(null)

    /** A falha do último envio, enquanto houver pendência; `null` quando tudo escoou. */
    val error: StateFlow<Throwable?> = _error.asStateFlow()

    /** Enfileira a resposta e dispara o envio. Por pergunta, o valor mais novo vence. */
    fun enqueue(item: QuestionnaireAnswerItem) {
        queued.update { it + (item.questionId to item) }
        refreshPending()
        scope.launch { drain() }
    }

    /**
     * Drena a fila agora e devolve `true` se o servidor tem tudo. Chamar ANTES de concluir: `false`
     * significa que concluir agora congelaria um resultado sem parte do que está na tela.
     */
    suspend fun flush(): Boolean = drain() && queued.value.isEmpty() && inFlight.value.isEmpty()

    /** Uma drenagem de cada vez (garantia 3): quem chega durante o envio espera e encontra a fila vazia. */
    private suspend fun drain(): Boolean = sending.withLock { drainLocked() }

    private suspend fun drainLocked(): Boolean {
        while (true) {
            val batch = queued.getAndUpdate { emptyMap() }.values.toList()
            if (batch.isEmpty()) {
                attempts = 0
                return true
            }
            inFlight.value = batch
            _saving.value = true
            refreshPending()
            try {
                save(batch)
                inFlight.value = emptyList()
                _error.value = null
            } catch (cancelled: CancellationException) {
                restore(batch)
                throw cancelled
            } catch (failure: Throwable) {
                restore(batch)
                _error.value = failure
                scheduleRetry()
                return false
            } finally {
                _saving.value = false
                refreshPending()
            }
        }
    }

    /** Devolve o lote à fila sem sobrescrever o que entrou durante o envio (quem respondeu de novo vence). */
    private fun restore(batch: List<QuestionnaireAnswerItem>) {
        inFlight.value = emptyList()
        queued.update { current ->
            val merged = LinkedHashMap<String, QuestionnaireAnswerItem>()
            batch.forEach { merged[it.questionId] = current[it.questionId] ?: it }
            current.forEach { (id, item) -> if (id !in merged) merged[id] = item }
            merged
        }
    }

    private fun scheduleRetry() {
        retryJob?.cancel()
        attempts++
        val factor = 1L shl (attempts - 1).coerceAtMost(30)
        val wait = (retryInitialDelay * factor.toDouble()).coerceAtMost(retryMaxDelay)
        retryJob = scope.launch {
            delay(wait)
            drain()
        }
    }

    private fun refreshPending() {
        _pending.value = queued.value.size + inFlight.value.count { it.questionId !in queued.value }
    }
}
