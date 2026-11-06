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

    private class ContaFalsa(var visto: Boolean = false, var fora: Boolean = false) : ProductTourAccountStore {
        var envios = 0
        override suspend fun isSeen(key: String): Boolean {
            if (fora) error("rede")
            return visto
        }
        override suspend fun markSeen(key: String) {
            if (fora) error("rede")
            envios++
            visto = true
        }
    }

    @Test
    fun `aparelho limpo e conta que ja viu nao abre`() = runTest {
        val prefs = InMemoryAppPreferences()
        assertFalse(ProductTourFirstRun("t.v1", prefs, ContaFalsa(visto = true)).shouldOpen())
        assertTrue(ProductTourFirstRun("t.v1", prefs).hasBeenSeen())
    }

    @Test
    fun `conta que nunca viu abre, e concluir grava nos dois`() = runTest {
        val conta = ContaFalsa()
        val tour = ProductTourFirstRun("t.v1", InMemoryAppPreferences(), conta)
        assertTrue(tour.shouldOpen())
        tour.markSeen()
        assertTrue(conta.visto)
        assertFalse(tour.shouldOpen())
    }

    @Test
    fun `servidor fora nao abre e nao marca`() = runTest {
        val prefs = InMemoryAppPreferences()
        assertFalse(ProductTourFirstRun("t.v1", prefs, ContaFalsa(fora = true)).shouldOpen())
        assertFalse(ProductTourFirstRun("t.v1", prefs).hasBeenSeen())
    }

    @Test
    fun `visto so no aparelho sobe para a conta uma vez e a falha tenta de novo`() = runTest {
        val prefs = InMemoryAppPreferences()
        ProductTourFirstRun("t.v1", prefs).markSeen()
        val conta = ContaFalsa(fora = true)
        assertFalse(ProductTourFirstRun("t.v1", prefs, conta).shouldOpen())
        assertEquals(0, conta.envios)
        conta.fora = false
        assertFalse(ProductTourFirstRun("t.v1", prefs, conta).shouldOpen())
        assertFalse(ProductTourFirstRun("t.v1", prefs, conta).shouldOpen())
        assertEquals(1, conta.envios)
    }

    @Test
    fun `sem conta segue como antes`() = runTest {
        val tour = ProductTourFirstRun("t.v1", InMemoryAppPreferences())
        assertTrue(tour.shouldOpen())
        tour.markLocally()
        assertFalse(tour.shouldOpen())
    }
}
