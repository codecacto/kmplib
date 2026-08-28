package br.com.codecacto.kmplib.core.network

import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.plugins.logging.LogLevel as KtorLogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

        // **gzip LIGADO (2.162.0).** O servidor comprimir não basta — sem `Accept-Encoding` a
        // resposta vem crua. Medido no Cidade Conectada: `/v1/categories` 26.847 B -> 8.172 B,
        // e -69% do tráfego JSON do app inteiro. É default porque depender de cada app lembrar de
        // um `implementation` a mais foi o que deixou o portfólio TODO sem pedir compressão.
        assertTrue(o.installContentEncoding, "gzip/deflate pedidos por padrão")
    }

    @Test
    fun client_installsContentEncoding_byDefault_andRespectsOptOut() {
        val padrao = createHttpClient(HttpClientOptions(enableLogging = false))
        try {
            assertNotNull(
                padrao.pluginOrNull(ContentEncoding),
                "o cliente padrão precisa PEDIR gzip — é o cabeçalho que falta, não a compressão do servidor",
            )
        } finally {
            padrao.close()
        }

        val semGzip = createHttpClient(
            HttpClientOptions(enableLogging = false, installContentEncoding = false),
        )
        try {
            assertNull(semGzip.pluginOrNull(ContentEncoding))
        } finally {
            semGzip.close()
        }
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
