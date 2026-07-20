package br.com.codecacto.kmplib.platform

/**
 * iOS: dívidas conhecidas e **declaradas** (nada de stub silencioso).
 *
 * **Estado 2.78.0 (auditoria):** os `actual` iOS de **câmera/OCR** (`CameraView.ios.kt` +
 * `PlateOcrAnalyzer.ios.kt`, AVFoundation + Apple Vision) e dos **9 geradores de PDF** (agora TODOS
 * reais via `UIGraphicsPDFRenderer` + CoreText — `IosPdfCanvas`/`renderIosPdfPaged`) foram
 * IMPLEMENTADOS no código. **Porém os flags permanecem `false`** porque o build Kotlin/Native iOS
 * **não roda em Linux** — o código é fiel ao par Android mas **não foi compilado/validado em macOS**.
 *
 * **Flip para `true` é o passo final em host macOS**, após:
 *  - `pdfGeneration = true`: compilar os alvos iOS + validação visual dos 9 PDFs;
 *  - `cameraCapture = true`: compilar + testar a captura/OCR num device (preview, permissão, Vision).
 *
 * Enquanto os flags são `false`, o app **esconde/não vende** a feature no iOS (via `PlatformCapability`
 * + `availableValues()`/`CapabilityGate`). Nenhum app precisa mudar quando o flag virar `true`.
 */
actual object PlatformCapabilities {
    actual val cameraCapture: Boolean = false
    actual val pdfGeneration: Boolean = false
}
