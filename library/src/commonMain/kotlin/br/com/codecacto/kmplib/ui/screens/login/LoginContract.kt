package br.com.codecacto.kmplib.ui.screens.login

import br.com.codecacto.kmplib.ui.mvi.UiAction
import br.com.codecacto.kmplib.ui.mvi.UiEffect
import br.com.codecacto.kmplib.ui.mvi.UiState

/**
 * Contrato MVI para LoginScreen
 */

/**
 * Estado da tela de login
 */
data class LoginState(
    // Campos
    val email: String = "",
    val password: String = "",

    // Estados de loading
    val isLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val isAppleLoading: Boolean = false,
    val isForgotPasswordLoading: Boolean = false,

    // Erros
    val errorMessage: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,

    // Dialog Esqueci Senha
    val showForgotPasswordDialog: Boolean = false,
    val forgotPasswordEmail: String = "",
    val forgotPasswordError: String? = null,

    // Visibilidade
    val isPasswordVisible: Boolean = false
) : UiState

/**
 * Ações que podem ser disparadas na tela de login
 */
sealed interface LoginAction : UiAction {

    // Inputs
    sealed interface Input : LoginAction {
        data class EmailChanged(val email: String) : Input
        data class PasswordChanged(val password: String) : Input
        data class ForgotPasswordEmailChanged(val email: String) : Input
    }

    // Toggles
    sealed interface Toggle : LoginAction {
        data object PasswordVisibility : Toggle
    }

    // Clicks
    sealed interface Click : LoginAction {
        data object Login : Click
        data object GoogleLogin : Click
        data object AppleLogin : Click
        data object ForgotPassword : Click
        data object SendResetPassword : Click
        data object CancelForgotPassword : Click
        data object Register : Click
        data object Terms : Click
        data object Privacy : Click
    }
}

/**
 * Efeitos colaterais da tela de login
 */
sealed interface LoginEffect : UiEffect {
    // Navegação
    sealed interface Navigate : LoginEffect {
        data object ToHome : Navigate
        data object ToRegister : Navigate

        /**
         * "Esqueci minha senha" leva a uma **TELA própria** do app, em vez do diálogo desta tela.
         *
         * ## Por que existe (defeito real, 18/ago/2026)
         *
         * O caminho padrão daqui é o `InputDialog` embutido, ligado por
         * [LoginState.showForgotPasswordDialog]. Mas um app cujo fluxo de recuperação continua em
         * outra tela (digitar o código, definir a nova senha) não cabe num diálogo — e, sem um
         * destino no contrato, a saída que sobrava era **usar o flag do diálogo como sinal de
         * navegação**: o ViewModel o ligava e a `Route` observava para navegar.
         *
         * Isso **pisca na cara do usuário**. O flag é estado de UI: o Compose recompõe e desenha o
         * diálogo no mesmo frame, e só depois o `LaunchedEffect` da Route roda, limpa o flag e
         * navega. O usuário vê um modal aparecer e sumir sozinho antes da tela certa — foi o que o
         * NeuroCoreX apresentou.
         *
         * Com este efeito o app emite uma **navegação**, que é o que ele quer dizer, e nenhum
         * diálogo chega a existir. Quem usa o diálogo da lib não muda nada: continua ligando o flag.
         */
        data object ToForgotPassword : Navigate
    }

    // Social Login
    sealed interface SocialLogin : LoginEffect {
        data object LaunchGoogle : SocialLogin
        data object LaunchApple : SocialLogin
    }

    // Links
    data class OpenUrl(val url: String) : LoginEffect

    // Mensagens
    data class ShowSnackbar(val message: String) : LoginEffect
    data class ShowError(val message: String) : LoginEffect
    data class ShowResetPasswordSuccess(val email: String) : LoginEffect
}

/**
 * Resultado do login com Google
 */
data class GoogleSignInResult(
    val idToken: String,
    val accessToken: String?
)

/**
 * Resultado do login com Apple
 */
data class AppleSignInResult(
    val idToken: String,
    val nonce: String
)
