package br.com.codecacto.kmplib.camera.barcode

import android.media.AudioManager
import android.media.ToneGenerator
import br.com.codecacto.kmplib.core.util.AppLogger

/**
 * Bipe de confirmação no Android — `ToneGenerator` no stream de mídia.
 *
 * O gerador é criado uma vez por processo (ele mantém um `AudioTrack`; recriar a cada leitura
 * causaria estalo e alocação a cada frame aceito num turno inteiro de escaneamento).
 */
private val toneGenerator: ToneGenerator? by lazy {
    runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME) }
        .onFailure { AppLogger.w(TAG, "ToneGenerator indisponível: ${it.message}") }
        .getOrNull()
}

private const val TAG = "BarcodeScanFeedback"
private const val TONE_VOLUME = 80
private const val TONE_DURATION_MS = 120

internal actual fun playBarcodeScanBeep() {
    runCatching {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_DURATION_MS)
    }
}
