package br.com.codecacto.kmplib.sync

import br.com.codecacto.kmplib.core.network.ConnectivityObserver
import br.com.codecacto.kmplib.sync.db.Synced_entity
import br.com.codecacto.kmplib.sync.rest.RestEntityMirror
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Os `observe*` dos repositórios emitem SÓ quando o resultado decodificado muda (2.335.2).
 *
 * O [FakeSyncStore] reproduz de propósito o comportamento do SQLDelight que causou o defeito:
 * QUALQUER gravação notifica TODOS os observadores (o SQLDelight notifica por tabela, e a
 * `synced_entity` é uma só para todas as entidades). Sem o `distinctUntilChanged` sobre o já
 * decodificado, cada gravação em outra entidade re-emitia a mesma lista — e o consumidor que reage
 * à emissão gravando algo (OnMore/App do Personal) fechava um laço de ~600 requisições por minuto.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveDistinctTest {

    @Serializable
    private data class Treino(val id: String, val nome: String, val updatedAt: String? = null)

    @Serializable
    private data class Serie(val id: String, val reps: Int)

    private object TreinoEntity : SyncableEntity<Treino> {
        override val name = "treino"
        override val serializer: KSerializer<Treino> = Treino.serializer()
        override fun clientIdOf(model: Treino) = model.id
        override fun serverIdOf(model: Treino): String? = null
        override fun updatedAtOf(model: Treino): String? = model.updatedAt
    }

    private object SerieEntity : SyncableEntity<Serie> {
        override val name = "serie"
        override val serializer: KSerializer<Serie> = Serie.serializer()
        override fun clientIdOf(model: Serie) = model.id
        override fun serverIdOf(model: Serie): String? = null
        override fun updatedAtOf(model: Serie): String? = null
    }

    /** Coleta tudo o que o fluxo emitir, de forma ansiosa (cada emissão conta). */
    private fun <T> TestScope.collectAll(flow: Flow<T>): List<T> {
        val out = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { out += it } }
        runCurrent()
        return out
    }

    /** Relógio que avança a cada gravação: regravar o mesmo modelo muda o `updated_at` da linha. */
    private fun relogio(): () -> String {
        var n = 0
        return { "2026-10-29T00:00:${(n++).toString().padStart(2, '0')}Z" }
    }

    // -- LocalRepository ---------------------------------------------------------------------

    @Test
    fun `LocalRepository observeAll nao re-emite com gravacao em outra entidade`() = runTest {
        val store = FakeSyncStore()
        val treinos = LocalRepository(TreinoEntity, store, now = relogio())
        val series = LocalRepository(SerieEntity, store, now = relogio())
        treinos.put(Treino("t1", "Peito"))

        val emissoes = collectAll(treinos.observeAll())
        assertEquals(1, emissoes.size)

        repeat(5) { i -> series.put(Serie("s$i", 10 + i)); runCurrent() }
        series.delete("s0"); runCurrent()

        assertEquals(1, emissoes.size, "gravação em outra entidade não pode re-emitir")
    }

    @Test
    fun `LocalRepository observeAll nao re-emite ao regravar conteudo identico`() = runTest {
        val store = FakeSyncStore()
        val treinos = LocalRepository(TreinoEntity, store, now = relogio())
        treinos.put(Treino("t1", "Peito"))

        val emissoes = collectAll(treinos.observeAll())
        repeat(3) { treinos.put(Treino("t1", "Peito")); runCurrent() }
        assertEquals(1, emissoes.size, "mesmo conteúdo (só o updated_at mudou) não pode re-emitir")

        treinos.put(Treino("t1", "Costas")); runCurrent()
        assertEquals(listOf("Peito", "Costas"), emissoes.map { it.single().nome })
    }

    @Test
    fun `LocalRepository observeById ignora outra entidade, outro registro e conteudo identico`() = runTest {
        val store = FakeSyncStore()
        val treinos = LocalRepository(TreinoEntity, store, now = relogio())
        val series = LocalRepository(SerieEntity, store, now = relogio())
        treinos.put(Treino("t1", "Peito"))

        val emissoes = collectAll(treinos.observeById("t1"))
        series.put(Serie("s1", 12)); runCurrent()
        treinos.put(Treino("t2", "Perna")); runCurrent()
        treinos.put(Treino("t1", "Peito")); runCurrent()
        assertEquals(1, emissoes.size)

        treinos.delete("t1"); runCurrent()
        assertEquals(listOf<String?>("Peito", null), emissoes.map { it?.nome })
    }

    // -- SyncableRepository ------------------------------------------------------------------

    @Test
    fun `SyncableRepository observeAll e observeById nao re-emitem sem mudanca do modelo`() = runTest {
        val store = FakeSyncStore()
        val repo = SyncableRepository(TreinoEntity, store, MotorParado)
        store.upsert(linha("treino", "t1", """{"id":"t1","nome":"Peito"}""", "2026-10-29T00:00:00Z"))

        val todos = collectAll(repo.observeAll())
        val um = collectAll(repo.observeById("t1"))
        store.upsert(linha("serie", "s1", """{"id":"s1","reps":10}""", "2026-10-29T00:00:01Z")); runCurrent()
        store.upsert(linha("treino", "t1", """{"id":"t1","nome":"Peito"}""", "2026-10-29T00:00:02Z")); runCurrent()
        assertEquals(1, todos.size)
        assertEquals(1, um.size)

        store.upsert(linha("treino", "t1", """{"id":"t1","nome":"Costas"}""", "2026-10-29T00:00:03Z")); runCurrent()
        assertEquals(2, todos.size)
        assertEquals("Costas", um.last()?.nome)
    }

    // -- RestEntityMirror --------------------------------------------------------------------

    private fun mirrors(store: SyncStore) =
        RestEntityMirror("treino", store, Treino.serializer(), { it.id }) to
            RestEntityMirror("serie", store, Serie.serializer(), { it.id })

    @Test
    fun `RestEntityMirror observeVisible e ById nao re-emitem com gravacao em outra entidade`() = runTest {
        val store = FakeSyncStore()
        val (treinos, series) = mirrors(store)
        treinos.putClean(Treino("t1", "Peito"), updatedAt = "2026-10-29T00:00:00Z")

        val todos = collectAll(treinos.observeVisible())
        val um = collectAll(treinos.observeVisibleById("t1"))
        val comEstado = collectAll(treinos.observeVisibleWithState())
        val umComEstado = collectAll(treinos.observeVisibleWithStateById("t1"))

        series.putDirty(Serie("s1", 10), SyncOpType.CREATE); runCurrent()
        series.putClean(Serie("s2", 8)); runCurrent()
        series.markFailed("s1", 422, "inválido"); runCurrent()
        series.removeHard("s2"); runCurrent()

        assertEquals(1, todos.size)
        assertEquals(1, um.size)
        assertEquals(1, comEstado.size)
        assertEquals(1, umComEstado.size)
    }

    @Test
    fun `RestEntityMirror observeVisible nao re-emite ao regravar conteudo identico`() = runTest {
        val store = FakeSyncStore()
        val (treinos, _) = mirrors(store)
        treinos.putClean(Treino("t1", "Peito"), updatedAt = "2026-10-29T00:00:00Z")

        val todos = collectAll(treinos.observeVisible())
        val um = collectAll(treinos.observeVisibleById("t1"))
        // O "pull que devolve o mesmo dado": mesma lista do servidor, só o carimbo mudou.
        treinos.putClean(Treino("t1", "Peito"), updatedAt = "2026-10-29T00:00:05Z"); runCurrent()
        treinos.reconcile(listOf(Treino("t1", "Peito"))); runCurrent()
        assertEquals(1, todos.size)
        assertEquals(1, um.size)

        treinos.putClean(Treino("t1", "Costas")); runCurrent()
        assertEquals(listOf("Peito", "Costas"), todos.map { it.single().nome })
        assertEquals(listOf("Peito", "Costas"), um.map { it?.nome })
    }

    @Test
    fun `observeVisibleWithState emite quando so o estado de envio muda`() = runTest {
        val store = FakeSyncStore()
        val (treinos, _) = mirrors(store)
        treinos.putDirty(Treino("t1", "Peito"), SyncOpType.CREATE)

        val comEstado = collectAll(treinos.observeVisibleWithState())
        val modelos = collectAll(treinos.observeVisible())
        treinos.markFailed("t1", 422, "inválido"); runCurrent()

        // O estado é parte do que a tela desenha: mudar Pending → Failed TEM de emitir…
        assertEquals(2, comEstado.size)
        assertEquals(true, comEstado.last().single().isFailed)
        // …mas quem observa só o modelo não vê nada de novo.
        assertEquals(1, modelos.size)
    }

    private fun linha(entity: String, id: String, payload: String, updatedAt: String) = Synced_entity(
        account_id = "",
        entity = entity,
        local_id = id,
        server_id = null,
        client_id = id,
        payload_json = payload,
        updated_at = updatedAt,
        dirty = 0L,
        pending_op = null,
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

    /** Motor que o teste nunca aciona: o `SyncableRepository` só lê do store aqui. */
    private object MotorParado : SyncEngine {
        override val state: StateFlow<SyncState> get() = error("não usado")
        override val queue: StateFlow<List<SyncQueueItem>> get() = error("não usado")
        override fun <T : Any> register(entity: SyncableEntity<T>) = Unit
        override suspend fun enqueue(entity: String, op: SyncOpType, model: Any) = error("não usado")
        override suspend fun push(): SyncResultSummary = error("não usado")
        override suspend fun pull(): SyncResultSummary = error("não usado")
        override suspend fun syncNow(): SyncResultSummary = error("não usado")
        override fun startAutoSync(connectivity: ConnectivityObserver) = Unit
        override fun stopAutoSync() = Unit
    }
}
