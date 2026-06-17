package br.com.codecacto.kmplib.ads.custom

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomAdFiltersTest {

    // ====== matchesPlacement ======

    @Test
    fun `matchesPlacement com filtro nulo aceita qualquer placement`() {
        val ad = CustomAd(placementId = "qualquer")
        assertTrue(matchesPlacement(ad, placementId = null))
    }

    @Test
    fun `matchesPlacement bate igual`() {
        val ad = CustomAd(placementId = "home_top")
        assertTrue(matchesPlacement(ad, placementId = "home_top"))
    }

    @Test
    fun `matchesPlacement rejeita diferente`() {
        val ad = CustomAd(placementId = "home_top")
        assertFalse(matchesPlacement(ad, placementId = "settings"))
    }

    // ====== matchesAppId ======

    @Test
    fun `matchesAppId com filtro nulo aceita qualquer app`() {
        val ad = CustomAd(appId = "qualquer-app")
        assertTrue(matchesAppId(ad, appId = null))
    }

    @Test
    fun `matchesAppId bate igual`() {
        val ad = CustomAd(appId = "meu-app")
        assertTrue(matchesAppId(ad, appId = "meu-app"))
    }

    @Test
    fun `matchesAppId rejeita diferente`() {
        val ad = CustomAd(appId = "meu-app")
        assertFalse(matchesAppId(ad, appId = "outro-app"))
    }

    @Test
    fun `matchesAppId casa via appIds N a N`() {
        val ad = CustomAd(appId = "dono", appIds = listOf("dono", "app-b", "app-c"))
        assertTrue(matchesAppId(ad, appId = "app-b"))
        assertTrue(matchesAppId(ad, appId = "app-c"))
        assertTrue(matchesAppId(ad, appId = "dono"))
    }

    @Test
    fun `matchesAppId rejeita app fora do appId e do appIds`() {
        val ad = CustomAd(appId = "dono", appIds = listOf("app-b"))
        assertFalse(matchesAppId(ad, appId = "app-z"))
    }

    @Test
    fun `matchesAppId doc antigo sem appIds casa pelo appId legado`() {
        val ad = CustomAd(appId = "meu-app", appIds = emptyList())
        assertTrue(matchesAppId(ad, appId = "meu-app"))
        assertFalse(matchesAppId(ad, appId = "outro-app"))
    }

    @Test
    fun `matchesAppId casa por appIds mesmo com appId legado vazio`() {
        val ad = CustomAd(appId = "", appIds = listOf("app-b"))
        assertTrue(matchesAppId(ad, appId = "app-b"))
        assertFalse(matchesAppId(ad, appId = "app-z"))
    }

    // ====== isInTimeWindow ======

    @Test
    fun `isInTimeWindow sem startsAt nem endsAt sempre verdade`() {
        val ad = CustomAd(startsAt = null, endsAt = null)
        assertTrue(isInTimeWindow(ad, nowMillis = 0L))
        assertTrue(isInTimeWindow(ad, nowMillis = Long.MAX_VALUE))
    }

    @Test
    fun `isInTimeWindow respeita startsAt`() {
        val ad = CustomAd(startsAt = 1_000L)
        assertFalse(isInTimeWindow(ad, nowMillis = 999L))
        assertTrue(isInTimeWindow(ad, nowMillis = 1_000L))
        assertTrue(isInTimeWindow(ad, nowMillis = 1_001L))
    }

    @Test
    fun `isInTimeWindow respeita endsAt`() {
        val ad = CustomAd(endsAt = 2_000L)
        assertTrue(isInTimeWindow(ad, nowMillis = 1_999L))
        assertTrue(isInTimeWindow(ad, nowMillis = 2_000L))
        assertFalse(isInTimeWindow(ad, nowMillis = 2_001L))
    }

    @Test
    fun `isInTimeWindow combina startsAt e endsAt`() {
        val ad = CustomAd(startsAt = 1_000L, endsAt = 2_000L)
        assertFalse(isInTimeWindow(ad, nowMillis = 999L))
        assertTrue(isInTimeWindow(ad, nowMillis = 1_500L))
        assertFalse(isInTimeWindow(ad, nowMillis = 2_001L))
    }
}
