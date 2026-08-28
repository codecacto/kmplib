package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/** Decisão do pull-to-refresh (o gesto/indicador em si é o `PullToRefreshBox` oficial do Material 3). */
class RefreshableBoxTest {

    @Test
    fun online_triggers_sync() {
        assertEquals(RefreshAction.Sync, resolveRefreshAction(isOnline = true))
    }

    @Test
    fun offline_degrades_without_hitting_network() {
        assertEquals(RefreshAction.Offline, resolveRefreshAction(isOnline = false))
    }

    @Test
    fun unknown_connectivity_tries_sync() {
        assertEquals(RefreshAction.Sync, resolveRefreshAction(isOnline = null))
    }

    @Test
    fun disabled_gesture_is_ignored_even_when_online() {
        assertEquals(RefreshAction.Ignore, resolveRefreshAction(isOnline = true, enabled = false))
        assertEquals(RefreshAction.Ignore, resolveRefreshAction(isOnline = false, enabled = false))
    }
}
