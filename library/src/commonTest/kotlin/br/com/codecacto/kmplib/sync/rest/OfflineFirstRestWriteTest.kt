package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.sync.FakeSyncStore
import br.com.codecacto.kmplib.sync.SyncOpType
import br.com.codecacto.kmplib.sync.db.Synced_entity
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private data class Marcacao(val id: String, val nome: String, val embarcadoEm: String? = null)

private object MarcacaoCrud : RestCrudEntity<Marcacao> {
    override val name = "marcacao"
    override val serializer: KSerializer<Marcacao> = Marcacao.serializer()
    override fun idOf(model: Marcacao) = model.id
    override fun encodeBody(model: Marcacao) = Json.encodeToString(Marcacao.serializer(), model)
    override fun decodeModel(body: String) = Json.decodeFromString(Marcacao.serializer(), body)
    override fun decodeList(body: String) = Json.decodeFromString(ListSerializer(Marcacao.serializer()), body)
    override fun withLocalId(model: Marcacao, clientId: String) = model.copy(id = clientId)
}

/**
 * **GAP-KL-M-RESTCRUD-LOCALFIRST** — a escrita do usuário não pode sumir.
 *
 * O cenário que motivou a correção: com rede presente e servidor recusando, `create`/`update` não
 * gravavam nada e nada entrava na outbox. No "Todos a Bordo" isso era uma criança marcada como
 * desembarcada **sem que o registro existisse**.
 */
class OfflineFirstRestWriteTest {

    private val jsonHeader = headersOf("Content-Type", "application/json")

    /** Backend controlável: cada verbo devolve o status corrente (ou ecoa o corpo, se 2xx). */
    private class Server {
        var status: HttpStatusCode = HttpStatusCode.OK
        var transportDown: Boolean = false
        var serverId: String = "srv-1"
        var body: String? = null
        var requests: Int = 0

        /** Chamadas recebidas, como `"POST /v1/marcacoes"` — o verbo importa (POST × PUT). */
        val calls = mutableListOf<String>()
    }

    private fun repo(
        server: Server,
        store: FakeSyncStore,
        mode: RestWriteMode,
    ): OfflineFirstRestRepository<Marcacao> = OfflineFirstRestRepository(
        api = DomainApiClient(
            HttpClient(
                MockEngine { request ->
                    server.requests++
                    server.calls += "${request.method.value} ${request.url.encodedPath}"
                    if (server.transportDown) throw RuntimeException("sem rede")
                    val enviado = (request.body as? io.ktor.http.content.TextContent)?.text ?: ""
                    if (server.status.value in 200..299) {
                        val eco = server.body
                            ?: enviado.replaceFirst(Regex("\"id\":\"[^\"]*\""), "\"id\":\"${server.serverId}\"")
                        respond(eco.ifBlank { "{}" }, server.status, jsonHeader)
                    } else {
                        respond("""{"error":"recusado"}""", server.status, jsonHeader)
                    }
                },
            ),
            DomainTokenProvider { "tok" },
            "https://api.example.com",
        ),
        descriptor = MarcacaoCrud,
        store = store,
        collectionPath = "/v1/marcacoes",
        writeMode = mode,
    )

    // -- Correção que vale para TODOS os apps (default OnlineFirst) --------

    @Test
    fun `OnlineFirst - 5xx com rede presente NAO perde a escrita, cai na outbox`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { status = HttpStatusCode.InternalServerError }
        val r = repo(server, store, RestWriteMode.OnlineFirst)

        val res = r.create(Marcacao(id = "", nome = "Ana"))

        // A escrita foi aceita localmente (é o que "offline-first" promete) e está na fila.
        assertTrue(res is DomainResult.Success)
        val local = (res as DomainResult.Success).data
        assertTrue(local.id.startsWith("local-"))
        assertEquals(1, store.getDirty("marcacao").size)
        val state = r.stateOf(local.id)
        assertTrue(state is RestRowState.Pending)
        assertEquals(SyncOpType.CREATE, (state as RestRowState.Pending).op)
        assertEquals(1, state.attempts) // a tentativa que falhou foi contada
    }

    @Test
    fun `OnlineFirst - 4xx de validacao continua devolvendo erro sem persistir (formulario corrige)`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { status = HttpStatusCode.UnprocessableEntity }
        val r = repo(server, store, RestWriteMode.OnlineFirst)

        val res = r.create(Marcacao(id = "", nome = "Ana"))

        assertTrue(res is DomainResult.Error)
        assertEquals(422, (res as DomainResult.Error).code)
        assertTrue(store.getDirty("marcacao").isEmpty())
        assertTrue(r.getAllCached().isEmpty())
    }

    @Test
    fun `OnlineFirst - update com 503 preserva a alteracao na outbox`() = runTest {
        val store = FakeSyncStore()
        val server = Server()
        val r = repo(server, store, RestWriteMode.OnlineFirst)
        val criada = (r.create(Marcacao(id = "", nome = "Ana")) as DomainResult.Success).data

        server.status = HttpStatusCode.ServiceUnavailable
        val res = r.update(criada.copy(embarcadoEm = "2026-07-28T07:10:00Z"))

        assertTrue(res is DomainResult.Success)
        assertEquals("2026-07-28T07:10:00Z", r.getCached(criada.id)?.embarcadoEm)
        assertTrue(r.stateOf(criada.id).isPending)
    }

    // -- Caminho LocalFirst (o toque É o registro) --------------------------

    @Test
    fun `LocalFirst - grava ANTES da rede e reconcilia com o modelo do servidor`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { serverId = "srv-77" }
        val r = repo(server, store, RestWriteMode.LocalFirst)

        val res = r.create(Marcacao(id = "", nome = "Ana"))

        assertTrue(res is DomainResult.Success)
        assertEquals("srv-77", (res as DomainResult.Success).data.id)
        assertTrue(store.getDirty("marcacao").isEmpty()) // confirmada: nada pendente
        assertTrue(r.stateOf("srv-77").isSynced)
        assertEquals("Ana", r.getCached("srv-77")?.nome)
    }

    @Test
    fun `LocalFirst - 4xx terminal deixa a linha VISIVEL e marcada como nao-salva, com o erro`() = runTest {
        val store = FakeSyncStore()
        val server = Server()
        val r = repo(server, store, RestWriteMode.LocalFirst)
        val criada = (r.create(Marcacao(id = "", nome = "Ana")) as DomainResult.Success).data

        server.status = HttpStatusCode.Forbidden
        val res = r.update(criada.copy(embarcadoEm = "07:10"))

        // O app recebe o erro (mostra o aviso), mas a marcação NÃO é revertida em silêncio.
        assertTrue(res is DomainResult.Error)
        assertEquals("07:10", r.getCached(criada.id)?.embarcadoEm)
        val state = r.stateOf(criada.id)
        assertTrue(state is RestRowState.Failed)
        assertEquals(403, (state as RestRowState.Failed).code)
        assertNotNull(state.message)
        // E a linha continua na lista da UI, com estado — sem overlay em memória no app.
        val linha = r.getAllCachedWithState().single()
        assertTrue(linha.isFailed)
        assertEquals("07:10", linha.model.embarcadoEm)
    }

    @Test
    fun `LocalFirst - linha recusada NAO e retentada sozinha pelo drain`() = runTest {
        val store = FakeSyncStore()
        val server = Server()
        val r = repo(server, store, RestWriteMode.LocalFirst)
        val criada = (r.create(Marcacao(id = "", nome = "Ana")) as DomainResult.Success).data
        server.status = HttpStatusCode.UnprocessableEntity
        r.update(criada.copy(embarcadoEm = "07:10"))

        val antes = server.requests
        server.status = HttpStatusCode.OK
        r.drainOutbox(emptyMap())

        assertEquals(antes, server.requests) // nenhuma requisição: a linha está fora da fila drenável
        assertTrue(r.stateOf(criada.id).isFailed)
    }

    @Test
    fun `LocalFirst - requeueFailed devolve a linha a fila e o proximo ciclo grava`() = runTest {
        val store = FakeSyncStore()
        val server = Server()
        val r = repo(server, store, RestWriteMode.LocalFirst)
        val criada = (r.create(Marcacao(id = "", nome = "Ana")) as DomainResult.Success).data
        server.status = HttpStatusCode.UnprocessableEntity
        r.update(criada.copy(embarcadoEm = "07:10"))
        assertEquals(1, r.failedRows().size)

        server.status = HttpStatusCode.OK
        assertTrue(r.requeueFailed(criada.id))
        r.drainOutbox(emptyMap())

        assertTrue(r.stateOf(criada.id).isSynced)
        assertEquals("07:10", r.getCached(criada.id)?.embarcadoEm)
        assertTrue(r.failedRows().isEmpty())
    }

    @Test
    fun `LocalFirst - discardFailed remove a escrita que o usuario desistiu de corrigir`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { status = HttpStatusCode.BadRequest }
        val r = repo(server, store, RestWriteMode.LocalFirst)
        val res = r.create(Marcacao(id = "", nome = "Ana"))
        val localId = store.getDirty("marcacao").single().local_id

        assertTrue(res is DomainResult.Error)
        assertTrue(r.stateOf(localId).isFailed)
        assertTrue(r.discardFailed(localId))
        assertTrue(r.getAllCached().isEmpty())
        assertFalse(r.discardFailed(localId)) // idempotente: não havia mais nada
    }

    @Test
    fun `LocalFirst - 5xx mantem a marcacao pendente e o drain confirma depois`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { status = HttpStatusCode.BadGateway }
        val r = repo(server, store, RestWriteMode.LocalFirst)

        val res = r.create(Marcacao(id = "", nome = "Ana", embarcadoEm = "07:10"))
        assertTrue(res is DomainResult.Success)
        val localId = (res as DomainResult.Success).data.id
        assertTrue(r.stateOf(localId).isPending)

        server.status = HttpStatusCode.OK
        server.serverId = "srv-5"
        val remap = r.drainOutbox(emptyMap())

        assertEquals("srv-5", remap[localId])
        assertTrue(r.stateOf("srv-5").isSynced)
        assertEquals("07:10", r.getCached("srv-5")?.embarcadoEm)
    }

    @Test
    fun `LocalFirst - 402 abre o Paywall E preserva a escrita como recusada por cota`() = runTest {
        val store = FakeSyncStore()
        val quotaBody = """{"error":{"details":{"feature":"marcacoes","limite":"10","contagem":"10"}}}"""
        val api = DomainApiClient(
            HttpClient(MockEngine { respond(quotaBody, HttpStatusCode.PaymentRequired, jsonHeader) }),
            DomainTokenProvider { "tok" },
            "https://api.example.com",
        )
        val r = OfflineFirstRestRepository(
            api, MarcacaoCrud, store, "/v1/marcacoes", writeMode = RestWriteMode.LocalFirst,
        )

        val res = r.create(Marcacao(id = "", nome = "Ana"))

        assertTrue(res is DomainResult.Quota) // semântica de cota intacta
        val state = r.stateOf(store.getDirty("marcacao").single().local_id)
        assertTrue(state is RestRowState.Failed)
        assertTrue((state as RestRowState.Failed).isQuota)
    }

    @Test
    fun `LocalFirst - delete recusado REEXIBE o registro (ele continua existindo no servidor)`() = runTest {
        val store = FakeSyncStore()
        val server = Server()
        val r = repo(server, store, RestWriteMode.LocalFirst)
        val criada = (r.create(Marcacao(id = "", nome = "Ana")) as DomainResult.Success).data

        server.status = HttpStatusCode.Forbidden
        val res = r.delete(criada.id)

        assertTrue(res is DomainResult.Error)
        assertEquals(1, r.getAllCached().size) // não sumiu da lista
        assertTrue(r.stateOf(criada.id).isFailed)
    }

    @Test
    fun `drain - 4xx durante o push para de retentar e vira Failed (antes era loop infinito)`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { transportDown = true }
        val r = repo(server, store, RestWriteMode.OnlineFirst)
        val local = (r.create(Marcacao(id = "", nome = "Ana")) as DomainResult.Success).data

        server.transportDown = false
        server.status = HttpStatusCode.UnprocessableEntity
        r.drainOutbox(emptyMap())
        val depoisDoPrimeiro = server.requests
        r.drainOutbox(emptyMap()) // 2º ciclo não deve nem tentar

        assertEquals(depoisDoPrimeiro, server.requests)
        assertTrue(r.stateOf(local.id).isFailed)
    }

    @Test
    fun `drain - resposta ilegivel no create nao retenta (duplicaria o registro no servidor)`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { transportDown = true }
        val r = repo(server, store, RestWriteMode.OnlineFirst)
        val local = (r.create(Marcacao(id = "", nome = "Ana")) as DomainResult.Success).data

        server.transportDown = false
        server.body = "<html>proxy</html>" // 200 com corpo que não decodifica
        r.drainOutbox(emptyMap())
        val depois = server.requests
        r.drainOutbox(emptyMap())

        assertEquals(depois, server.requests)
        val state = r.stateOf(local.id)
        assertTrue(state is RestRowState.Failed)
        assertEquals(OfflineFirstRestRepository.INVALID_RESPONSE_CODE, (state as RestRowState.Failed).code)
    }

    @Test
    fun `escopo de conta - o push NAO sobe a outbox de A enquanto B esta logado`() = runTest {
        val store = FakeSyncStore()
        val server = Server()
        val r = repo(server, store, RestWriteMode.LocalFirst)

        // A marca offline: a escrita fica na outbox DELE.
        store.setAccountScope("motorista-A")
        server.transportDown = true
        val local = (r.create(Marcacao(id = "", nome = "Ana")) as DomainResult.Success).data
        server.transportDown = false

        // B entra e o ciclo roda: nada de A pode subir com o Bearer de B.
        store.setAccountScope("motorista-B")
        val antes = server.requests
        assertTrue(r.drainOutbox(emptyMap()).isEmpty())
        assertEquals(antes, server.requests)
        assertTrue(r.getAllCached().isEmpty()) // nem aparece na tela de B

        // A volta: a marcação continua pendente e agora sim é enviada.
        store.setAccountScope("motorista-A")
        server.serverId = "srv-A1"
        assertEquals("srv-A1", r.drainOutbox(emptyMap())[local.id])
        assertEquals("Ana", r.getCached("srv-A1")?.nome)
    }

    // -- Máquina de estados da outbox: o que nasceu offline sobe como CREATE ---
    // GAP-KL-M-RESTCRUD-PENDINGOP (2.92.0). Sem isto, "iniciar a rota sem rede" nunca subia:
    // o 1º toque trocava o CREATE pendente por UPDATE, o drain fazia PUT /…/local-… e o 404
    // marcava a linha como recusada — para sempre.

    @Test
    fun `update sobre linha pendente de CREATE mantem a operacao CREATE e o drain faz POST`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { transportDown = true }
        val r = repo(server, store, RestWriteMode.LocalFirst)

        // 1) Sem rede, a linha nasce com id local e CREATE pendente.
        val local = (r.create(Marcacao(id = "", nome = "Ana")) as DomainResult.Success).data
        assertTrue(local.id.startsWith("local-"))

        // 2) Ainda sem rede, o motorista marca o embarque (1º toque = update).
        val res = r.update(local.copy(embarcadoEm = "07:10"))
        assertTrue(res is DomainResult.Success)
        val pendente = r.stateOf(local.id)
        assertTrue(pendente is RestRowState.Pending)
        assertEquals(SyncOpType.CREATE, (pendente as RestRowState.Pending).op) // NÃO virou UPDATE
        assertEquals("07:10", r.getCached(local.id)?.embarcadoEm)

        // 3) Reconectou: UM POST, id remapeado, linha limpa — nada de PUT em /local-…
        server.transportDown = false
        server.serverId = "srv-9"
        server.calls.clear()
        val remap = r.drainOutbox(emptyMap())

        assertEquals(listOf("POST /v1/marcacoes"), server.calls)
        assertEquals("srv-9", remap[local.id])
        assertTrue(r.stateOf("srv-9").isSynced)
        assertEquals("07:10", r.getCached("srv-9")?.embarcadoEm)
        // 2.93.0: o id local é o HANDLE do registro — continua alcançando a MESMA linha depois da
        // migração (antes devolvia null, e a tela aberta com ele esvaziava no meio do uso).
        assertEquals("srv-9", r.getCached(local.id)?.id)
        assertEquals("srv-9", r.canonicalId(local.id))
        assertTrue(r.failedRows().isEmpty())
    }

    @Test
    fun `OnlineFirst - update sobre CREATE pendente nao faz PUT em id local (era 404 eterno)`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { transportDown = true }
        val r = repo(server, store, RestWriteMode.OnlineFirst)
        val local = (r.create(Marcacao(id = "", nome = "Ana")) as DomainResult.Success).data

        // Rede de volta, mas o registro ainda não existe no servidor: nenhuma requisição deve sair.
        server.transportDown = false
        server.status = HttpStatusCode.NotFound
        server.calls.clear()
        val res = r.update(local.copy(embarcadoEm = "07:10"))

        assertTrue(res is DomainResult.Success)
        assertTrue(server.calls.isEmpty())
        assertEquals(SyncOpType.CREATE, (r.stateOf(local.id) as RestRowState.Pending).op)
        assertEquals("07:10", r.getCached(local.id)?.embarcadoEm)
    }

    @Test
    fun `varios updates antes de qualquer sync viram UM unico POST com o payload final`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { transportDown = true }
        val r = repo(server, store, RestWriteMode.LocalFirst)
        val local = (r.create(Marcacao(id = "", nome = "Ana")) as DomainResult.Success).data

        r.update(local.copy(embarcadoEm = "07:10"))
        r.update(local.copy(embarcadoEm = null)) // desmarcou
        r.update(local.copy(embarcadoEm = "07:12")) // marcou de novo

        assertEquals(1, store.getDirty("marcacao").size) // uma linha, uma operação

        server.transportDown = false
        server.serverId = "srv-3"
        server.calls.clear()
        r.drainOutbox(emptyMap())

        assertEquals(listOf("POST /v1/marcacoes"), server.calls)
        assertEquals("07:12", r.getCached("srv-3")?.embarcadoEm)
    }

    @Test
    fun `delete sobre CREATE pendente remove local e NAO envia DELETE (evita 404 previsivel)`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { transportDown = true }
        val r = repo(server, store, RestWriteMode.LocalFirst)
        val local = (r.create(Marcacao(id = "", nome = "Ana")) as DomainResult.Success).data

        server.transportDown = false
        server.calls.clear()
        val res = r.delete(local.id)

        assertTrue(res is DomainResult.Success)
        assertTrue(server.calls.isEmpty()) // nada foi à rede: não há o que apagar lá
        assertTrue(r.getAllCached().isEmpty())
        assertTrue(store.getDirty("marcacao").isEmpty()) // nem sobrou tombstone na outbox

        r.drainOutbox(emptyMap())
        assertTrue(server.calls.isEmpty()) // e o ciclo seguinte não tem o que drenar
    }

    @Test
    fun `mirror tombstone sobre CREATE pendente apaga local em vez de enfileirar DELETE`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { transportDown = true }
        val r = repo(server, store, RestWriteMode.LocalFirst)
        val local = (r.create(Marcacao(id = "", nome = "Ana")) as DomainResult.Success).data

        r.mirror.tombstone(local.id) // caminho de baixo nível usado pelos endpoints custom do app

        assertTrue(r.getAllCached().isEmpty())
        assertTrue(store.getDirty("marcacao").isEmpty()) // nem tombstone sobrou na outbox
        server.transportDown = false
        server.calls.clear()
        r.drainOutbox(emptyMap())
        assertTrue(server.calls.isEmpty())
    }

    @Test
    fun `mirror tombstone em linha sincronizada continua enfileirando DELETE (sem regressao)`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { serverId = "srv-1" }
        val r = repo(server, store, RestWriteMode.LocalFirst)
        r.create(Marcacao(id = "", nome = "Ana"))

        r.mirror.tombstone("srv-1")

        assertEquals(SyncOpType.DELETE, (r.stateOf("srv-1") as RestRowState.Pending).op)
        server.calls.clear()
        r.drainOutbox(emptyMap())
        assertEquals(listOf("DELETE /v1/marcacoes/srv-1"), server.calls)
        assertTrue(r.getAllCached().isEmpty())
    }

    @Test
    fun `linha legada gravada como UPDATE sem server_id e curada e sobe como POST`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { serverId = "srv-legado" }
        val r = repo(server, store, RestWriteMode.OnlineFirst)
        // Estado que versões anteriores deixaram no aparelho: create pendente que virou "update".
        store.upsert(
            Synced_entity(
                account_id = "",
                entity = "marcacao",
                local_id = "local-legado",
                server_id = null,
                client_id = "local-legado",
                payload_json = """{"id":"local-legado","nome":"Ana","embarcadoEm":"07:10"}""",
                updated_at = null,
                dirty = 1L,
                pending_op = SyncOpType.UPDATE.wire,
                deleted = 0L,
                base_updated_at = null,
                last_error = "404",
                failed = 0L,
                fail_code = null,
                attempts = 7L,
            ),
        )

        val remap = r.drainOutbox(emptyMap())

        assertEquals(listOf("POST /v1/marcacoes"), server.calls)
        assertEquals("srv-legado", remap["local-legado"])
        assertTrue(r.stateOf("srv-legado").isSynced)
        assertEquals("07:10", r.getCached("srv-legado")?.embarcadoEm)
    }

    @Test
    fun `update em linha JA sincronizada continua fazendo PUT (sem regressao para os apps)`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { serverId = "srv-1" }
        val r = repo(server, store, RestWriteMode.OnlineFirst)
        val criada = (r.create(Marcacao(id = "", nome = "Ana")) as DomainResult.Success).data
        assertEquals("srv-1", criada.id)

        server.calls.clear()
        val res = r.update(criada.copy(embarcadoEm = "07:10"))

        assertTrue(res is DomainResult.Success)
        assertEquals(listOf("PUT /v1/marcacoes/srv-1"), server.calls)
        assertTrue(r.stateOf("srv-1").isSynced)
    }

    @Test
    fun `delete em linha JA sincronizada continua indo ao servidor`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { serverId = "srv-1" }
        val r = repo(server, store, RestWriteMode.OnlineFirst)
        val criada = (r.create(Marcacao(id = "", nome = "Ana")) as DomainResult.Success).data

        server.calls.clear()
        val res = r.delete(criada.id)

        assertTrue(res is DomainResult.Success)
        assertEquals(listOf("DELETE /v1/marcacoes/srv-1"), server.calls)
        assertTrue(r.getAllCached().isEmpty())
    }

    @Test
    fun `observeAllWithState entrega modelo e estado juntos para a lista`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { transportDown = true }
        val r = repo(server, store, RestWriteMode.LocalFirst)
        r.create(Marcacao(id = "", nome = "Ana"))

        val linhas = r.getAllCachedWithState()
        assertEquals(1, linhas.size)
        assertEquals("Ana", linhas.single().model.nome)
        assertTrue(linhas.single().isPending)
        assertNull(r.failedRows().firstOrNull()) // sem rede não é "recusado"
    }
}
