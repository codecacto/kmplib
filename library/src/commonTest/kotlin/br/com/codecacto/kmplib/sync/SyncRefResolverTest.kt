package br.com.codecacto.kmplib.sync

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Unidade pura da resolução de FKs (backfill × refs). */
class SyncRefResolverTest {

    private fun JsonObject.field(name: String) = (this[name] as? JsonPrimitive)?.content

    @Test
    fun no_declared_refs_returns_payload_unchanged_and_null_refs() {
        val payload = buildJsonObject { put("origin", "SP") }
        val r = SyncRefResolver.resolve(payload, emptyMap()) { "ignored" }
        assertSame(payload, r.payload)
        assertNull(r.refs)
    }

    @Test
    fun null_payload_returns_null() {
        val r = SyncRefResolver.resolve(null, mapOf("clientRef" to "cli-1")) { "srv" }
        assertNull(r.payload)
        assertNull(r.refs)
    }

    @Test
    fun parent_with_serverId_backfills_payload_and_no_refs() {
        val payload = buildJsonObject { put("clientRef", "cli-1"); put("origin", "SP") }
        val r = SyncRefResolver.resolve(payload, mapOf("clientRef" to "cli-1")) { "srv-1" }
        assertEquals("srv-1", (r.payload as JsonObject).field("clientRef"))
        assertNull(r.refs)
    }

    @Test
    fun parent_without_serverId_emits_refs_and_keeps_clientId_in_payload() {
        val payload = buildJsonObject { put("clientRef", "cli-1") }
        val r = SyncRefResolver.resolve(payload, mapOf("clientRef" to "cli-1")) { null }
        assertEquals(mapOf("clientRef" to "cli-1"), r.refs)
        assertEquals("cli-1", (r.payload as JsonObject).field("clientRef"))
    }

    @Test
    fun multiple_fks_mixed_backfill_and_refs() {
        val payload = buildJsonObject {
            put("clientRef", "cli-1")
            put("truckId", "trk-2")
        }
        val r = SyncRefResolver.resolve(
            payload,
            mapOf("clientRef" to "cli-1", "truckId" to "trk-2"),
        ) { id -> if (id == "cli-1") "srv-cli-1" else null }

        val obj = r.payload as JsonObject
        assertEquals("srv-cli-1", obj.field("clientRef")) // backfill
        assertEquals("trk-2", obj.field("truckId"))       // mantém clientId
        assertEquals(mapOf("truckId" to "trk-2"), r.refs)  // só o pendente
    }

    @Test
    fun blank_declared_ref_is_ignored() {
        val payload = buildJsonObject { put("clientRef", "") }
        val r = SyncRefResolver.resolve(payload, mapOf("clientRef" to "")) { "srv" }
        assertNull(r.refs)
    }

    @Test
    fun payload_field_already_serverId_is_preserved_not_remapped() {
        // App já resolveu a FK para o serverId; declaredRef ainda traz o clientId local.
        // O resolver NÃO deve pisar no serverId já gravado nem emitir refs.
        val payload = buildJsonObject { put("clientRef", "srv-cli-1") }
        val r = SyncRefResolver.resolve(payload, mapOf("clientRef" to "cli-1")) { null }
        assertEquals("srv-cli-1", (r.payload as JsonObject).field("clientRef"))
        assertNull(r.refs)
    }
}
