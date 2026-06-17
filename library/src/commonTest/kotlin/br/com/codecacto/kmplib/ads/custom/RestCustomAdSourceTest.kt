package br.com.codecacto.kmplib.ads.custom

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

class RestCustomAdSourceTest {

    private val jsonHeader = headersOf("Content-Type", "application/json")

    private val body = """
        {
          "ads": [
            { "id": "ad1", "imageUrl": "https://i/1.png", "targetUrl": "https://t/1",
              "title": "App A", "ctaLabel": "Baixar", "placementId": "home_top",
              "priority": 10, "weight": 50 },
            { "id": "ad2", "imageUrl": "https://i/2.png", "targetUrl": "https://t/2",
              "placementId": "home_top", "priority": 5, "weight": 100 }
          ]
        }
    """.trimIndent()

    private data class Captured(var url: String? = null, var method: String? = null, var count: Int = 0)

    private fun source(
        captured: Captured = Captured(),
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = body,
        appId: String = "meu-app",
    ): RestCustomAdSource {
        val engine = MockEngine { request ->
            captured.url = request.url.toString()
            captured.method = request.method.value
            captured.count++
            respond(content = responseBody, status = status, headers = jsonHeader)
        }
        return RestCustomAdSource(httpClient = HttpClient(engine), appId = appId)
    }

    @Test
    fun `fetch faz GET em public ads app appId`() = runTest {
        val cap = Captured()
        source(captured = cap).fetchAds()
        assertEquals("GET", cap.method)
        assertTrue(cap.url!!.contains("/public/ads/app/meu-app"))
    }

    @Test
    fun `placement vira query param quando informado`() = runTest {
        val cap = Captured()
        source(captured = cap).fetchAds(placementId = "home_top")
        assertTrue(cap.url!!.contains("placement=home_top"), "URL: ${cap.url}")
    }

    @Test
    fun `placement nulo nao adiciona query`() = runTest {
        val cap = Captured()
        source(captured = cap).fetchAds(placementId = null)
        assertTrue(!cap.url!!.contains("placement="), "URL: ${cap.url}")
    }

    @Test
    fun `mapeia ads do payload preservando campos`() = runTest {
        val ads = source().fetchAds().getOrThrow()
        assertEquals(2, ads.size)
        val ad1 = ads.first { it.id == "ad1" }
        assertEquals("https://i/1.png", ad1.imageUrl)
        assertEquals("https://t/1", ad1.targetUrl)
        assertEquals("App A", ad1.title)
        assertEquals("Baixar", ad1.ctaLabel)
        assertEquals("home_top", ad1.placementId)
        assertEquals(10, ad1.priority)
        assertEquals(50, ad1.weight)
        assertTrue(ad1.active)
    }

    @Test
    fun `format ausente fica em branco e casa com qualquer formato no selectAd`() = runTest {
        val ads = source().fetchAds().getOrThrow()
        assertTrue(ads.all { it.format.isBlank() })
        // selectAd com format banner deve achar (format em branco casa)
        val banner = selectAd(ads, format = CustomAd.FORMAT_BANNER, placementId = "home_top")
        val interstitial = selectAd(ads, format = CustomAd.FORMAT_INTERSTITIAL, placementId = "home_top")
        assertTrue(banner != null)
        assertTrue(interstitial != null)
    }

    @Test
    fun `observeAds emite uma vez o resultado da busca`() = runTest {
        val emitted = source().observeAds(placementId = "home_top").first()
        assertEquals(2, emitted.size)
    }

    @Test
    fun `erro 500 vira lista vazia best-effort`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
        val src = RestCustomAdSource(httpClient = HttpClient(engine), appId = "meu-app")
        val ads = src.fetchAds().getOrThrow()
        assertTrue(ads.isEmpty())
    }

    @Test
    fun `corpo invalido vira lista vazia best-effort`() = runTest {
        val ads = source(responseBody = "nao e json").fetchAds().getOrThrow()
        assertTrue(ads.isEmpty())
    }
}
