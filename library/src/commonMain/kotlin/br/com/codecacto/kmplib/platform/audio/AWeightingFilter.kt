package br.com.codecacto.kmplib.platform.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * **Curva A da IEC 61672-1, derivada em runtime a partir da taxa de amostragem.**
 *
 * O filtro analógico da norma é definido por quatro frequências de polo —
 * 20,598997 · 107,65265 · 737,86223 · 12194,217 Hz — com quatro zeros na origem e ganho normalizado
 * para **0 dB em 1 kHz**. Aqui ele vira um filtro digital de 6ª ordem (três biquads) pela
 * **transformada bilinear**, e os coeficientes são calculados **para a taxa recebida**.
 *
 * ⚠️ **É proibido hardcodar coeficientes de 44.100 Hz** — e esta é a razão de a classe existir. O
 * aparelho que abrir o microfone em 48 kHz (muito comum) ou cair para 16 kHz mediria **errado em
 * silêncio**: o filtro escorregaria em frequência, o número na tela mudaria de significado e nada
 * no log denunciaria. Derivar da taxa custa três dúzias de linhas, uma vez por sessão.
 *
 * **Pré-warp seletivo dos polos.** A transformada bilinear comprime as frequências perto de
 * Nyquist; sem correção, a resposta a 10 kHz em 44,1 kHz erra **-1,5 dB** (o polo de 12,2 kHz cai
 * no lugar errado). Pré-warpar cada polo (`w = 2·fs·tan(π·f/fs)`) derruba esse erro para +0,7 dB.
 * O pré-warp só é aplicado a polo **seguramente abaixo de Nyquist** ([PREWARP_LIMIT_FRACTION]):
 * acima disso a tangente explode (e troca de sinal), o polo iria parar do lado errado do plano e o
 * filtro sairia deformado — foi medido: em 16 kHz de taxa, pré-warpar o polo de 12,2 kHz erra
 * **-5,5 dB** em 4 kHz.
 *
 * Precisão medida contra a tabela da norma (erro máximo, ponderando de 20 Hz a 12,5 kHz):
 * **0,2 dB em 44,1 kHz e 48 kHz**; 0,5 dB em 16 kHz. Perto de Nyquist a resposta despenca — é
 * inerente à transformada (os zeros da origem caem em `z = -1`) e não afeta a faixa que a norma
 * cobre.
 *
 * A classe é **pura**: entra `Double`, sai `Double`. Nenhum `actual` faz aritmética de dB.
 */
class AWeightingFilter(val sampleRate: Int) {

    init {
        require(sampleRate >= AudioCaptureConfig.MIN_SAMPLE_RATE) {
            "sampleRate deve ser >= ${AudioCaptureConfig.MIN_SAMPLE_RATE} Hz para a curva A"
        }
    }

    private val sections: Array<Biquad> = buildSections(sampleRate)

    /**
     * Ganho escalar que leva a resposta a **exatamente 0 dB em 1 kHz**, calculado sobre a cascata
     * **já digital** — e não pela constante analógica de 2 dB que as implementações costumam
     * copiar. Assim a normalização acompanha a taxa de amostragem em vez de assumi-la.
     */
    private val normalizationGain: Double = run {
        val magnitude = cascadeMagnitude(REFERENCE_FREQUENCY_HZ, sampleRate, sections)
        if (magnitude > 0.0 && magnitude.isFinite()) 1.0 / magnitude else 1.0
    }

    /** Filtra uma amostra (escala livre; a lib usa -1,0..1,0) e devolve a amostra ponderada. */
    fun process(sample: Double): Double {
        var value = sample
        for (section in sections) value = section.process(value)
        return value * normalizationGain
    }

    /**
     * Zera a memória interna dos biquads.
     *
     * Necessário ao **religar** a captura ou ao trocar de [AudioWeighting] com ela rodando: sem
     * isto, a primeira janela depois da troca carrega a cauda do sinal anterior — um estouro
     * fantasma de até um par de dB, no exato instante em que a pessoa mexeu no ajuste.
     */
    fun reset() {
        for (section in sections) section.reset()
    }

    companion object {
        /** Ponto de normalização da norma: 0 dB em 1 kHz. */
        const val REFERENCE_FREQUENCY_HZ: Double = 1_000.0

        /** Polo `f1` da IEC 61672-1 (Hz). */
        const val POLE_F1_HZ: Double = 20.598997

        /** Polo `f2` da IEC 61672-1 (Hz). */
        const val POLE_F2_HZ: Double = 107.65265

        /** Polo `f3` da IEC 61672-1 (Hz). */
        const val POLE_F3_HZ: Double = 737.86223

        /** Polo `f4` da IEC 61672-1 (Hz). */
        const val POLE_F4_HZ: Double = 12_194.217

        /**
         * Só polo abaixo desta fração da taxa é pré-warpado. Acima, `tan(π·f/fs)` deixa de ser uma
         * correção e vira uma deformação (ver KDoc da classe).
         */
        const val PREWARP_LIMIT_FRACTION: Double = 0.45

        private fun poleRadians(frequencyHz: Double, sampleRate: Int): Double =
            if (frequencyHz < PREWARP_LIMIT_FRACTION * sampleRate) {
                2.0 * sampleRate * tan(PI * frequencyHz / sampleRate)
            } else {
                2.0 * PI * frequencyHz
            }

        /**
         * Fatora o filtro da norma em três seções de 2ª ordem e as transforma para o domínio
         * digital:
         * 1. `s² / (s + w4)²` — os dois zeros na origem com o polo duplo dos agudos;
         * 2. `s² / (s + w1)²` — os outros dois zeros com o polo duplo dos graves;
         * 3. `1 / ((s + w2)(s + w3))` — os dois polos simples do meio da banda.
         */
        private fun buildSections(sampleRate: Int): Array<Biquad> {
            val w1 = poleRadians(POLE_F1_HZ, sampleRate)
            val w2 = poleRadians(POLE_F2_HZ, sampleRate)
            val w3 = poleRadians(POLE_F3_HZ, sampleRate)
            val w4 = poleRadians(POLE_F4_HZ, sampleRate)
            return arrayOf(
                bilinear(1.0, 0.0, 0.0, 1.0, 2.0 * w4, w4 * w4, sampleRate),
                bilinear(1.0, 0.0, 0.0, 1.0, 2.0 * w1, w1 * w1, sampleRate),
                bilinear(0.0, 0.0, 1.0, 1.0, w2 + w3, w2 * w3, sampleRate),
            )
        }

        /**
         * Transformada bilinear de uma seção analógica
         * `(b2·s² + b1·s + b0) / (a2·s² + a1·s + a0)`, substituindo
         * `s = 2·fs·(1 - z⁻¹)/(1 + z⁻¹)`.
         */
        private fun bilinear(
            b2: Double,
            b1: Double,
            b0: Double,
            a2: Double,
            a1: Double,
            a0: Double,
            sampleRate: Int,
        ): Biquad {
            val c = 2.0 * sampleRate
            val cc = c * c
            val nb0 = b2 * cc + b1 * c + b0
            val nb1 = 2.0 * (b0 - b2 * cc)
            val nb2 = b2 * cc - b1 * c + b0
            val na0 = a2 * cc + a1 * c + a0
            val na1 = 2.0 * (a0 - a2 * cc)
            val na2 = a2 * cc - a1 * c + a0
            return Biquad(
                b0 = nb0 / na0,
                b1 = nb1 / na0,
                b2 = nb2 / na0,
                a1 = na1 / na0,
                a2 = na2 / na0,
            )
        }

        /** Módulo da resposta em frequência da cascata, avaliada em `z = e^(j·2π·f/fs)`. */
        private fun cascadeMagnitude(
            frequencyHz: Double,
            sampleRate: Int,
            sections: Array<Biquad>,
        ): Double {
            val theta = 2.0 * PI * frequencyHz / sampleRate
            // z⁻¹ = cos(θ) - j·sen(θ)
            val cos1 = cos(theta)
            val sin1 = -sin(theta)
            val cos2 = cos(2.0 * theta)
            val sin2 = -sin(2.0 * theta)
            var magnitude = 1.0
            for (s in sections) {
                val numRe = s.b0 + s.b1 * cos1 + s.b2 * cos2
                val numIm = s.b1 * sin1 + s.b2 * sin2
                val denRe = 1.0 + s.a1 * cos1 + s.a2 * cos2
                val denIm = s.a1 * sin1 + s.a2 * sin2
                val den = sqrt(denRe * denRe + denIm * denIm)
                if (den == 0.0) return Double.POSITIVE_INFINITY
                magnitude *= sqrt(numRe * numRe + numIm * numIm) / den
            }
            return magnitude
        }
    }
}

/**
 * Seção de 2ª ordem na **forma direta II transposta** — a que a literatura de DSP indica para
 * ponto flutuante: dois estados por seção e menos acúmulo de erro numérico que a forma direta I.
 */
internal class Biquad(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double,
) {
    private var state1: Double = 0.0
    private var state2: Double = 0.0

    fun process(input: Double): Double {
        val output = b0 * input + state1
        state1 = b1 * input - a1 * output + state2
        state2 = b2 * input - a2 * output
        return output
    }

    fun reset() {
        state1 = 0.0
        state2 = 0.0
    }
}
