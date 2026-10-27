package br.com.codecacto.kmplib.sync

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [DefaultSyncEngine] (2.328.0): a gravação feita DURANTE o push não pode ser marcada limpa pela
 * confirmação da versão anterior — senão ela nunca sobe e o pull seguinte a troca pela antiga.
 */
class DefaultSyncEngineOrderTest {

    @Serializable
    data class Nota(val clientId: String, val texto: String, val updatedAt: String = "2026-10-27T10:00:00Z")

    private val notaSyncable = object : SyncableEntity<Nota> {
        override val name = "nota"
        override val serializer: KSerializer<Nota> = Nota.serializer()
        override fun clientIdOf(model: Nota) = model.clientId
        override fun serverIdOf(model: Nota): String? = null
        override fun updatedAtOf(model: Nota) = model.updatedAt
    }

    private class Porta(val durante: suspend () -> Unit = {}) : SyncPort {
        val enviados = mutableListOf<SyncPushRequest>()
        override suspend fun pull(req: SyncPullRequest) = SyncPullResponse(serverTime = "t")
        override suspend fun push(req: SyncPushRequest): SyncPushResponse {
            enviados += req
            if (enviados.size == 1) durante()
            return SyncPushResponse(
                serverTime = "t",
                results = req.ops.map { SyncResult(it.clientId, serverId = "srv-${it.clientId}", status = SyncOpStatus.APPLIED, serverUpdatedAt = "t1") },
            )
        }
    }

    @Test
    fun `edicao durante o push continua suja e sobe no proximo`() = runTest {
        val store = FakeSyncStore()
        lateinit var engine: DefaultSyncEngine
        val porta = Porta { engine.enqueue("nota", SyncOpType.UPDATE, Nota("n1", "v2", "2026-10-27T10:01:00Z")) }
        engine = DefaultSyncEngine(store = store, port = porta, now = { "2026-10-27T10:00:00Z" })
        engine.register(notaSyncable)

        engine.enqueue("nota", SyncOpType.CREATE, Nota("n1", "v1"))
        engine.push()

        val linha = assertNotNull(store.getByClientId("nota", "n1"))
        assertEquals(1L, linha.dirty, "a v2 não pode ser dada como enviada")
        assertEquals("srv-n1", linha.server_id)
        assertEquals(SyncOpType.UPDATE.wire, linha.pending_op)
        assertTrue(linha.payload_json.contains("v2"))

        engine.push()
        assertEquals(2, porta.enviados.size)
        assertTrue(porta.enviados[1].ops.single().payload.toString().contains("v2"))
        assertEquals(0L, store.getByClientId("nota", "n1")!!.dirty)
    }
}
