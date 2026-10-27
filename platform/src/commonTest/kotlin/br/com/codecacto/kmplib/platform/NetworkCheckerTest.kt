package br.com.codecacto.kmplib.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A regra do `NetworkChecker` (2.329.0): estado desconhecido não vira "offline". */
class NetworkCheckerTest {
    @Test
    fun estadoConhecidoDecide() {
        assertTrue(networkAvailableFrom(true))
        assertFalse(networkAvailableFrom(false))
    }

    @Test
    fun desconhecidoNaoInventaOffline() {
        assertTrue(networkAvailableFrom(null))
    }
}
