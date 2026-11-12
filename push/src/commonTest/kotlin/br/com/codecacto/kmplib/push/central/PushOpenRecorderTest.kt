package br.com.codecacto.kmplib.push.central

import br.com.codecacto.kmplib.core.storage.InMemoryBlobStore
import br.com.codecacto.kmplib.push.PushListenerHub
import br.com.codecacto.kmplib.push.PushNotificationListener
import br.com.codecacto.kmplib.push.action.PushAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class PushOpenRecorderTest {

    private val campanha = "0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b"
    private val outra = "0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5c"

    private class Env {
        val blobs = InMemoryBlobStore()
        val store = BlobPushOpenLedgerStore(blobs)
        val sent = mutableListOf<Pair<String, JsonObject>>()
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()
        var result: PushOpenSendResult = PushOpenSendResult.SENT
        var target: PushOpenTarget? = PushOpenTarget("meu-app", "https://apps-api.example.com/")
        var installId: String? = "11111111-2222-4333-8444-555555555555"
        var now = 1_000_000L
    }

    private fun TestScope.recorder(env: Env) = PushOpenRecorder(
        store = { env.store },
        target = { env.target },
        send = { url, body ->
            env.sent += url to (Json.parseToJsonElement(body) as JsonObject)
            env.result
        },
        platform = { "ios" },
        installId = { env.installId },
        trackEvent = { n, d -> env.events += n to d },
        scope = backgroundScope,
        clock = { env.now },
    )

    /** O recorder roda no `backgroundScope`, que o `advanceUntilIdle` não espera: avança um pouco o relógio. */
    private fun TestScope.esperar() = advanceTimeBy(1.seconds)

    private fun tap(campaign: String?, acao: String = "premium", extra: Map<String, String> = emptyMap()) =
        buildMap {
            put("cc_acao", acao)
            campaign?.let { put("cc_campanha", it) }
            putAll(extra)
        }

    @Test
    fun toque_de_campanha_envia_o_contrato_sem_authorization_e_conta_no_analytics() = runTest {
        val env = Env()
        val r = recorder(env)
        r.onTap(tap(campanha, "link", mapOf("cc_url" to "https://x.com/?email=a@b.com")))
        esperar()

        assertEquals(1, env.sent.size)
        val (url, body) = env.sent.single()
        assertEquals("https://apps-api.example.com/public/push/aberturas", url)
        assertEquals(campanha, body["campanha"]!!.jsonPrimitive.content)
        assertEquals("meu-app", body["projeto"]!!.jsonPrimitive.content)
        assertEquals("ios", body["plataforma"]!!.jsonPrimitive.content)
        assertEquals(env.installId, body["installId"]!!.jsonPrimitive.content)
        assertEquals(setOf("campanha", "projeto", "plataforma", "installId"), body.keys)
        // Só o tipo da ação, nunca a URL.
        assertEquals(listOf("push_aberto" to mapOf<String, Any?>("acao" to "link")), env.events)
        assertTrue(env.store.load().pending.isEmpty())
        assertEquals(listOf(campanha), env.store.load().recorded)
    }

    @Test
    fun sem_install_id_o_campo_nao_vai() = runTest {
        val env = Env().apply { installId = null }
        recorder(env).onTap(tap(campanha))
        esperar()
        assertFalse("installId" in env.sent.single().second)
    }

    @Test
    fun mesma_campanha_conta_uma_vez_so_por_aparelho() = runTest {
        val env = Env()
        val r = recorder(env)
        r.onTap(tap(campanha))
        esperar()
        r.onTap(tap(campanha))
        r.onTap(tap(campanha.uppercase()))
        esperar()
        assertEquals(1, env.sent.size)
        assertEquals(1, env.events.size)
        // Persistido: um recorder novo (app reaberto) também não reconta.
        val r2 = recorder(env)
        r2.onTap(tap(campanha))
        esperar()
        assertEquals(1, env.sent.size)
    }

    @Test
    fun toque_sem_campanha_ou_com_id_invalido_nao_conta() = runTest {
        val env = Env()
        val r = recorder(env)
        assertNull(r.onTap(mapOf("rota" to "pedido/1")))
        assertNull(r.onTap(tap("nao-e-uuid")))
        assertNull(r.onTap(tap("")))
        esperar()
        assertTrue(env.sent.isEmpty())
        assertTrue(env.events.isEmpty())
    }

    @Test
    fun falha_temporaria_fica_na_fila_e_reenvia_com_espera() = runTest {
        val env = Env().apply { result = PushOpenSendResult.RETRY }
        val r = recorder(env)
        r.onTap(tap(campanha))
        advanceTimeBy(1.seconds)
        assertEquals(1, env.sent.size)
        assertEquals(1, env.store.load().pending.size)
        env.result = PushOpenSendResult.SENT
        advanceTimeBy(31.seconds)
        assertEquals(2, env.sent.size)
        assertTrue(env.store.load().pending.isEmpty())
        assertEquals(listOf(campanha), env.store.load().recorded)
        // A contagem do analytics é do toque — não repete no reenvio.
        assertEquals(1, env.events.size)
    }

    @Test
    fun recusa_definitiva_sai_da_fila_sem_repetir() = runTest {
        val env = Env().apply { result = PushOpenSendResult.REJECTED }
        val r = recorder(env)
        r.onTap(tap(campanha))
        esperar()
        assertEquals(1, env.sent.size)
        assertTrue(env.store.load().pending.isEmpty())
        advanceTimeBy(20.minutes)
        assertEquals(1, env.sent.size)
    }

    @Test
    fun sem_destino_guarda_e_envia_no_flush() = runTest {
        val env = Env().apply { target = null }
        val r = recorder(env)
        r.onTap(tap(campanha))
        esperar()
        assertTrue(env.sent.isEmpty())
        assertEquals(1, env.store.load().pending.size)
        assertFalse(r.flush())

        env.target = PushOpenTarget("meu-app", "https://apps-api.example.com")
        assertTrue(r.flush())
        assertEquals(1, env.sent.size)
        assertTrue(env.store.load().pending.isEmpty())
    }

    @Test
    fun abertura_velha_demais_sai_sem_envio() = runTest {
        val env = Env().apply { target = null }
        val r = recorder(env)
        r.onTap(tap(campanha))
        esperar()
        env.now += PushOpenRecorder.MAX_AGE.inWholeMilliseconds + 1
        env.target = PushOpenTarget("meu-app", "https://apps-api.example.com")
        assertTrue(r.flush())
        assertTrue(env.sent.isEmpty())
        assertTrue(env.store.load().pending.isEmpty())
    }

    @Test
    fun fila_e_lista_de_contadas_tem_teto() = runTest {
        val env = Env().apply { target = null }
        val r = recorder(env)
        repeat(PushOpenRecorder.MAX_PENDING + 5) { i ->
            assertTrue(r.record("0193a1b2-c3d4-7e5f-8a9b-" + i.toString().padStart(12, '0')))
        }
        assertEquals(PushOpenRecorder.MAX_PENDING, env.store.load().pending.size)
        assertEquals("0193a1b2-c3d4-7e5f-8a9b-000000000005", env.store.load().pending.first().campaign)
    }

    @Test
    fun duas_campanhas_seguidas_vao_as_duas() = runTest {
        val env = Env()
        val r = recorder(env)
        r.onTap(tap(campanha))
        r.onTap(tap(outra, "comunicado", mapOf("cc_comunicado" to "aviso-1")))
        esperar()
        assertEquals(setOf(campanha, outra), env.sent.map { it.second["campanha"]!!.jsonPrimitive.content }.toSet())
        assertEquals(listOf("premium", "comunicado"), env.events.map { it.second["acao"] })
    }

    @Test
    fun registro_ilegivel_recomeca_vazio() = runTest {
        val env = Env()
        env.blobs.write(BlobPushOpenLedgerStore.BLOB_ID, "{lixo".encodeToByteArray())
        assertTrue(env.store.load().pending.isEmpty())
        val r = recorder(env)
        r.onTap(tap(campanha))
        esperar()
        assertEquals(1, env.sent.size)
    }

    @Test
    fun classificacao_do_status() {
        assertEquals(PushOpenSendResult.SENT, PushOpenRecorder.classify(204))
        assertEquals(PushOpenSendResult.SENT, PushOpenRecorder.classify(200))
        assertEquals(PushOpenSendResult.RETRY, PushOpenRecorder.classify(null))
        assertEquals(PushOpenSendResult.RETRY, PushOpenRecorder.classify(404))
        assertEquals(PushOpenSendResult.RETRY, PushOpenRecorder.classify(408))
        assertEquals(PushOpenSendResult.RETRY, PushOpenRecorder.classify(429))
        assertEquals(PushOpenSendResult.RETRY, PushOpenRecorder.classify(503))
        assertEquals(PushOpenSendResult.REJECTED, PushOpenRecorder.classify(400))
        assertEquals(PushOpenSendResult.REJECTED, PushOpenRecorder.classify(422))
        assertEquals(PushOpenSendResult.RETRY, PushOpenRecorder.classify(302))
    }

    @Test
    fun espera_cresce_ate_quinze_minutos() {
        assertEquals(30.seconds, PushOpenRecorder.defaultRetryDelay(1))
        assertEquals(60.seconds, PushOpenRecorder.defaultRetryDelay(2))
        assertEquals(15.minutes, PushOpenRecorder.defaultRetryDelay(30))
    }

    @Test
    fun rotulo_da_acao() {
        assertEquals("app", PushOpenRecorder.actionLabel(null))
        assertEquals("app", PushOpenRecorder.actionLabel(PushAction.OpenApp(null)))
        assertEquals("premium", PushOpenRecorder.actionLabel(PushAction.OpenPremium(null)))
        assertEquals("comunicado", PushOpenRecorder.actionLabel(PushAction.OpenAnnouncement("a", null)))
        assertEquals("link", PushOpenRecorder.actionLabel(PushAction.OpenLink("https://x", null)))
    }

    @Test
    fun hub_avisa_o_observador_de_todo_toque_inclusive_o_que_fica_pendente() {
        val hub = PushListenerHub()
        val vistos = mutableListOf<Map<String, String>>()
        hub.tapObserver = { vistos += it }
        hub.onNotificationClicked(mapOf("cc_campanha" to campanha))
        assertEquals(mapOf("cc_campanha" to campanha), hub.pendingTap.value)
        val recebidos = mutableListOf<Map<String, String>>()
        hub.addListener(object : PushNotificationListener {
            override fun onNotificationClicked(data: Map<String, String>) {
                recebidos += data
            }
        })
        hub.onNotificationClicked(mapOf("x" to "1"))
        assertEquals(2, vistos.size)
        assertEquals(2, recebidos.size)
    }

    @Test
    fun observador_que_falha_nao_impede_o_toque() {
        val hub = PushListenerHub()
        hub.tapObserver = { error("boom") }
        hub.onNotificationClicked(mapOf("a" to "b"))
        assertEquals(mapOf("a" to "b"), hub.pendingTap.value)
    }
}
