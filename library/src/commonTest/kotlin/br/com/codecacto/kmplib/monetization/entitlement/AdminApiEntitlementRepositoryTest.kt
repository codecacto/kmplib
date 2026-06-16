package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.core.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminApiEntitlementRepositoryTest {

    private val jsonHeader = headersOf("Content-Type", "application/json")

    private fun repo(
        token: String? = null,
        cacheTtl: Long = 0L,
        pathPrefix: String? = null,
        handler: (path: String, authHeader: String?) -> Pair<HttpStatusCode, String>,
    ): AdminApiEntitlementRepository {
        val engine = MockEngine { request ->
            val auth = request.headers["Authorization"]
            val (status, body) = handler(request.url.encodedPath, auth)
            respond(content = body, status = status, headers = jsonHeader)
        }
        return AdminApiEntitlementRepository(
            httpClient = HttpClient(engine),
            baseUrl = "https://admin.codecacto.com.br/",
            projectSlug = "super8",
            authToken = token,
            cacheTtlMillis = cacheTtl,
            pathPrefix = pathPrefix,
        )
    }

    private fun userAuthRepo(
        tokenProvider: () -> String?,
        handler: (path: String, authHeader: String?) -> Pair<HttpStatusCode, String>,
    ): AdminApiEntitlementRepository {
        val engine = MockEngine { request ->
            val auth = request.headers["Authorization"]
            val (status, body) = handler(request.url.encodedPath, auth)
            respond(content = body, status = status, headers = jsonHeader)
        }
        return AdminApiEntitlementRepository.forUserAuth(
            httpClient = HttpClient(engine),
            baseUrl = "https://admin.codecacto.com.br/",
            projectSlug = "super8",
            tokenProvider = tokenProvider,
            cacheTtlMillis = 0L,
        )
    }

    @Test
    fun `getEntitlement 200 decodifica e usa rota correta`() = runTest {
        var seenPath: String? = null
        val r = repo { path, _ ->
            seenPath = path
            HttpStatusCode.OK to """{"plano":"pro","features":["export_pdf"],"fonte":"store"}"""
        }
        val res = r.getEntitlement()
        assertIs<ApiResult.Success<Entitlement>>(res)
        assertEquals("pro", res.data.plano)
        assertTrue(res.data.hasFeature("export_pdf"))
        assertEquals("/v1/super8/entitlement", seenPath)
    }

    @Test
    fun `getUsage 200 decodifica e usa rota com feature`() = runTest {
        var seenPath: String? = null
        val r = repo { path, _ ->
            seenPath = path
            HttpStatusCode.OK to """{"feature":"recibos","contagem":2,"limite":5}"""
        }
        val res = r.getUsage("recibos")
        assertIs<ApiResult.Success<UsageSnapshot>>(res)
        assertEquals(3L, res.data.remaining)
        assertEquals("/v1/super8/usage/recibos", seenPath)
    }

    @Test
    fun `getPlans 200 decodifica lista`() = runTest {
        var seenPath: String? = null
        val r = repo { path, _ ->
            seenPath = path
            HttpStatusCode.OK to
                """[{"plano":"pro","nome":"Pro","preco":"19.90","store_product_id":"pro_m"}]"""
        }
        val res = r.getPlans()
        assertIs<ApiResult.Success<List<Plan>>>(res)
        assertEquals(1, res.data.size)
        assertEquals("pro_m", res.data.first().storeProductId)
        assertEquals("/v1/super8/plans", seenPath)
    }

    @Test
    fun `header Bearer enviado quando token presente`() = runTest {
        var seenAuth: String? = null
        val r = repo(token = "abc123") { _, auth ->
            seenAuth = auth
            HttpStatusCode.OK to """{"plano":"free"}"""
        }
        r.getEntitlement()
        assertEquals("Bearer abc123", seenAuth)
    }

    @Test
    fun `sem token nao envia Authorization`() = runTest {
        var seenAuth: String? = "sentinela"
        val r = repo(token = null) { _, auth ->
            seenAuth = auth
            HttpStatusCode.OK to """{"plano":"free"}"""
        }
        r.getEntitlement()
        assertNull(seenAuth)
    }

    @Test
    fun `402 vira ApiResult Error com code 402`() = runTest {
        val r = repo { _, _ ->
            HttpStatusCode.PaymentRequired to """{"feature":"recibos","limite":5,"contagem":5}"""
        }
        val res = r.getUsage("recibos")
        assertIs<ApiResult.Error>(res)
        assertEquals(402, res.code)
    }

    @Test
    fun `500 vira ApiResult Error de servidor`() = runTest {
        val r = repo { _, _ -> HttpStatusCode.InternalServerError to "{}" }
        val res = r.getEntitlement()
        assertIs<ApiResult.Error>(res)
        assertEquals(500, res.code)
    }

    @Test
    fun `cache em memoria evita segunda chamada dentro do TTL`() = runTest {
        var calls = 0
        val r = repo(cacheTtl = 60_000L) { _, _ ->
            calls++
            HttpStatusCode.OK to """{"plano":"pro"}"""
        }
        r.getEntitlement()
        r.getEntitlement()
        assertEquals(1, calls)
    }

    @Test
    fun `pathPrefix explicito monta rota me em entitlement`() = runTest {
        var seenPath: String? = null
        val r = repo(pathPrefix = "/v1/projects/super8/me") { path, _ ->
            seenPath = path
            HttpStatusCode.OK to """{"plano":"pro","features":["export_pdf"]}"""
        }
        val res = r.getEntitlement()
        assertIs<ApiResult.Success<Entitlement>>(res)
        assertEquals("/v1/projects/super8/me/entitlement", seenPath)
    }

    @Test
    fun `forUserAuth monta as tres rotas me e envia Firebase ID token`() = runTest {
        var seenPath: String? = null
        var seenAuth: String? = null
        val r = userAuthRepo(tokenProvider = { "firebase-id-token" }) { path, auth ->
            seenPath = path
            seenAuth = auth
            when {
                path.endsWith("/usage/recibos") ->
                    HttpStatusCode.OK to """{"feature":"recibos","contagem":1,"limite":5}"""
                path.endsWith("/plans") ->
                    HttpStatusCode.OK to """[{"plano":"pro","nome":"Pro"}]"""
                else -> HttpStatusCode.OK to """{"plano":"free"}"""
            }
        }

        r.getEntitlement()
        assertEquals("/v1/projects/super8/me/entitlement", seenPath)
        assertEquals("Bearer firebase-id-token", seenAuth)

        r.getUsage("recibos")
        assertEquals("/v1/projects/super8/me/usage/recibos", seenPath)

        r.getPlans()
        assertEquals("/v1/projects/super8/me/plans", seenPath)
    }

    @Test
    fun `cache nunca mascara erro - erro nao e cacheado`() = runTest {
        var calls = 0
        val r = repo(cacheTtl = 60_000L) { _, _ ->
            calls++
            HttpStatusCode.InternalServerError to "{}"
        }
        r.getEntitlement()
        r.getEntitlement()
        // erro nao entra no cache -> refaz a chamada (nunca concede offline)
        assertEquals(2, calls)
    }
}
