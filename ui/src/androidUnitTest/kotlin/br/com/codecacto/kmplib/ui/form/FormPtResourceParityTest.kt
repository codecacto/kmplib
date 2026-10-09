package br.com.codecacto.kmplib.ui.form

import br.com.codecacto.kmplib.ui.questionnaire.fillQuestionnaireTemplate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O recurso pt-BR diz o mesmo que os defaults de [FormRunnerTexts] e [FormIssueMessages] — quem usa o
 * default (teste, fora de composição) e quem usa `remember…()` num aparelho em português veem a mesma
 * frase. A paridade das QUATRO pastas (chaves e argumentos) é do `LibStringResourcesParityTest`.
 */
class FormPtResourceParityTest {

    private val pt: Map<String, String> by lazy {
        val xml = File("src/commonMain/composeResources/values/strings.xml").readText()
        Regex("""<string name="kmplib_form_([^"]+)">(.*?)</string>""").findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun fill(key: String, vararg args: Any) = fillQuestionnaireTemplate(pt.getValue(key), *args)

    @Test
    fun `textos da moldura - defaults e recurso pt-BR dizem o mesmo`() {
        val d = FormRunnerTexts()
        val image = listOf(FormFileKind.IMAGE)
        val pdf = listOf(FormFileKind.PDF)
        val both = listOf(FormFileKind.IMAGE, FormFileKind.PDF)
        assertEquals(d.stepProgress(2, 6), fill("step_progress", 2, 6))
        assertEquals(d.questionProgress(1, 3), fill("question_progress", 1, 3))
        assertEquals(d.estimate(8), fill("estimate", 8))
        assertEquals(d.answered(4, 9), fill("answered", 4, 9))
        assertEquals(d.missing(1), pt["missing_one"])
        assertEquals(d.missing(3), fill("missing_other", 3))
        assertEquals(d.needsAttention(1), pt["needs_attention_one"])
        assertEquals(d.needsAttention(2), fill("needs_attention_other", 2))
        assertEquals(d.back, pt["back"])
        assertEquals(d.next, pt["next"])
        assertEquals(d.submit, pt["submit"])
        assertEquals(d.submitting, pt["submitting"])
        assertEquals(d.stepsNav, pt["steps_nav"])
        assertEquals(d.stepDone, pt["step_done"])
        assertEquals(d.stepHasIssues, pt["step_has_issues"])
        assertEquals(d.optional, pt["optional"])
        assertEquals(d.reserved, pt["reserved"])
        assertEquals(d.reservedPending, pt["reserved_pending"])
        assertEquals(d.reservedPendingCount(1), pt["reserved_pending_count_one"])
        assertEquals(d.reservedPendingCount(2), fill("reserved_pending_count_other", 2))
        assertEquals(d.selectPlaceholder, pt["select_placeholder"])
        assertEquals(d.listAdd("Medicamento"), fill("list_add", "medicamento"))
        assertEquals(d.listItemTitle("Medicamento", 2), fill("list_item_title", "Medicamento", 2))
        assertEquals(d.listRemove, pt["list_remove"])
        assertEquals(d.listRemoveItem("Medicamento", 2), fill("list_remove_item", "medicamento", 2))
        assertEquals(d.fileAddPhoto, pt["file_add_photo"])
        assertEquals(d.fileAddPdf, pt["file_add_pdf"])
        assertEquals(d.fileHint(image, "10"), fill("file_hint_image", "10"))
        assertEquals(d.fileHint(pdf, "10"), fill("file_hint_pdf", "10"))
        assertEquals(d.fileHint(both, "10"), fill("file_hint_both", "10"))
        assertEquals(d.fileUploading("a.pdf"), fill("file_uploading", "a.pdf"))
        assertEquals(d.fileFallbackName(2), fill("file_fallback_name", 2))
        assertEquals(d.fileRemove("a.pdf"), fill("file_remove", "a.pdf"))
        assertEquals(d.fileRetry, pt["file_retry"])
        assertEquals(d.fileUploadError, pt["file_upload_error"])
        assertEquals(d.fileTooBig("10"), fill("file_too_big", "10"))
        assertEquals(d.fileWrongType(image), pt["file_wrong_type_image"])
        assertEquals(d.fileWrongType(pdf), pt["file_wrong_type_pdf"])
        assertEquals(d.fileWrongType(both), pt["file_wrong_type_both"])
        assertEquals(d.fileUnreadable, pt["file_unreadable"])
        assertEquals(d.fileCameraDenied, pt["file_camera_denied"])
        assertEquals(d.fileCameraUnavailable, pt["file_camera_unavailable"])
        assertEquals(d.filesPending, pt["files_pending"])
        assertEquals(d.opensExternally, pt["opens_externally"])
        assertEquals(d.scoreIncomplete, pt["score_incomplete"])
        assertEquals(d.scoreLabel("PHQ-2", "3", null), fill("score_label", "PHQ-2", "3"))
        assertEquals(d.scoreLabel("PHQ-2", "3", "Positivo"), fill("score_label_band", "PHQ-2", "3", "Positivo"))
        assertEquals(d.unsupported, pt["unsupported"])
        assertEquals(d.unsavedAnswers, pt["unsaved"])
        assertEquals(d.dateLabel, pt["date_label"])
    }

    @Test
    fun `frases dos problemas - defaults e recurso pt-BR dizem o mesmo`() {
        val m = FormIssueMessages()
        assertEquals(m.unknownQuestion, pt["issue_unknown_question"])
        assertEquals(m.notAnswerable, pt["issue_not_answerable"])
        assertEquals(m.wrongType, pt["issue_wrong_type"])
        assertEquals(m.textInvalidCharacter, pt["issue_text_invalid_character"])
        assertEquals(m.textTooLong("5"), fill("issue_text_too_long", "5"))
        assertEquals(m.numberBetween("1", "9"), fill("issue_number_between", "1", "9"))
        assertEquals(m.numberFrom("1"), fill("issue_number_from", "1"))
        assertEquals(m.numberUpTo("9"), fill("issue_number_up_to", "9"))
        assertEquals(m.numberOutOfRange, pt["issue_number_out_of_range"])
        assertEquals(m.numberInteger, pt["issue_number_integer"])
        assertEquals(m.numberDecimals(1), pt["issue_number_decimals_one"])
        assertEquals(m.numberDecimals(3), fill("issue_number_decimals_other", 3))
        assertEquals(m.numberStep("2,5"), fill("issue_number_step", "2,5"))
        assertEquals(m.dateInvalid, pt["issue_date_invalid"])
        assertEquals(m.dateBetween("a", "b"), fill("issue_date_between", "a", "b"))
        assertEquals(m.dateFrom("a"), fill("issue_date_from", "a"))
        assertEquals(m.dateUpTo("b"), fill("issue_date_up_to", "b"))
        assertEquals(m.dateOutOfRange, pt["issue_date_out_of_range"])
        assertEquals(m.optionInvalid, pt["issue_option_invalid"])
        assertEquals(m.optionDuplicate, pt["issue_option_duplicate"])
        assertEquals(m.optionExclusive, pt["issue_option_exclusive"])
        assertEquals(m.likertOffScale, pt["issue_likert_off_scale"])
        assertEquals(m.listTooLong(1), pt["issue_list_too_long_one"])
        assertEquals(m.listTooLong(3), fill("issue_list_too_long_other", 3))
        assertEquals(m.listItemUnknownField, pt["issue_list_item_unknown_field"])
        assertEquals(m.filesTooMany(1), pt["issue_files_too_many_one"])
        assertEquals(m.filesTooMany(3), fill("issue_files_too_many_other", 3))
        assertEquals(m.fileRefInvalid, pt["issue_file_ref_invalid"])
        assertEquals(m.fileDuplicate, pt["issue_file_duplicate"])
        assertEquals(m.required, pt["issue_required"])
        assertEquals(m.consentRequired, pt["issue_consent_required"])
        assertEquals(m.listTooShort(1), pt["issue_list_too_short_one"])
        assertEquals(m.listTooShort(2), fill("issue_list_too_short_other", 2))
        assertEquals(m.listItemIncomplete, pt["issue_list_item_incomplete"])
        assertEquals(m.listItemEmpty, pt["issue_list_item_empty"])
        assertEquals(85, pt.size, "chave nova no recurso precisa entrar nos defaults (e neste teste)")
    }
}
