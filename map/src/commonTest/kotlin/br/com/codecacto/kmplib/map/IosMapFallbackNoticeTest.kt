package br.com.codecacto.kmplib.map

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosMapFallbackNoticeTest {

    @AfterTest
    fun limpa() = IosMapFallbackNotice.resetForTest()

    @Test
    fun avisaSoUmaVezPorProcesso() {
        IosMapFallbackNotice.resetForTest()
        assertTrue(IosMapFallbackNotice.claim(), "a primeira queda no MapKit registra o aviso")
        repeat(5) { assertFalse(IosMapFallbackNotice.claim(), "recomposição e outros mapas não repetem o aviso") }
    }

    @Test
    fun mensagemDizOPadraoEComoRegistrarAPonte() {
        val m = IosMapFallbackNotice.MESSAGE
        assertTrue("Google Maps" in m)
        assertTrue("IosMapBridge.shared.register(factory:" in m)
        assertTrue("GMSServices.provideAPIKey" in m)
        assertTrue("MapKit" in m)
    }
}
