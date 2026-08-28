package br.com.codecacto.kmplib.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncModelsTest {

    @Test
    fun syncOpType_wire_mapping_roundtrips() {
        assertEquals("create", SyncOpType.CREATE.wire)
        assertEquals("update", SyncOpType.UPDATE.wire)
        assertEquals("delete", SyncOpType.DELETE.wire)
        assertEquals(SyncOpType.CREATE, SyncOpType.fromWire("create"))
        assertEquals(SyncOpType.DELETE, SyncOpType.fromWire("delete"))
    }

    @Test
    fun syncOpType_unknown_wire_defaults_to_update() {
        assertEquals(SyncOpType.UPDATE, SyncOpType.fromWire("weird"))
    }

    @Test
    fun summary_plus_accumulates_and_keeps_first_error() {
        val a = SyncResultSummary(pushed = 2, conflicts = 1, pulled = 3)
        val b = SyncResultSummary(pushed = 1, rejected = 1, deletedApplied = 2, failed = true, error = "boom")
        val sum = a + b
        assertEquals(3, sum.pushed)
        assertEquals(1, sum.conflicts)
        assertEquals(1, sum.rejected)
        assertEquals(3, sum.pulled)
        assertEquals(2, sum.deletedApplied)
        assertTrue(sum.failed)
        assertEquals("boom", sum.error)
    }

    @Test
    fun summary_ok_reflects_failed_flag() {
        assertTrue(SyncResultSummary.EMPTY.ok)
        assertFalse(SyncResultSummary.failure("x").ok)
    }

    @Test
    fun syncState_offline_and_error_are_distinct() {
        assertTrue(SyncState.Offline is SyncState)
        val e = SyncState.Error("falha")
        assertEquals("falha", (e as SyncState.Error).message)
        val s = SyncState.Syncing(5)
        assertEquals(5, (s as SyncState.Syncing).pending)
    }

    @Test
    fun queueItem_carries_label_and_error() {
        val item = SyncQueueItem(
            localId = "l1", entity = "frete", op = SyncOpType.UPDATE,
            status = SyncItemStatus.Error, label = "frete · update", error = "timeout",
        )
        assertEquals("frete · update", item.label)
        assertEquals(SyncItemStatus.Error, item.status)
        assertEquals("timeout", item.error)
    }
}
