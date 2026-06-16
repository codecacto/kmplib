package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiSelectListTest {

    // ========================
    // multiSelectSummary
    // ========================

    @Test
    fun summary_noneSelected() {
        val s = multiSelectSummary(emptySet(), listOf("a", "b", "c"))
        assertEquals(0, s.selectedCount)
        assertEquals(3, s.totalCount)
        assertTrue(s.noneSelected)
        assertFalse(s.allSelected)
    }

    @Test
    fun summary_allSelected() {
        val s = multiSelectSummary(setOf("a", "b", "c"), listOf("a", "b", "c"))
        assertEquals(3, s.selectedCount)
        assertTrue(s.allSelected)
        assertFalse(s.noneSelected)
    }

    @Test
    fun summary_partialSelected() {
        val s = multiSelectSummary(setOf("a"), listOf("a", "b", "c"))
        assertEquals(1, s.selectedCount)
        assertFalse(s.allSelected)
        assertFalse(s.noneSelected)
    }

    @Test
    fun summary_ignoresGhostIdsNotInEnabledList() {
        // "z" não está na lista selecionável → não conta (lista mudou após seleção).
        val s = multiSelectSummary(setOf("a", "z"), listOf("a", "b"))
        assertEquals(1, s.selectedCount)
        assertEquals(2, s.totalCount)
        assertFalse(s.allSelected)
    }

    @Test
    fun summary_emptyEnabledIsNeverAllSelected() {
        val s = multiSelectSummary(setOf("a"), emptyList())
        assertEquals(0, s.selectedCount)
        assertEquals(0, s.totalCount)
        assertFalse(s.allSelected)
        assertTrue(s.noneSelected)
    }

    // ========================
    // toggleSelection
    // ========================

    @Test
    fun toggle_addsWhenAbsent() {
        assertEquals(setOf("a"), toggleSelection(emptySet(), "a"))
    }

    @Test
    fun toggle_removesWhenPresent() {
        assertEquals(setOf("b"), toggleSelection(setOf("a", "b"), "a"))
    }

    @Test
    fun toggle_isIdempotentRoundTrip() {
        val start = setOf("a", "b")
        val after = toggleSelection(toggleSelection(start, "c"), "c")
        assertEquals(start, after)
    }

    // ========================
    // toggleAllSelection
    // ========================

    @Test
    fun toggleAll_selectsAllWhenNoneSelected() {
        assertEquals(setOf("a", "b", "c"), toggleAllSelection(emptySet(), listOf("a", "b", "c")))
    }

    @Test
    fun toggleAll_selectsAllWhenPartiallySelected() {
        assertEquals(setOf("a", "b", "c"), toggleAllSelection(setOf("a"), listOf("a", "b", "c")))
    }

    @Test
    fun toggleAll_clearsWhenAllSelected() {
        assertEquals(emptySet(), toggleAllSelection(setOf("a", "b"), listOf("a", "b")))
    }

    @Test
    fun toggleAll_clearOnlyRemovesEnabledIdsPreservingOthers() {
        // "x" não é selecionável; ao limpar os habilitados, "x" permanece no Set.
        val result = toggleAllSelection(setOf("a", "b", "x"), listOf("a", "b"))
        assertEquals(setOf("x"), result)
    }

    @Test
    fun toggleAll_selectAllPreservesPreexistingForeignIds() {
        val result = toggleAllSelection(setOf("x"), listOf("a", "b"))
        assertEquals(setOf("x", "a", "b"), result)
    }
}
