package br.com.codecacto.kmplib.ui.form

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate

/*
 * Núcleo PURO da tela do `FormRunner` — o que é decisão de INTERFACE, separado do motor do FormSchema
 * (visibilidade, descarte, validação), que é o contrato das três libs. É o `formRunner.logic` da
 * weblib, regra a regra. Público para o projeto que monta a própria tela por cima do protótipo sem
 * reimplementar estas regras ("protótipo vence a biblioteca").
 */

/** Uma etapa: uma seção visível com as perguntas visíveis dela, na ordem. */
@Immutable
data class FormRunnerStep(
    val sectionId: String,
    val section: FormSection,
    val questions: List<FormQuestion>,
)

/**
 * As etapas — no celular, uma por tela. Seção visível SEM nenhuma pergunta visível não vira etapa
 * (uma tela em branco com "Continuar" é degrau sem conteúdo).
 */
fun formRunnerSteps(schema: FormSchemaV1, visibility: FormVisibility): List<FormRunnerStep> {
    val steps = ArrayList<FormRunnerStep>()
    val seen = HashSet<String>()
    for (section in schema.sections) {
        if (!visibility.isSectionVisible(section.id) || !seen.add(section.id)) continue
        val questions = section.questions.filter { visibility.isQuestionVisible(it.id) }.distinctBy { it.id }
        if (questions.isNotEmpty()) steps += FormRunnerStep(section.id, section, questions)
    }
    return steps
}

/**
 * A etapa que vale para um id pedido. A seção pode ter sumido (uma resposta escondeu-a): aí vale a
 * PRIMEIRA etapa visível que vem depois dela no documento — o lugar para onde a pessoa ia — ou, sem
 * nenhuma depois, a última.
 */
fun resolveFormStepIndex(schema: FormSchemaV1, steps: List<FormRunnerStep>, sectionId: String?): Int {
    if (steps.isEmpty() || sectionId == null) return 0
    val direct = steps.indexOfFirst { it.sectionId == sectionId }
    if (direct >= 0) return direct
    val order = schema.sections.map { it.id }
    val wanted = order.indexOf(sectionId)
    if (wanted < 0) return 0
    val after = steps.indexOfFirst { order.indexOf(it.sectionId) > wanted }
    return if (after >= 0) after else steps.lastIndex
}

/** Respondida PARA O PROGRESSO: consentimento só conta aceito (`false` é "ainda não"). */
fun countsAsAnswered(question: FormQuestion, value: FormAnswerValue?): Boolean =
    if (question.type == FormQuestionType.CONSENT) value?.booleanOrNull() == true else isFormAnswered(value)

/** Quantas perguntas de um escopo já têm resposta. */
@Immutable
data class FormProgress(val answered: Int, val total: Int) {
    /** `answered / total`, `0` sem pergunta. */
    val fraction: Float get() = if (total == 0) 0f else answered.toFloat() / total
}

/**
 * O progresso do formulário inteiro: perguntas visíveis que recebem resposta (no escopo de quem
 * responde, se houver) e quantas já têm resposta. O total ACOMPANHA a visibilidade — abrir a
 * pergunta "de quantas semanas?" acrescenta uma ao total.
 */
fun formRunnerProgress(
    steps: List<FormRunnerStep>,
    answers: Map<String, FormAnswerValue?>,
    scope: Set<String>? = null,
): FormProgress {
    var answered = 0
    var total = 0
    for (step in steps) {
        for (question in step.questions) {
            if (!question.isHandledByClient) continue
            if (scope != null && question.id !in scope) continue
            total++
            if (countsAsAnswered(question, answers[question.id])) answered++
        }
    }
    return FormProgress(answered, total)
}

/** Perguntas da etapa que entram na conferência (no escopo, e que o cliente confere). */
fun stepQuestionIds(step: FormRunnerStep, scope: Set<String>? = null): List<String> =
    step.questions.filter { it.isHandledByClient && (scope == null || it.id in scope) }.map { it.id }

/**
 * O id da seção da PRIMEIRA etapa com pendência no envio (obrigatória sem resposta, formato,
 * consentimento), ou `null` com tudo pronto — o passo natural de quem retoma um formulário salvo pela
 * metade.
 */
fun firstIncompleteFormStep(
    schema: FormSchemaV1,
    answers: Map<String, FormAnswerValue?>,
    today: LocalDate = formToday(),
    context: FormContext? = null,
    onlyQuestionIds: Collection<String>? = null,
    requireConsent: Boolean = true,
): String? {
    val first = schema.validateSubmission(answers, today, context, onlyQuestionIds, requireConsent).issues.firstOrNull()
        ?: return null
    return schema.sectionOf(first.questionId)?.id
}

/** Problemas agrupados por pergunta, na ordem em que vieram. */
fun groupFormIssues(issues: List<FormAnswerIssue>): Map<String, List<FormAnswerIssue>> =
    issues.groupByTo(LinkedHashMap()) { it.questionId }

/**
 * Tira os problemas que uma edição resolveu. Sem [path], a pergunta inteira; com [path] (`"[1].dose"`),
 * só o daquele campo, o do item (`"[1]"`) e os da pergunta toda (sem path — um item a mais pode
 * resolver o "inclua pelo menos 2").
 */
fun clearFormIssuesOnEdit(issues: List<FormAnswerIssue>?, path: String? = null): List<FormAnswerIssue> {
    if (issues == null || path == null) return emptyList()
    val item = ITEM_PREFIX.find(path)?.value
    return issues.filter { it.path != null && it.path != path && it.path != item }
}

private val ITEM_PREFIX = Regex("""^\[\d+]""")

/** Reservada para quem vê: a pergunta ou a seção inteira (o servidor já mandou o EFETIVO). */
fun isReservedFormQuestion(question: FormQuestion, section: FormSection): Boolean = question.reserved || section.reserved

/** Reservadas da etapa ainda sem resposta — o "pendente — perguntar na consulta" do médico. */
fun pendingReservedIds(step: FormRunnerStep, answers: Map<String, FormAnswerValue?>): List<String> =
    step.questions
        .filter { it.isHandledByClient && isReservedFormQuestion(it, step.section) && !countsAsAnswered(it, answers[it.id]) }
        .map { it.id }

// ── Escolha ─────────────────────────────────────────────────────────────────────────────────────

/**
 * Marca/desmarca uma opção respeitando as exclusivas ("nenhuma" desmarca as outras, e vice-versa). A
 * lista sai na ordem das OPÇÕES, não na do toque: a mesma seleção grava sempre a mesma lista.
 */
fun toggleFormMultiChoice(
    current: List<String>,
    value: String,
    checked: Boolean,
    options: List<FormChoiceOption>,
    exclusive: List<String> = emptyList(),
): List<String> {
    val next: List<String> = when {
        !checked -> current.filter { it != value }
        value in exclusive -> listOf(value)
        else -> current.filter { it !in exclusive && it != value } + value
    }
    val order = options.map { it.value }
    return next.sortedBy { order.indexOf(it) }
}

/**
 * O tipo de pergunta ocupa MEIA largura na grade da tela larga (número, data, seleção, texto curto);
 * o resto, a largura toda.
 */
fun isCompactFormQuestion(question: FormQuestion): Boolean = when (question.type) {
    FormQuestionType.NUMBER, FormQuestionType.DATE -> true
    FormQuestionType.SINGLE_CHOICE -> question.display == FormChoiceDisplay.SELECT
    FormQuestionType.TEXT -> (question.maxLength ?: FORM_TEXT_DEFAULT_MAX_LENGTH) <= 120
    else -> false
}

// ── Número ──────────────────────────────────────────────────────────────────────────────────────

/**
 * O que a pessoa digitou, limpo enquanto digita: só dígitos, UM separador decimal (vírgula ou ponto,
 * convertido para [separator]), menos só no começo e só se a faixa admite negativo, e no máximo as
 * casas permitidas (sem `decimals`, inteiro — não há separador a digitar). Assim o rascunho sempre
 * vira número ou vazio, e "casas demais" não chega a existir.
 */
fun sanitizeFormNumberDraft(text: String, config: FormNumberConfig?, separator: Char): String {
    val decimals = config?.effectiveDecimals ?: 0
    val allowNegative = formNumberAllowsNegative(config)
    val out = StringBuilder()
    var seenSeparator = false
    var fraction = 0
    for (ch in text.trim()) {
        when {
            ch in '0'..'9' -> {
                if (seenSeparator) {
                    if (fraction >= decimals) continue
                    fraction++
                }
                out.append(ch)
            }
            (ch == ',' || ch == '.') && decimals > 0 && !seenSeparator -> {
                seenSeparator = true
                out.append(separator)
            }
            ch == '-' && allowNegative && out.isEmpty() -> out.append('-')
        }
    }
    return out.toString()
}

/** A faixa admite negativo: sem `min`, ou `min < 0`. */
fun formNumberAllowsNegative(config: FormNumberConfig?): Boolean = config?.min == null || config.min.signum < 0

/**
 * Rascunho → número EXATO (`"72,5"`/`"72.5"` → `72.5`); vazio, `"-"` ou só o separador → `null`. O
 * separador pendurado no fim ("72,") vale o inteiro.
 */
fun parseFormNumberDraft(text: String): FormDecimal? {
    val normalized = text.trim().replace(',', '.').removeSuffix(".")
    if (normalized.none { it in '0'..'9' }) return null
    val candidate = if (normalized.startsWith(".")) "0$normalized" else if (normalized.startsWith("-.")) "-0" + normalized.drop(1) else normalized
    return FormDecimal.parse(candidate)
}

/** Número → texto do campo, sem notação científica e com [separator] (`72.5` → `"72,5"`). */
fun formatFormNumberDraft(value: FormDecimal?, separator: Char): String =
    value?.toPlainString()?.replace('.', separator).orEmpty()

// ── Anexo ───────────────────────────────────────────────────────────────────────────────────────

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "tif", "tiff", "avif")

/**
 * O arquivo é de um tipo aceito — pelo MIME e, quando ele não vem (HEIC em alguns Androids, arquivo de
 * nuvem), pela extensão. A conferência pelos BYTES é do servidor.
 */
fun formFileMatchesAccept(name: String?, mimeType: String?, accept: List<FormFileKind>): Boolean {
    val type = mimeType.orEmpty().lowercase()
    val ext = name.orEmpty().lowercase().substringAfterLast('.', "")
    if (FormFileKind.IMAGE in accept && (type.startsWith("image/") || ext in IMAGE_EXTENSIONS)) return true
    return FormFileKind.PDF in accept && (type == "application/pdf" || ext == "pdf")
}

/** Passa do teto em MB (1 MB = 1024 × 1024 bytes — a régua mais folgada; o servidor tem a dele). */
fun formFileExceedsSize(sizeBytes: Long, maxSizeMb: FormDecimal): Boolean =
    maxSizeMb.signum > 0 && sizeBytes >= 0 && sizeBytes > formFileMaxBytes(maxSizeMb)

/** O teto em bytes de [maxSizeMb] (para o seletor de arquivo recusar antes de ler). */
fun formFileMaxBytes(maxSizeMb: FormDecimal): Long = (maxSizeMb.toDouble() * 1024.0 * 1024.0).toLong()

/**
 * Hrefs que um link de termo pode ter. O schema vem do servidor, mas um `javascript:` num template
 * mal revisado executaria no toque — defesa em profundidade: o resto vira texto. Caminho relativo
 * (`/termos`) só abre com a origem do portal ([resolveFormLink]).
 */
fun safeFormLinkHref(href: String): String? {
    val h = href.trim()
    if (Regex("^(https?:|mailto:)", RegexOption.IGNORE_CASE).containsMatchIn(h)) return h
    if (h.isNotEmpty() && h[0] in "/#?." && !h.startsWith("//")) return h
    return null
}

/**
 * O endereço que o app abre para um link de termo: absoluto como está; relativo (`/termos`) contra
 * [baseUrl] (a origem do portal); sem base, `null` — o link vira texto.
 */
fun resolveFormLink(href: String, baseUrl: String?): String? {
    val safe = safeFormLinkHref(href) ?: return null
    if (Regex("^(https?:|mailto:)", RegexOption.IGNORE_CASE).containsMatchIn(safe)) return safe
    val base = baseUrl?.trim()?.trimEnd('/')?.takeIf { Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(it) } ?: return null
    return if (safe.startsWith("/")) base + safe else "$base/$safe"
}
