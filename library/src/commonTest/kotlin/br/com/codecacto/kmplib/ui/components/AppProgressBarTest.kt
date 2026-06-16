package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class AppProgressBarTest {

    // ========================
    // normalizeProgress
    // ========================

    @Test
    fun normalizeProgress_keepsValueInRange() {
        assertEquals(0f, normalizeProgress(0f))
        assertEquals(0.5f, normalizeProgress(0.5f))
        assertEquals(1f, normalizeProgress(1f))
    }

    @Test
    fun normalizeProgress_clampsBelowZero() {
        assertEquals(0f, normalizeProgress(-0.3f))
    }

    @Test
    fun normalizeProgress_clampsAboveOne() {
        assertEquals(1f, normalizeProgress(1.7f))
    }

    @Test
    fun normalizeProgress_treatsNaNAsZero() {
        assertEquals(0f, normalizeProgress(Float.NaN))
    }

    // ========================
    // percentToFraction
    // ========================

    @Test
    fun percentToFraction_convertsPercentToFraction() {
        assertEquals(0f, percentToFraction(0))
        assertEquals(0.25f, percentToFraction(25))
        assertEquals(0.75f, percentToFraction(75))
        assertEquals(1f, percentToFraction(100))
    }

    @Test
    fun percentToFraction_clampsOutOfRange() {
        assertEquals(0f, percentToFraction(-10))
        assertEquals(1f, percentToFraction(140))
    }
}
