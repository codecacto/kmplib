package br.com.codecacto.kmplib.ui.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import br.com.codecacto.kmplib.core.locale.RegionalFormat
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_consent_required
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_date_between
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_date_from
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_date_invalid
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_date_out_of_range
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_date_up_to
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_file_duplicate
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_file_ref_invalid
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_files_too_many_one
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_files_too_many_other
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_likert_off_scale
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_list_item_empty
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_list_item_incomplete
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_list_item_unknown_field
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_list_too_long_one
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_list_too_long_other
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_list_too_short_one
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_list_too_short_other
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_not_answerable
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_number_between
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_number_decimals_one
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_number_decimals_other
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_number_from
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_number_integer
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_number_out_of_range
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_number_step
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_number_up_to
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_option_duplicate
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_option_exclusive
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_option_invalid
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_required
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_text_invalid_character
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_text_too_long
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_unknown_question
import br.com.codecacto.kmplib.generated.resources.kmplib_form_issue_wrong_type
import br.com.codecacto.kmplib.ui.questionnaire.fillQuestionnaireTemplate
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * As frases de cada problema — o que o CAMPO mostra embaixo dele, nos 4 idiomas da fábrica (o
 * `FORM_ISSUE_MESSAGES` da weblib, frase a frase). Os defaults são pt-BR (teste e uso fora de
 * composição); na tela, [rememberFormIssueMessages].
 *
 * Recebem número e data JÁ formatados (vírgula ou ponto, dd/mm ou mm/dd — pela REGIÃO do aparelho,
 * como todo número e data da kmplib). O servidor tem as próprias frases (pt-BR) no `details` do
 * erro; estas são as da tela, para o erro marcado no cliente antes de qualquer ida à rede.
 */
@Immutable
data class FormIssueMessages(
    val unknownQuestion: String = "Pergunta inexistente neste formulário.",
    val notAnswerable: String = "Este item é só informativo e não recebe resposta.",
    val wrongType: String = "Resposta em formato inválido.",
    val textInvalidCharacter: String = "O texto tem caracteres inválidos.",
    val textTooLong: (max: String) -> String = { "Use no máximo $it caracteres." },
    val numberBetween: (min: String, max: String) -> String = { min, max -> "Informe um valor entre $min e $max." },
    val numberFrom: (min: String) -> String = { "Informe um valor a partir de $it." },
    val numberUpTo: (max: String) -> String = { "Informe um valor até $it." },
    val numberOutOfRange: String = "Número fora do permitido.",
    val numberInteger: String = "Informe um número inteiro.",
    val numberDecimals: (decimals: Int) -> String = { if (it == 1) "Use no máximo 1 casa decimal." else "Use no máximo $it casas decimais." },
    val numberStep: (step: String) -> String = { "Informe um valor em passos de $it." },
    val dateInvalid: String = "Data inválida.",
    val dateBetween: (min: String, max: String) -> String = { min, max -> "Informe uma data entre $min e $max." },
    val dateFrom: (min: String) -> String = { "Informe uma data a partir de $it." },
    val dateUpTo: (max: String) -> String = { "Informe uma data até $it." },
    val dateOutOfRange: String = "Data fora do permitido.",
    val optionInvalid: String = "Escolha uma das opções.",
    val optionDuplicate: String = "Há opção repetida.",
    val optionExclusive: String = "Esta opção não pode ser marcada junto com outras.",
    val likertOffScale: String = "Escolha um ponto da escala.",
    val listTooLong: (max: Int) -> String = { if (it == 1) "Inclua no máximo 1 item." else "Inclua no máximo $it itens." },
    val listItemUnknownField: String = "Campo inexistente neste item.",
    val filesTooMany: (max: Int) -> String = { if (it == 1) "Envie no máximo 1 arquivo." else "Envie no máximo $it arquivos." },
    val fileRefInvalid: String = "Arquivo inválido. Envie o arquivo de novo.",
    val fileDuplicate: String = "Arquivo repetido.",
    val required: String = "Responda esta pergunta.",
    val consentRequired: String = "Aceite o termo para enviar.",
    val listTooShort: (min: Int) -> String = { if (it == 1) "Inclua pelo menos 1 item." else "Inclua pelo menos $it itens." },
    /** Campo obrigatório vazio DENTRO de um item da lista (aparece no próprio campo). */
    val listItemIncomplete: String = "Preencha este campo.",
    /** Item sem nenhum campo preenchido (aparece no item). */
    val listItemEmpty: String = "Este item está vazio. Preencha ou remova.",
) {
    /**
     * A frase de [issue].
     *
     * @param formatNumber número para a tela (default: separador decimal da REGIÃO, sem milhar).
     * @param formatDate data para a tela (default: [RegionalFormat.formatDate]).
     */
    fun messageFor(
        issue: FormAnswerIssue,
        formatNumber: (FormDecimal) -> String = ::formatFormNumber,
        formatDate: (LocalDate) -> String = { RegionalFormat.formatDate(it) },
    ): String {
        val p = issue.params
        return when (issue.code) {
            FormIssueCode.UNKNOWN_QUESTION -> unknownQuestion
            FormIssueCode.NOT_ANSWERABLE -> notAnswerable
            FormIssueCode.WRONG_TYPE -> wrongType
            FormIssueCode.TEXT_INVALID_CHARACTER -> textInvalidCharacter
            FormIssueCode.TEXT_TOO_LONG -> p.max?.let { textTooLong(it.toString()) } ?: wrongType
            FormIssueCode.NUMBER_OUT_OF_RANGE -> when {
                p.minValue != null && p.maxValue != null -> numberBetween(formatNumber(p.minValue), formatNumber(p.maxValue))
                p.minValue != null -> numberFrom(formatNumber(p.minValue))
                p.maxValue != null -> numberUpTo(formatNumber(p.maxValue))
                else -> numberOutOfRange
            }
            FormIssueCode.NUMBER_TOO_MANY_DECIMALS -> (p.decimals ?: 0).let { if (it == 0) numberInteger else numberDecimals(it) }
            FormIssueCode.NUMBER_OFF_STEP -> numberStep(formatNumber(p.step ?: FormDecimal.ONE))
            FormIssueCode.DATE_INVALID -> dateInvalid
            FormIssueCode.DATE_OUT_OF_RANGE -> when {
                p.minDate != null && p.maxDate != null -> dateBetween(formatDate(p.minDate), formatDate(p.maxDate))
                p.minDate != null -> dateFrom(formatDate(p.minDate))
                p.maxDate != null -> dateUpTo(formatDate(p.maxDate))
                else -> dateOutOfRange
            }
            FormIssueCode.OPTION_INVALID -> optionInvalid
            FormIssueCode.OPTION_DUPLICATE -> optionDuplicate
            FormIssueCode.OPTION_EXCLUSIVE -> optionExclusive
            FormIssueCode.LIKERT_OFF_SCALE -> likertOffScale
            FormIssueCode.LIST_TOO_LONG -> listTooLong(p.max ?: 0)
            FormIssueCode.LIST_ITEM_UNKNOWN_FIELD -> listItemUnknownField
            FormIssueCode.FILES_TOO_MANY -> filesTooMany(p.max ?: 0)
            FormIssueCode.FILE_REF_INVALID -> fileRefInvalid
            FormIssueCode.FILE_DUPLICATE -> fileDuplicate
            FormIssueCode.REQUIRED -> required
            FormIssueCode.CONSENT_REQUIRED -> consentRequired
            FormIssueCode.LIST_TOO_SHORT -> listTooShort(p.min ?: 0)
            FormIssueCode.LIST_ITEM_INCOMPLETE -> listItemIncomplete
            FormIssueCode.LIST_ITEM_EMPTY -> listItemEmpty
        }
    }
}

/**
 * Número para a TELA, exato e sem separador de milhar (`2,5` no Brasil, `2.5` nos EUA): o decimal do
 * schema sem passar por `Double`.
 */
fun formatFormNumber(value: FormDecimal, decimalSeparator: Char = RegionalFormat.decimalSeparator()): String =
    value.toPlainString().replace('.', decimalSeparator)

/** [FormIssueMessages] no idioma da tela (pt-BR, en, es, pt-PT). */
@Composable
fun rememberFormIssueMessages(): FormIssueMessages {
    val unknownQuestion = stringResource(Res.string.kmplib_form_issue_unknown_question)
    val notAnswerable = stringResource(Res.string.kmplib_form_issue_not_answerable)
    val wrongType = stringResource(Res.string.kmplib_form_issue_wrong_type)
    val textInvalid = stringResource(Res.string.kmplib_form_issue_text_invalid_character)
    val textTooLong = stringResource(Res.string.kmplib_form_issue_text_too_long)
    val numberBetween = stringResource(Res.string.kmplib_form_issue_number_between)
    val numberFrom = stringResource(Res.string.kmplib_form_issue_number_from)
    val numberUpTo = stringResource(Res.string.kmplib_form_issue_number_up_to)
    val numberOutOfRange = stringResource(Res.string.kmplib_form_issue_number_out_of_range)
    val numberInteger = stringResource(Res.string.kmplib_form_issue_number_integer)
    val numberDecimalsOne = stringResource(Res.string.kmplib_form_issue_number_decimals_one)
    val numberDecimalsOther = stringResource(Res.string.kmplib_form_issue_number_decimals_other)
    val numberStep = stringResource(Res.string.kmplib_form_issue_number_step)
    val dateInvalid = stringResource(Res.string.kmplib_form_issue_date_invalid)
    val dateBetween = stringResource(Res.string.kmplib_form_issue_date_between)
    val dateFrom = stringResource(Res.string.kmplib_form_issue_date_from)
    val dateUpTo = stringResource(Res.string.kmplib_form_issue_date_up_to)
    val dateOutOfRange = stringResource(Res.string.kmplib_form_issue_date_out_of_range)
    val optionInvalid = stringResource(Res.string.kmplib_form_issue_option_invalid)
    val optionDuplicate = stringResource(Res.string.kmplib_form_issue_option_duplicate)
    val optionExclusive = stringResource(Res.string.kmplib_form_issue_option_exclusive)
    val likertOffScale = stringResource(Res.string.kmplib_form_issue_likert_off_scale)
    val listTooLongOne = stringResource(Res.string.kmplib_form_issue_list_too_long_one)
    val listTooLongOther = stringResource(Res.string.kmplib_form_issue_list_too_long_other)
    val listItemUnknownField = stringResource(Res.string.kmplib_form_issue_list_item_unknown_field)
    val filesTooManyOne = stringResource(Res.string.kmplib_form_issue_files_too_many_one)
    val filesTooManyOther = stringResource(Res.string.kmplib_form_issue_files_too_many_other)
    val fileRefInvalid = stringResource(Res.string.kmplib_form_issue_file_ref_invalid)
    val fileDuplicate = stringResource(Res.string.kmplib_form_issue_file_duplicate)
    val required = stringResource(Res.string.kmplib_form_issue_required)
    val consentRequired = stringResource(Res.string.kmplib_form_issue_consent_required)
    val listTooShortOne = stringResource(Res.string.kmplib_form_issue_list_too_short_one)
    val listTooShortOther = stringResource(Res.string.kmplib_form_issue_list_too_short_other)
    val listItemIncomplete = stringResource(Res.string.kmplib_form_issue_list_item_incomplete)
    val listItemEmpty = stringResource(Res.string.kmplib_form_issue_list_item_empty)
    val all = listOf(
        unknownQuestion, notAnswerable, wrongType, textInvalid, textTooLong, numberBetween, numberFrom, numberUpTo,
        numberOutOfRange, numberInteger, numberDecimalsOne, numberDecimalsOther, numberStep, dateInvalid, dateBetween,
        dateFrom, dateUpTo, dateOutOfRange, optionInvalid, optionDuplicate, optionExclusive, likertOffScale,
        listTooLongOne, listTooLongOther, listItemUnknownField, filesTooManyOne, filesTooManyOther, fileRefInvalid,
        fileDuplicate, required, consentRequired, listTooShortOne, listTooShortOther, listItemIncomplete, listItemEmpty,
    )
    return remember(all) {
        FormIssueMessages(
            unknownQuestion = unknownQuestion,
            notAnswerable = notAnswerable,
            wrongType = wrongType,
            textInvalidCharacter = textInvalid,
            textTooLong = { fillQuestionnaireTemplate(textTooLong, it) },
            numberBetween = { min, max -> fillQuestionnaireTemplate(numberBetween, min, max) },
            numberFrom = { fillQuestionnaireTemplate(numberFrom, it) },
            numberUpTo = { fillQuestionnaireTemplate(numberUpTo, it) },
            numberOutOfRange = numberOutOfRange,
            numberInteger = numberInteger,
            numberDecimals = { if (it == 1) numberDecimalsOne else fillQuestionnaireTemplate(numberDecimalsOther, it) },
            numberStep = { fillQuestionnaireTemplate(numberStep, it) },
            dateInvalid = dateInvalid,
            dateBetween = { min, max -> fillQuestionnaireTemplate(dateBetween, min, max) },
            dateFrom = { fillQuestionnaireTemplate(dateFrom, it) },
            dateUpTo = { fillQuestionnaireTemplate(dateUpTo, it) },
            dateOutOfRange = dateOutOfRange,
            optionInvalid = optionInvalid,
            optionDuplicate = optionDuplicate,
            optionExclusive = optionExclusive,
            likertOffScale = likertOffScale,
            listTooLong = { if (it == 1) listTooLongOne else fillQuestionnaireTemplate(listTooLongOther, it) },
            listItemUnknownField = listItemUnknownField,
            filesTooMany = { if (it == 1) filesTooManyOne else fillQuestionnaireTemplate(filesTooManyOther, it) },
            fileRefInvalid = fileRefInvalid,
            fileDuplicate = fileDuplicate,
            required = required,
            consentRequired = consentRequired,
            listTooShort = { if (it == 1) listTooShortOne else fillQuestionnaireTemplate(listTooShortOther, it) },
            listItemIncomplete = listItemIncomplete,
            listItemEmpty = listItemEmpty,
        )
    }
}
