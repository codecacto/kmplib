package br.com.codecacto.kmplib.sync.rest

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Upload/download com teto de tempo próprio e nome de arquivo escapado no multipart — 2.311.0. */
class DomainApiClientUploadTimeoutTest {

    private class Rec {
        var timeout: Long? = null
        var socket: Long? = null
        var temCapacidade = false
        var corpo: OutgoingContent? = null
    }

    private fun cliente(rec: Rec, comPlugin: Boolean): DomainApiClient {
        val engine = MockEngine { request ->
            val cfg = request.getCapabilityOrNull(HttpTimeoutCapability)
            rec.temCapacidade = cfg != null
            rec.timeout = cfg?.requestTimeoutMillis
            rec.socket = cfg?.socketTimeoutMillis
            rec.corpo = request.body
            respond("""{"data":[]}""", HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val http = HttpClient(engine) {
            if (comPlugin) install(HttpTimeout) { requestTimeoutMillis = 30_000 }
        }
        return DomainApiClient(http, DomainTokenProvider { "t" }, "https://api.example.com")
    }

    private val parte = MultipartPart("arquivo", "Contrato \"assinado\".pdf", byteArrayOf(1, 2), "application/pdf")

    @Test
    fun `upload leva o teto proprio da chamada`() = runTest {
        val rec = Rec()
        val r = cliente(rec, comPlugin = true)
            .postMultipartParts("/v2/clientes/1/anexos", listOf(parte), emptyMap(), timeoutMillis = 300_000)
        assertIs<DomainResult.Success<String>>(r)
        assertEquals(300_000L, rec.timeout)
        assertEquals(300_000L, rec.socket)
    }

    @Test
    fun `sem o plugin HttpTimeout o teto e ignorado e a chamada segue`() = runTest {
        val rec = Rec()
        val r = cliente(rec, comPlugin = false)
            .postMultipartParts("/v2/clientes/1/anexos", listOf(parte), emptyMap(), timeoutMillis = 300_000)
        assertIs<DomainResult.Success<String>>(r)
        assertTrue(!rec.temCapacidade)
        assertNull(rec.timeout)
    }

    @Test
    fun `download com teto proprio`() = runTest {
        val rec = Rec()
        val r = cliente(rec, comPlugin = true).getBytes("/v2/clientes/1/anexos/2/conteudo", timeoutMillis = 120_000)
        assertIs<DomainResult.Success<ByteArray>>(r)
        assertEquals(120_000L, rec.timeout)
    }

    @Test
    fun `nome de arquivo com aspas e quebra de linha e escapado como no navegador`() {
        assertEquals("Contrato %22assinado%22.pdf", multipartFileNameValue("Contrato \"assinado\".pdf"))
        assertEquals("a%0D%0Ab.txt", multipartFileNameValue("a\r\nb.txt"))
        assertEquals("CNH João.jpg", multipartFileNameValue("CNH João.jpg"))
    }
}
