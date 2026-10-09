@file:Suppress("DEPRECATION")

package br.com.codecacto.kmplib.ui.questionnaire

import br.com.codecacto.kmplib.ui.components.LikertScaleTestTags
import br.com.codecacto.kmplib.ui.components.StatusTone
import kotlin.test.Test
import kotlin.test.assertEquals

/** Vocabulário, frases de erro e ids de automação do runner. */
class QuestionnaireTextsTest {

    private val texts = QuestionnaireTexts()

    @Test
    fun `defaults pt-BR - os da weblib`() {
        assertEquals("Bloco 2 de 5", texts.blockProgress(2, 5))
        assertEquals("Pergunta 3 de 6", texts.questionProgress(3, 6))
        assertEquals("12 de 28 respondidas", texts.answered(12, 28))
        assertEquals("Falta 1 pergunta neste bloco.", texts.missing(1))
        assertEquals("Faltam 3 perguntas neste bloco.", texts.missing(3))
        assertEquals("Escolha um ponto da escala.", texts.requiredScale, "o requiredError da weblib")
        assertEquals("Respondida por Paciente", texts.filledBy("Paciente"))
        assertEquals("Incompleto: 1 de 2 respondidas", texts.scoreIncomplete(1, 2))
    }

    @Test
    fun `frase de erro por tipo`() {
        val required = QuestionnaireFieldError.Required
        assertEquals(texts.requiredScale, texts.errorText(QuestionnaireQuestionType.SCALE, required))
        assertEquals(texts.requiredChoice, texts.errorText(QuestionnaireQuestionType.CHOICE, required))
        assertEquals(texts.requiredMultiChoice, texts.errorText(QuestionnaireQuestionType.MULTI_CHOICE, required))
        assertEquals(texts.requiredDate, texts.errorText(QuestionnaireQuestionType.DATE, required))
        assertEquals(texts.requiredField, texts.errorText(QuestionnaireQuestionType.NUMBER, required))
        assertEquals(texts.requiredField, texts.errorText(QuestionnaireQuestionType.TEXT, required))
        // Limites inteiros: o texto não depende do separador decimal da região do aparelho de teste.
        assertEquals("Informe um valor entre 0 e 7.", texts.errorText(QuestionnaireQuestionType.NUMBER, QuestionnaireFieldError.OutOfRange(0.0, 7.0)))
        assertEquals("Informe um valor a partir de 18.", texts.errorText(QuestionnaireQuestionType.NUMBER, QuestionnaireFieldError.OutOfRange(18.0, null)))
        assertEquals("Informe um valor até 300.", texts.errorText(QuestionnaireQuestionType.NUMBER, QuestionnaireFieldError.OutOfRange(null, 300.0)))
    }

    @Test
    fun `preenchimento dos modelos de recurso`() {
        assertEquals("Bloco 2 de 5", fillQuestionnaireTemplate("Bloco %1\$d de %2\$d", 2, 5))
        assertEquals("Respondida por Ana", fillQuestionnaireTemplate("Respondida por %1\$s", "Ana"))
        assertEquals("entre 1 e 9", fillQuestionnaireTemplate("entre %1\$s e %2\$s", "1", "9"))
        assertEquals("sem argumento", fillQuestionnaireTemplate("sem argumento"))
    }

    @Test
    fun `numero no formato do NumberField`() {
        assertEquals("72", questionnaireNumberFieldText(72.0))
        assertEquals("72,5", questionnaireNumberFieldText(72.5))
        assertEquals("0,25", questionnaireNumberFieldText(0.25))
        assertEquals("1,000001", questionnaireNumberFieldText(1.000001))
        assertEquals("3", questionnaireNumberFieldText(2.9999999999), "arredonda na 6ª casa, sem 2,9999999")
        assertEquals("-4,5", questionnaireNumberFieldText(-4.5))
        assertEquals("1234567", questionnaireNumberFieldText(1_234_567.0), "sem separador de milhar")
        assertEquals("", questionnaireNumberFieldText(Double.NaN))
    }

    @Test
    fun `ids de automacao por pergunta - e os da regua batem com o LikertScaleField`() {
        assertEquals("questionario-pergunta-q1", QuestionnaireTestTags.question("q1"))
        assertEquals("questionario-q1", QuestionnaireTestTags.answer("q1"))
        assertEquals(LikertScaleTestTags.option(QuestionnaireTestTags.answer("q1"), 3), QuestionnaireTestTags.option("q1", "3"))
        assertEquals(LikertScaleTestTags.error(QuestionnaireTestTags.answer("q1")), QuestionnaireTestTags.error("q1"))
        assertEquals("questionario-q1-reservada", QuestionnaireTestTags.reserved("q1"))
        assertEquals("questionario-q1-respondida-por", QuestionnaireTestTags.filledBy("q1"))
        assertEquals("questionario-escore-phq2", QuestionnaireTestTags.score("phq2"))
    }

    @Test
    fun `tom da faixa vira tom do tema`() {
        assertEquals(StatusTone.NEUTRAL, QuestionnaireTone.NEUTRAL.toStatusTone())
        assertEquals(StatusTone.SUCCESS, QuestionnaireTone.SUCCESS.toStatusTone())
        assertEquals(StatusTone.INFO, QuestionnaireTone.INFO.toStatusTone())
        assertEquals(StatusTone.WARNING, QuestionnaireTone.WARNING.toStatusTone())
        assertEquals(StatusTone.DANGER, QuestionnaireTone.DANGER.toStatusTone())
    }
}
