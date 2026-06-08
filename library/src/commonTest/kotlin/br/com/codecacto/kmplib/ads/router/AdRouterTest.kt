package br.com.codecacto.kmplib.ads.router

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
class AdRouterTest {

    @BeforeTest fun setUp() { AdRouter.reset() }
    @AfterTest fun tearDown() { AdRouter.reset() }

    @Test
    fun `estado inicial e OFF e nao inicializado`() {
        assertEquals(AdRouting.OFF, AdRouter.routing.value)
        assertFalse(AdRouter.initialized.value)
        assertNull(AdRouter.appId)
    }

    @Test
    fun `initialize sem doc no Firestore aplica defaults`() = runTest {
        val source = FakeAdRoutingSource(initialRouting = null)
        AdRouter.initialize("meu-app", defaults = AdRouting.ALL_CUSTOM, source = source, scope = backgroundScope)
        runCurrent()

        assertTrue(AdRouter.initialized.value)
        assertEquals("meu-app", AdRouter.appId)
        assertEquals(AdRouting.ALL_CUSTOM, AdRouter.routing.value)
    }

    @Test
    fun `initialize com doc existente usa valor do Firestore`() = runTest {
        val stored = AdRouting(banner = AdProvider.ADMOB, interstitial = AdProvider.CUSTOM, version = 7)
        val source = FakeAdRoutingSource(initialRouting = stored)
        AdRouter.initialize("meu-app", defaults = AdRouting.OFF, source = source, scope = backgroundScope)
        runCurrent()

        assertEquals(stored, AdRouter.routing.value)
    }

    @Test
    fun `atualizacao no source propaga para o routing flow`() = runTest {
        val source = FakeAdRoutingSource(initialRouting = AdRouting.ALL_CUSTOM)
        AdRouter.initialize("meu-app", source = source, scope = backgroundScope)
        runCurrent()
        assertEquals(AdRouting.ALL_CUSTOM, AdRouter.routing.value)

        // Admin trocou pra AdMob no portal
        source.set(AdRouting.ALL_ADMOB)
        runCurrent()
        assertEquals(AdRouting.ALL_ADMOB, AdRouter.routing.value)

        // Admin desligou
        source.set(AdRouting.OFF)
        runCurrent()
        assertEquals(AdRouting.OFF, AdRouter.routing.value)
    }

    @Test
    fun `reset limpa estado`() = runTest {
        AdRouter.initialize("x", source = FakeAdRoutingSource(AdRouting.ALL_ADMOB), scope = backgroundScope)
        runCurrent()
        assertTrue(AdRouter.initialized.value)

        AdRouter.reset()

        assertFalse(AdRouter.initialized.value)
        assertNull(AdRouter.appId)
        assertEquals(AdRouting.OFF, AdRouter.routing.value)
    }

    @Test
    fun `initialize trocando appId substitui o observer`() = runTest {
        val src1 = FakeAdRoutingSource(AdRouting.ALL_ADMOB)
        AdRouter.initialize("app-a", source = src1, scope = backgroundScope)
        runCurrent()
        assertEquals(AdRouting.ALL_ADMOB, AdRouter.routing.value)

        val src2 = FakeAdRoutingSource(AdRouting.ALL_CUSTOM)
        AdRouter.initialize("app-b", source = src2, scope = backgroundScope)
        runCurrent()
        assertEquals("app-b", AdRouter.appId)
        assertEquals(AdRouting.ALL_CUSTOM, AdRouter.routing.value)
    }
}
