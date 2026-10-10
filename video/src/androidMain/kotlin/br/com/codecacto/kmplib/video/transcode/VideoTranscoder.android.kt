package br.com.codecacto.kmplib.video.transcode

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.VIDEO_PREPARED_TEMP_DIRECTORY
import br.com.codecacto.kmplib.video.KmpLibVideoInternalApi
import br.com.codecacto.kmplib.video.VideoPlayerHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume

private const val TAG = "KmpLibVideoTranscoder"

@OptIn(KmpLibVideoInternalApi::class)
actual fun createVideoTranscoder(): VideoTranscoder = Media3VideoTranscoder { VideoPlayerHolder.getContext() }

/**
 * Media3 **`Transformer`** — a API do Android/Jetpack para editar e transcodificar vídeo no aparelho.
 *
 * O caminho:
 * 1. **Sondar** a fonte (`MediaMetadataRetriever`, fora da main thread): duração, medida e rotação —
 *    para validar o corte e para não AMPLIAR fonte pequena (o `Presentation.createForShortSide`
 *    amplia).
 * 2. **Transformar** na main thread (o `Transformer` exige um `Looper`; o trabalho pesado roda nas
 *    threads dele): `Composition` com tom HDR → SDR por OpenGL, `EditedMediaItem` com corte
 *    (`ClippingConfiguration`) e `setRemoveAudio`, efeitos `Presentation` + `FrameDropEffect`, e o
 *    `DefaultEncoderFactory` pedindo H.264 na taxa do perfil (com *fallback* — aparelho que não aceita
 *    a taxa exata usa a mais próxima, em vez de falhar).
 * 3. **Miniatura** do arquivo pronto (`MediaMetadataRetriever.getScaledFrameAtTime`, API 27+).
 */
@OptIn(UnstableApi::class)
private class Media3VideoTranscoder(
    private val contextProvider: () -> Context?,
) : VideoTranscoder {

    override suspend fun prepare(
        source: VideoTranscodeSource,
        trim: VideoTrim?,
        mute: Boolean,
        profile: VideoTranscodeProfile,
        onProgress: (Float) -> Unit,
    ): VideoTranscodeResult {
        val context = contextProvider()
            ?: return VideoTranscodeResult.Failure(
                VideoTranscodeError.UNKNOWN,
                "kmplib-video: chame initKmpLibVideo(context) antes de usar o VideoTranscoder",
            ).also { AppLogger.e(TAG, it.detail ?: "") }

        val uri = source.toAndroidUri()
        val sonda = withContext(Dispatchers.IO) { sondar(context, uri) }
            ?: return VideoTranscodeResult.Failure(VideoTranscodeError.SOURCE_UNREADABLE, "fonte não abriu")

        val trecho = resolveVideoTrim(trim, sonda.durationMillis)
            ?: return VideoTranscodeResult.Failure(VideoTranscodeError.INVALID_TRIM, "trecho fora do vídeo")

        val dir = File(context.cacheDir, VIDEO_PREPARED_TEMP_DIRECTORY)
        val saida = withContext(Dispatchers.IO) {
            dir.mkdirs()
            File(dir, "prep-${UUID.randomUUID()}.mp4")
        }

        val resultado = try {
            withContext(Dispatchers.Main) { transformar(context, uri, sonda, trecho, mute, profile, saida, onProgress) }
        } catch (e: Throwable) {
            withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) { saida.delete() }
            throw e
        }

        if (resultado !is ExportOutcome.Done) {
            withContext(Dispatchers.IO) { saida.delete() }
            return (resultado as ExportOutcome.Failed).failure
        }

        return withContext(Dispatchers.IO) {
            val miniatura = if (profile.thumbnailMaxDimension > 0) miniatura(saida, profile.thumbnailMaxDimension) else null
            val r = resultado.result
            val medida = sondar(context, Uri.fromFile(saida))
            onMain(onProgress, 1f)
            VideoTranscodeResult.Success(
                PreparedVideo(
                    path = saida.absolutePath,
                    uri = Uri.fromFile(saida).toString(),
                    sizeBytes = saida.length(),
                    durationMillis = medida?.durationMillis?.takeIf { it > 0 } ?: r.durationMs.coerceAtLeast(0L),
                    widthPx = medida?.displayWidth ?: r.width,
                    heightPx = medida?.displayHeight ?: r.height,
                    hasAudio = !mute && r.audioMimeType != null,
                    thumbnailJpeg = miniatura?.bytes,
                    thumbnailWidthPx = miniatura?.width ?: 0,
                    thumbnailHeightPx = miniatura?.height ?: 0,
                ),
            )
        }
    }

    private suspend fun transformar(
        context: Context,
        uri: Uri,
        sonda: Sonda,
        trecho: ResolvedVideoTrim,
        mute: Boolean,
        profile: VideoTranscodeProfile,
        saida: File,
        onProgress: (Float) -> Unit,
    ): ExportOutcome = coroutineScope {
        val item = MediaItem.Builder()
            .setUri(uri)
            .apply {
                val corta = trecho.startMillis > 0L || (trecho.endMillis in 1 until sonda.durationMillis)
                if (corta) {
                    setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(trecho.startMillis)
                            .apply { if (trecho.endMillis > 0L) setEndPositionMs(trecho.endMillis) }
                            .build(),
                    )
                }
            }
            .build()

        // Só reduz: o `Presentation` amplia fonte menor que o alvo, e ampliar só gasta bits.
        val efeitos = buildList {
            if (minOf(sonda.displayWidth, sonda.displayHeight) > profile.maxShortSidePx) {
                add(Presentation.createForShortSide(profile.maxShortSidePx))
            }
            add(FrameDropEffect.createDefaultFrameDropEffect(profile.frameRate.toFloat()))
        }
        val editado = EditedMediaItem.Builder(item)
            .setRemoveAudio(mute)
            .setEffects(Effects(emptyList(), efeitos))
            .build()
        val composicao = Composition.Builder(EditedMediaItemSequence.Builder(editado).build())
            // H.264 não carrega HDR10/HLG direito: vídeo HDR da câmera sai "lavado" sem o tom.
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()

        val codificadores = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(profile.videoBitrate)
                    .setiFrameIntervalSeconds(profile.keyFrameIntervalSeconds)
                    .build(),
            )
            .setRequestedAudioEncoderSettings(
                AudioEncoderSettings.Builder().setBitrate(profile.audioBitrate).build(),
            )
            .setEnableFallback(true)
            .build()

        var transformer: Transformer? = null
        val progresso = launch {
            val holder = ProgressHolder()
            while (isActive) {
                delay(PROGRESS_INTERVAL_MILLIS)
                val t = transformer ?: continue
                if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress((holder.progress / 100f).coerceIn(0f, 0.99f))
                }
            }
        }

        try {
            suspendCancellableCoroutine { cont ->
                val t = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(codificadores)
                    .addListener(
                        object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (cont.isActive) cont.resume(ExportOutcome.Done(exportResult))
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException,
                            ) {
                                AppLogger.w(TAG, "Transformer falhou (${exportException.errorCodeName})")
                                if (cont.isActive) cont.resume(ExportOutcome.Failed(exportException.paraFalha(saida)))
                            }
                        },
                    )
                    .build()
                transformer = t
                cont.invokeOnCancellation { runCatching { t.cancel() } }
                t.start(composicao, saida.absolutePath)
            }
        } finally {
            progresso.cancel()
        }
    }

    /** O que a fonte diz de si — `null` se não abre. Medida já na orientação de exibição. */
    private fun sondar(context: Context, uri: Uri): Sonda? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            if (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") return null
            val largura = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val altura = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotacao = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val deitado = rotacao == 90 || rotacao == 270
            Sonda(
                durationMillis = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                displayWidth = if (deitado) altura else largura,
                displayHeight = if (deitado) largura else altura,
            )
        } catch (e: Exception) {
            // Só o tipo: a mensagem pode trazer a URI/caminho do arquivo da pessoa.
            AppLogger.w(TAG, "Fonte de vídeo não abriu: ${e::class.simpleName}")
            null
        } finally {
            runCatching { r.release() }
        }
    }

    /** O primeiro quadro do arquivo pronto, reduzido e em JPEG (o retriever já aplica a rotação). */
    private fun miniatura(arquivo: File, maxDimension: Int): Miniatura? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(arquivo.absolutePath)
            val quadro: Bitmap = (
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    r.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxDimension, maxDimension)
                } else {
                    r.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { reduzir(it, maxDimension) }
                }
                ) ?: return null
            val saida = ByteArrayOutputStream()
            quadro.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_JPEG_QUALITY, saida)
            Miniatura(saida.toByteArray(), quadro.width, quadro.height).also { quadro.recycle() }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Miniatura não saiu: ${e::class.simpleName}")
            null
        } finally {
            runCatching { r.release() }
        }
    }

    private fun reduzir(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maior = maxOf(bitmap.width, bitmap.height)
        if (maior <= maxDimension) return bitmap
        val escala = maxDimension.toFloat() / maior
        val reduzida = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * escala).toInt().coerceAtLeast(1),
            (bitmap.height * escala).toInt().coerceAtLeast(1),
            true,
        )
        if (reduzida !== bitmap) bitmap.recycle()
        return reduzida
    }

    private suspend fun onMain(onProgress: (Float) -> Unit, value: Float) {
        withContext(Dispatchers.Main) { onProgress(value) }
    }
}

private class Sonda(val durationMillis: Long, val displayWidth: Int, val displayHeight: Int)

private class Miniatura(val bytes: ByteArray, val width: Int, val height: Int)

private sealed interface ExportOutcome {
    class Done(val result: ExportResult) : ExportOutcome
    class Failed(val failure: VideoTranscodeResult.Failure) : ExportOutcome
}

@OptIn(UnstableApi::class)
private fun ExportException.paraFalha(saida: File): VideoTranscodeResult.Failure {
    val semEspaco = generateSequence<Throwable>(this) { it.cause }
        .any { it is IOException && it.message?.contains("ENOSPC") == true } ||
        (saida.parentFile?.usableSpace ?: Long.MAX_VALUE) < LOW_SPACE_BYTES
    val tipo = when {
        semEspaco -> VideoTranscodeError.NO_SPACE
        errorCode == ExportException.ERROR_CODE_IO_FILE_NOT_FOUND ||
            errorCode == ExportException.ERROR_CODE_IO_NO_PERMISSION ||
            errorCode == ExportException.ERROR_CODE_IO_UNSPECIFIED -> VideoTranscodeError.SOURCE_UNREADABLE
        errorCode == ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
            errorCode == ExportException.ERROR_CODE_DECODER_INIT_FAILED ||
            errorCode == ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED -> VideoTranscodeError.UNSUPPORTED_FORMAT
        errorCode == ExportException.ERROR_CODE_ENCODER_INIT_FAILED ||
            errorCode == ExportException.ERROR_CODE_ENCODING_FAILED ||
            errorCode == ExportException.ERROR_CODE_MUXING_FAILED -> VideoTranscodeError.ENCODER_FAILED
        else -> VideoTranscodeError.UNKNOWN
    }
    return VideoTranscodeResult.Failure(tipo, errorCodeName)
}

/** `file://`/`content://` como URI; caminho cru vira `file://`. */
private fun VideoTranscodeSource.toAndroidUri(): Uri =
    if (isUri) Uri.parse(value) else Uri.fromFile(File(value))

internal actual fun deleteLocalVideoFile(path: String): Boolean = runCatching {
    val arquivo = File(path)
    !arquivo.exists() || arquivo.delete()
}.getOrDefault(false)

private const val PROGRESS_INTERVAL_MILLIS = 250L
private const val THUMBNAIL_JPEG_QUALITY = 82
/** Abaixo disto, falha de escrita é tratada como falta de espaço. */
private const val LOW_SPACE_BYTES = 20L * 1024 * 1024
