@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package br.com.codecacto.kmplib.camera.guided

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.ui.components.toPickedImage
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.defaultDeviceWithDeviceType
import platform.AVFoundation.AVCaptureFlashMode
import platform.AVFoundation.AVCaptureFlashModeAuto
import platform.AVFoundation.AVCaptureFlashModeOff
import platform.AVFoundation.AVCaptureFlashModeOn
import platform.AVFoundation.AVCaptureInput
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetPhoto
import platform.AVFoundation.AVCaptureSessionRuntimeErrorNotification
import platform.AVFoundation.AVCaptureVideoOrientation
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.fileDataRepresentation
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIView
import platform.darwin.DISPATCH_QUEUE_PRIORITY_HIGH
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create

private const val TAG = "KmpLibGuidedCamera"

/**
 * AVFoundation: `AVCaptureSession` com preset **`Photo`** (4:3, a resolução cheia do sensor) +
 * `AVCapturePhotoOutput`, e o `AVCaptureVideoPreviewLayer` em **`resizeAspect`** — o preview inteiro,
 * sem corte, na caixa 3:4 que o código comum dimensionou. A tela mostra o enquadramento da foto.
 *
 * - Configurar e ligar a sessão numa fila própria — recomendação explícita da Apple (o
 *   `startRunning` bloqueia).
 * - A foto sai de `AVCapturePhoto.fileDataRepresentation()` e é reduzida pelo ImageIO (o mesmo
 *   codificador do seletor de imagem: em pé, sem EXIF/GPS, teto de medida), numa fila de fundo.
 * - Frontal sem espelhar na foto (`videoMirrored = false` na conexão de foto), como no Android.
 * - Erro de execução da sessão (`AVCaptureSessionRuntimeErrorNotification`) vira estado de falha
 *   com "Tentar novamente", não um preview congelado.
 * - No `onDispose`: sessão parada, entradas e saídas removidas, observador retirado.
 *
 * `Info.plist` do app: `NSCameraUsageDescription`. O simulador não tem câmera: lá a tela mostra
 * "Câmera indisponível" — a validação real é em aparelho.
 */
@Composable
internal actual fun GuidedCameraPreview(
    modifier: Modifier,
    lens: CameraLens,
    flashMode: CameraFlashMode,
    maxDimension: Int,
    jpegQuality: Int,
    handle: GuidedCaptureHandle,
    onStatus: (GuidedCameraStatus) -> Unit,
) {
    val currentOnStatus by rememberUpdatedState(onStatus)
    val currentMaxDimension by rememberUpdatedState(maxDimension)
    val currentJpegQuality by rememberUpdatedState(jpegQuality)
    val controller = remember { GuidedCameraController(lens) { status -> currentOnStatus(status) } }

    LaunchedEffect(controller, flashMode) { controller.setFlashMode(flashMode) }

    DisposableEffect(controller, handle) {
        controller.start()
        handle.capture = { onResult ->
            controller.capture(currentMaxDimension, currentJpegQuality, onResult)
        }
        onDispose {
            handle.capture = null
            controller.stop()
        }
    }

    UIKitView(
        factory = { controller.previewView },
        modifier = modifier,
    )
}

/** `UIView` que hospeda o preview layer e o mantém do tamanho e na orientação da tela. */
internal class GuidedPreviewUIView(session: AVCaptureSession) :
    UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    val previewLayer: AVCaptureVideoPreviewLayer =
        AVCaptureVideoPreviewLayer(session = session).apply {
            // Inteiro, sem corte: é o que alinha a silhueta da tela com a da foto.
            videoGravity = AVLayerVideoGravityResizeAspect
        }

    init {
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        previewLayer.setFrame(bounds)
        previewLayer.connection?.let { conexao ->
            if (conexao.isVideoOrientationSupported()) conexao.videoOrientation = currentVideoOrientation()
        }
    }

    /**
     * A orientação da INTERFACE como orientação de vídeo — os valores das duas enumerações da
     * Apple coincidem de propósito (retrato = 1, de cabeça para baixo = 2, paisagem à direita = 3,
     * paisagem à esquerda = 4). Fora da faixa (sem janela ainda), retrato.
     */
    fun currentVideoOrientation(): AVCaptureVideoOrientation {
        val interfaceOrientation = window?.windowScene?.interfaceOrientation ?: return AVCaptureVideoOrientationPortrait
        return if (interfaceOrientation in 1L..4L) interfaceOrientation else AVCaptureVideoOrientationPortrait
    }
}

private class GuidedCameraController(
    private val requestedLens: CameraLens,
    private val onStatus: (GuidedCameraStatus) -> Unit,
) {
    private val session = AVCaptureSession()
    private val photoOutput = AVCapturePhotoOutput()
    private val sessionQueue = dispatch_queue_create("br.com.codecacto.kmplib.camera.guided.session", null)

    /** Lido e escrito só na [sessionQueue]. */
    private var flashMode: CameraFlashMode = CameraFlashMode.OFF
    private var released = false

    /**
     * Referência FORTE ao delegate do disparo em curso. O `AVCapturePhotoOutput` não garante reter o
     * delegate até o fim (o padrão do UIKit é referência fraca); sem esta, o ARC o liberaria e a foto
     * nunca voltaria — o botão "não faria nada".
     */
    private var photoDelegateInUse: NSObject? = null
    private var runtimeErrorObserver: NSObjectProtocol? = null

    val previewView: GuidedPreviewUIView by lazy { GuidedPreviewUIView(session) }

    fun start() {
        runtimeErrorObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVCaptureSessionRuntimeErrorNotification,
            `object` = session,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            AppLogger.w(TAG, "Sessão de câmera parou com erro de execução.")
            if (!released) onStatus(GuidedCameraStatus.Failed("erro de execução da sessão"))
        }

        dispatch_async(sessionQueue) {
            val traseira = AVCaptureDevice.defaultDeviceWithDeviceType(
                AVCaptureDeviceTypeBuiltInWideAngleCamera,
                AVMediaTypeVideo,
                AVCaptureDevicePositionBack,
            )
            val frontal = AVCaptureDevice.defaultDeviceWithDeviceType(
                AVCaptureDeviceTypeBuiltInWideAngleCamera,
                AVMediaTypeVideo,
                AVCaptureDevicePositionFront,
            )
            val disponiveis = buildSet {
                if (traseira != null) add(CameraLens.BACK)
                if (frontal != null) add(CameraLens.FRONT)
            }
            val ativa = resolveCameraLens(requestedLens, disponiveis)
            val dispositivo = when (ativa) {
                CameraLens.BACK -> traseira
                CameraLens.FRONT -> frontal
                null -> null
            }
            if (ativa == null || dispositivo == null) {
                AppLogger.w(TAG, "Nenhuma câmera disponível (simulador ou aparelho sem câmera).")
                naPrincipal { onStatus(GuidedCameraStatus.Unavailable) }
                return@dispatch_async
            }

            session.beginConfiguration()
            if (session.canSetSessionPreset(AVCaptureSessionPresetPhoto)) {
                session.sessionPreset = AVCaptureSessionPresetPhoto
            }
            val entrada = AVCaptureDeviceInput.deviceInputWithDevice(dispositivo, error = null)
            if (entrada == null || !session.canAddInput(entrada)) {
                session.commitConfiguration()
                naPrincipal { onStatus(GuidedCameraStatus.Failed("entrada de câmera recusada")) }
                return@dispatch_async
            }
            session.addInput(entrada)
            if (!session.canAddOutput(photoOutput)) {
                session.commitConfiguration()
                naPrincipal { onStatus(GuidedCameraStatus.Failed("saída de foto recusada")) }
                return@dispatch_async
            }
            session.addOutput(photoOutput)
            photoOutput.connectionWithMediaType(AVMediaTypeVideo)?.let(::semEspelhar)
            session.commitConfiguration()
            if (released) return@dispatch_async
            session.startRunning()

            val temFlash = supportsFlash(AVCaptureFlashModeOn)
            naPrincipal {
                if (!released) {
                    onStatus(
                        GuidedCameraStatus.Ready(
                            flashAvailable = temFlash,
                            availableLenses = disponiveis,
                            activeLens = ativa,
                        ),
                    )
                }
            }
        }
    }

    fun setFlashMode(mode: CameraFlashMode) {
        dispatch_async(sessionQueue) { flashMode = mode }
    }

    /** Chamado na main thread (o toque); a orientação da tela é lida aqui, antes de trocar de fila. */
    fun capture(maxDimension: Int, jpegQuality: Int, onResult: (GuidedCaptureResult) -> Unit) {
        val orientacao = previewView.currentVideoOrientation()
        dispatch_async(sessionQueue) {
            if (released || !session.running) {
                naPrincipal { onResult(GuidedCaptureResult.Failure("sessão de câmera fora do ar")) }
                return@dispatch_async
            }
            val ajustes = AVCapturePhotoSettings.photoSettings()
            val desejado: AVCaptureFlashMode = when (flashMode) {
                CameraFlashMode.OFF -> AVCaptureFlashModeOff
                CameraFlashMode.AUTO -> AVCaptureFlashModeAuto
                CameraFlashMode.ON -> AVCaptureFlashModeOn
            }
            // Pedir um modo que a câmera não suporta LANÇA (NSInvalidArgumentException) — por isso
            // a conferência, e não só "tem flash".
            ajustes.flashMode = if (supportsFlash(desejado)) desejado else AVCaptureFlashModeOff

            photoOutput.connectionWithMediaType(AVMediaTypeVideo)?.let { conexao ->
                if (conexao.isVideoOrientationSupported()) conexao.videoOrientation = orientacao
                semEspelhar(conexao)
            }

            val delegate = PhotoCaptureDelegate(maxDimension, jpegQuality) { resultado ->
                naPrincipal {
                    photoDelegateInUse = null
                    onResult(resultado)
                }
            }
            photoDelegateInUse = delegate
            photoOutput.capturePhotoWithSettings(ajustes, delegate)
        }
    }

    fun stop() {
        released = true
        runtimeErrorObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        runtimeErrorObserver = null
        dispatch_async(sessionQueue) {
            session.stopRunning()
            session.beginConfiguration()
            session.inputs.filterIsInstance<AVCaptureInput>().forEach { session.removeInput(it) }
            session.outputs.filterIsInstance<AVCaptureOutput>().forEach { session.removeOutput(it) }
            session.commitConfiguration()
        }
    }

    private fun supportsFlash(mode: AVCaptureFlashMode): Boolean =
        photoOutput.supportedFlashModes.any { (it as? NSNumber)?.longValue == mode }

    /** A foto da frontal sai como a pessoa é vista (sem espelho), igual ao Android. */
    private fun semEspelhar(conexao: AVCaptureConnection) {
        if (conexao.isVideoMirroringSupported()) {
            conexao.automaticallyAdjustsVideoMirroring = false
            conexao.videoMirrored = false
        }
    }
}

/** Recebe a foto do `AVCapturePhotoOutput` e a codifica numa fila de fundo. */
private class PhotoCaptureDelegate(
    private val maxDimension: Int,
    private val jpegQuality: Int,
    private val onDone: (GuidedCaptureResult) -> Unit,
) : NSObject(), AVCapturePhotoCaptureDelegateProtocol {

    override fun captureOutput(
        output: AVCapturePhotoOutput,
        didFinishProcessingPhoto: AVCapturePhoto,
        error: NSError?,
    ) {
        if (error != null) {
            AppLogger.w(TAG, "Disparo falhou (código ${error.code}).")
            onDone(GuidedCaptureResult.Failure(error.localizedDescription))
            return
        }
        val dados = didFinishProcessingPhoto.fileDataRepresentation()
        if (dados == null) {
            onDone(GuidedCaptureResult.Failure("foto sem dados"))
            return
        }
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_HIGH.toLong(), 0u)) {
            val foto = dados.toPickedImage(maxDimension, jpegQuality)
            onDone(
                if (foto == null) {
                    GuidedCaptureResult.Failure("foto não pôde ser codificada")
                } else {
                    GuidedCaptureResult.Success(foto)
                },
            )
        }
    }
}

private fun naPrincipal(bloco: () -> Unit) {
    dispatch_async(dispatch_get_main_queue()) { bloco() }
}
