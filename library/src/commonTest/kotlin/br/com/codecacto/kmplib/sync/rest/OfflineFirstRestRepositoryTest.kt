package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.sync.FakeSyncStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private data class Empresa(val id: String, val nome: String, val ownerId: String = "")

private object EmpresaCrud : RestCrudEntity<Empresa> {
    override val name = "empresa"
    override val serializer: KSerializer<Empresa> = Empresa.serializer()
    override fun idOf(model: Empresa) = model.id
    override fun encodeBody(model: Empresa) = Json.encodeToString(Empresa.serializer(), model)
    override fun decodeModel(body: String) = Json.decodeFromString(Empresa.serializer(), body)
    override fun decodeList(body: String) =
        Json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Empresa.serializer()), body)
    override fun withLocalId(model: Empresa, clientId: String) = model.copy(id = clientId)
}

@Serializable
private data class Lancamento(val id: String, val empresaId: String, val horas: Int)

private object LancamentoCrud : RestCrudEntity<Lancamento> {
    override val name = "lancamento"
    override val serializer: KSerializer<Lancamento> = Lancamento.serializer()
    override fun idOf(model: Lancamento) = model.id
    override fun encodeBody(model: Lancamento) = Json.encodeToString(Lancamento.serializer(), model)
    override fun decodeModel(body: String) = Json.decodeFromString(Lancamento.serializer(), body)
    override fun decodeList(body: String) =
        Json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Lancamento.serializer()), body)
    override fun withLocalId(model: Lancamento, clientId: String) = model.copy(id = clientId)
    // Remapeia a FK empresaId (clientId local → serverId) antes do push.
    override fun remapRefs(model: Lancamento, remap: Map<String, String>) =
        model.copy(empresaId = remap[model.empresaId] ?: model.empresaId)
}

/** Envelope de paginação do contrato REST-CRUD central: `{ data, page, size, total }`. */
@Serializable
private data class EmpresaPage(val data: List<Empresa>, val page: Int, val size: Int, val total: Long)

/** Descritor que decodifica o **envelope paginado** (expõe page/size/total via [RestCrudEntity.decodePage]). */
private object EmpresaPagedCrud : RestCrudEntity<Empresa> {
    override val name = "empresa"
    override val serializer: KSerializer<Empresa> = Empresa.serializer()
    override fun idOf(model: Empresa) = model.id
    override fun encodeBody(model: Empresa) = Json.encodeToString(Empresa.serializer(), model)
    override fun decodeModel(body: String) = Json.decodeFromString(Empresa.serializer(), body)
    override fun decodeList(body: String) = Json.decodeFromString(EmpresaPage.serializer(), body).data
    override fun decodePage(body: String): RestPage<Empresa> {
        val env = Json.decodeFromString(EmpresaPage.serializer(), body)
        return RestPage(env.data, env.page, env.size, env.total)
    }
    override fun withLocalId(model: Empresa, clientId: String) = model.copy(id = clientId)
}

class OfflineFirstRestRepositoryTest {

    private val jsonHeader = headersOf("Content-Type", "application/json")

    /**
     * Repo contra um backend paginado: fatia [dataset] pelos parâmetros `?page=&size=` e responde o
     * envelope `{data,page,size,total}`. `getCount`/`postCount` observáveis para assertar o nº de GETs.
     */
    private class PagedBackend(var dataset: List<Empresa>) {
        var getCount = 0
    }

    private fun pagedRepo(backend: PagedBackend, store: FakeSyncStore, pageSize: Int): OfflineFirstRestRepository<Empresa> =
        OfflineFirstRestRepository(
            api = DomainApiClient(
                HttpClient(
                    MockEngine { request ->
                        backend.getCount++
                        val page = request.url.parameters["page"]?.toIntOrNull() ?: 1
                        val size = request.url.parameters["size"]?.toIntOrNull() ?: 20
                        val all = backend.dataset
                        val from = (page - 1) * size
                        val slice = if (from >= all.size) emptyList() else all.subList(from, minOf(from + size, all.size))
                        val itemsJson = slice.joinToString(",") { Json.encodeToString(Empresa.serializer(), it) }
                        val body = """{"data":[$itemsJson],"page":$page,"size":$size,"total":${all.size}}"""
                        respond(body, HttpStatusCode.OK, jsonHeader)
                    },
                ),
                DomainTokenProvider { "tok" },
                "https://api.example.com",
            ),
            descriptor = EmpresaPagedCrud,
            store = store,
            collectionPath = "/v1/empresas",
            refreshPageSize = pageSize,
        )

    private fun empresas(n: Int): List<Empresa> = (1..n).map { Empresa(id = "e$it", nome = "Empresa $it") }

    @Test
    fun `refresh pagina o dataset COMPLETO (N maior que size) e preserva TODOS os itens`() = runTest {
        val store = FakeSyncStore()
        val backend = PagedBackend(empresas(25)) // 25 itens, size 10 -> 3 páginas (10,10,5)
        val r = pagedRepo(backend, store, pageSize = 10)

        assertTrue(r.refresh())

        // Todos os 25 no espelho — inclusive muito além da 1ª página (defeito B1: seriam apagados).
        assertEquals(25, r.getAllCached().size)
        assertEquals("Empresa 25", r.getCached("e25")?.nome)
        assertEquals("Empresa 11", r.getCached("e11")?.nome)
        assertEquals(3, backend.getCount) // parou na página 3 (incompleta), sem GET extra
    }

    @Test
    fun `refresh so apaga o item genuinamente removido no servidor (nao a pagina 2+)`() = runTest {
        val store = FakeSyncStore()
        val backend = PagedBackend(empresas(25))
        val r = pagedRepo(backend, store, pageSize = 10)
        assertTrue(r.refresh())
        assertEquals(25, r.getAllCached().size)

        // Servidor removeu 'e12' (que vive na 2ª página). Novo refresh deve apagar SÓ ele.
        backend.dataset = empresas(25).filterNot { it.id == "e12" }
        assertTrue(r.refresh())

        assertEquals(24, r.getAllCached().size)
        assertNull(r.getCached("e12"))         // genuinamente removido
        assertEquals("Empresa 20", r.getCached("e20")?.nome) // página 2+ preservada
        assertEquals("Empresa 25", r.getCached("e25")?.nome)
    }

    @Test
    fun `refresh sem paginacao (total menor ou igual a size) continua funcionando (regressao)`() = runTest {
        val store = FakeSyncStore()
        val backend = PagedBackend(empresas(5)) // total 5 <= size 10 -> 1 página
        val r = pagedRepo(backend, store, pageSize = 10)

        assertTrue(r.refresh())

        assertEquals(5, r.getAllCached().size)
        assertEquals(1, backend.getCount) // parada imediata na página incompleta
    }

    @Test
    fun `refresh preserva edicao local pendente (dirty) durante a reconciliacao paginada`() = runTest {
        val store = FakeSyncStore()
        val backend = PagedBackend(empresas(25))
        val r = pagedRepo(backend, store, pageSize = 10)
        assertTrue(r.refresh())

        // Edita 'e05' offline (fica dirty na outbox) e depois refaz o refresh.
        backend.dataset = empresas(25).map { if (it.id == "e5") it.copy(nome = "SERVIDOR") else it }
        r.mirror.putDirty(Empresa(id = "e5", nome = "LOCAL EDIT"), br.com.codecacto.kmplib.sync.SyncOpType.UPDATE)
        assertTrue(r.refresh())

        // A edição local vence: reconcile não sobrescreve linha dirty.
        assertEquals("LOCAL EDIT", r.getCached("e5")?.nome)
        assertEquals(1, store.getDirty("empresa").size)
    }

    @Test
    fun `refreshPage faz upsert-only sem apagar itens de outras paginas`() = runTest {
        val store = FakeSyncStore()
        val backend = PagedBackend(empresas(25))
        val r = pagedRepo(backend, store, pageSize = 10)

        // Carrega só a página 2 (server-side pura) — não deve apagar nada, nem exigir as outras páginas.
        val res = r.refreshPage(page = 2, size = 10)
        assertTrue(res is DomainResult.Success)
        val restPage = (res as DomainResult.Success).data
        assertEquals(10, restPage.items.size)
        assertEquals(25L, restPage.total)
        assertEquals(true, restPage.hasNextPage) // página 2 de 3

        // Só os 10 itens da página 2 no espelho; upsert-only não removeu nada de fora dela.
        assertEquals(10, r.getAllCached().size)
        assertEquals("Empresa 11", r.getCached("e11")?.nome)
        assertNull(r.getCached("e1")) // página 1 não foi carregada (e não foi apagada indevidamente)
    }

    private class Backend {
        var online = true
        var nextServerId = "srv-1"
        val posted = mutableListOf<String>()
        val list = mutableListOf<String>() // corpos de lista a devolver no GET coleção
    }

    private fun <T : Any> repo(descriptor: RestCrudEntity<T>, collection: String, backend: Backend, store: FakeSyncStore) =
        OfflineFirstRestRepository(
            api = DomainApiClient(
                HttpClient(
                    MockEngine { request ->
                        if (!backend.online) throw RuntimeException("offline")
                        val method = request.method.value
                        val path = request.url.encodedPath
                        when {
                            method == "POST" -> {
                                val body = (request.body as? io.ktor.http.content.TextContent)?.text ?: ""
                                backend.posted += body
                                // devolve o mesmo objeto com id do servidor
                                val withId = body.replaceFirst(Regex("\"id\":\"[^\"]*\""), "\"id\":\"${backend.nextServerId}\"")
                                respond(withId, HttpStatusCode.Created, jsonHeader)
                            }
                            method == "PUT" -> {
                                val body = (request.body as? io.ktor.http.content.TextContent)?.text ?: ""
                                respond(body, HttpStatusCode.OK, jsonHeader)
                            }
                            method == "DELETE" -> respond("", HttpStatusCode.NoContent, jsonHeader)
                            method == "GET" && backend.list.isNotEmpty() ->
                                respond(backend.list.removeAt(0), HttpStatusCode.OK, jsonHeader)
                            else -> respond("[]", HttpStatusCode.OK, jsonHeader)
                        }
                    },
                ),
                DomainTokenProvider { "tok" },
                "https://api.example.com",
            ),
            descriptor = descriptor,
            store = store,
            collectionPath = collection,
        )

    @Test
    fun `create online grava LIMPO com id do servidor`() = runTest {
        val store = FakeSyncStore()
        val backend = Backend().apply { nextServerId = "srv-9" }
        val r = repo(EmpresaCrud, "/v1/empresas", backend, store)
        val res = r.create(Empresa(id = "", nome = "Acme"))
        assertTrue(res is DomainResult.Success)
        assertEquals("srv-9", (res as DomainResult.Success).data.id)
        // Sem pendências na outbox.
        assertTrue(store.getDirty("empresa").isEmpty())
        assertEquals("Acme", r.getCached("srv-9")?.nome)
    }

    @Test
    fun `create offline grava SUJO na outbox com id local`() = runTest {
        val store = FakeSyncStore()
        val backend = Backend().apply { online = false }
        val r = repo(EmpresaCrud, "/v1/empresas", backend, store)
        val res = r.create(Empresa(id = "", nome = "Beta"))
        assertTrue(res is DomainResult.Success)
        val local = (res as DomainResult.Success).data
        assertTrue(local.id.startsWith("local-"))
        val dirty = store.getDirty("empresa")
        assertEquals(1, dirty.size)
        assertEquals("create", dirty.first().pending_op)
    }

    @Test
    fun `refresh reconcilia o espelho a partir do GET de lista`() = runTest {
        val store = FakeSyncStore()
        val backend = Backend()
        backend.list += """[{"id":"a","nome":"A"},{"id":"b","nome":"B"}]"""
        val r = repo(EmpresaCrud, "/v1/empresas", backend, store)
        assertTrue(r.refresh())
        assertEquals(2, r.getAllCached().size)
    }

    @Test
    fun `drainOutbox sobe create e remapeia FK do filho`() = runTest {
        val store = FakeSyncStore()

        // 1) empresa criada offline (id local).
        val empresaBackend = Backend().apply { online = false }
        val empresaRepo = repo(EmpresaCrud, "/v1/empresas", empresaBackend, store)
        val empresaLocalId = (empresaRepo.create(Empresa(id = "", nome = "Org")) as DomainResult.Success).data.id

        // 2) lançamento criado offline referenciando o clientId local da empresa.
        val lancBackend = Backend().apply { online = false }
        val lancRepo = repo(LancamentoCrud, "/v1/lancamentos", lancBackend, store)
        lancRepo.create(Lancamento(id = "", empresaId = empresaLocalId, horas = 8))

        // 3) volta a rede: drena empresa (gera remap) e passa o remap ao filho.
        empresaBackend.online = true; empresaBackend.nextServerId = "emp-SRV"
        lancBackend.online = true; lancBackend.nextServerId = "lan-SRV"
        val remap = empresaRepo.drainOutbox(emptyMap())
        assertEquals("emp-SRV", remap[empresaLocalId])
        lancRepo.drainOutbox(remap)

        // O corpo POST do lançamento já saiu com a FK remapeada para o serverId da empresa.
        assertTrue(lancBackend.posted.single().contains("\"empresaId\":\"emp-SRV\""))
        // Outbox vazia após drenar.
        assertTrue(store.getDirty("empresa").isEmpty())
        assertTrue(store.getDirty("lancamento").isEmpty())
    }

    @Test
    fun `delete offline vira tombstone e replay confirma no reconnect`() = runTest {
        val store = FakeSyncStore()
        val backend = Backend()
        // cria online.
        val repo = repo(EmpresaCrud, "/v1/empresas", backend, store).also {
            it.create(Empresa(id = "", nome = "X"))
        }
        // fica offline e deleta.
        backend.online = false
        val res = repo.delete("srv-1")
        assertTrue(res is DomainResult.Success)
        assertEquals(1, store.getDirty("empresa").size)
        assertEquals("delete", store.getDirty("empresa").first().pending_op)
        // reconecta e drena.
        backend.online = true
        repo.drainOutbox(emptyMap())
        assertTrue(store.getVisible("empresa").isEmpty())
    }

    @Test
    fun `getById cai no cache antes da rede`() = runTest {
        val store = FakeSyncStore()
        val backend = Backend()
        val repo = repo(EmpresaCrud, "/v1/empresas", backend, store)
        repo.create(Empresa(id = "", nome = "Cache")) // grava srv-1 limpo
        backend.online = false // se fosse à rede, falharia
        val res = repo.getById("srv-1")
        assertTrue(res is DomainResult.Success)
        assertEquals("Cache", (res as DomainResult.Success).data?.nome)
    }

    @Test
    fun `create com 402 propaga Quota sem gravar`() = runTest {
        val store = FakeSyncStore()
        val quotaBody = """{"error":{"details":{"feature":"multi_empresa","limite":"1","contagem":"1"}}}"""
        val api = DomainApiClient(
            HttpClient(MockEngine { respond(quotaBody, HttpStatusCode.PaymentRequired, jsonHeader) }),
            DomainTokenProvider { "tok" },
            "https://api.example.com",
        )
        val repo = OfflineFirstRestRepository(api, EmpresaCrud, store, "/v1/empresas")
        val res = repo.create(Empresa(id = "", nome = "Blocked"))
        assertTrue(res is DomainResult.Quota)
        assertTrue(store.getVisible("empresa").isEmpty())
        assertNull(repo.getCached("x"))
    }
}
