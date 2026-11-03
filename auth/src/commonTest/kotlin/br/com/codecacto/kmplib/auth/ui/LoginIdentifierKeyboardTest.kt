package br.com.codecacto.kmplib.ui.screens.login

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import br.com.codecacto.kmplib.auth.AuthIdentifierMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O identificador do login é digitado literal (2.340.1). Até a 2.340.0, `BOTH` e `USERNAME` usavam
 * `KeyboardType.Text` com os defaults de texto corrido, e o iPhone trocava "joao" por "Joao".
 */
class LoginIdentifierKeyboardTest {

    @Test
    fun `nenhum modo capitaliza nem autocorrige`() {
        AuthIdentifierMode.entries.forEach { mode ->
            val options = loginIdentifierKeyboardOptions(mode)
            assertEquals(KeyboardCapitalization.None, options.capitalization, mode.name)
            assertEquals(false, options.autoCorrectEnabled, mode.name)
            assertEquals(ImeAction.Next, options.imeAction, mode.name)
        }
    }

    @Test
    fun `quem aceita e-mail usa o teclado de e-mail`() {
        assertEquals(KeyboardType.Email, loginIdentifierKeyboardOptions(AuthIdentifierMode.EMAIL).keyboardType)
        assertEquals(KeyboardType.Email, loginIdentifierKeyboardOptions(AuthIdentifierMode.BOTH).keyboardType)
    }

    @Test
    fun `so usuario usa o teclado de texto`() {
        assertEquals(KeyboardType.Text, loginIdentifierKeyboardOptions(AuthIdentifierMode.USERNAME).keyboardType)
    }
}
