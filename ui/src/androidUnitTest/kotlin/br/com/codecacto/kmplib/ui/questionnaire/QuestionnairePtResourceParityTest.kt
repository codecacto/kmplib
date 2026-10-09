package br.com.codecacto.kmplib.ui.questionnaire

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O recurso pt-BR diz o mesmo que os defaults de [QuestionnaireTexts] — quem usa o default em teste
 * (ou fora de composição) e quem usa [rememberQuestionnaireTexts] num aparelho em português veem a
 * mesma frase. A paridade das QUATRO pastas (chaves e argumentos) é do `LibStringResourcesParityTest`.
 */
class QuestionnairePtResourceParityTest {

    private val pt: Map<String, String> by lazy {
        val xml = File("src/commonMain/composeResources/values/strings.xml").readText()
        Regex("""<string name="(kmplib_questionnaire_[^"]+)">(.*?)</string>""").findAll(xml)
            .associate { it.groupValues[1].removePrefix("kmplib_questionnaire_") to it.groupValues[2] }
    }

    private fun fill(key: String, vararg args: Any) = fillQuestionnaireTemplate(pt.getValue(key), *args)

    @Test
    fun `defaults e recurso pt-BR dizem o mesmo`() {
        val d = QuestionnaireTexts()
        assertEquals(d.blockProgress(2, 5), fill("block_progress", 2, 5))
        assertEquals(d.questionProgress(1, 3), fill("question_progress", 1, 3))
        assertEquals(d.answered(4, 9), fill("answered", 4, 9))
        assertEquals(d.missing(1), pt["missing_one"])
        assertEquals(d.missing(4), fill("missing_other", 4))
        assertEquals(d.back, pt["back"])
        assertEquals(d.next, pt["next"])
        assertEquals(d.finish, pt["finish"])
        assertEquals(d.finishing, pt["finishing"])
        assertEquals(d.requiredScale, pt["required_scale"])
        assertEquals(d.requiredChoice, pt["required_choice"])
        assertEquals(d.requiredMultiChoice, pt["required_multi_choice"])
        assertEquals(d.requiredField, pt["required_field"])
        assertEquals(d.requiredDate, pt["required_date"])
        assertEquals(d.outOfRange("1", "9"), fill("out_of_range", "1", "9"))
        assertEquals(d.outOfRange("1", null), fill("at_least", "1"))
        assertEquals(d.outOfRange(null, "9"), fill("at_most", "9"))
        assertEquals(d.optional, pt["optional"])
        assertEquals(d.reservedPending, pt["reserved_pending"])
        assertEquals(d.filledBy("Ana"), fill("filled_by", "Ana"))
        assertEquals(d.scoreIncomplete(1, 2), fill("score_incomplete", 1, 2))
        assertEquals(d.dateLabel, pt["date_label"])
        assertEquals(d.unavailable, pt["unavailable"])
        assertEquals(d.unsavedAnswers, pt["unsaved"])
        assertEquals(d.likert.option, pt["option"])
        assertEquals(d.likert.of, pt["of"])
        assertEquals(d.likert.unanswered, pt["unanswered"])
        assertEquals(d.likert.invalidScale, pt["invalid_scale"])
        assertEquals(28, pt.size, "chave nova no recurso precisa entrar nos defaults (e neste teste)")
    }
}
