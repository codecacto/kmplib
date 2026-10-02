package br.com.codecacto.kmplib.ui.screens.login

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    // ---- 2.240.1: paridade com o botão nas outras três condições -------------------------------

    @Test
    fun `com o dialogo de esqueci a senha aberto o teclado nao envia o login`() {
        assertNull(loginImeDoneAction(LoginState(showForgotPasswordDialog = true)))
    }

    @Test
    fun `com o login do Google em andamento o teclado nao envia o login`() {
        assertNull(loginImeDoneAction(LoginState(isGoogleLoading = true)))
    }

    @Test
    fun `com o login da Apple em andamento o teclado nao envia o login`() {
        assertNull(loginImeDoneAction(LoginState(isAppleLoading = true)))
    }

    @Test
    fun `o botao Entrar fica habilitado so no estado em que o teclado tambem envia`() {
        assertTrue(loginSubmitEnabled(LoginState()))
        assertFalse(loginSubmitEnabled(LoginState(isLoading = true)))
        assertFalse(loginSubmitEnabled(LoginState(showForgotPasswordDialog = true)))
        assertFalse(loginSubmitEnabled(LoginState(isGoogleLoading = true)))
        assertFalse(loginSubmitEnabled(LoginState(isAppleLoading = true)))
    }

    @Test
    fun `botao e teclado nunca divergem - a mesma condicao decide os dois`() {
        val estados = listOf(
            LoginState(),
            LoginState(email = "pessoa@empresa.com", password = "segredo"),
            LoginState(isLoading = true),
            LoginState(showForgotPasswordDialog = true),
            LoginState(isGoogleLoading = true),
            LoginState(isAppleLoading = true),
            LoginState(isForgotPasswordLoading = true),
            LoginState(errorMessage = "falhou"),
        )
        estados.forEach { estado ->
            assertEquals(
                loginSubmitEnabled(estado),
                loginImeDoneAction(estado) != null,
                "divergiu em $estado",
            )
        }
    }
}
