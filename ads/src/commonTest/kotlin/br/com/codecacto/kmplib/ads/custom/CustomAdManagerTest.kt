package br.com.codecacto.kmplib.ads.custom

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CustomAdManagerTest {

    @BeforeTest
    fun setUp() {
        CustomAdManager.reset()
    }

    @AfterTest
    fun tearDown() {
        CustomAdManager.reset()
    }

    @Test
    fun `estado inicial e vazio`() {
        assertEquals(emptyList(), CustomAdManager.ads.value)
        assertFalse(CustomAdManager.initialized.value)
        assertNull(CustomAdManager.config)
    }

    @Test
    fun `initialize marca como inicializado e propaga ads do source`() = runTest {
        val ads = listOf(
            CustomAd(id = "a", imageUrl = "https://x/1.png"),
            CustomAd(id = "b", imageUrl = "https://x/2.png"),
        )
        val source = FakeCustomAdSource(initialAds = ads)

        CustomAdManager.initialize(CustomAdConfig(projectSlug = "meu-app"), source, backgroundScope)
        runCurrent()

        assertTrue(CustomAdManager.initialized.value)
        assertEquals(ads, CustomAdManager.ads.value)
        assertEquals("meu-app", CustomAdManager.config?.projectSlug)
        assertEquals("app", CustomAdManager.config?.surface)
    }

    @Test
    fun `surface customizada e preservada na config`() = runTest {
        CustomAdManager.initialize(
            CustomAdConfig(projectSlug = "meu-app", surface = "home"),
            FakeCustomAdSource(),
            backgroundScope,
        )
        runCurrent()
        assertEquals("home", CustomAdManager.config?.surface)
    }

    @Test
    fun `emit subsequente atualiza o StateFlow ads`() = runTest {
        val source = FakeCustomAdSource(initialAds = emptyList())
        CustomAdManager.initialize(CustomAdConfig(), source, backgroundScope)
        runCurrent()
        assertEquals(emptyList(), CustomAdManager.ads.value)

        source.emit(listOf(CustomAd(id = "new", imageUrl = "https://x/3.png")))
        runCurrent()

        assertEquals(listOf("new"), CustomAdManager.ads.value.map { it.id })
    }

    @Test
    fun `refresh sem inicializar nao quebra`() {
        CustomAdManager.refresh()
        assertEquals(emptyList(), CustomAdManager.ads.value)
    }

    @Test
    fun `reset limpa estado`() = runTest {
        val source = FakeCustomAdSource(initialAds = listOf(CustomAd(id = "z")))
        CustomAdManager.initialize(CustomAdConfig(), source, backgroundScope)
        runCurrent()
        assertTrue(CustomAdManager.initialized.value)

        CustomAdManager.reset()

        assertFalse(CustomAdManager.initialized.value)
        assertEquals(emptyList(), CustomAdManager.ads.value)
        assertNull(CustomAdManager.config)
    }

    @Test
    fun `initialize chamado de novo substitui o source`() = runTest {
        val source1 = FakeCustomAdSource(initialAds = listOf(CustomAd(id = "old")))
        CustomAdManager.initialize(CustomAdConfig(), source1, backgroundScope)
        runCurrent()
        assertEquals(listOf("old"), CustomAdManager.ads.value.map { it.id })

        val source2 = FakeCustomAdSource(initialAds = listOf(CustomAd(id = "new")))
        CustomAdManager.initialize(CustomAdConfig(), source2, backgroundScope)
        runCurrent()

        assertEquals(listOf("new"), CustomAdManager.ads.value.map { it.id })
    }

    @Test
    fun `notifyImpression e notifyClick disparam callbacks da config`() = runTest {
        val impressions = mutableListOf<CustomAd>()
        val clicks = mutableListOf<CustomAd>()
        val config = CustomAdConfig(
            onImpression = { impressions += it },
            onClick = { clicks += it }
        )

        CustomAdManager.initialize(config, FakeCustomAdSource(), backgroundScope)
        val ad = CustomAd(id = "x", imageUrl = "https://x/x.png")

        CustomAdManager.notifyImpression(ad)
        CustomAdManager.notifyClick(ad)
        CustomAdManager.notifyClick(ad)

        assertEquals(listOf(ad), impressions)
        assertEquals(listOf(ad, ad), clicks)
    }
}
