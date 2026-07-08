package br.com.codecacto.kmplib.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataLayerTest {

    @Test
    fun localVariantsAreLocalAndNotCentral() {
        for (value in listOf(DataLayer.None, DataLayer.LocalOnly)) {
            assertTrue(value.isLocal, "$value deveria ser local")
            assertFalse(value.usesCentralData, "$value não usa dado central")
        }
    }

    @Test
    fun centralIsCentralAndNotLocal() {
        assertTrue(DataLayer.Central.usesCentralData)
        assertFalse(DataLayer.Central.isLocal)
    }

    @Test
    fun firestoreLegacyIsNeitherLocalNorCentral() {
        @Suppress("DEPRECATION")
        val legacy = DataLayer.Firestore
        assertFalse(legacy.isLocal)
        assertFalse(legacy.usesCentralData)
    }

    @Test
    fun exposesExactlyTheExpectedValues() {
        assertEquals(
            listOf("None", "LocalOnly", "Central", "Firestore"),
            DataLayer.entries.map { it.name },
        )
    }
}
