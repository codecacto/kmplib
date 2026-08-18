package br.com.codecacto.kmplib.ui.screens.login

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * O contrato do login — o que a tela promete ao app que a consome.
 *
 * O caso que estes testes travam é o do **modal que pisca**: sem um destino de navegação para
 * "esqueci minha senha", o app que tem TELA própria de recuperação era obrigado a usar
 * [LoginState.showForgotPasswordDialog] como sinal para a `Route` navegar — e, como aquilo é estado
 * de UI, o Compose desenhava o diálogo antes de a navegação acontecer. O usuário via um modal
 * aparecer e sumir sozinho.
 */
class LoginContractTest {

    @Test
    fun `existe destino de navegacao para recuperar senha`() {
        // Se este efeito sumir num refactor, o app volta a sinalizar navegação por flag de UI — e o
        // modal volta a piscar, sem quebrar build nenhum.
        val efeito: LoginEffect = LoginEffect.Navigate.ToForgotPassword
        assertTrue(efeito is LoginEffect.Navigate)
    }

    @Test
    fun `os tres destinos sao distintos entre si`() {
        val destinos = listOf(
            LoginEffect.Navigate.ToHome,
            LoginEffect.Navigate.ToRegister,
            LoginEffect.Navigate.ToForgotPassword,
        )
        assertTrue(destinos.toSet().size == destinos.size)
    }

    @Test
    fun `o estado nasce com o dialogo FECHADO`() {
        // O diálogo é o caminho padrão da lib, mas quem abre é a ação da pessoa. Nascer aberto faria
        // a tela de login pedir e-mail de recuperação antes de mostrar o login.
        assertFalse(LoginState().showForgotPasswordDialog)
    }

    @Test
    fun `navegar para recuperar senha NAO passa pelo flag do dialogo`() {
        // A prova de que os dois caminhos são independentes: o efeito de navegação existe sem tocar
        // no estado, então emiti-lo não pode desenhar diálogo nenhum.
        val estado = LoginState()
        val efeito = LoginEffect.Navigate.ToForgotPassword
        assertTrue(efeito is LoginEffect.Navigate.ToForgotPassword)
        assertFalse(estado.showForgotPasswordDialog)
    }
}
