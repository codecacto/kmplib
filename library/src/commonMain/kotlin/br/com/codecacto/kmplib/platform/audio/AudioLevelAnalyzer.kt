package br.com.codecacto.kmplib.platform.audio

import kotlin.math.abs
import kotlin.math.log10

/**
 * **Todo o cálculo de nível, em código comum e testado sem hardware.**
 *
 * Os `actual` de Android e iOS só leem o microfone e despejam as amostras aqui — **não há
 * aritmética de dB dentro de nenhum deles**, de propósito: se houvesse, o mesmo som daria números
 * diferentes nas duas plataformas e ninguém descobriria sem dois aparelhos na mão.
 *
 * O ciclo é: [accumulate] a cada bloco lido do hardware (blocos pequenos, ~2048 amostras) e
 * [buildLevel] quando o intervalo de emissão fecha. Acumular por bloco e emitir por intervalo é o
 * que desacopla o tamanho do buffer do aparelho da cadência que a tela pede.
 *
 * O que ele faz, na ordem:
 * 1. **pico no sinal cru**, antes de qualquer filtro — ponderar antes de medir pico esconderia a
 *    saturação, que é justamente o que o pico existe para denunciar;
 * 2. **ponderação em frequência** ([AudioWeighting.A] pela [AWeightingFilter], ou nada em
 *    [AudioWeighting.Z]);
 * 3. **soma dos quadrados** da amostra ponderada (a potência da janela);
 * 4. **integração temporal** sobre a potência ([TimeWeightingIntegrator]);
 * 5. conversão para dBFS, **uma vez**, com grampo no piso de silêncio.
 *
 * @param sampleRate taxa efetiva da captura. Se a plataforma trocar de taxa no meio da sessão (o
 *   iOS troca quando um fone é plugado), o `actual` cria um analisador novo — os coeficientes da
 *   curva A dependem da taxa e não podem continuar os mesmos.
 */
class AudioLevelAnalyzer(
    val sampleRate: Int,
    weighting: AudioWeighting = AudioWeighting.A,
    timeWeighting: AudioTimeWeighting = AudioTimeWeighting.FAST,
) {

    init {
        require(sampleRate >= AudioCaptureConfig.MIN_SAMPLE_RATE) {
            "sampleRate deve ser >= ${AudioCaptureConfig.MIN_SAMPLE_RATE} Hz"
        }
    }

    private val filter = AWeightingFilter(sampleRate)
    private val integrator = TimeWeightingIntegrator(timeWeighting)

    private var sumOfSquares: Double = 0.0
    private var accumulatedSamples: Int = 0
    private var peakAmplitude: Double = 0.0

    /**
     * Ponderação corrente. Trocá-la **zera a memória do filtro**: sem isso, a primeira janela
     * depois da troca carregaria a cauda do sinal anterior — um estouro fantasma no exato instante
     * em que a pessoa mexeu no ajuste.
     */
    var weighting: AudioWeighting = weighting
        set(value) {
            if (field == value) return
            field = value
            filter.reset()
        }

    /** Integração temporal corrente; pode mudar a quente, sem reiniciar a captura. */
    var timeWeighting: AudioTimeWeighting
        get() = integrator.timeWeighting
        set(value) {
            integrator.timeWeighting = value
        }

    /** `true` quando há amostras acumuladas esperando um [buildLevel]. */
    val hasPendingSamples: Boolean get() = accumulatedSamples > 0

    /** Duração, em segundos, do que já foi acumulado desde o último [buildLevel]. */
    val pendingSeconds: Double get() = accumulatedSamples.toDouble() / sampleRate

    /**
     * Acumula um bloco **PCM 16-bit** (o que o Android entrega).
     *
     * @param samples buffer lido do hardware.
     * @param count quantas amostras do início do buffer são válidas — o `AudioRecord` costuma
     *   devolver menos do que o array comporta, e medir o resto do array (zeros da leitura
     *   anterior) puxaria o nível para baixo.
     */
    fun accumulate(samples: ShortArray, count: Int = samples.size) {
        val limit = count.coerceIn(0, samples.size)
        for (i in 0 until limit) {
            val raw = samples[i].toInt()
            val magnitude = abs(raw) / FULL_SCALE
            if (magnitude > peakAmplitude) peakAmplitude = magnitude
            accumulateNormalized(raw / FULL_SCALE)
        }
        accumulatedSamples += limit
    }

    /**
     * Acumula um bloco **Float32 já normalizado em -1,0..1,0** (o que o `AVAudioEngine` entrega no
     * iOS). Converter o buffer do iOS para `Short` só para reconverter aqui seria perder resolução
     * e gastar uma passada a mais.
     */
    fun accumulate(samples: FloatArray, count: Int = samples.size) {
        val limit = count.coerceIn(0, samples.size)
        for (i in 0 until limit) {
            val value = samples[i].toDouble()
            if (!value.isFinite()) continue
            val magnitude = abs(value)
            if (magnitude > peakAmplitude) peakAmplitude = magnitude
            accumulateNormalized(value)
        }
        accumulatedSamples += limit
    }

    private fun accumulateNormalized(normalized: Double) {
        val weighted = if (weighting == AudioWeighting.A) filter.process(normalized) else normalized
        sumOfSquares += weighted * weighted
    }

    /**
     * Fecha a janela: devolve a leitura e zera os acumuladores (a memória do filtro e a da
     * integração **continuam**, que é o que dá continuidade entre janelas).
     */
    fun buildLevel(timestampMillis: Long): AudioLevel {
        val samples = accumulatedSamples
        val meanPower = if (samples > 0) sumOfSquares / samples else 0.0
        val elapsedSeconds = if (samples > 0) samples.toDouble() / sampleRate else 0.0
        val integratedPower = integrator.process(meanPower, elapsedSeconds)

        val peak = peakAmplitude
        val peakDbfs = amplitudeToDbfs(peak).coerceAtMost(0.0)
        val level = AudioLevel(
            rmsDbfs = powerToDbfs(integratedPower),
            peakDbfs = peakDbfs,
            isClipping = peak >= CLIPPING_AMPLITUDE || peakDbfs >= CLIPPING_PEAK_DBFS,
            sampleRate = sampleRate,
            weighting = weighting,
            timestampMillis = timestampMillis,
        )

        sumOfSquares = 0.0
        accumulatedSamples = 0
        peakAmplitude = 0.0
        return level
    }

    /**
     * Volta ao estado inicial: acumuladores, memória do filtro e da integração. Usado ao religar a
     * captura — a cauda da sessão anterior não pode vazar para a primeira leitura da nova.
     */
    fun reset() {
        sumOfSquares = 0.0
        accumulatedSamples = 0
        peakAmplitude = 0.0
        filter.reset()
        integrator.reset()
    }

    companion object {
        /**
         * Fundo de escala do PCM 16-bit com sinal: 2¹⁵. É `32768`, e não `32767`, porque é o valor
         * que a amostra mínima (`-32768`) de fato alcança — normalizar por 32767 faria o extremo
         * negativo passar de 1,0 e o pico aparecer **acima** de 0 dBFS.
         */
        const val FULL_SCALE: Double = 32_768.0

        /**
         * Amplitude a partir da qual a amostra encostou no teto do conversor: `32767/32768`, ou
         * seja, `|sample| >= 32767` no PCM 16-bit.
         */
        const val CLIPPING_AMPLITUDE: Double = 32_767.0 / FULL_SCALE

        /**
         * Segundo critério de saturação, mais conservador: pico a menos de 0,1 dB do fundo de
         * escala. Um sinal que chega tão perto do teto **já está deformado** na prática (o
         * pré-amplificador do celular comprime antes do conversor), mesmo que nenhuma amostra tenha
         * batido exatamente em 32767.
         */
        const val CLIPPING_PEAK_DBFS: Double = -0.1

        /** Converte amplitude (0..1) para dBFS, com grampo no piso de silêncio. */
        fun amplitudeToDbfs(amplitude: Double): Double {
            if (!amplitude.isFinite() || amplitude <= 0.0) return AudioLevel.SILENCE_DBFS
            val db = 20.0 * log10(amplitude)
            return if (db.isFinite()) db.coerceAtLeast(AudioLevel.SILENCE_DBFS)
            else AudioLevel.SILENCE_DBFS
        }

        /**
         * Converte **potência** (RMS², escala linear) para dBFS, com grampo no piso de silêncio.
         *
         * O grampo é o que impede `log10(0) = -Infinity` de virar `NaN` na animação da UI e
         * **sumir com o número da tela** — falha muda, que só aparece quando o ambiente fica em
         * silêncio digital (fone plugado, microfone tomado por outro app).
         */
        fun powerToDbfs(power: Double): Double {
            if (!power.isFinite() || power <= 0.0) return AudioLevel.SILENCE_DBFS
            val db = 10.0 * log10(power)
            return if (db.isFinite()) db.coerceAtLeast(AudioLevel.SILENCE_DBFS)
            else AudioLevel.SILENCE_DBFS
        }
    }
}
