package br.com.codecacto.kmplib.ui.form

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.core.locale.RegionalFormat
import br.com.codecacto.kmplib.platform.getUrlLauncher
import br.com.codecacto.kmplib.platform.motion.LocalReduceMotion
import br.com.codecacto.kmplib.ui.components.AppBanner
import br.com.codecacto.kmplib.ui.components.AppButton
import br.com.codecacto.kmplib.ui.components.AppOutlinedButton
import br.com.codecacto.kmplib.ui.components.FormContainer
import br.com.codecacto.kmplib.ui.components.ImagePickerSource
import br.com.codecacto.kmplib.ui.components.StatusChip
import br.com.codecacto.kmplib.ui.components.StatusChipStyle
import br.com.codecacto.kmplib.ui.components.StatusTone
import br.com.codecacto.kmplib.ui.components.scaffoldContentPadding
import br.com.codecacto.kmplib.ui.components.statusChipColors
import br.com.codecacto.kmplib.ui.components.statusToneColor
import br.com.codecacto.kmplib.ui.questionnaire.QuestionnaireMark
import br.com.codecacto.kmplib.ui.questionnaire.QuestionnairePace
import br.com.codecacto.kmplib.ui.questionnaire.QuestionnaireProgress
import br.com.codecacto.kmplib.ui.questionnaire.QuestionnaireRunner
import br.com.codecacto.kmplib.ui.questionnaire.rememberQuestionnaireTexts
import br.com.codecacto.kmplib.ui.theme.LocalIsCompact
import br.com.codecacto.kmplib.ui.theme.LocalWindowSizeClass
import br.com.codecacto.kmplib.ui.theme.leituraMaxWidth

/**
 * ============================================================================
 *  FormRunner — a tela de RESPONDER um formulário FormSchema v1, no app
 * ============================================================================
 *
 * O par, no app, do `FormRunner` da weblib, sobre o MESMO schema do `backlib-forms`: visibilidade,
 * descarte e validação são o núcleo puro deste pacote, que passa nas MESMAS fixtures das outras duas
 * libs — o que a tela esconde é exatamente o que o servidor descarta. **Pontuação não roda aqui**:
 * o servidor calcula e o runner só exibe ([FormRunnerState.scores]).
 *
 * ## Ritmo: uma SEÇÃO por passo
 * - **Compacto** (`LocalIsCompact`): uma seção por tela — "Etapa N de M", a barra do formulário
 *   inteiro e Voltar/Continuar no fim.
 * - **Expandido**: agrupado — o ÍNDICE de etapas ao lado (concluída ✓, com pendência "!", e, na visão
 *   do médico, quantas reservadas faltam) e as perguntas curtas (número, data, seleção, texto curto)
 *   dividindo a linha em duas colunas.
 * - Seção `likert` é **delegada ao `QuestionnaireRunner`** (a mesma régua, o ritmo pergunta a pergunta
 *   na tela larga, o "faltam N"), com o rótulo de etapa e a barra do formulário.
 *
 * ## Erro no campo, e só depois de tentar
 * "Continuar" confere a etapa (obrigatória visível, formato — consentimento NÃO); "Enviar" confere
 * TUDO e leva à primeira etapa com pendência. A frase fica embaixo do campo, a borda pinta, o foco vai
 * ao primeiro marcado. Mexer no campo limpa o erro DELE (no item da lista, só daquele campo); sair de
 * um campo editado confere o formato na hora.
 *
 * ## MVI — salvar é do ViewModel
 * STATELESS: desenha [state] e devolve [FormRunnerAction]s; o ViewModel aplica
 * [FormRunnerState.reduce] e trata os [FormRunnerEvent]s — a fila é a [FormAnswerQueue], o anexo é
 * o [FormFileUploader] (callback: a lib não conhece a rota).
 *
 * ```kotlin
 * Scaffold(topBar = { AppTopBar(…) }) { innerPadding ->
 *     FormRunner(
 *         state = state.form,
 *         onAction = { vm.onAction(PreconsultaAction.Formulario(it)) },
 *         contentPadding = innerPadding,
 *         notice = textos.unsavedAnswers.takeIf { state.falhaAoSalvar },
 *         linkBaseUrl = "https://vitalis-portal.codecacto.com.br",
 *     )
 * }
 * ```
 *
 * @param notice aviso persistente acima das perguntas (o `error` da fila: [FormRunnerTexts.unsavedAnswers]).
 * @param contentPadding o `innerPadding` do `Scaffold` — aplicado E consumido (ver `FormContainer`).
 * @param likertPace ritmo das seções de escala (delegadas ao `QuestionnaireRunner`).
 * @param imagePickerSource de onde vem a foto do anexo. [ImagePickerSource.GALLERY_AND_CAMERA] exige
 *   `android.permission.CAMERA` no manifesto do app.
 * @param linkBaseUrl origem do portal, para abrir link RELATIVO de termo (`/termos`). Sem ela, o link
 *   relativo vira texto.
 * @param onOpenLink abre um link de termo (default: o navegador do sistema).
 * @param describeFile nome de um anexo JÁ salvo (a resposta é só `{fileId}`; a lib sabe o nome dos
 *   enviados nesta tela).
 * @param onOpenFile abre um anexo salvo (rota autenticada do projeto). Sem ele, o nome não é tocável.
 * @param renderOptionImage figura da opção (`imageKey`, Bristol) nas escolhas `display: "images"`.
 */
@Composable
fun FormRunner(
    state: FormRunnerState,
    onAction: (FormRunnerAction) -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    likertPace: QuestionnairePace = QuestionnairePace.Auto,
    autoAdvance: Boolean = true,
    imagePickerSource: ImagePickerSource = ImagePickerSource.GALLERY_AND_CAMERA,
    linkBaseUrl: String? = null,
    onOpenLink: (url: String) -> Unit = { getUrlLauncher().openUrl(it) },
    describeFile: (ref: FormFileRef, questionId: String) -> String? = { _, _ -> null },
    onOpenFile: ((ref: FormFileRef, questionId: String) -> Unit)? = null,
    renderOptionImage: (@Composable (option: FormChoiceOption, question: FormQuestion) -> Unit)? = null,
    texts: FormRunnerTexts = rememberFormRunnerTexts(),
    issueMessages: FormIssueMessages = rememberFormIssueMessages(),
) {
    val latestOnAction by rememberUpdatedState(onAction)
    val dispatch: (FormRunnerAction) -> Unit = remember { { latestOnAction(it) } }
    val visibility = remember(state.schema, state.answers, state.context) { state.visibility() }
    val steps = remember(state.schema, visibility) { state.steps(visibility) }
    val compact = LocalIsCompact.current
    val reduceMotion = LocalReduceMotion.current
    val separator = remember { RegionalFormat.decimalSeparator() }
    val likertTexts = rememberQuestionnaireTexts().likert
    val env = FormFieldEnv(
        state = state,
        dispatch = dispatch,
        texts = texts,
        messages = issueMessages,
        separator = separator,
        compact = compact,
        imagePickerSource = imagePickerSource,
        linkBaseUrl = linkBaseUrl,
        onOpenLink = onOpenLink,
        describeFile = describeFile,
        onOpenFile = onOpenFile,
        renderOptionImage = renderOptionImage,
        likertTexts = likertTexts,
    )

    if (steps.isEmpty()) {
        Box(modifier.testTag(FormRunnerTestTags.ROOT))
        return
    }
    val index = state.stepIndex(steps)
    val progress = remember(steps, state.answers, state.lockedQuestions, state.onlyQuestionIds) { state.progress(steps) }

    Row(modifier = modifier.testTag(FormRunnerTestTags.ROOT).scaffoldContentPadding(contentPadding)) {
        if (!compact && steps.size > 1) {
            StepsIndex(
                env = env,
                steps = steps,
                current = index,
                modifier = Modifier
                    .width(FormRunnerDefaults.StepsIndexWidthDp.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
            )
        }
        AnimatedContent(
            targetState = steps[index].sectionId,
            modifier = Modifier.weight(1f),
            transitionSpec = { stepTransition(reduceMotion) },
            label = "formulario-passo",
        ) { sectionId ->
            // Desenha a etapa da CHAVE: durante a transição a que sai continua composta.
            val stepIndex = steps.indexOfFirst { it.sectionId == sectionId }
            val step = steps.getOrNull(stepIndex) ?: return@AnimatedContent
            val stepLabel = texts.stepProgress(stepIndex + 1, steps.size) +
                (state.schema.estimatedMinutes?.takeIf { stepIndex == 0 && it > 0 }?.let { " · " + texts.estimate(it) } ?: "")
            val delegate = remember(state, step, stepIndex) { state.likertDelegate(step, stepIndex) }
            if (delegate != null) {
                LikertStep(env, step, stepIndex, steps.size, stepLabel, delegate, progress, notice, likertPace, autoAdvance)
            } else {
                FieldsStep(env, step, stepIndex, steps.size, stepLabel, progress, notice, reduceMotion)
            }
        }
    }
}

private fun stepTransition(reduceMotion: Boolean): ContentTransform =
    if (reduceMotion) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        (fadeIn(tween(durationMillis = 220, delayMillis = 60)) + slideInVertically(tween(260)) { it / 40 }) togetherWith
            fadeOut(tween(durationMillis = 90))
    }

// ---------------------------------------------------------------------------------------------
// Etapa de campos
// ---------------------------------------------------------------------------------------------

@Composable
private fun FieldsStep(
    env: FormFieldEnv,
    step: FormRunnerStep,
    stepIndex: Int,
    stepCount: Int,
    stepLabel: String,
    progress: FormProgress,
    notice: String?,
    reduceMotion: Boolean,
) {
    val state = env.state
    val isLast = stepIndex == stepCount - 1
    FormContainer(
        maxContentWidth = leituraMaxWidth(LocalWindowSizeClass.current),
        horizontalPadding = if (env.compact) 16.dp else 24.dp,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        StepHeader(stepLabel, step.section)
        SectionExtras(env, step)
        ProgressSection(progress, env.texts, reduceMotion)
        if (!notice.isNullOrBlank()) {
            AppBanner(message = notice, tone = StatusTone.WARNING, modifier = Modifier.fillMaxWidth().testTag(FormRunnerTestTags.NOTICE))
        }
        QuestionGrid(env, step)

        val activeUploads = state.uploads.any { it.isActive }
        val flagged = step.questions.count { state.isFlagged(it.id) }
        val status = when {
            state.waitingFiles && activeUploads -> env.texts.filesPending
            flagged > 0 -> env.texts.needsAttention(flagged)
            else -> ""
        }
        // O que falta é dito FORA do botão, e anunciado.
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 20.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(FormRunnerTestTags.STATUS),
        )

        StepActions(
            isLast = isLast,
            showBack = stepIndex > 0 || state.exitEnabled,
            busy = state.busy,
            compact = env.compact,
            texts = env.texts,
            onPrimary = { env.dispatch(if (isLast) FormRunnerAction.Submit else FormRunnerAction.Next) },
            onBack = { env.dispatch(FormRunnerAction.Back) },
        )
    }
}

@Composable
private fun StepHeader(stepLabel: String, section: FormSection) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stepLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(FormRunnerTestTags.STEP),
        )
        Text(
            text = section.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }.testTag(FormRunnerTestTags.SECTION_TITLE),
        )
        section.context?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Selo da seção reservada (médico) e o resultado da escala que o SERVIDOR calculou. */
@Composable
private fun SectionExtras(env: FormFieldEnv, step: FormRunnerStep) {
    val state = env.state
    val sectionReserved = state.markReserved && step.section.reserved
    val score = state.scores.firstOrNull { it.sectionId == step.sectionId }
    if (!sectionReserved && score == null) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sectionReserved) {
            QuestionnaireMark(
                text = env.texts.reserved,
                tone = StatusTone.WARNING,
                icon = Icons.Outlined.Lock,
                testTag = FormRunnerTestTags.SECTION_RESERVED,
            )
        }
        if (score != null) {
            val label = env.texts.scoreLabel(
                score.label,
                formatFormNumber(score.score, env.separator),
                if (score.complete) score.band else env.texts.scoreIncomplete,
            )
            StatusChip(
                label = label,
                tone = if (score.complete) severityTone(score.severity) else StatusTone.NEUTRAL,
                modifier = Modifier.testTag(FormRunnerTestTags.score(step.sectionId)),
            )
        }
    }
}

/** Severidade do servidor (0 sem alerta … 3 o mais grave) no tom do tema. */
internal fun severityTone(severity: Int): StatusTone = when (severity) {
    0 -> StatusTone.SUCCESS
    1 -> StatusTone.INFO
    2 -> StatusTone.WARNING
    3 -> StatusTone.DANGER
    else -> StatusTone.NEUTRAL
}

@Composable
private fun ProgressSection(progress: FormProgress, texts: FormRunnerTexts, reduceMotion: Boolean) {
    val label = texts.answered(progress.answered, progress.total)
    val fraction by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 300),
        label = "formulario-progresso",
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                // O leitor de tela ouve "12 de 28 respondidas", não "43 por cento".
                .semantics { stateDescription = label }
                .testTag(FormRunnerTestTags.PROGRESS),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/**
 * As perguntas da etapa. Na tela larga, as CURTAS ([isCompactFormQuestion]) dividem a linha em duas
 * colunas; o resto ocupa a largura toda. No celular, uma por linha.
 */
@Composable
private fun QuestionGrid(env: FormFieldEnv, step: FormRunnerStep) {
    // `key` por pergunta: a visibilidade muda enquanto se responde, e sem ele o estado lembrado de um
    // campo (rascunho do número, foco) passaria para a pergunta que ocupou a posição dele.
    if (env.compact) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            step.questions.forEach { question -> key(question.id) { FormQuestionItem(env, question, step.section) } }
        }
        return
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val twoColumns = maxWidth >= FormRunnerDefaults.TwoColumnsMinWidthDp.dp
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (!twoColumns) {
                step.questions.forEach { question -> key(question.id) { FormQuestionItem(env, question, step.section) } }
                return@Column
            }
            gridRows(step.questions).forEach { row ->
                key(row.joinToString("|") { it.id }) {
                    if (row.size == 1 && !isCompactFormQuestion(row[0])) {
                        FormQuestionItem(env, row[0], step.section)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                            row.forEach { question ->
                                key(question.id) {
                                    Box(modifier = Modifier.weight(1f)) { FormQuestionItem(env, question, step.section) }
                                }
                            }
                            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** As linhas da grade de duas colunas: pares de perguntas curtas consecutivas; o resto, sozinho. */
internal fun gridRows(questions: List<FormQuestion>): List<List<FormQuestion>> {
    val rows = ArrayList<List<FormQuestion>>()
    var i = 0
    while (i < questions.size) {
        val current = questions[i]
        val next = questions.getOrNull(i + 1)
        if (isCompactFormQuestion(current) && next != null && isCompactFormQuestion(next)) {
            rows += listOf(current, next)
            i += 2
        } else {
            rows += listOf(current)
            i++
        }
    }
    return rows
}

@Composable
private fun StepActions(
    isLast: Boolean,
    showBack: Boolean,
    busy: Boolean,
    compact: Boolean,
    texts: FormRunnerTexts,
    onPrimary: () -> Unit,
    onBack: () -> Unit,
) {
    val primary = if (isLast) (if (busy) texts.submitting else texts.submit) else texts.next
    val primaryTag = if (isLast) FormRunnerTestTags.SUBMIT else FormRunnerTestTags.NEXT
    if (compact) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton(text = primary, onClick = onPrimary, enabled = !busy, modifier = Modifier.testTag(primaryTag))
            if (showBack) {
                AppOutlinedButton(text = texts.back, onClick = onBack, enabled = !busy, modifier = Modifier.testTag(FormRunnerTestTags.BACK))
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
                    modifier = Modifier.weight(1f).testTag(FormRunnerTestTags.BACK),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            AppButton(text = primary, onClick = onPrimary, enabled = !busy, modifier = Modifier.weight(1f).testTag(primaryTag))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Etapa de escala — o QuestionnaireRunner
// ---------------------------------------------------------------------------------------------

@Composable
private fun LikertStep(
    env: FormFieldEnv,
    step: FormRunnerStep,
    stepIndex: Int,
    stepCount: Int,
    stepLabel: String,
    delegate: FormLikertDelegate,
    progress: FormProgress,
    notice: String?,
    pace: QuestionnairePace,
    autoAdvance: Boolean,
) {
    val state = env.state
    val texts = env.texts
    val isLast = stepIndex == stepCount - 1
    val base = rememberQuestionnaireTexts()
    val runnerTexts = remember(base, texts, stepLabel, isLast) {
        base.copy(
            blockProgress = { _, _ -> stepLabel },
            questionProgress = texts.questionProgress,
            answered = texts.answered,
            missing = texts.missing,
            back = texts.back,
            next = texts.next,
            finish = if (isLast) texts.submit else texts.next,
            finishing = texts.submitting,
        )
    }
    val locked = step.questions.filter { it.isHandledByClient && it.id in state.lockedQuestions }
    QuestionnaireRunner(
        state = delegate.runner,
        onAction = { env.dispatch(FormRunnerAction.Likert(it)) },
        pace = pace,
        autoAdvance = autoAdvance,
        notice = notice,
        texts = runnerTexts,
        progress = QuestionnaireProgress(answered = progress.answered, total = progress.total, pending = 0, reservedPending = 0),
        headerExtra = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionExtras(env, step)
                locked.forEach { question -> LockedQuestion(question, state.lockedQuestions.getValue(question.id), texts) }
                if (state.waitingFiles && state.uploads.any { it.isActive }) {
                    Text(
                        text = texts.filesPending,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(FormRunnerTestTags.STATUS),
                    )
                }
            }
        },
        questionExtra = { runnerQuestion ->
            val question = step.questions.firstOrNull { it.id == runnerQuestion.id }
            if (question != null && state.markReserved && isReservedFormQuestion(question, step.section)) {
                ReservedMarks(
                    question = question,
                    showBadge = !step.section.reserved,
                    pending = !countsAsAnswered(question, state.answers[question.id]),
                    texts = texts,
                )
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Índice de etapas (tela larga)
// ---------------------------------------------------------------------------------------------

@Composable
private fun StepsIndex(env: FormFieldEnv, steps: List<FormRunnerStep>, current: Int, modifier: Modifier) {
    val state = env.state
    val statuses = remember(steps, state.answers, state.issues, state.serverErrors, state.dismissedServerErrors, state.markReserved) {
        steps.map { step ->
            val ids = stepQuestionIds(step, state.scope)
            StepStatus(
                done = state.schema.validateSubmission(state.answers, state.today, state.context, ids, state.requireConsent).isValid,
                hasIssues = step.questions.any { state.isFlagged(it.id) },
                pendingReserved = if (state.markReserved) pendingReservedIds(step, state.answers).count { it !in state.lockedQuestions } else 0,
            )
        }
    }
    Column(modifier = modifier.testTag(FormRunnerTestTags.STEPS_NAV), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = env.texts.stepsNav,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).semantics { heading() },
        )
        steps.forEachIndexed { i, step ->
            StepIndexItem(
                number = i + 1,
                title = step.section.title,
                status = statuses[i],
                isCurrent = i == current,
                enabled = !state.busy,
                texts = env.texts,
                testTag = FormRunnerTestTags.stepItem(step.sectionId),
                onClick = { env.dispatch(FormRunnerAction.GoToStep(step.sectionId)) },
            )
        }
    }
}

private class StepStatus(val done: Boolean, val hasIssues: Boolean, val pendingReserved: Int)

@Composable
private fun StepIndexItem(
    number: Int,
    title: String,
    status: StepStatus,
    isCurrent: Boolean,
    enabled: Boolean,
    texts: FormRunnerTexts,
    testTag: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val suffix = when {
        status.hasIssues -> texts.stepHasIssues
        status.done -> texts.stepDone
        else -> null
    }
    val reservedText = status.pendingReserved.takeIf { it > 0 }?.let(texts.reservedPendingCount)
    val description = listOfNotNull(title, reservedText, suffix).joinToString(", ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (isCurrent) scheme.secondaryContainer else scheme.surface)
            .selectable(selected = isCurrent, enabled = enabled, role = Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val (circleBackground, circleContent, circleBorder) = when {
            status.hasIssues -> Triple(scheme.surface, scheme.error, scheme.error)
            status.done -> Triple(scheme.primary, scheme.onPrimary, scheme.primary)
            isCurrent -> Triple(scheme.surface, scheme.primary, scheme.primary)
            else -> Triple(scheme.surface, scheme.onSurfaceVariant, scheme.outline)
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(circleBackground)
                .border(1.dp, circleBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                status.hasIssues -> Text("!", style = MaterialTheme.typography.labelLarge, color = circleContent, fontWeight = FontWeight.Bold)
                status.done -> Icon(Icons.Filled.Check, contentDescription = null, tint = circleContent, modifier = Modifier.size(16.dp))
                else -> Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = circleContent)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = scheme.onSurface,
            )
            if (reservedText != null) {
                // A cor do aviso escurecida até o contraste AA sobre a superfície (a do `StatusChip`).
                val warning = statusChipColors(statusToneColor(StatusTone.WARNING), StatusChipStyle.TINTED, scheme.surface).first
                Text(text = reservedText, style = MaterialTheme.typography.labelMedium, color = warning)
            }
        }
    }
}
