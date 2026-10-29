package br.com.codecacto.kmplib.ads.stats

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RestAdStatsRecorderTest {

    private val jsonHeader = headersOf("Content-Type", "application/json")

    private data class Captured(var url: String? = null, var method: String? = null, var body: String? = null)

    private fun recorder(
        captured: Captured = Captured(),
        status: HttpStatusCode = HttpStatusCode.OK,
        isDebugBuild: Boolean = false,
        installId: String? = null,
    ): RestAdStatsRecorder {
        val engine = MockEngine { request ->
            captured.url = request.url.toString()
            captured.method = request.method.value
            captured.body = (request.body as? io.ktor.http.content.TextContent)?.text
            respond(content = "{}", status = status, headers = jsonHeader)
        }
        return RestAdStatsRecorder(
            httpClient = HttpClient(engine),
            isDebugBuild = { isDebugBuild },
            installIdProvider = { installId },
        )
    }

    @Test
    fun `impression faz POST em public ad-stats com impressions 1`() = runTest {
        val cap = Captured()
        recorder(captured = cap).record(
            provider = AdProviderTag.CUSTOM,
            appId = "meu-app",
            format = AdFormat.BANNER,
            adId = "ad1",
            type = AdEventType.IMPRESSION,
        )
        assertEquals("POST", cap.method)
        assertTrue(cap.url!!.endsWith("/public/ad-stats"))
        val body = cap.body!!
        assertTrue(body.contains("\"provider\":\"custom\""), body)
        assertTrue(body.contains("\"appId\":\"meu-app\""), body)
        assertTrue(body.contains("\"format\":\"banner\""), body)
        assertTrue(body.contains("\"adId\":\"ad1\""), body)
        assertTrue(body.contains("\"impressions\":1"), body)
        assertTrue(body.contains("\"clicks\":0"), body)
    }

    @Test
    fun `click envia clicks 1 e impressions 0`() = runTest {
        val cap = Captured()
        recorder(captured = cap).record(
            provider = AdProviderTag.CUSTOM,
            appId = "meu-app",
            format = AdFormat.INTERSTITIAL,
            adId = null,
            type = AdEventType.CLICK,
        )
        val body = cap.body!!
        assertTrue(body.contains("\"provider\":\"custom\""), body)
        assertTrue(body.contains("\"format\":\"interstitial\""), body)
        // adId nulo vira "all" (agregado)
        assertTrue(body.contains("\"adId\":\"all\""), body)
        assertTrue(body.contains("\"impressions\":0"), body)
        assertTrue(body.contains("\"clicks\":1"), body)
    }

    @Test
    fun `build de debug manda debug true para o servidor descartar`() = runTest {
        val cap = Captured()
        recorder(captured = cap, isDebugBuild = true)
            .record(AdProviderTag.CUSTOM, "meu-app", AdFormat.BANNER, "ad1", AdEventType.CLICK)
        assertTrue(cap.body!!.contains("\"debug\":true"), cap.body)
    }

    @Test
    fun `build de release manda debug false`() = runTest {
        val cap = Captured()
        recorder(captured = cap, isDebugBuild = false)
            .record(AdProviderTag.CUSTOM, "meu-app", AdFormat.BANNER, "ad1", AdEventType.IMPRESSION)
        assertTrue(cap.body!!.contains("\"debug\":false"), cap.body)
    }

    @Test
    fun `erro 500 nao lanca best-effort`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
        val rec = RestAdStatsRecorder(httpClient = HttpClient(engine))
        // Nao deve lancar
        rec.record(AdProviderTag.CUSTOM, "x", AdFormat.BANNER, "a", AdEventType.IMPRESSION)
    }

    @Test
    fun `installId vai no corpo quando existe`() = runTest {
        val cap = Captured()
        recorder(captured = cap, installId = "0b3c1d2e-4f5a-4b6c-8d7e-9f0a1b2c3d4e")
            .record(AdProviderTag.CUSTOM, "meu-app", AdFormat.BANNER, "ad1", AdEventType.IMPRESSION)
        assertTrue(cap.body!!.contains("\"installId\":\"0b3c1d2e-4f5a-4b6c-8d7e-9f0a1b2c3d4e\""), cap.body)
    }

    @Test
    fun `sem installId o campo nao aparece`() = runTest {
        val cap = Captured()
        recorder(captured = cap, installId = null)
            .record(AdProviderTag.CUSTOM, "meu-app", AdFormat.BANNER, "ad1", AdEventType.IMPRESSION)
        assertTrue(!cap.body!!.contains("installId"), cap.body)
    }
}
