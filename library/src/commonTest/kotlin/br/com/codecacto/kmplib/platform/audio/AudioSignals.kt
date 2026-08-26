package br.com.codecacto.kmplib.platform.audio

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Geradores de sinal para os testes do módulo de áudio — vetores conhecidos, sem hardware.
 *
 * Amplitude é sempre **fração do fundo de escala** (1,0 = 32767), para o teste falar a mesma língua
 * do [AudioLevelAnalyzer].
 */
internal object AudioSignals {

    const val FULL_SCALE_SHORT: Int = 32_767

    /** Senoide contínua, começando na fase zero. */
    fun sine(
        frequencyHz: Double,
        sampleRate: Int,
        seconds: Double,
        amplitude: Double = 1.0,
    ): ShortArray {
        val count = (sampleRate * seconds).toInt()
        val peak = amplitude * FULL_SCALE_SHORT
        return ShortArray(count) { index ->
            (peak * sin(2.0 * PI * frequencyHz * index / sampleRate)).roundToInt().toShort()
        }
    }

    /** Nível contínuo (corrente contínua), útil para checar o fundo de escala exato. */
    fun constant(value: Int, count: Int): ShortArray = ShortArray(count) { value.toShort() }

    /** Onda quadrada alternando entre `+value` e `-value` — pico e RMS coincidem. */
    fun square(value: Int, count: Int): ShortArray =
        ShortArray(count) { index -> if (index % 2 == 0) value.toShort() else (-value).toShort() }

    /** Silêncio digital. */
    fun silence(count: Int): ShortArray = ShortArray(count)
}
