package br.com.codecacto.kmplib.ads.stats

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun utcMillis(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
    LocalDateTime(year, month, day, hour, minute, 0)
        .toInstant(TimeZone.UTC)
        .toEpochMilliseconds()

class FakeAdStatsRecorder : AdStatsRecorder {
    data class Event(
        val provider: AdProviderTag,
        val appId: String,
        val format: AdFormat,
        val adId: String?,
        val type: AdEventType,
    )

    val events = mutableListOf<Event>()
    var throwOnNextCall = false

    override suspend fun record(
        provider: AdProviderTag,
        appId: String,
        format: AdFormat,
        adId: String?,
        type: AdEventType,
    ) {
        if (throwOnNextCall) {
            throwOnNextCall = false
            throw RuntimeException("simulated error")
        }
        events += Event(provider, appId, format, adId, type)
    }
}

class AdStatsTest {

    @BeforeTest fun setUp() { AdStats.reset() }
    @AfterTest fun tearDown() { AdStats.reset() }

    @Test
    fun `quando nao inicializado tudo vira no-op`() {
        // Nao deve lancar nem gravar nada
        AdStats.recordImpression(AdProviderTag.CUSTOM, AdFormat.BANNER, "ad1")
        AdStats.recordClick(AdProviderTag.ADMOB, AdFormat.INTERSTITIAL)
        assertFalse(AdStats.enabled)
    }

    @Test
    fun `initialize ativa e injeta recorder`() = runTest {
        val fake = FakeAdStatsRecorder()
        AdStats.initialize(appId = "meu-app", recorder = fake, scope = backgroundScope)
        assertTrue(AdStats.enabled)
        assertEquals("meu-app", AdStats.appId)

        AdStats.recordImpression(AdProviderTag.CUSTOM, AdFormat.BANNER, "ad1")
        AdStats.recordClick(AdProviderTag.ADMOB, AdFormat.INTERSTITIAL)
        runCurrent()

        assertEquals(2, fake.events.size)
        assertEquals(
            FakeAdStatsRecorder.Event(
                provider = AdProviderTag.CUSTOM,
                appId = "meu-app",
                format = AdFormat.BANNER,
                adId = "ad1",
                type = AdEventType.IMPRESSION,
            ),
            fake.events[0],
        )
        assertEquals(AdEventType.CLICK, fake.events[1].type)
        assertEquals(AdProviderTag.ADMOB, fake.events[1].provider)
    }

    @Test
    fun `erro no recorder e silenciosamente engolido`() = runTest {
        val fake = FakeAdStatsRecorder().also { it.throwOnNextCall = true }
        AdStats.initialize(appId = "meu-app", recorder = fake, scope = backgroundScope)

        // Nao deve crashar — erro vira log warning
        AdStats.recordImpression(AdProviderTag.CUSTOM, AdFormat.BANNER, "ad1")
        AdStats.recordClick(AdProviderTag.CUSTOM, AdFormat.BANNER, "ad1")
        runCurrent()

        // primeiro evento lancou exception, segundo gravou normal
        assertEquals(1, fake.events.size)
        assertEquals(AdEventType.CLICK, fake.events[0].type)
    }

    @Test
    fun `reset desativa`() = runTest {
        val fake = FakeAdStatsRecorder()
        AdStats.initialize(appId = "x", recorder = fake, scope = backgroundScope)
        assertTrue(AdStats.enabled)

        AdStats.reset()

        assertFalse(AdStats.enabled)
        assertEquals("", AdStats.appId)

        // Apos reset, eventos viram no-op
        AdStats.recordImpression(AdProviderTag.CUSTOM, AdFormat.BANNER, "ad1")
        runCurrent()
        assertEquals(0, fake.events.size)
    }

    @Test
    fun `todayKey usa UTC para casar com o painel admin`() {
        // Esses casos seriam quebrados se a lib usasse timezone local em vez de UTC
        // — o admin (`getUTCFullYear`) colocaria os eventos num bucket diferente.
        assertEquals("2026-05-26", AdStats.todayKey(utcMillis(2026, 5, 26, 2, 30)))
        assertEquals("2026-05-25", AdStats.todayKey(utcMillis(2026, 5, 25, 23, 59)))
        // Virada exata da meia-noite UTC
        assertEquals("2026-05-26", AdStats.todayKey(utcMillis(2026, 5, 26, 0, 0)))
    }

    @Test
    fun `buildStatKey gera ID estavel e legivel`() {
        assertEquals(
            "custom__meu-app__banner__ad1__2026-05-25",
            buildStatKey(AdProviderTag.CUSTOM, "meu-app", AdFormat.BANNER, "ad1", "2026-05-25"),
        )
        assertEquals(
            "admob__meu-app__interstitial__all__2026-05-25",
            buildStatKey(AdProviderTag.ADMOB, "meu-app", AdFormat.INTERSTITIAL, null, "2026-05-25"),
        )
        // appId vazio vira "unknown"
        assertEquals(
            "admob__unknown__banner__all__2026-05-25",
            buildStatKey(AdProviderTag.ADMOB, "", AdFormat.BANNER, "", "2026-05-25"),
        )
    }
}
