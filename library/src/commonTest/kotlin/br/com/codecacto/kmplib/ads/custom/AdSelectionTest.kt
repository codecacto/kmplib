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
        assertNull(selectAd(emptyList(), CustomAd.FORMAT_BANNER))
    }

    @Test
    fun `filtra por formato`() {
        val ads = listOf(
            CustomAd(id = "1", imageUrl = "x", format = CustomAd.FORMAT_INTERSTITIAL),
            CustomAd(id = "2", imageUrl = "x", format = CustomAd.FORMAT_BANNER),
        )
        val picked = selectAd(ads, CustomAd.FORMAT_BANNER)
        assertEquals("2", picked?.id)
    }

    @Test
    fun `anuncio com format em branco casa com qualquer formato`() {
        val ads = listOf(CustomAd(id = "1", imageUrl = "x", format = ""))
        assertNotNull(selectAd(ads, CustomAd.FORMAT_BANNER))
        assertNotNull(selectAd(ads, CustomAd.FORMAT_INTERSTITIAL))
    }

    @Test
    fun `descarta anuncios com imageUrl vazia`() {
        val ads = listOf(
            CustomAd(id = "1", imageUrl = ""),
            CustomAd(id = "2", imageUrl = "x"),
        )
        val picked = selectAd(ads, CustomAd.FORMAT_BANNER)
        assertEquals("2", picked?.id)
    }

    @Test
    fun `retorna o unico elegivel`() {
        val ads = listOf(CustomAd(id = "so-eu", imageUrl = "x"))
        repeat(10) {
            assertEquals("so-eu", selectAd(ads, CustomAd.FORMAT_BANNER)?.id)
        }
    }

    @Test
    fun `rotacao uniforme distribui entre os elegiveis`() {
        val ads = listOf(
            CustomAd(id = "a", imageUrl = "x"),
            CustomAd(id = "b", imageUrl = "x"),
        )
        val random = Random(seed = 42)
        val counts = mutableMapOf("a" to 0, "b" to 0)
        repeat(1000) {
            val picked = selectAd(ads, CustomAd.FORMAT_BANNER, random)
            assertNotNull(picked)
            counts[picked.id] = counts.getValue(picked.id) + 1
        }
        // Distribuicao ~50/50 — margem larga pra evitar flakiness.
        val pctA = counts.getValue("a") / 10.0
        assertTrue(pctA in 35.0..65.0, "Esperava ~50% pra 'a', veio $pctA%")
    }
}
