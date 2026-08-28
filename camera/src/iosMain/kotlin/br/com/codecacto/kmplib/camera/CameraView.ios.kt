@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package br.com.codecacto.kmplib.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.CoreGraphics.CGRectMake
import platform.CoreImage.CIContext
import platform.CoreImage.CIImage
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.Foundation.NSData
import platform.QuartzCore.CACurrentMediaTime
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIView
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import platform.posix.memcpy

/**
 * Implementação iOS **real** do [CameraView] — **AVFoundation** (`AVCaptureSession` +
 * `AVCaptureVideoDataOutput`) para o preview/stream ao vivo e **Apple Vision** (`VNRecognizeTextRequest`)
 * para o OCR de placa on-device, em paridade com o **CameraX + ML Kit** do Android. Padrão-ouro:
 * APIs oficiais da Apple, sem WebView/terceiros.
 *
 * Arquitetura:
 *  - `AVCaptureSession` (preset alto) com input da câmera traseira.
 *  - `AVCaptureVideoPreviewLayer` embutido num `UIView` (via [UIKitView] do Compose MP), redimensionado
 *    em `layoutSubviews`.
 *  - `AVCaptureVideoDataOutput` com delegate ([PlateCaptureDelegate]) que, **com throttle**, roda a
 *    Vision no `CVPixelBuffer` de cada frame; ao extrair uma placa válida ([extractPlate]) dispara o
 *    callback UMA vez (na main thread). Na variante com foto, **o próprio frame reconhecido** é
 *    codificado em **JPEG** (via `CIImage`→`CIContext`→`UIImage`→`UIImageJPEGRepresentation`) — captura
 *    atômica: a foto é exatamente o quadro em que a placa foi lida.
 *
 * **Best-effort:** sem câmera/permissão negada, a sessão simplesmente não entrega frames (não lança); o
 * app deve pedir a permissão de câmera antes (`platform/permission/PermissionManager`).
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS):** escrito conforme as APIs oficiais Apple; o build
 * Kotlin/Native iOS **não roda em Linux** — compilar + testar num device (preview, orientação do buffer,
 * OCR, permissão) é o passo final em macOS. Info.plist exige `NSCameraUsageDescription`.
 */
@Composable
actual fun CameraView(
    onPlateCaptured: (String) -> Unit,
    modifier: Modifier,
) {
    PlateCamera(modifier, wantsPhoto = false) { plate, _ -> onPlateCaptured(plate) }
}

@Composable
actual fun CameraView(
    onCapture: (placa: String, jpegBytes: ByteArray) -> Unit,
    modifier: Modifier,
) {
    PlateCamera(modifier, wantsPhoto = true) { plate, jpeg -> if (jpeg != null) onCapture(plate, jpeg) }
}

@Composable
private fun PlateCamera(
    modifier: Modifier,
    wantsPhoto: Boolean,
    onResult: (String, ByteArray?) -> Unit,
) {
    val controller = remember { PlateCameraController(wantsPhoto, onResult) }

    DisposableEffect(Unit) {
        controller.start()
        onDispose { controller.stop() }
    }

    UIKitView(
        factory = { controller.previewView },
        modifier = modifier,
    )
}

/**
 * `UIView` que hospeda o `AVCaptureVideoPreviewLayer` da sessão e o redimensiona ao seu bounds.
 */
private class PreviewUIView(session: AVCaptureSession) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    val previewLayer: AVCaptureVideoPreviewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    init {
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        previewLayer.setFrame(bounds)
    }
}

/**
 * Encapsula a `AVCaptureSession` + o delegate de OCR e expõe o [previewView] para o Compose embutir.
 */
private class PlateCameraController(
    wantsPhoto: Boolean,
    onResult: (String, ByteArray?) -> Unit,
) {
    private val session = AVCaptureSession()
    private val delegate = PlateCaptureDelegate(wantsPhoto, onResult)
    private val sessionQueue = dispatch_queue_create("br.com.codecacto.kmplib.camera.session", null)
    private val videoQueue = dispatch_queue_create("br.com.codecacto.kmplib.camera.ocr", null)

    val previewView: PreviewUIView by lazy { PreviewUIView(session) }

    fun start() {
        // Preview é criado (lazy) na main thread pelo factory do UIKitView; a config/arranque da
        // sessão vai para uma fila própria (Apple recomenda NÃO configurar/startar na main).
        dispatch_async(sessionQueue) {
            session.beginConfiguration()
            session.sessionPreset = AVCaptureSessionPresetHigh

            val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
            val input = device?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, error = null) }
            if (input != null && session.canAddInput(input)) session.addInput(input)

            val output = AVCaptureVideoDataOutput().apply {
                alwaysDiscardsLateVideoFrames = true
                setSampleBufferDelegate(delegate, queue = videoQueue)
            }
            if (session.canAddOutput(output)) session.addOutput(output)

            session.commitConfiguration()
            session.startRunning()
        }
    }

    fun stop() {
        // stopRunning é idempotente/seguro mesmo se a sessão não estiver rodando.
        dispatch_async(sessionQueue) {
            session.stopRunning()
        }
    }
}

/**
 * Delegate de frames: roda a Vision (com throttle) e dispara o callback uma única vez.
 */
private class PlateCaptureDelegate(
    private val wantsPhoto: Boolean,
    private val onResult: (String, ByteArray?) -> Unit,
) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {

    private var lastRunSeconds = 0.0
    private var handled = false
    private val ciContext = CIContext(options = null)

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection,
    ) {
        if (handled) return
        val sampleBuffer = didOutputSampleBuffer ?: return

        val now = CACurrentMediaTime()
        if (now - lastRunSeconds < THROTTLE_SECONDS) return
        lastRunSeconds = now

        val pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) ?: return

        val text = recognizeText(pixelBuffer) ?: return
        val plate = extractPlate(text) ?: return

        handled = true
        val jpeg = if (wantsPhoto) pixelBufferToJpeg(pixelBuffer) else null
        dispatch_async(dispatch_get_main_queue()) {
            onResult(plate, jpeg)
        }
    }

    /** Roda `VNRecognizeTextRequest` no [pixelBuffer] e concatena o texto reconhecido. */
    private fun recognizeText(pixelBuffer: platform.CoreVideo.CVPixelBufferRef): String? {
        val recognized = StringBuilder()
        val request = VNRecognizeTextRequest { req, error ->
            if (error != null) return@VNRecognizeTextRequest
            val results = req?.results ?: return@VNRecognizeTextRequest
            for (result in results) {
                val observation = result as? VNRecognizedTextObservation ?: continue
                val candidate = observation.topCandidates(1u).firstOrNull() as? VNRecognizedText
                candidate?.string?.let { recognized.append(it).append('\n') }
            }
        }.apply {
            recognitionLevel = VNRequestTextRecognitionLevelAccurate
            usesLanguageCorrection = false
        }
        val handler = VNImageRequestHandler(cVPixelBuffer = pixelBuffer, options = emptyMap<Any?, Any?>())
        handler.performRequests(listOf(request), error = null)
        return recognized.toString().takeIf { it.isNotBlank() }
    }

    /** Codifica o frame [pixelBuffer] reconhecido em JPEG (q=0.85). */
    private fun pixelBufferToJpeg(pixelBuffer: platform.CoreVideo.CVPixelBufferRef): ByteArray? {
        val ciImage = CIImage(cVPixelBuffer = pixelBuffer)
        // UIImage pode ser criado diretamente a partir de CIImage
        val uiImage = UIImage(cIImage = ciImage)
        val data = UIImageJPEGRepresentation(uiImage, 0.85) ?: return null
        return data.toByteArray()
    }
}

// Constante movida para fora da classe NSObject para evitar erro em K/N 2.x
private const val THROTTLE_SECONDS = 0.6

private fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len == 0) return ByteArray(0)
    val out = ByteArray(len)
    bytes?.let { src ->
        out.usePinned { pinned ->
            memcpy(pinned.addressOf(0), src, len.toULong())
        }
    }
    return out
}
