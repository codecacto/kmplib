package br.com.codecacto.kmplib.ui.screens.register

import br.com.codecacto.kmplib.ui.screens.login.AppleSignInResult
import br.com.codecacto.kmplib.ui.screens.login.GoogleSignInResult

/**
 * Contrato MVI para RegisterScreen
 */

/**
 * Estado da tela de registro
 */
data class RegisterState(
    // Campos
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val acceptedTerms: Boolean = false,

    // Estados de loading
    val isLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val isAppleLoading: Boolean = false,

    // Erros
    val errorMessage: String? = null,
    val nameError: String? = null,
    val emailError: String? = null,
    val phoneError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,

    // Visibilidade
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false
)

/**
 * Ações que podem ser disparadas na tela de registro
 */
sealed interface RegisterAction {

    // Inputs
    sealed interface Input : RegisterAction {
        data class NameChanged(val name: String) : Input
        data class EmailChanged(val email: String) : Input
        data class PhoneChanged(val phone: String) : Input
        data class PasswordChanged(val password: String) : Input
        data class ConfirmPasswordChanged(val password: String) : Input
        data class TermsAcceptedChanged(val accepted: Boolean) : Input
    }

    // Toggles
    sealed interface Toggle : RegisterAction {
        data object PasswordVisibility : Toggle
        data object ConfirmPasswordVisibility : Toggle
    }

    // Clicks
    sealed interface Click : RegisterAction {
        data object Register : Click
        data object GoogleRegister : Click
        data object AppleRegister : Click
        data object Login : Click
        data object Terms : Click
        data object Privacy : Click
    }
}

/**
 * Efeitos colaterais da tela de registro
 */
sealed interface RegisterEffect {
    // Navegação
    sealed interface Navigate : RegisterEffect {
        data object ToHome : Navigate
        data object ToLogin : Navigate
    }

    // Social Login
    sealed interface SocialRegister : RegisterEffect {
        data object LaunchGoogle : SocialRegister
        data object LaunchApple : SocialRegister
    }

    // Links
    data class OpenUrl(val url: String) : RegisterEffect

    // Mensagens
    data class ShowSnackbar(val message: String) : RegisterEffect
    data class ShowError(val message: String) : RegisterEffect
}
