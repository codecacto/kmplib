package br.com.codecacto.kmplib.ui.form

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A espera de quietude padrão da [FormAnswerQueue] — a mesma do `useFormAnswerQueue` da weblib. */
val FORM_ANSWER_QUEUE_DEBOUNCE: Duration = 600.milliseconds

/**
 * **Fila de salvamento PARCIAL de um formulário FormSchema v1** — o par do [FormRunnerState], como o
 * `useFormAnswerQueue` é o par do `FormRunner` na weblib. Vive no ViewModel:
 *
 * ```kotlin
 * private val fila = FormAnswerQueue(viewModelScope) { patch ->
 *     api.put("/v1/public/patient/preconsult/answers", SalvarRespostas(patch))   // {"answers": patch}
 * }
 * // FormRunnerEvent.Answered → fila.enqueue(evento.questionId, evento.value)
 * // FormRunnerEvent.Submit   → if (fila.flush()) api.submit(...) else avisar(textos.unsavedAnswers)
 * // ON_STOP da tela          → fila.sendNow()
 * ```
 *
 * - O lote sai como o **`AnswerPatch`** do contrato (`{ [questionId]: valor }`), e a resposta APAGADA
 *   vai como **`null`** — é como o servidor apaga a resposta de quem salva. No DTO do corpo, use
 *   [FormAnswerPatchSerializer] (o `null` precisa ir EXPLÍCITO).
 * - **Espera de quietude de 600 ms**: o runner emite a cada tecla, e sem espera cada tecla seria uma
 *   requisição (rota com limite de taxa devolveria 429 a quem digita rápido). [flush] e [sendNow]
 *   atravessam a espera.
 *
 * ## As garantias (as mesmas da weblib — é onde toda cópia manual erra)
 * 1. **A tela não espera a rede**: a resposta já está no estado do runner; aqui só se envia.
 * 2. **O que falhou volta para a fila** e é reenviado sozinho, com espera crescente (0,5 s → 8 s),
 *    **sem sobrescrever um valor mais novo** que entrou durante o envio.
 * 3. **Uma requisição de cada vez**: o que chega durante o envio se junta (por pergunta, o mais novo
 *    vence) e vai no lote seguinte.
 * 4. **[flush] antes de enviar** — enviar com resposta pendente fecha o formulário sem parte dela.
 * 5. **[sendNow] ao sair da tela** (`ON_STOP`, ou o `DisposableEffect` da rota): manda na hora o que
 *    estiver esperando, e esse envio sobrevive ao fim do `viewModelScope` (uma tentativa só, sem tela
 *    para reagendar).
 *
 * Fila na memória do processo: sobrevive à rotação (mora no ViewModel), não à morte do processo.
 *
 * @param save manda o patch. Lançar exceção = falhou (volta para a fila). Deve ser IDEMPOTENTE —
 *   reenviar o mesmo valor não pode doer, porque é o que a retentativa faz.
 */
class FormAnswerQueue(
    scope: CoroutineScope,
    debounce: Duration = FORM_ANSWER_QUEUE_DEBOUNCE,
    retryInitialDelay: Duration = 500.milliseconds,
    retryMaxDelay: Duration = 8.seconds,
    save: suspend (patch: Map<String, FormAnswerValue?>) -> Unit,
) {
    private val core = KeyedSaveQueue<FormAnswerValue?>(scope, debounce, retryInitialDelay, retryMaxDelay) { batch ->
        val patch = LinkedHashMap<String, FormAnswerValue?>()
        batch.forEach { (id, value) -> patch[id] = value }
        save(patch)
    }

    /** Quantas perguntas têm resposta que o servidor ainda não confirmou (na fila, esperando ou no ar). */
    val pending: StateFlow<Int> = core.pending

    /** Há um lote no ar agora. */
    val saving: StateFlow<Boolean> = core.saving

    /**
     * A falha do último envio, enquanto houver pendência; `null` quando tudo escoou. Na tela, o aviso
     * do runner é [FormRunnerTexts.unsavedAnswers].
     */
    val error: StateFlow<Throwable?> = core.error

    /** Enfileira a resposta (`null` = apagada) e agenda o envio. Por pergunta, o valor mais novo vence. */
    fun enqueue(questionId: String, value: FormAnswerValue?) = core.enqueue(questionId, value)

    /**
     * Manda o que falta AGORA e devolve `true` se o servidor tem tudo. Chamar ANTES de enviar:
     * `false` significa que enviar agora fecharia o formulário sem parte do que está na tela.
     */
    suspend fun flush(): Boolean = core.flush()

    /** Sair da tela: manda já o que está esperando (ver a garantia 5). */
    fun sendNow() = core.sendNow()
}

/**
 * O núcleo das filas de salvamento incremental ([FormAnswerQueue], `QuestionnaireAnswerQueue`): o
 * `useSaveQueue` da weblib. Genérico no valor (pode ser `null` = apagado), por chave.
 */
internal class KeyedSaveQueue<V>(
    private val scope: CoroutineScope,
    private val debounce: Duration,
    private val retryInitialDelay: Duration,
    private val retryMaxDelay: Duration,
    private val save: suspend (List<Pair<String, V>>) -> Unit,
) {
    private val queued = MutableStateFlow<Map<String, V>>(emptyMap())
    private val inFlight = MutableStateFlow<List<Pair<String, V>>>(emptyList())
    private val sending = Mutex()
    private var attempts = 0
    private var retryJob: Job? = null
    private var debounceJob: Job? = null

    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _error = MutableStateFlow<Throwable?>(null)
    val error: StateFlow<Throwable?> = _error.asStateFlow()

    fun enqueue(key: String, value: V) {
        queued.update { current -> LinkedHashMap(current).apply { this[key] = value } }
        refreshPending()
        if (!debounce.isPositive()) {
            scope.launch { drain() }
            return
        }
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounce)
            drain()
        }
    }

    suspend fun flush(): Boolean {
        debounceJob?.cancel()
        return drain() && queued.value.isEmpty() && inFlight.value.isEmpty()
    }

    /**
     * Manda o pendente sem esperar a quietude, num escopo DESLIGADO do [scope]: a tela que fecha
     * cancela o `viewModelScope`, e o que a pessoa decidiu não pode sumir com ela.
     */
    fun sendNow() {
        debounceJob?.cancel()
        if (queued.value.isEmpty()) return
        val detached = CoroutineScope(scope.coroutineContext.minusKey(Job) + SupervisorJob())
        detached.launch { drain() }
    }

    /** Uma drenagem de cada vez: quem chega durante o envio espera e encontra a fila vazia. */
    private suspend fun drain(): Boolean = sending.withLock { drainLocked() }

    private suspend fun drainLocked(): Boolean {
        while (true) {
            val batch = queued.getAndUpdate { emptyMap() }.toList()
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

    /** Devolve o lote à fila sem sobrescrever o que entrou durante o envio (quem decidiu de novo vence). */
    private fun restore(batch: List<Pair<String, V>>) {
        inFlight.value = emptyList()
        queued.update { current ->
            val merged = LinkedHashMap<String, V>()
            batch.forEach { (key, value) -> merged[key] = if (current.containsKey(key)) current.getValue(key) else value }
            current.forEach { (key, value) -> if (!merged.containsKey(key)) merged[key] = value }
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
        val waiting = queued.value
        _pending.value = waiting.size + inFlight.value.count { (key, _) -> !waiting.containsKey(key) }
    }
}
