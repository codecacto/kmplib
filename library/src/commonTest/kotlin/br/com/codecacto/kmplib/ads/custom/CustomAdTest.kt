package br.com.codecacto.kmplib.ads.custom

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomAdTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `defaults sao seguros para construir sem argumentos`() {
        val ad = CustomAd()
        assertEquals("", ad.id)
        assertEquals("", ad.imageUrl)
        assertEquals("", ad.targetUrl)
        assertEquals(CustomAd.FORMAT_BANNER, ad.format)
        assertEquals("default", ad.placementId)
        assertTrue(ad.active)
        assertEquals(0, ad.priority)
        assertNull(ad.startsAt)
        assertNull(ad.endsAt)
    }

    @Test
    fun `serializa e desserializa preservando campos`() {
        val original = CustomAd(
            id = "abc123",
            appId = "meu-app",
            imageUrl = "https://example.com/img.png",
            targetUrl = "https://example.com/product",
            format = CustomAd.FORMAT_INTERSTITIAL,
            placementId = "home_top",
            active = true,
            priority = 5,
            weight = 70,
            startsAt = 1_700_000_000_000L,
            endsAt = 1_800_000_000_000L,
            title = "Promo",
            ctaLabel = "Saiba mais",
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_001_000L,
        )

        val encoded = json.encodeToString(CustomAd.serializer(), original)
        val decoded = json.decodeFromString(CustomAd.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `default weight e 100 quando doc nao traz o campo`() {
        val raw = """{ "imageUrl": "x", "targetUrl": "y" }"""
        val ad = json.decodeFromString(CustomAd.serializer(), raw)
        assertEquals(100, ad.weight)
    }

    @Test
    fun `desserializa documento minimo do Firestore`() {
        // doc tipico no Firestore: nao tem id (vem do snapshot), startsAt/endsAt opcionais
        val raw = """
            {
              "imageUrl": "https://x/y.png",
              "targetUrl": "https://x/z",
              "format": "banner",
              "placementId": "home",
              "active": true,
              "priority": 10
            }
        """.trimIndent()

        val ad = json.decodeFromString(CustomAd.serializer(), raw)

        assertEquals("https://x/y.png", ad.imageUrl)
        assertEquals(CustomAd.FORMAT_BANNER, ad.format)
        assertEquals("home", ad.placementId)
        assertEquals(10, ad.priority)
        assertNull(ad.startsAt)
        assertNull(ad.endsAt)
    }

    @Test
    fun `desserializa documento gravado pelo admin (com createdAt e updatedAt)`() {
        // O painel web (monitoramento/apps/web) grava createdAt e updatedAt
        // pra auditoria. A lib precisa aceitar esses campos sem falhar.
        val rawFromAdmin = """
            {
              "appId": "meu-app",
              "imageUrl": "https://x/y.png",
              "targetUrl": "https://x/z",
              "format": "banner",
              "placementId": "home_top",
              "active": true,
              "priority": 5,
              "title": "",
              "ctaLabel": "Saiba mais",
              "createdAt": 1700000000000,
              "updatedAt": 1700000001000
            }
        """.trimIndent()

        val ad = json.decodeFromString(CustomAd.serializer(), rawFromAdmin)
        assertEquals("meu-app", ad.appId)
        assertEquals("home_top", ad.placementId)
        assertEquals(1_700_000_000_000L, ad.createdAt)
        assertEquals(1_700_000_001_000L, ad.updatedAt)
    }

    @Test
    fun `appIds default vazio quando doc antigo nao traz o campo`() {
        // Docs gravados antes do N:N nao tem appIds — precisa cair em lista vazia.
        val raw = """{ "appId": "meu-app", "imageUrl": "x", "targetUrl": "y" }"""
        val ad = json.decodeFromString(CustomAd.serializer(), raw)
        assertEquals(emptyList(), ad.appIds)
        assertEquals("meu-app", ad.appId)
    }

    @Test
    fun `desserializa appIds N a N do painel`() {
        // O painel grava appIds (N:N) + appId legado preenchido.
        val raw = """
            {
              "appId": "dono",
              "appIds": ["dono", "app-b", "app-c"],
              "imageUrl": "https://x/y.png",
              "targetUrl": "https://x/z",
              "format": "banner",
              "active": true
            }
        """.trimIndent()
        val ad = json.decodeFromString(CustomAd.serializer(), raw)
        assertEquals(listOf("dono", "app-b", "app-c"), ad.appIds)
        assertEquals("dono", ad.appId)
    }

    @Test
    fun `constantes de formato batem com os literais esperados`() {
        // Esses literais ficam no Firestore. Mudar quebra dados em producao.
        assertEquals("banner", CustomAd.FORMAT_BANNER)
        assertEquals("interstitial", CustomAd.FORMAT_INTERSTITIAL)
    }
}
