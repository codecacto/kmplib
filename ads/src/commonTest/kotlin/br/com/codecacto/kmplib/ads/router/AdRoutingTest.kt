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
    fun `atalho ALL_CUSTOM`() {
        assertEquals(AdProvider.CUSTOM, AdRouting.ALL_CUSTOM.banner)
        assertEquals(AdProvider.CUSTOM, AdRouting.ALL_CUSTOM.interstitial)
        // House ads nao tem variante app_open — fica off.
        assertEquals(AdProvider.OFF, AdRouting.ALL_CUSTOM.appOpen)
    }

    @Test
    fun `serializa e desserializa preservando campos`() {
        val original = AdRouting(
            banner = AdProvider.CUSTOM,
            interstitial = AdProvider.OFF,
            appOpen = AdProvider.OFF,
            version = 42L,
            updatedAt = 1_700_000_000_000L,
        )
        val encoded = json.encodeToString(AdRouting.serializer(), original)
        val decoded = json.decodeFromString(AdRouting.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `desserializa doc legado sem campo appOpen com default OFF`() {
        val legacyDoc = """
            {
              "banner": "custom",
              "interstitial": "custom",
              "version": 1,
              "updatedAt": 1700000000000
            }
        """.trimIndent()

        val routing = json.decodeFromString(AdRouting.serializer(), legacyDoc)
        assertEquals(AdProvider.CUSTOM, routing.banner)
        assertEquals(AdProvider.CUSTOM, routing.interstitial)
        assertEquals(AdProvider.OFF, routing.appOpen)
    }

    @Test
    fun `enum serializa em lowercase para bater com o que o servidor grava`() {
        // O servidor grava "custom"/"off". Se o @SerialName mudar pra uppercase por engano, a
        // desserializacao silenciosamente cai no default e nada aparece pro usuario.
        val routing = AdRouting(banner = AdProvider.CUSTOM, interstitial = AdProvider.OFF)
        // encodeDefaults=true para que o valor default (OFF) também seja emitido e
        // possamos validar o @SerialName lowercase de ambos os campos.
        val encoder = Json { encodeDefaults = true }
        val encoded = encoder.encodeToString(AdRouting.serializer(), routing)
        assertTrue(encoded.contains("\"banner\":\"custom\""), "Esperava banner=custom, veio: $encoded")
        assertTrue(encoded.contains("\"interstitial\":\"off\""), "Esperava interstitial=off, veio: $encoded")
    }

    @Test
    fun `desserializa config do servidor (case lowercase, campos extras)`() {
        val rawFromServer = """
            {
              "banner": "custom",
              "interstitial": "off",
              "version": 3,
              "updatedAt": 1700000000000
            }
        """.trimIndent()

        val routing = json.decodeFromString(AdRouting.serializer(), rawFromServer)
        assertEquals(AdProvider.CUSTOM, routing.banner)
        assertEquals(AdProvider.OFF, routing.interstitial)
        assertEquals(3L, routing.version)
        assertEquals(1_700_000_000_000L, routing.updatedAt)
    }

    @Test
    fun `AdProvider fromString tolera case e espacos`() {
        assertEquals(AdProvider.CUSTOM, AdProvider.fromString("custom"))
        assertEquals(AdProvider.CUSTOM, AdProvider.fromString(" Custom "))
        assertEquals(AdProvider.OFF, AdProvider.fromString("off"))
    }

    @Test
    fun `AdProvider fromString cai para OFF em valor desconhecido ou nulo`() {
        assertEquals(AdProvider.OFF, AdProvider.fromString(null))
        assertEquals(AdProvider.OFF, AdProvider.fromString(""))
        assertEquals(AdProvider.OFF, AdProvider.fromString("xpto"))
        // "admob" agora e desconhecido -> OFF (provider removido).
        assertEquals(AdProvider.OFF, AdProvider.fromString("admob"))
    }
}
