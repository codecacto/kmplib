package br.com.codecacto.kmplib.ui.announcement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Estatística do comunicado (2.335.0): como cada fechamento do modal vira evento. */
class AnnouncementEventTest {

    @Test
    fun entendiEXSaoEventosDiferentes() {
        assertEquals(AnnouncementEvent.DISMISS_GOT_IT, AnnouncementDismissReason.GOT_IT.toEvent())
        assertEquals(AnnouncementEvent.DISMISS_CLOSE, AnnouncementDismissReason.CLOSE.toEvent())
    }

    @Test
    fun fecharDepoisDaAcaoNaoContaDeNovo() {
        // A ação já virou ACTION_CLICK; o fechamento automático que segue não é "Entendi" nem X.
        assertNull(AnnouncementDismissReason.AFTER_ACTION.toEvent())
    }
}
