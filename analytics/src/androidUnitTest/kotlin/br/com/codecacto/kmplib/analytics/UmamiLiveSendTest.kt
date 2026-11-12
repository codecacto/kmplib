package br.com.codecacto.kmplib.analytics

import br.com.codecacto.kmplib.core.network.HttpClientOptions
import br.com.codecacto.kmplib.core.network.createHttpClient
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/**
 * Envio REAL ao Umami — só roda com `KMPLIB_UMAMI_LIVE_WEBSITE=<uuid>` no ambiente (nunca no CI).
 * Serve para provar, contra o servidor de verdade, que o corpo e o User-Agent da lib são gravados
 * (e não descartados como robô). Os eventos vão com `hostname = kmplib-teste.app` para serem apagados
 * depois (`DELETE FROM website_event WHERE hostname = 'kmplib-teste.app'` + sessão órfã).
 *
 *     KMPLIB_UMAMI_LIVE_WEBSITE=<uuid> ./gradlew :kmplib-analytics:testDebugUnitTest --tests '*UmamiLiveSendTest*'
 */
class UmamiLiveSendTest {

    @Test
    fun enviaDeVerdadeQuandoPedido() {
        val website = System.getenv("KMPLIB_UMAMI_LIVE_WEBSITE")?.takeIf { it.isNotBlank() } ?: return
        val host = System.getenv("KMPLIB_UMAMI_LIVE_HOST") ?: AnalyticsConfig.DEFAULT_HOST_URL
        val config = AnalyticsConfig(websiteId = website, projectSlug = "kmplib-teste", hostUrl = host)
        val http = createHttpClient(HttpClientOptions())
        val resultados = mutableListOf<UmamiSendResult>()
        val transport = UmamiTransport(http, config.hostUrl, config.collectPath, config.batchPath)
        val gravador = object {
            suspend fun lote(bodies: List<kotlinx.serialization.json.JsonObject>, ua: String) =
                transport.sendBatch(bodies, ua).also { resultados += it }
        }
        val client = AnalyticsClient(
            config = config,
            queueFactory = { InMemoryAnalyticsQueue() },
            transportFactory = { transport },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            clock = { System.currentTimeMillis() },
            isDebugBuild = { false },
            isAutomation = { false },
            installId = { "5d0c1b7e-8f3a-4c2d-9e6b-1a2b3c4d5e6f" },
            device = { AnalyticsDevice(AnalyticsDevice.OS_ANDROID, "14", false, "412x915") },
            appVersion = { "0.0.1-teste" },
            language = { "pt-BR" },
            registerLifecycle = null,
        )
        client.trackScreen("teste/inicio")
        client.trackEvent("teste_kmplib", mapOf("origem" to "live-test", "quantidade" to 1, "email" to "nao@vai.com"))
        runBlocking {
            kotlinx.coroutines.delay(500)
            assertTrue(client.flush(), "a fila não esvaziou")
            // Prova o corpo e o UA ao vivo, com a resposta crua do servidor.
            val ua = analyticsUserAgent(AnalyticsDevice(AnalyticsDevice.OS_ANDROID, "14", false, "412x915"), "0.0.1-teste")
            val body = kotlinx.serialization.json.buildJsonObject {
                put("type", kotlinx.serialization.json.JsonPrimitive("event"))
                put(
                    "payload",
                    kotlinx.serialization.json.buildJsonObject {
                        put("website", kotlinx.serialization.json.JsonPrimitive(website))
                        put("hostname", kotlinx.serialization.json.JsonPrimitive("kmplib-teste.app"))
                        put("url", kotlinx.serialization.json.JsonPrimitive("/app/teste/confirmacao"))
                        put("os", kotlinx.serialization.json.JsonPrimitive("Android OS"))
                        put("device", kotlinx.serialization.json.JsonPrimitive("mobile"))
                        put("browser", kotlinx.serialization.json.JsonPrimitive("app"))
                    },
                )
            }
            val r = gravador.lote(listOf(body), ua)
            println("UMAMI-LIVE resultado do lote: $r")
            assertTrue(r is UmamiSendResult.Sent && !r.botDiscarded && r.errors == 0, "o Umami não gravou: $r")
        }
        println("UMAMI-LIVE ok: ${resultados.size} lote(s) de conferência")
    }
}
