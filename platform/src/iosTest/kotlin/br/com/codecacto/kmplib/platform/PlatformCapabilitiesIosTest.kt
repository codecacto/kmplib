package br.com.codecacto.kmplib.platform

import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * iOS: câmera/OCR e os 9 PDFs legados continuam DECLARADOS indisponíveis (ver o KDoc de
 * `PlatformCapabilities.ios.kt`) — o app não vende no iOS o que não foi validado lá. Virar `true` é
 * decisão deliberada, depois da validação no aparelho; este teste obriga quem virar a atualizá-lo
 * junto, em vez de a capacidade mudar de lado em silêncio.
 */
class PlatformCapabilitiesIosTest {

    @Test
    fun ios_declara_camera_e_pdf_indisponiveis_ate_a_validacao() {
        assertFalse(PlatformCapabilities.cameraCapture)
        assertFalse(PlatformCapabilities.pdfGeneration)
        assertFalse(PlatformCapability.CameraCapture.isAvailable)
        assertFalse(PlatformCapability.PdfGeneration.isAvailable)
    }
}
