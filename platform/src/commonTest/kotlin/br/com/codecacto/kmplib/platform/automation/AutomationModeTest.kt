package br.com.codecacto.kmplib.platform.automation

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(AutomationModeApi::class)
class AutomationModeTest {

    @AfterTest
    fun depois() = AutomationMode.reset()

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
