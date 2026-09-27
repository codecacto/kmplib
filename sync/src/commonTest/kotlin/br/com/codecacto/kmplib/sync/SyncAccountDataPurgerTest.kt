package br.com.codecacto.kmplib.sync

import br.com.codecacto.kmplib.core.storage.InMemoryBlobStore
import br.com.codecacto.kmplib.core.storage.SignOutPendingPolicy
import br.com.codecacto.kmplib.sync.db.Synced_entity
import br.com.codecacto.kmplib.sync.rest.DomainApiClient
import br.com.codecacto.kmplib.sync.rest.DomainTokenProvider
import br.com.codecacto.kmplib.sync.rest.RestUploadOutbox
import br.com.codecacto.kmplib.sync.rest.UploadEnqueueResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **O que a conta deixa no aparelho** (2.217.0) — espelho, outbox, fila de upload e binários.
 *
 * Caso de origem: app clínico (foto de lesão de paciente na fila de upload). Excluir a conta apagava
 * no servidor e deixava tudo isso no disco; e o logout não tinha como perguntar "há 2 fotos por
 * enviar — manter ou descartar?".
 */
class SyncAccountDataPurgerTest {

    private val api = DomainApiClient(
        HttpClient(MockEngine { respond("{}", HttpStatusCode.Created) }),
        DomainTokenProvider { "tok" },
        "https://api.example.com",
    )

    private fun linha(localId: String, serverId: String?, entidade: String = "paciente") = Synced_entity(
        account_id = "",
        entity = entidade,
        local_id = localId,
        server_id = serverId,
        client_id = localId,
        payload_json = """{"id":"$localId"}""",
        updated_at = null,
        dirty = if (serverId == null) 1L else 0L,
        pending_op = if (serverId == null) SyncOpType.CREATE.wire else null,
        deleted = 0L,
        base_updated_at = null,
        last_error = null,
        failed = 0L,
        fail_code = null,
        attempts = 0L,
        rejections = 0L,
        reject_code = null,
        reject_error = null,
    )

    private class Cenario(val store: FakeSyncStore, val blobs: InMemoryBlobStore, val outbox: RestUploadOutbox)

    /** Conta A com 2 registros sincronizados, 1 pendente e 1 foto na fila; conta B com 1 foto. */
    private suspend fun cenario(): Cenario {
        val store = FakeSyncStore()
        val blobs = InMemoryBlobStore()
        val outbox = RestUploadOutbox(api = api, store = store, blobs = blobs)

        store.setAccountScope("conta-b")
        assertIs<UploadEnqueueResult.Queued>(
            outbox.enqueue(byteArrayOf(9), "b.jpg", "image/jpeg", "/v1/fotos", id = "up-b"),
        )

        store.setAccountScope("conta-a")
        store.upsert(linha("p1", serverId = "srv-1"))
        store.upsert(linha("p2", serverId = "srv-2"))
        store.upsert(linha("p3", serverId = null))
        store.setCursor("paciente", "cursor-a")
        assertIs<UploadEnqueueResult.Queued>(
            outbox.enqueue(
                bytes = byteArrayOf(1, 2), fileName = "lesao.jpg", mimeType = "image/jpeg",
                path = "/v1/pacientes/{owner}/fotos", ownerEntity = "paciente", ownerHandle = "p2", id = "up-a",
            ),
        )
        return Cenario(store, blobs, outbox)
    }

    @Test
    fun `pendencias separam registros de arquivos`() = runTest {
        val c = cenario()
        val purger = SyncAccountDataPurger(c.store, listOf(c.outbox), clearSharedFiles = false)

        val pendente = purger.pendingChanges()

        assertEquals(1, pendente.records)
        assertEquals(1, pendente.uploads)
        assertFalse(pendente.isEmpty)
    }

    @Test
    fun `exclusao de conta apaga espelho outbox fila e binarios so da conta corrente`() = runTest {
        val c = cenario()
        var extra: LocalPurgeScope? = null
        val purger = SyncAccountDataPurger(
            c.store, listOf(c.outbox), clearSharedFiles = false, extraCleanup = { extra = it },
        )

        val relatorio = purger.purgeAccount()

        assertTrue(relatorio.isComplete)
        assertEquals(2, relatorio.discardedPending)
        assertTrue(c.store.getVisible("paciente").isEmpty())
        assertEquals(0L, c.store.countDirty())
        assertNull(c.store.getCursor("paciente"))
        assertTrue(c.outbox.pending().isEmpty())
        assertEquals(listOf("up-b-0"), c.blobs.ids(), "só o binário da OUTRA conta fica")
        assertEquals(LocalPurgeScope.Everything, extra)

        // A outra conta segue intacta.
        c.store.setAccountScope("conta-b")
        assertEquals(1, c.outbox.pending().size)
    }

    @Test
    fun `logout mantendo pendencias apaga so o sincronizado e preserva dono da foto`() = runTest {
        val c = cenario()
        val purger = SyncAccountDataPurger(c.store, listOf(c.outbox), clearSharedFiles = false)

        val relatorio = purger.purgeOnSignOut(SignOutPendingPolicy.Keep)

        assertTrue(relatorio.isComplete)
        assertEquals(2, relatorio.keptPending)
        assertEquals(1, relatorio.removedSyncedRows, "só p1: p2 é dono da foto pendente")
        assertNull(c.store.getByLocalId("paciente", "p1"))
        assertNotNull(c.store.getByLocalId("paciente", "p2"))
        assertNotNull(c.store.getByLocalId("paciente", "p3"), "pendente fica")
        assertEquals(1, c.outbox.pending().size)
        assertTrue("up-a-0" in c.blobs.ids(), "o binário pendente fica")
        assertNull(c.store.getCursor("paciente"), "sem cursor, o próximo pull devolve o que saiu")
    }

    @Test
    fun `logout descartando pendencias apaga tudo da conta`() = runTest {
        val c = cenario()
        val purger = SyncAccountDataPurger(c.store, listOf(c.outbox), clearSharedFiles = false)

        val relatorio = purger.purgeOnSignOut(SignOutPendingPolicy.Discard)

        assertEquals(2, relatorio.discardedPending)
        assertTrue(c.store.getVisible("paciente").isEmpty())
        assertEquals(listOf("up-b-0"), c.blobs.ids())
    }

    @Test
    fun `falha na limpeza do app nao impede o resto e aparece no relatorio`() = runTest {
        val c = cenario()
        val purger = SyncAccountDataPurger(
            c.store, listOf(c.outbox), clearSharedFiles = false,
            extraCleanup = { error("disco") },
        )

        val relatorio = purger.purgeAccount()

        assertFalse(relatorio.isComplete)
        assertEquals(1, relatorio.failures)
        assertTrue(c.store.getVisible("paciente").isEmpty())
    }

    @Test
    fun `fila de upload apaga linha com payload ilegivel e o binario dela`() = runTest {
        val c = cenario()
        c.store.upsert(linha("up-x", serverId = null, entidade = "kmplib_upload").copy(payload_json = "{quebrado"))
        c.blobs.write("up-x-0", byteArrayOf(7))

        val removidos = c.outbox.purgeCurrentAccount()

        assertEquals(2, removidos)
        assertFalse("up-x-0" in c.blobs.ids())
        assertTrue(c.store.getDirty("kmplib_upload").isEmpty())
    }
}
