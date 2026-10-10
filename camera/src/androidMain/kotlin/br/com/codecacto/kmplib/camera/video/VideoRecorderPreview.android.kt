package br.com.codecacto.kmplib.camera.video

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent as CameraXRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.codecacto.kmplib.camera.guided.CameraLens
import br.com.codecacto.kmplib.camera.guided.GuidedCameraStatus
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.VIDEO_CAPTURE_TEMP_DIRECTORY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private const val TAG = "KmpLibVideoRecorder"

/**
 * CameraX: `Preview` + `VideoCapture<Recorder>` — o caso de uso de vídeo oficial do CameraX. O
 * `Recorder` em 16:9 na [VideoRecordingQuality] pedida (com *fallback* para a mais próxima abaixo,
 * depois acima), e o `PreviewView` em `FIT_CENTER` dentro da caixa 9:16.
 *
 * - O arquivo vai para `cacheDir/kmplib_video_capture` (MP4), com
 *   `FileOutputOptions.setDurationLimitMillis(teto)` — o Recorder para sozinho e finaliza com
 *   `ERROR_DURATION_LIMIT_REACHED`, que é **sucesso** (o arquivo está inteiro).
 * - Áudio só com [recordAudio] **e** `RECORD_AUDIO` concedida (o código comum já pediu).
 * - Rotação de destino = a da tela no instante de gravar; frontal sem espelho (o default do
 *   `VideoCapture` é `MIRROR_MODE_OFF`), como na câmera guiada.
 * - `onDispose`: para a gravação em curso (e apaga o arquivo, quando ele finalizar) e solta a câmera.
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val currentOnStatus by rememberUpdatedState(onStatus)

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }
    val resolution = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .build()
    }
    val recorder = remember(quality) {
        val alvo = when (quality) {
            VideoRecordingQuality.SD_480P -> Quality.SD
            VideoRecordingQuality.HD_720P -> Quality.HD
            VideoRecordingQuality.FHD_1080P -> Quality.FHD
        }
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(alvo, FallbackStrategy.lowerQualityOrHigherThan(alvo)))
            .setAspectRatio(AspectRatio.RATIO_16_9)
            .build()
    }
    val videoCapture = remember(recorder) { VideoCapture.withOutput(recorder) }
    var boundProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var boundPreview by remember { mutableStateOf<Preview?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var activeLens by remember { mutableStateOf(lens) }

    LaunchedEffect(Unit) {
        val provider = runCatching {
            withContext(Dispatchers.IO) { ProcessCameraProvider.getInstance(context).get() }
        }.getOrElse { erro ->
            AppLogger.e(TAG, "Falha ao obter o ProcessCameraProvider", erro)
            currentOnStatus(GuidedCameraStatus.Failed(erro.message))
            return@LaunchedEffect
        }
        val disponiveis = buildSet {
            if (runCatching { provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)) add(CameraLens.BACK)
            if (runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)) add(CameraLens.FRONT)
        }
        val ativa = resolveRecorderLens(lens, disponiveis)
        if (ativa == null) {
            AppLogger.w(TAG, "Nenhuma câmera disponível no aparelho.")
            currentOnStatus(GuidedCameraStatus.Unavailable)
            return@LaunchedEffect
        }
        activeLens = ativa
        val seletor = if (ativa == CameraLens.BACK) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        val preview = Preview.Builder()
            .setResolutionSelector(resolution)
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }

        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, seletor, preview, videoCapture)
        }.onSuccess { vinculada ->
            camera = vinculada
            boundProvider = provider
            boundPreview = preview
            currentOnStatus(
                GuidedCameraStatus.Ready(
                    flashAvailable = runCatching { vinculada.cameraInfo.hasFlashUnit() }.getOrDefault(false),
                    availableLenses = disponiveis,
                    activeLens = ativa,
                ),
            )
        }.onFailure { erro ->
            AppLogger.e(TAG, "Falha ao vincular a câmera de vídeo ao ciclo de vida", erro)
            currentOnStatus(GuidedCameraStatus.Failed(erro.message))
        }
    }

    DisposableEffect(handle, videoCapture) {
        val principal = ContextCompat.getMainExecutor(context)
        var gravacao: Recording? = null
        var arquivo: File? = null
        var descartado = false

        handle.start = start@{ onEvent ->
            if (gravacao != null) return@start
            val dir = File(context.cacheDir, VIDEO_CAPTURE_TEMP_DIRECTORY).apply { mkdirs() }
            val destino = File(dir, "rec-${UUID.randomUUID()}.mp4")
            arquivo = destino
            videoCapture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
            val opcoes = FileOutputOptions.Builder(destino)
                .setDurationLimitMillis(maxDurationMillis)
                .build()
            val comAudio = recordAudio &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val lenteDaGravacao = activeLens
            gravacao = try {
                recorder.prepareRecording(context, opcoes)
                    .apply { if (comAudio) comAudioLigado(this) }
                    .start(principal) { evento ->
                        when (evento) {
                            is CameraXRecordEvent.Start -> if (!descartado) onEvent(VideoRecordEvent.Started)
                            is CameraXRecordEvent.Finalize -> {
                                gravacao = null
                                val limite = evento.error == CameraXRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
                                val ok = (!evento.hasError() || limite) && destino.length() > 0L
                                when {
                                    descartado -> destino.delete()
                                    ok -> scope.launch {
                                        val video = withContext(Dispatchers.IO) {
                                            lerGravado(context, destino, lenteDaGravacao, comAudio, limite)
                                        }
                                        onEvent(VideoRecordEvent.Finished(video))
                                    }
                                    else -> {
                                        destino.delete()
                                        AppLogger.w(TAG, "Gravação falhou (código ${evento.error}).")
                                        val falha = when (evento.error) {
                                            CameraXRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE,
                                            CameraXRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED,
                                            -> VideoRecordFailure.NO_SPACE
                                            CameraXRecordEvent.Finalize.ERROR_NO_VALID_DATA -> VideoRecordFailure.NO_DATA
                                            else -> VideoRecordFailure.OTHER
                                        }
                                        onEvent(VideoRecordEvent.Failed(falha, "Finalize ${evento.error}"))
                                    }
                                }
                            }
                            else -> Unit
                        }
                    }
            } catch (e: Exception) {
                destino.delete()
                AppLogger.w(TAG, "Gravação não começou: ${e::class.simpleName}")
                onEvent(VideoRecordEvent.Failed(VideoRecordFailure.OTHER, e::class.simpleName))
                null
            }
        }
        handle.stop = { gravacao?.stop() }

        onDispose {
            handle.start = null
            handle.stop = null
            // Saiu da tela gravando: ninguém pediu esse arquivo. Para e apaga ao finalizar.
            descartado = true
            gravacao?.stop()
            if (gravacao == null) arquivo?.takeIf { it.exists() && it.length() == 0L }?.delete()
            val usos = listOfNotNull(boundPreview, videoCapture)
            runCatching { boundProvider?.unbind(*usos.toTypedArray()) }
            camera = null
            boundProvider = null
            boundPreview = null
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/** `withAudioEnabled` exige `RECORD_AUDIO`, que foi conferida logo antes. */
@SuppressLint("MissingPermission")
private fun comAudioLigado(pendente: androidx.camera.video.PendingRecording) {
    pendente.withAudioEnabled()
}

/** Duração e medida do arquivo pronto (medida já na orientação de exibição). */
private fun lerGravado(
    context: Context,
    arquivo: File,
    lente: CameraLens,
    comAudio: Boolean,
    limite: Boolean,
): RecordedVideo {
    val r = MediaMetadataRetriever()
    var duracao = 0L
    var largura = 0
    var altura = 0
    var temAudio = comAudio
    try {
        r.setDataSource(context, Uri.fromFile(arquivo))
        duracao = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val l = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val a = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotacao = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val deitado = rotacao == 90 || rotacao == 270
        largura = if (deitado) a else l
        altura = if (deitado) l else a
        temAudio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
    } catch (e: Exception) {
        AppLogger.w(TAG, "Metadados da gravação não lidos: ${e::class.simpleName}")
    } finally {
        runCatching { r.release() }
    }
    return RecordedVideo(
        path = arquivo.absolutePath,
        uri = Uri.fromFile(arquivo).toString(),
        durationMillis = duracao,
        sizeBytes = arquivo.length(),
        widthPx = largura,
        heightPx = altura,
        mimeType = "video/mp4",
        lens = lente,
        hasAudio = temAudio,
        reachedMaxDuration = limite,
    )
}

internal actual fun deleteRecordedVideoFile(path: String): Boolean = runCatching {
    val arquivo = File(path)
    !arquivo.exists() || arquivo.delete()
}.getOrDefault(false)
