@file:Suppress("DEPRECATION") // compara com as VisualTransformation antigas, que ficaram @Deprecated

package br.com.codecacto.kmplib.mask

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.VisualTransformation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pares `Input`/`Output` de CPF, CNPJ, data, placa e CREFITO (2.329.0): digitação, colagem, apagar
 * no meio, cursor e a exibição idêntica à das `VisualTransformation` antigas.
 */
class TextFieldStateDocumentMasksTest {

    /** Digita [texto] um caractere por vez, na posição do cursor. */
    private fun TextFieldState.digitar(texto: String, t: InputTransformation) = texto.forEach { c ->
        edit {
            val buffer = this
            replace(selection.start, selection.end, c.toString())
            with(t) { buffer.transformInput() }
        }
    }

    private fun TextFieldState.colar(texto: String, t: InputTransformation) = edit {
        val buffer = this
        replace(selection.start, selection.end, texto)
        with(t) { buffer.transformInput() }
    }

    /** Backspace com o cursor em [posicao] (texto CRU do estado). */
    private fun TextFieldState.apagarAntesDe(posicao: Int, t: InputTransformation) {
        edit { selection = TextRange(posicao) }
        edit {
            val buffer = this
            delete(posicao - 1, posicao)
            with(t) { buffer.transformInput() }
        }
    }

    private fun TextFieldState.cursorEm(posicao: Int) = edit { selection = TextRange(posicao) }

    private fun exibir(valor: String, t: OutputTransformation): String {
        val state = TextFieldState(valor)
        state.edit {
            val buffer = this
            with(t) { buffer.transformOutput() }
        }
        return state.text.toString()
    }

    private fun igualAoAntigo(valores: List<String>, novo: OutputTransformation, antigo: VisualTransformation) =
        valores.forEach { v ->
            assertEquals(antigo.filter(AnnotatedString(v)).text.text, exibir(v, novo), "valor \"$v\"")
        }

    private fun prefixos(s: String) = (0..s.length).map { s.take(it) }

    // --- CPF ---

    @Test
    fun cpf_digitarGuardaSoAlgarismosAteOnze() {
        val s = TextFieldState("")
        s.digitar("123.456.789-09x9", CpfInputTransformation)
        assertEquals("12345678909", s.text.toString())
    }

    @Test
    fun cpf_colarFormatadoGuardaCru() {
        val s = TextFieldState("")
        s.colar("123.456.789-09", CpfInputTransformation)
        assertEquals("12345678909", s.text.toString())
        assertEquals(TextRange(11), s.selection)
    }

    @Test
    fun cpf_colarMaisDoQueCabeCorta() {
        val s = TextFieldState("")
        s.colar("1234567890999", CpfInputTransformation)
        assertEquals("12345678909", s.text.toString())
    }

    @Test
    fun cpf_campoCheioRecusaODigitoNoMeio() {
        val s = TextFieldState("12345678909")
        s.cursorEm(3)
        s.digitar("7", CpfInputTransformation)
        assertEquals("12345678909", s.text.toString(), "o último algarismo não pode ser empurrado para fora")
    }

    @Test
    fun cpf_letraNoMeioNaoMoveOCursor() {
        val s = TextFieldState("123456")
        s.cursorEm(3)
        s.digitar("a", CpfInputTransformation)
        assertEquals("123456", s.text.toString())
        assertEquals(TextRange(3), s.selection)
        s.digitar("9", CpfInputTransformation)
        assertEquals("1239456", s.text.toString())
        assertEquals(TextRange(4), s.selection)
    }

    @Test
    fun cpf_apagarNoMeioMantemOCursor() {
        val s = TextFieldState("12345678909")
        s.apagarAntesDe(5, CpfInputTransformation)
        assertEquals("1234678909", s.text.toString())
        assertEquals(TextRange(4), s.selection)
        assertEquals("123.467.890-9", exibir(s.text.toString(), CpfOutputTransformation))
    }

    @Test
    fun cpf_exibicaoIgualAoAntigo() {
        assertEquals("123.456.789-09", exibir("12345678909", CpfOutputTransformation))
        igualAoAntigo(prefixos("12345678909"), CpfOutputTransformation, CpfVisualTransformation())
    }

    // --- CNPJ ---

    @Test
    fun cnpj_alfanumericoMaiusculo() {
        val s = TextFieldState("")
        s.digitar("12.abc.345/01de-35zz", CnpjInputTransformation)
        assertEquals("12ABC34501DE35", s.text.toString())
    }

    @Test
    fun cnpj_letraAcentuadaNaoEntra() {
        val s = TextFieldState("")
        s.colar("1á2", CnpjInputTransformation)
        assertEquals("12", s.text.toString())
    }

    @Test
    fun cnpj_colarEApagarNoMeio() {
        val s = TextFieldState("")
        s.colar("11.222.333/0001-81", CnpjInputTransformation)
        assertEquals("11222333000181", s.text.toString())
        s.apagarAntesDe(3, CnpjInputTransformation)
        assertEquals("1122333000181", s.text.toString())
        assertEquals(TextRange(2), s.selection)
    }

    @Test
    fun cnpj_campoCheioRecusa() {
        val s = TextFieldState("11222333000181")
        s.cursorEm(0)
        s.digitar("9", CnpjInputTransformation)
        assertEquals("11222333000181", s.text.toString())
    }

    @Test
    fun cnpj_exibicaoIgualAoAntigo() {
        assertEquals("11.222.333/0001-81", exibir("11222333000181", CnpjOutputTransformation))
        igualAoAntigo(prefixos("11222333000181"), CnpjOutputTransformation, CnpjVisualTransformation())
        igualAoAntigo(prefixos("12ABC34501DE35"), CnpjOutputTransformation, CnpjVisualTransformation())
    }

    // --- Data ---

    @Test
    fun data_digitarColarApagar() {
        val s = TextFieldState("")
        s.digitar("27/10/2026", DateInputTransformation)
        assertEquals("27102026", s.text.toString())
        val c = TextFieldState("")
        c.colar("27/10/2026 10:00", DateInputTransformation)
        assertEquals("27102026", c.text.toString())
        s.apagarAntesDe(3, DateInputTransformation)
        assertEquals("2702026", s.text.toString())
        assertEquals(TextRange(2), s.selection)
    }

    @Test
    fun data_exibicaoIgualAoAntigo() {
        assertEquals("27/10/2026", exibir("27102026", DateOutputTransformation))
        assertEquals("27", exibir("27", DateOutputTransformation))
        assertEquals("27/1", exibir("271", DateOutputTransformation))
        igualAoAntigo(prefixos("27102026"), DateOutputTransformation, DateVisualTransformation())
    }

    // --- Placa ---

    @Test
    fun placa_maiusculaSemSeparador() {
        val s = TextFieldState("")
        s.digitar("abc-1d23", PlateInputTransformation)
        assertEquals("ABC1D23", s.text.toString())
        assertEquals(normalizePlate("abc-1d23"), s.text.toString())
        val c = TextFieldState("")
        c.colar("abc 1234 extra", PlateInputTransformation)
        assertEquals("ABC1234", c.text.toString())
    }

    @Test
    fun placa_apagarNoMeio() {
        val s = TextFieldState("ABC1234")
        s.apagarAntesDe(2, PlateInputTransformation)
        assertEquals("AC1234", s.text.toString())
        assertEquals(TextRange(1), s.selection)
    }

    @Test
    fun placa_exibicaoIgualAoAntigo() {
        assertEquals("ABC-1234", exibir("ABC1234", PlateOutputTransformation))
        assertEquals("ABC1D23", exibir("ABC1D23", PlateOutputTransformation))
        igualAoAntigo(prefixos("ABC1234"), PlateOutputTransformation, PlateVisualTransformation())
        igualAoAntigo(prefixos("ABC1D23"), PlateOutputTransformation, PlateVisualTransformation())
    }

    // --- CREFITO ---

    @Test
    fun crefito_letraSoDepoisDoSexto() {
        val s = TextFieldState("")
        s.digitar("12f3456t", CrefitoInputTransformation)
        assertEquals("123456T", s.text.toString())
        val c = TextFieldState("")
        c.colar("123456-f", CrefitoInputTransformation)
        assertEquals("123456F", c.text.toString())
    }

    @Test
    fun crefito_seisAlgarismosRecusaOSetimo() {
        val s = TextFieldState("123456")
        s.cursorEm(2)
        s.digitar("9", CrefitoInputTransformation)
        assertEquals("123456", s.text.toString())
    }

    @Test
    fun crefito_apagarNoMeio() {
        val s = TextFieldState("123456F")
        s.apagarAntesDe(3, CrefitoInputTransformation)
        // Com 5 algarismos a letra sai (regra do filterCrefitoInput: letra só depois do 6º).
        assertEquals("12456", s.text.toString())
        assertEquals(TextRange(2), s.selection)
    }

    @Test
    fun crefito_exibicaoIgualAoAntigo() {
        assertEquals("123456-F", exibir("123456F", CrefitoOutputTransformation))
        igualAoAntigo(prefixos("123456F"), CrefitoOutputTransformation, CrefitoVisualTransformation())
    }
}
