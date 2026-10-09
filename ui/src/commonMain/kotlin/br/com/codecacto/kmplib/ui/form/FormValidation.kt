package br.com.codecacto.kmplib.ui.form

import androidx.compose.runtime.Immutable
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Os códigos estáveis de problema — os MESMOS do `AnswerIssueCodes` do `backlib-forms` e do
 * `FORM_ISSUE_CODES` da weblib (o `name` de cada um é o código do fio).
 */
enum class FormIssueCode {
    UNKNOWN_QUESTION,
    NOT_ANSWERABLE,
    WRONG_TYPE,
    TEXT_INVALID_CHARACTER,
    TEXT_TOO_LONG,
    NUMBER_OUT_OF_RANGE,
    NUMBER_TOO_MANY_DECIMALS,
    NUMBER_OFF_STEP,
    DATE_INVALID,
    DATE_OUT_OF_RANGE,
    OPTION_INVALID,
    OPTION_DUPLICATE,
    OPTION_EXCLUSIVE,
    LIKERT_OFF_SCALE,
    LIST_TOO_LONG,
    LIST_ITEM_UNKNOWN_FIELD,
    FILES_TOO_MANY,
    FILE_REF_INVALID,
    FILE_DUPLICATE,

    // Só no ENVIO:
    REQUIRED,
    CONSENT_REQUIRED,
    LIST_TOO_SHORT,
    LIST_ITEM_INCOMPLETE,
    LIST_ITEM_EMPTY,
    ;

    companion object {
        fun fromWireName(name: String?): FormIssueCode? = entries.firstOrNull { it.name == name }
    }
}

/**
 * O que a frase do problema cita (já resolvido: `"today"` vira a data do dia). Só os códigos que
 * citam limite trazem algo.
 *
 * @property max teto de caracteres (`TEXT_TOO_LONG`), de itens (`LIST_TOO_LONG`) ou de arquivos (`FILES_TOO_MANY`).
 * @property min mínimo de itens (`LIST_TOO_SHORT`).
 * @property minValue/[maxValue] faixa do número (`NUMBER_OUT_OF_RANGE`).
 * @property decimals casas permitidas (`NUMBER_TOO_MANY_DECIMALS`).
 * @property step passo (`NUMBER_OFF_STEP`).
 * @property minDate/[maxDate] faixa de data (`DATE_OUT_OF_RANGE`).
 */
@Immutable
data class FormIssueParams(
    val max: Int? = null,
    val min: Int? = null,
    val minValue: FormDecimal? = null,
    val maxValue: FormDecimal? = null,
    val decimals: Int? = null,
    val step: FormDecimal? = null,
    val minDate: LocalDate? = null,
    val maxDate: LocalDate? = null,
)

/**
 * Um problema numa resposta. [code] é estável (fixtures `validation`, iguais nas três libs); [path]
 * aponta dentro do valor (`"[1].dose"` = campo `dose` do 2º item; `"[0]"` = 1º anexo). A frase sai de
 * [FormIssueMessages], no idioma da tela.
 */
@Immutable
data class FormAnswerIssue(
    val questionId: String,
    val code: FormIssueCode,
    val path: String? = null,
    val params: FormIssueParams = FormIssueParams(),
)

/** O resultado de uma validação. */
@Immutable
class FormValidation internal constructor(val issues: List<FormAnswerIssue>) {
    val isValid: Boolean get() = issues.isEmpty()

    /** As perguntas com problema, na ordem em que apareceram (a do documento, no envio). */
    val questionIds: List<String> get() = issues.mapTo(LinkedHashSet()) { it.questionId }.toList()

    /** Os problemas de uma pergunta. */
    fun issuesOf(questionId: String): List<FormAnswerIssue> = issues.filter { it.questionId == questionId }

    override fun toString(): String = "FormValidation(${issues.map { "${it.questionId}:${it.code}${it.path ?: ""}" }})"
}

/** Teto de `text` sem `maxLength` — em unidades UTF-16 (o `length` do JS e do Kotlin). */
const val FORM_TEXT_DEFAULT_MAX_LENGTH: Int = 1_000

/** Teto de `longText` sem `maxLength`. */
const val FORM_LONG_TEXT_DEFAULT_MAX_LENGTH: Int = 10_000

/** Teto de itens de `list` sem `maxItems`. */
const val FORM_LIST_DEFAULT_MAX_ITEMS: Int = 50

/** Teto de magnitude de `number`: o maior inteiro que o `number` do JavaScript guarda sem perda é ~9×10^15. */
val FORM_NUMBER_MAX_ABS: FormDecimal = FormDecimal.parse("1000000000000000")!!

/**
 * O dia de hoje no fuso [timeZone] — o default dos limites `"today"`. Passe o dia da CLÍNICA (fuso de
 * quem decide) quando o limite importar de verdade; o servidor confere de novo.
 */
fun formToday(timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDate = Clock.System.todayIn(timeZone)

/**
 * **O salvamento** (parcial, a cada passo): forma, faixa, opção, casas, passo, teto de cada valor do
 * patch. Não olha visibilidade nem obrigatoriedade — dá para salvar pela metade, e consentimento
 * `false` é salvamento válido. `null` (apagar) é sempre válido, exceto em pergunta que não existe.
 */
fun FormSchemaV1.validateValues(
    patch: Map<String, FormAnswerValue?>,
    today: LocalDate = formToday(),
): FormValidation {
    val issues = ArrayList<FormAnswerIssue>()
    for ((questionId, value) in patch) {
        val question = findQuestion(questionId)
        if (question == null) {
            issues += FormAnswerIssue(questionId, FormIssueCode.UNKNOWN_QUESTION)
            continue
        }
        if (value == null) continue
        if (question.type == FormQuestionType.UNSUPPORTED) continue // o servidor confere
        issues += checkValue(question, sectionOf(questionId), value, today)
    }
    return FormValidation(issues)
}

/**
 * **O envio**: obrigatória VISÍVEL sem resposta, consentimento obrigatório não aceito, lista abaixo do
 * mínimo ou com item vazio/incompleto — e o formato do que está visível. Pergunta invisível não barra
 * nem é validada: é descartada.
 *
 * @param answers TODAS as respostas (de quem quer que seja) — a visibilidade depende delas.
 * @param onlyQuestionIds limita o que é conferido à visão de quem envia (a triagem da recepção
 *   confere só as perguntas dela). `null` = todas.
 * @param requireConsent `false` na triagem da recepção: o consentimento é do paciente.
 */
fun FormSchemaV1.validateSubmission(
    answers: Map<String, FormAnswerValue?>,
    today: LocalDate = formToday(),
    context: FormContext? = null,
    onlyQuestionIds: Collection<String>? = null,
    requireConsent: Boolean = true,
): FormValidation {
    val only = onlyQuestionIds?.toHashSet()
    val visibility = evaluateVisibility(answers, context)
    val issues = ArrayList<FormAnswerIssue>()
    for (section in sections) {
        for (question in section.questions) {
            if (!question.isHandledByClient) continue
            if (!visibility.isQuestionVisible(question.id)) continue
            if (only != null && question.id !in only) continue
            val value = answers[question.id]
            if (value != null) {
                val format = checkValue(question, section, value, today)
                if (format.isNotEmpty()) {
                    issues += format
                    continue
                }
            }
            issues += requiredIssues(question, value, requireConsent)
        }
    }
    return FormValidation(issues)
}

/**
 * O primeiro problema de um número, na ordem do contrato: faixa (inclusive o teto de magnitude) →
 * casas decimais → passo. `null` = aceito. Sem `decimals`, só inteiro.
 */
fun formNumberIssue(value: FormDecimal, config: FormNumberConfig?): FormIssueCode? {
    if (value.abs() > FORM_NUMBER_MAX_ABS) return FormIssueCode.NUMBER_OUT_OF_RANGE
    if (config?.min != null && value < config.min) return FormIssueCode.NUMBER_OUT_OF_RANGE
    if (config?.max != null && value > config.max) return FormIssueCode.NUMBER_OUT_OF_RANGE
    if (value.decimalPlaces > (config?.effectiveDecimals ?: 0)) return FormIssueCode.NUMBER_TOO_MANY_DECIMALS
    val step = config?.step
    if (step != null && step.signum > 0 && !isOnFormStep(value, config.min ?: FormDecimal.ZERO, step)) {
        return FormIssueCode.NUMBER_OFF_STEP
    }
    return null
}

/** A régua de uma pergunta `likert`: a própria (seção `fields`) ou a da seção `likert`. */
fun formLikertScaleOf(question: FormQuestion, section: FormSection?): FormLikertScale? =
    question.scale ?: section?.scale?.takeIf { section.kind == FormSectionKind.LIKERT }

private val ISO_DATE = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

/** `AAAA-MM-DD` de uma data que EXISTE (`2026-02-30` não passa), no calendário gregoriano. */
fun parseFormDate(text: String): LocalDate? {
    val match = ISO_DATE.matchEntire(text) ?: return null
    val year = match.groupValues[1].toInt()
    val month = match.groupValues[2].toInt()
    val day = match.groupValues[3].toInt()
    if (month !in 1..12 || day < 1) return null
    val leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
    val days = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)[month - 1]
    if (day > days) return null
    return LocalDate(year, month, day)
}

/** `"today"` → [today]; data ISO válida → ela; qualquer outra coisa → `null` (sem limite). */
fun resolveFormDateBound(bound: String?, today: LocalDate): LocalDate? = when (bound) {
    null -> null
    "today" -> today
    else -> parseFormDate(bound)
}

/**
 * Controle C0 (U+0000..U+001F, menos TAB, LF e CR) e surrogate sozinho: o `jsonb` do Postgres recusa
 * `\u0000` e par de surrogate quebrado — gravado, viraria 500 na hora de salvar, não erro no campo.
 */
fun hasFormInvalidCharacter(text: String): Boolean {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c < ' ' && c != '\t' && c != '\n' && c != '\r' -> return true
            c.isHighSurrogate() -> {
                if (i + 1 >= text.length || !text[i + 1].isLowSurrogate()) return true
                i++
            }
            c.isLowSurrogate() -> return true
        }
        i++
    }
    return false
}

private val UUID_CANONICAL = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

/** `fileId` no formato do contrato: UUID canônico, minúsculo. */
fun isCanonicalFileId(fileId: String?): Boolean = fileId != null && UUID_CANONICAL.matches(fileId)

// ---------------------------------------------------------------------------------------------
// Conferências — no máximo um problema por valor, na ordem fixa dos códigos
// ---------------------------------------------------------------------------------------------

private fun requiredIssues(question: FormQuestion, value: FormAnswerValue?, requireConsent: Boolean): List<FormAnswerIssue> {
    val id = question.id
    return when (question.type) {
        FormQuestionType.CONSENT ->
            if (requireConsent && question.isRequired && value?.booleanOrNull() != true) {
                listOf(FormAnswerIssue(id, FormIssueCode.CONSENT_REQUIRED))
            } else {
                emptyList()
            }
        FormQuestionType.LIST -> listSubmissionIssues(question, value)
        else -> if (question.isRequired && !isFormAnswered(value)) listOf(FormAnswerIssue(id, FormIssueCode.REQUIRED)) else emptyList()
    }
}

private fun listSubmissionIssues(question: FormQuestion, value: FormAnswerValue?): List<FormAnswerIssue> {
    val id = question.id
    val items = value?.listItemsOrNull().orEmpty()
    if (items.isEmpty()) {
        return if (question.isRequired) listOf(FormAnswerIssue(id, FormIssueCode.REQUIRED)) else emptyList()
    }
    val config = question.list ?: return emptyList()
    val min = config.minItems ?: 0
    if (items.size < min) return listOf(FormAnswerIssue(id, FormIssueCode.LIST_TOO_SHORT, params = FormIssueParams(min = min)))
    val out = ArrayList<FormAnswerIssue>()
    items.forEachIndexed { i, item ->
        val answeredFields = config.fields.filter { field -> item[field.id].isAnsweredJson() }
        if (answeredFields.isEmpty()) {
            out += FormAnswerIssue(id, FormIssueCode.LIST_ITEM_EMPTY, path = "[$i]")
            return@forEachIndexed
        }
        config.fields.filter { it.isRequired && !item[it.id].isAnsweredJson() }.forEach { field ->
            out += FormAnswerIssue(id, FormIssueCode.LIST_ITEM_INCOMPLETE, path = "[$i].${field.id}")
        }
    }
    return out
}

/** As conferências de FORMATO de um valor (não nulo). */
private fun checkValue(question: FormQuestion, section: FormSection?, value: FormAnswerValue, today: LocalDate): List<FormAnswerIssue> =
    when (question.type) {
        FormQuestionType.LIST -> checkList(question, value, today)
        FormQuestionType.FILE -> checkFiles(question, value)
        FormQuestionType.INFO -> listOf(FormAnswerIssue(question.id, FormIssueCode.NOT_ANSWERABLE))
        else -> listOfNotNull(checkScalar(question, section, value, today, question.id, null))
    }

/** Os tipos de valor único — inclusive os campos de item de lista (`section = null`). */
private fun checkScalar(
    question: FormQuestion,
    section: FormSection?,
    value: FormAnswerValue,
    today: LocalDate,
    questionId: String,
    path: String?,
): FormAnswerIssue? {
    fun fail(code: FormIssueCode, params: FormIssueParams = FormIssueParams()) = FormAnswerIssue(questionId, code, path, params)
    return when (question.type) {
        FormQuestionType.TEXT, FormQuestionType.LONG_TEXT -> {
            val text = value.textOrNull() ?: return fail(FormIssueCode.WRONG_TYPE)
            if (hasFormInvalidCharacter(text)) return fail(FormIssueCode.TEXT_INVALID_CHARACTER)
            val max = question.maxLength ?: defaultMaxLength(question.type)
            if (text.length > max) fail(FormIssueCode.TEXT_TOO_LONG, FormIssueParams(max = max)) else null
        }
        FormQuestionType.NUMBER -> {
            val number = value.numberOrNull() ?: return fail(FormIssueCode.WRONG_TYPE)
            formNumberIssue(number, question.number)?.let { fail(it, numberParams(it, question.number)) }
        }
        FormQuestionType.DATE -> {
            val raw = value.textOrNull() ?: return fail(FormIssueCode.WRONG_TYPE)
            val date = parseFormDate(raw) ?: return fail(FormIssueCode.DATE_INVALID)
            val min = resolveFormDateBound(question.date?.min, today)
            val max = resolveFormDateBound(question.date?.max, today)
            if ((min != null && date < min) || (max != null && date > max)) {
                fail(FormIssueCode.DATE_OUT_OF_RANGE, FormIssueParams(minDate = min, maxDate = max))
            } else {
                null
            }
        }
        FormQuestionType.SINGLE_CHOICE -> {
            val chosen = value.textOrNull() ?: return fail(FormIssueCode.WRONG_TYPE)
            if (question.options.none { it.value == chosen }) fail(FormIssueCode.OPTION_INVALID) else null
        }
        FormQuestionType.MULTI_CHOICE -> {
            val chosen = value.stringListOrNull() ?: return fail(FormIssueCode.WRONG_TYPE)
            val valid = question.options.mapTo(HashSet()) { it.value }
            when {
                chosen.any { it !in valid } -> fail(FormIssueCode.OPTION_INVALID)
                chosen.toSet().size != chosen.size -> fail(FormIssueCode.OPTION_DUPLICATE)
                chosen.size > 1 && chosen.any { it in question.exclusiveValues } -> fail(FormIssueCode.OPTION_EXCLUSIVE)
                else -> null
            }
        }
        FormQuestionType.LIKERT -> {
            val number = value.numberOrNull() ?: return fail(FormIssueCode.WRONG_TYPE)
            val scale = formLikertScaleOf(question, section)
            if (scale == null || scale.points().none { it == number }) fail(FormIssueCode.LIKERT_OFF_SCALE) else null
        }
        FormQuestionType.CONSENT -> if (value.booleanOrNull() == null) fail(FormIssueCode.WRONG_TYPE) else null
        FormQuestionType.INFO -> fail(FormIssueCode.NOT_ANSWERABLE)
        // `list`/`file` como campo de item (o contrato não permite), ou tipo que esta versão não conhece.
        FormQuestionType.LIST, FormQuestionType.FILE, FormQuestionType.UNSUPPORTED -> fail(FormIssueCode.WRONG_TYPE)
    }
}

private fun checkList(question: FormQuestion, value: FormAnswerValue, today: LocalDate): List<FormAnswerIssue> {
    val id = question.id
    val array = value.json as? JsonArray ?: return listOf(FormAnswerIssue(id, FormIssueCode.WRONG_TYPE))
    val config = question.list ?: return listOf(FormAnswerIssue(id, FormIssueCode.WRONG_TYPE))
    val maxItems = config.maxItems ?: FORM_LIST_DEFAULT_MAX_ITEMS
    if (array.size > maxItems) return listOf(FormAnswerIssue(id, FormIssueCode.LIST_TOO_LONG, params = FormIssueParams(max = maxItems)))
    val fields = config.fields.associateBy { it.id }
    val out = ArrayList<FormAnswerIssue>()
    array.forEachIndexed { i, element ->
        val item = element as? JsonObject
        if (item == null) {
            out += FormAnswerIssue(id, FormIssueCode.WRONG_TYPE, path = "[$i]")
            return@forEachIndexed
        }
        for ((key, raw) in item) {
            val path = "[$i].$key"
            val field = fields[key]
            if (field == null) {
                out += FormAnswerIssue(id, FormIssueCode.LIST_ITEM_UNKNOWN_FIELD, path = path)
                continue
            }
            if (raw is JsonNull) continue
            if (raw !is JsonPrimitive) {
                out += FormAnswerIssue(id, FormIssueCode.WRONG_TYPE, path = path)
                continue
            }
            checkScalar(field, null, FormAnswerValue.fromJson(raw), today, id, path)?.let { out += it }
        }
    }
    return out
}

private fun checkFiles(question: FormQuestion, value: FormAnswerValue): List<FormAnswerIssue> {
    val id = question.id
    val array = value.json as? JsonArray ?: return listOf(FormAnswerIssue(id, FormIssueCode.WRONG_TYPE))
    val maxFiles = question.file?.maxFiles ?: 0
    if (array.size > maxFiles) return listOf(FormAnswerIssue(id, FormIssueCode.FILES_TOO_MANY, params = FormIssueParams(max = maxFiles)))
    val seen = HashSet<String>()
    val out = ArrayList<FormAnswerIssue>()
    array.forEachIndexed { i, element ->
        val fileId = (element as? JsonObject)
            ?.takeIf { it.keys == setOf("fileId") }
            ?.get("fileId")
            ?.textOrNull()
            ?.takeIf(::isCanonicalFileId)
        when {
            fileId == null -> out += FormAnswerIssue(id, FormIssueCode.FILE_REF_INVALID, path = "[$i]")
            !seen.add(fileId) -> out += FormAnswerIssue(id, FormIssueCode.FILE_DUPLICATE, path = "[$i]")
        }
    }
    return out
}

/** O que a frase de um problema de NÚMERO cita (os códigos que [formNumberIssue] devolve). */
private fun numberParams(code: FormIssueCode, config: FormNumberConfig?): FormIssueParams = when (code) {
    FormIssueCode.NUMBER_OUT_OF_RANGE -> FormIssueParams(minValue = config?.min, maxValue = config?.max)
    FormIssueCode.NUMBER_TOO_MANY_DECIMALS -> FormIssueParams(decimals = config?.effectiveDecimals ?: 0)
    else -> FormIssueParams(step = config?.step ?: FormDecimal.ONE)
}

private fun defaultMaxLength(type: FormQuestionType): Int =
    if (type == FormQuestionType.LONG_TEXT) FORM_LONG_TEXT_DEFAULT_MAX_LENGTH else FORM_TEXT_DEFAULT_MAX_LENGTH
