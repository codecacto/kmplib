@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package br.com.codecacto.kmplib.video.transcode

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.VIDEO_PREPARED_TEMP_DIRECTORY
import kotlinx.cinterop.CValue
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVLinearPCMBitDepthKey
import platform.AVFAudio.AVLinearPCMIsBigEndianKey
import platform.AVFAudio.AVLinearPCMIsFloatKey
import platform.AVFAudio.AVLinearPCMIsNonInterleaved
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVAssetReader
import platform.AVFoundation.AVAssetReaderStatusFailed
import platform.AVFoundation.AVAssetReaderTrackOutput
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVAssetWriter
import platform.AVFoundation.AVAssetWriterInput
import platform.AVFoundation.AVAssetWriterStatusCompleted
import platform.AVFoundation.AVErrorDiskFull
import platform.AVFoundation.AVErrorDecoderNotFound
import platform.AVFoundation.AVErrorEncoderNotFound
import platform.AVFoundation.AVErrorFileFormatNotRecognized
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.AVURLAssetPreferPreciseDurationAndTimingKey
import platform.AVFoundation.AVVideoAverageBitRateKey
import platform.AVFoundation.AVVideoCodecKey
import platform.AVFoundation.AVVideoCodecTypeH264
import platform.AVFoundation.AVVideoCompressionPropertiesKey
import platform.AVFoundation.AVVideoExpectedSourceFrameRateKey
import platform.AVFoundation.AVVideoHeightKey
import platform.AVFoundation.AVVideoMaxKeyFrameIntervalDurationKey
import platform.AVFoundation.AVVideoProfileLevelH264HighAutoLevel
import platform.AVFoundation.AVVideoProfileLevelKey
import platform.AVFoundation.AVVideoScalingModeKey
import platform.AVFoundation.AVVideoScalingModeResizeAspect
import platform.AVFoundation.AVVideoWidthKey
import platform.AVFoundation.transform
import platform.AVFoundation.duration
import platform.AVFoundation.naturalSize
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.tracksWithMediaType
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMSampleBufferGetPresentationTimeStamp
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.CMTimeRangeMake
import platform.CoreMedia.kCMTimeZero
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSCocoaErrorDomain
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileWriteOutOfSpaceError
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.numberWithUnsignedInt
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import platform.posix.memcpy
import kotlin.concurrent.AtomicInt
import kotlin.coroutines.resume
import kotlin.math.abs

private const val TAG = "KmpLibVideoTranscoder"

actual fun createVideoTranscoder(): VideoTranscoder = AvFoundationVideoTranscoder()

/**
 * AVFoundation: **`AVAssetReader` → `AVAssetWriter`**, o caminho que a Apple indica quando as
 * configurações de compressão importam (taxa de bits, intervalo de quadro-chave, perfil H.264). O
 * `AVAssetExportSession` só aceita presets, e o de 1280×720 sai com 5–8 Mbps.
 *
 * - **Vídeo:** leitura descomprimida (YUV 4:2:0 bi-planar, o formato nativo do decodificador),
 *   escrita em H.264 High com `AVVideoAverageBitRateKey`, quadro-chave a cada
 *   `keyFrameIntervalSeconds` e redução de medida pelo próprio codificador
 *   (`AVVideoScalingModeResizeAspect`). A medida é calculada na orientação da FAIXA, e a
 *   `preferredTransform` vai para a entrada do escritor — o arquivo continua em pé, como o original.
 * - **30 fps:** descarte de quadro na leitura ([shouldKeepVideoFrame]).
 * - **Corte:** `AVAssetReader.timeRange` + `startSessionAtSourceTime` no início do trecho (o
 *   arquivo começa em zero).
 * - **Áudio:** sem [VideoTranscoder.prepare] `mute`, PCM 44,1 kHz estéreo → AAC.
 * - O laço de cada faixa roda numa fila serial própria (`requestMediaDataWhenReadyOnQueue`), como no
 *   exemplo da Apple; quando as duas terminam, `finishWriting`.
 */
private class AvFoundationVideoTranscoder : VideoTranscoder {

    override suspend fun prepare(
        source: VideoTranscodeSource,
        trim: VideoTrim?,
        mute: Boolean,
        profile: VideoTranscodeProfile,
        onProgress: (Float) -> Unit,
    ): VideoTranscodeResult = withContext(Dispatchers.IO) {
        val url = source.toNsUrl()
            ?: return@withContext VideoTranscodeResult.Failure(VideoTranscodeError.SOURCE_UNREADABLE, "referência inválida")
        val asset = AVURLAsset(uRL = url, options = mapOf<Any?, Any?>(AVURLAssetPreferPreciseDurationAndTimingKey to true))

        // Propriedades síncronas do asset: arquivo LOCAL, já no disco — não há o que buscar (mesma
        // decisão do seletor de vídeo do `kmplib-ui`; as `load(_:)` assíncronas são Swift-only).
        val faixaDeVideo = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack
            ?: return@withContext VideoTranscodeResult.Failure(VideoTranscodeError.SOURCE_UNREADABLE, "sem faixa de vídeo")
        val faixaDeAudio = if (mute) null else asset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack

        val segundos = CMTimeGetSeconds(asset.duration)
        val duracaoFonte = if (segundos.isNaN() || segundos <= 0.0) 0L else (segundos * 1000.0).toLong()
        val trecho = resolveVideoTrim(trim, duracaoFonte)
            ?: return@withContext VideoTranscodeResult.Failure(VideoTranscodeError.INVALID_TRIM, "trecho fora do vídeo")

        val (larguraFaixa, alturaFaixa) = faixaDeVideo.naturalSize.useContents { width.toInt() to height.toInt() }
        val alvo = targetVideoDimensions(abs(larguraFaixa), abs(alturaFaixa), profile.maxShortSidePx)
            ?: return@withContext VideoTranscodeResult.Failure(VideoTranscodeError.UNSUPPORTED_FORMAT, "medida inválida")
        val transformacao = faixaDeVideo.preferredTransform
        val deitadoNoArquivo = transformacao.useContents { a == 0.0 && abs(b) == 1.0 }

        val dir = NSTemporaryDirectory().trimEnd('/') + "/" + VIDEO_PREPARED_TEMP_DIRECTORY
        NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        val caminho = "$dir/prep-${NSUUID().UUIDString}.mp4"
        val destino = NSURL.fileURLWithPath(caminho)

        val resultado = try {
            transcodificar(
            asset = asset,
            faixaDeVideo = faixaDeVideo,
            faixaDeAudio = faixaDeAudio,
            trecho = trecho,
            alvo = alvo,
            transformacao = transformacao,
            profile = profile,
            destino = destino,
            onProgress = onProgress,
            )
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            apagar(caminho)
            throw e
        }
        if (resultado != null) {
            apagar(caminho)
            return@withContext resultado
        }

        val miniatura = if (profile.thumbnailMaxDimension > 0) miniatura(destino, profile.thumbnailMaxDimension) else null
        val pronto = AVURLAsset(uRL = destino, options = null)
        val segundosProntos = CMTimeGetSeconds(pronto.duration)
        val duracao = if (segundosProntos.isNaN() || segundosProntos <= 0.0) trecho.durationMillis else (segundosProntos * 1000.0).toLong()
        dispatch_async(dispatch_get_main_queue()) { onProgress(1f) }
        VideoTranscodeResult.Success(
            PreparedVideo(
                path = caminho,
                uri = destino.absoluteString ?: "file://$caminho",
                sizeBytes = tamanhoDe(caminho),
                durationMillis = duracao,
                widthPx = if (deitadoNoArquivo) alvo.heightPx else alvo.widthPx,
                heightPx = if (deitadoNoArquivo) alvo.widthPx else alvo.heightPx,
                hasAudio = faixaDeAudio != null,
                thumbnailJpeg = miniatura?.bytes,
                thumbnailWidthPx = miniatura?.width ?: 0,
                thumbnailHeightPx = miniatura?.height ?: 0,
            ),
        )
    }

    /** Lê e escreve. `null` = deu certo; senão, a falha. Cancelável (apaga o parcial no chamador). */
    private suspend fun transcodificar(
        asset: AVURLAsset,
        faixaDeVideo: AVAssetTrack,
        faixaDeAudio: AVAssetTrack?,
        trecho: ResolvedVideoTrim,
        alvo: VideoDimensions,
        transformacao: CValue<platform.CoreGraphics.CGAffineTransform>,
        profile: VideoTranscodeProfile,
        destino: NSURL,
        onProgress: (Float) -> Unit,
    ): VideoTranscodeResult.Failure? {
        val leitor = AVAssetReader.assetReaderWithAsset(asset = asset, error = null)
            ?: return VideoTranscodeResult.Failure(VideoTranscodeError.SOURCE_UNREADABLE, "AVAssetReader não abriu")
        val inicio = CMTimeMakeWithSeconds(trecho.startMillis / 1000.0, TIMESCALE)
        if (trecho.endMillis > trecho.startMillis) {
            leitor.timeRange = CMTimeRangeMake(inicio, CMTimeMakeWithSeconds(trecho.durationMillis / 1000.0, TIMESCALE))
        }

        val chavePixel = CFBridgingRelease(CFRetain(kCVPixelBufferPixelFormatTypeKey))
        val saidaDeVideo = AVAssetReaderTrackOutput(
            track = faixaDeVideo,
            outputSettings = mapOf<Any?, Any?>(
                chavePixel to NSNumber.numberWithUnsignedInt(kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange),
            ),
        ).apply { alwaysCopiesSampleData = false }
        if (!leitor.canAddOutput(saidaDeVideo)) {
            return VideoTranscodeResult.Failure(VideoTranscodeError.UNSUPPORTED_FORMAT, "saída de vídeo recusada")
        }
        leitor.addOutput(saidaDeVideo)

        val saidaDeAudio = faixaDeAudio?.let { faixa ->
            AVAssetReaderTrackOutput(
                track = faixa,
                outputSettings = mapOf<Any?, Any?>(
                    AVFormatIDKey to NSNumber.numberWithUnsignedInt(kAudioFormatLinearPCM),
                    AVSampleRateKey to AUDIO_SAMPLE_RATE,
                    AVNumberOfChannelsKey to AUDIO_CHANNELS,
                    AVLinearPCMBitDepthKey to 16,
                    AVLinearPCMIsFloatKey to false,
                    AVLinearPCMIsBigEndianKey to false,
                    AVLinearPCMIsNonInterleaved to false,
                ),
            ).takeIf { leitor.canAddOutput(it) }?.also { leitor.addOutput(it) }
        }

        val escritor = AVAssetWriter.assetWriterWithURL(outputURL = destino, fileType = AVFileTypeMPEG4, error = null)
            ?: return VideoTranscodeResult.Failure(VideoTranscodeError.ENCODER_FAILED, "AVAssetWriter não abriu")
        // `moov` no início: a prévia e o player começam a tocar antes de ler o arquivo inteiro.
        escritor.shouldOptimizeForNetworkUse = true

        val entradaDeVideo = AVAssetWriterInput(
            mediaType = AVMediaTypeVideo,
            outputSettings = mapOf<Any?, Any?>(
                AVVideoCodecKey to AVVideoCodecTypeH264,
                AVVideoWidthKey to alvo.widthPx,
                AVVideoHeightKey to alvo.heightPx,
                AVVideoScalingModeKey to AVVideoScalingModeResizeAspect,
                AVVideoCompressionPropertiesKey to mapOf<Any?, Any?>(
                    AVVideoAverageBitRateKey to profile.videoBitrate,
                    AVVideoMaxKeyFrameIntervalDurationKey to profile.keyFrameIntervalSeconds.toDouble(),
                    AVVideoExpectedSourceFrameRateKey to profile.frameRate,
                    AVVideoProfileLevelKey to AVVideoProfileLevelH264HighAutoLevel,
                ),
            ),
        ).apply {
            expectsMediaDataInRealTime = false
            transform = transformacao
        }
        if (!escritor.canAddInput(entradaDeVideo)) {
            return VideoTranscodeResult.Failure(VideoTranscodeError.ENCODER_FAILED, "entrada de vídeo recusada")
        }
        escritor.addInput(entradaDeVideo)

        val entradaDeAudio = saidaDeAudio?.let {
            AVAssetWriterInput(
                mediaType = AVMediaTypeAudio,
                outputSettings = mapOf<Any?, Any?>(
                    AVFormatIDKey to NSNumber.numberWithUnsignedInt(kAudioFormatMPEG4AAC),
                    AVSampleRateKey to AUDIO_SAMPLE_RATE,
                    AVNumberOfChannelsKey to AUDIO_CHANNELS,
                    AVEncoderBitRateKey to profile.audioBitrate,
                ),
            ).apply { expectsMediaDataInRealTime = false }
                .takeIf { escritor.canAddInput(it) }
                ?.also { escritor.addInput(it) }
        }

        if (!leitor.startReading()) {
            return falhaDe(leitor.error, VideoTranscodeError.SOURCE_UNREADABLE)
        }
        if (!escritor.startWriting()) {
            leitor.cancelReading()
            return falhaDe(escritor.error, VideoTranscodeError.ENCODER_FAILED)
        }
        escritor.startSessionAtSourceTime(inicio)

        val inicioMicros = trecho.startMillis * 1000L
        val duracaoMicros = (trecho.durationMillis * 1000L).coerceAtLeast(1L)

        return suspendCancellableCoroutine { cont ->
            val cancelado = AtomicInt(0)
            val pendentes = AtomicInt(if (entradaDeAudio != null) 2 else 1)
            var ultimoProgresso = -1

            fun concluirFaixa() {
                if (pendentes.decrementAndGet() != 0) return
                if (cancelado.value == 1) return
                if (leitor.status == AVAssetReaderStatusFailed) {
                    escritor.cancelWriting()
                    if (cont.isActive) cont.resume(falhaDe(leitor.error, VideoTranscodeError.SOURCE_UNREADABLE))
                    return
                }
                escritor.finishWritingWithCompletionHandler {
                    val falha = if (escritor.status == AVAssetWriterStatusCompleted) {
                        null
                    } else {
                        falhaDe(escritor.error, VideoTranscodeError.ENCODER_FAILED)
                    }
                    if (cont.isActive) cont.resume(falha)
                }
            }

            val filaDeVideo = dispatch_queue_create("br.com.codecacto.kmplib.video.transcode.video", null)
            var ultimoMantido: Long? = null
            var videoTerminou = false
            entradaDeVideo.requestMediaDataWhenReadyOnQueue(filaDeVideo) {
                while (!videoTerminou && entradaDeVideo.readyForMoreMediaData) {
                    if (cancelado.value == 1) {
                        videoTerminou = true
                        break
                    }
                    val amostra = saidaDeVideo.copyNextSampleBuffer()
                    if (amostra == null) {
                        videoTerminou = true
                        entradaDeVideo.markAsFinished()
                        concluirFaixa()
                        break
                    }
                    val segundosAmostra = CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(amostra))
                    val micros = if (segundosAmostra.isNaN()) 0L else (segundosAmostra * 1_000_000.0).toLong()
                    val ok = if (shouldKeepVideoFrame(micros, ultimoMantido, profile.frameRate)) {
                        ultimoMantido = micros
                        entradaDeVideo.appendSampleBuffer(amostra)
                    } else {
                        true
                    }
                    CFRelease(amostra)
                    if (!ok) {
                        videoTerminou = true
                        leitor.cancelReading()
                        if (cancelado.compareAndSet(0, 1) && cont.isActive) {
                            cont.resume(falhaDe(escritor.error, VideoTranscodeError.ENCODER_FAILED))
                        }
                        break
                    }
                    val pct = (((micros - inicioMicros).toDouble() / duracaoMicros) * 100).toInt().coerceIn(0, 99)
                    if (pct > ultimoProgresso) {
                        ultimoProgresso = pct
                        dispatch_async(dispatch_get_main_queue()) { onProgress(pct / 100f) }
                    }
                }
            }

            if (entradaDeAudio != null && saidaDeAudio != null) {
                val filaDeAudio = dispatch_queue_create("br.com.codecacto.kmplib.video.transcode.audio", null)
                var audioTerminou = false
                entradaDeAudio.requestMediaDataWhenReadyOnQueue(filaDeAudio) {
                    while (!audioTerminou && entradaDeAudio.readyForMoreMediaData) {
                        if (cancelado.value == 1) {
                            audioTerminou = true
                            break
                        }
                        val amostra = saidaDeAudio.copyNextSampleBuffer()
                        if (amostra == null) {
                            audioTerminou = true
                            entradaDeAudio.markAsFinished()
                            concluirFaixa()
                            break
                        }
                        val ok = entradaDeAudio.appendSampleBuffer(amostra)
                        CFRelease(amostra)
                        if (!ok) {
                            audioTerminou = true
                            leitor.cancelReading()
                            if (cancelado.compareAndSet(0, 1) && cont.isActive) {
                                cont.resume(falhaDe(escritor.error, VideoTranscodeError.ENCODER_FAILED))
                            }
                            break
                        }
                    }
                }
            }

            cont.invokeOnCancellation {
                cancelado.value = 1
                leitor.cancelReading()
                escritor.cancelWriting()
            }
        }
    }

    /** A capa: primeiro quadro, em pé, maior lado ≤ [maxDimension], em JPEG. `null` se não sair. */
    private fun miniatura(url: NSURL, maxDimension: Int): Miniatura? = try {
        val gerador = AVAssetImageGenerator(AVURLAsset(uRL = url, options = null)).apply {
            appliesPreferredTrackTransform = true
            maximumSize = CGSizeMake(maxDimension.toDouble(), maxDimension.toDouble())
        }
        val quadro = gerador.copyCGImageAtTime(kCMTimeZero.readValue(), actualTime = null, error = null)
        if (quadro == null) {
            null
        } else {
            val largura = CGImageGetWidth(quadro).toInt()
            val altura = CGImageGetHeight(quadro).toInt()
            val dados = UIImageJPEGRepresentation(UIImage.imageWithCGImage(quadro), THUMBNAIL_JPEG_QUALITY)
            CGImageRelease(quadro)
            dados?.let { nsData ->
                val bytes = ByteArray(nsData.length.toInt())
                if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), nsData.bytes, nsData.length) }
                Miniatura(bytes, largura, altura)
            }
        }
    } catch (e: Exception) {
        AppLogger.w(TAG, "Miniatura não saiu: ${e::class.simpleName}")
        null
    }
}

private class Miniatura(val bytes: ByteArray, val width: Int, val height: Int)

/** O `NSError` do AVFoundation no vocabulário da lib (código no `detail`, nunca caminho). */
private fun falhaDe(erro: NSError?, padrao: VideoTranscodeError): VideoTranscodeResult.Failure {
    val tipo = when {
        erro == null -> padrao
        erro.code == AVErrorDiskFull ||
            (erro.domain == NSCocoaErrorDomain && erro.code == NSFileWriteOutOfSpaceError) -> VideoTranscodeError.NO_SPACE
        erro.code == AVErrorDecoderNotFound || erro.code == AVErrorFileFormatNotRecognized -> VideoTranscodeError.UNSUPPORTED_FORMAT
        erro.code == AVErrorEncoderNotFound -> VideoTranscodeError.ENCODER_FAILED
        else -> padrao
    }
    AppLogger.w(TAG, "Compressão falhou (${erro?.domain} ${erro?.code})")
    return VideoTranscodeResult.Failure(tipo, erro?.let { "${it.domain} ${it.code}" })
}

private fun VideoTranscodeSource.toNsUrl(): NSURL? =
    if (isUri) NSURL.URLWithString(value) else NSURL.fileURLWithPath(value)

private fun tamanhoDe(caminho: String): Long {
    val atributos = NSFileManager.defaultManager.attributesOfItemAtPath(caminho, error = null)
    return (atributos?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
}

private fun apagar(caminho: String) {
    deleteLocalVideoFile(caminho)
}

internal actual fun deleteLocalVideoFile(path: String): Boolean = runCatching {
    val fm = NSFileManager.defaultManager
    !fm.fileExistsAtPath(path) || fm.removeItemAtPath(path, error = null)
}.getOrDefault(false)

private const val TIMESCALE = 600
private const val AUDIO_SAMPLE_RATE = 44_100.0
private const val AUDIO_CHANNELS = 2
private const val THUMBNAIL_JPEG_QUALITY = 0.82
