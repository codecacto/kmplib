package br.com.codecacto.kmplib.ads.custom

import br.com.codecacto.kmplib.ads.AdLoadState
import br.com.codecacto.kmplib.core.locale.deviceLanguageTag
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.currentTime
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

    @Test
    fun `loadState vai de IDLE a LOADING e a READY na primeira resposta`() = runTest {
        assertEquals(AdLoadState.IDLE, CustomAdManager.loadState.value)
        val pending = PendingSource()
        CustomAdManager.initialize(CustomAdConfig(), pending, backgroundScope)
        runCurrent()
        assertEquals(AdLoadState.LOADING, CustomAdManager.loadState.value)

        pending.respond(listOf(CustomAd(id = "a", imageUrl = "https://x/a.png")))
        runCurrent()
        assertEquals(AdLoadState.READY, CustomAdManager.loadState.value)
    }

    @Test
    fun `resposta vazia tambem e READY - nao ha o que esperar`() = runTest {
        CustomAdManager.initialize(CustomAdConfig(), FakeCustomAdSource(emptyList()), backgroundScope)
        runCurrent()
        assertEquals(AdLoadState.READY, CustomAdManager.loadState.value)
    }

    @Test
    fun `fonte que lanca vira FAILED e nao derruba o app`() = runTest {
        CustomAdManager.initialize(CustomAdConfig(), ThrowingSource, backgroundScope)
        runCurrent()
        assertEquals(AdLoadState.FAILED, CustomAdManager.loadState.value)
        assertEquals(emptyList(), CustomAdManager.ads.value)
    }

    @Test
    fun `fonte que termina sem emitir vira FAILED`() = runTest {
        CustomAdManager.initialize(CustomAdConfig(), SilentSource, backgroundScope)
        runCurrent()
        assertEquals(AdLoadState.FAILED, CustomAdManager.loadState.value)
    }

    @Test
    fun `awaitFirstLoad espera a resposta e respeita o teto`() = runTest {
        val pending = PendingSource()
        CustomAdManager.initialize(CustomAdConfig(), pending, backgroundScope)
        val waited = async { CustomAdManager.awaitFirstLoad() }
        runCurrent()
        assertFalse(waited.isCompleted)
        pending.respond(emptyList())
        assertTrue(waited.await())

        CustomAdManager.initialize(CustomAdConfig(), PendingSource(), backgroundScope)
        val start = currentTime
        assertFalse(CustomAdManager.awaitFirstLoad())
        assertEquals(5_000L, currentTime - start)
    }

    @Test
    fun `reinicializar nao deixa o observer antigo mexer no estado novo`() = runTest {
        CustomAdManager.initialize(CustomAdConfig(), SilentSource, backgroundScope)
        CustomAdManager.initialize(CustomAdConfig(), PendingSource(), backgroundScope)
        runCurrent()
        assertEquals(AdLoadState.LOADING, CustomAdManager.loadState.value)
    }

    @Test
    fun `reset volta a IDLE`() = runTest {
        CustomAdManager.initialize(CustomAdConfig(), FakeCustomAdSource(), backgroundScope)
        runCurrent()
        CustomAdManager.reset()
        assertEquals(AdLoadState.IDLE, CustomAdManager.loadState.value)
    }

    /** Fonte cuja primeira resposta só chega quando o teste manda — a rede lenta da abertura. */
    private class PendingSource : CustomAdSource {
        private val responses = MutableSharedFlow<List<CustomAd>>(replay = 1)
        fun respond(ads: List<CustomAd>) { responses.tryEmit(ads) }
        override fun observeAds(): Flow<List<CustomAd>> = responses
        override suspend fun fetchAds(): Result<List<CustomAd>> = Result.success(emptyList())
    }

    private object ThrowingSource : CustomAdSource {
        override fun observeAds(): Flow<List<CustomAd>> = flow { throw IllegalStateException("boom") }
        override suspend fun fetchAds(): Result<List<CustomAd>> = Result.success(emptyList())
    }

    @Test
    fun `app so pt-BR nunca recarrega por idioma`() = runTest {
        val source = CountingSource(listOf(CustomAd(id = "a")))
        CustomAdManager.initialize(CustomAdConfig(projectSlug = "meu-app"), source, backgroundScope)
        runCurrent()

        for (tag in listOf("en-US", "es-MX", "pt-PT", "pt-BR")) CustomAdManager.syncLanguage(tag)
        runCurrent()

        assertEquals(1, source.observations)
        assertEquals(listOf("a"), CustomAdManager.ads.value.map { it.id })
    }

    @Test
    fun `app global recarrega so quando o idioma do anuncio muda`() = runTest {
        val source = CountingSource(listOf(CustomAd(id = "a")))
        val config = CustomAdConfig(projectSlug = "meu-app", supportedLanguages = setOf("pt-BR", "en"))
        CustomAdManager.initialize(config, source, backgroundScope)
        runCurrent()
        // Parte de um idioma conhecido: o inicial depende do host do teste.
        CustomAdManager.syncLanguage("pt-BR")
        runCurrent()
        val base = source.observations

        CustomAdManager.syncLanguage("en-US")
        runCurrent()
        assertEquals(base + 1, source.observations)
        assertEquals(AdLoadState.READY, CustomAdManager.loadState.value)

        CustomAdManager.syncLanguage("en-GB")
        CustomAdManager.syncLanguage("en")
        runCurrent()
        assertEquals(base + 1, source.observations)
    }

    @Test
    fun `troca de idioma tira a arte antiga enquanto a nova nao chega`() = runTest {
        val flow = MutableSharedFlow<List<CustomAd>>()
        val source = object : CustomAdSource {
            override fun observeAds(): Flow<List<CustomAd>> = flow
            override suspend fun fetchAds(): Result<List<CustomAd>> = Result.success(emptyList())
        }
        val config = CustomAdConfig(projectSlug = "meu-app", supportedLanguages = setOf("pt-BR", "en"))
        CustomAdManager.initialize(config, source, backgroundScope)
        runCurrent()
        CustomAdManager.syncLanguage("pt-BR")
        runCurrent()
        flow.emit(listOf(CustomAd(id = "pt")))
        runCurrent()
        assertEquals(listOf("pt"), CustomAdManager.ads.value.map { it.id })

        CustomAdManager.syncLanguage("en-US")

        assertEquals(emptyList(), CustomAdManager.ads.value)
        assertEquals(AdLoadState.LOADING, CustomAdManager.loadState.value)
    }

    @Test
    fun `busca antiga nao sobrescreve a lista do idioma novo`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val source = object : CustomAdSource {
            override fun observeAds(): Flow<List<CustomAd>> = flow { emit(listOf(CustomAd(id = "novo"))) }
            override suspend fun fetchAds(): Result<List<CustomAd>> {
                gate.await()
                return Result.success(listOf(CustomAd(id = "velho")))
            }
        }
        val config = CustomAdConfig(projectSlug = "meu-app", supportedLanguages = setOf("pt-BR", "en"))
        CustomAdManager.initialize(config, source, backgroundScope)
        runCurrent()
        CustomAdManager.syncLanguage("pt-BR")
        runCurrent()

        CustomAdManager.refresh()
        runCurrent()
        CustomAdManager.syncLanguage("en-US")
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf("novo"), CustomAdManager.ads.value.map { it.id })
    }

    @Test
    fun `syncLanguage antes do initialize nao faz nada`() {
        CustomAdManager.syncLanguage("en-US")
        assertEquals(AdLoadState.IDLE, CustomAdManager.loadState.value)
    }

    @Test
    fun `config leva os idiomas ate a fonte REST`() = runTest {
        val url = CompletableDeferred<String>()
        val client = HttpClient(MockEngine { request ->
            url.complete(request.url.toString())
            respond("""{"ads":[]}""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        })
        val supported = setOf("pt-BR", "pt-PT", "en", "es")
        CustomAdManager.initialize(
            CustomAdConfig(projectSlug = "meu-app", httpClient = client, supportedLanguages = supported),
            scope = backgroundScope,
        )

        // O MockEngine roda fora do relógio do runTest: espera a requisição, não o scheduler.
        val pedida = Url(url.await())
        assertEquals(adLanguageQueryValue(resolveAdLanguage(deviceLanguageTag(), supported)), pedida.parameters["lang"])
    }

    private class CountingSource(private val list: List<CustomAd>) : CustomAdSource {
        var observations = 0
        override fun observeAds(): Flow<List<CustomAd>> = flow {
            observations++
            emit(list)
        }
        override suspend fun fetchAds(): Result<List<CustomAd>> = Result.success(list)
    }

    private object SilentSource : CustomAdSource {
        override fun observeAds(): Flow<List<CustomAd>> = emptyFlow()
        override suspend fun fetchAds(): Result<List<CustomAd>> = Result.success(emptyList())
    }
}
