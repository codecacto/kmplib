package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.text.input.ImeAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `NumberField`/`DigitBoxField` com o texto no campo (2.262.1) — o defeito do Meu Controle no iOS:
 * "120" digitado rápido virava "10" porque cada dígito fazia a ida e volta pelo ViewModel.
 *
 * O campo é simulado com o MESMO motor do componente: `TextFieldState` + a
 * [NumberFieldInputTransformation] aplicada a cada tecla + o [TextInputReconciler] do
 * `rememberSyncedTextFieldState`, contra um ViewModel que devolve o valor atrasado.
 */
class NumberFieldInputTest {

    // --- filtro puro -------------------------------------------------------------------------

    @Test
    fun inteiroAceitaSoAlgarismos() {
        assertEquals("120", filterNumberFieldInput("1a2-0", allowDecimals = false, minValue = null, maxValue = null))
        assertEquals("", filterNumberFieldInput("abc", allowDecimals = false, minValue = null, maxValue = null))
        assertEquals("12", filterNumberFieldInput("1,2", allowDecimals = false, minValue = null, maxValue = null))
    }

    @Test
    fun decimalMantemUmaVirgulaSo() {
        assertEquals("1,25", filterNumberFieldInput("1,2,5", allowDecimals = true, minValue = null, maxValue = null))
        assertEquals(",", filterNumberFieldInput(",", allowDecimals = true, minValue = null, maxValue = null))
    }

    @Test
    fun pontoViraVirgulaQuandoNaoHaVirgula() {
        assertEquals("12,5", filterNumberFieldInput("12.5", allowDecimals = true, minValue = null, maxValue = null))
        // Com vírgula, o ponto é milhar colado e sai.
        assertEquals("1234,56", filterNumberFieldInput("1.234,56", allowDecimals = true, minValue = null, maxValue = null))
    }

    @Test
    fun foraDaFaixaRecusa() {
        assertNull(filterNumberFieldInput("101", allowDecimals = false, minValue = null, maxValue = 100.0))
        assertEquals("100", filterNumberFieldInput("100", allowDecimals = false, minValue = null, maxValue = 100.0))
        assertNull(filterNumberFieldInput("1", allowDecimals = false, minValue = 5.0, maxValue = null))
        assertEquals("", filterNumberFieldInput("", allowDecimals = false, minValue = 5.0, maxValue = null))
    }

    // --- transformação no buffer -----------------------------------------------------------------

    private fun TextFieldState.digitar(texto: String, t: InputTransformation) {
        texto.forEach { c ->
            edit {
                val buffer = this
                append(c.toString())
                with(t) { buffer.transformInput() }
            }
        }
    }

    @Test
    fun transformacaoFiltraERecusaNoProprioCampo() {
        val t = NumberFieldInputTransformation(allowDecimals = false, minValue = null, maxValue = 500.0)
        val state = TextFieldState("")
        state.digitar("1a2", t)
        assertEquals("12", state.text.toString())
        state.digitar("0", t)
        assertEquals("120", state.text.toString())
        // 1200 > 500: a tecla é desfeita, o texto fica.
        state.digitar("0", t)
        assertEquals("120", state.text.toString())
        assertEquals(3, state.selection.end)
    }

    @Test
    fun transformacaoDecimalTrocaPontoPorVirgula() {
        val t = NumberFieldInputTransformation(allowDecimals = true, minValue = null, maxValue = null)
        val state = TextFieldState("")
        state.digitar("3.5.0", t)
        assertEquals("3,50", state.text.toString())
    }

    // --- digitação rápida contra ViewModel atrasado ------------------------------------------------

    /** ViewModel que só devolve cada valor recebido alguns quadros depois. */
    private class ViewModelAtrasado(inicial: String) {
        var valor = inicial
            private set
        private val fila = ArrayDeque<String>()
        fun receber(v: String) = fila.addLast(v)
        fun quadro() { if (fila.isNotEmpty()) valor = fila.removeFirst() }
        fun drenar() { while (fila.isNotEmpty()) quadro() }
        fun definir(v: String) { fila.clear(); valor = v }
    }

    /** O campo como o `NumberField` monta: estado + filtro + reconciliação com o valor de fora. */
    private class Campo(private val vm: ViewModelAtrasado, private val t: InputTransformation) {
        val state = TextFieldState(vm.valor)
        private val reconciler = TextInputReconciler(vm.valor)
        val texto get() = state.text.toString()

        fun teclar(c: Char) {
            state.edit {
                val buffer = this
                append(c.toString())
                with(t) { buffer.transformInput() }
            }
            if (reconciler.onLocalText(texto)) vm.receber(texto)
        }

        /** A recomposição com o valor que o ViewModel tem agora (o `SideEffect`). */
        fun recompor() {
            reconciler.onExternal(vm.valor, texto)?.let { state.setTextAndPlaceCursorAtEnd(it) }
        }
    }

    @Test
    fun digitacaoRapidaNaoPerdeDigito() {
        val vm = ViewModelAtrasado("")
        val campo = Campo(vm, NumberFieldInputTransformation(false, null, null))
        // Três teclas antes de o ViewModel devolver a primeira, recompondo com o valor velho entre elas.
        campo.teclar('1'); campo.recompor()
        campo.teclar('2'); campo.recompor()
        vm.quadro(); campo.recompor() // eco atrasado de "1" chega com o campo em "12"
        campo.teclar('0'); campo.recompor()
        assertEquals("120", campo.texto)
        vm.drenar(); campo.recompor()
        assertEquals("120", campo.texto)
        assertEquals("120", vm.valor)
    }

    @Test
    fun digitacaoEmRajadaComVariosEcosAtrasados() {
        val vm = ViewModelAtrasado("")
        val campo = Campo(vm, NumberFieldInputTransformation(true, null, null))
        "1234,56".forEachIndexed { i, c ->
            campo.teclar(c)
            if (i % 2 == 1) vm.quadro()
            campo.recompor()
        }
        vm.drenar(); campo.recompor()
        assertEquals("1234,56", campo.texto)
        assertEquals("1234,56", vm.valor)
    }

    @Test
    fun resetExternoLimpaOCampo() {
        val vm = ViewModelAtrasado("")
        val campo = Campo(vm, NumberFieldInputTransformation(false, null, null))
        "350".forEach { campo.teclar(it); campo.recompor() }
        vm.drenar(); campo.recompor()
        vm.definir("") // "Limpar formulário"
        campo.recompor()
        assertEquals("", campo.texto)
        // E volta a digitar normalmente depois do reset.
        campo.teclar('7'); campo.recompor(); vm.drenar(); campo.recompor()
        assertEquals("7", campo.texto)
        assertEquals("7", vm.valor)
    }

    @Test
    fun valorExternoNovoPreencheOCampoComCursorNoFim() {
        val vm = ViewModelAtrasado("")
        val campo = Campo(vm, NumberFieldInputTransformation(false, null, null))
        vm.definir("42") // edição carregada do banco
        campo.recompor()
        assertEquals("42", campo.texto)
        assertEquals(2, campo.state.selection.end)
    }

    // --- DigitBoxField: algarismos locais ---------------------------------------------------------

    @Test
    fun digitBoxDigitacaoRapidaNaoPerdeDigito() {
        val vm = ViewModelAtrasado("")
        val reconciler = TextInputReconciler("")
        var local = ""
        fun teclar(c: Char) {
            val r = applyDigitBoxInput(local.take(4), local.take(4) + c, 4)
            if (r is DigitBoxInput.Accepted && r.value != local) {
                local = r.value
                if (reconciler.onLocalText(local)) vm.receber(local)
            }
        }
        fun recompor() { reconciler.onExternal(vm.valor, local)?.let { local = it } }
        "1234".forEachIndexed { i, c -> teclar(c); if (i == 2) vm.quadro(); recompor() }
        assertEquals("1234", local)
        vm.drenar(); recompor()
        assertEquals("1234", vm.valor)
        vm.definir(""); recompor()
        assertEquals("", local)
    }

    // --- KeyboardActions → handler ---------------------------------------------------------------

    @Test
    fun keyboardActionsSemCallbackUsaOPadrao() {
        assertNull(KeyboardActions.Default.toKeyboardActionHandler(ImeAction.Done))
        assertNull(KeyboardActions(onNext = {}).toKeyboardActionHandler(ImeAction.Done))
    }

    @Test
    fun keyboardActionsChamaOCallbackDaAcaoDoCampo() {
        var feito = 0
        var padrao = 0
        val handler = KeyboardActions(onDone = { feito++; defaultKeyboardAction(ImeAction.Done) })
            .toKeyboardActionHandler(ImeAction.Done)
        assertNotNull(handler)
        handler.onKeyboardAction { padrao++ }
        assertEquals(1, feito)
        assertEquals(1, padrao)
        assertTrue(KeyboardActions(onSearch = {}).toKeyboardActionHandler(ImeAction.Search) != null)
    }
}
