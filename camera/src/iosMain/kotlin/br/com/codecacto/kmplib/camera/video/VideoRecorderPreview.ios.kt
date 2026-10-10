@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package br.com.codecacto.kmplib.camera.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import br.com.codecacto.kmplib.camera.guided.CameraLens
import br.com.codecacto.kmplib.camera.guided.GuidedCameraStatus
import br.com.codecacto.kmplib.camera.guided.GuidedPreviewUIView
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.VIDEO_CAPTURE_TEMP_DIRECTORY
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureFileOutput
import platform.AVFoundation.AVCaptureFileOutputRecordingDelegateProtocol
import platform.AVFoundation.AVCaptureInput
import platform.AVFoundation.AVCaptureMovieFileOutput
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset1280x720
import platform.AVFoundation.AVCaptureSessionPreset1920x1080
import platform.AVFoundation.AVCaptureSessionPreset640x480
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureSessionRuntimeErrorNotification
import platform.AVFoundation.AVCaptureVideoOrientation
import platform.AVFoundation.AVErrorDiskFull
import platform.AVFoundation.AVErrorMaximumDurationReached
import platform.AVFoundation.AVErrorMaximumFileSizeReached
import platform.AVFoundation.AVErrorRecordingSuccessfullyFinishedKey
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.defaultDeviceWithDeviceType
import platform.AVFoundation.hasTorch
import platform.AVFoundation.duration
import platform.AVFoundation.naturalSize
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.tracksWithMediaType
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import kotlin.math.abs

private const val TAG = "KmpLibVideoRecorder"

/**
 * AVFoundation: `AVCaptureSession` com o preset da [VideoRecordingQuality] (16:9) +
 * `AVCaptureMovieFileOutput` com **`maxRecordedDuration`** — o teto que a Apple aplica sozinha: a
 * gravação termina com `AVErrorMaximumDurationReached` e `AVErrorRecordingSuccessfullyFinishedKey =
 * true`, que é **sucesso** (o arquivo está inteiro). Microfone como segunda entrada com [recordAudio].
 *
 * - Preview pelo mesmo `GuidedPreviewUIView` da câmera guiada (`resizeAspect`, inteiro, sem corte).
 * - Configurar e ligar a sessão numa fila própria (o `startRunning` bloqueia).
 * - Orientação do vídeo = a da interface no toque; frontal sem espelho (`videoMirrored = false`).
 * - O arquivo vai para `NSTemporaryDirectory()/kmplib_video_capture` (`.mov`, `video/quicktime`).
 * - O delegate é retido com referência FORTE enquanto grava (o AVFoundation não o retém).
 * - `onDispose`: para a gravação (o arquivo é apagado ao finalizar) e desmonta a sessão.
 *
 * `Info.plist`: `NSCameraUsageDescription` (+ `NSMicrophoneUsageDescription`). Simulador sem câmera.
 */
@Composable
internal actual fun VideoRecorderPreview(
    modifier: Modifier,
    lens: CameraLens,
    recordAudio: Boolean,
    quality: VideoRecordingQuality,
    maxDurationMillis: Long,
    handle: VideoRecordHandle,
    onStatus: (GuidedCameraStatus) -> Unit,
) {
    val currentOnStatus by rememberUpdatedState(onStatus)
    val controller = remember {
        VideoRecorderController(lens, recordAudio, quality, maxDurationMillis) { status -> currentOnStatus(status) }
    }

    DisposableEffect(controller, handle) {
        controller.start()
        handle.start = { onEvent -> controller.startRecording(onEvent) }
        handle.stop = { controller.stopRecording() }
        onDispose {
            handle.start = null
            handle.stop = null
            controller.stop()
        }
    }

    UIKitView(factory = { controller.previewView }, modifier = modifier)
}

private class VideoRecorderController(
    private val requestedLens: CameraLens,
    private val recordAudio: Boolean,
    private val quality: VideoRecordingQuality,
    private val maxDurationMillis: Long,
    private val onStatus: (GuidedCameraStatus) -> Unit,
) {
    private val session = AVCaptureSession()
    private val movieOutput = AVCaptureMovieFileOutput()
    private val sessionQueue = dispatch_queue_create("br.com.codecacto.kmplib.camera.video.session", null)

    /** Lidos/escritos na main thread. */
    private var released = false
    private var activeLens: CameraLens = requestedLens
    private var delegateInUse: RecordingDelegate? = null
    private var runtimeErrorObserver: NSObjectProtocol? = null

    val previewView: GuidedPreviewUIView by lazy { GuidedPreviewUIView(session) }

    fun start() {
        runtimeErrorObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVCaptureSessionRuntimeErrorNotification,
            `object` = session,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            AppLogger.w(TAG, "Sessão de vídeo parou com erro de execução.")
            if (!released) onStatus(GuidedCameraStatus.Failed("erro de execução da sessão"))
        }

        dispatch_async(sessionQueue) {
            val traseira = AVCaptureDevice.defaultDeviceWithDeviceType(
                AVCaptureDeviceTypeBuiltInWideAngleCamera, AVMediaTypeVideo, AVCaptureDevicePositionBack,
            )
            val frontal = AVCaptureDevice.defaultDeviceWithDeviceType(
                AVCaptureDeviceTypeBuiltInWideAngleCamera, AVMediaTypeVideo, AVCaptureDevicePositionFront,
            )
            val disponiveis = buildSet {
                if (traseira != null) add(CameraLens.BACK)
                if (frontal != null) add(CameraLens.FRONT)
            }
            val ativa = resolveRecorderLens(requestedLens, disponiveis)
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
            val preset = when (quality) {
                VideoRecordingQuality.SD_480P -> AVCaptureSessionPreset640x480
                VideoRecordingQuality.HD_720P -> AVCaptureSessionPreset1280x720
                VideoRecordingQuality.FHD_1080P -> AVCaptureSessionPreset1920x1080
            }
            session.sessionPreset = if (session.canSetSessionPreset(preset)) preset else AVCaptureSessionPresetHigh

            val entrada = AVCaptureDeviceInput.deviceInputWithDevice(dispositivo, error = null)
            if (entrada == null || !session.canAddInput(entrada)) {
                session.commitConfiguration()
                naPrincipal { onStatus(GuidedCameraStatus.Failed("entrada de câmera recusada")) }
                return@dispatch_async
            }
            session.addInput(entrada)

            if (recordAudio) {
                val mic = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeAudio)
                val entradaDeAudio = mic?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, error = null) }
                if (entradaDeAudio != null && session.canAddInput(entradaDeAudio)) {
                    session.addInput(entradaDeAudio)
                } else {
                    AppLogger.w(TAG, "Microfone indisponível: o vídeo sai sem som.")
                }
            }

            if (!session.canAddOutput(movieOutput)) {
                session.commitConfiguration()
                naPrincipal { onStatus(GuidedCameraStatus.Failed("saída de vídeo recusada")) }
                return@dispatch_async
            }
            session.addOutput(movieOutput)
            movieOutput.maxRecordedDuration = CMTimeMakeWithSeconds(maxDurationMillis / 1000.0, TIMESCALE)
            session.commitConfiguration()
            if (released) return@dispatch_async
            session.startRunning()

            naPrincipal {
                if (!released) {
                    activeLens = ativa
                    onStatus(
                        GuidedCameraStatus.Ready(
                            flashAvailable = dispositivo.hasTorch,
                            availableLenses = disponiveis,
                            activeLens = ativa,
                        ),
                    )
                }
            }
        }
    }

    /** Main thread (o toque): a orientação é lida aqui, antes de trocar de fila. */
    fun startRecording(onEvent: (VideoRecordEvent) -> Unit) {
        if (delegateInUse != null) return
        val orientacao: AVCaptureVideoOrientation = previewView.currentVideoOrientation()
        val lente = activeLens
        val dir = NSTemporaryDirectory().trimEnd('/') + "/" + VIDEO_CAPTURE_TEMP_DIRECTORY
        NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        val caminho = "$dir/rec-${NSUUID().UUIDString}.mov"
        val url = NSURL.fileURLWithPath(caminho)

        val delegate = RecordingDelegate(
            onStarted = { naPrincipal { if (!released) onEvent(VideoRecordEvent.Started) } },
            onFinished = { erro ->
                val resultado = resultadoDaGravacao(caminho, erro, lente)
                naPrincipal {
                    delegateInUse = null
                    when {
                        released -> apagar(caminho)
                        resultado is VideoRecordEvent.Failed -> {
                            apagar(caminho)
                            onEvent(resultado)
                        }
                        else -> onEvent(resultado)
                    }
                }
            },
        )
        delegateInUse = delegate

        dispatch_async(sessionQueue) {
            if (released || !session.running) {
                naPrincipal {
                    delegateInUse = null
                    onEvent(VideoRecordEvent.Failed(VideoRecordFailure.OTHER, "sessão de câmera fora do ar"))
                }
                return@dispatch_async
            }
            movieOutput.connectionWithMediaType(AVMediaTypeVideo)?.let { conexao ->
                if (conexao.isVideoOrientationSupported()) conexao.videoOrientation = orientacao
                semEspelhar(conexao)
            }
            movieOutput.startRecordingToOutputFileURL(url, recordingDelegate = delegate)
        }
    }

    fun stopRecording() {
        dispatch_async(sessionQueue) {
            if (movieOutput.isRecording()) movieOutput.stopRecording()
        }
    }

    fun stop() {
        released = true
        runtimeErrorObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        runtimeErrorObserver = null
        dispatch_async(sessionQueue) {
            // Gravando ao sair: para; o delegate apaga o arquivo (released = true).
            if (movieOutput.isRecording()) movieOutput.stopRecording()
            session.stopRunning()
            session.beginConfiguration()
            session.inputs.filterIsInstance<AVCaptureInput>().forEach { session.removeInput(it) }
            session.outputs.filterIsInstance<AVCaptureOutput>().forEach { session.removeOutput(it) }
            session.commitConfiguration()
        }
    }

    private fun semEspelhar(conexao: AVCaptureConnection) {
        if (conexao.isVideoMirroringSupported()) {
            conexao.automaticallyAdjustsVideoMirroring = false
            conexao.videoMirrored = false
        }
    }
}

/**
 * Traduz o fim da gravação. `error` com `AVErrorRecordingSuccessfullyFinishedKey = true` é sucesso
 * (é assim que o teto chega); disco cheio/teto de tamanho viram [VideoRecordFailure.NO_SPACE].
 */
private fun resultadoDaGravacao(caminho: String, erro: NSError?, lente: CameraLens): VideoRecordEvent {
    val terminouBem = erro == null ||
        (erro.userInfo[AVErrorRecordingSuccessfullyFinishedKey] as? NSNumber)?.boolValue == true
    if (!terminouBem && erro != null) {
        AppLogger.w(TAG, "Gravação falhou (${erro.domain} ${erro.code}).")
        val falha = when (erro.code) {
            AVErrorDiskFull, AVErrorMaximumFileSizeReached -> VideoRecordFailure.NO_SPACE
            else -> VideoRecordFailure.OTHER
        }
        return VideoRecordEvent.Failed(falha, "${erro.domain} ${erro.code}")
    }
    val tamanho = (NSFileManager.defaultManager.attributesOfItemAtPath(caminho, error = null)?.get(NSFileSize) as? NSNumber)
        ?.longLongValue ?: 0L
    if (tamanho <= 0L) return VideoRecordEvent.Failed(VideoRecordFailure.NO_DATA, "arquivo vazio")

    // Propriedades síncronas: arquivo local, recém-fechado — nada a buscar (mesma decisão do
    // seletor de vídeo do `kmplib-ui`).
    val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(caminho), options = null)
    val segundos = CMTimeGetSeconds(asset.duration)
    val faixa = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack
    val (largura, altura) = faixa?.let { f ->
        val (l, a) = f.naturalSize.useContents { width.toInt() to height.toInt() }
        val deitado = f.preferredTransform.useContents { this.a == 0.0 && abs(b) == 1.0 }
        if (deitado) a to l else l to a
    } ?: (0 to 0)
    val temAudio = asset.tracksWithMediaType(AVMediaTypeAudio).isNotEmpty()
    return VideoRecordEvent.Finished(
        RecordedVideo(
            path = caminho,
            uri = NSURL.fileURLWithPath(caminho).absoluteString ?: "file://$caminho",
            durationMillis = if (segundos.isNaN() || segundos < 0.0) 0L else (segundos * 1000.0).toLong(),
            sizeBytes = tamanho,
            widthPx = abs(largura),
            heightPx = abs(altura),
            mimeType = "video/quicktime",
            lens = lente,
            hasAudio = temAudio,
            reachedMaxDuration = erro?.code == AVErrorMaximumDurationReached,
        ),
    )
}

/** Recebe os eventos do `AVCaptureMovieFileOutput` (fila interna do AVFoundation). */
private class RecordingDelegate(
    private val onStarted: () -> Unit,
    private val onFinished: (NSError?) -> Unit,
) : NSObject(), AVCaptureFileOutputRecordingDelegateProtocol {

    override fun captureOutput(
        output: AVCaptureFileOutput,
        didStartRecordingToOutputFileAtURL: NSURL,
        fromConnections: List<*>,
    ) {
        onStarted()
    }

    override fun captureOutput(
        output: AVCaptureFileOutput,
        didFinishRecordingToOutputFileAtURL: NSURL,
        fromConnections: List<*>,
        error: NSError?,
    ) {
        onFinished(error)
    }
}

private fun apagar(caminho: String) {
    deleteRecordedVideoFile(caminho)
}

internal actual fun deleteRecordedVideoFile(path: String): Boolean = runCatching {
    val fm = NSFileManager.defaultManager
    !fm.fileExistsAtPath(path) || fm.removeItemAtPath(path, error = null)
}.getOrDefault(false)

private fun naPrincipal(bloco: () -> Unit) {
    dispatch_async(dispatch_get_main_queue()) { bloco() }
}

private const val TIMESCALE = 600
