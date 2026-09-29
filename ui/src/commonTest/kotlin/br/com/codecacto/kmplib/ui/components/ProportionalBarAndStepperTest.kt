package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProportionalBarAndStepperTest {

    @Test
    fun fractions_sumToOne_andIgnoreNegativeAndNaN() {
        val f = proportionalFractions(listOf(1.0, 3.0, -5.0, Double.NaN))
        assertEquals(listOf(0.25f, 0.75f, 0f, 0f), f)
        assertTrue(kotlin.math.abs(f.sum() - 1f) < 1e-6f)
    }

    @Test
    fun fractions_zeroTotal_isAllZero() {
        assertEquals(listOf(0f, 0f), proportionalFractions(listOf(0.0, 0.0)))
        assertEquals(emptyList(), proportionalFractions(emptyList()))
    }

    @Test
    fun percents_alwaysSumTo100() {
        assertEquals(listOf(34, 33, 33), proportionalPercents(listOf(1.0, 1.0, 1.0)))
        assertEquals(100, proportionalPercents(listOf(7.0, 13.0, 29.0, 51.0, 3.0)).sum())
        assertEquals(listOf(100, 0), proportionalPercents(listOf(5.0, 0.0)))
    }

    @Test
    fun percents_zeroTotal_isAllZero() {
        assertEquals(listOf(0, 0, 0), proportionalPercents(listOf(0.0, 0.0, 0.0)))
    }

    @Test
    fun stepper_clampsToBounds() {
        assertEquals(4, stepperNextValue(3, 1, 0, 10))
        assertEquals(0, stepperNextValue(0, -1, 0, 10))
        assertEquals(10, stepperNextValue(9, 5, 0, 10))
        assertEquals(-2, stepperNextValue(-1, -1, -5, 5))
    }

    @Test
    fun stepper_doesNotOverflowInt() {
        assertEquals(Int.MAX_VALUE, stepperNextValue(Int.MAX_VALUE, 1, 0, Int.MAX_VALUE))
        assertEquals(Int.MIN_VALUE, stepperNextValue(Int.MIN_VALUE, -1, Int.MIN_VALUE, 0))
    }

    @Test
    fun stepper_rejectsInvertedBounds() {
        assertFailsWith<IllegalArgumentException> { stepperNextValue(1, 1, 5, 0) }
    }
}
