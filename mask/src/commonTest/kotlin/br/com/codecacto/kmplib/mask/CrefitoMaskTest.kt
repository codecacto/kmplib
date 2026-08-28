package br.com.codecacto.kmplib.mask

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals

class CrefitoMaskTest {

    private val transformation = CrefitoVisualTransformation()

    @Test
    fun applies_dash_after_six_digits() {
        val transformed = transformation.filter(AnnotatedString("123456F"))
        assertEquals("123456-F", transformed.text.text)
    }

    @Test
    fun no_dash_below_six_digits() {
        val transformed = transformation.filter(AnnotatedString("1234"))
        assertEquals("1234", transformed.text.text)
    }

    @Test
    fun offset_mapping_handles_dash() {
        val transformed = transformation.filter(AnnotatedString("123456F"))
        assertEquals(6, transformed.offsetMapping.originalToTransformed(6))
        assertEquals(8, transformed.offsetMapping.originalToTransformed(7))
        assertEquals(7, transformed.offsetMapping.transformedToOriginal(8))
    }

    @Test
    fun filterCrefitoInput_strips_non_digits_and_keeps_suffix() {
        assertEquals("123456F", filterCrefitoInput("12-34.56F"))
        assertEquals("123456T", filterCrefitoInput("123456t"))
        assertEquals("1234", filterCrefitoInput("12a34"))
    }

    @Test
    fun filterCrefitoInput_drops_suffix_if_not_six_digits() {
        assertEquals("12345", filterCrefitoInput("12345F"))
    }
}
