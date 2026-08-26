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
 * 1. **pico e contagem de amostras saturadas no sinal cru**, antes de qualquer filtro — ponderar
 *    antes de medir esconderia a saturação, que é justamente o que essa medida existe para
 *    denunciar;
 * 2. **ponderação em frequência** ([AudioWeighting.A] pela [AWeightingFilter], ou nada em
 *    [AudioWeighting.Z]);
 * 3. **soma dos quadrados** da amostra ponderada (a potência da janela);
 * 4. **integração temporal** sobre a potência ([TimeWeightingIntegrator]);
 * 5. conversão para dBFS, **uma vez**, **sem grampo** — o número que sai é o do conversor (ver
 *    [AudioLevel.SILENCE_DBFS], que é sentinela de silêncio digital, não piso de exibição).
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
    private var clippedSamples: Int = 0
    /**
     * Menor RMS já observado na sessão, ou `null` enquanto **nada** foi observado.
     *
     * `null` e não [AudioLevel.SILENCE_DBFS]: um acumulador de mínimo inicializado no menor valor
     * possível nunca é atualizado — `rms < -120` é falso para qualquer leitura real, e o piso
     * ficaria preso na sentinela pelo resto da sessão. O `null` diz "ainda não sei", que é
     * diferente de "é -120".
     */
    private var observedNoiseFloor: Double? = null

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
            if (magnitude >= CLIPPING_AMPLITUDE) clippedSamples++
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
            if (magnitude >= CLIPPING_AMPLITUDE) clippedSamples++
            accumulateNormalized(value)
        }
        accumulatedSamples += limit
    }

    private fun accumulateNormalized(normalized: Double) {
        val weighted = if (weighting == AudioWeighting.A) filter.process(normalized) else normalized
        sumOfSquares += weighted * weighted
    }

    /**
     * Fecha a janela: devolve a leitura e zera os acumuladores (a memória do filtro, a da
     * integração e o **piso de ruído da sessão** [AudioLevel.noiseFloorDbfs] **continuam** — é o que
     * dá continuidade entre janelas; só [reset] os apaga).
     */
    fun buildLevel(timestampMillis: Long): AudioLevel {
        val samples = accumulatedSamples
        val meanPower = if (samples > 0) sumOfSquares / samples else 0.0
        val elapsedSeconds = if (samples > 0) samples.toDouble() / sampleRate else 0.0
        val integratedPower = integrator.process(meanPower, elapsedSeconds)

        val peakDbfs = amplitudeToDbfs(peakAmplitude).coerceAtMost(0.0)
        val rmsDbfs = powerToDbfs(integratedPower)
        val clippedRatio =
            if (samples > 0) (clippedSamples.toDouble() / samples).toFloat() else 0f

        // Só janela com sinal de verdade move o piso de ruído: silêncio digital (potência zero) é
        // ausência de sinal — microfone tomado por outro app, permissão negada —, e adotá-lo como
        // piso travaria o valor na sentinela pelo resto da sessão, sem nada a observar.
        if (samples > 0 && integratedPower > 0.0) {
            val pisoAtual = observedNoiseFloor
            if (pisoAtual == null || rmsDbfs < pisoAtual) observedNoiseFloor = rmsDbfs
        }

        val level = AudioLevel(
            rmsDbfs = rmsDbfs,
            peakDbfs = peakDbfs,
            noiseFloorDbfs = observedNoiseFloor ?: AudioLevel.SILENCE_DBFS,
            isClipping = clippedRatio >= CLIPPING_RATIO_THRESHOLD,
            clippedSampleRatio = clippedRatio,
            sampleRate = sampleRate,
            weighting = weighting,
            timestampMillis = timestampMillis,
        )

        sumOfSquares = 0.0
        accumulatedSamples = 0
        peakAmplitude = 0.0
        clippedSamples = 0
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
        clippedSamples = 0
        observedNoiseFloor = null
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
         * Amplitude a partir da qual **uma amostra** encostou no fundo de escala do conversor:
         * `32767/32768`, ou seja, `|sample| >= 32767` no PCM 16-bit.
         *
         * Isto define **amostra saturada**, não janela saturada — quem decide a janela é
         * [CLIPPING_RATIO_THRESHOLD].
         */
        const val CLIPPING_AMPLITUDE: Double = 32_767.0 / FULL_SCALE

        /**
         * **Fração de amostras no fundo de escala a partir da qual a janela é dada como saturada:
         * 0,1%.**
         *
         * O critério é por **fração**, e não por "houve alguma amostra no teto", porque uma amostra
         * isolada não é saturação: uma porta batendo, um toque no aparelho ou um clique do próprio
         * conversor marcariam a janela inteira como suspeita e o app gritaria "pode estar saturado"
         * numa medição perfeitamente válida — falso positivo na tela principal, que é onde ele
         * custa mais caro.
         *
         * O número separa bem os dois mundos, com folga dos dois lados:
         * - **ruído isolado não dispara** — na janela default (150 ms ≈ 6.600 amostras em 44,1 kHz)
         *   são precisas ~7 amostras no teto; num bloco de 2.048, 3. Uma amostra em 2.048 dá
         *   0,049%, menos da metade do limiar;
         * - **saturação real dispara** — quando o AGC/limitador do aparelho encosta no teto, o sinal
         *   fica **achatado** e o topo permanece lá por milhares de amostras. Uma senoide em fundo
         *   de escala, que é o caso mais brando, já mantém **0,35%** das amostras no teto.
         *
         * ⚠️ **Não existe limiar de saturação em dB, e é decisão.** Até a 2.150.0 havia um segundo
         * critério (`peakDbfs >= -0,1`), que foi **removido**: o teto de captação **não é fixo** —
         * varia por aparelho entre **82 e 100 dB SPL** (Smart Tools publica Moto G4 = 94, Galaxy
         * S6 = 85, Nexus 5 = 82), porque quem corta antes não é o elemento MEMS (aguentaria
         * ~120 dB SPL) e sim o **AGC que cada fabricante ajustou para voz**. Uma constante em dB
         * acerta um aparelho e erra todos os outros. A fração de amostras no fundo de escala, ao
         * contrário, é a mesma verdade em qualquer hardware.
         */
        const val CLIPPING_RATIO_THRESHOLD: Float = 0.001f

        /**
         * Converte amplitude (0..1) para dBFS.
         *
         * Devolve [AudioLevel.SILENCE_DBFS] **apenas** quando a entrada é zero ou não-finita — o
         * caso em que `log10` produziria `-Infinity`/`NaN`, que vira `NaN` na animação da UI e
         * **some com o número da tela**. Fora disso o valor calculado sai **como é**, sem grampo
         * inferior: a lib não inventa piso.
         */
        fun amplitudeToDbfs(amplitude: Double): Double {
            if (!amplitude.isFinite() || amplitude <= 0.0) return AudioLevel.SILENCE_DBFS
            val db = 20.0 * log10(amplitude)
            return if (db.isFinite()) db else AudioLevel.SILENCE_DBFS
        }

        /**
         * Converte **potência** (RMS², escala linear) para dBFS.
         *
         * Mesma regra de [amplitudeToDbfs]: [AudioLevel.SILENCE_DBFS] é **sentinela** de silêncio
         * digital (potência exatamente zero, ou entrada não-finita), não mínimo de exibição. Toda
         * leitura com sinal sai sem grampo.
         */
        fun powerToDbfs(power: Double): Double {
            if (!power.isFinite() || power <= 0.0) return AudioLevel.SILENCE_DBFS
            val db = 10.0 * log10(power)
            return if (db.isFinite()) db else AudioLevel.SILENCE_DBFS
        }
    }
}
