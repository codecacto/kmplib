package br.com.codecacto.kmplib.platform

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * O Android ENTREGA câmera e PDF (`actual`s de androidMain): se um deles virar `false`, o app deixa
 * de vender a feature no Android sem ninguém perceber. Mora em androidUnitTest porque é afirmação
 * sobre o `actual` desta plataforma — em commonTest ela rodava também no simulador iOS e falhava.
 */
class PlatformCapabilitiesAndroidTest {

    @Test
    fun `android entrega camera e pdf`() {
        assertTrue(PlatformCapabilities.cameraCapture)
        assertTrue(PlatformCapabilities.pdfGeneration)
        assertTrue(PlatformCapability.CameraCapture.isAvailable)
        assertTrue(PlatformCapability.PdfGeneration.isAvailable)
    }
}
