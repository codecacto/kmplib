package br.com.codecacto.kmplib.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive

class AnalyticsSanitizerTest {

    @Test
    fun caminhoDeTela_semQueryComIdTrocadoEMinusculo() {
        assertEquals("/app/home", analyticsScreenPath("home"))
        assertEquals("/app/clientes/:id", analyticsScreenPath("/Clientes/123?aba=fotos"))
        assertEquals("/app/item/:id", analyticsScreenPath("item/{id}"))
        assertEquals("/app/pedido/:id/itens", analyticsScreenPath("pedido/0b8e2c4a-1d2f-4a6b-9c3d-2e1f0a9b8c7d/itens"))
        assertEquals("/app/conta/:id", analyticsScreenPath("conta/maria@exemplo.com"))
        assertEquals("/app/token/:id", analyticsScreenPath("token/abcDEF123ghiJKL456"))
        assertEquals("/app/nota/:id", analyticsScreenPath("nota/NF2026"))
        assertEquals("/app", analyticsScreenPath(""))
        assertEquals("/app", analyticsScreenPath("  /  ?x=1"))
        assertEquals("/app/a/b", analyticsScreenPath("a//b#frag"))
        assertTrue(analyticsScreenPath("x/".repeat(300)).length <= ANALYTICS_MAX_PATH)
    }

    @Test
    fun nomeDeTela_daRotaTypeSafeEDeTexto() {
        assertEquals("item-detail", screenNameForRoute("br.com.app.navigation.Route.ItemDetail/{id}"))
        assertEquals("home", screenNameForRoute("br.com.app.navigation.Route.Home"))
        assertEquals("busca", screenNameForRoute("br.com.app.Route.Busca?termo={termo}"))
        assertEquals("item", screenNameForRoute("item/{id}"))
        assertEquals("perfil/editar", screenNameForRoute("perfil/editar"))
        assertEquals("tela-interna", screenNameForRoute("br.com.app.Outer\$TelaInterna"))
        assertNull(screenNameForRoute(null))
        assertNull(screenNameForRoute("   "))
        assertNull(screenNameForRoute("{id}"))
    }

    @Test
    fun kebab() {
        assertEquals("item-detail", toKebabCase("ItemDetail"))
        assertEquals("https-check", toKebabCase("HTTPSCheck"))
        assertEquals("item-lista", toKebabCase("item_lista"))
        assertEquals("passo2-fim", toKebabCase("Passo2Fim"))
        assertEquals("home", toKebabCase("home"))
    }

    @Test
    fun nomeDeEvento_semPrefixoPerigosoEComTeto() {
        assertEquals("assinou", sanitizeEventName("  assinou "))
        assertEquals("soma", sanitizeEventName("=+-@soma"))
        assertNull(sanitizeEventName("   "))
        assertNull(sanitizeEventName("=@"))
        assertEquals(ANALYTICS_MAX_EVENT_NAME, sanitizeEventName("x".repeat(80))!!.length)
    }

    @Test
    fun chaveDeDadoPessoal() {
        listOf("email", "e-mail", "emailCliente", "telefone", "TelefoneContato", "celular", "whatsapp", "cpf",
            "cpf_cliente", "cnpj", "rg", "nome", "sobrenome", "userName", "senha", "password", "token",
            "endereço", "cep", "data_nascimento", "documento").forEach {
            assertTrue(isPersonalDataKey(it), it)
        }
        listOf("plano", "origem", "quantidade", "tela", "screen_name", "cargo", "tipo", "valor", "metodo").forEach {
            assertFalse(isPersonalDataKey(it), it)
        }
    }

    @Test
    fun valorQueParecePessoal() {
        assertTrue(looksLikePersonalData("maria@exemplo.com"))
        assertTrue(looksLikePersonalData("(65) 99999-0000"))
        assertTrue(looksLikePersonalData("123.456.789-09"))
        assertFalse(looksLikePersonalData("anual"))
        assertFalse(looksLikePersonalData("2026"))
    }

    private enum class Plano { ANUAL }

    @Test
    fun dados_soPrimitivosSemPii() {
        val out = sanitizeEventData(
            mapOf(
                "plano" to Plano.ANUAL,
                "quantidade" to 3,
                "total" to 12L,
                "taxa" to 1.5,
                "fator" to 2.5f,
                "premium" to true,
                "email" to "x@y.com",
                "origem" to "ligou para (65) 99999-0000",
                "nulo" to null,
                "lista" to listOf(1),
                "infinito" to Double.POSITIVE_INFINITY,
                "  " to "branco",
                "=formula" to "ok",
                "curto" to 1.toShort(),
                "byte" to 1.toByte(),
            ),
        )!!
        assertEquals(JsonPrimitive("ANUAL"), out["plano"])
        assertEquals(JsonPrimitive(3), out["quantidade"])
        assertEquals(JsonPrimitive(12L), out["total"])
        assertEquals(JsonPrimitive(1.5), out["taxa"])
        assertEquals(JsonPrimitive(true), out["premium"])
        assertEquals(JsonPrimitive("ok"), out["formula"])
        assertTrue("fator" in out && "curto" in out && "byte" in out)
        listOf("email", "origem", "nulo", "lista", "infinito", "  ").forEach { assertFalse(it in out, it) }
    }

    @Test
    fun dados_vaziosOuSoPiiViramNulo_eTetoDeChaves() {
        assertNull(sanitizeEventData(emptyMap()))
        assertNull(sanitizeEventData(mapOf("email" to "a@b.co")))
        val muitos = (1..40).associate { "k$it" to it }
        assertEquals(ANALYTICS_MAX_DATA_KEYS, sanitizeEventData(muitos)!!.size)
        val longo = sanitizeEventData(mapOf("t" to "a".repeat(900)))!!
        assertEquals(ANALYTICS_MAX_STRING_VALUE, (longo["t"] as JsonPrimitive).content.length)
    }

    @Test
    fun userAgent_formatoDeNavegadorQueOUmamiAceita() {
        assertEquals(
            "Mozilla/5.0 (Linux; Android 14; Mobile) CodeCactoApp/1.4.2",
            analyticsUserAgent(AnalyticsDevice(AnalyticsDevice.OS_ANDROID, "14", false, "412x915"), "1.4.2"),
        )
        assertEquals(
            "Mozilla/5.0 (Linux; Android 15; Tablet) CodeCactoApp/2.0",
            analyticsUserAgent(AnalyticsDevice(AnalyticsDevice.OS_ANDROID, "15", true, null), "2.0"),
        )
        assertEquals(
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) CodeCactoApp/1.0.0",
            analyticsUserAgent(AnalyticsDevice(AnalyticsDevice.OS_IOS, "18.1", false, "393x852"), "1.0.0"),
        )
        assertEquals(
            "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) CodeCactoApp/0",
            analyticsUserAgent(AnalyticsDevice(AnalyticsDevice.OS_IOS, "17.5", true, null), null),
        )
        assertEquals(
            "Mozilla/5.0 (Linux; Android 0; Mobile) CodeCactoApp/1.0beta",
            analyticsUserAgent(AnalyticsDevice(AnalyticsDevice.OS_ANDROID, null, false, null), "1.0 beta)"),
        )
        assertEquals("tablet", AnalyticsDevice(AnalyticsDevice.OS_IOS, null, true, null).deviceType)
        assertEquals("mobile", AnalyticsDevice(AnalyticsDevice.OS_IOS, null, false, null).deviceType)
    }
}
