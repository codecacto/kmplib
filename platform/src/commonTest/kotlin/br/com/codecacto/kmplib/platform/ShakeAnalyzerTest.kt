package br.com.codecacto.kmplib.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShakeAnalyzerTest {

    /** Aparelho parado: só a gravidade, 1g num eixo. */
    private fun ShakeAnalyzer.atRest(now: Long) = onAcceleration(0f, 0f, 1f, now)

    /** Chacoalhão: [g] de aceleração total num eixo. */
    private fun ShakeAnalyzer.shakeOf(g: Float, now: Long) = onAcceleration(g, 0f, 0f, now)

    @Test
    fun paradoNaMesa_naoDispara() {
        val analyzer = ShakeAnalyzer(ShakeSensitivity.HIGH)
        repeat(50) { assertFalse(analyzer.atRest(it * 20L)) }
    }

    @Test
    fun chacoalhaoAcimaDoLimiar_dispara() {
        val analyzer = ShakeAnalyzer(ShakeSensitivity.MEDIUM)
        assertTrue(analyzer.shakeOf(3f, 0L))
    }

    @Test
    fun aRajadaDoMesmoGesto_viraUmEventoSo() {
        val analyzer = ShakeAnalyzer(ShakeSensitivity.MEDIUM)
        // 50 Hz durante meio segundo, tudo acima do limiar: é UM chacoalhão.
        val eventos = (0 until 25).count { analyzer.shakeOf(3.5f, it * 20L) }
        assertEquals(1, eventos)
    }

    @Test
    fun passadoOCooldown_oProximoGestoConta() {
        val analyzer = ShakeAnalyzer(ShakeSensitivity.MEDIUM)
        assertTrue(analyzer.shakeOf(3.5f, 0L))
        assertFalse(analyzer.shakeOf(3.5f, 599L))
        assertTrue(analyzer.shakeOf(3.5f, 600L))
    }

    @Test
    fun sensibilidadeBaixa_ignoraOQueAAltaAceita() {
        val fraco = 2.0f
        assertTrue(ShakeAnalyzer(ShakeSensitivity.HIGH).shakeOf(fraco, 0L))
        assertFalse(ShakeAnalyzer(ShakeSensitivity.LOW).shakeOf(fraco, 0L))
    }

    @Test
    fun sensibilidadeTrocadaEmRuntime_valeNaProximaAmostra() {
        val analyzer = ShakeAnalyzer(ShakeSensitivity.LOW)
        assertFalse(analyzer.shakeOf(2.0f, 0L))
        analyzer.sensitivity = ShakeSensitivity.HIGH
        assertTrue(analyzer.shakeOf(2.0f, 10L))
    }

    @Test
    fun reset_esqueceOCooldown() {
        val analyzer = ShakeAnalyzer(ShakeSensitivity.MEDIUM)
        assertTrue(analyzer.shakeOf(3.5f, 0L))
        analyzer.reset()
        assertTrue(analyzer.shakeOf(3.5f, 10L))
    }

    @Test
    fun fromFraction_umEhMaisSensivelQueZero() {
        val maisSensivel = ShakeSensitivity.fromFraction(1f)
        val menosSensivel = ShakeSensitivity.fromFraction(0f)
        assertEquals(ShakeSensitivity.MOST_SENSITIVE_G, maisSensivel.thresholdG)
        assertEquals(ShakeSensitivity.LEAST_SENSITIVE_G, menosSensivel.thresholdG)
        assertTrue(maisSensivel.thresholdG < menosSensivel.thresholdG)
    }

    @Test
    fun fromFraction_prendeForaDaFaixa() {
        assertEquals(ShakeSensitivity.MOST_SENSITIVE_G, ShakeSensitivity.fromFraction(9f).thresholdG)
        assertEquals(ShakeSensitivity.LEAST_SENSITIVE_G, ShakeSensitivity.fromFraction(-9f).thresholdG)
    }

    @Test
    fun limiarAbaixoDaGravidade_ehConfiguracaoInvalida() {
        // 1g é o aparelho PARADO: aceitar isso dispararia sem parar.
        assertFailsWith<IllegalArgumentException> { ShakeSensitivity(0.9f) }
        assertFailsWith<IllegalArgumentException> { ShakeSensitivity(2f, cooldownMillis = -1) }
    }

    @Test
    fun detectorInerte_naoLigaENaoQuebra() {
        val detector = UnavailableShakeDetector()
        assertFalse(detector.isAvailable)
        assertFalse(detector.start { })
        assertFalse(detector.isRunning)
        detector.stop()
    }
}
