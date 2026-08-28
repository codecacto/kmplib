package br.com.codecacto.kmplib.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contrato de fio do sync (paridade EXATA com o backend). Garante que o shape
 * serializado não muda sem intenção e que o round-trip preserva os campos.
 */
class SyncWireTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun pullRequest_roundtrip() {
        val req = SyncPullRequest(since = "2026-06-14T00:00:00Z", entities = listOf("frete", "caminhao"))
        val text = json.encodeToString(SyncPullRequest.serializer(), req)
        val back = json.decodeFromString(SyncPullRequest.serializer(), text)
        assertEquals(req, back)
        assertTrue(text.contains("\"since\""))
        assertTrue(text.contains("\"entities\""))
    }

    @Test
    fun pullResponse_parsesDeltasAndCursor() {
        val payload = buildJsonObject { put("origem", "SP"); put("destino", "RJ") }
        val resp = SyncPullResponse(
            serverTime = "2026-06-14T10:00:00Z",
            nextCursor = "cursor-2",
            hasMore = true,
            changes = mapOf("frete" to listOf(
                SyncDelta(serverId = "srv-1", clientId = "cli-1", deleted = false, updatedAt = "2026-06-14T09:00:00Z", payload = payload),
                SyncDelta(serverId = "srv-2", deleted = true, updatedAt = "2026-06-14T09:30:00Z"),
            )),
        )
        val text = json.encodeToString(SyncPullResponse.serializer(), resp)
        val back = json.decodeFromString(SyncPullResponse.serializer(), text)
        assertEquals(resp, back)
        assertEquals(2, back.changes.getValue("frete").size)
        assertTrue(back.changes.getValue("frete")[1].deleted)
        assertNull(back.changes.getValue("frete")[1].payload)
    }

    // NOTA: este teste valida apenas o SHAPE de fio (serialização) do SyncOp com refs
    // preenchido manualmente. NÃO exercita o engine — a resolução real de refs/backfill
    // (clientId→serverId) é coberta por DefaultSyncEngineRefsTest e SyncRefResolverTest.
    @Test
    fun pushRequest_and_results_roundtrip() {
        val op = SyncOp(
            entity = "frete",
            clientId = "cli-9",
            op = "create",
            baseUpdatedAt = null,
            updatedAt = "2026-06-14T11:00:00Z",
            payload = buildJsonObject { put("valor", "1000.00") },
            refs = mapOf("caminhao" to "srv-truck-1"),
        )
        val req = SyncPushRequest(ops = listOf(op))
        val reqText = json.encodeToString(SyncPushRequest.serializer(), req)
        assertEquals(req, json.decodeFromString(SyncPushRequest.serializer(), reqText))

        val resp = SyncPushResponse(
            serverTime = "2026-06-14T11:00:01Z",
            results = listOf(
                SyncResult(clientId = "cli-9", serverId = "srv-9", status = SyncOpStatus.APPLIED, serverUpdatedAt = "2026-06-14T11:00:01Z"),
            ),
        )
        val respText = json.encodeToString(SyncPushResponse.serializer(), resp)
        val back = json.decodeFromString(SyncPushResponse.serializer(), respText)
        assertEquals(resp, back)
        assertEquals(SyncOpStatus.APPLIED, back.results.first().status)
    }

    @Test
    fun delta_payload_is_generic_jsonObject() {
        val delta = SyncDelta(serverId = "s1", updatedAt = "t", payload = buildJsonObject { put("x", 1) })
        assertTrue(delta.payload is JsonObject)
    }
}
