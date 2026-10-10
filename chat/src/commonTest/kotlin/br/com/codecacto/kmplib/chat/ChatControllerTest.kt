package br.com.codecacto.kmplib.chat

import br.com.codecacto.kmplib.core.storage.InMemoryBlobStore
import br.com.codecacto.kmplib.sync.rest.DomainResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Servidor de mentira: guarda mensagens, devolve a existente para id repetido, conta chamadas. */
private class FakeServer(var agora: () -> Long) : ChatTransport {
    val mensagens = mutableListOf<ChatMessage>()
    val chamadas = mutableListOf<String>()
    val falhasEnvio = ArrayDeque<DomainResult.Error>()
    val falhasConsulta = ArrayDeque<DomainResult.Error>()
    var uploads = 0
    var lidaAte: String? = null

    private fun ordenadas() = mensagens.sortedWith(ChatMessageOrder)

    override suspend fun latest(size: Int): DomainResult<ChatPage> {
        chamadas += "latest"
        falhasConsulta.removeFirstOrNull()?.let { return it }
        val o = ordenadas()
        return DomainResult.Success(ChatPage(o.takeLast(size).reversed(), o.size > size))
    }

    override suspend fun before(beforeId: String, size: Int): DomainResult<ChatPage> {
        chamadas += "before:$beforeId"
        val o = ordenadas()
        val i = o.indexOfFirst { it.id == beforeId }
        val antes = o.take(i.coerceAtLeast(0))
        return DomainResult.Success(ChatPage(antes.takeLast(size).reversed(), antes.size > size))
    }

    override suspend fun after(afterId: String, size: Int): DomainResult<ChatPage> {
        chamadas += "after:$afterId"
        falhasConsulta.removeFirstOrNull()?.let { return it }
        val o = ordenadas()
        val i = o.indexOfFirst { it.id == afterId }
        val depois = o.drop(i + 1)
        return DomainResult.Success(ChatPage(depois.take(size), depois.size > size))
    }

    override suspend fun send(request: ChatSendRequest): DomainResult<ChatMessage> {
        chamadas += "send:${request.id}"
        falhasEnvio.removeFirstOrNull()?.let { return it }
        mensagens.firstOrNull { it.id == request.id }?.let { return DomainResult.Success(it) }
        val m = ChatMessage(request.id, true, request.kind, request.text, request.audioAssetId, null, agora())
        mensagens += m
        return DomainResult.Success(m)
    }

    override suspend fun uploadAudio(bytes: ByteArray, durationSeconds: Int): DomainResult<String> {
        chamadas += "upload:$durationSeconds"
        uploads++
        return DomainResult.Success("asset-$uploads")
    }

    override suspend fun markRead(upToMessageId: String): DomainResult<Unit> {
        chamadas += "read:$upToMessageId"
        lidaAte = upToMessageId
        return DomainResult.Success(Unit)
    }

    fun receber(id: String, em: Long, texto: String = "oi") {
        mensagens += ChatMessage(id, false, ChatMessageKind.TEXT, texto, createdAtMillis = em)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatControllerTest {

    private class Cena(val test: TestScope) {
        val blobs = InMemoryBlobStore()
        val store = ChatPendingStore(blobs)
        val server = FakeServer { test.testScheduler.currentTime }
        var ids = 0

        fun controller(config: ChatConfig = ChatConfig(pollIntervalMillis = 5_000, autoRetryLimit = 3)) = ChatController(
            transport = server,
            conversationKey = "aluno-1",
            accountId = "conta-a",
            scope = test.backgroundScope,
            store = store,
            config = config,
            nowMillis = { test.testScheduler.currentTime },
            idFactory = { "m-${++ids}" },
        )
    }

    @Test
    fun cargaInicialMarcaANaoLidaEOPollingTrazANova() = runTest {
        val c = Cena(this)
        c.server.receber("s1", 10)
        c.server.receber("s2", 20)
        val chat = c.controller()
        chat.start()
        runCurrent()

        val s = chat.state.value
        assertFalse(s.isLoadingInitial)
        assertEquals(listOf("s1", "s2"), s.entries.map { it.id })
        assertEquals(2, s.unreadCount)
        assertEquals("s1", s.firstUnreadId)

        c.server.receber("s3", 30)
        advanceTimeBy(5_001)
        assertEquals(listOf("s1", "s2", "s3"), chat.state.value.entries.map { it.id })
        assertTrue("after:s2" in c.server.chamadas, "polling por after=")
        assertEquals("s1", chat.state.value.firstUnreadId, "o divisor não pula")
        chat.stop()
    }

    @Test
    fun envioOtimistaAparecenaHoraEConfirmaSemDuplicar() = runTest {
        val c = Cena(this)
        val chat = c.controller()
        chat.start()
        runCurrent()

        assertTrue(chat.sendText("  olá  "))
        val e = chat.state.value.entries.single()
        assertEquals("olá", e.text)
        runCurrent()
        val confirmada = chat.state.value.entries.single()
        assertEquals("m-1", confirmada.id)
        assertEquals(ChatDelivery.SENT, confirmada.delivery)
        assertEquals(listOf("send:m-1"), c.server.chamadas.filter { it.startsWith("send") })

        // O eco do polling (a mesma mensagem) não duplica.
        advanceTimeBy(5_001)
        assertEquals(1, chat.state.value.entries.size)
        assertFalse(chat.sendText("   "))
        chat.stop()
    }

    @Test
    fun falhaDefinitivaParaEReenvioUsaOMesmoId() = runTest {
        val c = Cena(this)
        val chat = c.controller()
        chat.start()
        runCurrent()
        c.server.falhasEnvio += DomainResult.Error(400, "x", serverCode = "MESSAGE_EMPTY", serverMessage = "Mensagem vazia.")
        chat.sendText("oi")
        runCurrent()
        val falhou = chat.state.value.entries.single()
        assertEquals(ChatDelivery.FAILED, falhou.delivery)
        assertEquals("MESSAGE_EMPTY", falhou.failure?.serverCode)

        assertTrue(chat.retry("m-1"))
        runCurrent()
        assertEquals(ChatDelivery.SENT, chat.state.value.entries.single().delivery)
        assertEquals(listOf("send:m-1", "send:m-1"), c.server.chamadas.filter { it.startsWith("send") })
        chat.stop()
    }

    @Test
    fun reenvioDeMensagemQueOServidorJaTinhaNaoDuplica() = runTest {
        val c = Cena(this)
        // O servidor gravou mas a resposta se perdeu (5xx depois de gravar): a fila repete o id.
        val chat = c.controller()
        chat.start()
        runCurrent()
        c.server.mensagens += ChatMessage("m-1", true, ChatMessageKind.TEXT, "oi", createdAtMillis = 0)
        c.server.falhasEnvio += DomainResult.Error(503, "fora")
        chat.sendText("oi")
        runCurrent()
        assertEquals(ChatDelivery.SENDING, chat.state.value.entries.single().delivery, "retentável: segue enviando")
        advanceTimeBy(2_001)
        runCurrent()
        assertEquals(1, c.server.mensagens.size)
        assertEquals(ChatDelivery.SENT, chat.state.value.entries.single().delivery)
        chat.stop()
    }

    @Test
    fun retentavelEsgotaEViraNaoEnviada() = runTest {
        val c = Cena(this)
        val chat = c.controller()
        chat.start()
        runCurrent()
        repeat(3) { c.server.falhasEnvio += DomainResult.Error(500, "x") }
        chat.sendText("oi")
        runCurrent()
        advanceTimeBy(2_001)
        runCurrent()
        advanceTimeBy(4_001)
        runCurrent()
        assertEquals(ChatDelivery.FAILED, chat.state.value.entries.single().delivery)
        chat.stop()
    }

    @Test
    fun semRedeContinuaEnviandoSemGastarTentativa() = runTest {
        val c = Cena(this)
        val chat = c.controller()
        chat.start()
        runCurrent()
        repeat(6) { c.server.falhasEnvio += DomainResult.Error(DomainResult.OFFLINE_CODE, "sem rede") }
        chat.sendText("oi")
        runCurrent()
        repeat(6) {
            advanceTimeBy(15_001)
            runCurrent()
        }
        val e = chat.state.value.entries.single()
        assertEquals(ChatDelivery.SENT, e.delivery, "rede voltou: subiu, sem virar 'não enviada'")
        chat.stop()
    }

    @Test
    fun pendenteSobreviveAoProcessoMorto() = runTest {
        val c = Cena(this)
        val chat = c.controller()
        chat.start()
        runCurrent()
        repeat(2) { c.server.falhasEnvio += DomainResult.Error(DomainResult.OFFLINE_CODE, "sem rede") }
        chat.sendText("escrita sem sinal")
        runCurrent()
        chat.stop()
        assertEquals(1, c.store.pendingCount("conta-a"))

        // Outro processo: controller novo sobre o MESMO disco.
        c.server.falhasEnvio.clear()
        val novo = c.controller()
        novo.start()
        runCurrent()
        assertEquals(listOf("m-1"), c.server.mensagens.map { it.id }, "o mesmo id, sem duplicar")
        assertEquals(ChatDelivery.SENT, novo.state.value.entries.single().delivery)
        assertEquals(0, c.store.pendingCount("conta-a"))
        novo.stop()
    }

    @Test
    fun audioSobeUmaVezEMesmoComReenvioNaoSobeDeNovo() = runTest {
        val c = Cena(this)
        val chat = c.controller()
        chat.start()
        runCurrent()
        c.server.falhasEnvio += DomainResult.Error(409, "x", serverCode = "QUALQUER")
        assertTrue(chat.sendAudio(ByteArray(10) { 1 }, durationMillis = 4_200, levels = listOf(0.2f, 0.8f)))
        runCurrent()
        val falhou = chat.state.value.entries.single()
        assertEquals(ChatDelivery.FAILED, falhou.delivery)
        assertEquals("asset-1", falhou.audio?.assetId, "o asset ficou gravado")
        assertEquals(listOf(0.2f, 0.8f), falhou.audio?.levels)

        chat.retry(falhou.id)
        runCurrent()
        assertEquals(1, c.server.uploads)
        assertTrue("upload:5" in c.server.chamadas, "duração arredondada para cima")
        assertEquals(ChatDelivery.SENT, chat.state.value.entries.single().delivery)
        assertTrue(c.blobs.ids().none { it.startsWith("a-") }, "bytes do áudio saem do aparelho ao confirmar")
        chat.stop()
    }

    @Test
    fun descartarApagaDaFilaEOAudio() = runTest {
        val c = Cena(this)
        val chat = c.controller()
        chat.start()
        runCurrent()
        c.server.falhasEnvio += DomainResult.Error(422, "x")
        c.server.falhasEnvio += DomainResult.Error(422, "x")
        chat.sendAudio(ByteArray(3) { 2 }, 1_000)
        runCurrent()
        val id = chat.state.value.entries.single().id
        assertTrue(chat.discard(id))
        assertTrue(chat.state.value.entries.isEmpty())
        assertTrue(c.blobs.ids().isEmpty())
        assertFalse(chat.discard(id))
        chat.stop()
    }

    @Test
    fun marcarComoLidaUmaVezPorMensagemNova() = runTest {
        val c = Cena(this)
        c.server.receber("s1", 10)
        c.server.receber("s2", 20)
        val chat = c.controller()
        chat.start()
        runCurrent()
        chat.markLatestAsRead()
        runCurrent()
        assertEquals("s2", c.server.lidaAte)
        assertEquals(0, chat.state.value.unreadCount)
        assertFalse(chat.state.value.entries.any { it.unread })
        chat.markLatestAsRead()
        runCurrent()
        assertEquals(1, c.server.chamadas.count { it.startsWith("read:") }, "repetir não chama o servidor")
        chat.stop()
    }

    @Test
    fun rolarParaTrasCarregaAsAnteriores() = runTest {
        val c = Cena(this)
        repeat(5) { c.server.receber("s$it", (it + 1) * 10L) }
        val chat = c.controller(ChatConfig(pageSize = 2))
        chat.start()
        runCurrent()
        assertEquals(listOf("s3", "s4"), chat.state.value.entries.map { it.id })
        assertTrue(chat.state.value.hasMoreOlder)
        chat.loadOlder()
        runCurrent()
        assertEquals(listOf("s1", "s2", "s3", "s4"), chat.state.value.entries.map { it.id })
        chat.loadOlder()
        runCurrent()
        assertEquals(5, chat.state.value.entries.size)
        assertFalse(chat.state.value.hasMoreOlder)
        chat.stop()
    }

    @Test
    fun falhaNaCargaMostraErroEOPollingRecupera() = runTest {
        val c = Cena(this)
        c.server.receber("s1", 10)
        c.server.falhasConsulta += DomainResult.Error(503, "fora", serverMessage = "fora")
        val chat = c.controller()
        chat.start()
        runCurrent()
        val s = chat.state.value
        assertTrue(s.loadError != null)
        assertTrue(s.entries.isEmpty())
        advanceTimeBy(5_001)
        assertNull(chat.state.value.loadError)
        assertEquals(1, chat.state.value.entries.size)
        chat.stop()
    }

    @Test
    fun purgaDaContaApagaFilasEAudios() = runTest {
        val c = Cena(this)
        c.store.save("conta-a", "x", listOf(ChatOutgoing("p", ChatMessageKind.TEXT, "t", createdAtMillis = 1)))
        c.store.putAudio("conta-a", "p", byteArrayOf(1))
        c.store.save("conta-b", "x", listOf(ChatOutgoing("q", ChatMessageKind.TEXT, "t", createdAtMillis = 1)))
        assertEquals(2, c.store.purgeAccount("conta-a"))
        assertEquals(0, c.store.pendingCount("conta-a"))
        assertEquals(1, c.store.pendingCount("conta-b"))
        assertTrue(c.blobs.ids().none { it.contains(chatHash("conta-a")) })
    }
}
