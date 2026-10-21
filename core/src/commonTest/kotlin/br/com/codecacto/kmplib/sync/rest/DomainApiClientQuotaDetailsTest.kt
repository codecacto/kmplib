package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.monetization.entitlement.QuotaExceeded
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 2.316.0 — o 402 de cota chega INTEIRO ao ViewModel: código, frase do servidor e o `details` todo
 * (degrau sugerido, fale conosco, campos extras). Até a 2.315.0 só o `QuotaExceeded` sobrevivia.
 */
class DomainApiClientQuotaDetailsTest {

    private fun cliente(body: String): DomainApiClient {
        val engine = MockEngine {
            respond(body, HttpStatusCode.PaymentRequired, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return DomainApiClient(HttpClient(engine), DomainTokenProvider { "t" }, "https://api.example.com")
    }

    @Serializable
    private data class Faixa(val id: String, val vigias: Int)

    @Test
    fun `ErrorResponse da backlib preserva codigo frase e details`() = runTest {
        val r = cliente(
            """{"code":"QUOTA_EXCEEDED","message":"Seu plano permite até 3 vigias ativos. Assine uma faixa maior.",
               "details":{"feature":"vigias_ativos","limite":"3","contagem":"3","upgradeUrl":"https://x.test/up",
               "degrauSugerido":"v10","degrauSugeridoNome":"Até 10 vigias"}}""",
        ).postJson("/v1/vigias", "{}")
        assertIs<DomainResult.Quota>(r)
        assertEquals(QuotaExceeded("vigias_ativos", 3, 3, "https://x.test/up"), r.quota)
        assertEquals("QUOTA_EXCEEDED", r.serverCode)
        assertEquals("Seu plano permite até 3 vigias ativos. Assine uma faixa maior.", r.serverMessage)
        assertEquals(r.serverMessage, r.userMessage)
        assertEquals("vigias_ativos", r.recurso)
        assertEquals(3L, r.limite)
        assertEquals(3L, r.uso)
        assertEquals("v10", r.degrauSugerido)
        assertEquals("Até 10 vigias", r.degrauSugeridoNome)
        assertFalse(r.faleConosco)
        assertEquals("https://x.test/up", r.upgradeUrl)
        assertEquals("v10", r.details["degrauSugerido"])
    }

    @Test
    fun `envelope do admin-api com faleConosco e details tipados`() = runTest {
        val r = cliente(
            """{"ok":false,"error":{"code":"QUOTA_EXCEEDED","message":"Fale conosco.",
               "details":{"feature":"locais","limite":25,"contagem":25,"faleConosco":true,
               "faixas":[{"id":"v25","vigias":25}],"ids":["a","b"],"extra":{"k":"v"}}}}""",
        ).getJson("/v1/locais")
        assertIs<DomainResult.Quota>(r)
        assertTrue(r.faleConosco)
        assertNull(r.degrauSugerido)
        assertEquals(25L, r.limite)
        assertEquals(listOf("a", "b"), r.detailList("ids"))
        assertEquals("v", r.detailObject("extra")?.get("k")?.toString()?.trim('"'))
        assertEquals(listOf(Faixa("v25", 25)), r.decodeDetail<List<Faixa>>("faixas"))
        assertNull(r.detailLong("ids"))
        assertNull(r.detailBoolean("nada"))
    }

    @Test
    fun `vocabulario em portugues recurso e uso tambem vira Quota`() = runTest {
        val r = cliente("""{"code":"QUOTA_EXCEEDED","details":{"recurso":"pontos","limite":"10","uso":"10"}}""")
            .getJson("/v1/pontos")
        assertIs<DomainResult.Quota>(r)
        assertEquals("pontos", r.quota.feature)
        assertEquals("pontos", r.recurso)
        assertEquals(10L, r.uso)
        assertEquals(10, r.quota.contagem)
    }

    @Test
    fun `payload direto na raiz vira details`() = runTest {
        val r = cliente("""{"feature":"items","limite":5,"contagem":4,"faleConosco":"true"}""").getJson("/v1/itens")
        assertIs<DomainResult.Quota>(r)
        assertNull(r.serverCode)
        assertTrue(r.faleConosco)
        assertEquals(4L, r.uso)
        assertEquals("Limite atingido.", r.userMessage)
    }

    @Test
    fun `Quota construido a mao continua valendo e le do QuotaExceeded`() {
        val q = DomainResult.Quota(QuotaExceeded("items", 5, 5))
        assertEquals("items", q.recurso)
        assertEquals(5L, q.limite)
        assertEquals(5L, q.uso)
        assertNull(q.serverMessage)
        assertEquals(DomainResult.Quota.DEFAULT_QUOTA_MESSAGE, q.userMessage)
        assertFalse(q.faleConosco)
        // map/propagação mantêm o objeto inteiro.
        val propagado: DomainResult<Int> = q.map { 1 }
        assertEquals(q, propagado)
    }
}
