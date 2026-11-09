package br.com.codecacto.kmplib.help

import br.com.codecacto.kmplib.core.network.ApiResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

private fun flow(id: String, title: String = "Fluxo $id", summary: String? = null, steps: List<String> = emptyList()) =
    HelpFlow(id = id, slug = id, title = title, summary = summary, steps = steps, text = null, video = null)

private fun video(id: String, title: String = "Vídeo $id", flowId: String? = null, description: String? = null) = HelpVideo(
    id = id,
    flowId = flowId,
    type = "fluxo",
    title = title,
    description = description,
    format = HelpVideoFormat.VERTICAL,
    url = "https://x.y/$id.mp4",
    posterUrl = null,
    durationSeconds = null,
)

private val failure = ApiResult.Error(code = -1, message = "falhou")

/** Fonte cujas respostas o teste solta na ordem que quiser, sem responder a cancelamento. */
private class FakeSource(
    var catalog: ApiResult<HelpCatalog> = ApiResult.Success(HelpCatalog(true, emptyList(), emptyList())),
) : HelpSource {
    val pendingAsks = mutableListOf<Pair<String, CompletableDeferred<ApiResult<HelpAnswer>>>>()
    var catalogGate: CompletableDeferred<ApiResult<HelpCatalog>>? = null
    var catalogCalls = 0

    override suspend fun loadCatalog(): ApiResult<HelpCatalog> {
        catalogCalls++
        return catalogGate?.let { gate -> withContext(NonCancellable) { gate.await() } } ?: catalog
    }

    override suspend fun ask(question: String): ApiResult<HelpAnswer> {
        val deferred = CompletableDeferred<ApiResult<HelpAnswer>>()
        pendingAsks += question to deferred
        return withContext(NonCancellable) { deferred.await() }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HelpViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun HelpViewModel.asks(text: String) {
        onAction(HelpAction.QuestionChanged(text))
        onAction(HelpAction.Submit)
    }

    private fun found(id: String) = ApiResult.Success<HelpAnswer>(HelpAnswer.Found(flow(id)))

    @Test
    fun `abre com os primeiros fluxos como sugestao`() = runTest {
        val flows = (1..8).map { flow("f$it") }
        val vm = HelpViewModel(FakeSource(ApiResult.Success(HelpCatalog(true, flows, emptyList()))))
        assertEquals(HelpContentStatus.Ready, vm.state.value.status)
        assertEquals(flows.take(5), vm.state.value.suggestions)
    }

    @Test
    fun `ajuda desligada e falha de carga sao estados distintos`() = runTest {
        assertEquals(
            HelpContentStatus.Inactive,
            HelpViewModel(FakeSource(ApiResult.Success(HelpCatalog(false, emptyList(), emptyList())))).state.value.status,
        )
        val source = FakeSource(failure)
        val vm = HelpViewModel(source)
        assertEquals(HelpContentStatus.Failed, vm.state.value.status)
        source.catalog = ApiResult.Success(HelpCatalog(true, listOf(flow("a")), emptyList()))
        vm.onAction(HelpAction.RetryLoad)
        assertEquals(HelpContentStatus.Ready, vm.state.value.status)
    }

    @Test
    fun `pergunta curta nao vai ao servidor e marca o campo`() = runTest {
        val source = FakeSource()
        val vm = HelpViewModel(source)
        vm.asks(" oi ")
        assertTrue(source.pendingAsks.isEmpty())
        assertTrue(vm.state.value.questionError)
        vm.onAction(HelpAction.QuestionChanged("oi "))
        assertFalse(vm.state.value.questionError)
    }

    @Test
    fun `campo limita ao teto do contrato`() = runTest {
        val vm = HelpViewModel(FakeSource())
        vm.onAction(HelpAction.QuestionChanged("a".repeat(HELP_QUESTION_MAX_LENGTH + 50)))
        assertEquals(HELP_QUESTION_MAX_LENGTH, vm.state.value.question.length)
    }

    @Test
    fun `sem resposta e erro de rede sao distintos, e o erro repete a mesma pergunta`() = runTest {
        val source = FakeSource()
        val vm = HelpViewModel(source)
        vm.asks("como cancelo?")
        source.pendingAsks[0].second.complete(ApiResult.Success(HelpAnswer.NotFound))
        assertEquals(HelpAnswerState.NotFound, vm.state.value.answer)

        vm.onAction(HelpAction.ClearAnswer)
        vm.asks("como reagendo?")
        source.pendingAsks[1].second.complete(failure)
        assertEquals(HelpAnswerState.Failed, vm.state.value.answer)

        vm.onAction(HelpAction.RetryAsk)
        assertEquals("como reagendo?", source.pendingAsks[2].first)
        assertEquals(HelpAnswerState.Asking, vm.state.value.answer)
    }

    @Test
    fun `resposta de pergunta anterior nunca aparece`() = runTest {
        val source = FakeSource()
        val vm = HelpViewModel(source)
        vm.asks("primeira pergunta")
        vm.asks("segunda pergunta")

        source.pendingAsks[1].second.complete(found("segunda"))
        source.pendingAsks[0].second.complete(found("primeira"))

        assertEquals("segunda", assertIs<HelpAnswerState.Found>(vm.state.value.answer).flow.id)
    }

    @Test
    fun `resposta atrasada nao preenche o campo ja editado`() = runTest {
        val source = FakeSource()
        val vm = HelpViewModel(source)
        vm.asks("primeira pergunta")
        vm.onAction(HelpAction.QuestionChanged("primeira pergunta e mais"))
        assertEquals(HelpAnswerState.Idle, vm.state.value.answer)

        source.pendingAsks[0].second.complete(found("primeira"))

        assertEquals(HelpAnswerState.Idle, vm.state.value.answer)
    }

    @Test
    fun `sugestao escolhida com pergunta em voo vence a resposta atrasada`() = runTest {
        val source = FakeSource()
        val vm = HelpViewModel(source)
        vm.asks("primeira pergunta")
        vm.onAction(HelpAction.SuggestionSelected(flow("sugestao")))

        source.pendingAsks[0].second.complete(found("primeira"))

        assertEquals("sugestao", assertIs<HelpAnswerState.Found>(vm.state.value.answer).flow.id)
    }

    @Test
    fun `editar o texto apaga a resposta anterior, mas espaco no fim nao`() = runTest {
        val source = FakeSource()
        val vm = HelpViewModel(source)
        vm.asks("como cancelo")
        source.pendingAsks[0].second.complete(found("a"))
        vm.onAction(HelpAction.QuestionChanged("como cancelo "))
        assertIs<HelpAnswerState.Found>(vm.state.value.answer)
        vm.onAction(HelpAction.QuestionChanged("como cancelo o"))
        assertEquals(HelpAnswerState.Idle, vm.state.value.answer)
    }

    @Test
    fun `toque duplo no enviar nao duplica a pergunta`() = runTest {
        val source = FakeSource()
        val vm = HelpViewModel(source)
        vm.asks("como cancelo")
        vm.onAction(HelpAction.Submit)
        assertEquals(1, source.pendingAsks.size)
    }

    @Test
    fun `limpar a resposta volta para as sugestoes e esvazia o campo`() = runTest {
        val source = FakeSource()
        val vm = HelpViewModel(source)
        vm.asks("como cancelo")
        source.pendingAsks[0].second.complete(found("a"))
        vm.onAction(HelpAction.ClearAnswer)
        assertEquals(HelpAnswerState.Idle, vm.state.value.answer)
        assertEquals("", vm.state.value.question)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HelpLibraryViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val full = HelpCatalog(
        active = true,
        flows = listOf(flow("f1", "Cancelar agendamento"), flow("f2", "Cadastrar serviço")),
        videos = listOf(video("v1", "Demonstração", flowId = null), video("v2", "Cancelar", flowId = "f1")),
    )

    @Test
    fun `primeira carga mostra indicador e so depois a lista`() = runTest {
        val source = FakeSource().apply { catalogGate = CompletableDeferred() }
        val vm = HelpLibraryViewModel(source)
        vm.onAction(HelpLibraryAction.Resume)
        assertEquals(HelpContentStatus.Loading, vm.state.value.status)
        source.catalogGate!!.complete(ApiResult.Success(full))
        assertEquals(HelpContentStatus.Ready, vm.state.value.status)
        assertEquals(2, vm.state.value.visibleFlows.size)
        assertEquals(2, vm.state.value.visibleVideos.size)
    }

    @Test
    fun `busca filtra fluxos e videos e reaplica na recarga`() = runTest {
        val source = FakeSource(ApiResult.Success(full))
        val vm = HelpLibraryViewModel(source)
        vm.onAction(HelpLibraryAction.Resume)
        vm.onAction(HelpLibraryAction.QueryChanged("CANCEL"))
        assertEquals(listOf("f1"), vm.state.value.visibleFlows.map { it.id })
        assertEquals(listOf("v2"), vm.state.value.visibleVideos.map { it.id })

        vm.onAction(HelpLibraryAction.Resume)
        assertEquals(listOf("f1"), vm.state.value.visibleFlows.map { it.id })

        vm.onAction(HelpLibraryAction.QueryChanged("xyz"))
        assertFalse(vm.state.value.hasResults)
        assertTrue(vm.state.value.hasContent)
    }

    @Test
    fun `falha na primeira carga e erro, e o retry carrega`() = runTest {
        val source = FakeSource(failure)
        val vm = HelpLibraryViewModel(source)
        vm.onAction(HelpLibraryAction.Resume)
        assertEquals(HelpContentStatus.Failed, vm.state.value.status)
        source.catalog = ApiResult.Success(full)
        vm.onAction(HelpLibraryAction.Retry)
        assertEquals(HelpContentStatus.Ready, vm.state.value.status)
    }

    @Test
    fun `recarga silenciosa que falha mantem a lista sem aviso`() = runTest {
        val source = FakeSource(ApiResult.Success(full))
        val vm = HelpLibraryViewModel(source)
        vm.onAction(HelpLibraryAction.Resume)
        source.catalog = failure
        vm.onAction(HelpLibraryAction.Resume)
        assertEquals(HelpContentStatus.Ready, vm.state.value.status)
        assertEquals(2, vm.state.value.flows.size)
        assertFalse(vm.state.value.refreshFailed)
    }

    @Test
    fun `puxar para atualizar que falha mantem a lista, avisa e solta o indicador`() = runTest {
        val source = FakeSource(ApiResult.Success(full))
        val vm = HelpLibraryViewModel(source)
        vm.onAction(HelpLibraryAction.Resume)
        source.catalog = failure
        vm.onAction(HelpLibraryAction.Refresh)
        assertEquals(2, vm.state.value.flows.size)
        assertTrue(vm.state.value.refreshFailed)
        assertFalse(vm.state.value.isRefreshing)
    }

    @Test
    fun `carga mais recente vence a atrasada`() = runTest {
        val source = FakeSource()
        val first = CompletableDeferred<ApiResult<HelpCatalog>>()
        source.catalogGate = first
        val vm = HelpLibraryViewModel(source)
        vm.onAction(HelpLibraryAction.Resume)

        val second = CompletableDeferred<ApiResult<HelpCatalog>>()
        source.catalogGate = second
        vm.onAction(HelpLibraryAction.Refresh)

        second.complete(ApiResult.Success(full))
        first.complete(ApiResult.Success(HelpCatalog(true, emptyList(), emptyList())))

        assertEquals(2, vm.state.value.flows.size)
        assertFalse(vm.state.value.isRefreshing)
    }

    @Test
    fun `projeto desligado esvazia e fecha o detalhe`() = runTest {
        val source = FakeSource(ApiResult.Success(full))
        val vm = HelpLibraryViewModel(source)
        vm.onAction(HelpLibraryAction.Resume)
        vm.onAction(HelpLibraryAction.FlowSelected(full.flows[0]))
        source.catalog = ApiResult.Success(HelpCatalog(false, emptyList(), emptyList()))
        vm.onAction(HelpLibraryAction.Resume)
        assertEquals(HelpContentStatus.Inactive, vm.state.value.status)
        assertFalse(vm.state.value.hasContent)
        assertNull(vm.state.value.selection)
    }

    @Test
    fun `video de um fluxo abre o fluxo, video solto abre o video`() = runTest {
        val vm = HelpLibraryViewModel(FakeSource(ApiResult.Success(full)))
        vm.onAction(HelpLibraryAction.Resume)
        vm.onAction(HelpLibraryAction.VideoSelected(full.videos[1]))
        assertEquals("f1", assertIs<HelpLibrarySelection.Flow>(vm.state.value.selection).flow.id)
        vm.onAction(HelpLibraryAction.CloseDetail)
        assertNull(vm.state.value.selection)
        vm.onAction(HelpLibraryAction.VideoSelected(full.videos[0]))
        assertEquals("v1", assertIs<HelpLibrarySelection.Video>(vm.state.value.selection).video.id)
    }
}

class HelpSearchTest {

    private val flows = listOf(
        flow("a", "Cancelar agendamento", summary = "Desmarcar um horário"),
        flow("b", "Cadastrar serviço", steps = listOf("Abra o menu", "Toque em Serviços")),
    )

    @Test
    fun `procura por parte da palavra`() {
        assertEquals(listOf("a"), filterHelpFlows(flows, "agend").map { it.id })
    }

    @Test
    fun `ignora acento nos dois lados e caixa`() {
        assertEquals(listOf("a"), filterHelpFlows(flows, "HORARIO").map { it.id })
        assertEquals(listOf("b"), filterHelpFlows(flows, "servico").map { it.id })
        assertEquals(listOf("b"), filterHelpFlows(flows, "serviço").map { it.id })
    }

    @Test
    fun `palavras se somam, a segunda refina`() {
        assertEquals(listOf("a"), filterHelpFlows(flows, "cancel horario").map { it.id })
        assertTrue(filterHelpFlows(flows, "cancel servico").isEmpty())
    }

    @Test
    fun `procura tambem nos passos e busca em branco devolve tudo`() {
        assertEquals(listOf("b"), filterHelpFlows(flows, "menu").map { it.id })
        assertEquals(2, filterHelpFlows(flows, "   ").size)
    }

    @Test
    fun `videos buscam em titulo e descricao`() {
        val videos = listOf(video("v", "Demonstração", description = "Visão do cliente"))
        assertEquals(1, filterHelpVideos(videos, "demonstracao cliente").size)
        assertTrue(filterHelpVideos(videos, "profissional").isEmpty())
    }
}

class HelpAvailabilityCacheTest {

    private fun source(catalog: ApiResult<HelpCatalog>) = FakeSource(catalog)

    private val on = ApiResult.Success(HelpCatalog(true, emptyList(), emptyList()))
    private val off = ApiResult.Success(HelpCatalog(false, emptyList(), emptyList()))

    @Test
    fun `resposta fresca nao consulta de novo e vencida consulta`() = runTest {
        val time = TestTimeSource()
        val cache = HelpAvailabilityCache(timeSource = time, minInterval = 5.minutes)
        val fake = source(on)
        assertTrue(cache.refresh("k", fake))
        assertTrue(cache.refresh("k", fake))
        assertEquals(1, fake.catalogCalls)

        fake.catalog = off
        time += 6.minutes
        assertFalse(cache.refresh("k", fake))
        assertEquals(2, fake.catalogCalls)
    }

    @Test
    fun `sem nenhuma resposta fica desligado e falha mantem o valor conhecido`() = runTest {
        val time = TestTimeSource()
        val cache = HelpAvailabilityCache(timeSource = time, minInterval = 5.minutes)
        assertFalse(cache.refresh("sem-rede", source(failure)))

        assertTrue(cache.refresh("k", source(on)))
        time += 6.minutes
        assertTrue(cache.refresh("k", source(failure)))
    }

    @Test
    fun `combinacoes de conteudo sao independentes`() = runTest {
        val cache = HelpAvailabilityCache()
        assertTrue(cache.refresh("cliente", source(on)))
        assertFalse(cache.refresh("profissional", source(off)))
        assertEquals(mapOf("cliente" to true, "profissional" to false), cache.values.value)
    }
}
