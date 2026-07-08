package br.com.codecacto.kmplib.platform

/**
 * **Capacidades reais da plataforma corrente** — o que a kmplib de fato entrega em cada alvo.
 *
 * Existe porque alguns `actual` de iOS ainda são **placeholders/stubs** (dívida registrada). Sem esta
 * fachada, o stub é *silencioso*: o app compila, a tela abre e o usuário do iPhone descobre no uso que
 * "a foto do veículo não funciona" (MeuEstacionamento) ou que o **export PDF vendido como Pro** lança
 * exceção (ChamadaFacil). Consultar aqui permite **esconder/desabilitar a feature** — ou nem vender —
 * enquanto a dívida não é paga em host macOS.
 *
 * > Estes flags são **constantes de build**, não estado de runtime. Quando o `actual` iOS for
 * > implementado de verdade, o flag vira `true` e o app volta a exibir a feature sem mudar código.
 *
 * ```kotlin
 * if (PlatformCapabilities.pdfGeneration) {
 *     MenuItem("Exportar PDF") { export() }   // some no iOS enquanto for stub
 * }
 * ```
 */
expect object PlatformCapabilities {

    /**
     * `CameraView` com preview ao vivo + OCR de placa + captura de JPEG.
     *
     * - Android: `true` (CameraX + ML Kit).
     * - iOS: `false` — o `actual` desenha só um placeholder estático (nunca chama o callback).
     *   Dívida: `AVCaptureSession` + `AVCapturePhotoOutput` + Apple Vision, requer host macOS.
     */
    val cameraCapture: Boolean

    /**
     * Geradores de PDF estruturados (`OsPdf`, `ReciboPdf`, `DocumentPdf`, `TableReportPdf`,
     * `FinanceReportPdf`, `HoursReportPdf`, `WorkReportPdf`, `VaccinationCardPdf`, `InspectionPdf`).
     *
     * - Android: `true` (`android.graphics.pdf.PdfDocument`).
     * - iOS: `false` — **todos os 9 geradores lançam** `OsPdfNotSupportedException`/
     *   `ReciboPdfNotSupportedException`. Dívida: desenhar com **CoreText** (`CTFramesetter`/`CTLine`,
     *   exportado no Kotlin/Native) dentro de `UIGraphicsPDFRenderer`, em vez das categorias de
     *   desenho de texto do UIKit (`NSString.drawAtPoint:withAttributes:`) que não vêm no K/N 2.x.
     *
     * `renderPdfPagesToImages` (rasterizador) **não** é afetado: funciona nas duas plataformas.
     */
    val pdfGeneration: Boolean
}
