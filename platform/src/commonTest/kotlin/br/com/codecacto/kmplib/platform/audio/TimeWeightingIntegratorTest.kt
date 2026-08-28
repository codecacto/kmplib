package br.com.codecacto.kmplib.platform.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeWeightingIntegratorTest {

    @Test
    fun aposUmTau_odegrauChegaA63PorCentoDoAlvo() {
        // É a definição da integração exponencial da IEC 61672-1.
        val integrador = TimeWeightingIntegrator(AudioTimeWeighting.FAST)
        integrador.process(power = 0.0, elapsedSeconds = 0.125) // parte do silêncio
        val depoisDeUmTau = integrador.process(power = 1.0, elapsedSeconds = 0.125)
        assertEquals(0.632, depoisDeUmTau, 0.005)
    }

    @Test
    fun slowConvergeMaisDevagarQueFast() {
        val fast = TimeWeightingIntegrator(AudioTimeWeighting.FAST)
        val slow = TimeWeightingIntegrator(AudioTimeWeighting.SLOW)
        fast.process(0.0, 0.15)
        slow.process(0.0, 0.15)

        var valorFast = 0.0
        var valorSlow = 0.0
        repeat(4) {
            valorFast = fast.process(1.0, 0.15)
            valorSlow = slow.process(1.0, 0.15)
        }
        assertTrue(
            valorSlow < valorFast,
            "Slow (τ=1s) tem de estar atrás do Fast (τ=125ms): $valorSlow vs $valorFast",
        )
        assertTrue(valorFast > 0.9, "Em 600 ms o Fast já deveria ter praticamente chegado")
        assertTrue(valorSlow < 0.6, "Em 600 ms o Slow ainda está a meio caminho")
    }

    @Test
    fun noneNaoSuaviza() {
        val integrador = TimeWeightingIntegrator(AudioTimeWeighting.NONE)
        integrador.process(0.0, 0.15)
        assertEquals(0.25, integrador.process(0.25, 0.15))
        assertEquals(0.90, integrador.process(0.90, 0.15))
    }

    @Test
    fun aPrimeiraJanelaEadotadaComoEsta() {
        // Começar do zero faria o medidor subir do piso até o nível real ao longo de 1τ — no Slow,
        // um segundo inteiro de número errado toda vez que a tela abre.
        val integrador = TimeWeightingIntegrator(AudioTimeWeighting.SLOW)
        assertEquals(0.7, integrador.process(0.7, 0.15))
    }

    @Test
    fun janelaSemTempoDecorrido_naoAlteraOvalor() {
        val integrador = TimeWeightingIntegrator(AudioTimeWeighting.FAST)
        integrador.process(0.4, 0.15)
        assertEquals(0.4, integrador.process(1.0, 0.0))
        assertEquals(0.4, integrador.currentPower)
    }

    @Test
    fun potenciaInvalidaViraSilencio_naoNan() {
        val integrador = TimeWeightingIntegrator(AudioTimeWeighting.NONE)
        assertEquals(0.0, integrador.process(Double.NaN, 0.15))
        assertEquals(0.0, integrador.process(Double.NEGATIVE_INFINITY, 0.15))
        assertEquals(0.0, integrador.process(-1.0, 0.15))
    }

    @Test
    fun trocarDeIntegracaoAquente_valeDaProximaJanela() {
        val integrador = TimeWeightingIntegrator(AudioTimeWeighting.NONE)
        integrador.process(1.0, 0.15)
        integrador.timeWeighting = AudioTimeWeighting.SLOW
        val suavizado = integrador.process(0.0, 1.0)
        assertTrue(suavizado > 0.0, "com Slow, a queda para o silêncio é gradual")
        assertTrue(suavizado < 0.4)
    }

    @Test
    fun resetFazAproximaJanelaValerComoPrimeira() {
        val integrador = TimeWeightingIntegrator(AudioTimeWeighting.SLOW)
        integrador.process(1.0, 0.15)
        integrador.reset()
        assertEquals(0.0, integrador.currentPower)
        assertEquals(0.3, integrador.process(0.3, 0.15))
    }

    @Test
    fun oPassoDeTempoInformadoMuda_oResultado() {
        // A cadência real varia com o buffer do aparelho: assumir passo fixo faria o Slow deixar
        // de ser 1 s em quem lê blocos maiores.
        val curto = TimeWeightingIntegrator(AudioTimeWeighting.SLOW)
        val longo = TimeWeightingIntegrator(AudioTimeWeighting.SLOW)
        curto.process(0.0, 0.05)
        longo.process(0.0, 0.05)
        assertTrue(curto.process(1.0, 0.05) < longo.process(1.0, 0.5))
    }
}
