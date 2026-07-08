package br.com.codecacto.kmplib.platform

/**
 * iOS: dívidas conhecidas e **declaradas** (nada de stub silencioso).
 *
 * - [cameraCapture] = `false`: `CameraView.ios.kt` é placeholder estático.
 * - [pdfGeneration] = `false`: os 9 geradores de PDF lançam `OsPdfNotSupportedException`.
 *
 * Ambas as dívidas exigem host macOS para implementar/validar (Kotlin/Native iOS não compila em
 * Linux). Enquanto isso, o app **esconde** a feature no iOS lendo estes flags.
 */
actual object PlatformCapabilities {
    actual val cameraCapture: Boolean = false
    actual val pdfGeneration: Boolean = false
}
