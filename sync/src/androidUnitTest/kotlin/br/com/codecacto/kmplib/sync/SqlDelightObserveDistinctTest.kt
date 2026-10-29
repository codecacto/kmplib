package br.com.codecacto.kmplib.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import br.com.codecacto.kmplib.sync.db.SyncDatabase
import br.com.codecacto.kmplib.sync.rest.RestEntityMirror
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O defeito no banco DE VERDADE (2.335.2): o SQLDelight notifica as consultas por TABELA, e todas as
 * entidades moram na mesma `synced_entity`. Antes da correção, cada gravação de qualquer entidade
 * re-emitia todos os `observe*` — e o consumidor que grava ao receber fechava um laço infinito.
 * Aqui roda o [SqlDelightSyncStore] sobre um SQLite em memória (JDBC), sem emulador.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SqlDelightObserveDistinctTest {

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

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { SyncDatabase.Schema.create(it) }

    @AfterTest
    fun fechar() = driver.close()

    private fun TestScope.store() = SqlDelightSyncStore(
        SyncDatabase(driver),
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    private fun <T> TestScope.collectAll(flow: Flow<T>): List<T> {
        val out = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { out += it } }
        advanceUntilIdle()
        return out
    }

    private fun relogio(): () -> String {
        var n = 0
        return { "2026-10-29T00:00:${(n++).toString().padStart(2, '0')}Z" }
    }

    @Test
    fun `store nao re-emite a linha de uma entidade quando outra entidade e gravada`() = runTest {
        val store = store()
        val treinos = LocalRepository(TreinoEntity, store, now = relogio())
        val series = LocalRepository(SerieEntity, store, now = relogio())
        treinos.put(Treino("t1", "Peito"))

        val linhas = collectAll(store.observeVisible("treino"))
        val linha = collectAll(store.observeVisibleById("treino", "t1"))
        val porHandle = collectAll(store.observeVisibleByHandle("treino", "t1"))
        repeat(5) { i -> series.put(Serie("s$i", i)); advanceUntilIdle() }

        assertEquals(1, linhas.size)
        assertEquals(1, linha.size)
        assertEquals(1, porHandle.size)
    }

    @Test
    fun `LocalRepository sobre SQLite nao entra em laco quando o consumidor grava ao receber`() = runTest {
        val store = store()
        val treinos = LocalRepository(TreinoEntity, store, now = relogio())
        val series = LocalRepository(SerieEntity, store, now = relogio())
        treinos.put(Treino("t1", "Peito"))

        // O consumidor do OnMore: a cada emissão de treinos, grava um "cache" em OUTRA entidade
        // e regrava o próprio treino com o mesmo conteúdo. Antes: laço sem fim.
        var gravacoes = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            treinos.observeAll().collect { lista ->
                gravacoes++
                if (gravacoes > 50) error("laço: o observeAll re-emitiu a cada gravação")
                series.put(Serie("cache", lista.size))
                treinos.put(lista.single().copy())
            }
        }
        advanceUntilIdle()
        assertEquals(1, gravacoes)

        treinos.put(Treino("t1", "Costas")); advanceUntilIdle()
        assertEquals(2, gravacoes, "mudança de verdade continua chegando")
    }

    @Test
    fun `RestEntityMirror sobre SQLite ignora outra entidade e conteudo identico`() = runTest {
        val store = store()
        val treinos = RestEntityMirror("treino", store, Treino.serializer(), { it.id })
        val series = RestEntityMirror("serie", store, Serie.serializer(), { it.id })
        treinos.putClean(Treino("t1", "Peito"), updatedAt = "2026-10-29T00:00:00Z")

        val todos = collectAll(treinos.observeVisible())
        val um = collectAll(treinos.observeVisibleById("t1"))
        series.putDirty(Serie("s1", 10), SyncOpType.CREATE); advanceUntilIdle()
        treinos.putClean(Treino("t1", "Peito"), updatedAt = "2026-10-29T00:00:09Z"); advanceUntilIdle()
        assertEquals(1, todos.size)
        assertEquals(1, um.size)

        treinos.putClean(Treino("t1", "Costas")); advanceUntilIdle()
        assertEquals(listOf("Peito", "Costas"), todos.map { it.single().nome })
        assertEquals(listOf("Peito", "Costas"), um.map { it?.nome })
    }
}
