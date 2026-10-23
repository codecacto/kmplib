package br.com.codecacto.kmplib.presence

import br.com.codecacto.kmplib.core.central.CentralServices
import br.com.codecacto.kmplib.core.central.CentralServicesConfig
import br.com.codecacto.kmplib.core.prefs.InMemoryAppPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive

class AppPresenceTest {

    private class Recorder {
        val bodies = mutableListOf<JsonObject>()
        val urls = mutableListOf<String>()
        val methods = mutableListOf<String>()
    }

    private var day = LocalDate(2026, 10, 23)
    private val prefs = InMemoryAppPreferences()

    private fun presence(
        rec: Recorder = Recorder(),
        paying: (suspend () -> Boolean?)? = null,
        automation: Boolean = false,
        sendInAutomation: Boolean = false,
        respondWith: suspend MockRequestHandleScope.() -> HttpResponseData = { respond("", HttpStatusCode.NoContent) },
    ): AppPresenceClient {
        val engine = MockEngine { request ->
            rec.urls += request.url.toString()
            rec.methods += request.method.value
            val text = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            rec.bodies += Json.parseToJsonElement(text) as JsonObject
            respondWith()
        }
        return AppPresenceClient(
            config = AppPresenceConfig(
                projectSlug = "meu-app",
                httpClient = HttpClient(engine),
                appsApiBaseUrl = "https://apps-api.example/",
                payingProvider = paying,
                payingTimeout = 200.milliseconds,
                sendInAutomation = sendInAutomation,
            ),
            preferences = prefs,
            today = { day },
            isAutomation = { automation },
            platform = "android",
        )
    }

    @Test
    fun envia_uma_vez_por_dia() = runTest {
        val rec = Recorder()
        val client = presence(rec)

        assertEquals(AppPresenceResult.SENT, client.sendIfDue())
        assertEquals(AppPresenceResult.ALREADY_SENT_TODAY, client.sendIfDue())
        assertEquals(AppPresenceResult.ALREADY_SENT_TODAY, client.sendIfDue())

        assertEquals(1, rec.urls.size)
        assertEquals("https://apps-api.example/public/presence", rec.urls.single())
        assertEquals("POST", rec.methods.single())
        assertEquals("2026-10-23", prefs.getString(AppPresenceClient.KEY_LAST_SENT_DAY))
    }

    @Test
    fun corpo_so_tem_projeto_plataforma_e_pagante() = runTest {
        val rec = Recorder()
        presence(rec).sendIfDue()

        val body = rec.bodies.single()
        assertEquals(setOf("project", "platform", "pagante"), body.keys)
        assertEquals("meu-app", body["project"]!!.jsonPrimitive.content)
        assertEquals("android", body["platform"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, body["pagante"])
    }

    @Test
    fun virada_de_dia_envia_de_novo() = runTest {
        val rec = Recorder()
        val client = presence(rec)

        assertEquals(AppPresenceResult.SENT, client.sendIfDue())
        day = LocalDate(2026, 10, 24)
        assertEquals(AppPresenceResult.SENT, client.sendIfDue())
        assertEquals(AppPresenceResult.ALREADY_SENT_TODAY, client.sendIfDue())

        assertEquals(2, rec.urls.size)
        assertEquals("2026-10-24", prefs.getString(AppPresenceClient.KEY_LAST_SENT_DAY))
    }

    @Test
    fun data_gravada_sobrevive_a_novo_processo() = runTest {
        val rec = Recorder()
        presence(rec).sendIfDue()
        // Outro cliente (processo novo) com as mesmas preferências: não envia de novo no mesmo dia.
        assertEquals(AppPresenceResult.ALREADY_SENT_TODAY, presence(rec).sendIfDue())
        assertEquals(1, rec.urls.size)
    }

    @Test
    fun falha_http_nao_grava_a_data_e_tenta_na_proxima() = runTest {
        var fail = true
        val rec = Recorder()
        val client = presence(rec) {
            if (fail) respondError(HttpStatusCode.TooManyRequests) else respond("", HttpStatusCode.NoContent)
        }

        assertEquals(AppPresenceResult.FAILED, client.sendIfDue())
        assertFalse(prefs.has(AppPresenceClient.KEY_LAST_SENT_DAY))
        assertEquals(1, rec.urls.size, "falha não repete sozinha")

        fail = false
        assertEquals(AppPresenceResult.SENT, client.sendIfDue())
        assertEquals(2, rec.urls.size)
    }

    @Test
    fun falha_de_rede_nao_grava_a_data() = runTest {
        val client = presence { throw IOException("sem rede") }
        assertEquals(AppPresenceResult.FAILED, client.sendIfDue())
        assertFalse(prefs.has(AppPresenceClient.KEY_LAST_SENT_DAY))
    }

    @Test
    fun pagante_true_false_e_null() = runTest {
        suspend fun paganteCom(provider: (suspend () -> Boolean?)?): JsonObject {
            prefs.clear()
            val rec = Recorder()
            presence(rec, paying = provider).sendIfDue()
            return rec.bodies.single()
        }

        assertTrue(paganteCom { true }["pagante"]!!.jsonPrimitive.boolean)
        assertFalse(paganteCom { false }["pagante"]!!.jsonPrimitive.boolean)
        assertEquals(JsonNull, paganteCom { null }["pagante"])
        assertEquals(JsonNull, paganteCom(null)["pagante"])
        // Provedor que estoura: o envio segue, sem "pagante".
        assertEquals(JsonNull, paganteCom { error("loja fora") }["pagante"])
    }

    @Test
    fun pagante_que_demora_alem_do_teto_vira_null() = runTest {
        val rec = Recorder()
        presence(rec, paying = { awaitCancellation() }).sendIfDue()
        assertEquals(JsonNull, rec.bodies.single()["pagante"])
    }

    @Test
    fun tentativas_simultaneas_fazem_um_envio() = runTest {
        val gate = CompletableDeferred<Unit>()
        val rec = Recorder()
        val client = presence(rec, paying = { gate.await(); true })

        val first = async { client.sendIfDue() }
        testScheduler.runCurrent()
        assertEquals(AppPresenceResult.IN_FLIGHT, client.sendIfDue())
        gate.complete(Unit)
        assertEquals(AppPresenceResult.SENT, first.await())
        assertEquals(1, rec.urls.size)
    }

    @Test
    fun build_de_teste_nao_conta_salvo_se_pedir() = runTest {
        val rec = Recorder()
        assertEquals(AppPresenceResult.SKIPPED_AUTOMATION, presence(rec, automation = true).sendIfDue())
        assertTrue(rec.urls.isEmpty())

        assertEquals(
            AppPresenceResult.SENT,
            presence(rec, automation = true, sendInAutomation = true).sendIfDue(),
        )
    }

    // ------------------------------------------------------------------ amarração no CentralServices

    @BeforeTest
    fun reset() {
        CentralServices.resetForTesting()
        AppPresence.resetForTesting()
        CentralServices.listenToForegroundForPresence = false
    }

    @AfterTest
    fun cleanup() {
        CentralServices.resetForTesting()
        AppPresence.resetForTesting()
        CentralServices.listenToForegroundForPresence = true
    }

    private fun httpClient() = HttpClient(MockEngine { respond("", HttpStatusCode.NoContent) })

    @Test
    fun central_liga_a_presenca_por_padrao() {
        val paying: suspend () -> Boolean? = { true }
        CentralServices.initialize(
            CentralServicesConfig(
                projectSlug = "meu-app",
                httpClient = httpClient(),
                appsApiBaseUrl = "https://apps-api.example",
                presencePayingProvider = paying,
            ),
        )
        val cfg = assertNotNull(AppPresence.client).config
        assertEquals("meu-app", cfg.projectSlug)
        assertEquals("https://apps-api.example", cfg.appsApiBaseUrl)
        assertEquals(paying, cfg.payingProvider)
    }

    @Test
    fun desligado_nao_envia() {
        CentralServices.initialize(
            CentralServicesConfig(projectSlug = "meu-app", httpClient = httpClient(), enablePresence = false),
        )
        assertFalse(AppPresence.isInitialized)
        assertNull(AppPresence.client)
        AppPresence.onForeground() // sem cliente: no-op, não lança
    }
}
