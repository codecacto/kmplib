package br.com.codecacto.kmplib.ui.questionnaire

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.motion.LocalReduceMotion
import br.com.codecacto.kmplib.ui.components.AppBanner
import br.com.codecacto.kmplib.ui.components.AppButton
import br.com.codecacto.kmplib.ui.components.AppDatePicker
import br.com.codecacto.kmplib.ui.components.AppOutlinedButton
import br.com.codecacto.kmplib.ui.components.AppTextArea
import br.com.codecacto.kmplib.ui.components.AppTextField
import br.com.codecacto.kmplib.ui.components.FormContainer
import br.com.codecacto.kmplib.ui.components.LikertOptionState
import br.com.codecacto.kmplib.ui.components.LikertScaleDefaults
import br.com.codecacto.kmplib.ui.components.LikertScaleField
import br.com.codecacto.kmplib.ui.components.NumberField
import br.com.codecacto.kmplib.ui.components.StatusChipStyle
import br.com.codecacto.kmplib.ui.components.StatusTone
import br.com.codecacto.kmplib.ui.components.TextInputReconciler
import br.com.codecacto.kmplib.ui.components.likertOptionBorderWidth
import br.com.codecacto.kmplib.ui.components.likertOptionState
import br.com.codecacto.kmplib.ui.components.statusChipColors
import br.com.codecacto.kmplib.ui.components.statusToneColor
import br.com.codecacto.kmplib.ui.theme.AppTheme
import br.com.codecacto.kmplib.ui.theme.LocalIsCompact
import br.com.codecacto.kmplib.ui.theme.LocalWindowSizeClass
import br.com.codecacto.kmplib.ui.theme.leituraMaxWidth
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.ui.tooling.preview.Preview

private const val TAG = "QuestionnaireRunner"

/**
 * **A tela de responder um questionário** — o `QuestionnaireRunner` da weblib, no app: bloco a bloco
 * no celular, pergunta a pergunta em tela larga, sobre o MESMO JSON do servidor (ver
 * [Questionnaire]). Genérico: anamnese, pré-consulta, escala validada, pesquisa de satisfação.
 *
 * ## O ritmo é do tamanho da tela
 * [pace] `Auto` (default): **agrupado** em `LocalIsCompact` (as perguntas do bloco numa tela, numeradas)
 * e **uma por vez** fora dele (com avanço automático ao escolher, [autoAdvance], 260 ms depois —
 * nunca na última do bloco: concluir o bloco é decisão da pessoa).
 *
 * ## O primário não é um botão morto
 * Bloco incompleto NÃO desabilita "Continuar": o toque marca as pendentes NO CAMPO, rola até a
 * primeira e tenta o foco nela; "Faltam N perguntas" é dito FORA do botão, em região viva. O erro só
 * aparece depois do envio e sai da pergunta assim que ela é editada. "Concluir" confere o
 * questionário inteiro antes de emitir [QuestionnaireRunnerEvent.Finished].
 *
 * ## Estado do ViewModel (MVI)
 * O componente é STATELESS: desenha [state] e devolve [QuestionnaireRunnerAction]s; o ViewModel
 * aplica [QuestionnaireRunnerState.reduce] e trata o [QuestionnaireRunnerEvent] (salvar, concluir,
 * sair). A fila de salvamento é a [QuestionnaireAnswerQueue].
 *
 * ## Reservada e "quem respondeu"
 * Pergunta (ou bloco) `reserved` sem resposta leva a marca [QuestionnaireTexts.reservedPending] — o
 * servidor já tirou do documento quem não pode vê-la. Resposta de outro respondente leva
 * "Respondida por …" quando [respondentName] devolve um nome para a chave de
 * [QuestionnaireRunnerState.filledBy].
 *
 * ## Escores
 * Com [showScores], cada escore aparece ao fim do bloco da sua última pergunta: valor + faixa (tom do
 * tema), ou "incompleto" — escore parcial não ganha faixa. Interpretação é decisão de quem usa: na
 * tela de quem responde por si, deixe `false`.
 *
 * ```kotlin
 * Scaffold(topBar = { … }) { innerPadding ->
 *     QuestionnaireRunner(
 *         state = state.questionario,
 *         onAction = { vm.onAction(TriagemAction.Questionario(it)) },
 *         contentPadding = innerPadding,
 *         notice = textos.unsavedAnswers.takeIf { state.pendenteDeSalvar },
 *         respondentName = { chave -> nomes[chave] },
 *     )
 * }
 * ```
 *
 * @param contentPadding o `innerPadding` do `Scaffold` — aplicado E consumido (ver `FormContainer`).
 * @param notice aviso persistente acima das perguntas (ex.: resposta ainda não salva).
 * @param respondentName nome exibível de uma chave de respondente; `null` = sem marca.
 */
@Composable
fun QuestionnaireRunner(
    state: QuestionnaireRunnerState,
    onAction: (QuestionnaireRunnerAction) -> Unit,
    modifier: Modifier = Modifier,
    pace: QuestionnairePace = QuestionnairePace.Auto,
    autoAdvance: Boolean = true,
    notice: String? = null,
    showScores: Boolean = false,
    respondentName: (String) -> String? = { null },
    contentPadding: PaddingValues = PaddingValues(0.dp),
    texts: QuestionnaireTexts = rememberQuestionnaireTexts(),
) {
    val evaluation = remember(state.questionnaire, state.answers, state.context) { state.evaluate() }
    val compact = LocalIsCompact.current
    val oneByOne = pace.isOneByOne(compact)
    val position = remember(evaluation, state.blockId, state.questionId) { state.position(evaluation) }
    val latestOnAction by rememberUpdatedState(onAction)
    val scope = rememberCoroutineScope()
    val autoAdvanceJob = remember { AutoAdvanceJob() }
    val reduceMotion = LocalReduceMotion.current

    LaunchedEffect(state.questionnaire) {
        state.questionnaire.validate().forEach { issue -> AppLogger.w(TAG, "${issue.path}: ${issue.message}") }
    }

    val onTap: (QuestionnaireQuestion, QuestionnaireValue) -> Unit = { question, value ->
        latestOnAction(QuestionnaireRunnerAction.Answer(question.id, value))
        val tapType = question.type == QuestionnaireQuestionType.SCALE || question.type == QuestionnaireQuestionType.CHOICE
        if (oneByOne && autoAdvance && tapType) {
            autoAdvanceJob.job?.cancel()
            autoAdvanceJob.job = scope.launch {
                delay(QuestionnaireDefaults.AutoAdvanceDelayMillis)
                latestOnAction(QuestionnaireRunnerAction.AutoAdvance(question.id))
            }
        }
    }

    val step = PageKey(position?.block?.id, if (oneByOne) position?.question?.id else null)

    // A página inteira troca por `key`: cada passo nasce com a rolagem no topo (o "Continuar" fica
    // no fim, e o bloco seguinte abriria rolado até lá). Quem pediu menos movimento não vê deslize.
    AnimatedContent(
        targetState = step,
        modifier = modifier.testTag(QuestionnaireTestTags.ROOT),
        transitionSpec = { pageTransition(reduceMotion) },
        label = "questionario-passo",
    ) { page ->
        QuestionnairePage(
            page = page,
            state = state,
            evaluation = evaluation,
            oneByOne = oneByOne,
            compact = compact,
            notice = notice,
            showScores = showScores,
            respondentName = respondentName,
            contentPadding = contentPadding,
            texts = texts,
            reduceMotion = reduceMotion,
            onAction = { latestOnAction(it) },
            onTap = onTap,
        )
    }
}

/** A chave da página: o bloco e, no ritmo pergunta a pergunta, a pergunta. */
@Immutable
private data class PageKey(val blockId: String?, val questionId: String?)

private class AutoAdvanceJob {
    var job: Job? = null
}

private fun pageTransition(reduceMotion: Boolean): ContentTransform =
    if (reduceMotion) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        (fadeIn(tween(durationMillis = 220, delayMillis = 60)) + slideInVertically(tween(260)) { it / 40 }) togetherWith
            fadeOut(tween(durationMillis = 90))
    }

@Composable
private fun QuestionnairePage(
    page: PageKey,
    state: QuestionnaireRunnerState,
    evaluation: QuestionnaireEvaluation,
    oneByOne: Boolean,
    compact: Boolean,
    notice: String?,
    showScores: Boolean,
    respondentName: (String) -> String?,
    contentPadding: PaddingValues,
    texts: QuestionnaireTexts,
    reduceMotion: Boolean,
    onAction: (QuestionnaireRunnerAction) -> Unit,
    onTap: (QuestionnaireQuestion, QuestionnaireValue) -> Unit,
) {
    // Desenha a página da CHAVE, não do estado atual: durante a transição a página que sai continua
    // composta, e com o estado atual ela mostraria o bloco novo também.
    val blocks = evaluation.visibleBlocks
    val blockIndex = blocks.indexOfFirst { it.id == page.blockId }
    val block = blocks.getOrNull(blockIndex)
    val navigable = block?.let { evaluation.navigableQuestions(it) }.orEmpty()
    val questionIndex = if (oneByOne) navigable.indexOfFirst { it.id == page.questionId } else -1
    val shown: List<QuestionnaireQuestion> = when {
        block == null -> emptyList()
        oneByOne -> listOfNotNull(navigable.getOrNull(questionIndex))
        else -> evaluation.visibleQuestions(block)
    }
    val isLastBlock = blockIndex >= blocks.lastIndex
    val isLastStep = isLastBlock && (!oneByOne || questionIndex >= navigable.lastIndex)
    val isFirstStep = blockIndex <= 0 && (!oneByOne || questionIndex <= 0)

    FormContainer(
        contentPadding = contentPadding,
        maxContentWidth = leituraMaxWidth(LocalWindowSizeClass.current),
        horizontalPadding = if (compact) 16.dp else 24.dp,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (block != null) {
            val stepLabel = if (oneByOne && questionIndex >= 0) {
                texts.blockProgress(blockIndex + 1, blocks.size) + " · " +
                    texts.questionProgress(questionIndex + 1, navigable.size)
            } else {
                texts.blockProgress(blockIndex + 1, blocks.size)
            }
            PageHeader(stepLabel, block)
        }

        ProgressSection(evaluation.progress, texts, reduceMotion)

        if (!notice.isNullOrBlank()) {
            AppBanner(
                message = notice,
                tone = StatusTone.WARNING,
                modifier = Modifier.fillMaxWidth().testTag(QuestionnaireTestTags.NOTICE),
            )
        }

        if (block != null && block.reserved && navigable.any { evaluation.answerOf(it.id) == null }) {
            QuestionnaireMark(
                text = texts.reservedPending,
                tone = StatusTone.WARNING,
                icon = Icons.Outlined.Schedule,
                testTag = QuestionnaireTestTags.BLOCK_RESERVED,
            )
        }

        shown.forEachIndexed { index, question ->
            key(question.id) {
                QuestionItem(
                    question = question,
                    number = if (oneByOne) null else index + 1,
                    block = block!!,
                    state = state,
                    evaluation = evaluation,
                    compact = compact,
                    respondentName = respondentName,
                    texts = texts,
                    onAction = onAction,
                    onTap = onTap,
                )
            }
        }

        if (!oneByOne && block != null) {
            val pending = evaluation.pendingIn(block).size
            // O que falta é dito FORA do botão, e anunciado.
            Text(
                text = if (pending > 0) texts.missing(pending) else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 20.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(QuestionnaireTestTags.MISSING),
            )
        }

        if (showScores && block != null && (!oneByOne || questionIndex == navigable.lastIndex)) {
            evaluation.scoresOwnedBy(block).forEach { result -> ScoreSummary(result, texts) }
        }

        Actions(
            isFirstStep = isFirstStep,
            isLastStep = isLastStep,
            busy = state.busy,
            exitEnabled = state.exitEnabled,
            compact = compact,
            texts = texts,
            onNext = { onAction(QuestionnaireRunnerAction.Next(oneByOne)) },
            onBack = { onAction(QuestionnaireRunnerAction.Back(oneByOne)) },
        )
    }
}

@Composable
private fun PageHeader(stepLabel: String, block: QuestionnaireBlock) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stepLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(QuestionnaireTestTags.STEP),
        )
        Text(
            text = block.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }.testTag(QuestionnaireTestTags.BLOCK_TITLE),
        )
        block.context?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProgressSection(progress: QuestionnaireProgress, texts: QuestionnaireTexts, reduceMotion: Boolean) {
    val label = texts.answered(progress.answered, progress.total)
    val fraction by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 300),
        label = "questionario-progresso",
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                // O leitor de tela ouve "12 de 28 respondidas", não "43 por cento".
                .semantics { stateDescription = label }
                .testTag(QuestionnaireTestTags.PROGRESS),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Já é o estado da barra; lido duas vezes, vira ruído.
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuestionItem(
    question: QuestionnaireQuestion,
    number: Int?,
    block: QuestionnaireBlock,
    state: QuestionnaireRunnerState,
    evaluation: QuestionnaireEvaluation,
    compact: Boolean,
    respondentName: (String) -> String?,
    texts: QuestionnaireTexts,
    onAction: (QuestionnaireRunnerAction) -> Unit,
    onTap: (QuestionnaireQuestion, QuestionnaireValue) -> Unit,
) {
    val bringIntoView = remember { BringIntoViewRequester() }
    val focusRequester = remember { FocusRequester() }
    val request = state.focusRequest
    LaunchedEffect(request) {
        if (request != null && request.questionId == question.id) {
            // Um quadro: a página pode ter acabado de nascer, e sem medida não há para onde rolar.
            withFrameNanos { }
            bringIntoView.bringIntoView()
            // Campo de texto recebe o foco; opção de escolha, em modo toque, não é focável — aí
            // valem a rolagem e a mensagem no campo.
            runCatching { focusRequester.requestFocus() }
            onAction(QuestionnaireRunnerAction.FocusHandled(request.serial))
        }
    }

    val enabled = !state.busy
    val error = if (question.id in state.flagged) evaluation.errorOf(question.id) else null
    val errorText = error?.let { texts.errorText(question.type, it, question.decimals.coerceAtLeast(0)) }
    val usable = evaluation.answerOf(question.id)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .focusRequester(focusRequester)
            .testTag(QuestionnaireTestTags.question(question.id)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuestionHeader(question, number, texts)

        if (question.reserved && !block.reserved && usable == null) {
            QuestionnaireMark(
                text = texts.reservedPending,
                tone = StatusTone.WARNING,
                icon = Icons.Outlined.Schedule,
                testTag = QuestionnaireTestTags.reserved(question.id),
            )
        }
        val filler = state.filledBy[question.id]
        if (filler != null && filler != state.respondent && usable != null) {
            respondentName(filler)?.let { name ->
                QuestionnaireMark(
                    text = texts.filledBy(name),
                    tone = StatusTone.NEUTRAL,
                    icon = Icons.Outlined.Person,
                    testTag = QuestionnaireTestTags.filledBy(question.id),
                )
            }
        }

        if (!state.questionnaire.isAnswerable(question)) {
            UnavailableNotice(texts.unavailable)
            return@Column
        }

        when (question.type) {
            QuestionnaireQuestionType.SCALE -> ScaleAnswer(
                question = question,
                scale = state.questionnaire.scaleOf(question) ?: QuestionnaireScale(),
                selected = (usable as? QuestionnaireValue.Number)?.value,
                compact = compact,
                enabled = enabled,
                errorText = errorText,
                texts = texts,
                onTap = onTap,
            )
            QuestionnaireQuestionType.CHOICE -> OptionList(
                questionId = question.id,
                rows = question.options.map { OptionRow(value = it.value, leading = null, label = it.displayLabel, description = null) },
                multi = false,
                selected = setOfNotNull((usable as? QuestionnaireValue.Text)?.value),
                enabled = enabled,
                errorText = errorText,
                texts = texts,
                onToggle = { value -> onTap(question, QuestionnaireValue.Text(value)) },
            )
            QuestionnaireQuestionType.MULTI_CHOICE -> {
                val current = (usable as? QuestionnaireValue.Choices)?.values.orEmpty()
                OptionList(
                    questionId = question.id,
                    rows = question.options.map { OptionRow(value = it.value, leading = null, label = it.displayLabel, description = null) },
                    multi = true,
                    selected = current.toSet(),
                    enabled = enabled,
                    errorText = errorText,
                    texts = texts,
                    onToggle = { value ->
                        val next = if (value in current) current - value else current + value
                        onAction(QuestionnaireRunnerAction.Answer(question.id, QuestionnaireValue.Choices(next)))
                    },
                )
            }
            QuestionnaireQuestionType.NUMBER -> {
                val text = state.numberDrafts[question.id]
                    ?: (state.answers[question.id] as? QuestionnaireValue.Number)?.value?.let(::questionnaireNumberFieldText)
                    ?: ""
                NumberField(
                    value = text,
                    onValueChange = { onAction(QuestionnaireRunnerAction.EditNumber(question.id, it)) },
                    modifier = Modifier
                        .testTag(QuestionnaireTestTags.answer(question.id))
                        .then(if (question.unit.isNullOrBlank()) Modifier.semantics { contentDescription = question.text } else Modifier),
                    label = question.unit?.takeIf { it.isNotBlank() },
                    allowDecimals = question.decimals > 0,
                    errorMessage = errorText,
                    enabled = enabled,
                )
            }
            QuestionnaireQuestionType.TEXT -> TextAnswer(question, state, enabled, errorText, onAction)
            QuestionnaireQuestionType.DATE -> {
                val date = (usable as? QuestionnaireValue.Text)?.value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                AppDatePicker(
                    selectedDate = date,
                    onDateSelected = { onAction(QuestionnaireRunnerAction.Answer(question.id, QuestionnaireValue.Text(it.toString()))) },
                    label = texts.dateLabel,
                    modifier = Modifier.fillMaxWidth().testTag(QuestionnaireTestTags.answer(question.id)),
                    isEnabled = enabled,
                    errorMessage = errorText,
                )
            }
            QuestionnaireQuestionType.UNSUPPORTED -> UnavailableNotice(texts.unavailable)
        }
    }
}

@Composable
private fun QuestionHeader(question: QuestionnaireQuestion, number: Int?, texts: QuestionnaireTexts) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (number != null) {
                Text(
                    text = "$number.",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // O número é posição visual; o leitor de tela já anda pela lista.
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
            Text(
                text = question.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        if (!question.required) {
            Text(text = texts.optional, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        question.hint?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A régua. Em linha (o `LikertScaleField`, com âncoras) quando os pontos cabem como alvo; EMPILHADA,
 * com o rótulo de cada ponto, no celular quando o instrumento nomeia os pontos ("Mais da metade dos
 * dias" não cabe embaixo de um número em 360 dp — e é a mesma escolha da weblib) e sempre que a régua
 * não é de inteiros consecutivos.
 */
@Composable
private fun ScaleAnswer(
    question: QuestionnaireQuestion,
    scale: QuestionnaireScale,
    selected: Double?,
    compact: Boolean,
    enabled: Boolean,
    errorText: String?,
    texts: QuestionnaireTexts,
    onTap: (QuestionnaireQuestion, QuestionnaireValue) -> Unit,
) {
    val points = scale.points()
    val labels = scale.optionLabels
    val integers = points.all { it == floor(it) && abs(it) < 1e9 } && points.zipWithNext().all { (a, b) -> b - a == 1.0 }
    val stacked = (compact && labels.any { it.isNotBlank() }) || !integers || points.size > LikertScaleDefaults.MaxPoints
    if (!stacked) {
        LikertScaleField(
            value = selected?.toInt(),
            onValueChange = { onTap(question, QuestionnaireValue.Number(it.toDouble())) },
            min = points.first().toInt(),
            max = points.last().toInt(),
            optionLabels = labels,
            startAnchor = scale.startAnchor.orEmpty(),
            endAnchor = scale.endAnchor.orEmpty(),
            enabled = enabled,
            isError = errorText != null,
            errorMessage = errorText,
            texts = texts.likert,
            testTag = QuestionnaireTestTags.answer(question.id),
        )
        return
    }
    val rows = points.mapIndexed { index, point ->
        val label = labels.getOrNull(index)?.takeIf { it.isNotBlank() }
        val position = "${texts.likert.option} ${index + 1} ${texts.likert.of} ${points.size}"
        OptionRow(
            value = scalarOf(QuestionnaireValue.Number(point)).orEmpty(),
            leading = formatQuestionnaireNumber(point, maxFractionDigits = 6),
            label = label,
            description = if (label != null) "$label, $position" else position.replaceFirstChar { it.uppercaseChar() },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OptionList(
            questionId = question.id,
            rows = rows,
            multi = false,
            selected = setOfNotNull(selected?.let { scalarOf(QuestionnaireValue.Number(it)) }),
            enabled = enabled,
            errorText = errorText,
            texts = texts,
            onToggle = { value -> value.toDoubleOrNull()?.let { onTap(question, QuestionnaireValue.Number(it)) } },
        )
        // Sem rótulo por ponto, as âncoras dizem o que as pontas significam.
        if (labels.none { it.isNotBlank() } && (!scale.startAnchor.isNullOrBlank() || !scale.endAnchor.isNullOrBlank())) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(scale.startAnchor.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    scale.endAnchor.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Immutable
private data class OptionRow(val value: String, val leading: String?, val label: String?, val description: String?)

/**
 * Lista de alternativas — radiogroup (`selectableGroup` + `Role.RadioButton`) ou caixas
 * (`Role.Checkbox`), uma por linha de no mínimo 48 dp, com o estado dito por cor E por borda dupla E
 * por peso do texto (a mesma redundância do `LikertScaleField`).
 */
@Composable
private fun OptionList(
    questionId: String,
    rows: List<OptionRow>,
    multi: Boolean,
    selected: Set<String>,
    enabled: Boolean,
    errorText: String?,
    texts: QuestionnaireTexts,
    onToggle: (String) -> Unit,
) {
    val unanswered = selected.isEmpty()
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (multi) Modifier else Modifier.selectableGroup())
                .semantics {
                    if (unanswered) stateDescription = texts.likert.unanswered
                    if (errorText != null) error(errorText)
                }
                .testTag(QuestionnaireTestTags.answer(questionId)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rows.forEach { row ->
                OptionRowItem(
                    questionId = questionId,
                    row = row,
                    multi = multi,
                    isSelected = row.value in selected,
                    enabled = enabled,
                    isError = errorText != null,
                    onToggle = onToggle,
                )
            }
        }
        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(QuestionnaireTestTags.error(questionId)),
            )
        }
    }
}

@Composable
private fun OptionRowItem(
    questionId: String,
    row: OptionRow,
    multi: Boolean,
    isSelected: Boolean,
    enabled: Boolean,
    isError: Boolean,
    onToggle: (String) -> Unit,
) {
    val visual = likertOptionState(selected = isSelected, enabled = enabled, isError = isError)
    val scheme = MaterialTheme.colorScheme
    val disabledAlpha = LikertScaleDefaults.DisabledContentAlpha
    val container: Color = when (visual) {
        LikertOptionState.Selected, LikertOptionState.SelectedDisabled ->
            scheme.primary.copy(alpha = LikertScaleDefaults.SelectedContainerAlpha)
        LikertOptionState.Disabled -> scheme.surfaceVariant
        else -> scheme.surface
    }
    val borderColor: Color = when (visual) {
        LikertOptionState.Selected -> scheme.primary
        LikertOptionState.SelectedDisabled -> scheme.primary.copy(alpha = disabledAlpha)
        LikertOptionState.UnselectedError -> scheme.error
        LikertOptionState.Disabled -> scheme.outlineVariant
        LikertOptionState.Unselected -> scheme.outline
    }
    val textColor = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = disabledAlpha)
    val shape = MaterialTheme.shapes.small
    val interaction = if (multi) {
        Modifier.toggleable(value = isSelected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle(row.value) })
    } else {
        Modifier.selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = { onToggle(row.value) })
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LikertScaleDefaults.MinTouchTarget)
            .clip(shape)
            .background(container)
            .border(likertOptionBorderWidth(visual), borderColor, shape)
            .then(interaction)
            .then(if (row.description != null) Modifier.semantics { contentDescription = row.description } else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(QuestionnaireTestTags.option(questionId, row.value)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // onClick/onCheckedChange nulos: quem recebe o toque é a LINHA inteira (alvo grande, um nó
        // só para o leitor de tela) — o padrão oficial do Compose para grupo de rádio/caixa.
        if (multi) {
            Checkbox(checked = isSelected, onCheckedChange = null, enabled = enabled)
        } else {
            RadioButton(selected = isSelected, onClick = null, enabled = enabled)
        }
        row.leading?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected && enabled) scheme.primary else textColor,
            )
        }
        row.label?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Texto: uma linha no `AppTextField` (texto local), várias no `AppTextArea` (texto local também). */
@Composable
private fun TextAnswer(
    question: QuestionnaireQuestion,
    state: QuestionnaireRunnerState,
    enabled: Boolean,
    errorText: String?,
    onAction: (QuestionnaireRunnerAction) -> Unit,
) {
    val stored = (state.answers[question.id] as? QuestionnaireValue.Text)?.value.orEmpty()
    val emit: (String) -> Unit = { onAction(QuestionnaireRunnerAction.Answer(question.id, QuestionnaireValue.Text(it))) }
    val tag = Modifier
        .testTag(QuestionnaireTestTags.answer(question.id))
        .semantics { contentDescription = question.text }
    if (question.multiline) {
        // O texto mora no campo (o eco atrasado do ViewModel não apaga letra no iOS) — a mesma regra
        // do `keepTextLocally` do AppTextField, que o AppTextArea não tem.
        val reconciler = remember { TextInputReconciler(stored) }
        var local by remember { mutableStateOf(stored) }
        val latestEmit by rememberUpdatedState(emit)
        SideEffect { reconciler.onExternal(stored, local)?.let { local = it } }
        AppTextArea(
            value = local,
            onValueChange = { text ->
                local = text
                if (reconciler.onLocalText(text)) latestEmit(text)
            },
            modifier = tag,
            maxLength = question.maxLength,
            showCharCounter = question.maxLength != null,
            errorMessage = errorText,
            enabled = enabled,
        )
    } else {
        AppTextField(
            value = stored,
            onValueChange = emit,
            modifier = tag,
            maxLength = question.maxLength,
            showCharCounter = question.maxLength != null,
            errorMessage = errorText,
            enabled = enabled,
            keepTextLocally = true,
        )
    }
}

@Composable
private fun UnavailableNotice(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Marca curta (reservada, quem respondeu, faixa de escore) — as cores do `StatusChip` (contraste AA
 * garantido sobre [surface]), mas o texto QUEBRA linha em vez de virar reticência: com fonte grande,
 * "Pendente — perguntar na consulta" cortado no meio não diz nada.
 */
@Composable
private fun QuestionnaireMark(
    text: String,
    tone: StatusTone,
    icon: ImageVector?,
    testTag: String,
    surface: Color = MaterialTheme.colorScheme.surface,
) {
    val toneColor = statusToneColor(tone)
    val (content, background) = remember(toneColor, surface) { statusChipColors(toneColor, StatusChipStyle.TINTED, surface) }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { }
            .testTag(testTag),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

@Composable
private fun ScoreSummary(result: QuestionnaireScoreResult, texts: QuestionnaireTexts) {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = surface,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { }
            .testTag(QuestionnaireTestTags.score(result.score.id)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = result.score.label ?: result.score.id,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (result.isComplete) {
                    Text(
                        text = formatQuestionnaireNumber(result.value),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    // Escore parcial não mostra número nem faixa: 2 de 3 itens não dizem "negativo".
                    Text(
                        text = texts.scoreIncomplete(result.answered, result.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val band = result.band
            if (result.isComplete && band != null) {
                QuestionnaireMark(
                    text = band.label,
                    tone = band.tone.toStatusTone(),
                    icon = null,
                    testTag = QuestionnaireTestTags.score(result.score.id) + "-faixa",
                    surface = surface,
                )
            }
        }
    }
}

@Composable
private fun Actions(
    isFirstStep: Boolean,
    isLastStep: Boolean,
    busy: Boolean,
    exitEnabled: Boolean,
    compact: Boolean,
    texts: QuestionnaireTexts,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val primary = if (isLastStep) (if (busy) texts.finishing else texts.finish) else texts.next
    val showBack = !isFirstStep || exitEnabled
    if (compact) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton(text = primary, onClick = onNext, enabled = !busy, modifier = Modifier.testTag(QuestionnaireTestTags.NEXT))
            if (showBack) {
                AppOutlinedButton(text = texts.back, onClick = onBack, enabled = !busy, modifier = Modifier.testTag(QuestionnaireTestTags.BACK))
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(modifier = Modifier.weight(1f))
            if (showBack) {
                AppOutlinedButton(
                    text = texts.back,
                    onClick = onBack,
                    enabled = !busy,
                    modifier = Modifier.weight(1f).testTag(QuestionnaireTestTags.BACK),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            AppButton(
                text = primary,
                onClick = onNext,
                enabled = !busy,
                modifier = Modifier.weight(1f).testTag(QuestionnaireTestTags.NEXT),
            )
        }
    }
}

/** O tom de uma faixa no vocabulário do tema. */
fun QuestionnaireTone.toStatusTone(): StatusTone = when (this) {
    QuestionnaireTone.NEUTRAL -> StatusTone.NEUTRAL
    QuestionnaireTone.SUCCESS -> StatusTone.SUCCESS
    QuestionnaireTone.INFO -> StatusTone.INFO
    QuestionnaireTone.WARNING -> StatusTone.WARNING
    QuestionnaireTone.DANGER -> StatusTone.DANGER
}

/**
 * O número no formato do `NumberField` (vírgula decimal, sem milhar, sem notação científica) — o que
 * aparece no campo quando a resposta vem do servidor.
 */
internal fun questionnaireNumberFieldText(value: Double): String {
    if (!value.isFinite()) return ""
    val rounded = roundQuestionnaireNumber(value)
    if (abs(rounded) >= 1e12) return rounded.toString().replace('.', ',')
    val scaled = round(abs(rounded) * 1_000_000.0).toLong()
    val integer = scaled / 1_000_000
    val fraction = scaled % 1_000_000
    val body = if (fraction == 0L) integer.toString() else "$integer," + fraction.toString().padStart(6, '0').trimEnd('0')
    return if (rounded < 0) "-$body" else body
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun QuestionnaireRunnerPreview() {
    val questionnaire = Questionnaire(
        scale = QuestionnaireScale(
            min = 0.0,
            max = 3.0,
            optionLabels = listOf("Nenhuma vez", "Vários dias", "Mais da metade dos dias", "Quase todos os dias"),
        ),
        blocks = listOf(
            QuestionnaireBlock(
                id = "humor",
                title = "Humor",
                context = "Nas últimas duas semanas, com que frequência você se incomodou com:",
                questions = listOf(
                    QuestionnaireQuestion(id = "q1", text = "Pouco interesse ou prazer em fazer as coisas"),
                    QuestionnaireQuestion(id = "q2", text = "Sentir-se para baixo, deprimido(a) ou sem perspectiva", reserved = true),
                ),
            ),
        ),
        scores = listOf(QuestionnaireScore(id = "s", label = "Escore", questions = listOf("q1", "q2"))),
    )
    AppTheme {
        QuestionnaireRunner(
            state = QuestionnaireRunnerState.start(questionnaire, answers = mapOf("q1" to QuestionnaireValue.of(2))),
            onAction = {},
            showScores = true,
            texts = QuestionnaireTexts(),
        )
    }
}
