package br.com.codecacto.kmplib.ads.custom

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdSelectionTest {

    @Test
    fun `retorna null quando nao ha candidatos`() {
        assertNull(selectAd(emptyList(), CustomAd.FORMAT_BANNER, placementId = null))
    }

    @Test
    fun `filtra por formato`() {
        val ads = listOf(
            CustomAd(id = "1", imageUrl = "x", format = CustomAd.FORMAT_INTERSTITIAL),
            CustomAd(id = "2", imageUrl = "x", format = CustomAd.FORMAT_BANNER),
        )
        val picked = selectAd(ads, CustomAd.FORMAT_BANNER, placementId = null)
        assertEquals("2", picked?.id)
    }

    @Test
    fun `filtra por placementId quando informado`() {
        val ads = listOf(
            CustomAd(id = "1", imageUrl = "x", placementId = "home"),
            CustomAd(id = "2", imageUrl = "x", placementId = "settings"),
        )
        val picked = selectAd(ads, CustomAd.FORMAT_BANNER, placementId = "settings")
        assertEquals("2", picked?.id)
    }

    @Test
    fun `descarta anuncios com imageUrl vazia`() {
        val ads = listOf(
            CustomAd(id = "1", imageUrl = ""),
            CustomAd(id = "2", imageUrl = "x"),
        )
        val picked = selectAd(ads, CustomAd.FORMAT_BANNER, placementId = null)
        assertEquals("2", picked?.id)
    }

    @Test
    fun `priority manda quando ha so um vencedor`() {
        val ads = listOf(
            CustomAd(id = "low", imageUrl = "x", priority = 1, weight = 1000),
            CustomAd(id = "high", imageUrl = "x", priority = 10, weight = 1),
        )
        // Apesar do weight enorme do "low", "high" tem maior priority e vence.
        repeat(20) {
            assertEquals("high", selectAd(ads, CustomAd.FORMAT_BANNER, null)?.id)
        }
    }

    @Test
    fun `sorteio ponderado distribui aproximadamente conforme weight`() {
        val ads = listOf(
            CustomAd(id = "a", imageUrl = "x", priority = 5, weight = 1),
            CustomAd(id = "b", imageUrl = "x", priority = 5, weight = 9),
        )
        // Random fixo + 1000 iteracoes: esperamos ~10% "a", ~90% "b".
        // Margem larga pra evitar flakiness: 5%-15% pra "a".
        val random = Random(seed = 42)
        val counts = mutableMapOf("a" to 0, "b" to 0)
        repeat(1000) {
            val picked = selectAd(ads, CustomAd.FORMAT_BANNER, null, random)
            assertNotNull(picked)
            counts[picked.id] = counts.getValue(picked.id) + 1
        }
        val pctA = counts.getValue("a") / 10.0 // sobre 1000 → percent
        assertTrue(pctA in 5.0..15.0, "Esperava ~10% pra 'a', veio $pctA%")
    }

    @Test
    fun `quando todos weights sao zero retorna o primeiro do topo`() {
        val ads = listOf(
            CustomAd(id = "1", imageUrl = "x", priority = 5, weight = 0),
            CustomAd(id = "2", imageUrl = "x", priority = 5, weight = 0),
        )
        val picked = selectAd(ads, CustomAd.FORMAT_BANNER, null)
        assertEquals("1", picked?.id)
    }
}
