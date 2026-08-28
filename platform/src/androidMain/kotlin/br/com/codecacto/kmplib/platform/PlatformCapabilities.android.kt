package br.com.codecacto.kmplib.platform

/**
 * Android entrega tudo: CameraX + ML Kit (câmera/OCR) e `android.graphics.pdf.PdfDocument` (PDFs).
 */
actual object PlatformCapabilities {
    actual val cameraCapture: Boolean = true
    actual val pdfGeneration: Boolean = true
}
