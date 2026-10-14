package br.com.codecacto.kmplib.monetization.access

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.HttpClientOptions
import br.com.codecacto.kmplib.core.network.HttpRetryPolicy
import br.com.codecacto.kmplib.core.network.createHttpClient
import br.com.codecacto.kmplib.sync.rest.DomainResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 2.300.0 — o 402 TRIAL_EXPIRED/SUBSCRIPTION_REQUIRED vira um estado único que o gate do app lê. */
class SubscriptionBlockTest {

    private val trialBody = """{"code":"TRIAL_EXPIRED","message":"Seu teste grátis terminou"}"""
    private val requiredBody = """{"code":"SUBSCRIPTION_REQUIRED","message":"Assinatura inativa"}"""
    private val quotaBody =
        """{"code":"QUOTA_EXCEEDED","message":"cota","details":{"feature":"items","limite":"5","contagem":"5"}}"""

    // ---------- parser ----------

    @Test
    fun parse_trialExpired_naRaiz() {
        val b = parseSubscriptionBlock(402, trialBody)
        assertNotNull(b)
        assertEquals(SubscriptionBlockReason.TRIAL_EXPIRED, b.reason)
        assertEquals("Seu teste grátis terminou", b.serverMessage)
    }

    @Test
    fun parse_subscriptionRequired_aninhadoEmError() {
        val b = parseSubscriptionBlock(402, """{"ok":false,"error":{"code":"SUBSCRIPTION_REQUIRED"}}""")
        assertEquals(SubscriptionBlockReason.SUBSCRIPTION_REQUIRED, b?.reason)
    }

    @Test
    fun parse_ignoraCaixaEEspaco() {
        assertEquals(
            SubscriptionBlockReason.TRIAL_EXPIRED,
            parseSubscriptionBlock(402, """{"code":" trial_expired "}""")?.reason,
        )
    }

    @Test
    fun parse_naoBloqueia_emOutrosCasos() {
        assertNull(parseSubscriptionBlock(403, trialBody), "só 402")
        assertNull(parseSubscriptionBlock(200, trialBody))
        assertNull(parseSubscriptionBlock(402, quotaBody), "402 de cota não bloqueia a conta")
        assertNull(parseSubscriptionBlock(402, null))
        assertNull(parseSubscriptionBlock(402, ""))
        assertNull(parseSubscriptionBlock(402, "<html>Payment Required</html>"))
        assertNull(parseSubscriptionBlock(402, """{"code":"OUTRA_COISA"}"""))
        assertNull(parseSubscriptionBlock(402, "[1,2]"))
    }

    @Test
    fun reason_fromCode() {
        assertEquals(SubscriptionBlockReason.SUBSCRIPTION_REQUIRED, SubscriptionBlockReason.fromCode("SUBSCRIPTION_REQUIRED"))
        assertNull(SubscriptionBlockReason.fromCode(null))
        assertNull(SubscriptionBlockReason.fromCode("  "))
    }

    // ---------- estado ----------

    @Test
    fun state_reportEClear() {
        val s = SubscriptionBlockState()
        assertFalse(s.isBlocked)
        assertFalse(s.reportIfBlocked(402, quotaBody))
        assertNull(s.block.value)
        assertTrue(s.reportIfBlocked(402, trialBody))
        assertEquals(SubscriptionBlockReason.TRIAL_EXPIRED, s.block.value?.reason)
        // A resposta mais recente vence.
        s.report(SubscriptionBlock(SubscriptionBlockReason.SUBSCRIPTION_REQUIRED))
        assertEquals(SubscriptionBlockReason.SUBSCRIPTION_REQUIRED, s.block.value?.reason)
        s.clear()
        assertFalse(s.isBlocked)
    }

    // ---------- detector no HttpClient ----------

    private fun client(
        state: SubscriptionBlockState,
        handler: (path: String, host: String) -> Pair<HttpStatusCode, String>,
    ): HttpClient = createHttpClient(
        MockEngine { req ->
            val (status, body) = handler(req.url.encodedPath, req.url.host)
            respond(body, status, headersOf("Content-Type", "application/json"))
        },
        HttpClientOptions(enableLogging = false, retry = HttpRetryPolicy.Disabled, subscriptionBlock = state),
    )

    @Test
    fun detector_desligadoPorDefault() = runTest {
        val s = SubscriptionBlockState()
        val http = createHttpClient(
            MockEngine { respond(trialBody, HttpStatusCode.PaymentRequired) },
            HttpClientOptions(enableLogging = false, retry = HttpRetryPolicy.Disabled),
        )
        http.get("https://api.x.com/v1/locacoes")
        assertFalse(s.isBlocked)
        assertFalse(SubscriptionBlockState.Shared.isBlocked, "opt-in: sem a opção, nada é registrado")
    }

    @Test
    fun detector_registraO402_eOChamadorAindaLeOCorpo() = runTest {
        val s = SubscriptionBlockState()
        val http = client(s) { _, _ -> HttpStatusCode.PaymentRequired to trialBody }
        val resp = http.get("https://api.x.com/v1/locacoes")
        assertEquals(trialBody, resp.bodyAsText(), "o detector não consome o corpo de quem chamou")
        assertEquals(SubscriptionBlockReason.TRIAL_EXPIRED, s.block.value?.reason)
    }

    @Test
    fun detector_ignoraO402DeCota() = runTest {
        val s = SubscriptionBlockState()
        val http = client(s) { _, _ -> HttpStatusCode.PaymentRequired to quotaBody }
        http.get("https://api.x.com/v1/itens")
        assertFalse(s.isBlocked)
    }

    @Test
    fun detector_2xxDeRotaProtegidaDoMesmoHost_tiraOBloqueio() = runTest {
        val s = SubscriptionBlockState()
        var pago = false
        val http = client(s) { _, _ ->
            if (pago) HttpStatusCode.OK to "[]" else HttpStatusCode.PaymentRequired to trialBody
        }
        http.get("https://api.x.com/v1/locacoes")
        assertTrue(s.isBlocked)
        pago = true
        http.get("https://api.x.com/v1/locacoes")
        assertFalse(s.isBlocked, "o acesso voltou (pagou na web / contrato)")
    }

    @Test
    fun detector_2xxNeutroOuDeOutroHost_naoTiraOBloqueio() = runTest {
        val s = SubscriptionBlockState()
        val http = client(s) { path, host ->
            when {
                host == "api.x.com" && path.startsWith("/v1/locacoes") -> HttpStatusCode.PaymentRequired to trialBody
                else -> HttpStatusCode.OK to "{}"
            }
        }
        http.get("https://api.x.com/v1/locacoes")
        assertTrue(s.isBlocked)
        http.get("https://api.x.com/auth/refresh")
        http.get("https://api.x.com/api/v1/auth/me")
        http.get("https://api.x.com/public/empresa/abc")
        http.get("https://api.x.com/v1/billing/checkout")
        http.get("https://admin-api.codecacto.com.br/v1/clientes")
        assertTrue(s.isBlocked, "login, rota pública, cobrança e outro servidor não provam acesso")
    }

    @Test
    fun detector_onlyHosts() = runTest {
        val s = SubscriptionBlockState()
        val http = createHttpClient(
            MockEngine { respond(trialBody, HttpStatusCode.PaymentRequired) },
            HttpClientOptions(enableLogging = false, retry = HttpRetryPolicy.Disabled),
        ) {
            install(SubscriptionBlockDetector) {
                state = s
                onlyHosts = setOf("api-locasys.codecacto.com.br")
            }
        }
        http.get("https://terceiro.com/x")
        assertFalse(s.isBlocked)
        http.get("https://API-LOCASYS.codecacto.com.br/v1/x")
        assertTrue(s.isBlocked)
    }

    // ---------- tipos de erro ----------

    @Test
    fun erros_isSubscriptionBlocked() {
        assertTrue(ApiResult.Error(code = 402, message = "x", serverCode = "TRIAL_EXPIRED").isSubscriptionBlocked)
        assertFalse(ApiResult.Error(code = 402, message = "x", serverCode = "QUOTA_EXCEEDED").isSubscriptionBlocked)
        assertFalse(ApiResult.Error(code = 403, message = "x", serverCode = "TRIAL_EXPIRED").isSubscriptionBlocked)
        assertTrue(DomainResult.Error(code = 402, message = "x", serverCode = "SUBSCRIPTION_REQUIRED").isSubscriptionBlocked)
        assertFalse(DomainResult.Error(code = 402, message = "x").isSubscriptionBlocked)
    }
}
