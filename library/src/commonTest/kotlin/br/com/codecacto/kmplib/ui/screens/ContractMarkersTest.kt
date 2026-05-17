package br.com.codecacto.kmplib.ui.screens

import br.com.codecacto.kmplib.ui.mvi.UiAction
import br.com.codecacto.kmplib.ui.mvi.UiEffect
import br.com.codecacto.kmplib.ui.mvi.UiState
import br.com.codecacto.kmplib.ui.screens.login.LoginAction
import br.com.codecacto.kmplib.ui.screens.login.LoginEffect
import br.com.codecacto.kmplib.ui.screens.login.LoginState
import br.com.codecacto.kmplib.ui.screens.register.RegisterAction
import br.com.codecacto.kmplib.ui.screens.register.RegisterEffect
import br.com.codecacto.kmplib.ui.screens.register.RegisterState
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Garante que os contratos do LoginScreen/RegisterScreen implementam os
 * markers MVI da lib. Sem isso, `SimpleMviViewModel<State, Effect, Action>`
 * não compila com esses contratos.
 *
 * Adicionado em 2026-05-17 com a correção de marker interfaces.
 */
class ContractMarkersTest {

    // ====== LoginContract ======

    @Test
    fun `LoginState implementa UiState`() {
        val state: UiState = LoginState()
        assertIs<LoginState>(state)
    }

    @Test
    fun `LoginAction implementa UiAction`() {
        val actions: List<UiAction> = listOf(
            LoginAction.Input.EmailChanged("a@b.com"),
            LoginAction.Input.PasswordChanged("123"),
            LoginAction.Input.ForgotPasswordEmailChanged("a@b.com"),
            LoginAction.Toggle.PasswordVisibility,
            LoginAction.Click.Login,
            LoginAction.Click.GoogleLogin,
            LoginAction.Click.AppleLogin,
            LoginAction.Click.ForgotPassword,
            LoginAction.Click.SendResetPassword,
            LoginAction.Click.CancelForgotPassword,
            LoginAction.Click.Register,
            LoginAction.Click.Terms,
            LoginAction.Click.Privacy
        )
        actions.forEach { assertTrue(it is LoginAction) }
    }

    @Test
    fun `LoginEffect implementa UiEffect`() {
        val effects: List<UiEffect> = listOf(
            LoginEffect.Navigate.ToHome,
            LoginEffect.Navigate.ToRegister,
            LoginEffect.SocialLogin.LaunchGoogle,
            LoginEffect.SocialLogin.LaunchApple,
            LoginEffect.OpenUrl("https://example.com"),
            LoginEffect.ShowSnackbar("hi"),
            LoginEffect.ShowError("bad"),
            LoginEffect.ShowResetPasswordSuccess("a@b.com")
        )
        effects.forEach { assertTrue(it is LoginEffect) }
    }

    // ====== RegisterContract ======

    @Test
    fun `RegisterState implementa UiState`() {
        val state: UiState = RegisterState()
        assertIs<RegisterState>(state)
    }

    @Test
    fun `RegisterAction implementa UiAction`() {
        val actions: List<UiAction> = listOf(
            RegisterAction.Input.NameChanged("Joao"),
            RegisterAction.Input.EmailChanged("a@b.com"),
            RegisterAction.Input.PhoneChanged("11999999999"),
            RegisterAction.Input.PasswordChanged("s3cret"),
            RegisterAction.Input.ConfirmPasswordChanged("s3cret"),
            RegisterAction.Input.TermsAcceptedChanged(true),
            RegisterAction.Toggle.PasswordVisibility,
            RegisterAction.Toggle.ConfirmPasswordVisibility,
            RegisterAction.Click.Register,
            RegisterAction.Click.GoogleRegister,
            RegisterAction.Click.AppleRegister,
            RegisterAction.Click.Login,
            RegisterAction.Click.Terms,
            RegisterAction.Click.Privacy
        )
        actions.forEach { assertTrue(it is RegisterAction) }
    }

    @Test
    fun `RegisterEffect implementa UiEffect`() {
        val effects: List<UiEffect> = listOf(
            RegisterEffect.Navigate.ToHome,
            RegisterEffect.Navigate.ToLogin,
            RegisterEffect.SocialRegister.LaunchGoogle,
            RegisterEffect.SocialRegister.LaunchApple,
            RegisterEffect.OpenUrl("https://example.com"),
            RegisterEffect.ShowSnackbar("hi"),
            RegisterEffect.ShowError("bad")
        )
        effects.forEach { assertTrue(it is RegisterEffect) }
    }
}
