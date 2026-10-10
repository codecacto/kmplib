@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package br.com.codecacto.kmplib.media.voicenote

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.media.audioLevelFromAmplitude
import br.com.codecacto.kmplib.media.resampleWaveform
import br.com.codecacto.kmplib.platform.AUDIO_PLAYBACK_TEMP_DIRECTORY
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVLinearPCMBitDepthKey
import platform.AVFAudio.AVLinearPCMIsBigEndianKey
import platform.AVFAudio.AVLinearPCMIsFloatKey
import platform.AVFAudio.AVLinearPCMIsNonInterleaved
import platform.AVFoundation.AVAssetReader
import platform.AVFoundation.AVAssetReaderTrackOutput
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.tracksWithMediaType
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.CoreFoundation.CFRelease
import platform.CoreMedia.CMBlockBufferCopyDataBytes
import platform.CoreMedia.CMBlockBufferGetDataLength
import platform.CoreMedia.CMSampleBufferGetDataBuffer
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile

internal actual fun audioPlaybackCachePath(fileName: String): String? {
    val dir = NSTemporaryDirectory().trimEnd('/') + "/" + AUDIO_PLAYBACK_TEMP_DIRECTORY
    NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    return "$dir/$fileName"
}

internal actual fun audioFileSize(path: String): Long =
    (NSFileManager.defaultManager.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: -1L

internal actual fun writeAudioFile(path: String, bytes: ByteArray): Boolean {
    if (bytes.isEmpty()) return false
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    return data.writeToFile(path, atomically = true)
}

/**
 * Decodifica com `AVAssetReader` (saída Linear PCM 16 bits, little-endian, intercalado) e guarda o
 * pico de cada bloco de 1024 amostras; no fim reduz a [bars] barras. A API oficial de leitura de
 * mídia da Apple — sem lib de terceiros.
 */
actual suspend fun extractAudioWaveform(path: String, bars: Int): List<Float>? = withContext(Dispatchers.IO) {
    if (bars <= 0) return@withContext null
    try {
        val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(path), options = null)
        val faixa = asset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack ?: return@withContext null
        val leitor = AVAssetReader(asset = asset, error = null)
        val ajustes: Map<Any?, Any?> = mapOf(
            AVFormatIDKey to NSNumber(unsignedInt = kAudioFormatLinearPCM),
            AVLinearPCMBitDepthKey to NSNumber(int = 16),
            AVLinearPCMIsFloatKey to NSNumber(bool = false),
            AVLinearPCMIsBigEndianKey to NSNumber(bool = false),
            AVLinearPCMIsNonInterleaved to NSNumber(bool = false),
        )
        val saida = AVAssetReaderTrackOutput(track = faixa, outputSettings = ajustes)
        leitor.addOutput(saida)
        if (!leitor.startReading()) return@withContext null
        val picos = ArrayList<Int>(4096)
        var pico = 0
        var noBloco = 0
        while (true) {
            val amostra = saida.copyNextSampleBuffer() ?: break
            try {
                val bloco = CMSampleBufferGetDataBuffer(amostra) ?: continue
                val tamanho = CMBlockBufferGetDataLength(bloco).toInt()
                if (tamanho <= 1) continue
                val bytes = ByteArray(tamanho)
                bytes.usePinned { CMBlockBufferCopyDataBytes(bloco, 0u, tamanho.toULong(), it.addressOf(0)) }
                var i = 0
                while (i + 1 < tamanho) {
                    val v = ((bytes[i].toInt() and 0xFF) or (bytes[i + 1].toInt() shl 8)).toShort().toInt()
                    val a = if (v < 0) -v else v
                    if (a > pico) pico = a
                    if (++noBloco >= 1024) {
                        picos += pico
                        pico = 0
                        noBloco = 0
                    }
                    i += 2
                }
            } finally {
                CFRelease(amostra)
            }
        }
        if (noBloco > 0) picos += pico
        if (picos.isEmpty()) null else resampleWaveform(picos.map { audioLevelFromAmplitude(it) }, bars)
    } catch (e: Exception) {
        AppLogger.w("VoiceNotePlayer", "onda não extraída: ${e::class.simpleName}")
        null
    }
}
