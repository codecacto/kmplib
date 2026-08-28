package br.com.codecacto.kmplib.sync

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testa o **caminho real** do [DefaultSyncEngine]: criar pai+filho offline, gerar o lote de
 * push (via [SyncPort] fake que captura o [SyncPushRequest]) e verificar a resolução de FKs
 * por-clientId — backfill quando o pai já tem serverId, [SyncOp.refs] quando o pai está
 * pendente no mesmo lote. Regressão do bug em que `SyncOp.refs` era sempre `null`.
 */
class DefaultSyncEngineRefsTest {

    // -- Modelos de domínio de teste ---------------------------------------

    @Serializable
    data class ClientModel(
        val clientId: String,
        val id: String = clientId, // serverId == clientId enquanto só-local
        val name: String,
        val updatedAt: String = "2026-06-14T10:00:00Z",
    )

    @Serializable
    data class FreightModel(
        val clientId: String,
        val id: String = clientId,
        val clientRef: String, // FK → ClientModel.clientId
        val origin: String,
        val updatedAt: String = "2026-06-14T10:05:00Z",
    )

    private fun clientSyncable() = object : SyncableEntity<ClientModel> {
        override val name = "client"
        override val serializer: KSerializer<ClientModel> = ClientModel.serializer()
        override fun clientIdOf(model: ClientModel) = model.clientId
        override fun serverIdOf(model: ClientModel) = if (model.id == model.clientId) null else model.id
        override fun updatedAtOf(model: ClientModel) = model.updatedAt
    }

    private fun freightSyncable() = object : SyncableEntity<FreightModel> {
        override val name = "freight"
        override val serializer: KSerializer<FreightModel> = FreightModel.serializer()
        override fun clientIdOf(model: FreightModel) = model.clientId
        override fun serverIdOf(model: FreightModel) = if (model.id == model.clientId) null else model.id
        override fun updatedAtOf(model: FreightModel) = model.updatedAt
        override fun refsOf(model: FreightModel) = mapOf("clientRef" to model.clientRef)
    }

    // -- SyncPort fake que captura o request -------------------------------

    private class CapturingPort(
        val results: (SyncPushRequest) -> List<SyncResult> = { emptyList() },
    ) : SyncPort {
        var lastPush: SyncPushRequest? = null
        override suspend fun pull(req: SyncPullRequest) = SyncPullResponse(serverTime = "t")
        override suspend fun push(req: SyncPushRequest): SyncPushResponse {
            lastPush = req
            return SyncPushResponse(serverTime = "t", results = results(req))
        }
    }

    private fun engine(store: SyncStore, port: SyncPort): DefaultSyncEngine =
        DefaultSyncEngine(store = store, port = port, now = { "2026-06-14T10:00:00Z" })

    private fun SyncPushRequest.opFor(entity: String): SyncOp =
        ops.first { it.entity == entity }

    private fun JsonObject.field(name: String): String? =
        (this[name] as? JsonPrimitive)?.content

    // ----------------------------------------------------------------------

    @Test
    fun child_with_pending_parent_in_same_batch_emits_refs() = runTest {
        val store = FakeSyncStore()
        val port = CapturingPort()
        val eng = engine(store, port)
        eng.register(clientSyncable())
        eng.register(freightSyncable())

        // pai e filho criados offline no MESMO lote (pai ainda sem serverId).
        eng.enqueue("client", SyncOpType.CREATE, ClientModel(clientId = "cli-1", name = "Acme"))
        eng.enqueue("freight", SyncOpType.CREATE, FreightModel(clientId = "frt-1", clientRef = "cli-1", origin = "SP"))

        eng.push()

        val req = port.lastPush!!
        // ordem de push respeita dependência: client (registrado antes) vem antes de freight.
        assertEquals(listOf("client", "freight"), req.ops.map { it.entity })

        val freightOp = req.opFor("freight")
        // (a) pai no mesmo lote sem serverId → refs do filho contém a FK→clientId do pai.
        assertEquals(mapOf("clientRef" to "cli-1"), freightOp.refs)
        // payload do filho mantém o clientId local (servidor resolve via refs).
        assertEquals("cli-1", (freightOp.payload as JsonObject).field("clientRef"))
    }

    @Test
    fun child_with_already_synced_parent_backfills_serverId_into_payload() = runTest {
        val store = FakeSyncStore()
        // Porta que confirma o client com serverId "srv-cli-1" no push.
        val port = CapturingPort(results = { req ->
            req.ops.map { op ->
                if (op.entity == "client") {
                    SyncResult(clientId = op.clientId, serverId = "srv-cli-1", status = SyncOpStatus.APPLIED, serverUpdatedAt = "2026-06-14T10:10:00Z")
                } else {
                    SyncResult(clientId = op.clientId, serverId = "srv-frt-1", status = SyncOpStatus.APPLIED, serverUpdatedAt = "2026-06-14T10:11:00Z")
                }
            }
        })
        val eng = engine(store, port)
        eng.register(clientSyncable())
        eng.register(freightSyncable())

        // 1º lote: só o pai → confirmado com serverId no espelho.
        eng.enqueue("client", SyncOpType.CREATE, ClientModel(clientId = "cli-1", name = "Acme"))
        eng.push()
        assertEquals("srv-cli-1", store.getByClientId("client", "cli-1")?.server_id)

        // 2º lote: o filho, agora que o pai JÁ tem serverId conhecido.
        val port2 = CapturingPort()
        val eng2 = DefaultSyncEngine(store = store, port = port2, now = { "2026-06-14T10:00:00Z" })
        eng2.register(clientSyncable())
        eng2.register(freightSyncable())
        eng2.enqueue("freight", SyncOpType.CREATE, FreightModel(clientId = "frt-1", clientRef = "cli-1", origin = "SP"))
        eng2.push()

        val freightOp = port2.lastPush!!.opFor("freight")
        // (b) pai já com serverId → payload do filho sai com a FK = serverId (backfill)...
        assertEquals("srv-cli-1", (freightOp.payload as JsonObject).field("clientRef"))
        // ...e refs fica nulo (não há nada a resolver no lote).
        assertNull(freightOp.refs)
    }

    @Test
    fun entity_without_refs_emits_null_refs_and_untouched_payload() = runTest {
        val store = FakeSyncStore()
        val port = CapturingPort()
        val eng = engine(store, port)
        eng.register(clientSyncable())

        eng.enqueue("client", SyncOpType.CREATE, ClientModel(clientId = "cli-1", name = "Acme"))
        eng.push()

        val clientOp = port.lastPush!!.opFor("client")
        assertNull(clientOp.refs)
        assertEquals("Acme", (clientOp.payload as JsonObject).field("name"))
    }

    @Test
    fun unknown_parent_clientId_still_goes_to_refs_not_silently_dropped() = runTest {
        // Pai NÃO registrado/ausente do espelho → serverId desconhecido → vai para refs
        // (servidor decide), nunca enviamos clientId órfão sem refs.
        val store = FakeSyncStore()
        val port = CapturingPort()
        val eng = engine(store, port)
        eng.register(freightSyncable()) // só o filho registrado

        eng.enqueue("freight", SyncOpType.CREATE, FreightModel(clientId = "frt-1", clientRef = "cli-orphan", origin = "RJ"))
        eng.push()

        val freightOp = port.lastPush!!.opFor("freight")
        assertEquals(mapOf("clientRef" to "cli-orphan"), freightOp.refs)
        assertTrue((freightOp.payload as JsonObject).field("clientRef") == "cli-orphan")
    }
}
