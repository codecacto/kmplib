package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.core.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testa a leitura de entitlement/uso/planos e o `assert` de cota contra o admin-api central
 * (`AdminApiEntitlementRepository`) — fonte de verdade da monetização. Usa Ktor MockEngine
 * (sem rede real). Cobre o envelope `{ ok, data }`, mapeamento dos DTOs em inglês → modelos PT,
 * cache curto em memória, a regra de SEGURANÇA "nunca autopromover" (plano inativo → free),
 * o 402 → Paywall e o princípio "nunca conceder offline".
 */
class AdminApiEntitlementRepositoryTest {

    private val jsonHeaders = headersOf("Content-Type", "application/json")

    /** Cria o repo com um handler de rota programável e o contador de chamadas. */
    private fun repo(
        token: String? = null,
        cacheTtlMillis: Long = 60_000L,
        captured: MutableList<HttpRequestData>? = null,
        handler: (HttpRequestData) -> Pair<HttpStatusCode, String>
    ): AdminApiEntitlementRepository {
        val client = HttpClient(MockEngine { request ->
            captured?.add(request)
            val (status, body) = handler(request)
            respond(content = body, status = status, headers = jsonHeaders)
        })
        return AdminApiEntitlementRepository(
            httpClient = client,
            baseUrl = "https://admin.codecacto.com.br/",
            projectSlug = "meu-app",
            authToken = { token },
            cacheTtlMillis = cacheTtlMillis
        )
    }

    // ====== getEntitlement ======

    @Test
    fun `getEntitlement desembrulha envelope e mapeia plano ativo`() = runTest {
        val r = repo {
            HttpStatusCode.OK to """
                {"ok":true,"data":{"active":true,"status":"ACTIVE","source":"RevenueCat",
                "validUntil":"2026-12-31","plan":{"code":"premium","name":"Premium",
                "limits":[{"feature":"export_pdf","limit":-1},{"feature":"sem_marca","limit":-1}]}}}
            """.trimIndent()
        }
        val result = r.getEntitlement()
        assertIs<ApiResult.Success<Entitlement>>(result)
        val ent = result.data
        assertEquals("premium", ent.plano)
        assertEquals(setOf("export_pdf", "sem_marca"), ent.features)
        assertEquals("2026-12-31", ent.validoAte)
        assertEquals("revenuecat", ent.fonte)
        assertTrue(ent.hasFeature("export_pdf"))
    }

    @Test
    fun `getEntitlement NAO autopromove quando plano vem mas direito esta inativo`() = runTest {
        // Servidor devolve o plano premium mesmo para um entitlement EXPIRADO. A lib deve
        // rebaixar para "free" (segurança: a autoridade é active/status, não a presença do plano).
        val r = repo {
            HttpStatusCode.OK to """
                {"ok":true,"data":{"active":false,"status":"EXPIRED","source":"revenuecat",
                "plan":{"code":"premium","name":"Premium","limits":[{"feature":"export_pdf","limit":-1}]}}}
            """.trimIndent()
        }
        val result = r.getEntitlement()
        assertIs<ApiResult.Success<Entitlement>>(result)
        assertEquals("free", result.data.plano)
        assertTrue(result.data.features.isEmpty())
        assertTrue(result.data.isFree)
    }

    @Test
    fun `getEntitlement usa cache dentro do TTL e revalida apos invalidateCache`() = runTest {
        val calls = mutableListOf<HttpRequestData>()
        val r = repo(captured = calls) {
            HttpStatusCode.OK to """{"ok":true,"data":{"active":true,"status":"ACTIVE","plan":{"code":"premium","name":"P","limits":[]}}}"""
        }
        r.getEntitlement()
        r.getEntitlement() // deve vir do cache — sem nova chamada de rede
        assertEquals(1, calls.size, "Segunda leitura deveria usar o cache em memória")

        r.invalidateCache()
        r.getEntitlement() // cache limpo — refaz a chamada
        assertEquals(2, calls.size)
    }

    @Test
    fun `getEntitlement com cache desabilitado sempre vai a rede`() = runTest {
        val calls = mutableListOf<HttpRequestData>()
        val r = repo(captured = calls, cacheTtlMillis = 0L) {
            HttpStatusCode.OK to """{"ok":true,"data":{"active":true,"status":"ACTIVE","plan":{"code":"premium","name":"P","limits":[]}}}"""
        }
        r.getEntitlement()
        r.getEntitlement()
        assertEquals(2, calls.size)
    }

    @Test
    fun `getEntitlement envia Firebase ID token como Bearer`() = runTest {
        val calls = mutableListOf<HttpRequestData>()
        val r = repo(token = "id-token-123", captured = calls) {
            HttpStatusCode.OK to """{"ok":true,"data":{"active":true,"status":"ACTIVE","plan":{"code":"premium","name":"P","limits":[]}}}"""
        }
        r.getEntitlement()
        assertEquals("Bearer id-token-123", calls.first().headers["Authorization"])
        assertTrue(calls.first().url.fullPath.endsWith("/monet/meu-app/entitlement"))
    }

    @Test
    fun `getEntitlement nunca concede offline - erro de rede propaga e nao cacheia`() = runTest {
        // Primeira chamada falha (5xx); segunda retorna sucesso. Como o erro NÃO é cacheado,
        // a segunda chamada vai à rede de novo e reflete o estado real — nunca um "liberado" velho.
        val calls = mutableListOf<HttpRequestData>()
        var first = true
        val client = HttpClient(MockEngine { request ->
            calls.add(request)
            if (first) {
                first = false
                respondError(HttpStatusCode.InternalServerError)
            } else {
                respond(
                    """{"ok":true,"data":{"active":true,"status":"ACTIVE","plan":{"code":"premium","name":"P","limits":[]}}}""",
                    HttpStatusCode.OK,
                    jsonHeaders
                )
            }
        })
        val r = AdminApiEntitlementRepository(client, "https://admin.codecacto.com.br", "meu-app", { null })

        val failed = r.getEntitlement()
        assertIs<ApiResult.Error>(failed)

        val ok = r.getEntitlement()
        assertIs<ApiResult.Success<Entitlement>>(ok)
        assertEquals(2, calls.size, "Após falha não pode servir cache — deve revalidar")
    }

    // ====== getUsage ======

    @Test
    fun `getUsage mapeia contagem limite e restante`() = runTest {
        val r = repo {
            HttpStatusCode.OK to """{"ok":true,"data":{"feature":"export_pdf","count":3,"limit":5,"remaining":2}}"""
        }
        val result = r.getUsage("export_pdf")
        assertIs<ApiResult.Success<UsageSnapshot>>(result)
        assertEquals("export_pdf", result.data.feature)
        assertEquals(3, result.data.contagem)
        assertEquals(5, result.data.limite)
        assertEquals(2, result.data.restante)
    }

    @Test
    fun `getUsage com unlimited mapeia limite para -1`() = runTest {
        val r = repo {
            HttpStatusCode.OK to """{"ok":true,"data":{"feature":"export_pdf","count":10,"limit":5,"unlimited":true}}"""
        }
        val result = r.getUsage("export_pdf")
        assertIs<ApiResult.Success<UsageSnapshot>>(result)
        assertEquals(-1, result.data.limite)
    }

    @Test
    fun `getUsage usa a feature do pedido quando o servidor omite`() = runTest {
        val r = repo {
            HttpStatusCode.OK to """{"ok":true,"data":{"count":1,"limit":3}}"""
        }
        val result = r.getUsage("minha_feature")
        assertIs<ApiResult.Success<UsageSnapshot>>(result)
        assertEquals("minha_feature", result.data.feature)
    }

    // ====== getPlans ======

    @Test
    fun `getPlans mapeia catalogo do servidor`() = runTest {
        val r = repo {
            HttpStatusCode.OK to """
                {"ok":true,"data":[
                  {"code":"premium","name":"Premium","price":"19.90","currency":"BRL",
                   "interval":"monthly","storeProductId":"prod_x","highlights":["Sem anúncios"],"limits":[]}
                ]}
            """.trimIndent()
        }
        val result = r.getPlans()
        assertIs<ApiResult.Success<List<Plan>>>(result)
        val plan = result.data.single()
        assertEquals("premium", plan.plano)
        assertEquals("Premium", plan.nome)
        assertEquals("19.90", plan.preco)
        assertEquals("BRL", plan.moeda)
        assertEquals("monthly", plan.intervalo)
        assertEquals("prod_x", plan.storeProductId)
        assertEquals(listOf("Sem anúncios"), plan.destaques)
    }

    @Test
    fun `getPlans com interval ausente assume monthly`() = runTest {
        val r = repo {
            HttpStatusCode.OK to """{"ok":true,"data":[{"code":"basic","name":"Básico","limits":[]}]}"""
        }
        val result = r.getPlans()
        assertIs<ApiResult.Success<List<Plan>>>(result)
        assertEquals("monthly", result.data.single().intervalo)
    }

    // ====== assertUsage ======

    @Test
    fun `assertUsage 200 retorna Allowed`() = runTest {
        val r = repo { HttpStatusCode.OK to """{"ok":true,"data":{"allowed":true}}""" }
        assertEquals(AssertResult.Allowed, r.assertUsage("export_pdf", currentCount = 0, amount = 1))
    }

    @Test
    fun `assertUsage 402 retorna Denied com quota parseada do envelope`() = runTest {
        val r = repo {
            HttpStatusCode.PaymentRequired to """
                {"ok":false,"error":{"code":"QUOTA_EXCEEDED","message":"Limite atingido",
                "details":{"feature":"export_pdf","limite":"5","contagem":"5",
                "upgradeUrl":"https://admin.codecacto.com.br/upgrade"}}}
            """.trimIndent()
        }
        val result = r.assertUsage("export_pdf", currentCount = 5, amount = 1)
        assertIs<AssertResult.Denied>(result)
        assertEquals("export_pdf", result.quota.feature)
        assertEquals(5, result.quota.limite)
        assertEquals(5, result.quota.contagem)
        assertEquals("https://admin.codecacto.com.br/upgrade", result.quota.upgradeUrl)
    }

    @Test
    fun `assertUsage 500 retorna Failed com mensagem do envelope`() = runTest {
        val r = repo {
            HttpStatusCode.InternalServerError to """{"ok":false,"error":{"code":"INTERNAL","message":"Erro interno"}}"""
        }
        val result = r.assertUsage("export_pdf", currentCount = 0, amount = 1)
        assertIs<AssertResult.Failed>(result)
        assertEquals(500, result.code)
        assertEquals("Erro interno", result.message)
    }

    @Test
    fun `assertUsage com falha de rede retorna Failed code -1`() = runTest {
        val client = HttpClient(MockEngine { throw RuntimeException("boom") })
        val r = AdminApiEntitlementRepository(client, "https://admin.codecacto.com.br", "meu-app", { null })
        val result = r.assertUsage("export_pdf", currentCount = 0, amount = 1)
        assertIs<AssertResult.Failed>(result)
        assertEquals(-1, result.code)
    }

    // ====== parseQuotaExceeded (função pura) ======

    @Test
    fun `parseQuotaExceeded aceita numeros como string ou number e payload direto`() {
        val direto = parseQuotaExceeded("""{"feature":"f","limite":3,"contagem":3}""")
        assertEquals(QuotaExceeded("f", 3, 3), direto)

        val nulo = parseQuotaExceeded("not json")
        assertNull(nulo)

        val semCampos = parseQuotaExceeded("""{"error":{"details":{"feature":"f"}}}""")
        assertNull(semCampos)
    }
}
