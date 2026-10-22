package br.com.codecacto.kmplib.sync.direct

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** 2.321.0: o cliente do storage não segue 3xx — o arquivo nunca vai a outro host. */
class StorageHttpClientRedirectTest {
    @Test
    fun `307 volta como resposta e o PUT nao e repetido em outro host`() = runTest {
        val hosts = mutableListOf<String>()
        val engine = MockEngine { req ->
            hosts += req.url.host
            if (req.url.host == "r2.example.com") {
                respond("", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, "https://outro.example.net/coleta"))
            } else {
                respond("", HttpStatusCode.OK)
            }
        }
        val client = HttpClient(engine) { storageHttpClientConfig() }
        val r = client.put("https://r2.example.com/b/k?X-Amz-Signature=s") { setBody(byteArrayOf(1, 2, 3)) }
        assertEquals(HttpStatusCode.TemporaryRedirect, r.status)
        assertEquals(listOf("r2.example.com"), hosts)
    }
}
