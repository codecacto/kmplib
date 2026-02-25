package br.com.codecacto.kmplib.ui

import br.com.codecacto.kmplib.ui.screens.register.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

/**
 * Testes para RegisterContract (State, Action, Effect)
 */
class RegisterContractTest {

    // ========== RegisterState Tests ==========

    @Test
    fun `test RegisterState default values`() {
        val state = RegisterState()

        assertEquals("", state.name)
        assertEquals("", state.email)
        assertEquals("", state.phone)
        assertEquals("", state.password)
        assertEquals("", state.confirmPassword)
        assertFalse(state.acceptedTerms)
        assertFalse(state.isLoading)
        assertFalse(state.isGoogleLoading)
        assertFalse(state.isAppleLoading)
        assertNull(state.errorMessage)
        assertNull(state.nameError)
        assertNull(state.emailError)
        assertNull(state.phoneError)
        assertNull(state.passwordError)
        assertNull(state.confirmPasswordError)
    }

    @Test
    fun `test RegisterState with custom values`() {
        val state = RegisterState(
            name = "John Doe",
            email = "john@example.com",
            phone = "11987654321",
            password = "password123",
            confirmPassword = "password123",
            acceptedTerms = true,
            isLoading = true
        )

        assertEquals("John Doe", state.name)
        assertEquals("john@example.com", state.email)
        assertEquals("11987654321", state.phone)
        assertEquals("password123", state.password)
        assertEquals("password123", state.confirmPassword)
        assertTrue(state.acceptedTerms)
        assertTrue(state.isLoading)
    }

    @Test
    fun `test RegisterState copy with name`() {
        val original = RegisterState()
        val updated = original.copy(name = "Jane Doe")

        assertEquals("Jane Doe", updated.name)
        assertEquals(original.email, updated.email)
    }

    @Test
    fun `test RegisterState with loading states`() {
        val state = RegisterState(
            isLoading = true,
            isGoogleLoading = false,
            isAppleLoading = false
        )

        assertTrue(state.isLoading)
        assertFalse(state.isGoogleLoading)
        assertFalse(state.isAppleLoading)
    }

    @Test
    fun `test RegisterState with all errors`() {
        val state = RegisterState(
            nameError = "Name is required",
            emailError = "Invalid email",
            phoneError = "Invalid phone",
            passwordError = "Password too short",
            confirmPasswordError = "Passwords do not match",
            errorMessage = "Registration failed"
        )

        assertEquals("Name is required", state.nameError)
        assertEquals("Invalid email", state.emailError)
        assertEquals("Invalid phone", state.phoneError)
        assertEquals("Password too short", state.passwordError)
        assertEquals("Passwords do not match", state.confirmPasswordError)
        assertEquals("Registration failed", state.errorMessage)
    }

    @Test
    fun `test RegisterState with terms accepted`() {
        val notAccepted = RegisterState(acceptedTerms = false)
        val accepted = RegisterState(acceptedTerms = true)

        assertFalse(notAccepted.acceptedTerms)
        assertTrue(accepted.acceptedTerms)
    }

    // ========== RegisterAction Tests ==========

    @Test
    fun `test RegisterAction Input NameChanged`() {
        val action = RegisterAction.Input.NameChanged("John Doe")
        assertTrue(action is RegisterAction.Input)
        assertEquals("John Doe", action.name)
    }

    @Test
    fun `test RegisterAction Input EmailChanged`() {
        val action = RegisterAction.Input.EmailChanged("test@example.com")
        assertTrue(action is RegisterAction.Input)
        assertEquals("test@example.com", action.email)
    }

    @Test
    fun `test RegisterAction Input PhoneChanged`() {
        val action = RegisterAction.Input.PhoneChanged("11987654321")
        assertTrue(action is RegisterAction.Input)
        assertEquals("11987654321", action.phone)
    }

    @Test
    fun `test RegisterAction Input PasswordChanged`() {
        val action = RegisterAction.Input.PasswordChanged("newpassword")
        assertTrue(action is RegisterAction.Input)
        assertEquals("newpassword", action.password)
    }

    @Test
    fun `test RegisterAction Input ConfirmPasswordChanged`() {
        val action = RegisterAction.Input.ConfirmPasswordChanged("confirm123")
        assertTrue(action is RegisterAction.Input)
        assertEquals("confirm123", action.password)
    }

    @Test
    fun `test RegisterAction Input TermsAcceptedChanged`() {
        val acceptedAction = RegisterAction.Input.TermsAcceptedChanged(true)
        val rejectedAction = RegisterAction.Input.TermsAcceptedChanged(false)

        assertTrue(acceptedAction is RegisterAction.Input)
        assertTrue(acceptedAction.accepted)
        assertFalse(rejectedAction.accepted)
    }

    @Test
    fun `test RegisterAction Click Register`() {
        val action = RegisterAction.Click.Register
        assertTrue(action is RegisterAction.Click)
    }

    @Test
    fun `test RegisterAction Click GoogleRegister`() {
        val action = RegisterAction.Click.GoogleRegister
        assertTrue(action is RegisterAction.Click)
    }

    @Test
    fun `test RegisterAction Click AppleRegister`() {
        val action = RegisterAction.Click.AppleRegister
        assertTrue(action is RegisterAction.Click)
    }

    @Test
    fun `test RegisterAction Click Login`() {
        val action = RegisterAction.Click.Login
        assertTrue(action is RegisterAction.Click)
    }

    @Test
    fun `test RegisterAction Click Terms`() {
        val action = RegisterAction.Click.Terms
        assertTrue(action is RegisterAction.Click)
    }

    @Test
    fun `test RegisterAction Click Privacy`() {
        val action = RegisterAction.Click.Privacy
        assertTrue(action is RegisterAction.Click)
    }

    // ========== RegisterEffect Tests ==========

    @Test
    fun `test RegisterEffect Navigate ToHome`() {
        val effect = RegisterEffect.Navigate.ToHome
        assertTrue(effect is RegisterEffect.Navigate)
    }

    @Test
    fun `test RegisterEffect Navigate ToLogin`() {
        val effect = RegisterEffect.Navigate.ToLogin
        assertTrue(effect is RegisterEffect.Navigate)
    }

    @Test
    fun `test RegisterEffect SocialLogin LaunchGoogle`() {
        val effect = RegisterEffect.SocialRegister.LaunchGoogle
        assertTrue(effect is RegisterEffect.SocialRegister)
    }

    @Test
    fun `test RegisterEffect SocialLogin LaunchApple`() {
        val effect = RegisterEffect.SocialRegister.LaunchApple
        assertTrue(effect is RegisterEffect.SocialRegister)
    }

    @Test
    fun `test RegisterEffect OpenUrl`() {
        val effect = RegisterEffect.OpenUrl("https://example.com/privacy")
        assertTrue(effect is RegisterEffect)
        assertEquals("https://example.com/privacy", effect.url)
    }

    @Test
    fun `test RegisterEffect ShowSnackbar`() {
        val effect = RegisterEffect.ShowSnackbar("Registration successful")
        assertTrue(effect is RegisterEffect)
        assertEquals("Registration successful", effect.message)
    }

    @Test
    fun `test RegisterEffect ShowError`() {
        val effect = RegisterEffect.ShowError("Registration failed")
        assertTrue(effect is RegisterEffect)
        assertEquals("Registration failed", effect.message)
    }

}
