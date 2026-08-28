package br.com.codecacto.kmplib.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "O app não pode vender o que não tem": filtragem de features por capacidade de plataforma.
 * O resolvedor é injetado, então os dois alvos são testados de qualquer host.
 */
class PlatformCapabilityTest {

    private val android: (PlatformCapability) -> Boolean = { true }
    private val ios: (PlatformCapability) -> Boolean = { false } // hoje: câmera e PDF são stub

    private val paywallHighlights = listOf(
        "Chamadas ilimitadas".alwaysAvailable(),
        "Exportar PDF" requiring PlatformCapability.PdfGeneration,
        "Foto do veículo" requiring PlatformCapability.CameraCapture,
    )

    @Test
    fun `android vende todos os destaques`() {
        assertEquals(
            listOf("Chamadas ilimitadas", "Exportar PDF", "Foto do veículo"),
            paywallHighlights.availableValues(android),
        )
    }

    @Test
    fun `ios nao vende PDF nem camera`() {
        assertEquals(listOf("Chamadas ilimitadas"), paywallHighlights.availableValues(ios))
    }

    @Test
    fun `feature sem capacidade exigida sempre aparece`() {
        val f = "Sem anúncios".alwaysAvailable()
        assertEquals(null, f.requires)
        assertEquals(listOf("Sem anúncios"), listOf(f).availableValues(ios))
    }

    @Test
    fun `capacidade indisponivel esconde apenas a feature dependente`() {
        val semPdf: (PlatformCapability) -> Boolean = { it != PlatformCapability.PdfGeneration }
        assertEquals(
            listOf("Chamadas ilimitadas", "Foto do veículo"),
            paywallHighlights.availableValues(semPdf),
        )
    }

    @Test
    fun `isAvailable espelha o PlatformCapabilities da plataforma corrente`() {
        assertEquals(PlatformCapabilities.pdfGeneration, PlatformCapability.PdfGeneration.isAvailable)
        assertEquals(PlatformCapabilities.cameraCapture, PlatformCapability.CameraCapture.isAvailable)
    }

    @Test
    fun `android entrega camera e pdf`() {
        // Este teste roda no unit test do Android: os actuals de androidMain devem ser true/true.
        assertTrue(PlatformCapabilities.cameraCapture)
        assertTrue(PlatformCapabilities.pdfGeneration)
    }
}
