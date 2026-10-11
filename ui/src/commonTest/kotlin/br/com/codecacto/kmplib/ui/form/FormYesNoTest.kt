package br.com.codecacto.kmplib.ui.form

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/**
 * A pergunta sim/não (`yesNo`, backlib-forms 0.176.0 / fixtures v2) no lado do app: leitura, `false`
 * como resposta (progresso e obrigatória), as chaves de opção da tela e a diferença para `consent`.
 * A semântica de condição/visibilidade/descarte/validação é provada pelas fixtures compartilhadas.
 */
class FormYesNoTest {

    private val today = LocalDate(2026, 10, 12)

    private val schema = FormJson.decodeSchema(
        """
        {"schemaVersion":1,"id":"anamnese","title":"Anamnese","sections":[{"id":"s","title":"Saúde","questions":[
          {"id":"lesao","type":"yesNo","text":"Tem alguma lesão?"},
          {"id":"onde","type":"text","text":"Onde?","visibleIf":{"question":"lesao","op":"eq","value":true}},
          {"id":"fuma","type":"yesNo","text":"Fuma?","required":false}
        ]}]}
        """.trimIndent(),
    )

    @Test
    fun `yesNo e lido como YES_NO e volta igual no JSON`() {
        val question = schema.findQuestion("lesao")!!
        assertEquals(FormQuestionType.YES_NO, question.type)
        assertTrue(question.isHandledByClient)
        assertTrue("\"type\":\"yesNo\"" in FormJson.encodeSchema(schema))
        assertEquals(FormQuestionType.YES_NO, FormQuestionType.fromWireName("yesNo"))
    }

    @Test
    fun `false conta como respondida - no progresso e na obrigatoria`() {
        val question = schema.findQuestion("lesao")!!
        assertTrue(countsAsAnswered(question, FormAnswerValue.bool(false)))
        assertFalse(countsAsAnswered(question, null))
        val result = schema.validateSubmission(mapOf("lesao" to FormAnswerValue.bool(false)), today)
        assertTrue(result.issues.isEmpty(), result.issues.toString())
    }

    @Test
    fun `consent com false segue pendente - yesNo nao`() {
        val consent = FormQuestion(id = "aceite", type = FormQuestionType.CONSENT, text = "Aceito")
        assertFalse(countsAsAnswered(consent, FormAnswerValue.bool(false)))
    }

    @Test
    fun `true abre a dependente obrigatoria e texto nao vale como booleano`() {
        val opened = schema.validateSubmission(mapOf("lesao" to FormAnswerValue.bool(true)), today)
        assertEquals(listOf(FormAnswerIssue("onde", FormIssueCode.REQUIRED)), opened.issues)
        val wrong = schema.validateValues(mapOf("lesao" to FormAnswerValue.text("sim")), today)
        assertEquals(listOf(FormAnswerIssue("lesao", FormIssueCode.WRONG_TYPE)), wrong.issues)
    }

    @Test
    fun `progresso do formulario conta o nao`() {
        val answers = mapOf("lesao" to FormAnswerValue.bool(false), "fuma" to FormAnswerValue.bool(false))
        val steps = formRunnerSteps(schema, schema.evaluateVisibility(answers, null))
        assertEquals(FormProgress(answered = 2, total = 2), formRunnerProgress(steps, answers))
    }

    @Test
    fun `chaves de opcao da tela - sim e nao`() {
        assertEquals("sim", formYesNoOptionKey(true))
        assertEquals("nao", formYesNoOptionKey(false))
        assertEquals(true, formYesNoFromOptionKey("sim"))
        assertEquals(false, formYesNoFromOptionKey("nao"))
        assertNull(formYesNoFromOptionKey("talvez"))
        assertEquals("formulario-lesao-opcao-sim", FormRunnerTestTags.option("lesao", formYesNoOptionKey(true)))
    }

    @Test
    fun `reducer - escolher Nao grava false e o envio passa`() {
        var state = FormRunnerState.start(schema = schema, today = today)
        state = state.reduce(FormRunnerAction.Answer("lesao", FormAnswerValue.bool(false))).state
        assertEquals(FormAnswerValue.bool(false), state.answers["lesao"])
        assertTrue(schema.validateSubmission(state.answers, today).issues.isEmpty())
    }
}
