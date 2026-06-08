package br.com.codecacto.kmplib.ads.router

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdRoutingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `defaults vao para OFF`() {
        val routing = AdRouting()
        assertEquals(AdProvider.OFF, routing.banner)
        assertEquals(AdProvider.OFF, routing.interstitial)
        assertEquals(AdProvider.OFF, routing.appOpen)
        assertEquals(0L, routing.version)
    }

    @Test
    fun `atalhos ALL_CUSTOM e ALL_ADMOB`() {
        assertEquals(AdProvider.CUSTOM, AdRouting.ALL_CUSTOM.banner)
        assertEquals(AdProvider.CUSTOM, AdRouting.ALL_CUSTOM.interstitial)
        // Custom Ads nao tem variante app_open — fica off.
        assertEquals(AdProvider.OFF, AdRouting.ALL_CUSTOM.appOpen)

        assertEquals(AdProvider.ADMOB, AdRouting.ALL_ADMOB.banner)
        assertEquals(AdProvider.ADMOB, AdRouting.ALL_ADMOB.interstitial)
        assertEquals(AdProvider.ADMOB, AdRouting.ALL_ADMOB.appOpen)
    }

    @Test
    fun `serializa e desserializa preservando campos`() {
        val original = AdRouting(
            banner = AdProvider.CUSTOM,
            interstitial = AdProvider.ADMOB,
            appOpen = AdProvider.ADMOB,
            version = 42L,
            updatedAt = 1_700_000_000_000L,
        )
        val encoded = json.encodeToString(AdRouting.serializer(), original)
        val decoded = json.decodeFromString(AdRouting.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `desserializa doc legado sem campo appOpen com default OFF`() {
        // Doc gravado antes de adicionar appOpen — desserializacao precisa
        // continuar funcionando, com appOpen caindo no default OFF.
        val legacyDoc = """
            {
              "banner": "admob",
              "interstitial": "admob",
              "version": 1,
              "updatedAt": 1700000000000
            }
        """.trimIndent()

        val routing = json.decodeFromString(AdRouting.serializer(), legacyDoc)
        assertEquals(AdProvider.ADMOB, routing.banner)
        assertEquals(AdProvider.ADMOB, routing.interstitial)
        assertEquals(AdProvider.OFF, routing.appOpen)
    }

    @Test
    fun `enum serializa em lowercase para bater com o que o admin grava`() {
        // O painel web grava "admob"/"custom"/"off". Se o @SerialName mudar
        // pra uppercase por engano, a desserializacao silenciosamente cai no
        // default e nada aparece pro usuario.
        val routing = AdRouting(banner = AdProvider.ADMOB, interstitial = AdProvider.CUSTOM)
        val encoded = json.encodeToString(AdRouting.serializer(), routing)
        assertTrue(encoded.contains("\"banner\":\"admob\""), "Esperava banner=admob, veio: $encoded")
        assertTrue(encoded.contains("\"interstitial\":\"custom\""), "Esperava interstitial=custom, veio: $encoded")
    }

    @Test
    fun `desserializa doc do admin (case lowercase, campos extras)`() {
        // Simula exatamente o que o painel web grava em app_ad_configs/{appId}
        val rawFromAdmin = """
            {
              "banner": "custom",
              "interstitial": "off",
              "version": 3,
              "updatedAt": 1700000000000
            }
        """.trimIndent()

        val routing = json.decodeFromString(AdRouting.serializer(), rawFromAdmin)
        assertEquals(AdProvider.CUSTOM, routing.banner)
        assertEquals(AdProvider.OFF, routing.interstitial)
        assertEquals(3L, routing.version)
        assertEquals(1_700_000_000_000L, routing.updatedAt)
    }

    @Test
    fun `AdProvider fromString tolera case e espacos`() {
        assertEquals(AdProvider.ADMOB, AdProvider.fromString("admob"))
        assertEquals(AdProvider.ADMOB, AdProvider.fromString(" ADMOB "))
        assertEquals(AdProvider.CUSTOM, AdProvider.fromString("Custom"))
        assertEquals(AdProvider.OFF, AdProvider.fromString("off"))
    }

    @Test
    fun `AdProvider fromString cai para OFF em valor desconhecido ou nulo`() {
        assertEquals(AdProvider.OFF, AdProvider.fromString(null))
        assertEquals(AdProvider.OFF, AdProvider.fromString(""))
        assertEquals(AdProvider.OFF, AdProvider.fromString("xpto"))
    }
}
