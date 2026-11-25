package br.com.codecacto.kmplib.fiscal

import br.com.codecacto.kmplib.sync.rest.DomainApiClient
import br.com.codecacto.kmplib.sync.rest.DomainTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FiscalApiTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): FiscalApi {
        val engine = MockEngine { req -> requests += req; handler(req) }
        val client = DomainApiClient(HttpClient(engine), DomainTokenProvider { "tok" }, "https://api.example.com")
        return FiscalApi(client, "/locaki/v2/fiscal/")
    }

    private val json = headersOf("Content-Type", "application/json")

    private fun body(req: HttpRequestData): String = when (val b = req.body) {
        is io.ktor.http.content.TextContent -> b.text
        is OutgoingContent.ByteArrayContent -> b.bytes().decodeToString()
        else -> error("corpo inesperado: ${b::class}")
    }

    @Test
    fun `status sem o modulo vira desabilitado`() = runTest {
        val a = api { respond("""{"message":"Recurso não encontrado","code":"NOT_FOUND"}""", HttpStatusCode.NotFound, json) }
        val r = a.status()
        assertIs<FiscalResult.Ok<FiscalStatusResposta>>(r)
        assertFalse(r.value.habilitado)
        assertEquals("https://api.example.com/locaki/v2/fiscal/status", requests.single().url.toString())
    }

    @Test
    fun `status com emissor e campo desconhecido`() = runTest {
        val a = api {
            respond(
                """{"habilitado":true,"pronto":false,"novo":1,"emissor":{"ref":"u1","pendencias":[{"code":"CERTIFICADO_AUSENTE","message":"Falta o certificado"}]},"pendencias":[{"code":"CERTIFICADO_AUSENTE","message":"Falta o certificado"}]}""",
                HttpStatusCode.OK, json,
            )
        }
        val r = a.status().getOrNull()!!
        assertTrue(r.habilitado)
        assertEquals("CERTIFICADO_AUSENTE", r.emissor?.pendencias?.single()?.code)
    }

    @Test
    fun `emissor nao cadastrado e nulo`() = runTest {
        val a = api { respond("""{"message":"Cadastro fiscal ainda não feito","code":"FISCAL_EMISSOR_NAO_CADASTRADO"}""", HttpStatusCode.NotFound, json) }
        assertEquals(FiscalResult.Ok(null), a.emitter())
    }

    @Test
    fun `emitir manda a Idempotency-Key e omite os nulos`() = runTest {
        val a = api { respond("""{"id":"d1","status":"RECEBIDO"}""", HttpStatusCode.Accepted, json) }
        val pedido = toInvoiceBody(FiscalInvoiceDraft(competencia = "2026-11-25", tipoTomador = TipoTomador.NAO_IDENTIFICADO, valorServico = "10", codigoTributacaoNacional = "010101", descricao = "x"))
        val r = a.issue(pedido, "chave-123456")
        assertEquals("d1", r.getOrNull()?.id)
        val req = requests.single()
        assertEquals("chave-123456", req.headers[IDEMPOTENCY_KEY_HEADER])
        assertEquals("https://api.example.com/locaki/v2/fiscal/notas", req.url.toString())
        val sent = body(req)
        assertTrue("\"servico\":\"10.00\"" in sent, sent)
        assertFalse("null" in sent, sent)
    }

    @Test
    fun `recusa do Fiscal Central chega com o erro no campo`() = runTest {
        val a = api {
            respond(
                """{"message":"Dados inválidos","code":"VALIDACAO","details":{"tomador.documento":"CPF inválido"},"error":{"code":"VALIDACAO","message":"Dados inválidos","fields":{"tomador.documento":"CPF inválido"}}}""",
                HttpStatusCode.BadRequest, json,
            )
        }
        val e = a.preview(toInvoiceBody(FiscalInvoiceDraft(valorServico = "1"))).errorOrNull()!!
        assertEquals(FiscalErrorKind.FIELDS, e.kind)
        assertEquals("CPF inválido", e.fields["tomador.documento"])
        assertEquals("Dados inválidos", e.serverMessage)
    }

    @Test
    fun `lista com filtro na query`() = runTest {
        val a = api { respond("""{"items":[{"id":"1","status":"AUTORIZADO"}],"pagina":1,"tamanho":20,"temMais":true}""", HttpStatusCode.OK, json) }
        val r = a.invoices(FiscalDocumentoFiltro(status = "AUTORIZADO", q = " joão ", pagina = 2)).getOrNull()!!
        assertTrue(r.temMais)
        val url = requests.single().url
        assertEquals("AUTORIZADO", url.parameters["status"])
        assertEquals("joão", url.parameters["q"])
        assertEquals("2", url.parameters["pagina"])
        assertNull(url.parameters["ref"])
    }

    @Test
    fun `sugestao sem registro e nula`() = runTest {
        val a = api { respond("""{"message":"x","code":"FISCAL_SUGESTAO_INDISPONIVEL"}""", HttpStatusCode.NotFound, json) }
        assertEquals(FiscalResult.Ok(null), a.suggestion("locacao:1"))
        assertEquals("locacao:1", requests.single().url.parameters["ref"])
    }

    @Test
    fun `pdf baixa os bytes com nome de arquivo`() = runTest {
        val a = api { respond(byteArrayOf(0x25, 0x50, 0x44, 0x46), HttpStatusCode.OK, headersOf("Content-Type", "application/pdf")) }
        val f = a.pdf("d1", "77").getOrNull()!!
        assertEquals("nfse-77.pdf", f.nome)
        assertEquals(4, f.bytes.size)
        assertEquals("https://api.example.com/locaki/v2/fiscal/notas/d1/pdf", requests.single().url.toString())
    }

    @Test
    fun `certificado vai em multipart com arquivo e senha`() = runTest {
        val a = api { respond("""{"ref":"u1","pronto":true}""", HttpStatusCode.OK, json) }
        val r = a.uploadCertificate(byteArrayOf(1, 2, 3), "empresa.pfx", "segredo")
        assertTrue(r.getOrNull()!!.pronto)
        val req = requests.single()
        assertEquals("PUT", req.method.value)
        assertTrue(req.url.toString().endsWith("/emissor/certificado"))
    }

    @Test
    fun `fora do ar vira indisponivel`() = runTest {
        val a = api { respond("""{"message":"x","code":"FISCAL_INDISPONIVEL"}""", HttpStatusCode.ServiceUnavailable, json) }
        assertEquals(FiscalErrorKind.UNAVAILABLE, a.invoice("d1").errorOrNull()?.kind)
    }
}
