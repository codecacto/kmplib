package br.com.codecacto.kmplib.ui

import br.com.codecacto.kmplib.ui.screens.AuthMethods
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testes para a classe AuthMethods
 */
class AuthMethodsTest {

    @Test
    fun `test default auth methods - only email password`() {
        val methods = AuthMethods()

        assertTrue(methods.emailPassword)
        assertFalse(methods.google)
        assertFalse(methods.apple)
    }

    @Test
    fun `test email and google enabled`() {
        val methods = AuthMethods(
            emailPassword = true,
            google = true,
            apple = false
        )

        assertTrue(methods.emailPassword)
        assertTrue(methods.google)
        assertFalse(methods.apple)
    }

    @Test
    fun `test all methods enabled`() {
        val methods = AuthMethods(
            emailPassword = true,
            google = true,
            apple = true
        )

        assertTrue(methods.emailPassword)
        assertTrue(methods.google)
        assertTrue(methods.apple)
    }

    @Test
    fun `test only social login - no email password`() {
        val methods = AuthMethods(
            emailPassword = false,
            google = true,
            apple = true
        )

        assertFalse(methods.emailPassword)
        assertTrue(methods.google)
        assertTrue(methods.apple)
    }

    @Test
    fun `test only google login`() {
        val methods = AuthMethods(
            emailPassword = false,
            google = true,
            apple = false
        )

        assertFalse(methods.emailPassword)
        assertTrue(methods.google)
        assertFalse(methods.apple)
    }

    @Test
    fun `test only apple login`() {
        val methods = AuthMethods(
            emailPassword = false,
            google = false,
            apple = true
        )

        assertFalse(methods.emailPassword)
        assertFalse(methods.google)
        assertTrue(methods.apple)
    }

    @Test
    fun `test no auth methods enabled`() {
        val methods = AuthMethods(
            emailPassword = false,
            google = false,
            apple = false
        )

        assertFalse(methods.emailPassword)
        assertFalse(methods.google)
        assertFalse(methods.apple)
    }

    @Test
    fun `test copy with changed values`() {
        val original = AuthMethods()
        val updated = original.copy(google = true, apple = true)

        assertTrue(updated.emailPassword)
        assertTrue(updated.google)
        assertTrue(updated.apple)
    }

    @Test
    fun `test typical iOS configuration`() {
        val iosConfig = AuthMethods(
            emailPassword = true,
            google = true,
            apple = true // Apple login disponível no iOS
        )

        assertTrue(iosConfig.emailPassword)
        assertTrue(iosConfig.google)
        assertTrue(iosConfig.apple)
    }

    @Test
    fun `test typical Android configuration`() {
        val androidConfig = AuthMethods(
            emailPassword = true,
            google = true,
            apple = false // Apple login normalmente não disponível no Android
        )

        assertTrue(androidConfig.emailPassword)
        assertTrue(androidConfig.google)
        assertFalse(androidConfig.apple)
    }

    @Test
    fun `test hasAtLeastOneMethod function concept`() {
        // Testa se pelo menos um método está habilitado
        fun AuthMethods.hasAtLeastOneMethod() =
            emailPassword || google || apple

        assertTrue(AuthMethods(emailPassword = true).hasAtLeastOneMethod())
        assertTrue(AuthMethods(google = true).hasAtLeastOneMethod())
        assertTrue(AuthMethods(apple = true).hasAtLeastOneMethod())
        assertFalse(AuthMethods(
            emailPassword = false,
            google = false,
            apple = false
        ).hasAtLeastOneMethod())
    }

    @Test
    fun `test hasSocialLogin function concept`() {
        // Testa se tem pelo menos um login social
        fun AuthMethods.hasSocialLogin() = google || apple

        assertTrue(AuthMethods(google = true).hasSocialLogin())
        assertTrue(AuthMethods(apple = true).hasSocialLogin())
        assertTrue(AuthMethods(google = true, apple = true).hasSocialLogin())
        assertFalse(AuthMethods(emailPassword = true).hasSocialLogin())
    }
}
