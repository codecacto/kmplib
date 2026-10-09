package br.com.codecacto.kmplib.ui.questionnaire

import br.com.codecacto.kmplib.ui.form.KeyedSaveQueue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

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
    scope: CoroutineScope,
    retryInitialDelay: Duration = 500.milliseconds,
    retryMaxDelay: Duration = 8.seconds,
    save: suspend (List<QuestionnaireAnswerItem>) -> Unit,
) {
    // O núcleo é o mesmo da `FormAnswerQueue` (2.268.0): por pergunta, o item mais novo vence; sem
    // espera de quietude (a régua manda na hora, como o `useAnswerQueue` da weblib).
    private val core = KeyedSaveQueue<QuestionnaireAnswerItem>(scope, Duration.ZERO, retryInitialDelay, retryMaxDelay) { batch ->
        save(batch.map { it.second })
    }

    /** Quantas perguntas têm resposta que o servidor ainda não confirmou (na fila ou no lote no ar). */
    val pending: StateFlow<Int> = core.pending

    /** Há um lote no ar agora. */
    val saving: StateFlow<Boolean> = core.saving

    /** A falha do último envio, enquanto houver pendência; `null` quando tudo escoou. */
    val error: StateFlow<Throwable?> = core.error

    /** Enfileira a resposta e dispara o envio. Por pergunta, o valor mais novo vence. */
    fun enqueue(item: QuestionnaireAnswerItem) = core.enqueue(item.questionId, item)

    /**
     * Drena a fila agora e devolve `true` se o servidor tem tudo. Chamar ANTES de concluir: `false`
     * significa que concluir agora congelaria um resultado sem parte do que está na tela.
     */
    suspend fun flush(): Boolean = core.flush()
}
