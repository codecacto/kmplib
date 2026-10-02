package br.com.codecacto.kmplib.ui.screens.login

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * O "Concluído" do teclado no campo de senha envia o login (2.239.0).
 *
 * Até a 2.238.0 o campo era `ImeAction.Done` sem ação própria: o Done só baixava o teclado e o
 * formulário ficava preenchido e parado. Estes testes travam a regra — o teclado dispara a MESMA
 * ação do botão Entrar, e nunca durante um envio em curso.
 */
class LoginImeDoneActionTest {

    @Test
    fun `o Concluido da senha dispara a mesma acao do botao Entrar`() {
        assertEquals(LoginAction.Click.Login, loginImeDoneAction(LoginState()))
    }

    @Test
    fun `com os campos preenchidos continua sendo o Login - a validacao e do ViewModel`() {
        // A tela não decide se o formulário está válido: o botão também envia `Click.Login` com
        // campo vazio, e é o ViewModel que devolve o erro no campo. O teclado segue a mesma regra.
        val estado = LoginState(email = "pessoa@empresa.com", password = "segredo")
        assertEquals(LoginAction.Click.Login, loginImeDoneAction(estado))
    }

    @Test
    fun `durante o envio o teclado nao dispara um segundo login`() {
        assertNull(loginImeDoneAction(LoginState(isLoading = true)))
    }
}
