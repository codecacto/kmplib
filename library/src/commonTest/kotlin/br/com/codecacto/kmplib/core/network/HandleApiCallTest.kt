package br.com.codecacto.kmplib.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Serializable
private data class FakeDto(val name: String, val age: Int)

class HandleApiCallTest {

    private val json = Json { ignoreUnknownKeys = true }

    // `handleApiCall` é desenhado para Ktor CORE PURO (sem ContentNegotiation):
    // o consumidor desserializa manualmente (kotlinx) e `expectSuccess = true`
    // faz o cliente lançar `ResponseException` em 4xx/5xx — o caminho que a
    // produção realmente trata. O helper reproduz exatamente esse uso.
    private fun clientReturning(
        status: HttpStatusCode,
        body: String,
        contentType: String = "application/json"
    ): HttpClient = HttpClient(MockEngine { _ ->
        respond(
            content = body,
            status = status,
            headers = headersOf("Content-Type", contentType)
        )
    }) {
        expectSuccess = true
    }

    // ====== Success ======

    @Test
    fun `Success quando bloco retorna valor`() = runTest {
        val client = clientReturning(HttpStatusCode.OK, """{"name":"Joao","age":30}""")
        val result = handleApiCall<FakeDto> {
            json.decodeFromString<FakeDto>(client.get("/me").bodyAsText())
        }
        assertIs<ApiResult.Success<FakeDto>>(result)
        assertEquals("Joao", result.data.name)
        assertEquals(30, result.data.age)
    }

    @Test
    fun `Success suporta tipo nao-DTO`() = runTest {
        val client = clientReturning(HttpStatusCode.OK, "raw text", "text/plain")
        val result = handleApiCall<String> {
            client.get("/raw").bodyAsText()
        }
        assertIs<ApiResult.Success<String>>(result)
        assertEquals("raw text", result.data)
    }

    // ====== HTTP error responses (4xx/5xx) ======

    @Test
    fun `Error 401 com mensagem padrao do mapper`() = runTest {
        val client = clientReturning(
            HttpStatusCode.Unauthorized,
            """{"someOther":"field"}"""
        )
        val result = handleApiCall<FakeDto> {
            json.decodeFromString<FakeDto>(client.get("/me").bodyAsText())
        }
        assertIs<ApiResult.Error>(result)
        assertEquals(401, result.code)
        assertEquals("Sessão expirada. Faça login novamente.", result.message)
    }

    @Test
    fun `Error 403 com mensagem padrao do mapper`() = runTest {
        val client = clientReturning(HttpStatusCode.Forbidden, "{}")
        val result = handleApiCall<FakeDto> { json.decodeFromString<FakeDto>(client.get("/x").bodyAsText()) }
        assertIs<ApiResult.Error>(result)
        assertEquals(403, result.code)
        assertEquals("Você não tem permissão para esta ação.", result.message)
    }

    @Test
    fun `Error 404 com mensagem padrao`() = runTest {
        val client = clientReturning(HttpStatusCode.NotFound, "{}")
        val result = handleApiCall<FakeDto> { json.decodeFromString<FakeDto>(client.get("/x").bodyAsText()) }
        assertIs<ApiResult.Error>(result)
        assertEquals(404, result.code)
        assertEquals("Recurso não encontrado.", result.message)
    }

    @Test
    fun `Error 429 com mensagem padrao`() = runTest {
        val client = clientReturning(HttpStatusCode.TooManyRequests, "{}")
        val result = handleApiCall<FakeDto> { json.decodeFromString<FakeDto>(client.get("/x").bodyAsText()) }
        assertIs<ApiResult.Error>(result)
        assertEquals(429, result.code)
        // A frase mudou na 2.161.0: "Muitas requisições. Aguarde um momento." era lida como
        // acusação por quem não fez nada de errado — quem estoura um teto de rate limit ou está num
        // app que pede demais, ou num teto apertado, e os dois são problema nosso.
        assertEquals("O aplicativo está indo rápido demais. Tente de novo em instantes.", result.message)
    }

    @Test
    fun `Error 500 mapeia para mensagem de servidor`() = runTest {
        val client = clientReturning(HttpStatusCode.InternalServerError, "{}")
        val result = handleApiCall<FakeDto> { json.decodeFromString<FakeDto>(client.get("/x").bodyAsText()) }
        assertIs<ApiResult.Error>(result)
        assertEquals(500, result.code)
        assertEquals("Servidor temporariamente indisponível. Tente novamente.", result.message)
    }

    @Test
    fun `Error usa mensagem do backend quando disponivel via campo message`() = runTest {
        val client = clientReturning(
            HttpStatusCode.BadRequest,
            """{"message":"Campo X é obrigatório"}"""
        )
        val result = handleApiCall<FakeDto> { json.decodeFromString<FakeDto>(client.get("/x").bodyAsText()) }
        assertIs<ApiResult.Error>(result)
        assertEquals(400, result.code)
        assertEquals("Campo X é obrigatório", result.message)
    }

    @Test
    fun `Error usa mensagem do backend via campo error como fallback`() = runTest {
        val client = clientReturning(
            HttpStatusCode.BadRequest,
            """{"error":"Bad input"}"""
        )
        val result = handleApiCall<FakeDto> { json.decodeFromString<FakeDto>(client.get("/x").bodyAsText()) }
        assertIs<ApiResult.Error>(result)
        assertEquals("Bad input", result.message)
    }

    @Test
    fun `Error 400 sem message nem error usa mensagem do throwable como fallback`() = runTest {
        val client = clientReturning(HttpStatusCode.BadRequest, "{}")
        val result = handleApiCall<FakeDto> { json.decodeFromString<FakeDto>(client.get("/x").bodyAsText()) }
        assertIs<ApiResult.Error>(result)
        assertEquals(400, result.code)
        // Mensagem do ResponseException ou string default
        assertTrue(result.message.isNotBlank())
    }

    // ====== Serialization / JSON malformado ======

    @Test
    fun `Error de serializacao quando JSON malformado`() = runTest {
        val client = clientReturning(HttpStatusCode.OK, "not even json")
        val result = handleApiCall<FakeDto> {
            json.decodeFromString<FakeDto>(client.get("/bad").bodyAsText())
        }
        assertIs<ApiResult.Error>(result)
        assertEquals(-1, result.code)
        assertEquals("Resposta inválida do servidor", result.message)
    }

    @Test
    fun `Error de serializacao quando campos obrigatorios faltam`() = runTest {
        // FakeDto exige name e age — só name causa SerializationException
        val client = clientReturning(HttpStatusCode.OK, """{"name":"Joao"}""")
        val result = handleApiCall<FakeDto> {
            json.decodeFromString<FakeDto>(client.get("/partial").bodyAsText())
        }
        assertIs<ApiResult.Error>(result)
        assertEquals(-1, result.code)
    }

    // ====== CancellationException ======

    @Test
    fun `CancellationException e re-lancada sem virar Error`() = runTest {
        assertFailsWith<CancellationException> {
            handleApiCall<FakeDto> {
                throw CancellationException("user canceled")
            }
        }
    }

    // ====== Throwable genérico ======

    @Test
    fun `Error generico quando bloco lanca Throwable comum`() = runTest {
        val result = handleApiCall<FakeDto> {
            throw RuntimeException("Unable to resolve host: api.example.com")
        }
        assertIs<ApiResult.Error>(result)
        assertEquals(-1, result.code)
        assertEquals("Não foi possível encontrar o servidor. Verifique sua conexão e tente novamente.", result.message)
    }

    @Test
    fun `Error generico Connection refused`() = runTest {
        val result = handleApiCall<FakeDto> {
            throw RuntimeException("Connection refused")
        }
        assertIs<ApiResult.Error>(result)
        assertEquals("Servidor indisponível. Tente novamente mais tarde.", result.message)
    }

    @Test
    fun `Error generico timeout no message`() = runTest {
        val result = handleApiCall<FakeDto> {
            throw RuntimeException("connect timeout")
        }
        assertIs<ApiResult.Error>(result)
        assertEquals("Tempo de conexão esgotado. Verifique sua internet.", result.message)
    }

    @Test
    fun `Error generico com mensagem desconhecida usa a propria mensagem`() = runTest {
        val result = handleApiCall<FakeDto> {
            throw RuntimeException("custom failure")
        }
        assertIs<ApiResult.Error>(result)
        assertEquals("custom failure", result.message)
    }

    @Test
    fun `Error generico sem mensagem usa Falha de conexao`() = runTest {
        val result = handleApiCall<FakeDto> {
            throw RuntimeException()
        }
        assertIs<ApiResult.Error>(result)
        assertEquals("Falha de conexão", result.message)
    }

    // ====== defaultHttpErrorMessage / mapGenericNetworkMessage isolados ======

    @Test
    fun `defaultHttpErrorMessage status nao mapeado usa fallback`() {
        val msg = defaultHttpErrorMessage(418, fallback = "I'm a teapot")
        assertEquals("I'm a teapot", msg)
    }

    @Test
    fun `defaultHttpErrorMessage status nao mapeado e sem fallback usa mensagem generica`() {
        val msg = defaultHttpErrorMessage(418, fallback = null)
        assertEquals("Erro na requisição", msg)
    }

    @Test
    fun `mapGenericNetworkMessage caso insensitive`() {
        assertEquals(
            "Não foi possível encontrar o servidor. Verifique sua conexão e tente novamente.",
            mapGenericNetworkMessage(RuntimeException("UNABLE TO RESOLVE HOST"))
        )
        assertEquals(
            "Tempo de conexão esgotado. Verifique sua internet.",
            mapGenericNetworkMessage(RuntimeException("Timeout occurred"))
        )
    }
}
