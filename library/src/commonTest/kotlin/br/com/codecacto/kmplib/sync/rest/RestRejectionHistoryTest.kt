package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.sync.FakeSyncStore
import br.com.codecacto.kmplib.sync.SyncOpType
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
private data class Linha(val id: String, val nome: String, val marcadoEm: String? = null)

private object LinhaCrud : RestCrudEntity<Linha> {
    override val name = "linha"
    override val serializer: KSerializer<Linha> = Linha.serializer()
    override fun idOf(model: Linha) = model.id
    override fun encodeBody(model: Linha) = Json.encodeToString(Linha.serializer(), model)
    override fun decodeModel(body: String) = Json.decodeFromString(Linha.serializer(), body)
    override fun decodeList(body: String) = Json.decodeFromString(ListSerializer(Linha.serializer()), body)
    override fun withLocalId(model: Linha, clientId: String) = model.copy(id = clientId)
}

/**
 * **GAP-KL-M-RESTCRUD-REJECTHISTORY** — o toque seguinte não pode apagar a prova da recusa.
 *
 * `failed`/`fail_code`/`last_error` descrevem o estado ATUAL da falha e devem mesmo ser limpos por
 * uma escrita nova (a intenção nova substitui a recusa). Mas o **histórico de entrega** descreve a
 * ENTREGA, não o conteúdo — e era zerado junto, apagando a única evidência de que aquele registro já
 * havia sido recusado pelo servidor.
 *
 * A sequência silenciosa que isso devolvia (a do "Todos a Bordo"):
 * 1. o registro é recusado (4xx) — o app avisa "não salvo" ✔
 * 2. **sem sinal**, o usuário toca de novo → tudo era zerado
 * 3. a linha virava `Pending(attempts = 0)`, indistinguível de uma pendência nova legítima, e a
 *    conferência a dava por boa: **"Tudo certo!"** com o servidor sem registro nenhum.
 */
class RestRejectionHistoryTest {

    private val jsonHeader = headersOf("Content-Type", "application/json")

    private class Server {
        var status: HttpStatusCode = HttpStatusCode.OK
        var transportDown: Boolean = false
        var serverId: String = "srv-1"
    }

    private fun repo(
        server: Server,
        store: FakeSyncStore,
        mode: RestWriteMode = RestWriteMode.LocalFirst,
    ) = OfflineFirstRestRepository(
        api = DomainApiClient(
            HttpClient(
                MockEngine { request ->
                    if (server.transportDown) throw RuntimeException("sem rede")
                    val enviado = (request.body as? io.ktor.http.content.TextContent)?.text ?: ""
                    if (server.status.value in 200..299) {
                        val eco = enviado.replaceFirst(Regex("\"id\":\"[^\"]*\""), "\"id\":\"${server.serverId}\"")
                        respond(eco.ifBlank { "{}" }, server.status, jsonHeader)
                    } else {
                        respond("""{"error":"conteudo invalido"}""", server.status, jsonHeader)
                    }
                },
            ),
            DomainTokenProvider { "tok" },
            "https://api.example.com",
        ),
        descriptor = LinhaCrud,
        store = store,
        collectionPath = "/v1/linhas",
        writeMode = mode,
    )

    // -- A sequência do desfecho fatal -------------------------------------

    @Test
    fun `recusa, toque OFFLINE, e a evidencia SOBREVIVE (era o Tudo certo com o registro faltando)`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { serverId = "srv-9" }
        val r = repo(server, store)

        // 1) A linha existe no servidor…
        val criada = (r.create(Linha(id = "", nome = "Ana")) as DomainResult.Success).data
        assertTrue(r.stateOf(criada.id).isSynced)

        // 2) …e uma marcação é RECUSADA (4xx terminal). O app avisa "não salvo".
        server.status = HttpStatusCode.UnprocessableEntity
        assertTrue(r.update(criada.copy(marcadoEm = "07:10")) is DomainResult.Error)
        val recusado = r.stateOf(criada.id)
        assertTrue(recusado is RestRowState.Failed)
        assertEquals(422, (recusado as RestRowState.Failed).code)

        // 3) No ponto seguinte, SEM SINAL, o usuário toca de novo naquela mesma linha.
        server.status = HttpStatusCode.OK
        server.transportDown = true
        val res = r.update(criada.copy(marcadoEm = "07:10", nome = "Ana"))
        assertTrue(res is DomainResult.Success) // offline: a escrita é aceita e vai para a outbox

        // 4) O ESTADO ATUAL da falha foi limpo (a linha voltou a ser drenável) — isso está certo…
        val depois = r.stateOf(criada.id)
        assertTrue(depois is RestRowState.Pending)
        assertEquals(1, store.getDrainable("linha").size)

        // …mas o HISTÓRICO sobreviveu: esta NÃO é uma pendência nova legítima.
        val historico = assertNotNull(depois.rejection, "a recusa anterior tem de continuar registrada")
        assertEquals(1, historico.count)
        assertEquals(422, historico.code)
        assertEquals(1, (depois as RestRowState.Pending).attempts) // attempts é histórico, não foi zerado
        assertTrue(depois.wasRejected)
        assertTrue(depois.hasDeliveryTrouble) // ← o sinal que impede o "Tudo certo!"
        assertFalse(depois.isUntriedPending)
    }

    @Test
    fun `linha criada offline e recusada guarda o historico quando o usuario toca de novo`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { status = HttpStatusCode.BadRequest }
        val r = repo(store = store, server = server)

        // Nasce local, o servidor recusa na hora.
        val local = (r.create(Linha(id = "", nome = "Ana")) as DomainResult.Error).let {
            store.getDirty("linha").single().local_id
        }
        assertTrue(r.stateOf(local).isFailed)

        // Toque seguinte, sem sinal.
        server.transportDown = true
        r.update(Linha(id = local, nome = "Ana", marcadoEm = "07:10"))

        val estado = r.stateOf(local)
        assertTrue(estado is RestRowState.Pending)
        // A linha continua pendente de CREATE (2.92.0) E carrega a recusa (2.94.0).
        assertEquals(SyncOpType.CREATE, (estado as RestRowState.Pending).op)
        assertEquals(400, assertNotNull(estado.rejection).code)
        assertTrue(estado.hasDeliveryTrouble)
    }

    // -- As duas camadas: estado atual × histórico -------------------------

    @Test
    fun `pendencia NOVA legitima nao tem historico nenhum (offline puro e confiavel)`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { transportDown = true }
        val r = repo(server, store)

        val local = (r.create(Linha(id = "", nome = "Ana")) as DomainResult.Success).data
        val estado = r.stateOf(local.id)

        assertTrue(estado.isPending)
        assertNull(estado.rejection)
        assertEquals(0, (estado as RestRowState.Pending).attempts)
        assertFalse(estado.hasDeliveryTrouble) // não é alarme: vai subir
        assertTrue(estado.isUntriedPending)
    }

    @Test
    fun `5xx passageiro conta tentativa mas NAO e recusa (nada de historico de recusa)`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { status = HttpStatusCode.BadGateway }
        val r = repo(server, store)

        val local = (r.create(Linha(id = "", nome = "Ana")) as DomainResult.Success).data
        val estado = r.stateOf(local.id)

        assertTrue(estado is RestRowState.Pending)
        assertEquals(1, (estado as RestRowState.Pending).attempts)
        assertNull(estado.rejection) // 5xx não é o servidor dizendo "não"
        assertFalse(estado.wasRejected)
        assertTrue(estado.hasDeliveryTrouble) // mas já houve problema: a UI pode sinalizar
    }

    @Test
    fun `o servidor ACEITAR e o unico caminho que apaga o historico`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply {
            serverId = "srv-3"
            status = HttpStatusCode.UnprocessableEntity
        }
        val r = repo(server, store)

        // Recusada…
        val local = store.let {
            r.create(Linha(id = "", nome = "Ana"))
            it.getDirty("linha").single().local_id
        }
        assertTrue(r.stateOf(local).isFailed)

        // …devolvida à fila e finalmente aceita.
        server.status = HttpStatusCode.OK
        assertTrue(r.requeueFailed(local))
        r.drainOutbox(emptyMap())

        assertTrue(r.stateOf("srv-3").isSynced)
        assertNull(r.stateOf("srv-3").rejection)

        // E uma escrita posterior nasce limpa: o registro existe do outro lado, não há do que desconfiar.
        server.transportDown = true
        r.update(Linha(id = "srv-3", nome = "Ana", marcadoEm = "07:20"))
        val estado = r.stateOf("srv-3")
        assertTrue(estado.isUntriedPending)
        assertNull(estado.rejection)
    }

    @Test
    fun `requeueFailed SEM SINAL nao apaga a prova (era o retry que transformava recusa em pendencia boa)`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { status = HttpStatusCode.Forbidden }
        val r = repo(server, store)
        r.create(Linha(id = "", nome = "Ana"))
        val local = store.getDirty("linha").single().local_id
        assertTrue(r.stateOf(local).isFailed)

        // O usuário toca em "Tentar enviar" ainda sem sinal: a linha volta à fila…
        server.transportDown = true
        assertTrue(r.requeueFailed(local))
        r.drainOutbox(emptyMap())

        // …mas continua não sendo uma pendência confiável.
        val estado = r.stateOf(local)
        assertTrue(estado.isPending)
        assertEquals(403, assertNotNull(estado.rejection).code)
        assertTrue(estado.hasDeliveryTrouble)
    }

    @Test
    fun `varios toques offline nao inflam nem apagam a contagem de recusas`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { serverId = "srv-7" }
        val r = repo(server, store)
        val criada = (r.create(Linha(id = "", nome = "Ana")) as DomainResult.Success).data

        server.status = HttpStatusCode.UnprocessableEntity
        r.update(criada.copy(marcadoEm = "07:10"))
        server.transportDown = true
        repeat(3) { r.update(criada.copy(marcadoEm = "07:1$it")) }

        val estado = r.stateOf(criada.id)
        // Uma recusa aconteceu — uma, não zero e não quatro.
        assertEquals(1, assertNotNull(estado.rejection).count)
        assertEquals("07:12", r.getCached(criada.id)?.marcadoEm) // o payload é o último toque
    }

    @Test
    fun `recusa por cota fica registrada como cota no historico`() = runTest {
        val store = FakeSyncStore()
        val quotaBody = """{"error":{"details":{"feature":"linhas","limite":"10","contagem":"10"}}}"""
        val r = OfflineFirstRestRepository(
            api = DomainApiClient(
                HttpClient(MockEngine { respond(quotaBody, HttpStatusCode.PaymentRequired, jsonHeader) }),
                DomainTokenProvider { "tok" },
                "https://api.example.com",
            ),
            descriptor = LinhaCrud,
            store = store,
            collectionPath = "/v1/linhas",
            writeMode = RestWriteMode.LocalFirst,
        )

        assertTrue(r.create(Linha(id = "", nome = "Ana")) is DomainResult.Quota)
        val local = store.getDirty("linha").single().local_id
        assertTrue(assertNotNull(r.stateOf(local).rejection).isQuota)
    }

    @Test
    fun `a linha da lista carrega o sinal (a UI nao precisa remontar a regra)`() = runTest {
        val store = FakeSyncStore()
        val server = Server().apply { status = HttpStatusCode.UnprocessableEntity }
        val r = repo(server, store)
        r.create(Linha(id = "", nome = "Ana"))
        server.transportDown = true
        val local = store.getDirty("linha").single().local_id
        r.update(Linha(id = local, nome = "Ana", marcadoEm = "07:10"))

        val linha = r.getAllCachedWithState().single()
        assertTrue(linha.isPending)
        assertTrue(linha.wasRejected)
        assertTrue(linha.hasDeliveryTrouble)
    }

    @Test
    fun `Synced nunca carrega historico`() {
        assertNull(RestRowState.Synced.rejection)
        assertFalse(RestRowState.Synced.wasRejected)
        assertFalse(RestRowState.Synced.hasDeliveryTrouble)
        assertFalse(RestRowState.Synced.isUntriedPending)
    }
}
