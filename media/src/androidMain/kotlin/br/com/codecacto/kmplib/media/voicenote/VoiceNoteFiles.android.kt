package br.com.codecacto.kmplib.media.voicenote

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.media.SoundEffectPlayerHolder
import br.com.codecacto.kmplib.media.audioLevelFromAmplitude
import br.com.codecacto.kmplib.platform.AUDIO_PLAYBACK_TEMP_DIRECTORY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder

internal actual fun audioPlaybackCachePath(fileName: String): String? {
    val context = SoundEffectPlayerHolder.getContext() ?: return null
    val dir = File(context.cacheDir, AUDIO_PLAYBACK_TEMP_DIRECTORY).apply { mkdirs() }
    return File(dir, fileName).absolutePath
}

internal actual fun audioFileSize(path: String): Long = File(path).let { if (it.isFile) it.length() else -1L }

internal actual fun writeAudioFile(path: String, bytes: ByteArray): Boolean = runCatching {
    val destino = File(path)
    val tmp = File(destino.parentFile, destino.name + ".tmp")
    tmp.writeBytes(bytes)
    destino.delete()
    tmp.renameTo(destino)
}.getOrDefault(false)

/**
 * Decodifica com `MediaExtractor` + `MediaCodec` (o decodificador do aparelho, sem lib nativa) e
 * guarda o pico de cada trecho. O número de amostras por barra sai da duração e da taxa do formato;
 * sem duração conhecida, acumula tudo e reduz no fim.
 */
actual suspend fun extractAudioWaveform(path: String, bars: Int): List<Float>? = withContext(Dispatchers.Default) {
    if (bars <= 0) return@withContext null
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    try {
        extractor.setDataSource(path)
        val faixa = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: return@withContext null
        extractor.selectTrack(faixa)
        val formato = extractor.getTrackFormat(faixa)
        val mime = formato.getString(MediaFormat.KEY_MIME) ?: return@withContext null
        val dec = MediaCodec.createDecoderByType(mime)
        codec = dec
        dec.configure(formato, null, null, 0)
        dec.start()

        val picos = ArrayList<Int>(4096)
        var picoAtual = 0
        var amostrasNoBloco = 0
        val porBloco = 1024 // ~23 ms a 44,1 kHz: resolução fina, reduzida a [bars] no fim
        val info = MediaCodec.BufferInfo()
        var fimEntrada = false
        var fimSaida = false
        while (!fimSaida) {
            if (!fimEntrada) {
                val i = dec.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    val buf = dec.getInputBuffer(i) ?: continue
                    val n = extractor.readSampleData(buf, 0)
                    if (n < 0) {
                        dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        fimEntrada = true
                    } else {
                        dec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val o = dec.dequeueOutputBuffer(info, 10_000)
            if (o >= 0) {
                val out = dec.getOutputBuffer(o)
                if (out != null && info.size > 0) {
                    out.position(info.offset)
                    out.limit(info.offset + info.size)
                    val shorts = out.order(ByteOrder.nativeOrder()).asShortBuffer()
                    while (shorts.hasRemaining()) {
                        val v = kotlin.math.abs(shorts.get().toInt())
                        if (v > picoAtual) picoAtual = v
                        if (++amostrasNoBloco >= porBloco) {
                            picos += picoAtual
                            picoAtual = 0
                            amostrasNoBloco = 0
                        }
                    }
                }
                dec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) fimSaida = true
            }
        }
        if (amostrasNoBloco > 0) picos += picoAtual
        if (picos.isEmpty()) return@withContext null
        br.com.codecacto.kmplib.media.resampleWaveform(picos.map { audioLevelFromAmplitude(it) }, bars)
    } catch (e: Exception) {
        AppLogger.w("VoiceNotePlayer", "onda não extraída: ${e::class.simpleName}")
        null
    } finally {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { extractor.release() }
    }
}
