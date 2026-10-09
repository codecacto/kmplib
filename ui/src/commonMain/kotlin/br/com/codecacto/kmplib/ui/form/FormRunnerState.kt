package br.com.codecacto.kmplib.ui.form

import androidx.compose.runtime.Immutable
import br.com.codecacto.kmplib.ui.questionnaire.Questionnaire
import br.com.codecacto.kmplib.ui.questionnaire.QuestionnaireBlock
import br.com.codecacto.kmplib.ui.questionnaire.QuestionnaireFocusRequest
import br.com.codecacto.kmplib.ui.questionnaire.QuestionnaireQuestion
import br.com.codecacto.kmplib.ui.questionnaire.QuestionnaireRunnerAction
import br.com.codecacto.kmplib.ui.questionnaire.QuestionnaireRunnerEvent
import br.com.codecacto.kmplib.ui.questionnaire.QuestionnaireRunnerState
import br.com.codecacto.kmplib.ui.questionnaire.QuestionnaireScale
import br.com.codecacto.kmplib.ui.questionnaire.QuestionnaireValue
import br.com.codecacto.kmplib.ui.questionnaire.points
import kotlin.math.abs
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Pedido de foco numa pergunta — o "foco no primeiro campo marcado" da weblib.
 *
 * É ESTADO, não efeito avulso (a recomendação oficial do Android para evento de ViewModel): a tela
 * rola até a pergunta, tenta o foco e devolve [FormRunnerAction.FocusHandled] com o [serial].
 */
@Immutable
data class FormFocusRequest(val questionId: String, val serial: Long)

/**
 * Um arquivo escolhido na tela, pronto para a rota de upload do projeto (o app o recebe em
 * [FormRunnerEvent.UploadRequested]). Igualdade por IDENTIDADE: comparar megabytes a cada mudança de
 * estado seria o custo de uma tela que trava.
 */
class FormPickedFile(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    val sizeBytes: Long get() = bytes.size.toLong()

    /** Nome de arquivo pode ser dado pessoal ("exame-joao.pdf"): fora do log. */
    override fun toString(): String = "FormPickedFile(<redigido>, $mimeType, ${bytes.size} B)"
}

/** Por que um anexo não subiu — a tela diz a frase ([FormRunnerTexts]). */
sealed interface FormUploadProblem {
    /** Formato fora do `accept` da pergunta. */
    data object WrongType : FormUploadProblem

    /** Passa do `maxSizeMb`. */
    data object TooBig : FormUploadProblem

    /** A pergunta já tem `maxFiles` anexos (ou subindo). */
    data object TooMany : FormUploadProblem

    /** O seletor não conseguiu ler o arquivo. */
    data object Unreadable : FormUploadProblem

    /** A câmera foi negada (ou o app não declara a permissão). */
    data object CameraDenied : FormUploadProblem

    /** A câmera não abriu. */
    data object CameraUnavailable : FormUploadProblem

    /**
     * A rota do projeto falhou — o único que se tenta de novo. [message] é a frase do SERVIDOR quando
     * ele mandou uma para gente ([FormUploadException]); sem ela, a da lib.
     */
    data class UploadFailed(val message: String? = null) : FormUploadProblem
}

/**
 * Um anexo subindo — ou que falhou, ou que foi recusado aqui (tipo, tamanho, teto). Mora no estado do
 * runner para sobreviver à troca de etapa.
 *
 * @property progress 0..1, ou `null` sem progresso informado (indeterminado).
 */
@Immutable
data class FormUpload(
    val key: String,
    val questionId: String,
    val name: String,
    val sizeBytes: Long,
    val progress: Float? = null,
    val problem: FormUploadProblem? = null,
    /** O arquivo, para tentar de novo. `null` no recusado (não há o que reenviar). */
    val file: FormPickedFile? = null,
) {
    /** Ainda subindo (nem falhou nem foi recusado). */
    val isActive: Boolean get() = problem == null

    val isRetryable: Boolean get() = problem is FormUploadProblem.UploadFailed && file != null

    override fun toString(): String = "FormUpload($key, <redigido>, progress=$progress, problem=$problem)"
}

/** O que o app recebe para subir um arquivo pela rota DELE (ver [FormFileUploader]). */
@Immutable
data class FormUploadRequest(
    val key: String,
    val questionId: String,
    /** O `file.purpose` da pergunta (`"LAB_REPORT"`) — o projeto o manda à rota. */
    val purpose: String,
    val file: FormPickedFile,
)

/**
 * O estado do `FormRunner` — imutável, do ViewModel do app (MVI). A tela só desenha e devolve ações;
 * o ViewModel aplica [reduce] e trata os [FormRunnerEvent]s (salvar, subir arquivo, enviar, sair).
 *
 * ```kotlin
 * is PreconsultaAction.Formulario -> {
 *     val update = currentState.form.reduce(action.action)
 *     setState { copy(form = update.state) }
 *     update.events.forEach { evento ->
 *         when (evento) {
 *             is FormRunnerEvent.Answered -> fila.enqueue(evento.questionId, evento.value)
 *             is FormRunnerEvent.UploadRequested -> anexos.start(evento.request) { onAction(PreconsultaAction.Formulario(it)) }
 *             is FormRunnerEvent.UploadCancelled -> anexos.cancel(evento.key)
 *             is FormRunnerEvent.Submit -> enviar()
 *             FormRunnerEvent.Exited -> sendEffect(PreconsultaEffect.Voltar)
 *             is FormRunnerEvent.StepChanged -> Unit
 *         }
 *     }
 * }
 * ```
 *
 * @property schema o formulário como o servidor serviu para QUEM VÊ (reservada já resolvida).
 * @property answers as respostas (a resposta apagada sai do mapa; vai como `null` na fila).
 * @property today o `"today"` dos limites de data — no fuso de quem decide (a clínica).
 * @property context o que o cliente sabe do contexto (quase sempre nada: o servidor já resolveu).
 * @property requireConsent `false` na triagem da recepção (o consentimento é do paciente).
 * @property onlyQuestionIds limita o que o envio confere à visão de quem envia. `null` = todas.
 * @property lockedQuestions perguntas que esta tela NÃO edita, com a frase no lugar do campo — na
 *   triagem, as que o paciente já respondeu ("Respondida pelo paciente"). Ficam fora da conferência e
 *   contam como respondidas no progresso.
 * @property markReserved mostra o selo de RESERVADA e o "pendente — perguntar na consulta" (a visão do
 *   MÉDICO). O paciente recebe o schema com `reserved` também — por isso é opt-in.
 * @property scores resultados de escala que o SERVIDOR calculou (médico). O runner só exibe.
 * @property serverErrors o `details` do 400/422 do servidor, por pergunta — vale até a pessoa mexer no
 *   campo. Use [FormRunnerAction.ServerErrors] para que a tela leve à etapa do primeiro.
 * @property stepId a etapa (id da seção). Ids, não índices: a visibilidade muda enquanto se responde.
 * @property issues problemas EXIBIDOS por pergunta — marcados por "Continuar"/"Enviar" ou pelo sair de
 *   um campo editado; cada pergunta limpa os seus ao ser editada.
 * @property busy enviando: trava a tela (dois toques em "Enviar" não enviam duas vezes).
 * @property exitEnabled "Voltar" na primeira etapa sai do formulário ([FormRunnerEvent.Exited]).
 */
@Immutable
data class FormRunnerState(
    val schema: FormSchemaV1,
    val answers: Map<String, FormAnswerValue> = emptyMap(),
    val today: LocalDate = formToday(),
    val context: FormContext = FormContext(),
    val requireConsent: Boolean = true,
    val onlyQuestionIds: Set<String>? = null,
    val lockedQuestions: Map<String, String> = emptyMap(),
    val markReserved: Boolean = false,
    val scores: List<FormScoreResult> = emptyList(),
    val serverErrors: Map<String, String> = emptyMap(),
    val stepId: String? = null,
    val issues: Map<String, List<FormAnswerIssue>> = emptyMap(),
    val dismissedServerErrors: Set<String> = emptySet(),
    val touched: Set<String> = emptySet(),
    /** A pergunta da seção `likert` em que o ritmo pergunta a pergunta está (`null` = a primeira sem resposta). */
    val likertQuestionId: String? = null,
    val uploads: List<FormUpload> = emptyList(),
    /** Nome dos arquivos enviados NESTA tela, por `fileId` (o servidor só devolve o id). */
    val fileNames: Map<String, String> = emptyMap(),
    /** "Enviar" com anexo ainda subindo: a tela diz "aguarde". */
    val waitingFiles: Boolean = false,
    val focusRequest: FormFocusRequest? = null,
    val busy: Boolean = false,
    val exitEnabled: Boolean = false,
    /** Contador das chaves de anexo. */
    val uploadSequence: Long = 0,
) {

    /** O que está visível agora (crie uma por mudança; a tela faz `remember`). */
    fun visibility(): FormVisibility = schema.evaluateVisibility(answers, context)

    /** As etapas visíveis, na ordem. */
    fun steps(visibility: FormVisibility = visibility()): List<FormRunnerStep> = formRunnerSteps(schema, visibility)

    /** O índice da etapa atual em [steps] (resolvido contra a visibilidade). */
    fun stepIndex(steps: List<FormRunnerStep> = steps()): Int = resolveFormStepIndex(schema, steps, stepId)

    /**
     * O escopo da conferência: o de quem envia, menos as travadas. `null` = todas as perguntas.
     */
    val scope: Set<String>?
        get() {
            if (onlyQuestionIds == null && lockedQuestions.isEmpty()) return null
            val base = onlyQuestionIds ?: schema.sections.flatMap { s -> s.questions.map { it.id } }.toSet()
            return base - lockedQuestions.keys
        }

    /** O progresso do formulário inteiro; as travadas contam como respondidas (alguém já respondeu). */
    fun progress(steps: List<FormRunnerStep> = steps()): FormProgress {
        val own = formRunnerProgress(steps, answers, scope)
        val locked = steps.sumOf { step -> step.questions.count { it.isHandledByClient && it.id in lockedQuestions } }
        return FormProgress(own.answered + locked, own.total + locked)
    }

    /** A frase do servidor para a pergunta, enquanto a pessoa não mexeu no campo. */
    fun serverErrorOf(questionId: String): String? = serverErrors[questionId]?.takeIf { questionId !in dismissedServerErrors }

    /** A pergunta tem problema marcado (do cliente ou do servidor). */
    fun isFlagged(questionId: String): Boolean = !issues[questionId].isNullOrEmpty() || serverErrorOf(questionId) != null

    /** Aplica uma ação. Puro: devolve o estado novo e os eventos para o ViewModel. */
    fun reduce(action: FormRunnerAction): FormRunnerUpdate = FormRunnerReducer.reduce(this, action)

    /** Resposta de formulário clínico é dado de saúde: o estado não vai inteiro para o log. */
    override fun toString(): String =
        "FormRunnerState(schema=${schema.id}, step=$stepId, answers=${answers.size}, issues=${issues.keys}, " +
            "uploads=${uploads.size}, busy=$busy)"

    companion object {
        /**
         * Estado inicial. Com [resume] (default), abre na PRIMEIRA etapa com pendência — quem fechou
         * no meio volta onde parou ([firstIncompleteFormStep]).
         */
        fun start(
            schema: FormSchemaV1,
            answers: Map<String, FormAnswerValue> = emptyMap(),
            today: LocalDate = formToday(),
            context: FormContext = FormContext(),
            requireConsent: Boolean = true,
            onlyQuestionIds: Set<String>? = null,
            lockedQuestions: Map<String, String> = emptyMap(),
            markReserved: Boolean = false,
            scores: List<FormScoreResult> = emptyList(),
            exitEnabled: Boolean = false,
            resume: Boolean = true,
        ): FormRunnerState {
            val base = FormRunnerState(
                schema = schema,
                answers = answers,
                today = today,
                context = context,
                requireConsent = requireConsent,
                onlyQuestionIds = onlyQuestionIds,
                lockedQuestions = lockedQuestions,
                markReserved = markReserved,
                scores = scores,
                exitEnabled = exitEnabled,
            )
            if (!resume) return base
            val first = firstIncompleteFormStep(schema, answers, today, context, base.scope, requireConsent)
            return base.copy(stepId = first)
        }
    }
}

/** O que a tela pede. */
sealed interface FormRunnerAction {
    /**
     * Resposta mudou — `null` apaga. [path] = o campo do item editado (`"[1].dose"`), para limpar só
     * o erro dele. Texto vazio e lista vazia também apagam.
     */
    data class Answer(val questionId: String, val value: FormAnswerValue?, val path: String? = null) : FormRunnerAction

    /** Saiu de um campo: se foi editado, confere o FORMATO na hora (a obrigatoriedade é do envio). */
    data class Blur(val questionId: String) : FormRunnerAction

    /** "Continuar" (na última etapa, o mesmo que [Submit]). */
    data object Next : FormRunnerAction

    /** "Enviar": confere TUDO e, limpo, emite [FormRunnerEvent.Submit]. */
    data object Submit : FormRunnerAction

    /** "Voltar". */
    data object Back : FormRunnerAction

    /** Ir direto a uma etapa (o índice de etapas da tela larga). */
    data class GoToStep(val sectionId: String) : FormRunnerAction

    /** Ação da seção `likert`, que o runner delega ao `QuestionnaireRunner`. */
    data class Likert(val action: QuestionnaireRunnerAction) : FormRunnerAction

    /** Arquivos escolhidos para uma pergunta `file`. */
    data class PickFiles(val questionId: String, val files: List<FormPickedFile>) : FormRunnerAction

    /** O seletor recusou/falhou antes de haver arquivo (tamanho, leitura, câmera). */
    data class PickFailed(
        val questionId: String,
        val problem: FormUploadProblem,
        val name: String? = null,
        val sizeBytes: Long = -1,
    ) : FormRunnerAction

    data class RetryUpload(val key: String) : FormRunnerAction

    /** O X do anexo: subindo, CANCELA; com falha, descarta a linha. */
    data class DismissUpload(val key: String) : FormRunnerAction

    /** Progresso REAL do envio, 0..1 — vindo do [FormFileUploader]. */
    data class UploadProgress(val key: String, val fraction: Float) : FormRunnerAction

    data class UploadSucceeded(val key: String, val ref: FormFileRef) : FormRunnerAction

    data class UploadFailed(val key: String, val message: String? = null) : FormRunnerAction

    /** A tela atendeu o [FormFocusRequest] de [serial]. */
    data class FocusHandled(val serial: Long) : FormRunnerAction

    /** O `details` do 400/422 do servidor: vale no campo até a pessoa mexer, e leva à etapa dele. */
    data class ServerErrors(val errors: Map<String, String>) : FormRunnerAction
}

/** O que o ViewModel faz depois de uma ação. */
sealed interface FormRunnerEvent {
    /**
     * Uma resposta mudou — vai para a fila ([FormAnswerQueue]); `null` = apagada. Texto e número
     * disparam a cada tecla: a fila espera a pessoa parar de digitar.
     */
    data class Answered(val questionId: String, val value: FormAnswerValue?) : FormRunnerEvent

    data class StepChanged(val sectionId: String) : FormRunnerEvent

    /**
     * O envio LIMPO (obrigatórias visíveis, formato, consentimento), com as respostas JÁ descartadas
     * (só as visíveis). Escoe a fila (`flush()`) antes de chamar a rota de envio, e ponha `busy`.
     */
    data class Submit(val answers: Map<String, FormAnswerValue>) : FormRunnerEvent

    /** "Voltar" na primeira etapa, com [FormRunnerState.exitEnabled]. */
    data object Exited : FormRunnerEvent

    /** Suba o arquivo pela rota do projeto ([FormFileUploader.start]). */
    data class UploadRequested(val request: FormUploadRequest) : FormRunnerEvent

    /** A pessoa cancelou um envio em curso ([FormFileUploader.cancel]). */
    data class UploadCancelled(val key: String) : FormRunnerEvent
}

/** Resultado de [FormRunnerState.reduce]. */
@Immutable
data class FormRunnerUpdate(
    val state: FormRunnerState,
    val events: List<FormRunnerEvent> = emptyList(),
)

/** A regra de navegação, validação e anexo — pura e testada em `FormRunnerReducerTest`. */
internal object FormRunnerReducer {

    fun reduce(state: FormRunnerState, action: FormRunnerAction): FormRunnerUpdate {
        // Resultado assíncrono (anexo, foco) chega mesmo com a tela travada.
        when (action) {
            is FormRunnerAction.FocusHandled -> return focusHandled(state, action.serial)
            is FormRunnerAction.UploadProgress -> return uploadProgress(state, action)
            is FormRunnerAction.UploadSucceeded -> return uploadSucceeded(state, action)
            is FormRunnerAction.UploadFailed -> return uploadFailed(state, action)
            is FormRunnerAction.ServerErrors -> return serverErrors(state, action.errors)
            is FormRunnerAction.Likert -> if (action.action is QuestionnaireRunnerAction.FocusHandled) {
                return focusHandled(state, action.action.serial)
            }
            else -> Unit
        }
        if (state.busy) return FormRunnerUpdate(state)
        return when (action) {
            is FormRunnerAction.Answer -> answer(state, action.questionId, action.value, action.path)
            is FormRunnerAction.Blur -> blur(state, action.questionId)
            FormRunnerAction.Next -> next(state)
            FormRunnerAction.Submit -> submit(state)
            FormRunnerAction.Back -> back(state)
            is FormRunnerAction.GoToStep -> goToStep(state, action.sectionId)
            is FormRunnerAction.Likert -> likert(state, action.action)
            is FormRunnerAction.PickFiles -> pickFiles(state, action.questionId, action.files)
            is FormRunnerAction.PickFailed -> pickFailed(state, action)
            is FormRunnerAction.RetryUpload -> retryUpload(state, action.key)
            is FormRunnerAction.DismissUpload -> dismissUpload(state, action.key)
            is FormRunnerAction.FocusHandled, is FormRunnerAction.UploadProgress, is FormRunnerAction.UploadSucceeded,
            is FormRunnerAction.UploadFailed, is FormRunnerAction.ServerErrors -> FormRunnerUpdate(state)
        }
    }

    // -----------------------------------------------------------------------------------------
    // Resposta
    // -----------------------------------------------------------------------------------------

    private fun answer(state: FormRunnerState, questionId: String, value: FormAnswerValue?, path: String?): FormRunnerUpdate {
        val question = state.schema.findQuestion(questionId) ?: return FormRunnerUpdate(state)
        if (!question.isHandledByClient || questionId in state.lockedQuestions) return FormRunnerUpdate(state)
        return store(state, questionId, normalized(value), path)
    }

    /** Texto vazio e lista vazia são "apagar" — o que vai como `null` no patch. */
    private fun normalized(value: FormAnswerValue?): FormAnswerValue? {
        val json = value?.json ?: return null
        if (json is JsonPrimitive && json.isString && json.content.isEmpty()) return null
        if (json is JsonArray && json.isEmpty()) return null
        return value
    }

    private fun store(state: FormRunnerState, questionId: String, value: FormAnswerValue?, path: String?): FormRunnerUpdate {
        val old = state.answers[questionId]
        val answers = if (value == null) state.answers - questionId else state.answers + (questionId to value)
        val kept = clearFormIssuesOnEdit(state.issues[questionId], path)
        val issues = if (kept.isEmpty()) state.issues - questionId else state.issues + (questionId to kept)
        val next = state.copy(
            answers = answers,
            touched = state.touched + questionId,
            issues = issues,
            dismissedServerErrors = state.dismissedServerErrors + questionId,
        )
        val events = if (old != value) listOf<FormRunnerEvent>(FormRunnerEvent.Answered(questionId, value)) else emptyList()
        return FormRunnerUpdate(next, events)
    }

    private fun blur(state: FormRunnerState, questionId: String): FormRunnerUpdate {
        if (questionId !in state.touched) return FormRunnerUpdate(state)
        val untouched = state.copy(touched = state.touched - questionId)
        val value = state.answers[questionId] ?: return FormRunnerUpdate(untouched)
        val found = state.schema.validateValues(mapOf(questionId to value), state.today).issues
        val issues = if (found.isEmpty()) untouched.issues - questionId else untouched.issues + (questionId to found)
        return FormRunnerUpdate(untouched.copy(issues = issues))
    }

    // -----------------------------------------------------------------------------------------
    // Navegação e envio
    // -----------------------------------------------------------------------------------------

    private fun next(state: FormRunnerState): FormRunnerUpdate {
        val steps = state.steps()
        if (steps.isEmpty()) return FormRunnerUpdate(state)
        val index = state.stepIndex(steps)
        if (index == steps.lastIndex) return submit(state)
        val ids = stepQuestionIds(steps[index], state.scope)
        val validation = state.schema.validateSubmission(state.answers, state.today, state.context, ids, requireConsent = false)
        if (!validation.isValid) return FormRunnerUpdate(mark(state, validation.issues, ids))
        return goTo(mark(state, emptyList(), ids), steps, index + 1, forward = true)
    }

    /**
     * Envio: espera o anexo subir; confere TUDO (no escopo de quem envia) e, com pendência, leva à
     * primeira etapa que a tem; limpo, emite as respostas já descartadas.
     */
    private fun submit(state: FormRunnerState): FormRunnerUpdate {
        if (state.uploads.any { it.isActive }) return FormRunnerUpdate(state.copy(waitingFiles = true))
        val validation = state.schema.validateSubmission(
            state.answers,
            state.today,
            state.context,
            state.scope,
            state.requireConsent,
        )
        if (validation.isValid) {
            return FormRunnerUpdate(
                state.copy(issues = emptyMap(), waitingFiles = false),
                listOf(FormRunnerEvent.Submit(state.schema.pruneAnswers(state.answers, state.context))),
            )
        }
        val first = validation.issues.first().questionId
        val flagged = state.copy(issues = groupFormIssues(validation.issues), focusRequest = nextFocus(state, first))
        return goToQuestion(flagged, first)
    }

    private fun back(state: FormRunnerState): FormRunnerUpdate {
        val steps = state.steps()
        val index = state.stepIndex(steps)
        if (steps.isNotEmpty() && index > 0) return goTo(state, steps, index - 1, forward = false)
        return if (state.exitEnabled) FormRunnerUpdate(state, listOf(FormRunnerEvent.Exited)) else FormRunnerUpdate(state)
    }

    private fun goToStep(state: FormRunnerState, sectionId: String): FormRunnerUpdate {
        val steps = state.steps()
        val target = steps.indexOfFirst { it.sectionId == sectionId }
        if (target < 0) return FormRunnerUpdate(state)
        val index = state.stepIndex(steps)
        if (target == index) return FormRunnerUpdate(state)
        return goTo(state, steps, target, forward = target > index)
    }

    private fun goTo(state: FormRunnerState, steps: List<FormRunnerStep>, target: Int, forward: Boolean): FormRunnerUpdate {
        val destination = steps[target]
        // "Voltar" para uma seção de escala cai na ÚLTIMA pergunta dela: é a que a pessoa acabou de deixar.
        val likertQuestion = if (!forward && destination.section.kind == FormSectionKind.LIKERT) {
            likertQuestions(state, destination).lastOrNull()?.id
        } else {
            null
        }
        return FormRunnerUpdate(
            state.copy(stepId = destination.sectionId, waitingFiles = false, likertQuestionId = likertQuestion),
            listOf(FormRunnerEvent.StepChanged(destination.sectionId)),
        )
    }

    /** Leva à etapa da pergunta (se não for a atual), mantendo os problemas marcados. */
    private fun goToQuestion(state: FormRunnerState, questionId: String): FormRunnerUpdate {
        val steps = state.steps()
        val target = steps.indexOfFirst { step -> step.questions.any { it.id == questionId } }
        val index = state.stepIndex(steps)
        val likertQuestion = steps.getOrNull(target)?.takeIf { it.section.kind == FormSectionKind.LIKERT }?.let { questionId }
        if (target < 0) return FormRunnerUpdate(state)
        if (target == index) return FormRunnerUpdate(state.copy(likertQuestionId = likertQuestion ?: state.likertQuestionId))
        val destination = steps[target]
        return FormRunnerUpdate(
            state.copy(stepId = destination.sectionId, waitingFiles = false, likertQuestionId = likertQuestion),
            listOf(FormRunnerEvent.StepChanged(destination.sectionId)),
        )
    }

    /**
     * Marca os problemas das perguntas conferidas (substitui o que havia DELAS; o resto fica). Com
     * problema, pede o foco no primeiro.
     */
    private fun mark(state: FormRunnerState, found: List<FormAnswerIssue>, checked: List<String>): FormRunnerState {
        val issues = (state.issues - checked.toSet()) + groupFormIssues(found)
        val focus = found.firstOrNull()?.let { nextFocus(state, it.questionId) } ?: state.focusRequest
        return state.copy(issues = issues, focusRequest = focus)
    }

    private fun serverErrors(state: FormRunnerState, errors: Map<String, String>): FormRunnerUpdate {
        val base = state.copy(serverErrors = errors, dismissedServerErrors = emptySet())
        val order = state.schema.sections.flatMap { s -> s.questions.map { it.id } }
        val first = order.firstOrNull { it in errors } ?: return FormRunnerUpdate(base)
        return goToQuestion(base.copy(focusRequest = nextFocus(base, first)), first)
    }

    private fun focusHandled(state: FormRunnerState, serial: Long): FormRunnerUpdate =
        if (state.focusRequest?.serial == serial) FormRunnerUpdate(state.copy(focusRequest = null)) else FormRunnerUpdate(state)

    private fun nextFocus(state: FormRunnerState, questionId: String): FormFocusRequest =
        FormFocusRequest(questionId, (state.focusRequest?.serial ?: 0L) + 1)

    // -----------------------------------------------------------------------------------------
    // Seção de escala — delegada ao QuestionnaireRunner (a MESMA régua e o mesmo ritmo)
    // -----------------------------------------------------------------------------------------

    private fun likert(state: FormRunnerState, action: QuestionnaireRunnerAction): FormRunnerUpdate {
        val steps = state.steps()
        if (steps.isEmpty()) return FormRunnerUpdate(state)
        val index = state.stepIndex(steps)
        val step = steps[index]
        val delegate = state.likertDelegate(step, index) ?: return FormRunnerUpdate(state)
        val update = delegate.runner.reduce(action)
        val nested = update.state

        // Posição, marcas novas (o "faltam" do Continuar) e foco voltam para o estado do formulário.
        var next = state.copy(likertQuestionId = nested.questionId)
        val added = nested.flagged - delegate.runner.flagged
        if (added.isNotEmpty()) {
            next = next.copy(
                issues = next.issues + added.filter { next.issues[it].isNullOrEmpty() }
                    .associateWith { listOf(FormAnswerIssue(it, FormIssueCode.REQUIRED)) },
            )
        }
        if (nested.focusRequest != delegate.runner.focusRequest) {
            next = next.copy(focusRequest = nested.focusRequest?.let { FormFocusRequest(it.questionId, it.serial) })
        }

        return when (val event = update.event) {
            null -> FormRunnerUpdate(next)
            is QuestionnaireRunnerEvent.Answered -> {
                val value = event.item.value?.let { delegate.exactPoint(event.item.questionId, it) }
                store(next, event.item.questionId, value?.let(FormAnswerValue::number), null)
            }
            is QuestionnaireRunnerEvent.Finished -> {
                val cleared = next.copy(issues = next.issues - delegate.questionIds.toSet())
                if (index == steps.lastIndex) submit(cleared) else goTo(cleared, steps, index + 1, forward = true)
            }
            QuestionnaireRunnerEvent.Exited -> back(next)
        }
    }

    // -----------------------------------------------------------------------------------------
    // Anexos — o estado mora aqui para sobreviver à troca de etapa
    // -----------------------------------------------------------------------------------------

    private fun pickFiles(state: FormRunnerState, questionId: String, files: List<FormPickedFile>): FormRunnerUpdate {
        val question = state.schema.findQuestion(questionId) ?: return FormRunnerUpdate(state)
        val config = question.file ?: return FormRunnerUpdate(state)
        if (question.type != FormQuestionType.FILE || questionId in state.lockedQuestions) return FormRunnerUpdate(state)
        val existing = state.answers[questionId]?.fileRefsOrNull().orEmpty().size
        var free = config.maxFiles - existing - state.uploads.count { it.questionId == questionId && it.isActive }
        var sequence = state.uploadSequence
        val uploads = state.uploads.toMutableList()
        val events = ArrayList<FormRunnerEvent>()
        for (file in files) {
            sequence++
            val key = "$questionId#$sequence"
            val problem = when {
                !formFileMatchesAccept(file.name, file.mimeType, config.accept) -> FormUploadProblem.WrongType
                formFileExceedsSize(file.sizeBytes, config.maxSizeMb) -> FormUploadProblem.TooBig
                free <= 0 -> FormUploadProblem.TooMany
                else -> null
            }
            if (problem != null) {
                uploads += FormUpload(key, questionId, file.name, file.sizeBytes, problem = problem)
                continue
            }
            free--
            uploads += FormUpload(key, questionId, file.name, file.sizeBytes, file = file)
            events += FormRunnerEvent.UploadRequested(FormUploadRequest(key, questionId, config.purpose, file))
        }
        return FormRunnerUpdate(state.copy(uploads = uploads, uploadSequence = sequence), events)
    }

    private fun pickFailed(state: FormRunnerState, action: FormRunnerAction.PickFailed): FormRunnerUpdate {
        if (state.schema.findQuestion(action.questionId)?.type != FormQuestionType.FILE) return FormRunnerUpdate(state)
        val sequence = state.uploadSequence + 1
        val row = FormUpload(
            key = "${action.questionId}#$sequence",
            questionId = action.questionId,
            name = action.name.orEmpty(),
            sizeBytes = action.sizeBytes,
            problem = action.problem,
        )
        return FormRunnerUpdate(state.copy(uploads = state.uploads + row, uploadSequence = sequence))
    }

    private fun retryUpload(state: FormRunnerState, key: String): FormRunnerUpdate {
        val upload = state.uploads.firstOrNull { it.key == key } ?: return FormRunnerUpdate(state)
        val file = upload.file ?: return FormRunnerUpdate(state)
        if (!upload.isRetryable) return FormRunnerUpdate(state)
        val purpose = state.schema.findQuestion(upload.questionId)?.file?.purpose ?: return FormRunnerUpdate(state)
        val restarted = upload.copy(problem = null, progress = null)
        return FormRunnerUpdate(
            state.copy(uploads = state.uploads.map { if (it.key == key) restarted else it }),
            listOf(FormRunnerEvent.UploadRequested(FormUploadRequest(key, upload.questionId, purpose, file))),
        )
    }

    private fun dismissUpload(state: FormRunnerState, key: String): FormRunnerUpdate {
        val upload = state.uploads.firstOrNull { it.key == key } ?: return FormRunnerUpdate(state)
        val next = state.copy(uploads = state.uploads - upload)
        return if (upload.isActive) FormRunnerUpdate(next, listOf(FormRunnerEvent.UploadCancelled(key))) else FormRunnerUpdate(next)
    }

    private fun uploadProgress(state: FormRunnerState, action: FormRunnerAction.UploadProgress): FormRunnerUpdate {
        val fraction = if (action.fraction.isNaN()) 0f else action.fraction.coerceIn(0f, 1f)
        if (state.uploads.none { it.key == action.key && it.isActive }) return FormRunnerUpdate(state)
        return FormRunnerUpdate(state.copy(uploads = state.uploads.map { if (it.key == action.key) it.copy(progress = fraction) else it }))
    }

    private fun uploadSucceeded(state: FormRunnerState, action: FormRunnerAction.UploadSucceeded): FormRunnerUpdate {
        // Cancelado ou descartado no meio: ninguém mais espera o `fileId`.
        val upload = state.uploads.firstOrNull { it.key == action.key && it.isActive } ?: return FormRunnerUpdate(state)
        if (!isCanonicalFileId(action.ref.fileId)) {
            return uploadFailed(state, FormRunnerAction.UploadFailed(action.key))
        }
        val refs = state.answers[upload.questionId]?.fileRefsOrNull().orEmpty().filter { it.fileId != action.ref.fileId }
        val base = state.copy(
            uploads = state.uploads - upload,
            fileNames = state.fileNames + (action.ref.fileId to upload.name),
        )
        val stored = store(base, upload.questionId, FormAnswerValue.files(refs + action.ref), null)
        // Subiu o último anexo que o "Enviar" esperava: a frase de espera sai.
        val waiting = stored.state.waitingFiles && stored.state.uploads.any { it.isActive }
        return stored.copy(state = stored.state.copy(waitingFiles = waiting))
    }

    private fun uploadFailed(state: FormRunnerState, action: FormRunnerAction.UploadFailed): FormRunnerUpdate {
        if (state.uploads.none { it.key == action.key && it.isActive }) return FormRunnerUpdate(state)
        val failed = FormUploadProblem.UploadFailed(action.message)
        val next = state.copy(uploads = state.uploads.map { if (it.key == action.key) it.copy(problem = failed, progress = null) else it })
        return FormRunnerUpdate(next.copy(waitingFiles = next.waitingFiles && next.uploads.any { it.isActive }))
    }
}

// ---------------------------------------------------------------------------------------------
// A seção `likert` como um QuestionnaireRunner de um bloco só
// ---------------------------------------------------------------------------------------------

/**
 * O `QuestionnaireRunnerState` DERIVADO de uma etapa `likert` — nunca guardado: o estado do
 * formulário é a fonte única (posição, marcas, foco, respostas), e o derivado é refeito a cada
 * composição e a cada ação. Assim, uma recarga das respostas pelo ViewModel (`copy(answers = …)`)
 * nunca deixa a régua mostrando a resposta velha.
 */
internal class FormLikertDelegate(
    val runner: QuestionnaireRunnerState,
    val questionIds: List<String>,
    private val exactPoints: Map<String, List<FormDecimal>>,
    private val displayPoints: Map<String, List<Double>>,
) {
    /** O ponto EXATO da régua do schema que corresponde ao ponto que o runner devolveu. */
    fun exactPoint(questionId: String, value: QuestionnaireValue): FormDecimal? {
        val number = (value as? QuestionnaireValue.Number)?.value ?: return null
        val index = displayPoints[questionId]?.indexOfFirst { abs(it - number) < 1e-6 } ?: return null
        return exactPoints[questionId]?.getOrNull(index)
    }
}

/**
 * A etapa como `QuestionnaireRunnerState`, ou `null` quando não dá para delegar (régua impossível,
 * todas as perguntas travadas): aí a tela desenha a seção campo a campo, com o aviso no campo.
 */
internal fun FormRunnerState.likertDelegate(step: FormRunnerStep, stepIndex: Int): FormLikertDelegate? {
    if (step.section.kind != FormSectionKind.LIKERT) return null
    val questions = likertQuestions(this, step)
    if (questions.isEmpty()) return null
    val exact = HashMap<String, List<FormDecimal>>()
    val display = HashMap<String, List<Double>>()
    val converted = questions.map { question ->
        val scale = formLikertScaleOf(question, step.section) ?: return null
        val points = scale.points()
        val asQuestionnaire = scale.toQuestionnaireScale()
        val shown = asQuestionnaire.points()
        // Régua impossível (ou que o Double não reproduz ponto a ponto): sem delegar.
        if (points.isEmpty() || points.size != shown.size) return null
        exact[question.id] = points
        display[question.id] = shown
        QuestionnaireQuestion(
            id = question.id,
            text = question.text,
            required = question.required,
            hint = question.hint,
            scale = if (question.scale != null) asQuestionnaire else null,
        )
    }
    val sectionScale = step.section.scale?.toQuestionnaireScale()
    val ids = converted.map { it.id }
    val answers = ids.mapNotNull { id ->
        answers[id]?.numberOrNull()?.let { exact[id]?.indexOf(it) }?.takeIf { it >= 0 }?.let { index ->
            id to QuestionnaireValue.Number(display.getValue(id)[index])
        }
    }.toMap()
    val runner = QuestionnaireRunnerState(
        questionnaire = Questionnaire(
            blocks = listOf(QuestionnaireBlock(id = step.sectionId, title = step.section.title, context = step.section.context, questions = converted)),
            scale = sectionScale,
        ),
        answers = answers,
        blockId = step.sectionId,
        questionId = likertQuestionId?.takeIf { it in ids },
        flagged = ids.filter { !issues[it].isNullOrEmpty() || serverErrorOf(it) != null }.toSet(),
        focusRequest = focusRequest?.takeIf { it.questionId in ids }?.let { QuestionnaireFocusRequest(it.questionId, it.serial) },
        busy = busy,
        exitEnabled = stepIndex > 0 || exitEnabled,
    )
    return FormLikertDelegate(runner, ids, exact, display)
}

/** As perguntas de uma etapa `likert` que esta tela responde (não `info`, conhecidas, não travadas). */
internal fun likertQuestions(state: FormRunnerState, step: FormRunnerStep): List<FormQuestion> =
    step.questions.filter { it.isHandledByClient && it.id !in state.lockedQuestions }

/** A régua do schema no formato do `QuestionnaireRunner` (o mesmo campo a campo, em `Double`). */
internal fun FormLikertScale.toQuestionnaireScale(): QuestionnaireScale = QuestionnaireScale(
    min = min.toDouble(),
    max = max.toDouble(),
    step = (step ?: FormDecimal.ONE).toDouble(),
    optionLabels = optionLabels,
    startAnchor = startAnchor,
    endAnchor = endAnchor,
)
