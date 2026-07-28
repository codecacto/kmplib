package br.com.codecacto.kmplib.sync

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Modelo de teste de um app 100% local (arquétipo A). */
@Serializable
private data class Note(val id: String, val text: String, val updatedAt: String? = null)

private object NoteEntity : SyncableEntity<Note> {
    override val name: String = "note"
    override val serializer: KSerializer<Note> = Note.serializer()
    override fun clientIdOf(model: Note): String = model.id
    override fun serverIdOf(model: Note): String? = null
    override fun updatedAtOf(model: Note): String? = model.updatedAt
}

class LocalRepositoryTest {

    private fun repo(store: SyncStore = FakeSyncStore()) =
        LocalRepository(NoteEntity, store, now = { "2026-06-27T00:00:00Z" })

    @Test
    fun `put grava limpo e get le de volta`() = runTest {
        val store = FakeSyncStore()
        val repo = repo(store)
        repo.put(Note("a", "primeira"))

        assertEquals("primeira", repo.get("a")?.text)
        // Local-only NUNCA enfileira push: nada sujo na outbox.
        assertEquals(0L, store.countDirty())
    }

    @Test
    fun `put com mesmo clientId atualiza no lugar`() = runTest {
        val repo = repo()
        repo.put(Note("a", "v1"))
        repo.put(Note("a", "v2"))

        assertEquals("v2", repo.get("a")?.text)
        assertEquals(1, repo.getAll().size)
    }

    @Test
    fun `getAll e observeAll refletem os registros`() = runTest {
        val repo = repo()
        repo.putAll(listOf(Note("a", "A"), Note("b", "B")))

        assertEquals(setOf("A", "B"), repo.getAll().map { it.text }.toSet())
        assertEquals(2, repo.observeAll().first().size)
    }

    @Test
    fun `delete remove o registro`() = runTest {
        val repo = repo()
        repo.put(Note("a", "A"))
        repo.delete("a")

        assertNull(repo.get("a"))
        assertTrue(repo.getAll().isEmpty())
    }

    @Test
    fun `clear remove todos da entidade`() = runTest {
        val repo = repo()
        repo.putAll(listOf(Note("a", "A"), Note("b", "B")))
        repo.clear()

        assertTrue(repo.getAll().isEmpty())
    }

    @Test
    fun `payload corrompido nao quebra a leitura`() = runTest {
        val store = FakeSyncStore()
        store.upsert(
            br.com.codecacto.kmplib.sync.db.Synced_entity(
                account_id = "",
                entity = "note",
                local_id = "x",
                server_id = null,
                client_id = "x",
                payload_json = "{ nao eh json valido",
                updated_at = "2026-06-27T00:00:00Z",
                dirty = 0L,
                pending_op = null,
                deleted = 0L,
                base_updated_at = null,
                last_error = null,
                failed = 0L,
                fail_code = null,
                attempts = 0L,
            ),
        )
        val repo = repo(store)
        assertNull(repo.get("x"))
        assertTrue(repo.getAll().isEmpty())
    }
}
