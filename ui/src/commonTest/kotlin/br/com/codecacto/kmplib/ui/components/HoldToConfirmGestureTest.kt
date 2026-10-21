package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 2.315.0 — a regra do pressione-e-segure (origem: botão de pânico do RondAki). */
class HoldToConfirmGestureTest {

    private val gesture = HoldToConfirmGesture(durationMillis = 2_000, hapticSteps = 4)

    @Test
    fun `progresso e proporcional e fica entre zero e um`() {
        assertEquals(0f, gesture.progress(-50))
        assertEquals(0f, gesture.progress(0))
        assertEquals(0.5f, gesture.progress(1_000))
        assertEquals(1f, gesture.progress(5_000))
    }

    @Test
    fun `so confirma depois do tempo inteiro`() {
        assertFalse(gesture.isConfirmed(1_999))
        assertTrue(gesture.isConfirmed(2_000))
        assertIs<HoldToConfirmStep.Holding>(gesture.advance(1_999, lastStep = 3, pressed = true))
        assertEquals(HoldToConfirmStep.Confirmed, gesture.advance(2_000, lastStep = 3, pressed = true))
    }

    @Test
    fun `soltar antes do fim cancela`() {
        assertEquals(HoldToConfirmStep.Cancelled, gesture.advance(1_500, lastStep = 2, pressed = false))
        assertEquals(HoldToConfirmStep.Cancelled, gesture.advance(0, lastStep = 0, pressed = false))
    }

    @Test
    fun `soltar depois do fim conta como confirmado`() {
        assertEquals(HoldToConfirmStep.Confirmed, gesture.advance(2_100, lastStep = 3, pressed = false))
    }

    @Test
    fun `haptico de degrau so ao alcancar um degrau novo e intermediario`() {
        val antes = gesture.advance(400, lastStep = 0, pressed = true) as HoldToConfirmStep.Holding
        assertEquals(0, antes.step)
        assertFalse(antes.stepHaptic)

        val primeiro = gesture.advance(500, lastStep = 0, pressed = true) as HoldToConfirmStep.Holding
        assertEquals(1, primeiro.step)
        assertTrue(primeiro.stepHaptic)

        val repetido = gesture.advance(700, lastStep = 1, pressed = true) as HoldToConfirmStep.Holding
        assertFalse(repetido.stepHaptic, "o mesmo degrau não vibra duas vezes")

        val terceiro = gesture.advance(1_600, lastStep = 2, pressed = true) as HoldToConfirmStep.Holding
        assertEquals(3, terceiro.step)
        assertTrue(terceiro.stepHaptic)
    }

    @Test
    fun `degraus intermediarios sao tres em quatro, o quarto e a confirmacao`() {
        var last = 0
        var ticks = 0
        var t = 0L
        while (true) {
            when (val s = gesture.advance(t, last, pressed = true)) {
                is HoldToConfirmStep.Holding -> if (s.stepHaptic) { ticks++; last = s.step }
                HoldToConfirmStep.Confirmed -> break
                HoldToConfirmStep.Cancelled -> error("não cancela segurando")
            }
            t += 16
        }
        assertEquals(3, ticks)
    }

    @Test
    fun `sem degraus nao ha haptico intermediario`() {
        val semDegrau = HoldToConfirmGesture(durationMillis = 1_000, hapticSteps = 0)
        val s = semDegrau.advance(900, lastStep = 0, pressed = true) as HoldToConfirmStep.Holding
        assertEquals(0, s.step)
        assertFalse(s.stepHaptic)
    }

    @Test
    fun `duracao e degraus fora da faixa sao recusados`() {
        assertFailsWith<IllegalArgumentException> { HoldToConfirmGesture(durationMillis = 100) }
        assertFailsWith<IllegalArgumentException> { HoldToConfirmGesture(durationMillis = 20_000) }
        assertFailsWith<IllegalArgumentException> { HoldToConfirmGesture(hapticSteps = -1) }
        assertFailsWith<IllegalArgumentException> { HoldToConfirmGesture(hapticSteps = 21) }
    }

    @Test
    fun `leitor de tela le rotulo, apoio e a dica do gesto`() {
        assertEquals(
            "Acionar pânico. Toque duas vezes e segure para confirmar",
            holdToConfirmContentDescription("Acionar pânico", null, "Toque duas vezes e segure para confirmar"),
        )
        assertEquals(
            "Encerrar turno. Fecha a ronda. Toque duas vezes e segure para confirmar",
            holdToConfirmContentDescription("Encerrar turno", "Fecha a ronda", "Toque duas vezes e segure para confirmar"),
        )
        assertEquals("X. dica", holdToConfirmContentDescription("X", "  ", "dica"))
    }
}
