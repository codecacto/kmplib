package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.core.storage.BlobStore
import br.com.codecacto.kmplib.core.storage.InMemoryBlobStore
import br.com.codecacto.kmplib.core.storage.isValidBlobId
import br.com.codecacto.kmplib.sync.FakeSyncStore
import br.com.codecacto.kmplib.sync.SyncOpType
import br.com.codecacto.kmplib.sync.SyncStore
import br.com.codecacto.kmplib.sync.db.Synced_entity
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.readString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Fila de upload PERSISTENTE** (2.104.0 — GAP-AC-M-PHOTOOUTBOX-01).
 *
 * O caso que originou o gap é o do Acervo: foto tirada numa feira **sem sinal**, app fechado, volta
 * para casa — a foto tem de subir. Por isso o teste de "o processo morreu" recria o
 * [RestUploadOutbox] inteiro sobre o MESMO espelho e o MESMO disco (é exatamente o que sobrevive a um
 * reinício: a instância morre, a persistência não) e confere que o corpo enviado carrega os bytes
 * gravados.
 */
class RestUploadOutboxTest {

    private val jsonHeader = headersOf("Content-Type", "application/json")
    private val fotoAnverso = byteArrayOf(10, 20, 30, 40)
    private val fotoReverso = byteArrayOf(50, 60)

    // -- Infra de teste ----------------------------------------------------

    private class Servidor {
        val caminhos = mutableListOf<String>()
        val corpos = mutableListOf<String>()
        val respostas = ArrayDeque<HttpStatusCode>()
        var padrao: HttpStatusCode = HttpStatusCode.Created
        var quedaDeRede: Int = 0
    }

    private fun api(servidor: Servidor): DomainApiClient {
        val engine = MockEngine { request ->
            if (servidor.quedaDeRede > 0) {
                servidor.quedaDeRede--
                throw RuntimeException("sem rede")
            }
            servidor.caminhos += request.url.encodedPath
            servidor.corpos += lerCorpo(request)
            val status = servidor.respostas.removeFirstOrNull() ?: servidor.padrao
            if (status.value >= 400) respondError(status) else respond("""{"id":"foto-1"}""", status, jsonHeader)
        }
        return DomainApiClient(HttpClient(engine), DomainTokenProvider { "tok" }, "https://api.example.com")
    }

    private suspend fun lerCorpo(request: HttpRequestData): String {
        val content = request.body as? OutgoingContent.WriteChannelContent ?: return ""
        return coroutineScope {
            val channel = ByteChannel(autoFlush = true)
            launch {
                content.writeTo(channel)
                channel.flushAndClose()
            }
            channel.readRemaining().readString()
        }
    }

    /** Relógio controlado — o recuo se testa sem esperar meia hora. */
    private class Relogio(var agora: Long = 1_000L) : () -> Long {
        override fun invoke(): Long = agora
    }

    private fun outbox(
        servidor: Servidor,
        store: SyncStore,
        blobs: BlobStore,
        relogio: Relogio = Relogio(),
        retry: UploadRetryPolicy = UploadRetryPolicy(),
        onUploaded: (suspend (PendingUpload, String) -> Unit)? = null,
    ) = RestUploadOutbox(
        api = api(servidor),
        store = store,
        blobs = blobs,
        retry = retry,
        nowMillis = relogio,
        onUploaded = onUploaded,
    )

    /** Grava no espelho a linha de um item dono, como o [OfflineFirstRestRepository] faria. */
    private fun linhaDona(
        store: SyncStore,
        localId: String,
        serverId: String? = null,
        clientId: String = localId,
        entidade: String = "item",
    ) = store.upsert(
        Synced_entity(
            account_id = "",
            entity = entidade,
            local_id = localId,
            server_id = serverId,
            client_id = clientId,
            payload_json = """{"id":"$localId"}""",
            updated_at = null,
            dirty = if (serverId == null) 1L else 0L,
            pending_op = if (serverId == null) SyncOpType.CREATE.wire else null,
            deleted = 0L,
            base_updated_at = null,
            last_error = null,
            failed = 0L,
            fail_code = null,
            attempts = 0L,
            rejections = 0L,
            reject_code = null,
            reject_error = null,
        ),
    )

    private suspend fun RestUploadOutbox.enfileirarAnverso(handle: String, id: String = "up-1") = enqueue(
        bytes = fotoAnverso,
        fileName = "anverso.jpg",
        mimeType = "image/jpeg",
        path = "/v1/items/{owner}/photos",
        ownerEntity = "item",
        ownerHandle = handle,
        label = "anverso",
        id = id,
    )

    // -- 1. O caso que originou o gap --------------------------------------

    @Test
    fun `foto enfileirada offline sobrevive ao processo morrer e sobe depois com os bytes gravados`() = runTest {
        val store = FakeSyncStore()
        val blobs = InMemoryBlobStore()
        val servidor = Servidor()

        // Feira, sem sinal: o item nasce com id local e as duas fotos entram na fila.
        linhaDona(store, localId = "local-1")
        val antes = outbox(servidor, store, blobs)
        antes.enfileirarAnverso("local-1", id = "up-1")
        antes.enqueue(
            bytes = fotoReverso, fileName = "reverso.jpg", mimeType = "image/jpeg",
            path = "/v1/items/{owner}/photos", ownerEntity = "item", ownerHandle = "local-1",
            label = "reverso", id = "up-2",
        )
        assertEquals(2, antes.pending().size)

        // O app é fechado. Sobrevivem o espelho (SQLDelight) e o disco (BlobStore) — nada mais.
        val depois = outbox(servidor, store, blobs)
        assertEquals(2, depois.pending().size, "a fila tem de sobreviver ao processo morrer")

        // Em casa, com rede: o item sobe primeiro (o id migra) e então as fotos.
        val resumo = depois.drainNow(parentRemap = mapOf("local-1" to "srv-9"))

        assertEquals(2, resumo.uploaded)
        assertEquals(listOf("/v1/items/srv-9/photos", "/v1/items/srv-9/photos"), servidor.caminhos)
        assertTrue(servidor.corpos[0].contains("anverso.jpg"), "o corpo tem de carregar o arquivo gravado")
        assertTrue(servidor.corpos[1].contains("reverso.jpg"))
        assertTrue(depois.pending().isEmpty(), "fila limpa após o envio")
        assertTrue(blobs.ids().isEmpty(), "e os binários enviados saem do disco")
    }

    @Test
    fun `ordem FIFO por chegada`() = runTest {
        val store = FakeSyncStore()
        val blobs = InMemoryBlobStore()
        val servidor = Servidor()
        val relogio = Relogio()
        linhaDona(store, localId = "srv-1", serverId = "srv-1")
        val fila = outbox(servidor, store, blobs, relogio)

        relogio.agora = 100
        fila.enqueue(byteArrayOf(1), "a.jpg", "image/jpeg", "/v1/items/{owner}/photos", "item", "srv-1", id = "up-a")
        relogio.agora = 200
        fila.enqueue(byteArrayOf(2), "b.jpg", "image/jpeg", "/v1/items/{owner}/photos", "item", "srv-1", id = "up-b")

        fila.drainNow()

        assertTrue(servidor.corpos[0].contains("a.jpg"))
        assertTrue(servidor.corpos[1].contains("b.jpg"))
    }

    // -- 2. Amarração de ordem (ADR-0006 do Todos a Bordo) -----------------

    @Test
    fun `foto NAO sobe enquanto o item dono tem so id local`() = runTest {
        val store = FakeSyncStore()
        val blobs = InMemoryBlobStore()
        val servidor = Servidor()
        linhaDona(store, localId = "local-7") // ainda sem server_id
        val fila = outbox(servidor, store, blobs)
        fila.enfileirarAnverso("local-7")

        val resumo = fila.drainNow()

        assertEquals(0, resumo.uploaded)
        assertEquals(1, resumo.waitingOwner)
        assertTrue(servidor.caminhos.isEmpty(), "nenhum POST em /v1/items/local-7/photos")
        // Esperar NÃO é falhar: nada de tentativa contada nem erro na tela.
        val estado = fila.stateOf("up-1")
        assertIs<RestRowState.Pending>(estado)
        assertEquals(0, estado.attempts)
        assertNull(estado.rejection)
    }

    @Test
    fun `remap duravel faz a foto achar o item mesmo noutro ciclo`() = runTest {
        val store = FakeSyncStore()
        val blobs = InMemoryBlobStore()
        val servidor = Servidor()
        val fila = outbox(servidor, store, blobs)
        // O item foi criado offline, sincronizou e a linha migrou para o id do servidor: o handle
        // local só existe no remap durável.
        fila.enfileirarAnverso("local-7")
        store.rememberServerId("item", "local-7", "srv-42")
        linhaDona(store, localId = "srv-42", serverId = "srv-42", clientId = "local-7")

        val resumo = fila.drainNow() // sem parentRemap: outro ciclo, outra execução do app

        assertEquals(1, resumo.uploaded)
        assertEquals(listOf("/v1/items/srv-42/photos"), servidor.caminhos)
    }

    @Test
    fun `item dono que sumiu do espelho vira falha visivel com o binario preservado`() = runTest {
        val store = FakeSyncStore()
        val blobs = InMemoryBlobStore()
        val servidor = Servidor()
        val fila = outbox(servidor, store, blobs)
        fila.enfileirarAnverso("local-9") // nunca houve linha dona

        val resumo = fila.drainNow()

        assertEquals(1, resumo.rejected)
        val estado = fila.stateOf("up-1")
        assertIs<RestRowState.Failed>(estado)
        assertEquals(RestUploadOutbox.MISSING_OWNER_CODE, estado.code)
        assertEquals(1, blobs.ids().size, "o binário NÃO é apagado — quem descarta é o usuário")
    }

    @Test
    fun `correlacao por handles acha as fotos do item depois de o id migrar`() = runTest {
        val store = FakeSyncStore()
        val blobs = InMemoryBlobStore()
        val fila = outbox(Servidor(), store, blobs)
        linhaDona(store, localId = "local-3")
        fila.enfileirarAnverso("local-3")
        store.rememberServerId("item", "local-3", "srv-3")

        // A tela reaberta pelo histórico carrega o id do SERVIDOR; a foto guarda o id LOCAL.
        val achadas = fila.observeForOwner("srv-3").first()

        assertEquals(1, achadas.size, "comparar por igualdade derrubaria esta foto")
        assertEquals("anverso", achadas.single().model.label)
    }

    // -- 3. Falha, recuo e retentativa -------------------------------------

    @Test
    fun `falha retentavel adia com recuo exponencial e nao perde o binario`() = runTest {
        val store = FakeSyncStore()
        val blobs = InMemoryBlobStore()
        val servidor = Servidor().apply { respostas.addLast(HttpStatusCode.InternalServerError) }
        val relogio = Relogio(agora = 1_000)
        linhaDona(store, localId = "srv-1", serverId = "srv-1")
        val fila = outbox(servidor, store, blobs, relogio, retry = UploadRetryPolicy(baseDelayMillis = 30_000))
        fila.enfileirarAnverso("srv-1")

        assertEquals(0, fila.drainNow().uploaded)
        assertTrue(fila.stateOf("up-1").isPending, "5xx não é recusa: continua na fila")

        // Ainda dentro da janela de recuo: nem tenta.
        relogio.agora = 11_000
        assertEquals(1, fila.drainNow().deferred)
        assertEquals(1, servidor.caminhos.size, "não pode ter tentado de novo antes da hora")

        // Passado o recuo, sobe.
        relogio.agora = 32_000
        assertEquals(1, fila.drainNow().uploaded)
        assertEquals(2, servidor.caminhos.size)
        assertTrue(blobs.ids().isEmpty())
    }

    @Test
    fun `sem rede a fila inteira pausa e nada e perdido`() = runTest {
        val store = FakeSyncStore()
        val blobs = InMemoryBlobStore()
        val servidor = Servidor().apply { quedaDeRede = 1 }
        linhaDona(store, localId = "srv-1", serverId = "srv-1")
        val fila = outbox(servidor, store, blobs)
        fila.enfileirarAnverso("srv-1", id = "up-1")
        fila.enqueue(fotoReverso, "b.jpg", "image/jpeg", "/v1/items/{owner}/photos", "item", "srv-1", id = "up-2")

        val resumo = fila.drainNow()

        assertTrue(resumo.paused)
        assertEquals(0, resumo.uploaded)
        assertEquals(2, fila.pending().size)
        assertEquals(2, blobs.ids().size)
        // A queda derrubou a 1ª requisição; a 2ª nem foi tentada (insistir sem rede não converge).
        assertTrue(servidor.caminhos.isEmpty())
    }

    @Test
    fun `4xx e recusa terminal visivel e o requeue devolve a fila zerando o recuo`() = runTest {
        val store = FakeSyncStore()
        val blobs = InMemoryBlobStore()
        val servidor = Servidor().apply { respostas.addLast(HttpStatusCode.UnprocessableEntity) }
        linhaDona(store, localId = "srv-1", serverId = "srv-1")
        val fila = outbox(servidor, store, blobs)
        fila.enfileirarAnverso("srv-1")

        fila.drainNow()
        val recusada = fila.stateOf("up-1")
        assertIs<RestRowState.Failed>(recusada)
        assertEquals(422, recusada.code)
        assertEquals(1, fila.failed().size)
        // Recusada sai da fila drenável: não retenta sozinha para sempre.
        fila.drainNow()
        assertEquals(1, servidor.caminhos.size)

        assertTrue(fila.requeue("up-1"))
        assertEquals(1, fila.drainNow().uploaded)
    }

    @Test
    fun `402 de cota vira recusa propria e nao apaga a foto`() = runTest {
        val store = FakeSyncStore()
        val blobs = InMemoryBlobStore()
        val servidor = Servidor().apply { respostas.addLast(HttpStatusCode.PaymentRequired) }
        linhaDona(store, localId = "srv-1", serverId = "srv-1")
        val fila = outbox(servidor, store, blobs)
        fila.enfileirarAnverso("srv-1")

        fila.drainNow()

        val estado = fila.stateOf("up-1")
        assertIs<RestRowState.Failed>(estado)
        assertTrue(estado.isQuota, "402 tem semântica de paywall, não de erro genérico")
        assertEquals(1, blobs.ids().size)
    }

    @Test
    fun `tentativas esgotadas viram erro visivel em vez de retentar para sempre`() = runTest {
        val store = FakeSyncStore()
        val blobs = InMemoryBlobStore()
        val servidor = Servidor().apply { padrao = HttpStatusCode.InternalServerError }
        val relogio = Relogio()
        linhaDona(store, localId = "srv-1", serverId = "srv-1")
        val fila = outbox(
            servidor, store, blobs, relogio,
            retry = UploadRetryPolicy(baseDelayMillis = 1_000, maxAttempts = 3),
        )
        fila.enfileirarAnverso("srv-1")

        repeat(3) {
            relogio.agora += 1_000_000
            fila.drainNow()
        }

        val estado = fila.stateOf("up-1")
        assertIs<RestRowState.Failed>(estado)
        assertEquals(RestUploadOutbox.RETRY_EXHAUSTED_CODE, estado.code)
        assertEquals(3, servidor.caminhos.size, "parou exatamente no limite da política")
        assertEquals(1, blobs.ids().size, "e a foto continua no disco")
    }

    @Test
    fun `historico de recusa sobrevive ao requeue - a pendencia nao vira confiavel`() = runTest {
        val store = FakeSyncStore()
        val blobs = InMemoryBlobStore()
        val servidor = Servidor().apply { respostas.addLast(HttpStatusCode.UnprocessableEntity) }
        linhaDona(store, localId = "srv-1", serverId = "srv-1")
        val fila = outbox(servidor, store, blobs)
        fila.enfileirarAnverso("srv-1")

        fila.drainNow()
        fila.requeue("up-1")

        val estado = fila.stateOf("up-1")
        assertTrue(estado.isPending)
        assertNotNull(estado.rejection, "a fila herda o histórico de entrega do espelho")
        assertTrue(estado.hasDeliveryTrouble, "e por isso não pode ser dada por boa")
    }

    // -- 4. Escopo de conta -------------------------------------------------

    @Test
    fun `foto de uma conta nao sobe sob o Bearer de outra`() = runTest {
        val store = FakeSyncStore()
        val blobs = InMemoryBlobStore()
        val servidor = Servidor()
        store.setAccountScope("conta-a")
        linhaDona(store, localId = "srv-1", serverId = "srv-1")
        val fila = outbox(servidor, store, blobs)
        fila.enfileirarAnverso("srv-1")

        store.setAccountScope("conta-b")
        assertTrue(fila.pending().isEmpty(), "a fila de A é invisível para B")
        assertEquals(0, fila.drainNow().uploaded)
        assertTrue(servidor.caminhos.isEmpty())

        store.setAccountScope("conta-a")
        assertEquals(1, fila.pending().size, "e continua intacta quando A volta")
        assertEquals(1, fila.drainNow().uploaded)
    }

    // -- 5. Disco: limpeza e órfãos ----------------------------------------

    @Test
    fun `varredura apaga so o orfao - preserva o que outra conta ainda tem para enviar`() = runTest {
        val store = FakeSyncStore()
        val blobs = InMemoryBlobStore()
        val fila = outbox(Servidor(), store, blobs)
        store.setAccountScope("conta-a")
        linhaDona(store, localId = "srv-1", serverId = "srv-1")
        fila.enfileirarAnverso("srv-1", id = "up-a")

        // Resíduo de um processo morto entre gravar o arquivo e gravar a linha.
        blobs.write("orfao-0", byteArrayOf(9))

        store.setAccountScope("conta-b")
        val removidos = fila.sweepOrphanBlobs()

        assertEquals(1, removidos)
        assertFalse(blobs.exists("orfao-0"))
        assertTrue(blobs.exists("up-a-0"), "a foto de A não pode sumir porque B está logado")
    }

    @Test
    fun `store que nao le todas as contas nao varre nada`() = runTest {
        val blobs = InMemoryBlobStore()
        blobs.write("orfao-0", byteArrayOf(9))
        val cego = object : SyncStore by FakeSyncStore() {
            override fun getRowsAcrossAccounts(entity: String): List<Synced_entity>? = null
        }
        val fila = outbox(Servidor(), cego, blobs)

        assertEquals(0, fila.sweepOrphanBlobs())
        assertTrue(blobs.exists("orfao-0"), "na dúvida, não se apaga foto do usuário")
    }

    @Test
    fun `descartar remove a linha e o binario`() = runTest {
        val store = FakeSyncStore()
        val blobs = InMemoryBlobStore()
        val fila = outbox(Servidor(), store, blobs)
        linhaDona(store, localId = "srv-1", serverId = "srv-1")
        fila.enfileirarAnverso("srv-1")

        assertTrue(fila.discard("up-1"))

        assertTrue(fila.pending().isEmpty())
        assertTrue(blobs.ids().isEmpty())
    }

    @Test
    fun `binario que sumiu do disco vira erro em vez de enviar vazio`() = runTest {
        val store = FakeSyncStore()
        val blobs = InMemoryBlobStore()
        val servidor = Servidor()
        val fila = outbox(servidor, store, blobs)
        linhaDona(store, localId = "srv-1", serverId = "srv-1")
        fila.enfileirarAnverso("srv-1")
        blobs.delete("up-1-0")

        fila.drainNow()

        val estado = fila.stateOf("up-1")
        assertIs<RestRowState.Failed>(estado)
        assertEquals(RestUploadOutbox.MISSING_BLOB_CODE, estado.code)
        assertTrue(servidor.caminhos.isEmpty())
    }

    // -- 6. Enfileirar: recusas e multipart ---------------------------------

    @Test
    fun `enqueue recusa conteudo vazio e caminho sem dono - e nada fica no disco`() = runTest {
        val store = FakeSyncStore()
        val blobs = InMemoryBlobStore()
        val fila = outbox(Servidor(), store, blobs)

        val vazio = fila.enqueue(ByteArray(0), "a.jpg", "image/jpeg", "/v1/x", id = "up-1")
        val semDono = fila.enqueue(byteArrayOf(1), "a.jpg", "image/jpeg", "/v1/items/{owner}/photos", id = "up-2")

        assertIs<UploadEnqueueResult.Rejected>(vazio)
        assertEquals(UploadRejectReason.InvalidRequest, vazio.reason)
        assertIs<UploadEnqueueResult.Rejected>(semDono)
        assertTrue(blobs.ids().isEmpty())
        assertTrue(fila.pending().isEmpty())
    }

    @Test
    fun `disco que recusa a escrita NAO deixa promessa na fila`() = runTest {
        val store = FakeSyncStore()
        val discoCheio = object : BlobStore by InMemoryBlobStore() {
            override suspend fun write(id: String, bytes: ByteArray): Boolean = false
        }
        val fila = outbox(Servidor(), store, discoCheio)

        val r = fila.enqueue(fotoAnverso, "a.jpg", "image/jpeg", "/v1/x", id = "up-1")

        assertIs<UploadEnqueueResult.Rejected>(r)
        assertEquals(UploadRejectReason.StorageFailed, r.reason)
        assertTrue(fila.pending().isEmpty(), "linha sem arquivo é promessa que o disco não cumpre")
    }

    @Test
    fun `multiplas partes viajam num unico request e saem juntas do disco`() = runTest {
        val store = FakeSyncStore()
        val blobs = InMemoryBlobStore()
        val servidor = Servidor()
        linhaDona(store, localId = "srv-1", serverId = "srv-1")
        val fila = outbox(servidor, store, blobs)

        fila.enqueueParts(
            parts = listOf(
                UploadContent(fotoAnverso, "full.jpg", "image/jpeg", "full"),
                UploadContent(fotoReverso, "thumb.jpg", "image/jpeg", "thumb"),
            ),
            path = "/v1/items/{owner}/photos",
            ownerEntity = "item",
            ownerHandle = "srv-1",
            id = "up-1",
        )
        assertEquals(2, blobs.ids().size)

        assertEquals(1, fila.drainNow().uploaded)
        assertEquals(1, servidor.corpos.size)
        assertTrue(servidor.corpos.single().contains("name=full"))
        assertTrue(servidor.corpos.single().contains("name=thumb"))
        assertTrue(blobs.ids().isEmpty())
    }

    @Test
    fun `onUploaded recebe o corpo e uma excecao nele nao refaz o upload`() = runTest {
        val store = FakeSyncStore()
        val blobs = InMemoryBlobStore()
        val servidor = Servidor()
        var recebido: String? = null
        linhaDona(store, localId = "srv-1", serverId = "srv-1")
        val fila = outbox(servidor, store, blobs) { _, corpo ->
            recebido = corpo
            error("o app quebrou ao gravar a foto no espelho dele")
        }
        fila.enfileirarAnverso("srv-1")

        assertEquals(1, fila.drainNow().uploaded)

        assertEquals("""{"id":"foto-1"}""", recebido)
        assertTrue(fila.pending().isEmpty(), "o servidor já aceitou: repetir duplicaria a foto")
    }

    @Test
    fun `contagem pendente e o numero da frase 3 fotos aguardando envio`() = runTest {
        val store = FakeSyncStore()
        val blobs = InMemoryBlobStore()
        val servidor = Servidor().apply { respostas.addLast(HttpStatusCode.UnprocessableEntity) }
        linhaDona(store, localId = "srv-1", serverId = "srv-1")
        val fila = outbox(servidor, store, blobs)
        fila.enfileirarAnverso("srv-1", id = "up-1")
        fila.enqueue(fotoReverso, "b.jpg", "image/jpeg", "/v1/items/{owner}/photos", "item", "srv-1", id = "up-2")

        assertEquals(2, fila.observePendingCount().first())

        fila.drainNow() // a 1ª é recusada, a 2ª sobe
        assertEquals(0, fila.observePendingCount().first(), "recusada é erro, não 'aguardando envio'")
        assertEquals(1, fila.failed().size)
    }

    // -- 7. Peças puras -----------------------------------------------------

    @Test
    fun `recuo dobra ate o teto e o caminho exige o dono`() {
        val p = UploadRetryPolicy(baseDelayMillis = 1_000, maxDelayMillis = 8_000)
        assertEquals(0L, uploadRetryDelayMillis(0, p))
        assertEquals(1_000L, uploadRetryDelayMillis(1, p))
        assertEquals(2_000L, uploadRetryDelayMillis(2, p))
        assertEquals(4_000L, uploadRetryDelayMillis(3, p))
        assertEquals(8_000L, uploadRetryDelayMillis(4, p))
        assertEquals(8_000L, uploadRetryDelayMillis(40, p), "não estoura nem transborda")

        assertTrue(uploadPathRequiresOwner("/v1/items/{owner}/photos"))
        assertEquals("/v1/items/9/photos", resolveUploadPath("/v1/items/{owner}/photos", "9"))
        assertNull(resolveUploadPath("/v1/items/{owner}/photos", ""), "nunca montar /v1/items//photos")
        assertEquals("/v1/avatar", resolveUploadPath("/v1/avatar", ""))
        assertTrue(isValidBlobId(uploadBlobId(newRestClientId(), 0)), "o id gerado serve de nome de arquivo")
        assertFalse(isValidUploadId("../../etc/passwd"))
    }
}
