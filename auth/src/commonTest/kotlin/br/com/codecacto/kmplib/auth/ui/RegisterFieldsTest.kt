package br.com.codecacto.kmplib.ui

import br.com.codecacto.kmplib.mask.PhoneVisualTransformation
import br.com.codecacto.kmplib.ui.screens.register.RegisterFields
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testes para a classe RegisterFields
 */
class RegisterFieldsTest {

    @Test
    fun `test default RegisterFields values`() {
        val fields = RegisterFields()

        assertTrue(fields.showNameField)
        assertTrue(fields.showPhoneField)
        assertTrue(fields.showTermsCheckbox)
        assertTrue(fields.phoneMask is PhoneVisualTransformation)
    }

    @Test
    fun `test RegisterFields with all fields visible`() {
        val fields = RegisterFields(
            showNameField = true,
            showPhoneField = true,
            showTermsCheckbox = true
        )

        assertTrue(fields.showNameField)
        assertTrue(fields.showPhoneField)
        assertTrue(fields.showTermsCheckbox)
    }

    @Test
    fun `test RegisterFields with all fields hidden`() {
        val fields = RegisterFields(
            showNameField = false,
            showPhoneField = false,
            showTermsCheckbox = false
        )

        assertFalse(fields.showNameField)
        assertFalse(fields.showPhoneField)
        assertFalse(fields.showTermsCheckbox)
    }

    @Test
    fun `test RegisterFields with only email and password`() {
        val fields = RegisterFields(
            showNameField = false,
            showPhoneField = false,
            showTermsCheckbox = false
        )

        assertFalse(fields.showNameField)
        assertFalse(fields.showPhoneField)
        assertFalse(fields.showTermsCheckbox)
    }

    @Test
    fun `test RegisterFields with custom phone mask`() {
        val customMask = PhoneVisualTransformation()
        val fields = RegisterFields(
            phoneMask = customMask
        )

        assertEquals(customMask, fields.phoneMask)
    }

    @Test
    fun `test RegisterFields copy with showNameField`() {
        val original = RegisterFields()
        val updated = original.copy(showNameField = false)

        assertFalse(updated.showNameField)
        assertTrue(updated.showPhoneField)
        assertTrue(updated.showTermsCheckbox)
    }

    @Test
    fun `test RegisterFields copy with showPhoneField`() {
        val original = RegisterFields()
        val updated = original.copy(showPhoneField = false)

        assertTrue(updated.showNameField)
        assertFalse(updated.showPhoneField)
        assertTrue(updated.showTermsCheckbox)
    }

    @Test
    fun `test RegisterFields copy with showTermsCheckbox`() {
        val original = RegisterFields()
        val updated = original.copy(showTermsCheckbox = false)

        assertTrue(updated.showNameField)
        assertTrue(updated.showPhoneField)
        assertFalse(updated.showTermsCheckbox)
    }

    @Test
    fun `test RegisterFields minimal configuration`() {
        // Configuração mínima: apenas email, senha e confirmar senha
        val minimalFields = RegisterFields(
            showNameField = false,
            showPhoneField = false,
            showTermsCheckbox = false
        )

        assertFalse(minimalFields.showNameField)
        assertFalse(minimalFields.showPhoneField)
        assertFalse(minimalFields.showTermsCheckbox)
    }

    @Test
    fun `test RegisterFields full configuration`() {
        // Configuração completa: todos os campos visíveis
        val fullFields = RegisterFields(
            showNameField = true,
            showPhoneField = true,
            showTermsCheckbox = true
        )

        assertTrue(fullFields.showNameField)
        assertTrue(fullFields.showPhoneField)
        assertTrue(fullFields.showTermsCheckbox)
    }

    @Test
    fun `test RegisterFields selective configuration`() {
        // Configuração seletiva: nome + termos, sem telefone
        val selectiveFields = RegisterFields(
            showNameField = true,
            showPhoneField = false,
            showTermsCheckbox = true
        )

        assertTrue(selectiveFields.showNameField)
        assertFalse(selectiveFields.showPhoneField)
        assertTrue(selectiveFields.showTermsCheckbox)
    }
}
