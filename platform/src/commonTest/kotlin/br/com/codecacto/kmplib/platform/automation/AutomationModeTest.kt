package br.com.codecacto.kmplib.platform.automation

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(AutomationModeApi::class)
class AutomationModeTest {

    @BeforeTest
    fun antes() {
        // O simulador da fila do Mac pode estar marcado pelo runner: o teste decide a marca.
        qaRunnerMarkOverride = { false }
    }

    @AfterTest
    fun depois() {
        AutomationMode.reset()
        qaRunnerMarkOverride = null
    }

    @Test
    fun marcaDoRunnerLigaOModoECalaOsPedidos() {
        qaRunnerMarkOverride = { true }
        assertEquals(AutomationSignal.QA_RUNNER, AutomationMode.signal)
        assertTrue(AutomationMode.suppressesAutomaticPrompts)
    }

    @Test
    fun declaracaoVenceAMarcaDoRunner() {
        qaRunnerMarkOverride = { true }
        AutomationMode.activate(AutomationSignal.STORE_DOUBLE)
        assertEquals(AutomationSignal.STORE_DOUBLE, AutomationMode.signal)
    }

    @Test
    fun resetNaoDesligaAMarcaDoRunner() {
        qaRunnerMarkOverride = { true }
        AutomationMode.activate()
        AutomationMode.reset()
        assertEquals(AutomationSignal.QA_RUNNER, AutomationMode.signal, "a marca é do aparelho, não declaração")
    }

    @Test
    fun processoComumNaoEAutomacao() {
        // Teste JVM/nativo não é aparelho em Test Harness: sem declaração, o modo fica desligado.
        assertNull(AutomationMode.signal)
        assertFalse(AutomationMode.isActive)
        assertFalse(AutomationMode.suppressesAutomaticPrompts)
    }

    @Test
    fun activateLigaOModoECalaOsPedidos() {
        AutomationMode.activate(AutomationSignal.STORE_DOUBLE)
        assertTrue(AutomationMode.isActive)
        assertTrue(AutomationMode.suppressesAutomaticPrompts)
        assertEquals(AutomationSignal.STORE_DOUBLE, AutomationMode.signal)
    }

    @Test
    fun oPrimeiroSinalDeclaradoPermanece() {
        AutomationMode.activate(AutomationSignal.STORE_DOUBLE)
        AutomationMode.activate(AutomationSignal.TEST_BUILD)
        assertEquals(AutomationSignal.STORE_DOUBLE, AutomationMode.signal)
    }

    @Test
    fun activateSemArgumentoDeclaraBuildDeTeste() {
        AutomationMode.activate()
        assertEquals(AutomationSignal.TEST_BUILD, AutomationMode.signal)
    }

    @Test
    fun resetEsqueceADeclaracao() {
        AutomationMode.activate()
        AutomationMode.reset()
        assertFalse(AutomationMode.isActive)
    }
}
