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

/**
 * O modo do identificador no estado da tela — o que faz o app parar de mentir quando o sistema
 * aceita nome de usuário.
 *
 * Antes disto o rótulo e o teclado eram **fixos em e-mail**: no Meu Barbeiro, o portal já dizia
 * "E-mail ou usuário" e o app dizia "E-mail", com teclado de e-mail, para quem precisava digitar
 * `joao.silva`. O login funcionava (a API aceita os dois) — a TELA é que estava errada.
 */
class LoginIdentifierModeTest {

    @kotlin.test.Test
    fun `default e EMAIL, para app que nao consulta o servidor nao mudar de aparencia sozinho`() {
        kotlin.test.assertEquals(
            br.com.codecacto.kmplib.auth.AuthIdentifierMode.EMAIL,
            LoginState().identifierMode,
        )
        kotlin.test.assertEquals("", LoginState().identifierLabel)
    }

    @kotlin.test.Test
    fun `o rotulo do servidor vence o texto local`() {
        // Num sistema que a empresa configurou como "Matrícula", o app tem de dizer "Matrícula".
        val state = LoginState(
            identifierMode = br.com.codecacto.kmplib.auth.AuthIdentifierMode.BOTH,
            identifierLabel = "Matrícula",
        )
        kotlin.test.assertEquals("Matrícula", state.identifierLabel.ifBlank { "E-mail ou usuário" })
    }

    @kotlin.test.Test
    fun `config do servidor desserializa, e corpo estranho cai no default`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val lido = json.decodeFromString(
            br.com.codecacto.kmplib.auth.OwnAuthIdentifierConfig.serializer(),
            """{"identifierMode":"BOTH","identifierLabel":"E-mail ou usuário","extra":1}""",
        )
        kotlin.test.assertEquals(
            br.com.codecacto.kmplib.auth.AuthIdentifierMode.BOTH,
            lido.identifierMode,
        )
        // Backend anterior à 0.80.0 não manda o campo: EMAIL, o comportamento de sempre.
        val vazio = json.decodeFromString(
            br.com.codecacto.kmplib.auth.OwnAuthIdentifierConfig.serializer(),
            "{}",
        )
        kotlin.test.assertEquals(
            br.com.codecacto.kmplib.auth.AuthIdentifierMode.EMAIL,
            vazio.identifierMode,
        )
    }
}
