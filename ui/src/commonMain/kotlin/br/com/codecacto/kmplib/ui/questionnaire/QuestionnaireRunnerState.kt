@file:Suppress("DEPRECATION")

package br.com.codecacto.kmplib.ui.questionnaire

import androidx.compose.runtime.Immutable
import br.com.codecacto.kmplib.ui.components.toDoubleFromNumberField

/**
 * O ritmo do questionário — o `QuestionnairePace` da weblib.
 *
 * - [Grouped]: as perguntas do MESMO bloco numa tela só (celular: uma pergunta por tela multiplica
 *   toque e espera, e a pessoa perde o fio do bloco).
 * - [OneByOne]: uma pergunta por vez, com avanço automático ao escolher (tela larga: o bloco inteiro
 *   vira parede de texto e a régua fica longe do enunciado).
 * - [Auto] (default): agrupado em `LocalIsCompact`, uma por vez fora dele.
 */
enum class QuestionnairePace {
    Auto,
    Grouped,
    OneByOne,
    ;

    /** `true` quando, nesta largura, o ritmo é pergunta a pergunta. */
    fun isOneByOne(isCompact: Boolean): Boolean = this == OneByOne || (this == Auto && !isCompact)
}

/**
 * Pedido de foco para uma pergunta — o "rolar até a primeira que falta e destacá-la" da weblib.
 *
 * É ESTADO, não efeito avulso (a recomendação oficial do Android para evento de ViewModel): a tela
 * rola até a pergunta, tenta o foco e devolve [QuestionnaireRunnerAction.FocusHandled] com o
 * [serial], e o pedido sai do estado. O serial distingue dois pedidos seguidos para a mesma pergunta.
 */
@Immutable
data class QuestionnaireFocusRequest(val questionId: String, val serial: Long)

/** Onde a pessoa está, já resolvido contra a visibilidade atual. Índices são das listas visíveis. */
@Immutable
data class QuestionnairePosition(
    val blockIndex: Int,
    val blockCount: Int,
    val block: QuestionnaireBlock,
    val questionIndex: Int,
    val questionCount: Int,
    val question: QuestionnaireQuestion?,
) {
    val isFirstBlock: Boolean get() = blockIndex == 0
    val isLastBlock: Boolean get() = blockIndex == blockCount - 1
    val isFirstQuestion: Boolean get() = questionIndex <= 0
    val isLastQuestion: Boolean get() = questionIndex >= questionCount - 1
}

/**
 * O estado do runner — imutável, do ViewModel do app (MVI). A tela só desenha e devolve ações.
 *
 * O consumidor guarda este estado no SEU estado de tela e repassa as ações a [reduce]:
 * ```kotlin
 * is TriagemAction.Questionario -> {
 *     val update = currentState.questionario.reduce(action.action)
 *     setState { copy(questionario = update.state) }
 *     when (val evento = update.event) {
 *         is QuestionnaireRunnerEvent.Answered -> fila.enqueue(evento.item)
 *         is QuestionnaireRunnerEvent.Finished -> concluir(evento)
 *         QuestionnaireRunnerEvent.Exited -> sendEffect(TriagemEffect.Voltar)
 *         null -> Unit
 *     }
 * }
 * ```
 *
 * @property answers as respostas guardadas (inclusive de pergunta hoje oculta — a conclusão as descarta).
 * @property context valores que as condições leem e que NÃO são pergunta: `sexo`, `idade`… O app
 *   informa (vêm do cadastro); o runner nunca os edita.
 * @property filledBy quem respondeu cada pergunta (chave do respondente: `"paciente"`,
 *   `"recepcao"`…). Vem do servidor, que carimba pela sessão autenticada; o runner só atualiza a
 *   cópia local quando [respondent] responde, para a marca sair na hora.
 * @property respondent quem está respondendo agora. Respostas de outro respondente ganham a marca
 *   "Respondida por …" na tela.
 * @property blockId/[questionId] a posição (`null` = começo). Ids, não índices: a visibilidade muda
 *   enquanto se responde, e índice de lista que muda aponta para a pergunta errada.
 * @property flagged perguntas com erro EXIBIDO — marcadas por um envio recusado, cada uma limpa ao ser
 *   editada (o padrão da fábrica: erro no campo, só depois do envio, sai ao digitar).
 * @property numberDrafts o texto cru dos campos numéricos ("72," no meio da digitação), para o eco do
 *   ViewModel não reescrever o campo com "72".
 * @property busy concluindo: toda ação é ignorada (dois toques em "Concluir" não concluem duas vezes).
 * @property exitEnabled "Voltar" no primeiro passo sai do questionário ([QuestionnaireRunnerEvent.Exited]);
 *   sem isso, o botão some no começo — o `onExit` opcional da weblib.
 */
@Immutable
data class QuestionnaireRunnerState(
    val questionnaire: Questionnaire,
    val answers: Map<String, QuestionnaireValue> = emptyMap(),
    val context: Map<String, QuestionnaireValue> = emptyMap(),
    val filledBy: Map<String, String> = emptyMap(),
    val respondent: String? = null,
    val blockId: String? = null,
    val questionId: String? = null,
    val flagged: Set<String> = emptySet(),
    val numberDrafts: Map<String, String> = emptyMap(),
    val focusRequest: QuestionnaireFocusRequest? = null,
    val busy: Boolean = false,
    val exitEnabled: Boolean = false,
) {

    /** Avalia o questionário neste estado (crie uma por mudança; ver [QuestionnaireEvaluation]). */
    fun evaluate(): QuestionnaireEvaluation = questionnaire.evaluate(answers, context)

    /**
     * A posição resolvida. Bloco/pergunta que deixaram de aparecer caem no vizinho mais próximo
     * DEPOIS deles (senão no de antes); sem pergunta escolhida, a primeira sem resposta do bloco.
     * `null` = nenhum bloco visível.
     */
    fun position(evaluation: QuestionnaireEvaluation = evaluate()): QuestionnairePosition? {
        val blocks = evaluation.visibleBlocks
        if (blocks.isEmpty()) return null
        val blockIndex = resolveBlockIndex(blocks)
        val block = blocks[blockIndex]
        val navigable = evaluation.navigableQuestions(block)
        val questionIndex = resolveQuestionIndex(block, navigable, evaluation)
        return QuestionnairePosition(
            blockIndex = blockIndex,
            blockCount = blocks.size,
            block = block,
            questionIndex = questionIndex,
            questionCount = navigable.size,
            question = navigable.getOrNull(questionIndex),
        )
    }

    /** Aplica uma ação. Puro: devolve o estado novo e, quando houver, o evento para o ViewModel. */
    fun reduce(action: QuestionnaireRunnerAction): QuestionnaireRunnerUpdate = QuestionnaireRunnerReducer.reduce(this, action)

    private fun resolveBlockIndex(blocks: List<QuestionnaireBlock>): Int {
        val id = blockId ?: return 0
        val direct = blocks.indexOfFirst { it.id == id }
        if (direct >= 0) return direct
        val all = questionnaire.blocks
        val position = all.indexOfFirst { it.id == id }
        if (position < 0) return 0
        val after = blocks.indexOfFirst { visible -> all.indexOfFirst { it.id == visible.id } > position }
        return if (after >= 0) after else blocks.lastIndex
    }

    private fun resolveQuestionIndex(
        block: QuestionnaireBlock,
        navigable: List<QuestionnaireQuestion>,
        evaluation: QuestionnaireEvaluation,
    ): Int {
        if (navigable.isEmpty()) return -1
        val id = questionId
        if (id == null) {
            val firstOpen = navigable.indexOfFirst { !evaluation.isAnswered(it.id) }
            return if (firstOpen >= 0) firstOpen else 0
        }
        val direct = navigable.indexOfFirst { it.id == id }
        if (direct >= 0) return direct
        val order = block.questions.indexOfFirst { it.id == id }
        if (order < 0) return 0
        val after = navigable.indexOfFirst { candidate -> block.questions.indexOfFirst { it.id == candidate.id } > order }
        return if (after >= 0) after else navigable.lastIndex
    }

    internal fun nextFocus(questionId: String): QuestionnaireFocusRequest =
        QuestionnaireFocusRequest(questionId, (focusRequest?.serial ?: 0L) + 1)

    companion object {
        /**
         * Estado inicial. Com [resume] (default), abre na primeira pergunta sem resposta — quem fechou
         * no meio volta onde parou, inclusive no ritmo pergunta a pergunta.
         */
        fun start(
            questionnaire: Questionnaire,
            answers: Map<String, QuestionnaireValue> = emptyMap(),
            context: Map<String, QuestionnaireValue> = emptyMap(),
            filledBy: Map<String, String> = emptyMap(),
            respondent: String? = null,
            exitEnabled: Boolean = false,
            resume: Boolean = true,
        ): QuestionnaireRunnerState {
            val base = QuestionnaireRunnerState(
                questionnaire = questionnaire,
                answers = answers,
                context = context,
                filledBy = filledBy,
                respondent = respondent,
                exitEnabled = exitEnabled,
            )
            val evaluation = base.evaluate()
            if (resume) {
                val step = evaluation.firstUnansweredStep() ?: return base
                return base.copy(blockId = step.blockId, questionId = step.questionId)
            }
            val first = evaluation.visibleBlocks.firstOrNull() ?: return base
            return base.copy(blockId = first.id, questionId = evaluation.navigableQuestions(first).firstOrNull()?.id)
        }
    }
}

/** O que a tela pede. */
sealed interface QuestionnaireRunnerAction {
    /**
     * Resposta de `scale`/`choice`/`multi-choice`/`date`/`text` — `null` apaga. Valor que não é
     * resposta válida para a pergunta (opção inexistente, ponto fora da régua) é ignorado.
     */
    data class Answer(val questionId: String, val value: QuestionnaireValue?) : QuestionnaireRunnerAction

    /** Texto digitado num campo `number` (vírgula decimal, como o `NumberField`). */
    @Deprecated(DEPRECATED_QUESTION_TYPE)
    data class EditNumber(val questionId: String, val text: String) : QuestionnaireRunnerAction

    /** "Continuar"/"Concluir". [oneByOne] é o ritmo em que a tela está (ver [QuestionnairePace]). */
    data class Next(val oneByOne: Boolean) : QuestionnaireRunnerAction

    /** "Voltar". */
    data class Back(val oneByOne: Boolean) : QuestionnaireRunnerAction

    /** O avanço automático depois de escolher (ritmo pergunta a pergunta), disparado pela tela com atraso. */
    data class AutoAdvance(val questionId: String) : QuestionnaireRunnerAction

    /** Ir direto a uma pergunta visível (ex.: a lista de reservadas pendentes) — com foco nela. */
    data class GoTo(val questionId: String) : QuestionnaireRunnerAction

    /** A tela atendeu o [QuestionnaireFocusRequest] de [serial]. */
    data class FocusHandled(val serial: Long) : QuestionnaireRunnerAction
}

/** O que o ViewModel precisa fazer depois de uma ação. */
sealed interface QuestionnaireRunnerEvent {
    /**
     * Uma resposta mudou — o item vai para a fila de salvamento ([QuestionnaireAnswerQueue]).
     * Texto e número disparam a cada tecla: a fila junta e manda o valor mais novo.
     */
    data class Answered(val item: QuestionnaireAnswerItem) : QuestionnaireRunnerEvent

    /**
     * Tudo válido e a pessoa concluiu. [answers] são as respostas que VALEM (perguntas visíveis,
     * normalizadas); [cleared] são as perguntas cuja resposta deixou de se aplicar (ficaram ocultas)
     * e já saíram do estado — mande-as à fila como item com `value = null` antes do `flush()`.
     * Depois, ponha `busy = true` no estado enquanto conclui.
     */
    data class Finished(
        val answers: Map<String, QuestionnaireValue>,
        val cleared: List<String>,
    ) : QuestionnaireRunnerEvent

    /** "Voltar" no primeiro passo, com [QuestionnaireRunnerState.exitEnabled]. */
    data object Exited : QuestionnaireRunnerEvent
}

/** Resultado de [QuestionnaireRunnerState.reduce]. */
@Immutable
data class QuestionnaireRunnerUpdate(
    val state: QuestionnaireRunnerState,
    val event: QuestionnaireRunnerEvent? = null,
)

/** A regra de navegação e de resposta — pura e testada em `QuestionnaireRunnerReducerTest`. */
internal object QuestionnaireRunnerReducer {

    fun reduce(state: QuestionnaireRunnerState, action: QuestionnaireRunnerAction): QuestionnaireRunnerUpdate {
        if (action is QuestionnaireRunnerAction.FocusHandled) {
            return if (state.focusRequest?.serial == action.serial) {
                QuestionnaireRunnerUpdate(state.copy(focusRequest = null))
            } else {
                QuestionnaireRunnerUpdate(state)
            }
        }
        if (state.busy) return QuestionnaireRunnerUpdate(state)
        return when (action) {
            is QuestionnaireRunnerAction.Answer -> answer(state, action.questionId, action.value)
            is QuestionnaireRunnerAction.EditNumber -> editNumber(state, action.questionId, action.text)
            is QuestionnaireRunnerAction.Next -> next(state, action.oneByOne)
            is QuestionnaireRunnerAction.Back -> back(state, action.oneByOne)
            is QuestionnaireRunnerAction.AutoAdvance -> autoAdvance(state, action.questionId)
            is QuestionnaireRunnerAction.GoTo -> goTo(state, action.questionId)
            is QuestionnaireRunnerAction.FocusHandled -> QuestionnaireRunnerUpdate(state)
        }
    }

    private fun answerable(state: QuestionnaireRunnerState, questionId: String): QuestionnaireQuestion? {
        val evaluation = state.evaluate()
        val question = evaluation.question(questionId) ?: return null
        if (!evaluation.isVisible(questionId) || !state.questionnaire.isAnswerable(question)) return null
        return question
    }

    private fun answer(state: QuestionnaireRunnerState, questionId: String, value: QuestionnaireValue?): QuestionnaireRunnerUpdate {
        val question = answerable(state, questionId) ?: return QuestionnaireRunnerUpdate(state)
        val stored: QuestionnaireValue? = when {
            value == null -> null
            // Texto guarda o que foi DIGITADO (espaços inclusive): é o eco que o campo reconhece.
            question.type == QuestionnaireQuestionType.TEXT -> when (value) {
                is QuestionnaireValue.Text -> value.takeIf { it.value.isNotEmpty() }
                else -> return QuestionnaireRunnerUpdate(state)
            }
            question.type == QuestionnaireQuestionType.MULTI_CHOICE &&
                value is QuestionnaireValue.Choices && value.values.isEmpty() -> null
            else -> coerceQuestionnaireAnswer(state.questionnaire, question, value)
                ?: return QuestionnaireRunnerUpdate(state)
        }
        return store(state, questionId, stored, state.numberDrafts - questionId)
    }

    private fun editNumber(state: QuestionnaireRunnerState, questionId: String, text: String): QuestionnaireRunnerUpdate {
        val question = answerable(state, questionId) ?: return QuestionnaireRunnerUpdate(state)
        if (question.type != QuestionnaireQuestionType.NUMBER) return QuestionnaireRunnerUpdate(state)
        val parsed = text.trim().toDoubleFromNumberField()?.takeIf { it.isFinite() }
        val drafts = if (text.isEmpty()) state.numberDrafts - questionId else state.numberDrafts + (questionId to text)
        return store(state, questionId, parsed?.let { QuestionnaireValue.Number(it) }, drafts)
    }

    private fun store(
        state: QuestionnaireRunnerState,
        questionId: String,
        stored: QuestionnaireValue?,
        drafts: Map<String, String>,
    ): QuestionnaireRunnerUpdate {
        val old = state.answers[questionId]
        val changed = old != stored
        val answers = if (stored == null) state.answers - questionId else state.answers + (questionId to stored)
        val filledBy = when {
            !changed -> state.filledBy
            stored == null -> state.filledBy - questionId
            state.respondent != null -> state.filledBy + (questionId to state.respondent)
            else -> state.filledBy
        }
        val next = state.copy(answers = answers, filledBy = filledBy, flagged = state.flagged - questionId, numberDrafts = drafts)
        val before = old?.let(::normalizedOrNull)
        val after = stored?.let(::normalizedOrNull)
        val event = if (before != after) QuestionnaireRunnerEvent.Answered(QuestionnaireAnswerItem(questionId, after)) else null
        return QuestionnaireRunnerUpdate(next, event)
    }

    private fun next(state: QuestionnaireRunnerState, oneByOne: Boolean): QuestionnaireRunnerUpdate {
        val evaluation = state.evaluate()
        val position = state.position(evaluation) ?: return finish(state, evaluation)
        val block = position.block
        val navigable = evaluation.navigableQuestions(block)
        if (oneByOne) {
            val current = position.question
            if (current != null && evaluation.errorOf(current.id) != null) {
                return QuestionnaireRunnerUpdate(
                    state.copy(
                        blockId = block.id,
                        questionId = current.id,
                        flagged = setOf(current.id),
                        focusRequest = state.nextFocus(current.id),
                    ),
                )
            }
            if (position.questionIndex in 0 until navigable.lastIndex) {
                return QuestionnaireRunnerUpdate(
                    state.copy(blockId = block.id, questionId = navigable[position.questionIndex + 1].id, flagged = emptySet()),
                )
            }
        }
        // Fim do bloco (e sempre, no agrupado): o bloco inteiro precisa estar completo — no agrupado
        // a pessoa pode ter pulado uma no meio da lista.
        val pending = evaluation.pendingIn(block)
        if (pending.isNotEmpty()) return flag(state, block, pending)
        if (!position.isLastBlock) {
            val nextBlock = evaluation.visibleBlocks[position.blockIndex + 1]
            val entry = evaluation.navigableQuestions(nextBlock).let { list -> list.firstOrNull { !evaluation.isAnswered(it.id) } ?: list.firstOrNull() }
            return QuestionnaireRunnerUpdate(state.copy(blockId = nextBlock.id, questionId = entry?.id, flagged = emptySet()))
        }
        return finish(state, evaluation)
    }

    private fun flag(state: QuestionnaireRunnerState, block: QuestionnaireBlock, pending: List<QuestionnaireQuestion>): QuestionnaireRunnerUpdate {
        val first = pending.first()
        return QuestionnaireRunnerUpdate(
            state.copy(
                blockId = block.id,
                questionId = first.id,
                flagged = pending.map { it.id }.toSet(),
                focusRequest = state.nextFocus(first.id),
            ),
        )
    }

    /**
     * Concluir confere o questionário INTEIRO: uma resposta adiante pode ter feito aparecer uma
     * pergunta obrigatória num bloco já passado, e a retomada pode ter aberto depois de um bloco
     * incompleto. Havendo pendência, leva até ela em vez de concluir.
     */
    private fun finish(state: QuestionnaireRunnerState, evaluation: QuestionnaireEvaluation): QuestionnaireRunnerUpdate {
        val pendingBlock = evaluation.visibleBlocks.firstOrNull { evaluation.pendingIn(it).isNotEmpty() }
        if (pendingBlock != null) return flag(state, pendingBlock, evaluation.pendingIn(pendingBlock))
        val cleared = evaluation.hiddenAnsweredIds
        val next = state.copy(
            answers = state.answers - cleared.toSet(),
            filledBy = state.filledBy - cleared.toSet(),
            numberDrafts = state.numberDrafts - cleared.toSet(),
            flagged = emptySet(),
        )
        return QuestionnaireRunnerUpdate(next, QuestionnaireRunnerEvent.Finished(evaluation.effectiveAnswers, cleared))
    }

    private fun back(state: QuestionnaireRunnerState, oneByOne: Boolean): QuestionnaireRunnerUpdate {
        val evaluation = state.evaluate()
        val position = state.position(evaluation)
        if (position != null) {
            if (oneByOne && position.questionIndex > 0) {
                val navigable = evaluation.navigableQuestions(position.block)
                return QuestionnaireRunnerUpdate(
                    state.copy(blockId = position.block.id, questionId = navigable[position.questionIndex - 1].id, flagged = emptySet()),
                )
            }
            if (position.blockIndex > 0) {
                // Volta para a ÚLTIMA pergunta do bloco anterior: "voltar" é a pergunta de antes.
                val previous = evaluation.visibleBlocks[position.blockIndex - 1]
                val last = evaluation.navigableQuestions(previous).lastOrNull()
                return QuestionnaireRunnerUpdate(state.copy(blockId = previous.id, questionId = last?.id, flagged = emptySet()))
            }
        }
        return if (state.exitEnabled) {
            QuestionnaireRunnerUpdate(state.copy(flagged = emptySet()), QuestionnaireRunnerEvent.Exited)
        } else {
            QuestionnaireRunnerUpdate(state)
        }
    }

    /**
     * Avanço automático: só para a FRENTE, só da pergunta que ainda é a atual e só quando há para
     * onde ir — na última pergunta do bloco ele viraria "conclui o bloco sozinho", que é decisão da
     * pessoa, não da tela.
     */
    private fun autoAdvance(state: QuestionnaireRunnerState, questionId: String): QuestionnaireRunnerUpdate {
        val evaluation = state.evaluate()
        val position = state.position(evaluation) ?: return QuestionnaireRunnerUpdate(state)
        if (position.question?.id != questionId) return QuestionnaireRunnerUpdate(state)
        if (evaluation.errorOf(questionId) != null) return QuestionnaireRunnerUpdate(state)
        val navigable = evaluation.navigableQuestions(position.block)
        if (position.questionIndex !in 0 until navigable.lastIndex) return QuestionnaireRunnerUpdate(state)
        return QuestionnaireRunnerUpdate(
            state.copy(blockId = position.block.id, questionId = navigable[position.questionIndex + 1].id, flagged = emptySet()),
        )
    }

    private fun goTo(state: QuestionnaireRunnerState, questionId: String): QuestionnaireRunnerUpdate {
        val evaluation = state.evaluate()
        val block = evaluation.visibleBlocks.firstOrNull { b -> evaluation.navigableQuestions(b).any { it.id == questionId } }
            ?: return QuestionnaireRunnerUpdate(state)
        return QuestionnaireRunnerUpdate(
            state.copy(blockId = block.id, questionId = questionId, flagged = emptySet(), focusRequest = state.nextFocus(questionId)),
        )
    }
}
