package br.com.codecacto.kmplib.ui.form

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/** As regras de INTERFACE do runner — o `formRunner.logic` da weblib, regra a regra. */
class FormRunnerLogicTest {

    private fun d(text: String) = FormDecimal.parse(text)!!

    @Test
    fun `rascunho de numero - separador do idioma - casas - sinal`() {
        val peso = FormNumberConfig(min = d("20"), max = d("300"), decimals = 1)
        assertEquals("72,5", sanitizeFormNumberDraft("72.55", peso, ','), "casa além de decimals não entra")
        assertEquals("72,", sanitizeFormNumberDraft("72,", peso, ','))
        assertEquals("72.5", sanitizeFormNumberDraft("72,5", peso, '.'))
        assertEquals("725", sanitizeFormNumberDraft("7a2b5", peso, ','))
        assertEquals("72", sanitizeFormNumberDraft("-72", peso, ','), "min ≥ 0: sem sinal")
        val inteiro = FormNumberConfig(min = d("0"), max = d("7"))
        assertEquals("25", sanitizeFormNumberDraft("2,5", inteiro, ','), "sem decimals: não há separador a digitar")
        assertEquals("-3,5", sanitizeFormNumberDraft("-3.5", FormNumberConfig(decimals = 1), ','), "sem min: negativo vale")
        assertEquals("-3", sanitizeFormNumberDraft("-3-", FormNumberConfig(min = d("-10")), ','), "menos só no começo")
        assertEquals("1,57", sanitizeFormNumberDraft("1,5,7", FormNumberConfig(decimals = 2), ','), "um separador só")
    }

    @Test
    fun `rascunho vira numero exato ou nada`() {
        assertEquals(d("72.5"), parseFormNumberDraft("72,5"))
        assertEquals(d("72"), parseFormNumberDraft("72,"))
        assertEquals(d("0.5"), parseFormNumberDraft(",5"))
        assertEquals(d("-0.5"), parseFormNumberDraft("-,5"))
        assertNull(parseFormNumberDraft(""))
        assertNull(parseFormNumberDraft("-"))
        assertNull(parseFormNumberDraft(","))
        assertEquals("72,5", formatFormNumberDraft(d("72.50"), ','))
        assertEquals("", formatFormNumberDraft(null, ','))
        assertEquals("0,0001", formatFormNumberDraft(d("1e-4"), ','), "sem notação científica")
    }

    @Test
    fun `multipla - a exclusiva desmarca as outras e a ordem e a das opcoes`() {
        val options = listOf("febre", "tosse", "dor", "nenhum").map { FormChoiceOption(it, it) }
        val exclusive = listOf("nenhum")
        assertEquals(listOf("febre", "tosse"), toggleFormMultiChoice(listOf("tosse"), "febre", true, options, exclusive))
        assertEquals(listOf("nenhum"), toggleFormMultiChoice(listOf("febre", "tosse"), "nenhum", true, options, exclusive))
        assertEquals(listOf("dor"), toggleFormMultiChoice(listOf("nenhum"), "dor", true, options, exclusive))
        assertEquals(listOf("febre"), toggleFormMultiChoice(listOf("febre", "tosse"), "tosse", false, options, exclusive))
    }

    @Test
    fun `limpar o erro da edicao - so o do campo - o do item e o da pergunta toda`() {
        val issues = listOf(
            FormAnswerIssue("remedios", FormIssueCode.LIST_TOO_SHORT),
            FormAnswerIssue("remedios", FormIssueCode.LIST_ITEM_INCOMPLETE, "[1].nome"),
            FormAnswerIssue("remedios", FormIssueCode.LIST_ITEM_EMPTY, "[1]"),
            FormAnswerIssue("remedios", FormIssueCode.LIST_ITEM_INCOMPLETE, "[2].nome"),
        )
        assertEquals(listOf(issues[3]), clearFormIssuesOnEdit(issues, "[1].nome"))
        assertEquals(emptyList(), clearFormIssuesOnEdit(issues, null), "sem caminho: a pergunta inteira")
        assertEquals(emptyList(), clearFormIssuesOnEdit(null, "[0].x"))
    }

    @Test
    fun `anexo - tipo pelo mime ou pela extensao - teto em MB`() {
        val both = listOf(FormFileKind.IMAGE, FormFileKind.PDF)
        assertTrue(formFileMatchesAccept("exame.pdf", "application/pdf", both))
        assertTrue(formFileMatchesAccept("foto.HEIC", "", listOf(FormFileKind.IMAGE)))
        assertFalse(formFileMatchesAccept("planilha.xlsx", "application/vnd.ms-excel", both))
        assertFalse(formFileMatchesAccept("foto.jpg", "image/jpeg", listOf(FormFileKind.PDF)))
        assertTrue(formFileExceedsSize(10L * 1024 * 1024 + 1, d("10")))
        assertFalse(formFileExceedsSize(10L * 1024 * 1024, d("10")))
        assertEquals(1_572_864L, formFileMaxBytes(d("1.5")))
    }

    @Test
    fun `link do termo - javascript vira texto e relativo so com a origem do portal`() {
        assertEquals("https://x.com/t", safeFormLinkHref(" https://x.com/t "))
        assertNull(safeFormLinkHref("javascript:alert(1)"))
        assertNull(safeFormLinkHref("//evil.com"))
        assertEquals("/termos", safeFormLinkHref("/termos"))
        assertEquals("https://portal.x/termos", resolveFormLink("/termos", "https://portal.x/"))
        assertNull(resolveFormLink("/termos", null))
        assertNull(resolveFormLink("/termos", "ftp://portal.x"))
        assertEquals("mailto:dpo@x.com", resolveFormLink("mailto:dpo@x.com", null))
    }

    @Test
    fun `grade de duas colunas - pares de perguntas curtas consecutivas`() {
        fun q(id: String, type: FormQuestionType, display: FormChoiceDisplay? = null, maxLength: Int? = null) =
            FormQuestion(id = id, type = type, text = id, display = display, maxLength = maxLength)
        val questions = listOf(
            q("peso", FormQuestionType.NUMBER),
            q("altura", FormQuestionType.NUMBER),
            q("queixa", FormQuestionType.LONG_TEXT),
            q("nascimento", FormQuestionType.DATE),
            q("sexo", FormQuestionType.SINGLE_CHOICE, display = FormChoiceDisplay.SELECT),
            q("nome", FormQuestionType.TEXT, maxLength = 80),
            q("obs", FormQuestionType.TEXT),
        )
        assertEquals(
            listOf(listOf("peso", "altura"), listOf("queixa"), listOf("nascimento", "sexo"), listOf("nome"), listOf("obs")),
            gridRows(questions).map { row -> row.map { it.id } },
        )
    }

    @Test
    fun `etapas - retomada e progresso acompanham a visibilidade`() {
        val schema = FormJson.decodeSchema(
            """{"id":"f","title":"F","sections":[
                {"id":"a","title":"A","questions":[{"id":"gestante","type":"singleChoice","text":"G","options":[{"value":"sim","label":"S"},{"value":"nao","label":"N"}]},
                  {"id":"semanas","type":"number","text":"S","visibleIf":{"question":"gestante","op":"eq","value":"sim"}}]},
                {"id":"vazia","title":"V","questions":[{"id":"x","type":"text","text":"X","visibleIf":{"any":[]}}]},
                {"id":"b","title":"B","questions":[{"id":"aviso","type":"info","text":"I"},{"id":"aceite","type":"consent","text":"C","consent":{"documentVersion":"v1"}}]}]}""",
        )
        val today = LocalDate(2026, 10, 12)
        val semResposta = schema.evaluateVisibility(emptyMap())
        assertEquals(listOf("a", "b"), formRunnerSteps(schema, semResposta).map { it.sectionId }, "seção sem pergunta visível não é etapa")
        val steps = formRunnerSteps(schema, schema.evaluateVisibility(mapOf("gestante" to FormAnswerValue.text("sim"))))
        assertEquals(FormProgress(1, 3), formRunnerProgress(steps, mapOf("gestante" to FormAnswerValue.text("sim"))), "abrir 'semanas' soma ao total")
        assertEquals(FormProgress(1, 3), formRunnerProgress(steps, mapOf("gestante" to FormAnswerValue.text("sim"), "aceite" to FormAnswerValue.bool(false))), "consentimento só conta aceito")
        assertEquals(1, resolveFormStepIndex(schema, formRunnerSteps(schema, semResposta), "vazia"), "seção sumida: a próxima visível")
        assertEquals("a", firstIncompleteFormStep(schema, emptyMap(), today))
        assertEquals("b", firstIncompleteFormStep(schema, mapOf("gestante" to FormAnswerValue.text("nao")), today))
        assertNull(firstIncompleteFormStep(schema, mapOf("gestante" to FormAnswerValue.text("nao"), "aceite" to FormAnswerValue.bool(true)), today))
    }
}
