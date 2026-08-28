package br.com.codecacto.kmplib.platform.audio

import kotlin.math.exp

/**
 * **Integração temporal Fast/Slow da IEC 61672-1**, em código comum e testável.
 *
 * A norma define a resposta de um medidor como uma média exponencial de constante de tempo τ:
 * depois de um degrau, o valor integrado chega a **~63%** do alvo em 1τ. É o que faz o número na
 * tela ser legível — sem isto, cada janela de 150 ms é medida isolada e o valor pula tanto que
 * ninguém consegue lê-lo antes de mudar.
 *
 * ⚠️ **A média é sobre a POTÊNCIA (RMS²), nunca sobre o valor em dB.** Média de decibéis não é
 * média de energia: o decibel é logarítmico, e suavizar nele achata os transientes para baixo —
 * um estouro curto e forte aparece menor do que foi, exatamente no caso em que o número importa.
 * Por isso a classe recebe e devolve **potência linear**, e a conversão para dB acontece depois,
 * uma vez, no [AudioLevelAnalyzer].
 *
 * O passo de tempo é **informado a cada janela** em vez de fixo: a cadência real de emissão varia
 * (o bloco lido do hardware não é múltiplo exato do intervalo pedido), e assumir um passo constante
 * faria o Slow ser mais rápido ou mais lento que 1 s conforme o tamanho do buffer do aparelho.
 */
class TimeWeightingIntegrator(
    /** Integração corrente. Pode ser trocada com a captura rodando (tela de Configurações). */
    var timeWeighting: AudioTimeWeighting = AudioTimeWeighting.FAST,
) {

    private var smoothedPower: Double = 0.0
    private var initialized: Boolean = false

    /** Potência integrada corrente (o mesmo valor que o último [process] devolveu). */
    val currentPower: Double get() = smoothedPower

    /**
     * Integra a potência média de uma janela.
     *
     * A **primeira** janela depois de um [reset] é adotada como está, sem suavização: começar do
     * zero faria o medidor subir do piso de silêncio até o nível real ao longo de 1τ — no Slow, um
     * segundo inteiro de número errado toda vez que a tela abre.
     *
     * @param power potência média da janela (RMS², escala linear, nunca negativa).
     * @param elapsedSeconds duração da janela em segundos. `0` ou negativo devolve o valor
     *   corrente sem alterar nada (não há tempo decorrido para integrar).
     * @return a potência integrada, pronta para virar dB.
     */
    fun process(power: Double, elapsedSeconds: Double): Double {
        val safePower = if (power.isFinite() && power > 0.0) power else 0.0
        val tau = timeWeighting.tauSeconds
        if (tau <= 0.0) {
            smoothedPower = safePower
            initialized = true
            return smoothedPower
        }
        if (!initialized) {
            smoothedPower = safePower
            initialized = true
            return smoothedPower
        }
        if (elapsedSeconds <= 0.0) return smoothedPower
        // alfa da média exponencial para um passo de tempo arbitrário: 1 - e^(-dt/τ).
        val alpha = 1.0 - exp(-elapsedSeconds / tau)
        smoothedPower += alpha * (safePower - smoothedPower)
        return smoothedPower
    }

    /** Esquece o histórico: a próxima janela é adotada como está. */
    fun reset() {
        smoothedPower = 0.0
        initialized = false
    }
}
