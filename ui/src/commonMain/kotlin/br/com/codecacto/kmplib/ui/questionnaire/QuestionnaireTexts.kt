@file:Suppress("DEPRECATION")

package br.com.codecacto.kmplib.ui.questionnaire

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import br.com.codecacto.kmplib.core.locale.RegionalFormat
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_answered
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_at_least
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_at_most
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_back
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_block_progress
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_date_label
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_filled_by
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_finish
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_finishing
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_invalid_scale
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_missing_one
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_missing_other
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_next
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_of
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_option
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_optional
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_out_of_range
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_question_progress
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_required_choice
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_required_date
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_required_field
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_required_multi_choice
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_required_scale
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_reserved_pending
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_score_incomplete
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_unanswered
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_unavailable
import br.com.codecacto.kmplib.generated.resources.kmplib_questionnaire_unsaved
import br.com.codecacto.kmplib.ui.components.LikertScaleTexts
import org.jetbrains.compose.resources.stringResource

/**
 * O vocabulário do runner — o `QuestionnaireRunnerLabels` da weblib, mais o que os tipos novos
 * pedem. Os defaults são pt-BR (para teste e uso fora de composição); na tela, o default é
 * [rememberQuestionnaireTexts], nos 4 idiomas da fábrica.
 *
 * Enunciado, opções, âncoras e rótulo de faixa NÃO moram aqui: são conteúdo do modelo, vêm do
 * servidor. [reservedPending] e [filledBy] são genéricos de propósito — o app troca pela frase do
 * produto (`copy(reservedPending = "Pendente — perguntar na consulta")`).
 */
@Immutable
data class QuestionnaireTexts(
    /** `(atual, total)` → "Bloco 2 de 5". 1-based. */
    val blockProgress: (current: Int, total: Int) -> String = { c, t -> "Bloco $c de $t" },
    /** `(atual, total)` → "Pergunta 3 de 6". 1-based, dentro do bloco. */
    val questionProgress: (current: Int, total: Int) -> String = { c, t -> "Pergunta $c de $t" },
    /** `(respondidas, total)` → "12 de 28 respondidas". */
    val answered: (answered: Int, total: Int) -> String = { a, t -> "$a de $t respondidas" },
    /** `(faltam)` → "Faltam 2 perguntas neste bloco." — dito FORA do botão, em região viva. */
    val missing: (missing: Int) -> String = { n ->
        if (n == 1) "Falta 1 pergunta neste bloco." else "Faltam $n perguntas neste bloco."
    },
    val back: String = "Voltar",
    val next: String = "Continuar",
    val finish: String = "Concluir",
    val finishing: String = "Concluindo…",
    /** Erro sob a régua (o `requiredError` da weblib). */
    val requiredScale: String = "Escolha um ponto da escala.",
    val requiredChoice: String = "Escolha uma opção.",
    val requiredMultiChoice: String = "Marque pelo menos uma opção.",
    val requiredField: String = "Preencha este campo.",
    val requiredDate: String = "Escolha uma data.",
    /** `(mínimo, máximo)` já formatados — um dos dois pode ser `null`. */
    val outOfRange: (min: String?, max: String?) -> String = { min, max ->
        when {
            min != null && max != null -> "Informe um valor entre $min e $max."
            min != null -> "Informe um valor a partir de $min."
            else -> "Informe um valor até $max."
        }
    },
    /** Marca de pergunta opcional (com a maioria obrigatória, é a opcional que se marca). */
    val optional: String = "Opcional",
    /** Marca da reservada sem resposta. */
    val reservedPending: String = "Pendente — perguntar pessoalmente",
    /** `(nome)` → "Respondida por Paciente". */
    val filledBy: (name: String) -> String = { "Respondida por $it" },
    /** `(respondidas, total)` de um escore incompleto. */
    val scoreIncomplete: (answered: Int, total: Int) -> String = { a, t -> "Incompleto: $a de $t respondidas" },
    /** Rótulo do campo de data. */
    val dateLabel: String = "Data",
    /** Pergunta que esta versão do app não sabe desenhar (tipo novo, régua impossível). */
    val unavailable: String = "Pergunta indisponível nesta versão do app.",
    /** Aviso para o runner quando a [QuestionnaireAnswerQueue] tem pendência (o default do `useAnswerQueue`). */
    val unsavedAnswers: String = "Suas respostas estão nesta tela, mas ainda não foram salvas.",
    /** Vocabulário da régua (leitor de tela) — o `LikertScaleTexts`, traduzido. */
    val likert: LikertScaleTexts = LikertScaleTexts(),
) {
    /** A frase de erro de [error] numa pergunta de [type]. */
    fun errorText(type: QuestionnaireQuestionType, error: QuestionnaireFieldError, decimals: Int = 2): String = when (error) {
        QuestionnaireFieldError.Required -> when (type) {
            QuestionnaireQuestionType.SCALE -> requiredScale
            QuestionnaireQuestionType.CHOICE -> requiredChoice
            QuestionnaireQuestionType.MULTI_CHOICE -> requiredMultiChoice
            QuestionnaireQuestionType.DATE -> requiredDate
            else -> requiredField
        }
        is QuestionnaireFieldError.OutOfRange -> outOfRange(
            error.min?.let { formatQuestionnaireNumber(it, decimals) },
            error.max?.let { formatQuestionnaireNumber(it, decimals) },
        )
    }
}

/** Número para a TELA, no formato da região (`72,5` no Brasil, `72.5` nos EUA). */
fun formatQuestionnaireNumber(value: Double, maxFractionDigits: Int = 2): String =
    RegionalFormat.formatNumber(value, minFractionDigits = 0, maxFractionDigits = maxFractionDigits.coerceIn(0, 6))

/** [QuestionnaireTexts] no idioma da tela (pt-BR, en, es, pt-PT). */
@Composable
fun rememberQuestionnaireTexts(): QuestionnaireTexts {
    val blockProgress = stringResource(Res.string.kmplib_questionnaire_block_progress)
    val questionProgress = stringResource(Res.string.kmplib_questionnaire_question_progress)
    val answered = stringResource(Res.string.kmplib_questionnaire_answered)
    val missingOne = stringResource(Res.string.kmplib_questionnaire_missing_one)
    val missingOther = stringResource(Res.string.kmplib_questionnaire_missing_other)
    val back = stringResource(Res.string.kmplib_questionnaire_back)
    val next = stringResource(Res.string.kmplib_questionnaire_next)
    val finish = stringResource(Res.string.kmplib_questionnaire_finish)
    val finishing = stringResource(Res.string.kmplib_questionnaire_finishing)
    val requiredScale = stringResource(Res.string.kmplib_questionnaire_required_scale)
    val requiredChoice = stringResource(Res.string.kmplib_questionnaire_required_choice)
    val requiredMulti = stringResource(Res.string.kmplib_questionnaire_required_multi_choice)
    val requiredField = stringResource(Res.string.kmplib_questionnaire_required_field)
    val requiredDate = stringResource(Res.string.kmplib_questionnaire_required_date)
    val outOfRange = stringResource(Res.string.kmplib_questionnaire_out_of_range)
    val atLeast = stringResource(Res.string.kmplib_questionnaire_at_least)
    val atMost = stringResource(Res.string.kmplib_questionnaire_at_most)
    val optional = stringResource(Res.string.kmplib_questionnaire_optional)
    val reserved = stringResource(Res.string.kmplib_questionnaire_reserved_pending)
    val filledBy = stringResource(Res.string.kmplib_questionnaire_filled_by)
    val scoreIncomplete = stringResource(Res.string.kmplib_questionnaire_score_incomplete)
    val dateLabel = stringResource(Res.string.kmplib_questionnaire_date_label)
    val unavailable = stringResource(Res.string.kmplib_questionnaire_unavailable)
    val unsaved = stringResource(Res.string.kmplib_questionnaire_unsaved)
    val option = stringResource(Res.string.kmplib_questionnaire_option)
    val of = stringResource(Res.string.kmplib_questionnaire_of)
    val unanswered = stringResource(Res.string.kmplib_questionnaire_unanswered)
    val invalidScale = stringResource(Res.string.kmplib_questionnaire_invalid_scale)
    return remember(
        blockProgress, questionProgress, answered, missingOne, missingOther, back, next, finish, finishing,
        requiredScale, requiredChoice, requiredMulti, requiredField, requiredDate, outOfRange, atLeast,
        atMost, optional, reserved, filledBy, scoreIncomplete, dateLabel, unavailable, unsaved, option, of,
        unanswered, invalidScale,
    ) {
        QuestionnaireTexts(
            blockProgress = { c, t -> fillQuestionnaireTemplate(blockProgress, c, t) },
            questionProgress = { c, t -> fillQuestionnaireTemplate(questionProgress, c, t) },
            answered = { a, t -> fillQuestionnaireTemplate(answered, a, t) },
            missing = { n -> if (n == 1) missingOne else fillQuestionnaireTemplate(missingOther, n) },
            back = back,
            next = next,
            finish = finish,
            finishing = finishing,
            requiredScale = requiredScale,
            requiredChoice = requiredChoice,
            requiredMultiChoice = requiredMulti,
            requiredField = requiredField,
            requiredDate = requiredDate,
            outOfRange = { min, max ->
                when {
                    min != null && max != null -> fillQuestionnaireTemplate(outOfRange, min, max)
                    min != null -> fillQuestionnaireTemplate(atLeast, min)
                    else -> fillQuestionnaireTemplate(atMost, max.orEmpty())
                }
            },
            optional = optional,
            reservedPending = reserved,
            filledBy = { name -> fillQuestionnaireTemplate(filledBy, name) },
            scoreIncomplete = { a, t -> fillQuestionnaireTemplate(scoreIncomplete, a, t) },
            dateLabel = dateLabel,
            unavailable = unavailable,
            unsavedAnswers = unsaved,
            likert = LikertScaleTexts(option = option, of = of, unanswered = unanswered, invalidScale = invalidScale),
        )
    }
}

/** Preenche `%1$d`/`%2$d`/`%1$s`/`%2$s` — os textos são lambdas e não passam pelo formatador do recurso. */
internal fun fillQuestionnaireTemplate(template: String, vararg args: Any): String {
    var out = template
    args.forEachIndexed { i, arg ->
        val n = i + 1
        out = out.replace("%$n\$d", arg.toString()).replace("%$n\$s", arg.toString())
    }
    return out
}

/** Medidas e tempos do runner. */
object QuestionnaireDefaults {
    /** Espera do avanço automático no ritmo pergunta a pergunta — a mesma da weblib (260 ms). */
    const val AutoAdvanceDelayMillis: Long = 260L
}

/**
 * Ids de automação (`testTag`) do runner — Maestro toca por id, nunca por texto (4 idiomas).
 *
 * Os ids de pergunta são POR PERGUNTA: num bloco de 8 perguntas, um id fixo de opção apareceria 8
 * vezes e o teste tocaria na primeira — respondendo a pergunta errada e ficando verde.
 */
object QuestionnaireTestTags {
    const val ROOT: String = "questionario"
    const val STEP: String = "questionario-passo"
    const val BLOCK_TITLE: String = "questionario-bloco-titulo"
    const val PROGRESS: String = "questionario-progresso"
    const val NOTICE: String = "questionario-aviso"
    const val MISSING: String = "questionario-faltam"
    const val NEXT: String = "questionario-btn-continuar"
    const val BACK: String = "questionario-btn-voltar"
    const val BLOCK_RESERVED: String = "questionario-bloco-reservado"

    /** O cartão inteiro da pergunta. */
    fun question(questionId: String): String = "questionario-pergunta-$questionId"

    /** O controle de resposta (grupo de opções, régua ou campo). Na régua, é o prefixo do `LikertScaleField`. */
    fun answer(questionId: String): String = "questionario-$questionId"

    /** Uma opção (régua, escolha, múltipla). [value] é o `value` da opção ou o ponto da régua (`3`, `0.5`). */
    fun option(questionId: String, value: String): String = "questionario-$questionId-opcao-$value"

    /** A mensagem de erro da pergunta (a do campo de texto/número/data fica no próprio campo). */
    fun error(questionId: String): String = "questionario-$questionId-erro"

    fun reserved(questionId: String): String = "questionario-$questionId-reservada"
    fun filledBy(questionId: String): String = "questionario-$questionId-respondida-por"
    fun score(scoreId: String): String = "questionario-escore-$scoreId"
}
