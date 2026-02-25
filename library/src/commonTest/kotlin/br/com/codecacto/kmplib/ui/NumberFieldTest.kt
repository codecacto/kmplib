package br.com.codecacto.kmplib.ui

import br.com.codecacto.kmplib.ui.components.toDoubleFromNumberField
import br.com.codecacto.kmplib.ui.components.toIntFromNumberField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Testes para NumberField helpers
 */
class NumberFieldTest {

    @Test
    fun `toDoubleFromNumberField converts comma to dot`() {
        val value = "123,45"
        val result = value.toDoubleFromNumberField()
        assertEquals(123.45, result)
    }

    @Test
    fun `toDoubleFromNumberField handles integer`() {
        val value = "100"
        val result = value.toDoubleFromNumberField()
        assertEquals(100.0, result)
    }

    @Test
    fun `toDoubleFromNumberField handles invalid string`() {
        val value = "abc"
        val result = value.toDoubleFromNumberField()
        assertNull(result)
    }

    @Test
    fun `toDoubleFromNumberField handles empty string`() {
        val value = ""
        val result = value.toDoubleFromNumberField()
        assertNull(result)
    }

    @Test
    fun `toIntFromNumberField converts string to int`() {
        val value = "123"
        val result = value.toIntFromNumberField()
        assertEquals(123, result)
    }

    @Test
    fun `toIntFromNumberField handles invalid string`() {
        val value = "abc"
        val result = value.toIntFromNumberField()
        assertNull(result)
    }

    @Test
    fun `toIntFromNumberField handles decimal string`() {
        val value = "123.45"
        val result = value.toIntFromNumberField()
        assertNull(result)
    }
}
