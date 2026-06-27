package br.com.codecacto.kmplib.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RestSyncPortTest {

    private val jsonHeader = headersOf("Content-Type", "application/json")

    private data class Captured(
        var url: String? = null,
        var method: String? = null,
        var auth: String? = null,
        var body: String? = null,
    )

    private fun port(
        captured: Captured = Captured(),
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = """{"serverTime":"2026-06-27T00:00:00Z"}""",
        token: String? = "id-token-123",
    ): Pair<RestSyncPort, Captured> {
        val engine = MockEngine { request ->
            captured.url = request.url.toString()
            captured.method = request.method.value
            captured.auth = request.headers["Authorization"]
            captured.body = (request.body as? io.ktor.http.content.TextContent)?.text
            respond(content = responseBody, status = status, headers = jsonHeader)
        }
        val p = RestSyncPort(
            httpClient = HttpClient(engine),
            baseUrl = "https://apps-api.codecacto.com.br",
            pathPrefix = "/sync/v1/meu-fisio",
            tokenProvider = { token },
        )
        return p to captured
    }

    @Test
    fun `pull faz POST na rota pull com Bearer e parseia a resposta`() = runTest {
        val (p, cap) = port(
            responseBody = """
                {"serverTime":"2026-06-27T00:00:00Z","nextCursor":"c1","hasMore":true,
                 "changes":{"frete":[{"serverId":"s1","updatedAt":"2026-06-27T00:00:00Z"}]}}
            """.trimIndent(),
        )
        val resp = p.pull(SyncPullRequest(since = "c0", entities = listOf("frete")))

        assertEquals("POST", cap.method)
        assertEquals("https://apps-api.codecacto.com.br/sync/v1/meu-fisio/pull", cap.url)
        assertEquals("Bearer id-token-123", cap.auth)
        assertEquals("c1", resp.nextCursor)
        assertTrue(resp.hasMore)
        assertEquals(1, resp.changes["frete"]?.size)
        assertTrue(cap.body!!.contains("\"since\":\"c0\""))
    }

    @Test
    fun `push faz POST na rota push e devolve os resultados`() = runTest {
        val (p, cap) = port(
            responseBody = """
                {"serverTime":"2026-06-27T00:00:00Z",
                 "results":[{"clientId":"cli-1","serverId":"srv-1","status":"applied"}]}
            """.trimIndent(),
        )
        val resp = p.push(
            SyncPushRequest(
                ops = listOf(SyncOp(entity = "frete", clientId = "cli-1", op = "create", updatedAt = "2026-06-27T00:00:00Z")),
            ),
        )

        assertEquals("https://apps-api.codecacto.com.br/sync/v1/meu-fisio/push", cap.url)
        assertEquals(1, resp.results.size)
        assertEquals("srv-1", resp.results.first().serverId)
        assertEquals(SyncOpStatus.APPLIED, resp.results.first().status)
        assertTrue(cap.body!!.contains("\"clientId\":\"cli-1\""))
    }

    @Test
    fun `sem token nao envia header Authorization`() = runTest {
        val (p, cap) = port(token = null)
        p.pull(SyncPullRequest())
        assertEquals(null, cap.auth)
    }

    @Test
    fun `erro HTTP lanca para o engine tratar como falha`() = runTest {
        val (p, _) = port(status = HttpStatusCode.InternalServerError, responseBody = "boom")
        assertFailsWith<IllegalStateException> { p.pull(SyncPullRequest()) }
    }
}
