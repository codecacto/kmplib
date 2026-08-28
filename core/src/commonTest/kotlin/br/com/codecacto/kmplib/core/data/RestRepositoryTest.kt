package br.com.codecacto.kmplib.core.data

import br.com.codecacto.kmplib.core.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private data class Request(
    val id: String = "",
    val titulo: String = "",
    val status: String = "open",
)

class RestRepositoryTest {

    private val jsonHeader = headersOf("Content-Type", "application/json")

    private data class Captured(
        var path: String? = null,
        var method: String? = null,
        var query: String? = null,
        var auth: String? = "sentinela",
        var body: String? = null,
    )

    private fun repo(
        token: String? = null,
        cacheTtl: Long = 0L,
        onUnauthorized: (suspend () -> Unit)? = null,
        captured: Captured = Captured(),
        handler: (path: String, method: HttpMethod) -> Pair<HttpStatusCode, String>,
    ): RestRepository<Request, String> {
        val engine = MockEngine { request ->
            captured.path = request.url.encodedPath
            captured.method = request.method.value
            captured.query = request.url.encodedQuery
            captured.auth = request.headers["Authorization"]
            captured.body = (request.body as? io.ktor.http.content.TextContent)?.text
            val (status, body) = handler(request.url.encodedPath, request.method)
            respond(content = body, status = status, headers = jsonHeader)
        }
        val config = RestConfig(
            httpClient = HttpClient(engine),
            baseUrl = "https://api.codecacto.com.br/",
            projectSlug = "meu-advogado",
            tokenProvider = token?.let { { it } },
            onUnauthorized = onUnauthorized,
            cacheTtlMillis = cacheTtl,
        )
        return RestRepository(
            config = config,
            pathPrefix = "/meu-advogado/v1/requests",
            entitySerializer = Request.serializer(),
        )
    }

    @Test
    fun `list 200 decodifica PaginatedResponse e usa rota e paginacao`() = runTest {
        val cap = Captured()
        val r = repo(captured = cap) { _, _ ->
            HttpStatusCode.OK to
                """{"data":[{"id":"1","titulo":"A"},{"id":"2","titulo":"B"}],"page":1,"pageSize":20,"total":2,"totalPages":1}"""
        }
        val res = r.list()
        assertIs<ApiResult.Success<*>>(res)
        val page = (res as ApiResult.Success).data
        assertEquals(2, page.data.size)
        assertEquals("A", page.data.first().titulo)
        assertEquals("/meu-advogado/v1/requests", cap.path)
        assertTrue(cap.query!!.contains("page=1"))
        assertTrue(cap.query!!.contains("pageSize=20"))
    }

    @Test
    fun `list envia filtros como query params`() = runTest {
        val cap = Captured()
        val r = repo(captured = cap) { _, _ ->
            HttpStatusCode.OK to """{"data":[],"page":2,"pageSize":5,"total":0,"totalPages":0}"""
        }
        r.list(filters = mapOf("status" to "open"), page = 2, pageSize = 5)
        assertTrue(cap.query!!.contains("status=open"))
        assertTrue(cap.query!!.contains("page=2"))
        assertTrue(cap.query!!.contains("pageSize=5"))
    }

    @Test
    fun `getById 200 decodifica entidade e monta rota com id`() = runTest {
        val cap = Captured()
        val r = repo(captured = cap) { _, _ ->
            HttpStatusCode.OK to """{"id":"42","titulo":"Caso","status":"closed"}"""
        }
        val res = r.getById("42")
        assertIs<ApiResult.Success<Request>>(res)
        assertEquals("42", res.data.id)
        assertEquals("closed", res.data.status)
        assertEquals("/meu-advogado/v1/requests/42", cap.path)
        assertEquals("GET", cap.method)
    }

    @Test
    fun `create faz POST com body serializado e devolve entidade`() = runTest {
        val cap = Captured()
        val r = repo(captured = cap) { _, _ ->
            HttpStatusCode.Created to """{"id":"novo","titulo":"Novo","status":"open"}"""
        }
        val res = r.create(Request(titulo = "Novo"))
        assertIs<ApiResult.Success<Request>>(res)
        assertEquals("novo", res.data.id)
        assertEquals("POST", cap.method)
        assertEquals("/meu-advogado/v1/requests", cap.path)
        assertTrue(cap.body!!.contains("\"titulo\":\"Novo\""))
    }

    @Test
    fun `update faz PUT na rota com id`() = runTest {
        val cap = Captured()
        val r = repo(captured = cap) { _, _ ->
            HttpStatusCode.OK to """{"id":"7","titulo":"Editado","status":"open"}"""
        }
        val res = r.update("7", Request(id = "7", titulo = "Editado"))
        assertIs<ApiResult.Success<Request>>(res)
        assertEquals("Editado", res.data.titulo)
        assertEquals("PUT", cap.method)
        assertEquals("/meu-advogado/v1/requests/7", cap.path)
    }

    @Test
    fun `delete faz DELETE e devolve Unit`() = runTest {
        val cap = Captured()
        val r = repo(captured = cap) { _, _ -> HttpStatusCode.NoContent to "" }
        val res = r.delete("9")
        assertIs<ApiResult.Success<Unit>>(res)
        assertEquals("DELETE", cap.method)
        assertEquals("/meu-advogado/v1/requests/9", cap.path)
    }

    @Test
    fun `header Bearer enviado quando ha token`() = runTest {
        val cap = Captured()
        val r = repo(token = "id-token-firebase", captured = cap) { _, _ ->
            HttpStatusCode.OK to """{"id":"1"}"""
        }
        r.getById("1")
        assertEquals("Bearer id-token-firebase", cap.auth)
    }

    @Test
    fun `sem token nao envia Authorization`() = runTest {
        val cap = Captured()
        val r = repo(token = null, captured = cap) { _, _ ->
            HttpStatusCode.OK to """{"id":"1"}"""
        }
        r.getById("1")
        assertNull(cap.auth)
    }

    @Test
    fun `404 vira ApiResult Error com code 404`() = runTest {
        val r = repo { _, _ -> HttpStatusCode.NotFound to """{"message":"nao achou"}""" }
        val res = r.getById("x")
        assertIs<ApiResult.Error>(res)
        assertEquals(404, res.code)
    }

    @Test
    fun `500 vira ApiResult Error de servidor`() = runTest {
        val r = repo { _, _ -> HttpStatusCode.InternalServerError to "{}" }
        val res = r.list()
        assertIs<ApiResult.Error>(res)
        assertEquals(500, res.code)
    }

    @Test
    fun `401 dispara onUnauthorized e devolve Error 401`() = runTest {
        var called = false
        val r = repo(onUnauthorized = { called = true }) { _, _ ->
            HttpStatusCode.Unauthorized to """{"message":"sessao expirada"}"""
        }
        val res = r.getById("1")
        assertIs<ApiResult.Error>(res)
        assertEquals(401, res.code)
        assertTrue(called)
    }

    @Test
    fun `cache em memoria evita segunda chamada de getById dentro do TTL`() = runTest {
        var calls = 0
        val r = repo(cacheTtl = 60_000L) { _, _ ->
            calls++
            HttpStatusCode.OK to """{"id":"1","titulo":"A"}"""
        }
        r.getById("1")
        r.getById("1")
        assertEquals(1, calls)
    }

    @Test
    fun `erro nunca e cacheado`() = runTest {
        var calls = 0
        val r = repo(cacheTtl = 60_000L) { _, _ ->
            calls++
            HttpStatusCode.InternalServerError to "{}"
        }
        r.getById("1")
        r.getById("1")
        assertEquals(2, calls)
    }

    @Test
    fun `mutacao invalida o cache de leitura`() = runTest {
        var getCalls = 0
        val r = repo(cacheTtl = 60_000L) { _, method ->
            when (method) {
                HttpMethod.Get -> {
                    getCalls++
                    HttpStatusCode.OK to """{"id":"1","titulo":"A"}"""
                }
                else -> HttpStatusCode.OK to """{"id":"1","titulo":"B"}"""
            }
        }
        r.getById("1")        // chama rede (getCalls=1) e cacheia
        r.getById("1")        // cache (getCalls=1)
        r.update("1", Request(id = "1", titulo = "B")) // limpa cache
        r.getById("1")        // chama rede de novo (getCalls=2)
        assertEquals(2, getCalls)
    }

    @Test
    fun `refresh limpa o cache forcando releitura`() = runTest {
        var calls = 0
        val r = repo(cacheTtl = 60_000L) { _, _ ->
            calls++
            HttpStatusCode.OK to """{"id":"1","titulo":"A"}"""
        }
        r.getById("1")
        r.refresh()
        r.getById("1")
        assertEquals(2, calls)
    }
}
