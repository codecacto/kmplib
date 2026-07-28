package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.sync.FakeSyncStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Serializable
private data class Grupo(val id: String, val nome: String)

private object GrupoCrud : RestCrudEntity<Grupo> {
    override val name = "grupo"
    override val serializer: KSerializer<Grupo> = Grupo.serializer()
    override fun idOf(model: Grupo) = model.id
    override fun encodeBody(model: Grupo) = Json.encodeToString(Grupo.serializer(), model)
    override fun decodeModel(body: String) = Json.decodeFromString(Grupo.serializer(), body)
    override fun decodeList(body: String) = Json.decodeFromString(ListSerializer(Grupo.serializer()), body)
    override fun withLocalId(model: Grupo, clientId: String) = model.copy(id = clientId)
}

@Serializable
private data class Item(val id: String, val grupoId: String, val nome: String)

private object ItemCrud : RestCrudEntity<Item> {
    override val name = "item"
    override val serializer: KSerializer<Item> = Item.serializer()
    override fun idOf(model: Item) = model.id
    override fun encodeBody(model: Item) = Json.encodeToString(Item.serializer(), model)
    override fun decodeModel(body: String) = Json.decodeFromString(Item.serializer(), body)
    override fun decodeList(body: String) = Json.decodeFromString(ListSerializer(Item.serializer()), body)
    override fun withLocalId(model: Item, clientId: String) = model.copy(id = clientId)
}

/**
 * **GAP-KL-M-RESTCRUD-HANDLESET** — correlacionar filhos por CONJUNTO DE HANDLES, nunca por
 * igualdade de id.
 *
 * O exemplo `it.paiId == paiId` era o que a própria lib documentava, e é **incorreto sempre que o
 * drain puder ser interrompido** — que é o caso default (`RestFailureClass.Offline` aborta o ciclo).
 * Interrompido o drain, filhos já migrados (FK = id do servidor) e filhos ainda locais (FK = id
 * local) **convivem na mesma lista**: comparar por igualdade contra qualquer um dos dois derruba a
 * outra metade, e "sumiu da lista" é registro que não aparece onde deveria.
 */
class RestHandleCorrelationTest {

    private val jsonHeader = headersOf("Content-Type", "application/json")

    /** Backend que aceita tudo e atribui o próximo id da fila. */
    private class Backend {
        var online = true
        val idsDeSaida = ArrayDeque<String>()
    }

    private fun api(backend: Backend) = DomainApiClient(
        HttpClient(
            MockEngine { request ->
                if (!backend.online) throw RuntimeException("sem rede")
                val corpo = (request.body as? io.ktor.http.content.TextContent)?.text ?: ""
                val novo = backend.idsDeSaida.removeFirstOrNull() ?: "srv-?"
                respond(
                    corpo.replaceFirst(Regex("\"id\":\"[^\"]*\""), "\"id\":\"$novo\""),
                    HttpStatusCode.OK,
                    jsonHeader,
                )
            },
        ),
        DomainTokenProvider { "tok" },
        "https://api.example.com",
    )

    private fun grupoRepo(b: Backend, store: FakeSyncStore) =
        OfflineFirstRestRepository(api(b), GrupoCrud, store, "/v1/grupos", writeMode = RestWriteMode.LocalFirst)

    private fun itemRepo(b: Backend, store: FakeSyncStore) =
        OfflineFirstRestRepository(api(b), ItemCrud, store, "/v1/itens", writeMode = RestWriteMode.LocalFirst)

    /**
     * Monta o estado real que quebra o `==`: um grupo criado offline, três itens filhos, e um drain
     * **interrompido** depois do primeiro item — o grupo e o item A já migraram, os itens B e C não.
     *
     * @return o handle do grupo (o id local, como a tela o carrega desde a navegação).
     */
    private suspend fun cenarioDrainInterrompido(
        backend: Backend,
        grupos: OfflineFirstRestRepository<Grupo>,
        itens: OfflineFirstRestRepository<Item>,
    ): String {
        backend.online = false
        val grupoHandle = (grupos.create(Grupo("", "Turma")) as DomainResult.Success).data.id
        listOf("A", "B", "C").forEach { itens.create(Item("", grupoHandle, it)) }

        backend.online = true
        backend.idsDeSaida += "srv-grupo"
        grupos.drainOutbox(emptyMap())

        // O drain dos itens sobe só o primeiro e a rede cai no meio.
        backend.idsDeSaida += "srv-item-A"
        val fila = itens.mirror.drainableRows()
        val primeiro = fila.first()
        val modelo = itens.mirror.decode(primeiro.payload_json)!!
        itens.mirror.markSynced(primeiro.local_id, modelo.copy(id = "srv-item-A", grupoId = "srv-grupo"))
        return grupoHandle
    }

    // -- handlesOf ---------------------------------------------------------

    @Test
    fun `handlesOf resolve nos DOIS sentidos (local a partir do servidor e vice-versa)`() = runTest {
        val store = FakeSyncStore()
        val backend = Backend().apply { idsDeSaida += "srv-1" }
        val repo = grupoRepo(backend, store)

        backend.online = false
        val local = (repo.create(Grupo("", "Turma")) as DomainResult.Success).data.id
        assertEquals(setOf(local), repo.ids.handlesOf(local)) // ainda não migrou

        backend.online = true
        repo.drainOutbox(emptyMap())

        assertEquals(setOf(local, "srv-1"), repo.ids.handlesOf(local))
        assertEquals(setOf(local, "srv-1"), repo.ids.handlesOf("srv-1"))
    }

    @Test
    fun `handlesOf de id em branco e vazio, e de id desconhecido e ele mesmo`() {
        val ids = RestIdResolver(FakeSyncStore())
        assertTrue(ids.handlesOf("").isEmpty())
        assertEquals(setOf("qualquer"), ids.handlesOf("qualquer"))
        assertTrue(ids.handlesOf(emptyList()).isEmpty())
    }

    // -- O defeito que o exemplo da doc induzia ----------------------------

    @Test
    fun `com o drain interrompido, igualdade derruba METADE da lista - handles nao`() = runTest {
        val store = FakeSyncStore()
        val backend = Backend()
        val grupos = grupoRepo(backend, store)
        val itens = itemRepo(backend, store)
        val handle = cenarioDrainInterrompido(backend, grupos, itens)

        val todos = itens.getAllCached()
        assertEquals(3, todos.size)

        // O que a doc mandava fazer: comparar a FK com o id canônico do pai.
        val canonico = grupos.canonicalId(handle)
        assertEquals("srv-grupo", canonico)
        assertEquals(1, todos.count { it.grupoId == canonico }) // ← só o item que já migrou
        assertEquals(2, todos.count { it.grupoId == handle }) // ← e os outros dois "somem"

        // O certo: conjunto de handles.
        val handles = grupos.ids.handlesOf(handle)
        assertEquals(3, todos.count { it.grupoId in handles })
    }

    @Test
    fun `observeChildren mantem a lista completa com o drain interrompido`() = runTest {
        val store = FakeSyncStore()
        val backend = Backend()
        val grupos = grupoRepo(backend, store)
        val itens = itemRepo(backend, store)
        val handle = cenarioDrainInterrompido(backend, grupos, itens)

        val filhos = grupos.observeChildren(handle, itens.observeAll()) { it.grupoId }.first()

        assertEquals(listOf("A", "B", "C"), filhos.map { it.nome }.sorted())
    }

    @Test
    fun `observeHandles emite o conjunto novo assim que o id migra`() = runTest {
        val store = FakeSyncStore()
        val backend = Backend().apply { idsDeSaida += "srv-1" }
        val repo = grupoRepo(backend, store)

        backend.online = false
        val local = (repo.create(Grupo("", "Turma")) as DomainResult.Success).data.id
        assertEquals(setOf(local), repo.observeHandles(local).first())

        backend.online = true
        repo.drainOutbox(emptyMap())
        assertEquals(setOf(local, "srv-1"), repo.observeHandles(local).first())
    }

    // -- Índice por handle (atributo do cadastro a partir de uma FK congelada) --

    @Test
    fun `indexByHandle acha o cadastro pelo id congelado na FK do filho`() = runTest {
        val store = FakeSyncStore()
        val backend = Backend()
        val grupos = grupoRepo(backend, store)
        val itens = itemRepo(backend, store)
        val handle = cenarioDrainInterrompido(backend, grupos, itens)

        val porHandle = grupos.ids.indexByHandle(grupos.getAllCached()) { it.id }

        // O mapa responde tanto pelo id local (congelado na navegação) quanto pelo do servidor.
        assertEquals("Turma", porHandle[handle]?.nome)
        assertEquals("Turma", porHandle["srv-grupo"]?.nome)
    }

    // -- Agrupamento / contagem -------------------------------------------

    @Test
    fun `groupByRef conta e agrupa aceitando qualquer handle do pai`() = runTest {
        val store = FakeSyncStore()
        val backend = Backend()
        val grupos = grupoRepo(backend, store)
        val itens = itemRepo(backend, store)
        val handle = cenarioDrainInterrompido(backend, grupos, itens)

        val porGrupo = grupos.ids.groupByRef(itens.getAllCached()) { it.grupoId }

        assertEquals(3, porGrupo.count(handle)) // pelo handle antigo da tela
        assertEquals(3, porGrupo.count("srv-grupo")) // e pelo id do servidor
        assertContentEquals(listOf("A", "B", "C"), porGrupo[handle].map { it.nome }.sorted())
        assertEquals(mapOf("srv-grupo" to 3), porGrupo.countByCanonicalId())
        assertEquals(0, porGrupo.count("grupo-inexistente"))
        assertTrue(porGrupo[""].isEmpty())
    }

    @Test
    fun `groupByRef ignora filho sem pai`() {
        val ids = RestIdResolver(FakeSyncStore())
        val grupos = ids.groupByRef(
            listOf(Item("1", "p", "A"), Item("2", "", "orfao")),
        ) { it.grupoId }
        assertEquals(1, grupos.count("p"))
        assertEquals(setOf("p"), grupos.canonicalIds)
    }

    // -- O operador de fluxo resolve FORA do contexto do coletor ------------

    @Test
    fun `resolvingIds resolve no dispatcher do resolvedor, nao no do coletor`() = runTest {
        val marcador = DispatcherEspiao(Dispatchers.Default)
        val ids = RestIdResolver(FakeSyncStore(), dispatcher = marcador)

        val indexado = flowOf(listOf(Item("i1", "p1", "A")))
            .resolvingIds(ids) { lista -> indexByHandle(lista) { it.id } }
            .first()

        assertEquals("A", indexado["i1"]?.nome)
        assertTrue(marcador.usado, "a resolução tem de sair do contexto do coletor (é leitura de banco)")
    }

    /** Dispatcher que só registra que foi usado e delega. */
    private class DispatcherEspiao(private val delegado: CoroutineDispatcher) : CoroutineDispatcher() {
        var usado = false
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            usado = true
            delegado.dispatch(context, block)
        }
    }

    // -- Regressão: `same` continua valendo para comparar DOIS ids ----------

    @Test
    fun `same continua correto (a diferenca e o custo, nao o resultado)`() = runTest {
        val store = FakeSyncStore()
        val backend = Backend().apply { idsDeSaida += "srv-1" }
        val repo = grupoRepo(backend, store)
        backend.online = false
        val local = (repo.create(Grupo("", "Turma")) as DomainResult.Success).data.id
        backend.online = true
        repo.drainOutbox(emptyMap())

        assertTrue(repo.ids.same(local, "srv-1"))
        assertTrue(repo.ids.same("srv-1", local))
        assertTrue(!repo.ids.same(local, "outro"))
    }

    @Test
    fun `handles de varios pais somam (filtrar filhos de um conjunto)`() = runTest {
        val store = FakeSyncStore()
        val backend = Backend().apply { idsDeSaida += "srv-1"; idsDeSaida += "srv-2" }
        val repo = grupoRepo(backend, store)
        backend.online = false
        val a = (repo.create(Grupo("", "A")) as DomainResult.Success).data.id
        val b = (repo.create(Grupo("", "B")) as DomainResult.Success).data.id
        backend.online = true
        repo.drainOutbox(emptyMap())

        assertEquals(setOf(a, b, "srv-1", "srv-2"), repo.ids.handlesOf(listOf(a, b)))
    }

    /** Guarda: o escopo de conta continua isolando o remap (o handle de A não vale sob B). */
    @Test
    fun `handlesOf respeita o escopo de conta`() = runTest {
        val store = FakeSyncStore()
        store.setAccountScope("conta-A")
        val backend = Backend().apply { idsDeSaida += "srv-1" }
        val repo = grupoRepo(backend, store)
        backend.online = false
        val local = (repo.create(Grupo("", "Turma")) as DomainResult.Success).data.id
        backend.online = true
        repo.drainOutbox(emptyMap())
        assertEquals(setOf(local, "srv-1"), repo.ids.handlesOf(local))

        store.setAccountScope("conta-B")
        assertEquals(setOf(local), repo.ids.handlesOf(local))
    }
}
