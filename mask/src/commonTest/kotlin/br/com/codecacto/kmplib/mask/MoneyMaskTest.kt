package br.com.codecacto.kmplib.mask

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.AnnotatedString
import br.com.codecacto.kmplib.core.currency.Money
import br.com.codecacto.kmplib.core.locale.RegionalFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** Comparado ao formatador oficial: o teste vale em qualquer região de quem roda. */
class MoneyMaskTest {

    private fun TextFieldState.digitar(texto: String, t: InputTransformation) = texto.forEach { c ->
        edit {
            val buffer = this
            append(c.toString())
            with(t) { buffer.transformInput() }
        }
    }

    private fun exibir(valor: String, t: OutputTransformation): String {
        val state = TextFieldState(valor)
        state.edit {
            val buffer = this
            with(t) { buffer.transformOutput() }
        }
        return state.text.toString()
    }

    @Test
    fun filtro_soAlgarismos_semZeroAEsquerda_comTeto() {
        assertEquals("123456", filterMoneyInput("R$ 1.234,56"))
        assertEquals("5", filterMoneyInput("0005"))
        assertEquals("", filterMoneyInput("000"))
        assertEquals("123", filterMoneyInput("12345", maxDigits = 3))
        assertEquals(15, filterMoneyInput("9".repeat(30)).length)
    }

    @Test
    fun conversao_entreAlgarismosEMoney() {
        assertEquals(Money(288000, "BRL"), moneyFromInputDigits("288000", "BRL"))
        assertEquals(Money(2880, "JPY"), moneyFromInputDigits("2880", "JPY"))
        assertNull(moneyFromInputDigits("", "BRL"))
        assertNull(moneyFromInputDigits("00", "BRL"))
        assertEquals("288000", Money(288000, "BRL").toMoneyInputDigits())
        assertEquals("", (null as Money?).toMoneyInputDigits())
        assertEquals("", Money(0, "BRL").toMoneyInputDigits())
        assertEquals("", Money(-5, "BRL").toMoneyInputDigits())
    }

    @Test
    fun exibicao_peloFormatadorDaPlataforma() {
        assertEquals(RegionalFormat.formatMoney(288000, "BRL"), formatMoneyInput("288000", "BRL", false))
        assertEquals(RegionalFormat.formatMoney(5, "USD"), formatMoneyInput("5", "USD", false))
        assertEquals(RegionalFormat.formatMoney(2880, "JPY"), formatMoneyInput("2880", "JPY", false))
        assertEquals("", formatMoneyInput("", "BRL", false))
        assertEquals(RegionalFormat.formatMoney(0, "EUR"), formatMoneyInput("", "EUR", true))
    }

    @Test
    fun visualTransformation_formataEMantemCursoNoFim() {
        val t = MoneyVisualTransformation("BRL")
        val r = t.filter(AnnotatedString("288000"))
        assertEquals(RegionalFormat.formatMoney(288000, "BRL"), r.text.text)
        assertEquals(r.text.length, r.offsetMapping.originalToTransformed(6))
        assertEquals(0, r.offsetMapping.originalToTransformed(0))
        assertEquals(6, r.offsetMapping.transformedToOriginal(r.text.length))
        assertEquals(0, r.offsetMapping.transformedToOriginal(0))

        val vazio = t.filter(AnnotatedString(""))
        assertEquals("", vazio.text.text)
        assertEquals(0, vazio.offsetMapping.originalToTransformed(0))

        val comZero = MoneyVisualTransformation("BRL", showZeroWhenEmpty = true).filter(AnnotatedString(""))
        assertEquals(RegionalFormat.formatMoney(0, "BRL"), comZero.text.text)

        assertEquals(MoneyVisualTransformation("BRL"), MoneyVisualTransformation("BRL"))
        assertEquals(MoneyVisualTransformation("BRL").hashCode(), MoneyVisualTransformation("BRL").hashCode())
        assertNotEquals(MoneyVisualTransformation("BRL"), MoneyVisualTransformation("USD"))
    }

    @Test
    fun estado_guardaSoAlgarismos() {
        val s = TextFieldState("")
        s.digitar("0R$2.880,00", MoneyInputTransformation())
        assertEquals("288000", s.text.toString())
        val curto = TextFieldState("")
        curto.digitar("123456", MoneyInputTransformation(maxDigits = 4))
        assertEquals("1234", curto.text.toString())
        assertEquals(MoneyInputTransformation(), MoneyInputTransformation())
        assertEquals(MoneyInputTransformation(3).hashCode(), MoneyInputTransformation(3).hashCode())
    }

    @Test
    fun saida_formataOEstado() {
        assertEquals(RegionalFormat.formatMoney(288000, "BRL"), exibir("288000", MoneyOutputTransformation("BRL")))
        assertEquals(RegionalFormat.formatMoney(2880, "JPY"), exibir("2880", MoneyOutputTransformation("JPY")))
        assertEquals("", exibir("", MoneyOutputTransformation("BRL")))
        assertEquals(RegionalFormat.formatMoney(0, "BRL"), exibir("", MoneyOutputTransformation("BRL", showZeroWhenEmpty = true)))
        assertEquals("abc", exibir("abc", MoneyOutputTransformation("BRL")))
        assertEquals(MoneyOutputTransformation("BRL"), MoneyOutputTransformation("BRL"))
        assertEquals(MoneyOutputTransformation("BRL", true).hashCode(), MoneyOutputTransformation("BRL", true).hashCode())
    }
}
