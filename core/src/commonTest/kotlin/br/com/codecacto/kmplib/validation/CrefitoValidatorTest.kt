package br.com.codecacto.kmplib.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrefitoValidatorTest {

    @Test
    fun valid_crefito_with_F() {
        assertTrue(CrefitoValidator.isValid("123456F"))
    }

    @Test
    fun valid_crefito_with_T() {
        assertTrue(CrefitoValidator.isValid("123456T"))
    }

    @Test
    fun valid_when_lowercase() {
        assertTrue(CrefitoValidator.isValid("123456f"))
    }

    @Test
    fun invalid_when_no_letter() {
        assertFalse(CrefitoValidator.isValid("123456"))
    }

    @Test
    fun invalid_when_wrong_letter() {
        assertFalse(CrefitoValidator.isValid("123456X"))
    }

    @Test
    fun invalid_when_short() {
        assertFalse(CrefitoValidator.isValid("12345F"))
    }
}
