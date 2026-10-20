package br.com.codecacto.kmplib.ui.announcement

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regra de abertura do modal com o modo automação (2.310.0). */
class AnnouncementDialogAutomationTest {

    private fun canOpen(
        hasCurrent: Boolean = true,
        blocking: Boolean = false,
        ready: Boolean = true,
        suppressed: Boolean = false,
        imeVisible: Boolean = false,
        automationMuted: Boolean = false,
    ) = announcementDialogCanOpen(hasCurrent, blocking, ready, suppressed, imeVisible, automationMuted)

    @Test
    fun fechavelAbreForaDaAutomacao() {
        assertTrue(canOpen())
    }

    @Test
    fun fechavelNaoAbreEmAutomacao() {
        // Build de teste (marca do runner, dublê da loja): o comunicado não cobre a tela da suíte.
        assertFalse(canOpen(automationMuted = true))
    }

    @Test
    fun manutencaoQueBloqueiaAbreMesmoEmAutomacao() {
        // Igual ao Hard update: o bloqueio é comportamento do produto, não pedido que se cala.
        assertTrue(canOpen(blocking = true, automationMuted = true, ready = false, suppressed = true, imeVisible = true))
    }

    @Test
    fun semComunicadoNaoAbre() {
        assertFalse(canOpen(hasCurrent = false))
        assertFalse(canOpen(hasCurrent = false, blocking = true))
    }

    @Test
    fun asTravasDeAntesContinuam() {
        assertFalse(canOpen(ready = false))
        assertFalse(canOpen(suppressed = true))
        assertFalse(canOpen(imeVisible = true))
    }
}
