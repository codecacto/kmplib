package br.com.codecacto.kmplib.modalevents

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ModalEventsTest {

    private val http = HttpClient(MockEngine { respond("", HttpStatusCode.Accepted) })

    private class Fake : ModalEventsRecorder {
        val batches = mutableListOf<ModalEventBatch>()
        override suspend fun send(batch: ModalEventBatch): Boolean {
            batches += batch
            return true
        }
    }

    private fun TestScope.client(
        rec: ModalEventsRecorder,
        automation: Boolean = false,
        debug: Boolean = false,
        installId: String? = "0b3c1d2e-4f5a-4b6c-8d7e-9f0a1b2c3d4e",
        appVersion: String? = "1.4.0",
    ) = ModalEventsClient(
        config = ModalEventsConfig(projectSlug = "locaki", httpClient = http, appVersion = appVersion, flushDelay = 3.seconds),
        recorder = rec,
        scope = backgroundScope,
        installId = { installId },
        isAutomation = { automation },
        isDebugBuild = { debug },
        clock = { 1_761_750_000_000L },
        platform = "android",
    )

    /** O coletor roda no `backgroundScope`, que o `advanceUntilIdle` não espera: anda o relógio virtual. */
    private fun TestScope.settle() {
        advanceTimeBy(10_000)
        runCurrent()
    }

    @AfterTest
    fun limpa() = ModalEvents.resetForTesting()

    @Test
    fun juntaEmLoteENaJanela() = runTest(StandardTestDispatcher()) {
        val rec = Fake()
        val c = client(rec)
        c.record(ModalEventType.ANNOUNCEMENT_SHOWN, refId = "a1")
        c.record(ModalEventType.ANNOUNCEMENT_ACTION_CLICK, refId = "a1")
        runCurrent()
        assertTrue(rec.batches.isEmpty(), "espera a janela")
        advanceTimeBy(3_001)
        runCurrent()
        assertEquals(1, rec.batches.size)
        val b = rec.batches.single()
        assertEquals("locaki", b.project)
        assertEquals("android", b.platform)
        assertEquals("1.4.0", b.appVersion)
        assertEquals("0b3c1d2e-4f5a-4b6c-8d7e-9f0a1b2c3d4e", b.installId)
        assertFalse(b.debug)
        assertEquals(listOf("shown", "action_click"), b.events.map { it.event })
        assertTrue(b.events.all { it.surface == "announcement" && it.refId == "a1" && it.trigger == null })
        assertEquals(1_761_750_000_000L, b.events.first().occurredAt)
    }

    @Test
    fun cinquentaEventosDisparamNaHoraEmLotesDe50() = runTest(StandardTestDispatcher()) {
        val rec = Fake()
        val c = client(rec)
        repeat(120) { c.record(ModalEventType.REVIEW_SHOWN) }
        settle()
        assertTrue(rec.batches.all { it.events.size in 1..50 })
        assertEquals(120, rec.batches.sumOf { it.events.size })
    }

    @Test
    fun automacaoNaoRegistra() = runTest(StandardTestDispatcher()) {
        val rec = Fake()
        val c = client(rec, automation = true)
        c.record(ModalEventType.REVIEW_SHOWN)
        settle()
        assertTrue(rec.batches.isEmpty())
    }

    @Test
    fun comunicadoSemRefIdEDescartado() = runTest(StandardTestDispatcher()) {
        val rec = Fake()
        val c = client(rec)
        c.record(ModalEventType.ANNOUNCEMENT_SHOWN, refId = " ")
        settle()
        assertTrue(rec.batches.isEmpty())
    }

    @Test
    fun avaliacaoLevaTriggerESemRefId() = runTest(StandardTestDispatcher()) {
        val rec = Fake()
        val c = client(rec, debug = true, installId = null, appVersion = null)
        c.record(ModalEventType.REVIEW_POSITIVE, refId = "ignorado", trigger = "locacao_finalizada")
        c.record(ModalEventType.REVIEW_DISMISSED, trigger = "  ")
        settle()
        val b = rec.batches.single()
        assertTrue(b.debug)
        assertNull(b.installId)
        assertEquals("review", b.events[0].surface)
        assertEquals("positive", b.events[0].event)
        assertNull(b.events[0].refId)
        assertEquals("locacao_finalizada", b.events[0].trigger)
        assertNull(b.events[1].trigger)
    }

    @Test
    fun recorderQueLancaNaoQuebra() = runTest(StandardTestDispatcher()) {
        val c = client(ModalEventsRecorder { error("boom") })
        c.record(ModalEventType.REVIEW_SHOWN)
        settle()
        assertEquals(0, c.pendingCount)
    }

    @Test
    fun semInitializeEhNoop() {
        ModalEvents.record(ModalEventType.REVIEW_SHOWN)
        assertFalse(ModalEvents.isInitialized)
    }

    @Test
    fun cadaEventoNaSuaSuperficie() {
        val nomes = ModalEventType.entries.groupBy({ it.surface }, { it.wireName })
        assertEquals(listOf("shown", "action_click", "dismiss_gotit", "dismiss_close"), nomes[ModalSurface.ANNOUNCEMENT])
        assertEquals(listOf("shown", "positive", "negative", "feedback_sent", "dismissed"), nomes[ModalSurface.REVIEW])
    }

    // ====== REST ======

    @Test
    fun restMandaOCorpoDoContrato() = runTest {
        var url = ""
        var body = ""
        val engine = MockEngine { request ->
            url = request.url.toString()
            body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond("", HttpStatusCode.Accepted)
        }
        val ok = RestModalEventsRecorder(HttpClient(engine), "https://apps-api.example/").send(
            ModalEventBatch(
                project = "locaki",
                platform = "ios",
                appVersion = "1.4.0",
                installId = null,
                debug = false,
                events = listOf(ModalEventPayload("review", "shown", null, null, 5L)),
            ),
        )
        assertTrue(ok)
        assertEquals("https://apps-api.example/public/modal-events", url)
        val json = Json.parseToJsonElement(body) as JsonObject
        assertEquals("locaki", json["project"]!!.jsonPrimitive.content)
        assertEquals("ios", json["platform"]!!.jsonPrimitive.content)
        assertEquals(false, json["debug"]!!.jsonPrimitive.boolean)
        val ev = json["events"]!!.jsonArray.single().jsonObject
        assertEquals(JsonNull, ev["refId"])
        assertEquals(JsonNull, ev["trigger"])
        assertEquals(5L, ev["occurredAt"]!!.jsonPrimitive.long)
    }

    @Test
    fun restFalhaDevolveFalseSemLancar() = runTest {
        val rec = RestModalEventsRecorder(HttpClient(MockEngine { respondError(HttpStatusCode.TooManyRequests) }))
        assertFalse(rec.send(ModalEventBatch("x", "android", events = listOf(ModalEventPayload("review", "shown", occurredAt = 1L)))))
    }
}
