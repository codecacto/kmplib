package br.com.codecacto.kmplib.sync

import br.com.codecacto.kmplib.sync.db.Synced_entity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **GAP-KL-M-SYNC-ACCOUNTSCOPE** — o espelho local é escopado por conta.
 *
 * Sem isso, trocar de usuário no mesmo aparelho vazava dado na leitura e, pior, na **escrita**: o
 * ciclo faz PUSH antes do PULL, então a outbox de A subia inteira para a conta de B no servidor.
 * A exigência é **isolar, não apagar**: trocar de conta e voltar preserva a fila pendente de cada uma.
 */
class SyncAccountScopeTest {

    private fun row(entity: String, id: String, dirty: Boolean = false, payload: String = "{}") = Synced_entity(
        account_id = "",
        entity = entity,
        local_id = id,
        server_id = if (dirty) null else id,
        client_id = id,
        payload_json = payload,
        updated_at = null,
        dirty = if (dirty) 1L else 0L,
        pending_op = if (dirty) SyncOpType.CREATE.wire else null,
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

    @Test
    fun `dado de uma conta e invisivel para a outra`() {
        val store = FakeSyncStore()

        store.setAccountScope("motorista-A")
        store.upsert(row("passageiro", "p1", payload = """{"nome":"crianca de A"}"""))

        store.setAccountScope("motorista-B")
        assertTrue(store.getVisible("passageiro").isEmpty())
        assertNull(store.getByLocalId("passageiro", "p1"))

        store.setAccountScope("motorista-A")
        assertEquals(1, store.getVisible("passageiro").size)
    }

    @Test
    fun `outbox de A nao e drenavel sob a conta de B — e volta intacta quando A retorna`() {
        val store = FakeSyncStore()

        store.setAccountScope("A")
        store.upsert(row("marcacao", "local-1", dirty = true))
        assertEquals(1, store.getDirty("marcacao").size)

        // B entra: a fila que o push consumiria está vazia — nada de A sobe com o Bearer de B.
        store.setAccountScope("B")
        assertTrue(store.getDirty("marcacao").isEmpty())
        assertTrue(store.getAllDirty().isEmpty())
        assertEquals(0L, store.countDirty())

        // B cria a sua própria pendência; as duas convivem.
        store.upsert(row("marcacao", "local-2", dirty = true))
        assertEquals(1, store.getDirty("marcacao").size)

        // A volta: a pendência dele continua lá (isolar ≠ apagar).
        store.setAccountScope("A")
        assertEquals(1, store.getDirty("marcacao").size)
        assertEquals("local-1", store.getDirty("marcacao").single().local_id)
    }

    @Test
    fun `a leitura reativa acompanha a troca de titular`() = runTest {
        val store = FakeSyncStore()
        store.setAccountScope("A")
        store.upsert(row("passageiro", "p1"))

        assertEquals(1, store.observeVisible("passageiro").first().size)
        store.setAccountScope("B")
        assertTrue(store.observeVisible("passageiro").first().isEmpty())
    }

    @Test
    fun `Adopt (default) reivindica a base legada uma unica vez — nao perde a outbox de quem atualizou o app`() {
        val store = FakeSyncStore()
        // Base gravada antes do escopo existir (bucket "").
        store.upsert(row("marcacao", "antiga", dirty = true))
        assertEquals(1L, store.countLegacyRows())

        store.setAccountScope("A") // política default: Adopt

        assertEquals(0L, store.countLegacyRows())
        assertEquals(1, store.getDirty("marcacao").size)

        // E não vaza para a próxima conta.
        store.setAccountScope("B")
        assertTrue(store.getDirty("marcacao").isEmpty())
    }

    @Test
    fun `Isolate preserva a base legada intacta e invisivel`() {
        val store = FakeSyncStore()
        store.upsert(row("marcacao", "antiga", dirty = true))

        store.setAccountScope("A", LegacyRowsPolicy.Isolate)

        assertTrue(store.getDirty("marcacao").isEmpty()) // não adotada
        assertEquals(1L, store.countLegacyRows())        // nem apagada
    }

    @Test
    fun `Discard apaga a base legada (fail-closed de aparelho compartilhado)`() {
        val store = FakeSyncStore()
        store.upsert(row("marcacao", "antiga", dirty = true))

        store.setAccountScope("A", LegacyRowsPolicy.Discard)

        assertEquals(0L, store.countLegacyRows())
        assertTrue(store.getDirty("marcacao").isEmpty())
    }

    @Test
    fun `deleteAccountData apaga so a conta pedida (exclusao de conta - LGPD)`() {
        val store = FakeSyncStore()
        store.setAccountScope("A")
        store.upsert(row("passageiro", "p1"))
        store.setAccountScope("B")
        store.upsert(row("passageiro", "p2"))

        store.deleteAccountData("A")

        assertEquals(1, store.getVisible("passageiro").size) // B intacto
        store.setAccountScope("A")
        assertTrue(store.getVisible("passageiro").isEmpty())
    }

    @Test
    fun `cursor tambem e por conta`() {
        val store = FakeSyncStore()
        store.setAccountScope("A")
        store.setCursor("passageiro", "cursor-A")
        store.setAccountScope("B")
        assertNull(store.getCursor("passageiro"))
        store.setAccountScope("A")
        assertEquals("cursor-A", store.getCursor("passageiro"))
    }

    @Test
    fun `escopo em branco volta ao bucket legado sem apagar nada`() {
        val store = FakeSyncStore()
        store.setAccountScope("A")
        store.upsert(row("passageiro", "p1"))

        store.setAccountScope(null)

        assertEquals(SyncStore.NO_ACCOUNT, store.accountScope.value)
        assertTrue(store.getVisible("passageiro").isEmpty())
        store.setAccountScope("A")
        assertNotNull(store.getByLocalId("passageiro", "p1"))
    }
}
