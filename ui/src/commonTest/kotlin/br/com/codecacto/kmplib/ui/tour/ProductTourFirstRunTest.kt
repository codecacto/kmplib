package br.com.codecacto.kmplib.ui.tour

import br.com.codecacto.kmplib.core.prefs.InMemoryAppPreferences
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProductTourFirstRunTest {

    @Test
    fun `nasce nao visto, grava e outra instancia le`() = runTest {
        val prefs = InMemoryAppPreferences()
        val primeira = ProductTourFirstRun("tour.inicio.v1", prefs)
        assertFalse(primeira.hasBeenSeen())
        primeira.markSeen()
        assertTrue(primeira.hasBeenSeen())
        assertTrue(ProductTourFirstRun("tour.inicio.v1", prefs).hasBeenSeen())
        assertTrue(prefs.getBoolean("kmplib.tour.seen.tour.inicio.v1"))
    }

    @Test
    fun `chave versionada mostra o tour novo a quem viu o antigo`() = runTest {
        val prefs = InMemoryAppPreferences()
        ProductTourFirstRun("tour.inicio.v1", prefs).markSeen()
        assertFalse(ProductTourFirstRun("tour.inicio.v2", prefs).hasBeenSeen())
    }

    @Test
    fun `reset volta a abrir`() = runTest {
        val prefs = InMemoryAppPreferences()
        val fr = ProductTourFirstRun("tour", prefs)
        fr.markSeen()
        fr.reset()
        assertFalse(fr.hasBeenSeen())
        assertFalse(ProductTourFirstRun("tour", prefs).hasBeenSeen())
    }

    @Test
    fun `chave com espacos e aparada e vazia e recusada`() {
        assertEquals("kmplib.tour.seen.x", ProductTourFirstRun("  x ", InMemoryAppPreferences()).storageKey)
        assertFailsWith<IllegalArgumentException> { ProductTourFirstRun("  ", InMemoryAppPreferences()) }
    }
}
