package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.text.input.TextFieldState
import br.com.codecacto.kmplib.mask.CepInputTransformation
import br.com.codecacto.kmplib.mask.CepOutputTransformation
import kotlin.test.Test
import kotlin.test.assertEquals

/** O CEP do `AddressFields` com texto no campo (2.262.1); desde 2.270.0 as transformações são as públicas do `kmplib-mask`. */
class CepTransformationsTest {

    private fun TextFieldState.digitar(texto: String) = texto.forEach { c ->
        edit {
            val buffer = this
            append(c.toString())
            with(CepInputTransformation) { buffer.transformInput() }
        }
    }

    @Test
    fun soAlgarismosEAteOito() {
        val state = TextFieldState("")
        state.digitar("78a0450001")
        assertEquals("78045000", state.text.toString())
    }

    @Test
    fun colarComHifenGuardaOsOitoAlgarismos() {
        val state = TextFieldState("")
        state.edit {
            val buffer = this
            append("78000-000")
            with(CepInputTransformation) { buffer.transformInput() }
        }
        assertEquals("78000000", state.text.toString())
    }

    private fun mascarar(digitos: String): String {
        val state = TextFieldState(digitos)
        state.edit {
            val buffer = this
            with(CepOutputTransformation) { buffer.transformOutput() }
        }
        return state.text.toString()
    }

    @Test
    fun mascaraSoDepoisDoQuintoDigito() {
        assertEquals("78045", mascarar("78045"))
        assertEquals("78045-0", mascarar("780450"))
        assertEquals("78045-000", mascarar("78045000"))
    }
}
