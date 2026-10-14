package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.core.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 2.300.0 — `categoria` (contrato/isento) no entitlement: conta liberada sem plano pago. */
class EntitlementCategoryTest {

    private fun repo(body: String) = AdminApiEntitlementRepository(
        httpClient = HttpClient(MockEngine {
            respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }),
        baseUrl = "https://admin-api.codecacto.com.br",
        projectSlug = "locasys",
        cacheTtlMillis = 0,
    )

    @Test
    fun modelo_contratoEIsentoContamComoLiberado() {
        val contrato = Entitlement(plano = "free", categoria = EntitlementCategory.CONTRACT)
        assertTrue(contrato.isContract)
        assertTrue(contrato.isGrantedByCategory)
        assertTrue(contrato.isPremium)
        assertFalse(contrato.isFree)

        val isento = Entitlement(plano = "", categoria = EntitlementCategory.EXEMPT)
        assertTrue(isento.isExempt)
        assertTrue(isento.isPremium)
    }

    @Test
    fun modelo_semCategoriaOuDesconhecida_naoLibera() {
        assertFalse(Entitlement.FREE.isPremium)
        assertNull(Entitlement.FREE.categoria)
        val estranha = Entitlement(plano = "free", categoria = "parceiro-vip")
        assertFalse(estranha.isGrantedByCategory)
        assertFalse(estranha.isPremium)
    }

    @Test
    fun normalize() {
        assertEquals("contrato", EntitlementCategory.normalize(" CONTRATO "))
        assertNull(EntitlementCategory.normalize(""))
        assertNull(EntitlementCategory.normalize(null))
    }

    @Test
    fun dto_contratoSemGrantAtivo_continuaLiberado() = runTest {
        val r = repo("""{"plano":"free","features":[],"ativo":false,"fonte":"NONE","categoria":"Contrato"}""")
            .getEntitlement()
        assertIs<ApiResult.Success<Entitlement>>(r)
        assertEquals("contrato", r.data.categoria)
        assertTrue(r.data.isContract)
        assertTrue(r.data.isPremium)
    }

    @Test
    fun dto_ativoComCategoriaIsento() = runTest {
        val r = repo("""{"plano":"premium","features":["x"],"ativo":true,"categoria":"isento"}""").getEntitlement()
        assertIs<ApiResult.Success<Entitlement>>(r)
        assertTrue(r.data.isExempt)
        assertEquals(setOf("x"), r.data.features)
    }

    @Test
    fun dto_semCampo_servidorAntigo() = runTest {
        val r = repo("""{"plano":"free","features":[],"ativo":false}""").getEntitlement()
        assertIs<ApiResult.Success<Entitlement>>(r)
        assertNull(r.data.categoria)
        assertFalse(r.data.isPremium)
    }

    @Test
    fun dto_categoriaNulaOuVazia() = runTest {
        val r = repo("""{"plano":"free","ativo":false,"categoria":""}""").getEntitlement()
        assertIs<ApiResult.Success<Entitlement>>(r)
        assertNull(r.data.categoria)
        val r2 = repo("""{"plano":"free","ativo":false,"categoria":null}""").getEntitlement()
        assertIs<ApiResult.Success<Entitlement>>(r2)
        assertNull(r2.data.categoria)
    }
}
