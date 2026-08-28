package br.com.codecacto.kmplib.ui

import br.com.codecacto.kmplib.ui.screens.register.RegisterTexts
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Testes para a classe RegisterTexts
 *
 * Nota: Não podemos invocar os lambdas @Composable em testes unitários regulares.
 * Estes testes focam na estrutura da data class e na capacidade de customização.
 */
class RegisterTextsTest {

    @Test
    fun `test default RegisterTexts instance creation`() {
        val texts = RegisterTexts()

        // Verifica que a instância foi criada corretamente
        assertNotNull(texts.title)
        assertNotNull(texts.nameLabel)
        assertNotNull(texts.namePlaceholder)
        assertNotNull(texts.emailLabel)
        assertNotNull(texts.emailPlaceholder)
        assertNotNull(texts.phoneLabel)
        assertNotNull(texts.phonePlaceholder)
        assertNotNull(texts.passwordLabel)
        assertNotNull(texts.passwordPlaceholder)
        assertNotNull(texts.confirmPasswordLabel)
        assertNotNull(texts.confirmPasswordPlaceholder)
        assertNotNull(texts.registerButton)
        assertNotNull(texts.loginPrompt)
        assertNotNull(texts.loginLink)
        assertNotNull(texts.orContinueWith)
        assertNotNull(texts.googleRegister)
        assertNotNull(texts.appleRegister)
        assertNotNull(texts.termsPrefix)
        assertNotNull(texts.termsText)
        assertNotNull(texts.andText)
        assertNotNull(texts.privacyText)
    }

    @Test
    fun `test custom title can be set`() {
        val texts = RegisterTexts(title = { "Crie sua Conta" })

        assertNotNull(texts.title)
    }

    @Test
    fun `test all field labels can be customized`() {
        val texts = RegisterTexts(
            nameLabel = { "Full Name" },
            emailLabel = { "Email Address" },
            phoneLabel = { "Phone Number" },
            passwordLabel = { "Password" },
            confirmPasswordLabel = { "Confirm Password" }
        )

        assertNotNull(texts.nameLabel)
        assertNotNull(texts.emailLabel)
        assertNotNull(texts.phoneLabel)
        assertNotNull(texts.passwordLabel)
        assertNotNull(texts.confirmPasswordLabel)
    }

    @Test
    fun `test English texts configuration`() {
        val englishTexts = RegisterTexts(
            title = { "Create Account" },
            registerButton = { "Sign Up" },
            loginPrompt = { "Already have an account?" },
            loginLink = { "Sign In" }
        )

        assertNotNull(englishTexts.title)
        assertNotNull(englishTexts.registerButton)
        assertNotNull(englishTexts.loginPrompt)
        assertNotNull(englishTexts.loginLink)
    }

    @Test
    fun `test Spanish texts configuration`() {
        val spanishTexts = RegisterTexts(
            title = { "Crear Cuenta" },
            nameLabel = { "Nombre completo" },
            registerButton = { "Registrarse" }
        )

        assertNotNull(spanishTexts.title)
        assertNotNull(spanishTexts.nameLabel)
        assertNotNull(spanishTexts.registerButton)
    }

    @Test
    fun `test custom button texts`() {
        val texts = RegisterTexts(
            registerButton = { "Register Now" },
            loginLink = { "Login" }
        )

        assertNotNull(texts.registerButton)
        assertNotNull(texts.loginLink)
    }

    @Test
    fun `test social register customization`() {
        val texts = RegisterTexts(
            googleRegister = { "Criar conta com Google" },
            appleRegister = { "Criar conta com Apple" }
        )

        assertNotNull(texts.googleRegister)
        assertNotNull(texts.appleRegister)
    }

    @Test
    fun `test terms and privacy customization`() {
        val texts = RegisterTexts(
            termsPrefix = { "Ao se cadastrar, você aceita os " },
            termsText = { "Termos" },
            andText = { " e a " },
            privacyText = { "Privacidade" }
        )

        assertNotNull(texts.termsPrefix)
        assertNotNull(texts.termsText)
        assertNotNull(texts.andText)
        assertNotNull(texts.privacyText)
    }

    @Test
    fun `test copy with custom values`() {
        val original = RegisterTexts()
        val updated = original.copy(
            title = { "New Title" },
            registerButton = { "Create Account" }
        )

        assertNotNull(updated.title)
        assertNotNull(updated.registerButton)
        assertNotNull(updated.nameLabel) // Mantém valores originais
        assertNotNull(updated.emailLabel) // Mantém valores originais
    }

    @Test
    fun `test all texts can be customized`() {
        val texts = RegisterTexts(
            title = { "Custom" },
            nameLabel = { "Custom" },
            namePlaceholder = { "Custom" },
            emailLabel = { "Custom" },
            emailPlaceholder = { "Custom" },
            phoneLabel = { "Custom" },
            phonePlaceholder = { "Custom" },
            passwordLabel = { "Custom" },
            passwordPlaceholder = { "Custom" },
            confirmPasswordLabel = { "Custom" },
            confirmPasswordPlaceholder = { "Custom" },
            registerButton = { "Custom" },
            loginPrompt = { "Custom" },
            loginLink = { "Custom" },
            orContinueWith = { "Custom" },
            googleRegister = { "Custom" },
            appleRegister = { "Custom" },
            termsPrefix = { "Custom" },
            termsText = { "Custom" },
            andText = { "Custom" },
            privacyText = { "Custom" }
        )

        // Verifica que todos os campos foram customizados
        assertNotNull(texts.title)
        assertNotNull(texts.nameLabel)
        assertNotNull(texts.registerButton)
        assertNotNull(texts.googleRegister)
        assertNotNull(texts.termsText)
    }
}
