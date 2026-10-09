package br.com.codecacto.kmplib.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 2.270.0 — o log de requisição (ligado por default, sai no release) NÃO grava a query string:
 * termo de busca é dado pessoal (e-mail, final de telefone, nome).
 */
class HttpRequestLogRedactionTest {

    @Test
    fun redact_absoluteUrl_keepsHostAndPath_dropsQuery() {
        assertEquals(
            "REQUEST: https://api.x.com.br/v1/clientes?…",
            redactHttpLogMessage("REQUEST: https://api.x.com.br/v1/clientes?busca=maria%40x.com&tel=9999"),
        )
    }

    @Test
    fun redact_relativePath_dropsQuery() {
        assertEquals("--> GET /v1/clientes?… (12ms)", redactHttpLogMessage("--> GET /v1/clientes?q=joao (12ms)"))
    }

    @Test
    fun redact_withoutQuery_isUnchanged() {
        val linha = "RESPONSE: 200 OK\nMETHOD: GET\nFROM: https://api.x.com.br/v1/clientes"
        assertEquals(linha, redactHttpLogMessage(linha))
    }

    @Test
    fun redact_isIdempotent() {
        val uma = redactHttpLogMessage("FROM: https://h.com/a?x=1 e /b?y=2")
        assertEquals(uma, redactHttpLogMessage(uma))
        assertEquals("FROM: https://h.com/a?… e /b?…", uma)
    }

    @Test
    fun clientLog_neverContainsQuery_butKeepsMethodPathAndStatus() = runTest {
        val linhas = mutableListOf<Pair<String, String>>()
        val engine = MockEngine { respond("[]", HttpStatusCode.OK) }
        val http = createHttpClient(
            engine,
            HttpClientOptions(retry = HttpRetryPolicy.Disabled),
            logSink = { tag, msg -> linhas += tag to msg },
        )
        http.get("https://api.x.com.br/v1/clientes?busca=maria@x.com&telefone=99998888")
        http.close()

        val tudo = linhas.joinToString("\n") { it.second }
        assertTrue(linhas.isNotEmpty(), "log de requisição continua ligado")
        assertTrue(linhas.all { it.first == HTTP_LOG_TAG })
        assertFalse(tudo.contains("maria"), tudo)
        assertFalse(tudo.contains("99998888"), tudo)
        assertFalse(tudo.contains("busca="), tudo)
        assertTrue(tudo.contains("GET"), tudo)
        assertTrue(tudo.contains("https://api.x.com.br/v1/clientes"), "host + caminho ficam: $tudo")
        assertTrue(tudo.contains("200"), tudo)
    }
}
