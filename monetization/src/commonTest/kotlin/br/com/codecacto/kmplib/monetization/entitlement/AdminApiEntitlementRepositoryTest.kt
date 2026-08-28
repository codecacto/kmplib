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
 * Testa a leitura de entitlement/uso/planos do PROPRIO usuario contra o admin-api central
 * (`AdminApiEntitlementRepository`) — fonte de verdade da monetizacao. Usa Ktor MockEngine (sem
 * rede real). Cobre o contrato reconciliado (2.57.0): rotas `/v1/projects/{slug}/me/...`, respostas
 * **DTO puro (SEM envelope)** com **campos em PT**, o caso **free-default** (200, `ativo=false`), a
 * regra de SEGURANCA "nunca autopromover" (entitlement inativo -> free), planos com
 * `tipo`/`durationMonths`, o cache curto em memoria, o principio "nunca conceder offline" e a
 * degradacao segura de `assertUsage` (GAP de backend — sem `/me/assert`).
 */
class AdminApiEntitlementRepositoryTest {

    private val jsonHeaders = headersOf("Content-Type", "application/json")

    /** Cria o repo com um handler de rota programavel e o contador de chamadas. */
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
            baseUrl = "https://admin-api.codecacto.com.br/",
            projectSlug = "meu-app",
            authToken = { token },
            cacheTtlMillis = cacheTtlMillis
        )
    }

    // ====== getEntitlement ======

    @Test
    fun `getEntitlement mapeia DTO puro pt de plano ativo`() = runTest {
        val r = repo {
            HttpStatusCode.OK to """
                {"project":"meu-app","tenant":"uid-1","plano":"premium",
                "features":["export_pdf","sem_marca"],"validoAte":"2026-12-31",
                "fonte":"REVENUECAT","atualizadoEm":"2026-07-01","ativo":true}
            """.trimIndent()
        }
        val result = r.getEntitlement()
        assertIs<ApiResult.Success<Entitlement>>(result)
        val ent = result.data
        assertEquals("premium", ent.plano)
        assertEquals(setOf("export_pdf", "sem_marca"), ent.features)
        assertEquals("2026-12-31", ent.validoAte)
        assertEquals("revenuecat", ent.fonte)
        assertTrue(ent.isPremium)
        assertTrue(ent.hasFeature("export_pdf"))
    }

    @Test
    fun `getEntitlement trata default free (ativo=false) como nao-premium sem erro`() = runTest {
        // Usuario sem grant: o admin-api responde 200 com o default free (plano="free", ativo=false).
        val r = repo {
            HttpStatusCode.OK to """
                {"project":"meu-app","tenant":"uid-1","plano":"free","features":[],
                "validoAte":null,"fonte":"NONE","atualizadoEm":"","ativo":false}
            """.trimIndent()
        }
        val result = r.getEntitlement()
        assertIs<ApiResult.Success<Entitlement>>(result)
        assertEquals("free", result.data.plano)
        assertTrue(result.data.features.isEmpty())
        assertTrue(result.data.isFree)
    }

    @Test
    fun `getEntitlement NAO autopromove quando plano vem mas ativo=false`() = runTest {
        // Servidor devolve plano premium mesmo para entitlement EXPIRADO (ativo=false). A lib deve
        // rebaixar para "free" (seguranca: a autoridade e `ativo`, nao a presenca do plano).
        val r = repo {
            HttpStatusCode.OK to """
                {"project":"meu-app","tenant":"uid-1","plano":"premium",
                "features":["export_pdf"],"validoAte":"2020-01-01","fonte":"revenuecat",
                "atualizadoEm":"2020-01-01","ativo":false}
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
            HttpStatusCode.OK to """{"project":"meu-app","tenant":"u","plano":"premium","features":[],"validoAte":null,"fonte":"m","atualizadoEm":"","ativo":true}"""
        }
        r.getEntitlement()
        r.getEntitlement() // deve vir do cache — sem nova chamada de rede
        assertEquals(1, calls.size, "Segunda leitura deveria usar o cache em memoria")

        r.invalidateCache()
        r.getEntitlement() // cache limpo — refaz a chamada
        assertEquals(2, calls.size)
    }

    @Test
    fun `getEntitlement com cache desabilitado sempre vai a rede`() = runTest {
        val calls = mutableListOf<HttpRequestData>()
        val r = repo(captured = calls, cacheTtlMillis = 0L) {
            HttpStatusCode.OK to """{"project":"meu-app","tenant":"u","plano":"premium","features":[],"validoAte":null,"fonte":"m","atualizadoEm":"","ativo":true}"""
        }
        r.getEntitlement()
        r.getEntitlement()
        assertEquals(2, calls.size)
    }

    @Test
    fun `getEntitlement envia Firebase ID token como Bearer na rota me`() = runTest {
        val calls = mutableListOf<HttpRequestData>()
        val r = repo(token = "id-token-123", captured = calls) {
            HttpStatusCode.OK to """{"project":"meu-app","tenant":"u","plano":"premium","features":[],"validoAte":null,"fonte":"m","atualizadoEm":"","ativo":true}"""
        }
        r.getEntitlement()
        assertEquals("Bearer id-token-123", calls.first().headers["Authorization"])
        assertTrue(calls.first().url.fullPath.endsWith("/v1/projects/meu-app/me/entitlement"))
    }

    @Test
    fun `getEntitlement nunca concede offline - erro de rede propaga e nao cacheia`() = runTest {
        // Primeira chamada falha (5xx); segunda retorna sucesso. Como o erro NAO e cacheado,
        // a segunda vai a rede de novo e reflete o estado real — nunca um "liberado" velho.
        val calls = mutableListOf<HttpRequestData>()
        var first = true
        val client = HttpClient(MockEngine { request ->
            calls.add(request)
            if (first) {
                first = false
                respondError(HttpStatusCode.InternalServerError)
            } else {
                respond(
                    """{"project":"meu-app","tenant":"u","plano":"premium","features":[],"validoAte":null,"fonte":"m","atualizadoEm":"","ativo":true}""",
                    HttpStatusCode.OK,
                    jsonHeaders
                )
            }
        })
        val r = AdminApiEntitlementRepository(client, "https://admin-api.codecacto.com.br", "meu-app", { null })

        val failed = r.getEntitlement()
        assertIs<ApiResult.Error>(failed)

        val ok = r.getEntitlement()
        assertIs<ApiResult.Success<Entitlement>>(ok)
        assertEquals(2, calls.size, "Apos falha nao pode servir cache — deve revalidar")
    }

    // ====== getUsage ======

    @Test
    fun `getUsage mapeia contagem limite e restante do DTO pt e usa feature no path`() = runTest {
        val calls = mutableListOf<HttpRequestData>()
        val r = repo(captured = calls) {
            HttpStatusCode.OK to """{"feature":"export_pdf","contagem":3,"limite":5,"restante":2,"janelaFim":null}"""
        }
        val result = r.getUsage("export_pdf")
        assertIs<ApiResult.Success<UsageSnapshot>>(result)
        assertEquals("export_pdf", result.data.feature)
        assertEquals(3, result.data.contagem)
        assertEquals(5, result.data.limite)
        assertEquals(2, result.data.restante)
        assertTrue(calls.first().url.fullPath.endsWith("/v1/projects/meu-app/me/usage/export_pdf"))
    }

    @Test
    fun `getUsage com limite -1 e ilimitado`() = runTest {
        val r = repo {
            HttpStatusCode.OK to """{"feature":"export_pdf","contagem":10,"limite":-1,"restante":0,"janelaFim":null}"""
        }
        val result = r.getUsage("export_pdf")
        assertIs<ApiResult.Success<UsageSnapshot>>(result)
        assertEquals(-1, result.data.limite)
        assertTrue(result.data.isUnlimited)
    }

    @Test
    fun `getUsage usa a feature do pedido quando o servidor omite`() = runTest {
        val r = repo {
            HttpStatusCode.OK to """{"contagem":1,"limite":3}"""
        }
        val result = r.getUsage("minha_feature")
        assertIs<ApiResult.Success<UsageSnapshot>>(result)
        assertEquals("minha_feature", result.data.feature)
    }

    // ====== getPlans ======

    @Test
    fun `getPlans mapeia catalogo pt do servidor`() = runTest {
        val calls = mutableListOf<HttpRequestData>()
        val r = repo(captured = calls) {
            HttpStatusCode.OK to """
                [
                  {"projectSlug":"meu-app","plano":"premium_mensal","nome":"Mensal","preco":"19.90",
                   "moeda":"BRL","intervalo":"monthly","ativo":true,"tipo":"MENSAL",
                   "durationMonths":1,"storeProductId":"prod_x"}
                ]
            """.trimIndent()
        }
        val result = r.getPlans()
        assertIs<ApiResult.Success<List<Plan>>>(result)
        val plan = result.data.single()
        assertEquals("premium_mensal", plan.plano)
        assertEquals("Mensal", plan.nome)
        assertEquals("19.90", plan.preco)
        assertEquals("BRL", plan.moeda)
        assertEquals("monthly", plan.intervalo)
        assertTrue(plan.ativo)
        assertEquals("prod_x", plan.storeProductId)
        assertTrue(calls.first().url.fullPath.endsWith("/v1/projects/meu-app/me/plans"))
    }

    @Test
    fun `getPlans com intervalo ausente assume monthly`() = runTest {
        val r = repo {
            HttpStatusCode.OK to """[{"plano":"basic","nome":"Basico"}]"""
        }
        val result = r.getPlans()
        assertIs<ApiResult.Success<List<Plan>>>(result)
        assertEquals("monthly", result.data.single().intervalo)
    }

    @Test
    fun `getPlans decodifica tipo e durationMonths da oferta padronizada`() = runTest {
        // O backend emite tipo (MENSAL|SEMESTRAL|ANUAL) e durationMonths (1|6|12) na oferta de paywall;
        // sem decodifica-los o paywall novo (correlacao por duracao com o Package) viria vazio.
        val r = repo {
            HttpStatusCode.OK to """
                [
                  {"projectSlug":"meu-app","plano":"premium_anual","nome":"Anual","preco":"89.90",
                   "moeda":"BRL","intervalo":"yearly","ativo":true,"tipo":"ANUAL",
                   "durationMonths":12,"storeProductId":"prod_anual"}
                ]
            """.trimIndent()
        }
        val result = r.getPlans()
        assertIs<ApiResult.Success<List<Plan>>>(result)
        val plan = result.data.single()
        assertEquals("ANUAL", plan.tipo)
        assertEquals(12, plan.durationMonths)
    }

    @Test
    fun `getPlans sem tipo e durationMonths mantem nulos retrocompativeis`() = runTest {
        val r = repo {
            HttpStatusCode.OK to """[{"plano":"free","nome":"Gratis"}]"""
        }
        val result = r.getPlans()
        assertIs<ApiResult.Success<List<Plan>>>(result)
        val plan = result.data.single()
        assertNull(plan.tipo)
        assertNull(plan.durationMonths)
    }

    // ====== assertUsage (GAP de backend — degradacao segura, sem rede) ======

    @Test
    fun `assertUsage degrada seguro para Failed sem chamar rede (sem rota me-assert)`() = runTest {
        val calls = mutableListOf<HttpRequestData>()
        val r = repo(captured = calls) { HttpStatusCode.OK to "{}" }
        val result = r.assertUsage("export_pdf", currentCount = 0, amount = 1)
        assertIs<AssertResult.Failed>(result)
        assertEquals(AdminApiEntitlementRepository.ASSERT_UNAVAILABLE_CODE, result.code)
        // Nunca vira Allowed (jamais autoconcede) e nao toca a rede.
        assertTrue(calls.isEmpty(), "assertUsage nao deve chamar rede — nao ha rota /me/assert")
    }

    // ====== parseQuotaExceeded (funcao pura — usada no gate real 402 da acao de dominio) ======

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
