package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class RefreshIndicatorPhaseTest {

    @Test
    fun faseSegueAFracaoPuxadaEAtualizacaoVenceTudo() {
        assertEquals(RefreshIndicatorPhase.Idle, refreshIndicatorPhase(0f, isRefreshing = false))
        assertEquals(RefreshIndicatorPhase.Pulling, refreshIndicatorPhase(0.4f, isRefreshing = false))
        assertEquals(RefreshIndicatorPhase.ReadyToRelease, refreshIndicatorPhase(1f, isRefreshing = false))
        assertEquals(RefreshIndicatorPhase.ReadyToRelease, refreshIndicatorPhase(1.6f, isRefreshing = false))
        assertEquals(RefreshIndicatorPhase.Refreshing, refreshIndicatorPhase(0f, isRefreshing = true))
        assertEquals(RefreshIndicatorPhase.Refreshing, refreshIndicatorPhase(1.2f, isRefreshing = true))
    }

    @Test
    fun estadoExpoeAFase() {
        assertEquals(RefreshIndicatorPhase.Pulling, RefreshIndicatorState(0.5f, false).phase)
    }
}
