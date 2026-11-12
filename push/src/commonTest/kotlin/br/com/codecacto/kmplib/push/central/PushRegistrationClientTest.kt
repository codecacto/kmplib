package br.com.codecacto.kmplib.push.central

import br.com.codecacto.kmplib.core.prefs.InMemoryAppPreferences
import br.com.codecacto.kmplib.push.PushEvents
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive

class PushRegistrationClientTest {

    private class Recorder {
        val paths = mutableListOf<String>()
        val bodies = mutableListOf<JsonObject>()
    }

    private class FakeDevice(
        var currentToken: String? = "tok-1",
        override val platform: String = "android",
        override val provider: String = "fcm",
        var projectId: String? = "code-cacto",
    ) : PushDeviceSource {
        override suspend fun token(): String? = currentToken
        override fun fcmProjectId(): String? = projectId
    }

    private fun TestScope.client(
        rec: Recorder = Recorder(),
        device: FakeDevice = FakeDevice(),
        prefs: InMemoryAppPreferences = InMemoryAppPreferences(),
        automation: Boolean = false,
        kids: Boolean = false,
        appId: String? = "br.com.codecacto.app",
        fail: Boolean = false,
        loadScope: CoroutineScope = backgroundScope,
    ): PushRegistrationClient {
        val engine = MockEngine { request ->
            val text = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            rec.bodies += Json.parseToJsonElement(text) as JsonObject
            rec.paths += request.url.encodedPath
            if (fail) respondError(HttpStatusCode.ServiceUnavailable) else respond("", HttpStatusCode.NoContent)
        }
        return PushRegistrationClient(
            config = PushRegistrationConfig(
                projectSlug = "meu-app",
                httpClient = HttpClient(engine),
                appsApiBaseUrl = "https://api.test/",
                appId = appId,
                appVersion = "1.2.3",
            ),
            marketing = PushMarketingPreference(prefs, loadScope),
            device = device,
            isAutomation = { automation },
            isKids = { kids },
            languageTag = { "pt-BR" },
        )
    }

    /** O MockEngine responde em outra thread: o `advanceUntilIdle` não espera a rede — espera real. */
    private suspend fun TestScope.eventually(cond: () -> Boolean) {
        repeat(500) {
            advanceUntilIdle()
            if (cond()) return
            withContext(Dispatchers.Default) { delay(10) }
        }
        fail("condição não atingida em 5 s")
    }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

    @Test
    fun registra_com_o_corpo_do_contrato() = runTest {
        val rec = Recorder()
        val c = client(rec)
        assertEquals(PushRegistrationOutcome.REGISTERED, c.register("conta-1"))
        assertEquals(listOf("/push/v1/aparelhos"), rec.paths)
        val body = rec.bodies.single()
        assertEquals("meu-app", body.str("projeto"))
        assertEquals("br.com.codecacto.app", body.str("app"))
        assertEquals("tok-1", body.str("token"))
        assertEquals("android", body.str("plataforma"))
        assertEquals("fcm", body.str("provedor"))
        assertEquals("code-cacto", body.str("projetoFcm"))
        assertEquals("conta-1", body.str("conta"))
        assertEquals("pt-BR", body.str("idioma"))
        assertEquals("1.2.3", body.str("versaoApp"))
        assertTrue(body["publicidade"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun ios_sem_conta_nao_manda_os_opcionais() = runTest {
        val rec = Recorder()
        val c = client(rec, device = FakeDevice(currentToken = "ab12", platform = "ios", provider = "apns", projectId = null))
        c.register(null)
        val body = rec.bodies.single()
        assertEquals("apns", body.str("provedor"))
        assertFalse("projetoFcm" in body)
        assertFalse("conta" in body)
    }

    @Test
    fun mesmo_cadastro_nao_e_reenviado() = runTest {
        val rec = Recorder()
        val c = client(rec)
        c.register("a")
        assertEquals(PushRegistrationOutcome.UNCHANGED, c.register("a"))
        assertEquals(1, rec.paths.size)
        assertEquals(PushRegistrationOutcome.REGISTERED, c.register("b"))
    }

    @Test
    fun sem_token_sem_app_id_e_em_automacao_nao_vai_a_rede() = runTest {
        val rec = Recorder()
        assertEquals(PushRegistrationOutcome.NO_TOKEN, client(rec, device = FakeDevice(currentToken = null)).register("a"))
        assertEquals(PushRegistrationOutcome.SKIPPED_AUTOMATION, client(rec, automation = true).register("a"))
        assertEquals(PushRegistrationOutcome.SKIPPED_AUTOMATION, client(rec, automation = true).unregister())
        assertTrue(rec.paths.isEmpty())
    }

    @Test
    fun falha_de_rede_nao_lanca_e_tenta_de_novo() = runTest {
        val rec = Recorder()
        val c = client(rec, fail = true)
        assertEquals(PushRegistrationOutcome.FAILED, c.register("a"))
        assertEquals(PushRegistrationOutcome.FAILED, c.register("a"))
        assertEquals(2, rec.paths.size)
    }

    @Test
    fun publicidade_desligada_e_app_infantil_mandam_false() = runTest {
        val prefs = InMemoryAppPreferences()
        prefs.setBoolean(PushMarketingPreference.KEY, false)
        val rec = Recorder()
        client(rec, prefs = prefs).register("a")
        assertFalse(rec.bodies.last()["publicidade"]!!.jsonPrimitive.boolean)

        client(rec, kids = true).register("a")
        assertFalse(rec.bodies.last()["publicidade"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun remover_usa_o_ultimo_token_cadastrado() = runTest {
        val rec = Recorder()
        val device = FakeDevice()
        val c = client(rec, device = device)
        c.register("a")
        device.currentToken = "tok-2"
        assertEquals(PushRegistrationOutcome.REMOVED, c.unregister())
        assertEquals("/push/v1/aparelhos/remover", rec.paths.last())
        assertEquals("tok-1", rec.bodies.last().str("token"))
        assertEquals("meu-app", rec.bodies.last().str("projeto"))
        assertEquals(2, rec.bodies.last().size)
        // Depois da remoção, o mesmo cadastro volta a ser enviado.
        assertEquals(PushRegistrationOutcome.REGISTERED, c.register("a"))
    }

    @Test
    fun bind_registra_no_login_remove_no_logout_e_reage_a_token_e_publicidade() = runTest {
        val rec = Recorder()
        val device = FakeDevice()
        val c = client(rec, device = device)
        val accounts = MutableStateFlow<String?>(null)
        val before = PushEvents.listenerCount
        val job = launch { c.bind(accounts) }
        eventually { PushEvents.listenerCount == before + 1 }
        assertTrue(rec.paths.isEmpty(), "abrir sem sessão não cadastra nem remove")

        accounts.value = "u1"
        eventually { rec.paths.size == 1 }
        assertEquals("/push/v1/aparelhos", rec.paths.last())
        assertEquals("u1", rec.bodies.last().str("conta"))

        c.marketing.setEnabled(false)
        eventually { rec.paths.size == 2 }
        assertFalse(rec.bodies.last()["publicidade"]!!.jsonPrimitive.boolean)

        device.currentToken = "tok-novo"
        PushEvents.hub.onNewToken("tok-novo")
        eventually { rec.paths.size == 3 }
        assertEquals("tok-novo", rec.bodies.last().str("token"))

        accounts.value = null
        eventually { rec.paths.size == 4 }
        assertEquals("/push/v1/aparelhos/remover", rec.paths.last())

        job.cancel()
        eventually { PushEvents.listenerCount == before }
    }

    @Test
    fun bind_sem_login_cadastra_sem_conta() = runTest {
        val rec = Recorder()
        val c = client(rec)
        val job = launch { c.bind(MutableStateFlow(null), registerSignedOut = true) }
        eventually { rec.paths.size == 1 }
        assertFalse("conta" in rec.bodies.single())
        job.cancel()
    }

    @Test
    fun objeto_sem_inicializar_responde_not_initialized() = runTest {
        PushRegistration.resetForTesting()
        assertEquals(PushRegistrationOutcome.NOT_INITIALIZED, PushRegistration.register("a"))
        assertNull(PushRegistration.client)
    }
}
