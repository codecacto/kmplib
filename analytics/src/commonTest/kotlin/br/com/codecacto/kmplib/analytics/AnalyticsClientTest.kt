package br.com.codecacto.kmplib.analytics

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.io.IOException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val WEBSITE = "4f5c2a8e-7d1b-5e3a-9c6f-0a1b2c3d4e5f"
private const val INSTALL = "9b2e8f1c-3a4d-4e5f-8a7b-6c5d4e3f2a1b"

class AnalyticsClientTest {

    private val requests = mutableListOf<Pair<HttpRequestData, String>>()
    private var handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { okBatch() }
    private var now = 1_800_000_000_000L
    private var automation = false
    private var debug = false
    private val lifecycle = mutableListOf<Pair<() -> Unit, () -> Unit>>()

    @AfterTest
    fun limpa() = Analytics.resetForTesting()

    private fun MockRequestHandleScope.okBatch() = respond(
        """{"size":1,"processed":1,"errors":0,"details":[],"cache":"tok"}""",
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun MockRequestHandleScope.okOne() = respond(
        """{"cache":"tok","sessionId":"s","visitId":"v"}""",
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun httpClient(d: CoroutineDispatcher) = HttpClient(
        MockEngine.create {
            dispatcher = d
            addHandler { req ->
            val body = (req.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()
                ?: (req.body as? io.ktor.http.content.TextContent)?.text
                ?: ""
            requests += req to body
            handler(req)
            }
        },
    )

    private fun TestScope.client(
        config: AnalyticsConfig = AnalyticsConfig(websiteId = WEBSITE, projectSlug = "Meu-App"),
        queue: AnalyticsQueue = InMemoryAnalyticsQueue(),
        scope: CoroutineScope = backgroundScope,
        installId: String? = INSTALL,
        device: AnalyticsDevice = AnalyticsDevice(AnalyticsDevice.OS_ANDROID, "14", false, "412x915"),
    ): Pair<AnalyticsClient, AnalyticsQueue> {
        val http = httpClient(StandardTestDispatcher(testScheduler))
        val c = AnalyticsClient(
            config = config,
            queueFactory = { queue },
            transportFactory = { UmamiTransport(http, config.hostUrl, config.collectPath, config.batchPath) },
            scope = scope,
            clock = { now },
            isDebugBuild = { debug },
            isAutomation = { automation },
            installId = { installId },
            device = { device },
            appVersion = { "1.4.2" },
            language = { "pt-BR" },
            registerLifecycle = { fg, bg -> lifecycle += fg to bg; true },
        )
        return c to queue
    }

    private fun payloads(): List<JsonObject> = requests.flatMap { (req, body) ->
        val json = analyticsJson.parseToJsonElement(body)
        val items = if (json is JsonArray) json.map { it.jsonObject } else listOf(json.jsonObject)
        items.map { it["payload"]!!.jsonObject }
    }

    @Test
    fun telaEEvento_vaoNumLoteSoComOQuePrecisa() = runTest {
        val (c, q) = client()
        assertEquals(AnalyticsStatus.ENABLED, c.status)
        assertTrue(c.isEnabled)
        assertEquals(1, lifecycle.size)

        c.trackScreen("clientes/123?aba=x")
        c.trackEvent("assinou", mapOf("plano" to "anual", "email" to "a@b.com"), screen = "paywall")
        runCurrent()
        assertTrue(requests.isEmpty(), "espera a janela do lote")
        advanceTimeBy(6.seconds)
        runCurrent()

        assertEquals(1, requests.size)
        val (req, _) = requests.single()
        assertEquals("https://metricas.codecacto.com.br/api/batch", req.url.toString())
        assertEquals("Mozilla/5.0 (Linux; Android 14; Mobile) CodeCactoApp/1.4.2", req.headers[HttpHeaders.UserAgent])
        assertNull(req.headers[HttpHeaders.Authorization])

        val (tela, evento) = payloads()
        assertEquals(WEBSITE, tela["website"]!!.jsonPrimitive.content)
        assertEquals("meu-app.app", tela["hostname"]!!.jsonPrimitive.content)
        assertEquals("/app/clientes/:id", tela["url"]!!.jsonPrimitive.content)
        assertEquals("clientes/:id", tela["title"]!!.jsonPrimitive.content)
        assertNull(tela["name"])
        assertEquals("pt-BR", tela["language"]!!.jsonPrimitive.content)
        assertEquals("412x915", tela["screen"]!!.jsonPrimitive.content)
        assertEquals("Android OS", tela["os"]!!.jsonPrimitive.content)
        assertEquals("mobile", tela["device"]!!.jsonPrimitive.content)
        assertEquals("app", tela["browser"]!!.jsonPrimitive.content)
        assertEquals("1.4.2", tela["tag"]!!.jsonPrimitive.content)
        assertEquals(INSTALL, tela["id"]!!.jsonPrimitive.content)
        assertEquals(now / 1000, tela["timestamp"]!!.jsonPrimitive.content.toLong())

        assertEquals("assinou", evento["name"]!!.jsonPrimitive.content)
        assertEquals("/app/paywall", evento["url"]!!.jsonPrimitive.content)
        val dados = evento["data"]!!.jsonObject
        assertEquals("anual", dados["plano"]!!.jsonPrimitive.content)
        assertFalse("email" in dados)
        assertEquals(0, q.size())
    }

    @Test
    fun semIdDeWebsite_debugOuSemSlug_naoFazNada() = runTest {
        val (semId, _) = client(AnalyticsConfig(websiteId = null, projectSlug = "x"))
        assertEquals(AnalyticsStatus.NO_WEBSITE_ID, semId.status)
        val (idRuim, _) = client(AnalyticsConfig(websiteId = "nao-e-uuid", projectSlug = "x"))
        assertEquals(AnalyticsStatus.NO_WEBSITE_ID, idRuim.status)
        val (semSlug, _) = client(AnalyticsConfig(websiteId = WEBSITE, projectSlug = " "))
        assertEquals(AnalyticsStatus.NO_PROJECT_SLUG, semSlug.status)
        debug = true
        val (dbg, q) = client()
        assertEquals(AnalyticsStatus.DEBUG_BUILD, dbg.status)
        dbg.trackScreen("home")
        dbg.trackEvent("x")
        advanceUntilIdle()
        assertTrue(requests.isEmpty())
        assertEquals(0, q.size())
        assertEquals(0, dbg.pendingCount())
        assertTrue(dbg.flush())
        assertTrue(lifecycle.isEmpty(), "desligado não registra ciclo de vida")

        val (dbgLigado, _) = client(AnalyticsConfig(websiteId = WEBSITE, projectSlug = "x", sendInDebug = true))
        assertEquals(AnalyticsStatus.ENABLED, dbgLigado.status)
    }

    @Test
    fun automacao_naoEnvia_salvoQuandoPedido() = runTest {
        automation = true
        val (c, q) = client()
        assertFalse(c.isEnabled)
        c.trackScreen("home")
        advanceUntilIdle()
        assertEquals(0, q.size())
        assertTrue(requests.isEmpty())

        val (c2, _) = client(AnalyticsConfig(websiteId = WEBSITE, projectSlug = "x", sendInAutomation = true))
        assertTrue(c2.isEnabled)
    }

    @Test
    fun eventoSemNome_eDescartado() = runTest {
        val (c, q) = client()
        c.trackEvent("  =  ")
        advanceUntilIdle()
        assertEquals(0, q.size())
    }

    @Test
    fun loteCheio_enviaNaHora() = runTest {
        val (c, _) = client(AnalyticsConfig(websiteId = WEBSITE, projectSlug = "x", maxBatchSize = 3))
        repeat(3) { c.trackScreen("t$it") }
        runCurrent()
        assertEquals(1, requests.size)
        assertEquals(3, payloads().size)
    }

    @Test
    fun semRede_ficaNaFila_eReenviaComEsperaDobrando() = runTest {
        var falhas = 2
        handler = {
            if (falhas-- > 0) throw IOException("sem rede") else okBatch()
        }
        val (c, q) = client()
        c.trackScreen("home")
        advanceTimeBy(6.seconds)
        runCurrent()
        assertEquals(1, requests.size)
        assertEquals(1, q.size())
        advanceTimeBy(31.seconds) // 1ª espera: 30 s
        runCurrent()
        assertEquals(2, requests.size)
        assertEquals(1, q.size())
        // 1º envio em t=5 s, 1ª nova tentativa em t=35 s; a 2ª espera (60 s) vence em t=95 s.
        advanceTimeBy(57.seconds) // t=94 s — ainda não
        runCurrent()
        assertEquals(2, requests.size)
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(3, requests.size)
        assertEquals(0, q.size())
    }

    @Test
    fun servidorFora_429e5xx_ficaNaFila() = runTest {
        handler = { respond("", HttpStatusCode.ServiceUnavailable) }
        val (c, q) = client()
        c.trackScreen("home")
        advanceTimeBy(6.seconds)
        runCurrent()
        assertEquals(1, q.size())
        assertFalse(c.flush())
        handler = { respond("", HttpStatusCode.TooManyRequests) }
        assertFalse(c.flush())
        assertEquals(1, q.size())
        handler = { okBatch() }
        assertTrue(c.flush())
        assertEquals(0, q.size())
    }

    @Test
    fun loteNaoSuportado_passaAoEnvioUnitario() = runTest {
        handler = { req ->
            if (req.url.encodedPath == "/api/batch") respond("", HttpStatusCode.NotFound) else okOne()
        }
        val (c, q) = client()
        c.trackScreen("a")
        c.trackScreen("b")
        advanceTimeBy(6.seconds)
        runCurrent()
        assertEquals(listOf("/api/batch", "/api/m", "/api/m"), requests.map { it.first.url.encodedPath })
        assertEquals(0, q.size())
        c.trackScreen("c")
        advanceTimeBy(6.seconds)
        runCurrent()
        assertEquals("/api/m", requests.last().first.url.encodedPath, "não tenta o lote de novo")
    }

    @Test
    fun loteRecusado_isolaUmAUm_eDescartaOInvalido() = runTest {
        handler = { req ->
            when (req.url.encodedPath) {
                "/api/batch" -> respond("""{"error":"bad"}""", HttpStatusCode.BadRequest)
                else -> if (requests.count { it.first.url.encodedPath == "/api/m" } == 1) {
                    respond("""{"error":"bad"}""", HttpStatusCode.BadRequest)
                } else {
                    okOne()
                }
            }
        }
        val (c, q) = client()
        c.trackScreen("a")
        c.trackScreen("b")
        advanceTimeBy(6.seconds)
        runCurrent()
        assertEquals(3, requests.size)
        assertEquals(0, q.size())
        // E segue usando o lote na próxima.
        handler = { okBatch() }
        c.trackScreen("c")
        advanceTimeBy(6.seconds)
        runCurrent()
        assertEquals("/api/batch", requests.last().first.url.encodedPath)
    }

    @Test
    fun envioUnitarioSemRede_paraEFicaNaFila() = runTest {
        val (c, q) = client(AnalyticsConfig(websiteId = WEBSITE, projectSlug = "x", batchPath = null))
        handler = { throw IOException("x") }
        c.trackScreen("a")
        advanceTimeBy(6.seconds)
        runCurrent()
        assertEquals("/api/m", requests.single().first.url.encodedPath)
        assertEquals(1, q.size())
    }

    @Test
    fun loteAceitoComRoboOuErros_saiDaFila() = runTest {
        handler = {
            respond(
                """{"size":2,"processed":1,"errors":1,"details":[{"index":0,"response":{"message":"Website not found."}}]}""",
                HttpStatusCode.OK,
            )
        }
        val (c, q) = client()
        c.trackScreen("a")
        c.trackScreen("b")
        advanceTimeBy(6.seconds)
        runCurrent()
        assertEquals(0, q.size())
        handler = { respond("""{"beep":"boop"}""", HttpStatusCode.OK) }
        val (c2, q2) = client(AnalyticsConfig(websiteId = WEBSITE, projectSlug = "x", batchPath = null))
        c2.trackScreen("a")
        advanceTimeBy(6.seconds)
        runCurrent()
        assertEquals(0, q2.size())
        handler = { respond("nao-json", HttpStatusCode.OK) }
        c.trackScreen("z")
        runCurrent()
        assertTrue(c.flush())
    }

    @Test
    fun eventoVelhoDemais_eDescartadoSemEnviar() = runTest {
        val (c, q) = client()
        c.trackScreen("velho")
        runCurrent()
        now += 8.days.inWholeMilliseconds
        c.trackScreen("novo")
        advanceTimeBy(6.seconds)
        runCurrent()
        assertEquals(listOf("/app/novo"), payloads().map { it["url"]!!.jsonPrimitive.content })
        assertEquals(0, q.size())
    }

    @Test
    fun filaCheia_descartaOMaisAntigo() = runTest {
        handler = { throw IOException("x") }
        val (c, q) = client(AnalyticsConfig(websiteId = WEBSITE, projectSlug = "x", maxQueuedEvents = 3, maxBatchSize = 50))
        repeat(5) {
            c.trackScreen("t$it")
            now += 1
            runCurrent()
        }
        assertEquals(3, q.size())
        assertEquals(listOf("/app/t2", "/app/t3", "/app/t4"), q.oldest(10).map { it.url })
    }

    @Test
    fun cicloDeVida_primeiroPlanoESegundoPlanoEsvaziamAFila() = runTest {
        handler = { throw IOException("x") }
        val (c, q) = client()
        c.trackScreen("a")
        advanceTimeBy(6.seconds)
        runCurrent()
        assertEquals(1, q.size())
        handler = { okBatch() }
        lifecycle.single().first.invoke()
        runCurrent()
        assertEquals(0, q.size())
        c.trackScreen("b")
        runCurrent()
        lifecycle.single().second.invoke()
        runCurrent()
        assertEquals(0, q.size())
    }

    @Test
    fun semIdDeInstalacao_eTabletIOS() = runTest {
        val (c, _) = client(
            AnalyticsConfig(websiteId = WEBSITE, projectSlug = "x", sendInstallId = true),
            installId = null,
            device = AnalyticsDevice(AnalyticsDevice.OS_IOS, "17.5", true, null),
        )
        c.trackScreen("a", title = "  Início ")
        advanceTimeBy(6.seconds)
        runCurrent()
        val p = payloads().single()
        assertNull(p["id"])
        assertNull(p["screen"])
        assertEquals("tablet", p["device"]!!.jsonPrimitive.content)
        assertEquals("iOS", p["os"]!!.jsonPrimitive.content)
        assertEquals("Início", p["title"]!!.jsonPrimitive.content)
        assertEquals("Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) CodeCactoApp/1.4.2", requests.single().first.headers[HttpHeaders.UserAgent])

        val (c2, _) = client(AnalyticsConfig(websiteId = WEBSITE, projectSlug = "x", sendInstallId = false))
        c2.trackScreen("b")
        advanceTimeBy(6.seconds)
        runCurrent()
        assertNull(payloads().last()["id"])
    }

    @Test
    fun singleton_noOpSemInicializar_eDelegaDepois() = runTest {
        Analytics.trackScreen("home")
        Analytics.trackEvent("x")
        assertFalse(Analytics.isEnabled)
        assertTrue(Analytics.flush())
        assertNull(Analytics.client)

        val (c, q) = client()
        Analytics.initialize(c)
        assertTrue(Analytics.isEnabled)
        Analytics.trackScreen("home")
        Analytics.trackEvent("assinou", mapOf("plano" to "mensal"))
        runCurrent()
        assertEquals(2, q.size())
        assertTrue(Analytics.flush())
        assertEquals(0, q.size())

        Analytics.initialize(AnalyticsConfig(websiteId = null, projectSlug = "x"))
        assertFalse(Analytics.isEnabled)
        assertEquals(AnalyticsStatus.NO_WEBSITE_ID, Analytics.client?.status)
    }

    @Test
    fun configPadrao() {
        val c = AnalyticsConfig(websiteId = " ${WEBSITE.uppercase()} ", projectSlug = " LocAki ")
        assertEquals(WEBSITE, c.normalizedWebsiteId)
        assertEquals("locaki.app", c.hostname)
        assertEquals("https://metricas.codecacto.com.br", c.hostUrl)
        assertEquals("/api/m", c.collectPath)
        assertEquals("/api/batch", c.batchPath)
        assertEquals(15.minutes, AnalyticsConfig.MAX_RETRY_DELAY)
    }
}
