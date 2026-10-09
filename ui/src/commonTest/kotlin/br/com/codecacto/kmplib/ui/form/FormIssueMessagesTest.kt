package br.com.codecacto.kmplib.ui.form

import br.com.codecacto.kmplib.ui.components.formatDateBr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/** A frase de cada código — o que o campo mostra (número e data no formato da região). */
class FormIssueMessagesTest {

    private val messages = FormIssueMessages()
    private val br: (FormDecimal) -> String = { formatFormNumber(it, ',') }
    private val brDate: (LocalDate) -> String = ::formatDateBr
    private fun d(text: String) = FormDecimal.parse(text)!!
    private fun msg(code: FormIssueCode, params: FormIssueParams = FormIssueParams()) =
        messages.messageFor(FormAnswerIssue("q", code, params = params), br, brDate)

    @Test
    fun `todo codigo tem frase`() {
        FormIssueCode.entries.forEach { code -> assertTrue(msg(code).isNotBlank(), code.name) }
    }

    @Test
    fun `faixa - casas e passo citam o limite no formato da regiao`() {
        assertEquals("Informe um valor entre 20 e 300,5.", msg(FormIssueCode.NUMBER_OUT_OF_RANGE, FormIssueParams(minValue = d("20"), maxValue = d("300.5"))))
        assertEquals("Informe um valor a partir de 1.", msg(FormIssueCode.NUMBER_OUT_OF_RANGE, FormIssueParams(minValue = d("1"))))
        assertEquals("Número fora do permitido.", msg(FormIssueCode.NUMBER_OUT_OF_RANGE))
        assertEquals("Informe um número inteiro.", msg(FormIssueCode.NUMBER_TOO_MANY_DECIMALS, FormIssueParams(decimals = 0)))
        assertEquals("Use no máximo 1 casa decimal.", msg(FormIssueCode.NUMBER_TOO_MANY_DECIMALS, FormIssueParams(decimals = 1)))
        assertEquals("Use no máximo 2 casas decimais.", msg(FormIssueCode.NUMBER_TOO_MANY_DECIMALS, FormIssueParams(decimals = 2)))
        assertEquals("Informe um valor em passos de 2,5.", msg(FormIssueCode.NUMBER_OFF_STEP, FormIssueParams(step = d("2.5"))))
        assertEquals("Use no máximo 500 caracteres.", msg(FormIssueCode.TEXT_TOO_LONG, FormIssueParams(max = 500)))
    }

    @Test
    fun `data e quantidade`() {
        assertEquals(
            "Informe uma data até 12/10/2026.",
            msg(FormIssueCode.DATE_OUT_OF_RANGE, FormIssueParams(maxDate = LocalDate(2026, 10, 12))),
        )
        assertEquals(
            "Informe uma data entre 01/01/1900 e 12/10/2026.",
            msg(FormIssueCode.DATE_OUT_OF_RANGE, FormIssueParams(minDate = LocalDate(1900, 1, 1), maxDate = LocalDate(2026, 10, 12))),
        )
        assertEquals("Inclua pelo menos 2 itens.", msg(FormIssueCode.LIST_TOO_SHORT, FormIssueParams(min = 2)))
        assertEquals("Inclua pelo menos 1 item.", msg(FormIssueCode.LIST_TOO_SHORT, FormIssueParams(min = 1)))
        assertEquals("Envie no máximo 1 arquivo.", msg(FormIssueCode.FILES_TOO_MANY, FormIssueParams(max = 1)))
    }

    @Test
    fun `a validacao entrega o que a frase cita`() {
        val schema = FormJson.decodeSchema(
            """{"id":"f","title":"F","sections":[{"id":"s","title":"S","questions":[
                {"id":"dum","type":"date","text":"DUM","date":{"max":"today"}},
                {"id":"dose","type":"number","text":"Dose","number":{"min":0,"max":10,"step":2.5,"decimals":1}}]}]}""",
        )
        val today = LocalDate(2026, 10, 12)
        val issues = schema.validateValues(
            mapOf("dum" to FormAnswerValue.text("2026-10-13"), "dose" to FormAnswerValue.number(d("6"))),
            today,
        ).issues
        assertEquals("Informe uma data até 12/10/2026.", messages.messageFor(issues[0], br, brDate))
        assertEquals("Informe um valor em passos de 2,5.", messages.messageFor(issues[1], br, brDate))
    }
}
