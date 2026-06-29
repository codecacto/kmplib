package br.com.codecacto.kmplib.ads.custom

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class CustomAdTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `defaults sao seguros para construir sem argumentos`() {
        val ad = CustomAd()
        assertEquals("", ad.id)
        assertEquals("", ad.imageUrl)
        assertEquals("", ad.targetUrl)
        assertEquals(CustomAd.FORMAT_BANNER, ad.format)
        assertEquals("", ad.title)
        assertEquals("", ad.ctaLabel)
        assertEquals(CustomAd.DEFAULT_DURATION_SECONDS, ad.durationSeconds)
    }

    @Test
    fun `durationSeconds default e 5 quando ausente no payload`() {
        val raw = """
            {
              "id": "ad1",
              "imageUrl": "https://x/y.png",
              "format": "interstitial"
            }
        """.trimIndent()
        val ad = json.decodeFromString(CustomAd.serializer(), raw)
        assertEquals(5, ad.durationSeconds)
        assertEquals(CustomAd.DEFAULT_DURATION_SECONDS, ad.durationSeconds)
    }

    @Test
    fun `durationSeconds e lido do payload quando presente`() {
        val raw = """
            {
              "id": "ad1",
              "imageUrl": "https://x/y.png",
              "format": "interstitial",
              "durationSeconds": 8
            }
        """.trimIndent()
        val ad = json.decodeFromString(CustomAd.serializer(), raw)
        assertEquals(8, ad.durationSeconds)
    }

    @Test
    fun `serializa e desserializa preservando campos`() {
        val original = CustomAd(
            id = "abc123",
            imageUrl = "https://example.com/img.png",
            targetUrl = "https://example.com/product",
            format = CustomAd.FORMAT_INTERSTITIAL,
            title = "Promo",
            ctaLabel = "Saiba mais",
        )

        val encoded = json.encodeToString(CustomAd.serializer(), original)
        val decoded = json.decodeFromString(CustomAd.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `desserializa item minimo do payload`() {
        val raw = """
            {
              "id": "ad1",
              "imageUrl": "https://x/y.png",
              "targetUrl": "https://x/z",
              "format": "banner"
            }
        """.trimIndent()

        val ad = json.decodeFromString(CustomAd.serializer(), raw)

        assertEquals("ad1", ad.id)
        assertEquals("https://x/y.png", ad.imageUrl)
        assertEquals(CustomAd.FORMAT_BANNER, ad.format)
    }

    @Test
    fun `ignora campos extras do servidor sem falhar`() {
        // Servidor pode mandar campos que a lib nao usa — nao pode quebrar.
        val raw = """
            {
              "id": "ad1",
              "imageUrl": "https://x/y.png",
              "targetUrl": "https://x/z",
              "title": "App A",
              "ctaLabel": "Baixar",
              "active": true,
              "priority": 5
            }
        """.trimIndent()
        val ad = json.decodeFromString(CustomAd.serializer(), raw)
        assertEquals("App A", ad.title)
        assertEquals("Baixar", ad.ctaLabel)
    }

    @Test
    fun `constantes de formato batem com os literais esperados`() {
        // Esses literais batem com o contrato do apps-api. Mudar quebra dados em producao.
        assertEquals("banner", CustomAd.FORMAT_BANNER)
        assertEquals("interstitial", CustomAd.FORMAT_INTERSTITIAL)
    }
}
