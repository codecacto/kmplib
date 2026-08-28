package br.com.codecacto.kmplib.ui

import br.com.codecacto.kmplib.ui.screens.login.LoginTexts
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Testes para a classe LoginTexts (versão 2.0.0 - @Composable lambdas)
 *
 * Nota: Não podemos invocar os lambdas @Composable em testes unitários regulares.
 * Estes testes focam na estrutura da data class e na capacidade de customização.
 */
class LoginTextsTest {

    @Test
    fun `test default LoginTexts instance creation`() {
        val texts = LoginTexts()

        // Verifica que a instância foi criada corretamente
        assertNull(texts.title)
        assertNotNull(texts.emailLabel)
        assertNotNull(texts.emailPlaceholder)
        assertNotNull(texts.passwordLabel)
        assertNotNull(texts.passwordPlaceholder)
        assertNotNull(texts.loginButton)
        assertNotNull(texts.forgotPassword)
        assertNotNull(texts.registerPrompt)
        assertNotNull(texts.registerLink)
        assertNotNull(texts.orContinueWith)
        assertNotNull(texts.googleLogin)
        assertNotNull(texts.appleLogin)
        assertNotNull(texts.termsPrefix)
        assertNotNull(texts.termsText)
        assertNotNull(texts.privacyText)
        assertNotNull(texts.termsSuffix)
        assertNotNull(texts.forgotPasswordTitle)
        assertNotNull(texts.forgotPasswordMessage)
        assertNotNull(texts.sendButton)
    }

    @Test
    fun `test custom title can be set`() {
        val texts = LoginTexts(title = { "Bem-vindo de volta!" })

        assertNotNull(texts.title)
    }

    @Test
    fun `test all texts can be customized`() {
        val texts = LoginTexts(
            title = { "Custom Title" },
            emailLabel = { "Custom Email" },
            emailPlaceholder = { "custom@email.com" },
            passwordLabel = { "Custom Password" },
            passwordPlaceholder = { "****" },
            loginButton = { "Custom Login" },
            forgotPassword = { "Custom Forgot" },
            registerPrompt = { "Custom Prompt" },
            registerLink = { "Custom Register" },
            orContinueWith = { "Custom Or" },
            googleLogin = { "Custom Google" },
            appleLogin = { "Custom Apple" },
            termsPrefix = { "Custom Prefix" },
            termsText = { "Custom Terms" },
            privacyText = { "Custom Privacy" },
            termsSuffix = { "Custom Suffix" },
            forgotPasswordTitle = { "Custom FP Title" },
            forgotPasswordMessage = { "Custom FP Message" },
            sendButton = { "Custom Send" },
            cancelButton = { "Custom Cancel" }
        )

        // Verifica que todos os campos podem ser customizados
        assertNotNull(texts.title)
        assertNotNull(texts.emailLabel)
        assertNotNull(texts.passwordLabel)
        assertNotNull(texts.loginButton)
    }

    @Test
    fun `test copy with custom values`() {
        val original = LoginTexts()
        val updated = original.copy(
            title = { "New Title" },
            loginButton = { "New Login" }
        )

        // Verifica que copy funcionou
        assertNotNull(updated.title)
        assertNotNull(updated.loginButton)
        assertNotNull(updated.emailLabel) // Mantém valores originais
    }

    @Test
    fun `test English texts configuration`() {
        val englishTexts = LoginTexts(
            title = { "Welcome Back" },
            emailLabel = { "Email" },
            passwordLabel = { "Password" },
            loginButton = { "Sign In" },
            registerLink = { "Sign Up" },
            forgotPassword = { "Forgot Password?" }
        )

        assertNotNull(englishTexts.title)
        assertNotNull(englishTexts.loginButton)
        assertNotNull(englishTexts.registerLink)
    }

    @Test
    fun `test Spanish texts configuration`() {
        val spanishTexts = LoginTexts(
            title = { "Bienvenido" },
            emailLabel = { "Correo electrónico" },
            passwordLabel = { "Contraseña" },
            loginButton = { "Iniciar sesión" }
        )

        assertNotNull(spanishTexts.title)
        assertNotNull(spanishTexts.loginButton)
    }

    @Test
    fun `test social login customization`() {
        val texts = LoginTexts(
            googleLogin = { "Entrar com Google" },
            appleLogin = { "Entrar com Apple" }
        )

        assertNotNull(texts.googleLogin)
        assertNotNull(texts.appleLogin)
    }

    @Test
    fun `test terms and privacy customization`() {
        val texts = LoginTexts(
            termsPrefix = { "Custom prefix" },
            termsText = { "Custom terms" },
            privacyText = { "Custom privacy" },
            termsSuffix = { "Custom suffix" }
        )

        assertNotNull(texts.termsPrefix)
        assertNotNull(texts.termsText)
        assertNotNull(texts.privacyText)
        assertNotNull(texts.termsSuffix)
    }

    @Test
    fun `test forgot password dialog customization`() {
        val texts = LoginTexts(
            forgotPasswordTitle = { "Reset Password" },
            forgotPasswordMessage = { "Enter your email" },
            sendButton = { "Send" }
        )

        assertNotNull(texts.forgotPasswordTitle)
        assertNotNull(texts.forgotPasswordMessage)
        assertNotNull(texts.sendButton)
    }
}
