package br.com.codecacto.kmplib.mask

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import kotlin.test.Test
import kotlin.test.assertEquals

class TextFieldStateMasksTest {

    private fun TextFieldState.digitar(texto: String, t: InputTransformation) = texto.forEach { c ->
        edit {
            val buffer = this
            append(c.toString())
            with(t) { buffer.transformInput() }
        }
    }

    private fun TextFieldState.colar(texto: String, t: InputTransformation) = edit {
        val buffer = this
        append(texto)
        with(t) { buffer.transformInput() }
    }

    private fun exibir(valor: String, t: OutputTransformation): String {
        val state = TextFieldState(valor)
        state.edit {
            val buffer = this
            with(t) { buffer.transformOutput() }
        }
        return state.text.toString()
    }

    // --- telefone: entrada ---

    @Test
    fun phoneInput_soAlgarismos_ateOnze() {
        val s = TextFieldState("")
        s.digitar("(65) 9a9999-88889", PhoneInputTransformation)
        assertEquals("65999998888", s.text.toString())
    }

    @Test
    fun phoneInput_colarComDdi_tiraO55() {
        val s = TextFieldState("")
        s.colar("+55 (65) 99999-8888", PhoneInputTransformation)
        assertEquals("65999998888", s.text.toString())
        val fixo = TextFieldState("")
        fixo.colar("+55 65 3322-1100", PhoneInputTransformation)
        assertEquals("6533221100", fixo.text.toString())
    }

    @Test
    fun phoneInput_colarSemDdi_mantem() {
        val s = TextFieldState("")
        s.colar("(55) 99999-8888", PhoneInputTransformation) // DDD 55 (RS), 11 algarismos
        assertEquals("55999998888", s.text.toString())
    }

    @Test
    fun normalize_casosDeBorda() {
        assertEquals("", normalizeBrPhoneDigits(""))
        assertEquals("123456789012".take(11), normalizeBrPhoneDigits("123456789012"))
        assertEquals("55123", normalizeBrPhoneDigits("55123"))
    }

    // --- telefone: exibição ---

    @Test
    fun phoneOutput_progressivo() {
        assertEquals("", exibir("", PhoneBrOutputTransformation))
        assertEquals("(6", exibir("6", PhoneBrOutputTransformation))
        assertEquals("(65", exibir("65", PhoneBrOutputTransformation))
        assertEquals("(65) 9", exibir("659", PhoneBrOutputTransformation))
        assertEquals("(65) 3322", exibir("653322", PhoneBrOutputTransformation))
        assertEquals("(65) 3322-1", exibir("6533221", PhoneBrOutputTransformation))
        assertEquals("(65) 3322-1100", exibir("6533221100", PhoneBrOutputTransformation))
        assertEquals("(65) 99999-8888", exibir("65999998888", PhoneBrOutputTransformation))
    }

    @Test
    fun phoneOutput_igualAVisualTransformation() {
        val visual = PhoneVisualTransformation()
        listOf("6", "65", "659", "6599", "65999", "659999", "6599999", "65999998", "6599999888", "65999998888")
            .forEach { d ->
                val esperado = visual.filter(androidx.compose.ui.text.AnnotatedString(d)).text.text
                assertEquals(esperado, exibir(d, PhoneBrOutputTransformation), "dígitos $d")
            }
    }

    @Test
    fun phoneOutput_comNaoAlgarismo_naoMexe() {
        assertEquals("abc", exibir("abc", PhoneBrOutputTransformation))
    }

    // --- CEP ---

    @Test
    fun cepInput_soAlgarismosEAteOito() {
        val s = TextFieldState("")
        s.digitar("78a0450001", CepInputTransformation)
        assertEquals("78045000", s.text.toString())
        val c = TextFieldState("")
        c.colar("78000-000", CepInputTransformation)
        assertEquals("78000000", c.text.toString())
    }

    @Test
    fun cepOutput_hifenDepoisDoQuinto() {
        assertEquals("78045", exibir("78045", CepOutputTransformation))
        assertEquals("78045-0", exibir("780450", CepOutputTransformation))
        assertEquals("78045-000", exibir("78045000", CepOutputTransformation))
    }
}
