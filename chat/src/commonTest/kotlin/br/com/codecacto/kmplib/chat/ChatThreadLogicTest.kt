package br.com.codecacto.kmplib.chat

import br.com.codecacto.kmplib.sync.rest.DomainApiClient
import br.com.codecacto.kmplib.sync.rest.DomainResult
import br.com.codecacto.kmplib.sync.rest.DomainTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatThreadLogicTest {

    private fun m(id: String, em: Long, meu: Boolean = false, lida: Long? = null) =
        ChatMessage(id, meu, ChatMessageKind.TEXT, "t", createdAtMillis = em, readByOtherAtMillis = lida)

    @Test
    fun mesclarDeduplicaPorIdOrdenaEMantemOLida() {
        val r = mergeChatMessages(listOf(m("b", 20), m("a", 10, meu = true, lida = 15)), listOf(m("a", 10, meu = true), m("c", 20)))
        assertEquals(listOf("a", "b", "c"), r.map { it.id }, "instante e depois id")
        assertEquals(15L, r.first().readByOtherAtMillis, "lida não volta atrás")
        assertEquals(listOf(m("x", 1)), mergeChatMessages(listOf(m("x", 1)), emptyList()))
    }

    @Test
    fun entradasJuntamConfirmadasEPendentesSemEco() {
        val confirmadas = listOf(m("s1", 10), m("p1", 20, meu = true, lida = 30))
        val pendentes = listOf(
            ChatOutgoing("p1", ChatMessageKind.TEXT, "eco", createdAtMillis = 20),
            ChatOutgoing("p3", ChatMessageKind.TEXT, "c", createdAtMillis = 40, failure = ChatSendFailure(400)),
            ChatOutgoing("p2", ChatMessageKind.AUDIO, audioBlobId = "b", audioDurationMillis = 3_000, createdAtMillis = 35),
        )
        val e = buildChatEntries(confirmadas, pendentes)
        assertEquals(listOf("s1", "p1", "p2", "p3"), e.map { it.id })
        assertEquals(listOf(null, ChatDelivery.READ, ChatDelivery.SENDING, ChatDelivery.FAILED), e.map { it.delivery })
        assertTrue(e[0].unread)
        assertEquals("b", e[2].audio?.localBlobId)
    }

    @Test
    fun naoLidasPrimeiraEUltimaRecebida() {
        val l = listOf(m("a", 1, lida = 2), m("b", 3), m("c", 4, meu = true), m("d", 5))
        assertEquals(2, chatUnreadCount(l))
        assertEquals("b", chatFirstUnreadId(l))
        assertEquals("d", chatLatestIncomingId(l))
        val lidas = markChatReadUpTo(l, "b", 99)
        assertEquals(99L, lidas[1].readByOtherAtMillis)
        assertNull(lidas[3].readByOtherAtMillis, "depois do alvo continua não lida")
        assertNull(lidas[2].readByOtherAtMillis, "a minha não muda")
        assertEquals(l, markChatReadUpTo(l, "zz", 1))
        assertNull(chatLatestIncomingId(listOf(m("c", 4, meu = true))))
    }

    @Test
    fun textoRecuoEDivisorDeDia() {
        assertEquals("oi", normalizeChatText("  oi \n"))
        assertNull(normalizeChatText("   "))
        assertNull(normalizeChatText("x".repeat(CHAT_TEXT_MAX_LENGTH + 1)))
        assertEquals(5_000L, chatPollDelayMillis(0, 5_000, 60_000))
        assertEquals(20_000L, chatPollDelayMillis(2, 5_000, 60_000))
        assertEquals(60_000L, chatPollDelayMillis(10, 5_000, 60_000))
        val e = buildChatEntries(listOf(m("a", 1_000), m("b", 2_000), m("c", 90_000_000)), emptyList())
        val dia = { ms: Long -> ms / 86_400_000L }
        assertEquals(listOf(true, false, true), e.indices.map { chatNeedsDaySeparator(e, it, dia) })
        assertTrue(isRetryableChatError(503))
        assertFalse(isRetryableChatError(409))
    }

    @Test
    fun lerOMessageDtoDoContrato() {
        val o = Json.parseToJsonElement(
            """{"items":[{"id":"1","senderRole":"ALUNO","kind":"AUDIO","text":null,"audioAssetId":"as","audioDurationSeconds":7,
               "createdAt":"2026-10-10T12:00:00Z","readByOtherAt":null},
              {"id":"2","senderRole":"PERSONAL","kind":"PHOTO","createdAt":"2026-10-10T12:00:00Z"},
              {"id":"3","senderRole":"PERSONAL","kind":"TEXT","text":"oi","createdAt":"2026-10-10T12:01:00Z","readByOtherAt":"2026-10-10T12:02:00Z"}],
              "hasMore":true}""",
        ).jsonObject
        val p = parseChatPage(o, myRole = "ALUNO")!!
        assertTrue(p.hasMore)
        assertEquals(listOf("1", "3"), p.messages.map { it.id }, "tipo desconhecido é ignorado")
        assertTrue(p.messages[0].fromMe)
        assertEquals(7_000L, p.messages[0].audioDurationMillis)
        assertNull(p.messages[0].text)
        assertFalse(p.messages[1].fromMe)
        assertEquals(1_791_633_720_000L, p.messages[1].readByOtherAtMillis)
        assertNull(parseChatPage(Json.parseToJsonElement("{}").jsonObject, "ALUNO"))
        assertFalse(p.messages[1].toString().contains("oi"), "sem conteúdo no toString")
    }

    @Test
    fun transporteRestFalaOContrato() = runTest {
        val vistos = mutableListOf<Pair<String, String>>()
        val engine = MockEngine { req ->
            val corpo = (req.body as? TextContent)?.text ?: req.body.contentType?.toString().orEmpty()
            vistos += (req.method.value + " " + req.url.encodedPathAndQuery) to corpo
            val resposta = when {
                req.url.encodedPath.endsWith("/read") -> ""
                req.url.encodedPath.endsWith("/audio") -> """{"id":"asset-9"}"""
                req.method.value == "POST" ->
                    """{"id":"m-1","senderRole":"PERSONAL","kind":"TEXT","text":"oi","createdAt":"2026-10-10T12:00:00Z"}"""
                else -> """{"items":[],"hasMore":false}"""
            }
            respond(resposta, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val api = DomainApiClient(HttpClient(engine), DomainTokenProvider { "tok" }, "https://api.example.com")
        val t = RestChatTransport(api, messagesPath = "/v1/personal/students/al-1/messages/", myRole = "PERSONAL")
        assertIs<DomainResult.Success<ChatPage>>(t.latest(30))
        assertIs<DomainResult.Success<ChatPage>>(t.after("m 5", 30))
        assertIs<DomainResult.Success<ChatPage>>(t.before("m-1", 10))
        val enviada = assertIs<DomainResult.Success<ChatMessage>>(t.send(ChatSendRequest("m-1", ChatMessageKind.TEXT, "oi", null))).data
        assertTrue(enviada.fromMe)
        assertEquals("asset-9", (t.uploadAudio(byteArrayOf(1, 2), 3) as DomainResult.Success).data)
        assertIs<DomainResult.Success<Unit>>(t.markRead("m-1"))

        assertEquals("GET /v1/personal/students/al-1/messages?size=30", vistos[0].first)
        assertEquals("GET /v1/personal/students/al-1/messages?after=m%205&size=30", vistos[1].first)
        assertEquals("GET /v1/personal/students/al-1/messages?before=m-1&size=10", vistos[2].first)
        assertEquals("POST /v1/personal/students/al-1/messages", vistos[3].first)
        assertEquals("""{"id":"m-1","kind":"TEXT","text":"oi"}""", vistos[3].second)
        assertEquals("POST /v1/media/audio", vistos[4].first)
        assertEquals("""{"upToMessageId":"m-1"}""", vistos[5].second)
    }
}
