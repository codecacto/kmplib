package br.com.codecacto.kmplib.ui.tour

import br.com.codecacto.kmplib.sync.rest.DomainApiClient
import br.com.codecacto.kmplib.sync.rest.DomainTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RestProductTourAccountStoreTest {

    private class Rec {
        val calls = mutableListOf<String>()
    }

    private fun store(rec: Rec, status: HttpStatusCode = HttpStatusCode.OK, body: String = """{"seen":["tour.inicio.v1"]}"""): RestProductTourAccountStore {
        val engine = MockEngine { req ->
            rec.calls += "${req.method.value} ${req.url.encodedPath}"
            if (req.method.value == "PUT") respond("", HttpStatusCode.NoContent)
            else respond(body, status, headersOf("Content-Type", "application/json"))
        }
        val api = DomainApiClient(HttpClient(engine), DomainTokenProvider { "tok" }, "https://api.example.com")
        return RestProductTourAccountStore(api, "/locaki/v1/me/tours/seen")
    }

    @Test
    fun `le a lista uma vez e responde por chave`() = runTest {
        val rec = Rec()
        val s = store(rec)
        assertTrue(s.isSeen("tour.inicio.v1"))
        assertFalse(s.isSeen("welcome"))
        assertEquals(listOf("GET /locaki/v1/me/tours/seen"), rec.calls)
    }

    @Test
    fun `marca com PUT na chave e a leitura seguinte ja ve`() = runTest {
        val rec = Rec()
        val s = store(rec)
        assertFalse(s.isSeen("welcome"))
        s.markSeen("welcome")
        assertTrue(s.isSeen("welcome"))
        assertEquals(listOf("GET /locaki/v1/me/tours/seen", "PUT /locaki/v1/me/tours/seen/welcome"), rec.calls)
    }

    @Test
    fun `falha na leitura lanca`() = runTest {
        assertFailsWith<ProductTourStoreException> { store(Rec(), HttpStatusCode.InternalServerError, "{}").isSeen("welcome") }
        assertFailsWith<ProductTourStoreException> { store(Rec(), body = "[]").isSeen("welcome") }
    }

    @Test
    fun `parse do corpo`() {
        assertEquals(setOf("a", "b"), parseSeenKeys("""{"seen":["a"," b ",1]}"""))
        assertNull(parseSeenKeys("""{"vistos":[]}"""))
        assertNull(parseSeenKeys("x"))
    }
}
