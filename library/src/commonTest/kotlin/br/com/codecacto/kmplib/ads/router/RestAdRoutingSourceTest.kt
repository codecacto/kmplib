package br.com.codecacto.kmplib.ads.router

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RestAdRoutingSourceTest {

    private val jsonHeader = headersOf("Content-Type", "application/json")

    private data class Captured(var url: String? = null, var method: String? = null)

    private fun source(
        captured: Captured = Captured(),
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = """{ "banner": "custom", "interstitial": "off", "appOpen": "off" }""",
    ): RestAdRoutingSource {
        val engine = MockEngine { request ->
            captured.url = request.url.toString()
            captured.method = request.method.value
            respond(content = body, status = status, headers = jsonHeader)
        }
        return RestAdRoutingSource(httpClient = HttpClient(engine))
    }

    @Test
    fun `fetch faz GET em public ad-config appId`() = runTest {
        val cap = Captured()
        source(captured = cap).fetchRouting("meu-app", AdRouting.OFF)
        assertEquals("GET", cap.method)
        assertTrue(cap.url!!.endsWith("/public/ad-config/meu-app"))
    }

    @Test
    fun `desserializa providers do payload`() = runTest {
        val routing = source().fetchRouting("meu-app", AdRouting.OFF).getOrThrow()
        assertEquals(AdProvider.CUSTOM, routing.banner)
        assertEquals(AdProvider.OFF, routing.interstitial)
        assertEquals(AdProvider.OFF, routing.appOpen)
    }

    @Test
    fun `campos ausentes caem no OFF default do modelo`() = runTest {
        val routing = source(body = """{ "banner": "custom" }""").fetchRouting("x", AdRouting.OFF).getOrThrow()
        assertEquals(AdProvider.CUSTOM, routing.banner)
        assertEquals(AdProvider.OFF, routing.interstitial)
        assertEquals(AdProvider.OFF, routing.appOpen)
    }

    @Test
    fun `observeRouting emite uma vez`() = runTest {
        val routing = source().observeRouting("meu-app", AdRouting.OFF).first()
        assertEquals(AdProvider.CUSTOM, routing.banner)
    }

    @Test
    fun `erro 500 cai no default best-effort`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
        val src = RestAdRoutingSource(httpClient = HttpClient(engine))
        val routing = src.fetchRouting("x", AdRouting.ALL_CUSTOM).getOrThrow()
        assertEquals(AdRouting.ALL_CUSTOM, routing)
    }

    @Test
    fun `corpo invalido cai no default best-effort`() = runTest {
        val routing = source(body = "nao e json").fetchRouting("x", AdRouting.ALL_CUSTOM).getOrThrow()
        assertEquals(AdRouting.ALL_CUSTOM, routing)
    }
}
