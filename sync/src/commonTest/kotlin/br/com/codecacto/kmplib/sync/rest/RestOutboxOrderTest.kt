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
import kotlin.test.assertTrue

@Serializable
private data class OrdemTalhao(val id: String, val nome: String)

private object OrdemTalhaoCrud : RestCrudEntity<OrdemTalhao> {
    override val name = "talhao"
    override val serializer: KSerializer<OrdemTalhao> = OrdemTalhao.serializer()
    override fun idOf(model: OrdemTalhao) = model.id
    override fun encodeBody(model: OrdemTalhao) = Json.encodeToString(OrdemTalhao.serializer(), model)
    override fun decodeModel(body: String) = Json.decodeFromString(OrdemTalhao.serializer(), body)
    override fun decodeList(body: String) = Json.decodeFromString(ListSerializer(OrdemTalhao.serializer()), body)
    override fun withLocalId(model: OrdemTalhao, clientId: String) = model.copy(id = clientId)
}

@Serializable
private data class OrdemLancamento(val id: String, val talhaoId: String, val descricao: String)

private object OrdemLancamentoCrud : RestCrudEntity<OrdemLancamento> {
    override val name = "lancamento"
    override val serializer: KSerializer<OrdemLancamento> = OrdemLancamento.serializer()
    override fun idOf(model: OrdemLancamento) = model.id
    override fun encodeBody(model: OrdemLancamento) = Json.encodeToString(OrdemLancamento.serializer(), model)
    override fun decodeModel(body: String) = Json.decodeFromString(OrdemLancamento.serializer(), body)
    override fun decodeList(body: String) = Json.decodeFromString(ListSerializer(OrdemLancamento.serializer()), body)
    override fun withLocalId(model: OrdemLancamento, clientId: String) = model.copy(id = clientId)
}

/**
 * **Auditoria de ORDEM da fila de dados (2.328.0)** — os dois defeitos que o Raiz achou na fila dele,
 * reproduzidos aqui contra a outbox REST-CRUD da lib:
 *
 * 1. **Filho antes do pai.** O pai falhava de forma retentável (5xx) e o filho, gravado depois,
 *    subia mesmo assim com a FK local → 422 → recusa TERMINAL → registro perdido.
 * 2. **Edição antiga sobrescrevendo a nova.** A resposta de um envio antigo marcava LIMPA a linha
 *    que já tinha a gravação nova da pessoa — a nova nunca subia e sumia do aparelho.
 */
class RestOutboxOrderTest {

    private val jsonHeader = headersOf("Content-Type", "application/json")

    private class Backend {
        var talhaoFalha500 = false
        var lancamentoRecusa422 = false
        val talhoes = mutableMapOf<String, String>() // id -> nome no servidor
        val lancamentos = mutableMapOf<String, String>() // id -> descricao
        val recebidos = mutableListOf<String>()
        var recusasPorFk = 0
        var seq = 0

        /** Chamado DENTRO do tratamento da requisição — a pessoa grava enquanto o envio está em voo. */
        var duranteOEnvio: (() -> Unit)? = null
    }

    private fun api(b: Backend) = DomainApiClient(
        HttpClient(
            MockEngine { request ->
                val corpo = (request.body as? io.ktor.http.content.TextContent)?.text ?: ""
                val caminho = request.url.encodedPath
                val metodo = request.method.value
                b.recebidos += "$metodo $caminho $corpo"
                b.duranteOEnvio?.let { b.duranteOEnvio = null; it() }
                when {
                    metodo == "GET" -> respond("[]", HttpStatusCode.OK, jsonHeader)
                    caminho.startsWith("/v1/talhoes") -> {
                        if (b.talhaoFalha500) return@MockEngine respond("{}", HttpStatusCode.InternalServerError, jsonHeader)
                        val t = Json.decodeFromString(OrdemTalhao.serializer(), corpo)
                        val id = if (metodo == "POST") "tal-SRV-${++b.seq}" else caminho.substringAfterLast('/')
                        b.talhoes[id] = t.nome
                        respond(Json.encodeToString(OrdemTalhao.serializer(), t.copy(id = id)), HttpStatusCode.OK, jsonHeader)
                    }
                    else -> {
                        val l = Json.decodeFromString(OrdemLancamento.serializer(), corpo)
                        if (l.talhaoId !in b.talhoes) {
                            b.recusasPorFk++
                            return@MockEngine respond("""{"error":"talhao inexistente"}""", HttpStatusCode.UnprocessableEntity, jsonHeader)
                        }
                        if (b.lancamentoRecusa422) {
                            return@MockEngine respond("""{"error":"invalido"}""", HttpStatusCode.UnprocessableEntity, jsonHeader)
                        }
                        val id = if (metodo == "POST") "lan-SRV-${++b.seq}" else caminho.substringAfterLast('/')
                        b.lancamentos[id] = l.descricao
                        respond(Json.encodeToString(OrdemLancamento.serializer(), l.copy(id = id)), HttpStatusCode.OK, jsonHeader)
                    }
                }
            },
        ),
        DomainTokenProvider { "tok" },
        "https://api.example.com",
    )

    private fun talhoes(b: Backend, s: FakeSyncStore) =
        OfflineFirstRestRepository(api(b), OrdemTalhaoCrud, s, "/v1/talhoes", writeMode = RestWriteMode.LocalFirst)

    private fun lancamentos(b: Backend, s: FakeSyncStore) =
        OfflineFirstRestRepository(api(b), OrdemLancamentoCrud, s, "/v1/lancamentos", writeMode = RestWriteMode.LocalFirst)

    // ---------------------------------------------------------------------
    // 1) Filho antes do pai
    // ---------------------------------------------------------------------

    @Test
    fun `filho de pai com falha retentavel e SEGURADO, nao recusado, e sobe depois do pai`() = runTest {
        val s = FakeSyncStore()
        val b = Backend().apply { talhaoFalha500 = true }
        val tal = talhoes(b, s)
        val lan = lancamentos(b, s)

        // O talhão falha (500) e fica na fila com recuo; o lançamento é gravado em seguida.
        val talhao = (tal.create(OrdemTalhao("", "Talhão 1")) as DomainResult.Success).data
        val lancamento = lan.create(OrdemLancamento("", talhao.id, "Adubação"))
        assertTrue(lancamento is DomainResult.Success, "a gravação do filho é aceita (fica pendente)")

        // Ciclo com o pai ainda falhando: o filho NÃO sai com a FK local.
        tal.drainOutbox(emptyMap())
        lan.drainOutbox(emptyMap())
        assertEquals(0, b.recusasPorFk, "nenhum POST de filho com a FK local")
        assertTrue(lan.failedRows().isEmpty(), "o filho não virou recusa terminal")
        assertEquals(1, s.getDirty("lancamento").size, "o filho continua pendente")

        // O servidor volta: pai e filho sobem, na ordem.
        b.talhaoFalha500 = false
        val remap = tal.drainOutbox(emptyMap())
        lan.drainOutbox(remap)
        assertEquals(0, b.recusasPorFk)
        assertEquals(setOf("Adubação"), b.lancamentos.values.toSet())
        assertTrue(s.getDirty("lancamento").isEmpty())
        assertTrue(s.getDirty("talhao").isEmpty())
    }

    @Test
    fun `filho drenado ANTES do pai no mesmo ciclo espera, sem recusa`() = runTest {
        val s = FakeSyncStore()
        val b = Backend()
        val tal = talhoes(b, s)
        val lan = lancamentos(b, s)
        // Grava offline (nada sobe): força o caminho da outbox.
        tal.mirror.putDirty(OrdemTalhao("local-t1", "Talhão 1"), SyncOpType.CREATE)
        lan.mirror.putDirty(OrdemLancamento("local-l1", "local-t1", "Plantio"), SyncOpType.CREATE)

        // Participantes na ordem ERRADA (filho primeiro).
        lan.drainOutbox(emptyMap())
        assertEquals(0, b.recusasPorFk)
        assertTrue(lan.failedRows().isEmpty())

        val remap = tal.drainOutbox(emptyMap())
        lan.drainOutbox(remap)
        assertEquals(setOf("Plantio"), b.lancamentos.values.toSet())
        assertEquals(0, b.recusasPorFk)
    }

    @Test
    fun `filho que referencia so pai ja criado nao e segurado`() = runTest {
        val s = FakeSyncStore()
        val b = Backend()
        val tal = talhoes(b, s)
        val lan = lancamentos(b, s)
        val talhao = (tal.create(OrdemTalhao("", "Talhão 1")) as DomainResult.Success).data
        assertTrue(talhao.id.startsWith("tal-SRV"))
        val r = lan.create(OrdemLancamento("", talhao.id, "Colheita"))
        assertTrue(r is DomainResult.Success && r.data.id.startsWith("lan-SRV"), "sobe na hora")
    }

    // ---------------------------------------------------------------------
    // 2) Gravação nova durante o envio da antiga
    // ---------------------------------------------------------------------

    @Test
    fun `edicao feita durante o PUT da anterior nao se perde e sobe depois`() = runTest {
        val s = FakeSyncStore()
        val b = Backend()
        val tal = talhoes(b, s)
        val criado = (tal.create(OrdemTalhao("", "v1")) as DomainResult.Success).data

        // v2 fica na outbox (sem rede na hora da gravação)…
        tal.mirror.putDirty(criado.copy(nome = "v2"), SyncOpType.UPDATE)
        // …e, enquanto o PUT da v2 está em voo, a pessoa grava a v3.
        b.duranteOEnvio = { tal.mirror.putDirty(criado.copy(nome = "v3"), SyncOpType.UPDATE) }
        tal.drainOutbox(emptyMap())

        // A resposta da v2 NÃO apagou a v3: ela continua no aparelho, pendente.
        assertEquals("v3", tal.getCached(criado.id)?.nome)
        assertEquals(1, s.getDirty("talhao").size, "a v3 segue na fila")
        assertEquals("v2", b.talhoes[criado.id])

        tal.drainOutbox(emptyMap())
        assertEquals("v3", b.talhoes[criado.id], "a última gravação é a que fica no servidor")
        assertTrue(s.getDirty("talhao").isEmpty())
    }

    @Test
    fun `edicao feita durante o POST da criacao migra para o id do servidor e sobe como PUT`() = runTest {
        val s = FakeSyncStore()
        val b = Backend()
        val tal = talhoes(b, s)
        tal.mirror.putDirty(OrdemTalhao("local-t9", "v1"), SyncOpType.CREATE)
        b.duranteOEnvio = { tal.mirror.putDirty(OrdemTalhao("local-t9", "v2"), SyncOpType.UPDATE) }

        tal.drainOutbox(emptyMap())
        val idServidor = b.talhoes.keys.single()
        assertEquals("v1", b.talhoes[idServidor])
        val linha = assertNotNull(s.getDirty("talhao").singleOrNull(), "a v2 segue na fila")
        assertEquals(idServidor, linha.server_id)
        assertEquals(SyncOpType.UPDATE.wire, linha.pending_op, "não cria de novo — atualiza")
        assertEquals("v2", tal.getCached("local-t9")?.nome, "o handle local ainda acha o registro")

        tal.drainOutbox(emptyMap())
        assertEquals(1, b.talhoes.size, "nenhum registro duplicado")
        assertEquals("v2", b.talhoes[idServidor])
        assertTrue(b.recebidos.any { it.startsWith("PUT /v1/talhoes/$idServidor") })
        assertTrue(s.getDirty("talhao").isEmpty())
    }

    @Test
    fun `recusa da versao antiga nao marca como recusada a versao nova`() = runTest {
        val s = FakeSyncStore()
        val b = Backend()
        val tal = talhoes(b, s)
        val lan = lancamentos(b, s)
        val talhao = (tal.create(OrdemTalhao("", "T")) as DomainResult.Success).data
        val criado = (lan.create(OrdemLancamento("", talhao.id, "ok")) as DomainResult.Success).data

        lan.mirror.putDirty(criado.copy(descricao = "invalida"), SyncOpType.UPDATE)
        b.lancamentoRecusa422 = true
        // Durante o PUT da versão inválida (que o servidor vai recusar), a pessoa corrige.
        b.duranteOEnvio = { lan.mirror.putDirty(criado.copy(descricao = "corrigida"), SyncOpType.UPDATE) }
        lan.drainOutbox(emptyMap())

        assertTrue(lan.failedRows().isEmpty(), "a recusa era da versão antiga")
        assertEquals("corrigida", lan.getCached(criado.id)?.descricao)
        assertEquals(1, s.getDirty("lancamento").size, "a correção segue na fila")

        b.lancamentoRecusa422 = false
        lan.drainOutbox(emptyMap())
        assertEquals("corrigida", b.lancamentos[criado.id])
        assertTrue(s.getDirty("lancamento").isEmpty())
    }

    @Test
    fun `envio de linha apagada localmente durante o voo nao ressuscita o registro`() = runTest {
        val s = FakeSyncStore()
        val b = Backend()
        val tal = talhoes(b, s)
        val criado = (tal.create(OrdemTalhao("", "v1")) as DomainResult.Success).data
        tal.mirror.putDirty(criado.copy(nome = "v2"), SyncOpType.UPDATE)
        // Limpeza da conta (logout) apaga a linha enquanto o PUT está em voo.
        b.duranteOEnvio = { tal.mirror.removeHard(criado.id) }
        tal.drainOutbox(emptyMap())
        assertFalse(tal.getAllCached().any { it.id == criado.id })
    }
}
