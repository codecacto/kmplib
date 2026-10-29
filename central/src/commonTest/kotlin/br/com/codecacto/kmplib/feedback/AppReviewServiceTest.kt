package br.com.codecacto.kmplib.feedback

import br.com.codecacto.kmplib.platform.ReviewPromptConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppReviewServiceTest {

    private val json = headersOf("Content-Type", "application/json")

    @AfterTest
    fun limpa() = AppReviewService.resetForTesting()

    @Test
    fun leAConfigDoServidor() = runTest {
        var url = ""
        val http = HttpClient(MockEngine { req ->
            url = req.url.toString()
            respond("""{"enabled":false,"triggerCount":5,"remindAfterDays":7,"maxPrompts":2,"novo":1}""", HttpStatusCode.OK, json)
        })
        val c = RestReviewConfigSource(http, "locaki", "https://apps-api.example/", platform = "ios").fetch()
        assertEquals(ReviewPromptConfig(false, 5, 7, 2), c)
        assertTrue(url.startsWith("https://apps-api.example/public/review-config?"), url)
        assertTrue(url.contains("project=locaki") && url.contains("platform=ios"), url)
    }

    @Test
    fun campoFaltandoViraDefault() = runTest {
        val http = HttpClient(MockEngine { respond("""{"triggerCount":4}""", HttpStatusCode.OK, json) })
        assertEquals(ReviewPromptConfig(triggerCount = 4), RestReviewConfigSource(http, "x").fetch())
    }

    @Test
    fun falhaOuLixoDevolveNull() = runTest {
        assertNull(RestReviewConfigSource(HttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }), "x").fetch())
        assertNull(RestReviewConfigSource(HttpClient(MockEngine { respond("<html>", HttpStatusCode.OK) }), "x").fetch())
    }

    @Test
    fun semInitializeNaoHaFonte() {
        assertFalse(AppReviewService.isInitialized)
        assertNull(AppReviewService.configSource())
    }
}
