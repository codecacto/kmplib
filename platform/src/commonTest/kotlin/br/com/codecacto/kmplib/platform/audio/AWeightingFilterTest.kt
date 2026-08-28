package br.com.codecacto.kmplib.platform.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * **O teste que prova que os coeficientes são derivados da taxa de amostragem.**
 *
 * A mesma bateria roda em **44.100 E 48.000 Hz**: com coeficientes hardcodados para 44,1 kHz, a
 * segunda passagem erraria feio — e é exatamente esse o defeito que mediria errado em silêncio no
 * aparelho que abre o microfone em 48 kHz.
 */
class AWeightingFilterTest {

    /**
     * Resposta do filtro numa frequência, em dB, medida como a lib mede: senoide sintética passando
     * pelo [AudioLevelAnalyzer] com curva A, comparada com a mesma senoide sem ponderação.
     *
     * A primeira metade do sinal é descartada — é o transiente do filtro, que não faz parte da
     * resposta em regime.
     */
    private fun respostaDb(frequencyHz: Double, sampleRate: Int): Double {
        val comCurva = AudioLevelAnalyzer(sampleRate, AudioWeighting.A, AudioTimeWeighting.NONE)
        val semCurva = AudioLevelAnalyzer(sampleRate, AudioWeighting.Z, AudioTimeWeighting.NONE)

        val sinal = AudioSignals.sine(frequencyHz, sampleRate, seconds = 2.0, amplitude = 0.5)
        val metade = sinal.size / 2
        val transiente = sinal.copyOfRange(0, metade)
        val regime = sinal.copyOfRange(metade, sinal.size)

        comCurva.accumulate(transiente)
        comCurva.buildLevel(0L)
        semCurva.accumulate(transiente)
        semCurva.buildLevel(0L)

        comCurva.accumulate(regime)
        semCurva.accumulate(regime)
        return comCurva.buildLevel(1L).rmsDbfs - semCurva.buildLevel(1L).rmsDbfs
    }

    @Test
    fun em44100Hz_aCurvaBateComATabelaDaNorma() {
        verificarTabela(44_100)
    }

    @Test
    fun em48000Hz_aCurvaBateComAmesmaTabela() {
        // Se os coeficientes fossem de 44,1 kHz, este teste denunciaria: em 48 kHz o filtro
        // escorregaria em frequência e o erro passaria de 1 dB nos graves.
        verificarTabela(48_000)
    }

    private fun verificarTabela(sampleRate: Int) {
        assertEquals(0.0, respostaDb(1_000.0, sampleRate), 0.3, "1 kHz é o ponto de normalização")
        assertEquals(-19.1, respostaDb(100.0, sampleRate), 0.8, "100 Hz (IEC 61672-1)")
        assertEquals(-2.5, respostaDb(10_000.0, sampleRate), 1.0, "10 kHz (IEC 61672-1)")
        assertEquals(-39.4, respostaDb(31.5, sampleRate), 1.5, "31,5 Hz (IEC 61672-1)")
    }

    @Test
    fun aCurvaAtenuaOsGravesEpreservaOmeioDaBanda() {
        // A razão de existir da curva A: sem ela o número infla com ar-condicionado e trânsito
        // distante, e não bate com nenhum medidor comercial.
        val grave = respostaDb(31.5, 44_100)
        val meio = respostaDb(1_000.0, 44_100)
        assertTrue(grave < meio - 30.0, "31,5 Hz precisa ser muito mais atenuado que 1 kHz")
    }

    @Test
    fun aMesmaSenoideEmTaxasDiferentes_daOMesmoNivelPonderado() {
        // O nível de 1 kHz não pode depender da taxa em que o aparelho abriu o microfone.
        val em44 = respostaDb(1_000.0, 44_100)
        val em48 = respostaDb(1_000.0, 48_000)
        val em16 = respostaDb(1_000.0, 16_000)
        assertEquals(em44, em48, 0.2)
        assertEquals(em44, em16, 0.3)
    }

    @Test
    fun resetApagaAmemoriaDosBiquads() {
        val filtro = AWeightingFilter(44_100)
        repeat(1_000) { filtro.process(1.0) }
        filtro.reset()
        // Com a memória zerada, a primeira amostra depois do reset só depende de b0.
        val primeiraDepoisDoReset = filtro.process(0.0)
        assertEquals(0.0, primeiraDepoisDoReset, 1e-12)
    }

    @Test
    fun taxaAbaixoDoMinimo_eRecusadaNaConstrucao() {
        assertFailsWith<IllegalArgumentException> { AWeightingFilter(4_000) }
    }
}
