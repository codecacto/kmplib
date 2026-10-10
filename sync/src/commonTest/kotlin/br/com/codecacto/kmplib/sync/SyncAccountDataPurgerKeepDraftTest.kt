package br.com.codecacto.kmplib.sync

import br.com.codecacto.kmplib.core.storage.SignOutPendingPolicy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private data class ActiveRun(val id: String, val sets: Int)

private object ActiveRunEntity : SyncableEntity<ActiveRun> {
    override val name: String = "active_run"
    override val serializer: KSerializer<ActiveRun> = ActiveRun.serializer()
    override fun clientIdOf(model: ActiveRun): String = model.id
    override fun serverIdOf(model: ActiveRun): String? = null
    override fun updatedAtOf(model: ActiveRun): String? = null
}

@Serializable
private data class Program(val id: String)

private object ProgramEntity : SyncableEntity<Program> {
    override val name: String = "program"
    override val serializer: KSerializer<Program> = Program.serializer()
    override fun clientIdOf(model: Program): String = model.id
    override fun serverIdOf(model: Program): String? = model.id
    override fun updatedAtOf(model: Program): String? = null
}

/**
 * **O `Keep` guarda o rascunho local da conta** (2.273.0). Caso de origem: App do Personal — a sessão
 * cai sem ação do aluno no meio do treino, e o treino em andamento (linha LIMPA de `LocalRepository`)
 * saía junto com o programa já sincronizado.
 */
class SyncAccountDataPurgerKeepDraftTest {

    private val agora = { "2026-10-09T10:00:00Z" }

    private suspend fun cenario(): FakeSyncStore {
        val store = FakeSyncStore()
        store.setAccountScope("aluno-a")
        LocalRepository(ActiveRunEntity, store, now = agora).put(ActiveRun("run-1", sets = 4))
        LocalRepository(ProgramEntity, store, now = agora).put(Program("prog-1"))
        return store
    }

    @Test
    fun `sem declarar nada o comportamento de antes continua - rascunho limpo sai`() = runTest {
        val store = cenario()

        val relatorio = SyncAccountDataPurger(store, clearSharedFiles = false)
            .purgeOnSignOut("aluno-a", SignOutPendingPolicy.Keep)

        assertTrue(relatorio.isComplete)
        assertEquals(2, relatorio.removedSyncedRows)
        assertNull(store.getByLocalId("active_run", "run-1"))
    }

    @Test
    fun `keepLocalEntities mantem o treino em andamento e apaga o sincronizado`() = runTest {
        val store = cenario()
        val purger = SyncAccountDataPurger(store, clearSharedFiles = false, keepLocalEntities = setOf("active_run"))

        val relatorio = purger.purgeOnSignOut("aluno-a", SignOutPendingPolicy.Keep)

        assertTrue(relatorio.isComplete)
        assertEquals(1, relatorio.removedSyncedRows, "só o programa sai; o rascunho não conta")
        assertNull(store.getByLocalId("program", "prog-1"))
        assertNotNull(store.getByLocalId("active_run", "run-1"))
        assertEquals(4, LocalRepository(ActiveRunEntity, store).get("run-1")?.sets)
    }

    @Test
    fun `rascunho mantido fica isolado na conta e volta quando ela entra de novo`() = runTest {
        val store = cenario()
        SyncAccountDataPurger(store, clearSharedFiles = false, keepLocalEntities = setOf("active_run"))
            .purgeOnSignOut("aluno-a", SignOutPendingPolicy.Keep)

        store.setAccountScope("aluno-b")
        assertTrue(LocalRepository(ActiveRunEntity, store).getAll().isEmpty(), "outra conta não vê")

        store.setAccountScope("aluno-a")
        assertNotNull(LocalRepository(ActiveRunEntity, store).get("run-1"))
    }

    @Test
    fun `keepLocalRow mantem linha avulsa`() = runTest {
        val store = cenario()
        LocalRepository(ActiveRunEntity, store, now = agora).put(ActiveRun("run-velho", sets = 1))
        val purger = SyncAccountDataPurger(
            store,
            clearSharedFiles = false,
            keepLocalRow = { it.entity == "active_run" && it.local_id == "run-1" },
        )

        purger.purgeOnSignOut("aluno-a", SignOutPendingPolicy.Keep)

        assertNotNull(store.getByLocalId("active_run", "run-1"))
        assertNull(store.getByLocalId("active_run", "run-velho"))
        assertNull(store.getByLocalId("program", "prog-1"))
    }

    @Test
    fun `discard e exclusao de conta apagam o rascunho mesmo declarado`() = runTest {
        val discard = cenario()
        SyncAccountDataPurger(discard, clearSharedFiles = false, keepLocalEntities = setOf("active_run"))
            .purgeOnSignOut("aluno-a", SignOutPendingPolicy.Discard)
        assertNull(discard.getByLocalId("active_run", "run-1"))

        val exclusao = cenario()
        SyncAccountDataPurger(exclusao, clearSharedFiles = false, keepLocalEntities = setOf("active_run"))
            .purgeAccount("aluno-a")
        assertNull(exclusao.getByLocalId("active_run", "run-1"))
    }

    @Test
    fun `predicado com defeito conta falha e nao apaga nada do espelho`() = runTest {
        val store = cenario()
        val purger = SyncAccountDataPurger(store, clearSharedFiles = false, keepLocalRow = { error("bug") })

        val relatorio = purger.purgeOnSignOut("aluno-a", SignOutPendingPolicy.Keep)

        assertFalse(relatorio.isComplete)
        assertEquals(1, relatorio.failures)
        assertNotNull(store.getByLocalId("active_run", "run-1"))
        assertNotNull(store.getByLocalId("program", "prog-1"))
    }
}
