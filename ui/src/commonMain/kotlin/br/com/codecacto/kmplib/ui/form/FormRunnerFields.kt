package br.com.codecacto.kmplib.ui.form

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.platform.FilePickResult
import br.com.codecacto.kmplib.platform.rememberFilePicker
import br.com.codecacto.kmplib.ui.components.AppDatePicker
import br.com.codecacto.kmplib.ui.components.AppDropdownField
import br.com.codecacto.kmplib.ui.components.AppOutlinedButton
import br.com.codecacto.kmplib.ui.components.AppTextArea
import br.com.codecacto.kmplib.ui.components.AppTextField
import br.com.codecacto.kmplib.ui.components.ImagePickerError
import br.com.codecacto.kmplib.ui.components.ImagePickerSource
import br.com.codecacto.kmplib.ui.components.LikertScaleDefaults
import br.com.codecacto.kmplib.ui.components.LikertScaleTexts
import br.com.codecacto.kmplib.ui.components.PickerOption
import br.com.codecacto.kmplib.ui.components.StatusTone
import br.com.codecacto.kmplib.ui.components.TextInputReconciler
import br.com.codecacto.kmplib.ui.components.appKeyboardOptions
import br.com.codecacto.kmplib.ui.components.rememberImagePickerLauncher
import br.com.codecacto.kmplib.ui.questionnaire.LikertAnswerControl
import br.com.codecacto.kmplib.ui.questionnaire.OptionList
import br.com.codecacto.kmplib.ui.questionnaire.OptionRow
import br.com.codecacto.kmplib.ui.questionnaire.OptionTags
import br.com.codecacto.kmplib.ui.questionnaire.QuestionnaireMark
import br.com.codecacto.kmplib.ui.questionnaire.points
import kotlin.math.abs
import kotlin.time.Clock
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/*
 * Os campos do `FormRunner`, um por tipo de pergunta do FormSchema v1 (o `FormRunnerFields` da weblib).
 * O que é REGRA (rascunho de número, anexo, limpeza de erro, ordem da múltipla) mora no núcleo puro
 * (`FormRunnerLogic`), testado; aqui só o desenho e a semântica de acessibilidade.
 */

/** O que cada campo recebe da tela. */
internal class FormFieldEnv(
    val state: FormRunnerState,
    val dispatch: (FormRunnerAction) -> Unit,
    val texts: FormRunnerTexts,
    val messages: FormIssueMessages,
    val separator: Char,
    val compact: Boolean,
    val imagePickerSource: ImagePickerSource,
    val linkBaseUrl: String?,
    val onOpenLink: (String) -> Unit,
    val describeFile: (FormFileRef, String) -> String?,
    val onOpenFile: ((FormFileRef, String) -> Unit)?,
    val renderOptionImage: (@Composable (FormChoiceOption, FormQuestion) -> Unit)?,
    val likertTexts: LikertScaleTexts,
) {
    val enabled: Boolean get() = !state.busy

    fun message(issue: FormAnswerIssue): String = messages.messageFor(issue, formatNumber = { formatFormNumber(it, separator) })

    /** A frase da pergunta: o primeiro problema SEM caminho (o da pergunta inteira), ou o do servidor. */
    fun questionMessage(questionId: String): String? =
        state.issues[questionId]?.firstOrNull { it.path == null }?.let(::message) ?: state.serverErrorOf(questionId)

    /** A frase de um caminho dentro do valor (`"[1].dose"`, `"[0]"`). */
    fun pathMessage(questionId: String, path: String): String? =
        state.issues[questionId]?.firstOrNull { it.path == path }?.let(::message)

    fun tags(questionId: String): OptionTags = OptionTags(
        group = FormRunnerTestTags.answer(questionId),
        error = FormRunnerTestTags.error(questionId),
        option = { FormRunnerTestTags.option(questionId, it) },
    )
}

// ---------------------------------------------------------------------------------------------
// A pergunta: foco, quadro e despacho por tipo
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FormQuestionItem(env: FormFieldEnv, question: FormQuestion, section: FormSection) {
    val bringIntoView = remember { BringIntoViewRequester() }
    val focusRequester = remember { FocusRequester() }
    val request = env.state.focusRequest
    LaunchedEffect(request) {
        if (request != null && request.questionId == question.id) {
            // Um quadro: a etapa pode ter acabado de nascer, e sem medida não há para onde rolar.
            withFrameNanos { }
            bringIntoView.bringIntoView()
            // Campo de texto recebe o foco; opção de escolha, em modo toque, não é focável — aí valem
            // a rolagem e a frase no campo.
            runCatching { focusRequester.requestFocus() }
            env.dispatch(FormRunnerAction.FocusHandled(request.serial))
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .focusRequester(focusRequester)
            .testTag(FormRunnerTestTags.question(question.id)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val lockedNote = env.state.lockedQuestions[question.id]
        when {
            lockedNote != null && question.isHandledByClient -> LockedQuestion(question, lockedNote, env.texts)
            question.type == FormQuestionType.INFO -> InfoCallout(question)
            question.type == FormQuestionType.UNSUPPORTED -> {
                QuestionHeader(env, question, section)
                Notice(env.texts.unsupported)
                env.state.serverErrorOf(question.id)?.let { ErrorText(it, FormRunnerTestTags.error(question.id)) }
            }
            question.type == FormQuestionType.CONSENT -> ConsentField(env, question)
            else -> {
                QuestionHeader(env, question, section)
                when (question.type) {
                    FormQuestionType.TEXT -> ShortTextField(env, question)
                    FormQuestionType.LONG_TEXT -> LongTextField(env, question)
                    FormQuestionType.NUMBER -> NumberQuestionField(env, question)
                    FormQuestionType.DATE -> DateField(env, question)
                    FormQuestionType.SINGLE_CHOICE -> SingleChoiceField(env, question)
                    FormQuestionType.MULTI_CHOICE -> MultiChoiceField(env, question)
                    FormQuestionType.LIKERT -> LikertField(env, question, section)
                    FormQuestionType.LIST -> ListField(env, question)
                    FormQuestionType.FILE -> FileField(env, question)
                }
            }
        }
    }
}

/** Enunciado, "opcional", selo de reservada (médico), dica e "pendente". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionHeader(env: FormFieldEnv, question: FormQuestion, section: FormSection) {
    val reserved = env.state.markReserved && isReservedFormQuestion(question, section)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = question.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!question.isRequired && question.isAnswerable) {
                Text(
                    text = env.texts.optional,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
        question.hint?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (reserved) {
            ReservedMarks(
                question = question,
                showBadge = !section.reserved,
                pending = question.isHandledByClient && !countsAsAnswered(question, env.state.answers[question.id]),
                texts = env.texts,
            )
        }
    }
}

/** O selo "Reservada" e, sem resposta, o "pendente — perguntar na consulta" (a visão do médico). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReservedMarks(question: FormQuestion, showBadge: Boolean, pending: Boolean, texts: FormRunnerTexts) {
    if (!showBadge && !pending) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showBadge) {
            QuestionnaireMark(texts.reserved, StatusTone.WARNING, Icons.Outlined.Lock, FormRunnerTestTags.reserved(question.id))
        }
        if (pending) {
            QuestionnaireMark(texts.reservedPending, StatusTone.WARNING, Icons.Outlined.Schedule, FormRunnerTestTags.reservedPending(question.id))
        }
    }
}

/** Pergunta que esta tela não edita: o enunciado e a frase do projeto no lugar do campo. */
@Composable
internal fun LockedQuestion(question: FormQuestion, note: String, texts: FormRunnerTexts) {
    Column(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }.testTag(FormRunnerTestTags.locked(question.id)),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = question.text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(text = note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** `info`: texto para ler, sem campo. */
@Composable
private fun InfoCallout(question: FormQuestion) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(text = question.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Notice(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorText(text: String, testTag: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag(testTag),
    )
}

/** Avisa a saída do campo DEPOIS de ele ter tido foco — o "blur" que confere o formato. */
@Composable
private fun Modifier.onBlur(onBlur: () -> Unit): Modifier {
    var hadFocus by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(onBlur)
    return onFocusChanged { focus ->
        if (hadFocus && !focus.isFocused) latest()
        hadFocus = focus.isFocused
    }
}

// ---------------------------------------------------------------------------------------------
// Texto, número, data
// ---------------------------------------------------------------------------------------------

@Composable
private fun ShortTextField(env: FormFieldEnv, question: FormQuestion) {
    val value = env.state.answers[question.id]?.textOrNull().orEmpty()
    AppTextField(
        value = value,
        onValueChange = { env.dispatch(FormRunnerAction.Answer(question.id, it.takeIf(String::isNotEmpty)?.let(FormAnswerValue::text))) },
        modifier = Modifier
            .onBlur { env.dispatch(FormRunnerAction.Blur(question.id)) }
            .semantics { contentDescription = question.text }
            .testTag(FormRunnerTestTags.answer(question.id)),
        placeholder = question.placeholder,
        errorMessage = env.questionMessage(question.id),
        enabled = env.enabled,
        maxLength = question.maxLength ?: FORM_TEXT_DEFAULT_MAX_LENGTH,
        keepTextLocally = true,
    )
}

/**
 * Texto longo: o limite é MACIO (o contador mostra `n/teto` e o envio acusa o excesso) — colar um
 * texto maior não perde o fim em silêncio.
 */
@Composable
private fun LongTextField(env: FormFieldEnv, question: FormQuestion) {
    val value = env.state.answers[question.id]?.textOrNull().orEmpty()
    val error = env.questionMessage(question.id)
    AppTextArea(
        value = value,
        onValueChange = { env.dispatch(FormRunnerAction.Answer(question.id, it.takeIf(String::isNotEmpty)?.let(FormAnswerValue::text))) },
        modifier = Modifier
            .onBlur { env.dispatch(FormRunnerAction.Blur(question.id)) }
            .semantics { contentDescription = question.text }
            .testTag(FormRunnerTestTags.answer(question.id)),
        placeholder = question.placeholder,
        maxLength = null,
        minLines = 4,
        showCharCounter = false,
        errorMessage = error,
        helperText = question.maxLength?.let { "${value.length}/$it" },
        enabled = env.enabled,
        keepTextLocally = true,
    )
}

@Composable
private fun NumberQuestionField(env: FormFieldEnv, question: FormQuestion) {
    FormNumberInput(
        config = question.number,
        value = env.state.answers[question.id]?.numberOrNull(),
        separator = env.separator,
        label = null,
        placeholder = question.placeholder,
        errorText = env.questionMessage(question.id),
        enabled = env.enabled,
        description = question.text,
        testTag = FormRunnerTestTags.answer(question.id),
        onValue = { env.dispatch(FormRunnerAction.Answer(question.id, it?.let(FormAnswerValue::number))) },
        onBlur = { env.dispatch(FormRunnerAction.Blur(question.id)) },
    )
}

/**
 * Número com RASCUNHO: o campo guarda o que a pessoa digitou ("72," no meio da digitação) e só o
 * número que ele forma vai para a resposta. Vírgula ou ponto viram o separador da região; casas além
 * de `decimals` não entram; sem `decimals`, inteiro. O texto mora no campo (`TextFieldState`) e o
 * valor de fora só o reescreve quando muda por outro motivo que não a digitação (recarga) — o eco
 * atrasado do ViewModel não come dígito no iOS.
 *
 * **Não salvável (2.276.0):** resposta de formulário de saúde (peso, pressão, escala) não vai ao
 * estado salvo do sistema (`Bundle` no Android, restauração de cena no iOS) — o rascunho é
 * `remember { TextFieldState(…) }`, só memória. A resposta em si mora no ViewModel / fila de envio;
 * numa morte de processo o rascunho não digitado até o fim se perde.
 */
@Composable
internal fun FormNumberInput(
    config: FormNumberConfig?,
    value: FormDecimal?,
    separator: Char,
    label: String?,
    placeholder: String?,
    errorText: String?,
    enabled: Boolean,
    description: String,
    testTag: String,
    onValue: (FormDecimal?) -> Unit,
    onBlur: () -> Unit,
) {
    val key = value?.toPlainString().orEmpty()
    val fieldState = remember { TextFieldState(formatFormNumberDraft(value, separator)) }
    val reconciler = remember { TextInputReconciler(key) }
    val latestOnValue by rememberUpdatedState(onValue)
    SideEffect {
        val local = parseFormNumberDraft(fieldState.text.toString())?.toPlainString().orEmpty()
        reconciler.onExternal(key, local)?.let { fieldState.setTextAndPlaceCursorAtEnd(formatFormNumberDraft(value, separator)) }
    }
    LaunchedEffect(fieldState, reconciler) {
        snapshotFlow { fieldState.text.toString() }.collect { draft ->
            val parsed = parseFormNumberDraft(draft)
            if (reconciler.onLocalText(parsed?.toPlainString().orEmpty())) latestOnValue(parsed)
        }
    }
    val transformation = remember(config, separator) { FormNumberInputTransformation(config, separator) }
    val decimals = (config?.effectiveDecimals ?: 0) > 0
    // Sem teclado numérico com sinal no Android nem no iOS: com faixa que admite negativo, o teclado
    // de texto (o filtro do campo recusa o que não é número).
    val keyboardType = when {
        formNumberAllowsNegative(config) -> KeyboardType.Text
        decimals -> KeyboardType.Decimal
        else -> KeyboardType.Number
    }
    OutlinedTextField(
        state = fieldState,
        modifier = Modifier
            .fillMaxWidth()
            .onBlur(onBlur)
            .semantics { contentDescription = description }
            .testTag(testTag),
        enabled = enabled,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        suffix = config?.unit?.takeIf { it.isNotBlank() }?.let { unit -> { Text(unit) } },
        supportingText = errorText?.let { { Text(it) } },
        isError = errorText != null,
        inputTransformation = transformation,
        keyboardOptions = appKeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Next,
            capitalization = KeyboardCapitalization.None,
            autoCorrect = false,
        ),
        lineLimits = TextFieldLineLimits.SingleLine,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            errorBorderColor = MaterialTheme.colorScheme.error,
            errorLabelColor = MaterialTheme.colorScheme.error,
        ),
    )
}

/** O filtro do campo de número aplicado no buffer, antes de o texto ser aceito ([sanitizeFormNumberDraft]). */
internal data class FormNumberInputTransformation(val config: FormNumberConfig?, val separator: Char) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val proposed = asCharSequence().toString()
        val sanitized = sanitizeFormNumberDraft(proposed, config, separator)
        if (sanitized != proposed) {
            replace(0, length, sanitized)
            placeCursorAtEnd()
        }
    }
}

@Composable
private fun DateField(env: FormFieldEnv, question: FormQuestion) {
    val raw = env.state.answers[question.id]?.textOrNull()
    val date = raw?.let(::parseFormDate)
    AppDatePicker(
        selectedDate = date,
        onDateSelected = {
            env.dispatch(FormRunnerAction.Answer(question.id, FormAnswerValue.text(it.toString())))
            env.dispatch(FormRunnerAction.Blur(question.id))
        },
        label = env.texts.dateLabel,
        modifier = Modifier.fillMaxWidth().testTag(FormRunnerTestTags.answer(question.id)),
        isEnabled = env.enabled,
        errorMessage = env.questionMessage(question.id),
        minDate = resolveFormDateBound(question.date?.min, env.state.today),
        maxDate = resolveFormDateBound(question.date?.max, env.state.today),
        // Resposta de saúde: o calendário aberto não vai ao estado salvo (2.276.0).
        ephemeral = true,
        onClear = if (raw != null) {
            { env.dispatch(FormRunnerAction.Answer(question.id, null)) }
        } else {
            null
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Escolha
// ---------------------------------------------------------------------------------------------

@Composable
private fun SingleChoiceField(env: FormFieldEnv, question: FormQuestion) {
    val chosen = env.state.answers[question.id]?.textOrNull()
    val error = env.questionMessage(question.id)
    val select: (String?) -> Unit = { env.dispatch(FormRunnerAction.Answer(question.id, it?.let(FormAnswerValue::text))) }
    when (question.display) {
        FormChoiceDisplay.SELECT -> {
            val placeholder = question.placeholder ?: env.texts.selectPlaceholder
            // Opcional: a primeira linha do menu é "sem resposta" (o `<option value="">` da weblib).
            val options = (if (question.isRequired) emptyList() else listOf(PickerOption("", placeholder))) +
                question.options.map { PickerOption(it.value, it.label) }
            AppDropdownField(
                value = chosen.orEmpty(),
                onValueChange = { value ->
                    select(value.ifEmpty { null })
                    env.dispatch(FormRunnerAction.Blur(question.id))
                },
                options = options,
                modifier = Modifier.testTag(FormRunnerTestTags.answer(question.id)),
                placeholder = placeholder,
                errorMessage = error,
                enabled = env.enabled,
            )
        }
        FormChoiceDisplay.IMAGES -> ImageOptionGrid(env, question, setOfNotNull(chosen), multi = false, error = error) { select(it) }
        else -> {
            // Até 4 opções curtas: lado a lado, quebrando linha (o `ChoiceGroup` em linha da weblib).
            val short = question.options.size <= 4 && question.options.all { it.label.length <= 24 }
            OptionList(
                tags = env.tags(question.id),
                rows = question.options.map { OptionRow(value = it.value, leading = null, label = it.label, description = null) },
                multi = false,
                selected = setOfNotNull(chosen),
                enabled = env.enabled,
                errorText = error,
                unansweredLabel = env.likertTexts.unanswered,
                onToggle = { select(it) },
                flow = short,
            )
        }
    }
}

@Composable
private fun MultiChoiceField(env: FormFieldEnv, question: FormQuestion) {
    val chosen = env.state.answers[question.id]?.stringListOrNull().orEmpty()
    val error = env.questionMessage(question.id)
    val toggle: (String) -> Unit = { value ->
        val next = toggleFormMultiChoice(chosen, value, value !in chosen, question.options, question.exclusiveValues)
        env.dispatch(FormRunnerAction.Answer(question.id, next.takeIf { it.isNotEmpty() }?.let(FormAnswerValue::choices)))
    }
    if (question.display == FormChoiceDisplay.IMAGES) {
        ImageOptionGrid(env, question, chosen.toSet(), multi = true, error = error, onToggle = toggle)
        return
    }
    OptionList(
        tags = env.tags(question.id),
        rows = question.options.map { OptionRow(value = it.value, leading = null, label = it.label, description = null) },
        multi = true,
        selected = chosen.toSet(),
        enabled = env.enabled,
        errorText = error,
        unansweredLabel = env.likertTexts.unanswered,
        onToggle = toggle,
        flow = question.display == FormChoiceDisplay.CHIPS,
    )
}

/**
 * Escolha por FIGURA (`display: "images"`, a escala de Bristol): grade de cartões com a figura do
 * projeto ([FormFieldEnv.renderOptionImage]) e o rótulo — rádio ou caixa, como as outras escolhas.
 */
@Composable
private fun ImageOptionGrid(
    env: FormFieldEnv,
    question: FormQuestion,
    selected: Set<String>,
    multi: Boolean,
    error: String?,
    onToggle: (String) -> Unit,
) {
    val columns = if (env.compact) 2 else 4
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (multi) Modifier else Modifier.selectableGroup())
                .semantics {
                    if (selected.isEmpty()) stateDescription = env.likertTexts.unanswered
                    if (error != null) error(error)
                }
                .testTag(FormRunnerTestTags.answer(question.id)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            question.options.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { option ->
                        val on = option.value in selected
                        val interaction = if (multi) {
                            Modifier.toggleable(value = on, enabled = env.enabled, role = Role.Checkbox, onValueChange = { onToggle(option.value) })
                        } else {
                            Modifier.selectable(selected = on, enabled = env.enabled, role = Role.RadioButton, onClick = { onToggle(option.value) })
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 96.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(if (on) scheme.primary.copy(alpha = LikertScaleDefaults.SelectedContainerAlpha) else scheme.surface)
                                .border(
                                    width = if (on) 2.dp else 1.dp,
                                    color = when {
                                        on -> scheme.primary
                                        error != null -> scheme.error
                                        else -> scheme.outline
                                    },
                                    shape = MaterialTheme.shapes.medium,
                                )
                                .then(interaction)
                                .padding(12.dp)
                                .testTag(FormRunnerTestTags.option(question.id, option.value)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                        ) {
                            Box(modifier = Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                                env.renderOptionImage?.invoke(option, question)
                            }
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                color = scheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
        if (error != null) ErrorText(error, FormRunnerTestTags.error(question.id))
    }
}

/** Uma pergunta `likert` em seção `fields` (régua própria): a MESMA régua do `QuestionnaireRunner`. */
@Composable
private fun LikertField(env: FormFieldEnv, question: FormQuestion, section: FormSection) {
    val scale = formLikertScaleOf(question, section)
    val exact = remember(scale) { scale?.points().orEmpty() }
    val shown = remember(scale) { scale?.toQuestionnaireScale() }
    val display = remember(shown) { shown?.points().orEmpty() }
    if (scale == null || shown == null || exact.isEmpty() || exact.size != display.size) {
        // Régua impossível no cadastro: aparecer é melhor que sumir (o "Escala inválida" do LikertScaleField).
        ErrorText(env.likertTexts.invalidScale, FormRunnerTestTags.error(question.id))
        return
    }
    val selectedIndex = env.state.answers[question.id]?.numberOrNull()?.let(exact::indexOf)?.takeIf { it >= 0 }
    LikertAnswerControl(
        tags = env.tags(question.id),
        scale = shown,
        selected = selectedIndex?.let(display::get),
        compact = env.compact,
        enabled = env.enabled,
        errorText = env.questionMessage(question.id),
        likertTexts = env.likertTexts,
        onSelect = { point ->
            val index = display.indexOfFirst { abs(it - point) < 1e-6 }
            exact.getOrNull(index)?.let { env.dispatch(FormRunnerAction.Answer(question.id, FormAnswerValue.number(it))) }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Lista repetível
// ---------------------------------------------------------------------------------------------

/**
 * Lista repetível ("nome e dose de cada medicamento"): cada item é um cartão com os campos dele e o
 * "Remover"; o "Adicionar" some no teto (`maxItems`, ou 50). Item novo leva o foco ao 1º campo dele.
 */
@Composable
private fun ListField(env: FormFieldEnv, question: FormQuestion) {
    val config = question.list ?: return
    val items = env.state.answers[question.id]?.listItemsOrNull().orEmpty()
    val max = config.maxItems ?: FORM_LIST_DEFAULT_MAX_ITEMS
    var focusItem by remember { mutableStateOf<Int?>(null) }
    val firstFieldRequesters = remember { HashMap<Int, FocusRequester>() }
    LaunchedEffect(focusItem) {
        val target = focusItem ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { firstFieldRequesters[target]?.requestFocus() }
        focusItem = null
    }
    val emit: (List<FormListItem>, String?) -> Unit = { next, path ->
        env.dispatch(FormRunnerAction.Answer(question.id, next.takeIf { it.isNotEmpty() }?.let(FormAnswerValue::items), path))
    }
    val error = env.questionMessage(question.id)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEachIndexed { index, item ->
            val title = env.texts.listItemTitle(config.itemLabel, index + 1)
            val itemError = env.pathMessage(question.id, "[$index]")
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (itemError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                    .testTag(FormRunnerTestTags.listItem(question.id, index)),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { emit(items.filterIndexed { i, _ -> i != index }, null) },
                            enabled = env.enabled,
                            modifier = Modifier
                                .semantics { contentDescription = env.texts.listRemoveItem(config.itemLabel, index + 1) }
                                .testTag(FormRunnerTestTags.listItemRemove(question.id, index)),
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(env.texts.listRemove)
                        }
                    }
                    config.fields.forEachIndexed { fieldIndex, field ->
                        val requester = if (fieldIndex == 0) firstFieldRequesters.getOrPut(index) { FocusRequester() } else null
                        ListItemField(
                            env = env,
                            questionId = question.id,
                            index = index,
                            field = field,
                            value = item[field.id],
                            error = env.pathMessage(question.id, "[$index].${field.id}"),
                            focusRequester = requester,
                            onChange = { value ->
                                val next = items.mapIndexed { i, it -> if (i == index) it + (field.id to (value ?: JsonNull)) else it }
                                emit(next, "[$index].${field.id}")
                            },
                        )
                    }
                    if (itemError != null) ErrorText(itemError, FormRunnerTestTags.listItem(question.id, index) + "-erro")
                }
            }
        }
        if (items.size < max) {
            AppOutlinedButton(
                text = env.texts.listAdd(config.itemLabel),
                onClick = {
                    emit(items + listOf(emptyMap()), null)
                    focusItem = items.size
                },
                enabled = env.enabled,
                icon = Icons.Filled.Add,
                modifier = Modifier.testTag(FormRunnerTestTags.listAdd(question.id)),
            )
        }
        if (error != null) ErrorText(error, FormRunnerTestTags.error(question.id))
    }
}

/** Um campo de item de lista: `text`, `number`, `singleChoice` (seleção) ou `date`. */
@Composable
private fun ListItemField(
    env: FormFieldEnv,
    questionId: String,
    index: Int,
    field: FormQuestion,
    value: JsonPrimitive?,
    error: String?,
    focusRequester: FocusRequester?,
    onChange: (JsonPrimitive?) -> Unit,
) {
    val label = if (field.isRequired) field.text else "${field.text} (${env.texts.optional})"
    val tag = FormRunnerTestTags.listItemField(questionId, index, field.id)
    val focus = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    val text = value?.takeIf { it !is JsonNull && it.isString }?.content
    when (field.type) {
        FormQuestionType.TEXT -> AppTextField(
            value = text.orEmpty(),
            onValueChange = { onChange(it.takeIf(String::isNotEmpty)?.let(::formListText)) },
            modifier = focus.onBlur { env.dispatch(FormRunnerAction.Blur(questionId)) }.testTag(tag),
            label = label,
            placeholder = field.placeholder,
            errorMessage = error,
            enabled = env.enabled,
            maxLength = field.maxLength ?: FORM_TEXT_DEFAULT_MAX_LENGTH,
            keepTextLocally = true,
        )
        FormQuestionType.NUMBER -> Box(modifier = focus) {
            FormNumberInput(
                config = field.number,
                value = value?.takeIf { it !is JsonNull && !it.isString }?.toFormDecimalOrNull(),
                separator = env.separator,
                label = label,
                placeholder = field.placeholder,
                errorText = error,
                enabled = env.enabled,
                description = label,
                testTag = tag,
                onValue = { onChange(it?.let(::formListNumber)) },
                onBlur = { env.dispatch(FormRunnerAction.Blur(questionId)) },
            )
        }
        FormQuestionType.SINGLE_CHOICE -> AppDropdownField(
            value = text.orEmpty(),
            onValueChange = { picked -> onChange(picked.takeIf(String::isNotEmpty)?.let(::formListText)) },
            options = (if (field.isRequired) emptyList() else listOf(PickerOption("", env.texts.selectPlaceholder))) +
                field.options.map { PickerOption(it.value, it.label) },
            modifier = focus.testTag(tag),
            label = label,
            placeholder = field.placeholder ?: env.texts.selectPlaceholder,
            errorMessage = error,
            enabled = env.enabled,
        )
        FormQuestionType.DATE -> AppDatePicker(
            selectedDate = text?.let(::parseFormDate),
            onDateSelected = { onChange(formListText(it.toString())) },
            label = label,
            modifier = focus.fillMaxWidth().testTag(tag),
            isEnabled = env.enabled,
            errorMessage = error,
            minDate = resolveFormDateBound(field.date?.min, env.state.today),
            maxDate = resolveFormDateBound(field.date?.max, env.state.today),
            ephemeral = true,
            onClear = if (text != null) {
                { onChange(null) }
            } else {
                null
            },
        )
        // Campo de item que o contrato não permite (lista/arquivo aninhados): não se edita aqui.
        else -> Unit
    }
}

// ---------------------------------------------------------------------------------------------
// Anexo
// ---------------------------------------------------------------------------------------------

/**
 * Anexo por CALLBACK: a lib não conhece rota (o app sobe pelo [FormFileUploader]). Mostra os já
 * enviados (nome pelo `describeFile` do projeto, ou "Arquivo N"), os que estão subindo, com
 * progresso real e X que cancela, e os que falharam, com "Tentar de novo". Foto vem do seletor de
 * imagem (câmera/galeria, sempre JPEG sem EXIF); PDF, do seletor de arquivos (recusa o grande ANTES de
 * ler). Tipo, tamanho e teto são conferidos aqui; os bytes, pelo servidor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileField(env: FormFieldEnv, question: FormQuestion) {
    val config = question.file ?: return
    val state = env.state
    val refs = state.answers[question.id]?.fileRefsOrNull().orEmpty()
    val uploads = state.uploads.filter { it.questionId == question.id }
    val remaining = config.maxFiles - refs.size - uploads.count { it.isActive }
    val imageOnly = config.accept.isNotEmpty() && config.accept.all { it == FormFileKind.IMAGE }
    val mb = formatFormNumber(config.maxSizeMb, env.separator)

    val imagePicker = rememberImagePickerLauncher(
        source = env.imagePickerSource,
        onImagePicked = { image ->
            val name = "foto-${Clock.System.now().toEpochMilliseconds()}.jpg"
            env.dispatch(FormRunnerAction.PickFiles(question.id, listOf(FormPickedFile(name, image.mimeType, image.bytes))))
        },
        onError = { problem ->
            val mapped = when (problem) {
                ImagePickerError.CAMERA_PERMISSION_DENIED -> FormUploadProblem.CameraDenied
                ImagePickerError.CAMERA_UNAVAILABLE -> FormUploadProblem.CameraUnavailable
                ImagePickerError.IMAGE_UNREADABLE -> FormUploadProblem.Unreadable
            }
            env.dispatch(FormRunnerAction.PickFailed(question.id, mapped))
        },
    )
    val pdfPicker = rememberFilePicker(mimeTypes = listOf("application/pdf"), maxBytes = formFileMaxBytes(config.maxSizeMb)) { result ->
        when (result) {
            is FilePickResult.Picked -> {
                val file = result.file
                env.dispatch(
                    FormRunnerAction.PickFiles(
                        question.id,
                        listOf(FormPickedFile(file.name, file.mimeType.ifBlank { "application/pdf" }, file.data)),
                    ),
                )
            }
            is FilePickResult.TooLarge -> env.dispatch(FormRunnerAction.PickFailed(question.id, FormUploadProblem.TooBig, result.name, result.sizeBytes))
            is FilePickResult.Failed -> env.dispatch(FormRunnerAction.PickFailed(question.id, FormUploadProblem.Unreadable, result.name))
            FilePickResult.Cancelled -> Unit
        }
    }

    val error = env.questionMessage(question.id)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        refs.forEachIndexed { index, ref -> key(ref.fileId) {
            val name = env.describeFile(ref, question.id) ?: state.fileNames[ref.fileId] ?: env.texts.fileFallbackName(index + 1)
            val rowError = env.pathMessage(question.id, "[$index]")
            FileRow(
                icon = if (imageOnly) Icons.Outlined.Image else Icons.Outlined.Description,
                title = name,
                error = rowError,
                onOpen = env.onOpenFile?.let { open -> { open(ref, question.id) } },
                testTag = FormRunnerTestTags.file(question.id, index),
            ) {
                IconButton(
                    onClick = {
                        val next = refs.filter { it.fileId != ref.fileId }
                        env.dispatch(FormRunnerAction.Answer(question.id, next.takeIf { it.isNotEmpty() }?.let(FormAnswerValue::files)))
                    },
                    enabled = env.enabled,
                    modifier = Modifier.testTag(FormRunnerTestTags.fileRemove(question.id, index)),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = env.texts.fileRemove(name))
                }
            }
        } }
        uploads.forEach { upload -> key(upload.key) { UploadRow(env, config, upload, mb) } }
        if (remaining > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (FormFileKind.IMAGE in config.accept) {
                    AppOutlinedButton(
                        text = env.texts.fileAddPhoto,
                        onClick = { imagePicker.launch() },
                        enabled = env.enabled,
                        icon = Icons.Outlined.PhotoCamera,
                        modifier = Modifier.weight(1f).testTag(FormRunnerTestTags.fileAdd(question.id, FormFileKind.IMAGE)),
                    )
                }
                if (FormFileKind.PDF in config.accept) {
                    AppOutlinedButton(
                        text = env.texts.fileAddPdf,
                        onClick = pdfPicker,
                        enabled = env.enabled,
                        icon = Icons.Outlined.PictureAsPdf,
                        modifier = Modifier.weight(1f).testTag(FormRunnerTestTags.fileAdd(question.id, FormFileKind.PDF)),
                    )
                }
            }
            Text(text = env.texts.fileHint(config.accept, mb), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (error != null) ErrorText(error, FormRunnerTestTags.error(question.id))
    }
}

@Composable
private fun FileRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    error: String?,
    onOpen: (() -> Unit)?,
    testTag: String,
    trailing: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(MaterialTheme.shapes.small)
                .border(1.dp, if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                .padding(start = 12.dp)
                .testTag(testTag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textDecoration = if (onOpen != null) TextDecoration.Underline else null,
                modifier = Modifier
                    .weight(1f)
                    .then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier),
            )
            trailing()
        }
        if (error != null) ErrorText(error, "$testTag-erro")
    }
}

@Composable
private fun UploadRow(env: FormFieldEnv, config: FormFileConfig, upload: FormUpload, mb: String) {
    val texts = env.texts
    val problem = upload.problem
    val message = when (problem) {
        null -> null
        FormUploadProblem.WrongType -> texts.fileWrongType(config.accept)
        FormUploadProblem.TooBig -> texts.fileTooBig(mb)
        FormUploadProblem.TooMany -> env.messages.filesTooMany(config.maxFiles)
        FormUploadProblem.Unreadable -> texts.fileUnreadable
        FormUploadProblem.CameraDenied -> texts.fileCameraDenied
        FormUploadProblem.CameraUnavailable -> texts.fileCameraUnavailable
        is FormUploadProblem.UploadFailed -> problem.message ?: texts.fileUploadError
    }
    val tag = FormRunnerTestTags.upload(upload.key)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(MaterialTheme.shapes.small)
                .border(1.dp, if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                .padding(start = 12.dp)
                .testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (upload.name.isNotEmpty() || problem == null) {
                    Text(
                        text = if (problem != null) upload.name else texts.fileUploading(upload.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (problem == null) {
                    val progressLabel = texts.fileUploading(upload.name)
                    val fraction = upload.progress
                    if (fraction == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().semantics { contentDescription = progressLabel })
                    } else {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = progressLabel },
                        )
                    }
                }
            }
            if (upload.isRetryable) {
                TextButton(onClick = { env.dispatch(FormRunnerAction.RetryUpload(upload.key)) }, enabled = env.enabled, modifier = Modifier.testTag(FormRunnerTestTags.uploadRetry(upload.key))) {
                    Text(texts.fileRetry)
                }
            }
            // Subindo, o X CANCELA o envio; com falha, descarta a linha.
            IconButton(onClick = { env.dispatch(FormRunnerAction.DismissUpload(upload.key)) }, modifier = Modifier.testTag(FormRunnerTestTags.uploadDismiss(upload.key))) {
                Icon(Icons.Filled.Close, contentDescription = texts.fileRemove(upload.name.ifEmpty { texts.fileFallbackName(1) }))
            }
        }
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }.testTag("$tag-erro"),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Consentimento
// ---------------------------------------------------------------------------------------------

/**
 * Consentimento: a caixa e o texto do termo na mesma linha, os links abaixo (abrem FORA do app — o
 * leitor de tela ouve isso). `false` é resposta válida no salvamento; quem barra é o ENVIO.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConsentField(env: FormFieldEnv, question: FormQuestion) {
    val checked = env.state.answers[question.id]?.booleanOrNull() == true
    val error = env.questionMessage(question.id)
    val scheme = MaterialTheme.colorScheme
    val links = question.consent?.links.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = scheme.surface,
            modifier = Modifier.fillMaxWidth().border(1.dp, if (error != null) scheme.error else scheme.outlineVariant, MaterialTheme.shapes.medium),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = checked,
                            enabled = env.enabled,
                            role = Role.Checkbox,
                            onValueChange = { env.dispatch(FormRunnerAction.Answer(question.id, FormAnswerValue.bool(it))) },
                        )
                        .semantics { if (error != null) error(error) }
                        .padding(horizontal = 12.dp)
                        .testTag(FormRunnerTestTags.answer(question.id)),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(checked = checked, onCheckedChange = null, enabled = env.enabled)
                    Text(
                        text = question.text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f).padding(top = 10.dp, end = 4.dp),
                    )
                }
                if (links.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(start = 56.dp, end = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        links.forEachIndexed { index, link ->
                            val url = resolveFormLink(link.href, env.linkBaseUrl)
                            val tag = FormRunnerTestTags.consentLink(question.id, index)
                            if (url != null) {
                                Row(
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clickable(role = Role.Button, onClick = { env.onOpenLink(url) })
                                        .semantics(mergeDescendants = true) {
                                            role = Role.Button
                                            contentDescription = "${link.label} (${env.texts.opensExternally})"
                                        }
                                        .testTag(tag),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = link.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = scheme.primary,
                                        textDecoration = TextDecoration.Underline,
                                    )
                                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(14.dp))
                                }
                            } else {
                                // `javascript:` ou relativo sem origem: vira texto (defesa em profundidade).
                                Text(
                                    text = link.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant,
                                    modifier = Modifier.heightIn(min = 48.dp).padding(top = 14.dp).testTag(tag),
                                )
                            }
                        }
                    }
                }
            }
        }
        question.hint?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        if (error != null) ErrorText(error, FormRunnerTestTags.error(question.id))
    }
}
