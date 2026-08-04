@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package br.com.codecacto.kmplib.camera.barcode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.hasTorch
import platform.AVFoundation.torchMode
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create

/**
 * Implementação iOS do [BarcodeCameraPreview] — **AVFoundation** com
 * **`AVCaptureMetadataOutput`**.
 *
 * ## Por que `AVCaptureMetadataOutput` e não Vision
 *
 * Para leitura **ao vivo**, a Apple decodifica códigos **dentro do pipeline de captura**: o
 * `AVCaptureMetadataOutput` devolve `AVMetadataMachineReadableCodeObject` já pronto, sem copiar
 * cada frame para a CPU e sem rodar um request de visão por quadro. É o caminho oficial e de longe
 * o mais econômico — e economia importa num app que fica com a câmera aberta o turno inteiro
 * (bateria do aparelho do operador de loja). O `VNDetectBarcodesRequest` é o caminho oficial para
 * **imagem parada**, e é o que o [BarcodeAnalyzer] usa.
 *
 * ## Arquitetura (espelha o `CameraView` de placa)
 *
 * - `AVCaptureSession` (preset alto) com input da câmera traseira;
 * - `AVCaptureVideoPreviewLayer` embutido num `UIView` via [UIKitView], redimensionado em
 *   `layoutSubviews`;
 * - `AVCaptureMetadataOutput` com delegate ([BarcodeMetadataDelegate]) numa fila própria; os
 *   valores passam por [parseBarcode] (verificador conferido) e são entregues **na main thread**.
 *
 * Configurar/iniciar a sessão fora da main thread é recomendação explícita da Apple.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS):** escrito conforme as APIs oficiais; o build
 * Kotlin/Native iOS não roda em Linux. Info.plist exige `NSCameraUsageDescription`.
 */
@Composable
actual fun BarcodeCameraPreview(
    onBarcodesDetected: (List<ScannedBarcode>) -> Unit,
    modifier: Modifier,
    formats: Set<BarcodeFormat>,
    torchEnabled: Boolean,
    onCameraStatus: (BarcodeCameraStatus) -> Unit,
) {
    val currentOnDetected by rememberUpdatedState(onBarcodesDetected)
    val currentOnStatus by rememberUpdatedState(onCameraStatus)

    val controller = remember(formats) {
        BarcodeCameraController(
            formats = formats.ifEmpty { BarcodeFormats.RETAIL },
            onResults = { results -> currentOnDetected(results) },
            onStatus = { status -> currentOnStatus(status) },
        )
    }

    DisposableEffect(controller) {
        controller.start()
        onDispose { controller.stop() }
    }

    LaunchedEffect(controller, torchEnabled) {
        controller.setTorch(torchEnabled)
    }

    UIKitView(
        factory = { controller.previewView },
        modifier = modifier,
    )
}

/** `UIView` que hospeda o preview layer da sessão e o redimensiona ao seu bounds. */
private class BarcodePreviewUIView(session: AVCaptureSession) :
    UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    val previewLayer: AVCaptureVideoPreviewLayer =
        AVCaptureVideoPreviewLayer(session = session).apply {
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

/** Encapsula a sessão de captura, a lanterna e o delegate de metadata. */
private class BarcodeCameraController(
    private val formats: Set<BarcodeFormat>,
    onResults: (List<ScannedBarcode>) -> Unit,
    private val onStatus: (BarcodeCameraStatus) -> Unit,
) {
    private val session = AVCaptureSession()
    private val delegate = BarcodeMetadataDelegate(onResults)
    private val sessionQueue = dispatch_queue_create("br.com.codecacto.kmplib.camera.barcode.session", null)
    private val metadataQueue = dispatch_queue_create("br.com.codecacto.kmplib.camera.barcode.meta", null)
    private var device: AVCaptureDevice? = null

    val previewView: BarcodePreviewUIView by lazy { BarcodePreviewUIView(session) }

    fun start() {
        dispatch_async(sessionQueue) {
            val camera = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
            if (camera == null) {
                dispatch_async(dispatch_get_main_queue()) {
                    onStatus(BarcodeCameraStatus.Unavailable)
                }
                return@dispatch_async
            }
            device = camera

            session.beginConfiguration()
            session.sessionPreset = AVCaptureSessionPresetHigh

            val input = AVCaptureDeviceInput.deviceInputWithDevice(camera, error = null)
            if (input == null || !session.canAddInput(input)) {
                session.commitConfiguration()
                dispatch_async(dispatch_get_main_queue()) {
                    onStatus(BarcodeCameraStatus.Failed("Não foi possível abrir a câmera"))
                }
                return@dispatch_async
            }
            session.addInput(input)

            val output = AVCaptureMetadataOutput()
            if (!session.canAddOutput(output)) {
                session.commitConfiguration()
                dispatch_async(dispatch_get_main_queue()) {
                    onStatus(BarcodeCameraStatus.Failed("Saída de leitura indisponível"))
                }
                return@dispatch_async
            }
            session.addOutput(output)
            output.setMetadataObjectsDelegate(delegate, queue = metadataQueue)

            // `metadataObjectTypes` só pode conter tipos presentes em `availableMetadataObjectTypes`
            // (a AVFoundation lança NSInvalidArgumentException caso contrário) — e o conjunto
            // disponível depende do aparelho e da versão do iOS. A interseção é obrigatória.
            val available = output.availableMetadataObjectTypes.mapNotNull { it as? String }.toSet()
            val desired = formats.flatMap { it.toAvMetadataTypes() }.filter { it in available }
            if (desired.isEmpty()) {
                session.commitConfiguration()
                dispatch_async(dispatch_get_main_queue()) {
                    onStatus(BarcodeCameraStatus.Failed("Nenhuma simbologia suportada neste aparelho"))
                }
                return@dispatch_async
            }
            output.metadataObjectTypes = desired

            session.commitConfiguration()
            session.startRunning()

            val torchAvailable = camera.hasTorch
            dispatch_async(dispatch_get_main_queue()) {
                onStatus(BarcodeCameraStatus.Ready(torchAvailable = torchAvailable))
            }
        }
    }

    fun stop() {
        dispatch_async(sessionQueue) {
            setTorchInternal(false)
            session.stopRunning()
        }
    }

    fun setTorch(enabled: Boolean) {
        dispatch_async(sessionQueue) { setTorchInternal(enabled) }
    }

    private fun setTorchInternal(enabled: Boolean) {
        val camera = device ?: return
        if (!camera.hasTorch) return
        if (camera.lockForConfiguration(null)) {
            camera.torchMode = if (enabled) AVCaptureTorchModeOn else AVCaptureTorchModeOff
            camera.unlockForConfiguration()
        }
    }
}

/**
 * Delegate de metadata: normaliza cada objeto lido e entrega a lista **na main thread**.
 *
 * Não há throttle aqui — a política de anti-repetição é do [BarcodeScanDebouncer], em código
 * comum, para que Android e iOS se comportem igual.
 */
private class BarcodeMetadataDelegate(
    private val onResults: (List<ScannedBarcode>) -> Unit,
) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        val results = didOutputMetadataObjects.mapNotNull { raw ->
            val code = raw as? AVMetadataMachineReadableCodeObject ?: return@mapNotNull null
            val format = (code.type as? String)?.avMetadataTypeToBarcodeFormat()
                ?: return@mapNotNull null
            parseBarcode(code.stringValue, format)
        }
        if (results.isEmpty()) return
        dispatch_async(dispatch_get_main_queue()) { onResults(results) }
    }
}
