package br.com.codecacto.kmplib.ui

import br.com.codecacto.kmplib.ui.screens.login.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

/**
 * Testes para LoginContract (State, Action, Effect)
 */
class LoginContractTest {

    // ========== LoginState Tests ==========

    @Test
    fun `test LoginState default values`() {
        val state = LoginState()

        assertEquals("", state.email)
        assertEquals("", state.password)
        assertFalse(state.isLoading)
        assertFalse(state.isGoogleLoading)
        assertFalse(state.isAppleLoading)
        assertNull(state.errorMessage)
        assertNull(state.emailError)
        assertNull(state.passwordError)
        assertFalse(state.showForgotPasswordDialog)
        assertEquals("", state.forgotPasswordEmail)
        assertNull(state.forgotPasswordError)
        assertFalse(state.isPasswordVisible)
    }

    @Test
    fun `test LoginState with custom values`() {
        val state = LoginState(
            email = "test@example.com",
            password = "password123",
            isLoading = true,
            errorMessage = "Login failed"
        )

        assertEquals("test@example.com", state.email)
        assertEquals("password123", state.password)
        assertTrue(state.isLoading)
        assertEquals("Login failed", state.errorMessage)
    }

    @Test
    fun `test LoginState copy with email`() {
        val original = LoginState()
        val updated = original.copy(email = "new@email.com")

        assertEquals("new@email.com", updated.email)
        assertEquals(original.password, updated.password)
    }

    @Test
    fun `test LoginState with loading states`() {
        val state = LoginState(
            isLoading = true,
            isGoogleLoading = false,
            isAppleLoading = false
        )

        assertTrue(state.isLoading)
        assertFalse(state.isGoogleLoading)
        assertFalse(state.isAppleLoading)
    }

    @Test
    fun `test LoginState with errors`() {
        val state = LoginState(
            emailError = "Invalid email",
            passwordError = "Password too short",
            errorMessage = "Login failed"
        )

        assertEquals("Invalid email", state.emailError)
        assertEquals("Password too short", state.passwordError)
        assertEquals("Login failed", state.errorMessage)
    }

    @Test
    fun `test LoginState forgot password dialog`() {
        val state = LoginState(
            showForgotPasswordDialog = true,
            forgotPasswordEmail = "recovery@email.com",
            forgotPasswordError = "Invalid email format"
        )

        assertTrue(state.showForgotPasswordDialog)
        assertEquals("recovery@email.com", state.forgotPasswordEmail)
        assertEquals("Invalid email format", state.forgotPasswordError)
    }

    @Test
    fun `test LoginState password visibility`() {
        val hidden = LoginState(isPasswordVisible = false)
        val visible = LoginState(isPasswordVisible = true)

        assertFalse(hidden.isPasswordVisible)
        assertTrue(visible.isPasswordVisible)
    }

    // ========== LoginAction Tests ==========

    @Test
    fun `test LoginAction Input EmailChanged`() {
        val action = LoginAction.Input.EmailChanged("test@example.com")
        assertTrue(action is LoginAction.Input)
        assertEquals("test@example.com", action.email)
    }

    @Test
    fun `test LoginAction Input PasswordChanged`() {
        val action = LoginAction.Input.PasswordChanged("newpassword")
        assertTrue(action is LoginAction.Input)
        assertEquals("newpassword", action.password)
    }

    @Test
    fun `test LoginAction Input ForgotPasswordEmailChanged`() {
        val action = LoginAction.Input.ForgotPasswordEmailChanged("forgot@email.com")
        assertTrue(action is LoginAction.Input)
        assertEquals("forgot@email.com", action.email)
    }

    @Test
    fun `test LoginAction Toggle PasswordVisibility`() {
        val action = LoginAction.Toggle.PasswordVisibility
        assertTrue(action is LoginAction.Toggle)
    }

    @Test
    fun `test LoginAction Click Login`() {
        val action = LoginAction.Click.Login
        assertTrue(action is LoginAction.Click)
    }

    @Test
    fun `test LoginAction Click GoogleLogin`() {
        val action = LoginAction.Click.GoogleLogin
        assertTrue(action is LoginAction.Click)
    }

    @Test
    fun `test LoginAction Click AppleLogin`() {
        val action = LoginAction.Click.AppleLogin
        assertTrue(action is LoginAction.Click)
    }

    @Test
    fun `test LoginAction Click ForgotPassword`() {
        val action = LoginAction.Click.ForgotPassword
        assertTrue(action is LoginAction.Click)
    }

    @Test
    fun `test LoginAction Click SendResetPassword`() {
        val action = LoginAction.Click.SendResetPassword
        assertTrue(action is LoginAction.Click)
    }

    @Test
    fun `test LoginAction Click CancelForgotPassword`() {
        val action = LoginAction.Click.CancelForgotPassword
        assertTrue(action is LoginAction.Click)
    }

    @Test
    fun `test LoginAction Click Register`() {
        val action = LoginAction.Click.Register
        assertTrue(action is LoginAction.Click)
    }

    @Test
    fun `test LoginAction Click Terms`() {
        val action = LoginAction.Click.Terms
        assertTrue(action is LoginAction.Click)
    }

    @Test
    fun `test LoginAction Click Privacy`() {
        val action = LoginAction.Click.Privacy
        assertTrue(action is LoginAction.Click)
    }

    // ========== LoginEffect Tests ==========

    @Test
    fun `test LoginEffect Navigate ToHome`() {
        val effect = LoginEffect.Navigate.ToHome
        assertTrue(effect is LoginEffect.Navigate)
    }

    @Test
    fun `test LoginEffect Navigate ToRegister`() {
        val effect = LoginEffect.Navigate.ToRegister
        assertTrue(effect is LoginEffect.Navigate)
    }

    @Test
    fun `test LoginEffect SocialLogin LaunchGoogle`() {
        val effect = LoginEffect.SocialLogin.LaunchGoogle
        assertTrue(effect is LoginEffect.SocialLogin)
    }

    @Test
    fun `test LoginEffect SocialLogin LaunchApple`() {
        val effect = LoginEffect.SocialLogin.LaunchApple
        assertTrue(effect is LoginEffect.SocialLogin)
    }

    @Test
    fun `test LoginEffect OpenUrl`() {
        val effect = LoginEffect.OpenUrl("https://example.com/terms")
        assertTrue(effect is LoginEffect)
        assertEquals("https://example.com/terms", effect.url)
    }

    @Test
    fun `test LoginEffect ShowSnackbar`() {
        val effect = LoginEffect.ShowSnackbar("Success message")
        assertTrue(effect is LoginEffect)
        assertEquals("Success message", effect.message)
    }

    @Test
    fun `test LoginEffect ShowError`() {
        val effect = LoginEffect.ShowError("Error message")
        assertTrue(effect is LoginEffect)
        assertEquals("Error message", effect.message)
    }

    @Test
    fun `test LoginEffect ShowResetPasswordSuccess`() {
        val effect = LoginEffect.ShowResetPasswordSuccess("test@email.com")
        assertTrue(effect is LoginEffect)
        assertEquals("test@email.com", effect.email)
    }

    // ========== Social Login Result Tests ==========

    @Test
    fun `test GoogleSignInResult`() {
        val result = GoogleSignInResult(
            idToken = "google_id_token_123",
            accessToken = "google_access_token_456"
        )

        assertEquals("google_id_token_123", result.idToken)
        assertEquals("google_access_token_456", result.accessToken)
    }

    @Test
    fun `test GoogleSignInResult without accessToken`() {
        val result = GoogleSignInResult(
            idToken = "google_id_token_123",
            accessToken = null
        )

        assertEquals("google_id_token_123", result.idToken)
        assertNull(result.accessToken)
    }

    @Test
    fun `test AppleSignInResult`() {
        val result = AppleSignInResult(
            idToken = "apple_id_token_123",
            nonce = "apple_nonce_456"
        )

        assertEquals("apple_id_token_123", result.idToken)
        assertEquals("apple_nonce_456", result.nonce)
    }
}
