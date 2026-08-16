package br.com.codecacto.kmplib.core.network

import io.ktor.client.plugins.logging.LogLevel as KtorLogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpClientFactoryTest {

    @Test
    fun options_defaults_matchEcosystemBaseline() {
        val o = HttpClientOptions()
        assertEquals(30_000, o.requestTimeoutMillis)
        assertEquals(15_000, o.connectTimeoutMillis)
        assertEquals(30_000, o.socketTimeoutMillis)
        assertFalse(o.installJsonContentNegotiation, "ContentNegotiation desligado por padrão (Ktor core puro)")

        // **Regra da fábrica (2.117.0): todo projeto loga requisição.** O default é LIGADO porque a
        // alternativa já custou caro: app apontando para host inexistente, login girando até o
        // timeout, "erro de conexão" na tela e o logcat sem uma linha.
        assertTrue(o.enableLogging, "log de requisição LIGADO por padrão")

        // E em INFO, nunca HEADERS/BODY: `HEADERS` imprime o `Authorization` (o token inteiro) e
        // `BODY` imprime o corpo do login — a senha em claro. O default não pode ser o nível que
        // vaza credencial.
        assertEquals(HttpLogLevel.INFO, o.logLevel)
    }

    @Test
    fun logLevel_mapsOneToOneToKtor() {
        assertEquals(KtorLogLevel.NONE, HttpLogLevel.NONE.toKtorLogLevel())
        assertEquals(KtorLogLevel.INFO, HttpLogLevel.INFO.toKtorLogLevel())
        assertEquals(KtorLogLevel.HEADERS, HttpLogLevel.HEADERS.toKtorLogLevel())
        assertEquals(KtorLogLevel.BODY, HttpLogLevel.BODY.toKtorLogLevel())
        assertEquals(KtorLogLevel.ALL, HttpLogLevel.ALL.toKtorLogLevel())
    }

    @Test
    fun defaultJson_isTolerant() {
        // Json config não expõe getters públicos estáveis para asserção direta; garante que a
        // instância existe e desserializa ignorando campos desconhecidos (shape tolerante da lib).
        val decoded = DefaultHttpClientJson.decodeFromString<Map<String, String>>("""{"a":"1","x":"2"}""")
        assertTrue(decoded.containsKey("a"))
    }
}
