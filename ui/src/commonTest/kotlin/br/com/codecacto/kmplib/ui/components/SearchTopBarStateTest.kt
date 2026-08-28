package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Estado do `SearchTopBar` (lupa que revela o campo) + badge do funil de filtros. */
class SearchTopBarStateTest {

    @Test
    fun starts_closed_and_empty() {
        val state = SearchTopBarState()
        assertFalse(state.active)
        assertEquals("", state.query)
    }

    @Test
    fun open_reveals_field_and_close_clears_query() {
        val state = SearchTopBarState()
        state.open()
        state.onQueryChange("maria")
        assertTrue(state.active)
        assertEquals("maria", state.query)

        state.close()
        assertFalse(state.active)
        assertEquals("", state.query, "fechar a busca precisa limpar o filtro da lista")
    }

    @Test
    fun clear_query_keeps_search_open() {
        val state = SearchTopBarState(initialQuery = "abc", initialActive = true)
        state.clearQuery()
        assertTrue(state.active)
        assertEquals("", state.query)
    }

    @Test
    fun toggle_alternates() {
        val state = SearchTopBarState()
        state.toggle()
        assertTrue(state.active)
        state.onQueryChange("x")
        state.toggle()
        assertFalse(state.active)
        assertEquals("", state.query)
    }

    @Test
    fun filter_badge_hidden_when_no_active_filter() {
        assertNull(filterBadgeLabel(0))
        assertNull(filterBadgeLabel(-3))
    }

    @Test
    fun filter_badge_shows_count_and_caps_at_nine_plus() {
        assertEquals("1", filterBadgeLabel(1))
        assertEquals("9", filterBadgeLabel(9))
        assertEquals("9+", filterBadgeLabel(10))
        assertEquals("9+", filterBadgeLabel(120))
    }
}
