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
import kotlin.time.Duration.Companion.hours
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

    /** Histórico recebido por pergunta, na ordem das chamadas. */
    val histories = mutableListOf<List<HelpHistoryTurn>>()
    val pendingReplies = mutableListOf<CompletableDeferred<ApiResult<HelpReply>>>()

    override suspend fun ask(question: String): ApiResult<HelpAnswer> {
        val deferred = CompletableDeferred<ApiResult<HelpAnswer>>()
        pendingAsks += question to deferred
        return withContext(NonCancellable) { deferred.await() }
    }

    override suspend fun ask(question: String, history: List<HelpHistoryTurn>): ApiResult<HelpReply> {
        val deferred = CompletableDeferred<ApiResult<HelpReply>>()
        pendingAsks += question to CompletableDeferred()
        histories += history
        pendingReplies += deferred
        return withContext(NonCancellable) { deferred.await() }
    }
}

/** Fonte escrita antes da 2.367.0: só a pergunta única. O default da interface tem de servir a ela. */
private class LegacySource(private val answer: HelpAnswer) : HelpSource {
    var asked = 0
    override suspend fun loadCatalog(): ApiResult<HelpCatalog> =
        ApiResult.Success(HelpCatalog(true, emptyList(), emptyList()))
    override suspend fun ask(question: String): ApiResult<HelpAnswer> {
        asked++
        return ApiResult.Success(answer)
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

    private fun found(id: String) = ApiResult.Success(HelpReply(HelpAnswer.Found(flow(id))))
    private val notFound = ApiResult.Success(HelpReply(HelpAnswer.NotFound))
    private val vague = ApiResult.Success(HelpReply(HelpAnswer.NotFound, needsDetails = true))

    private fun FakeSource.reply(index: Int, result: ApiResult<HelpReply>) = pendingReplies[index].complete(result)

    @Test
    fun `abre com os primeiros fluxos como sugestao`() = runTest {
        val flows = (1..8).map { flow("f$it") }
        val vm = HelpViewModel(FakeSource(ApiResult.Success(HelpCatalog(true, flows, emptyList()))))
        assertEquals(HelpContentStatus.Ready, vm.state.value.status)
        assertEquals(flows.take(5), vm.state.value.suggestions)
        assertTrue(vm.state.value.turns.isEmpty())
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
        assertTrue(vm.state.value.turns.isEmpty())
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
    fun `cada pergunta vira um turno, o campo esvazia e a resposta entra no turno`() = runTest {
        val source = FakeSource()
        val vm = HelpViewModel(source)
        vm.asks("  como cancelo?  ")
        assertEquals("", vm.state.value.question)
        val turn = vm.state.value.turns.single()
        assertEquals("como cancelo?", turn.question)
        assertEquals(HelpTurnStatus.Asking, turn.status)
        assertTrue(vm.state.value.isAsking)
        assertEquals(HelpAnswerState.Asking, vm.state.value.answer)

        source.reply(0, found("cancelar"))
        assertEquals("cancelar", assertIs<HelpTurnStatus.Found>(vm.state.value.turns.single().status).flow.id)
        assertEquals("cancelar", assertIs<HelpAnswerState.Found>(vm.state.value.answer).flow.id)
    }

    @Test
    fun `a primeira pergunta manda historico vazio e as seguintes os turnos anteriores com o slug`() = runTest {
        val source = FakeSource()
        val vm = HelpViewModel(source)
        vm.asks("como cancelo?")
        assertEquals(emptyList(), source.histories[0])
        source.reply(0, found("cancelar"))

        vm.asks("e o reembolso?")
        source.reply(1, notFound)

        vm.asks("e no cartão?")
        assertEquals(
            listOf(HelpHistoryTurn("como cancelo?", "cancelar"), HelpHistoryTurn("e o reembolso?", null)),
            source.histories[2],
        )
    }

    @Test
    fun `historico leva so os ultimos 5 e nunca os turnos com erro`() = runTest {
        val source = FakeSource()
        val vm = HelpViewModel(source)
        repeat(7) { i ->
            vm.asks("pergunta $i")
            source.reply(i, if (i == 5) failure else found("f$i"))
        }
        vm.asks("pergunta final")
        val history = source.histories.last()
        assertEquals(HELP_HISTORY_MAX_TURNS, history.size)
        assertEquals(listOf("pergunta 1", "pergunta 2", "pergunta 3", "pergunta 4", "pergunta 6"), history.map { it.question })
        assertTrue(history.none { it.question == "pergunta 5" })
    }

    @Test
    fun `pedirDetalhes vira o turno que pede explicacao, sem resposta comum continua igual`() = runTest {
        val source = FakeSource()
        val vm = HelpViewModel(source)
        vm.asks("ajuda")
        source.reply(0, vague)
        assertEquals(HelpTurnStatus.NotFound(needsDetails = true), vm.state.value.turns.last().status)
        assertEquals(HelpAnswerState.NotFound, vm.state.value.answer)

        vm.asks("como emito nota fiscal?")
        source.reply(1, notFound)
        assertEquals(HelpTurnStatus.NotFound(needsDetails = false), vm.state.value.turns.last().status)
        assertEquals(listOf(HelpHistoryTurn("ajuda", null)), source.histories[1])
    }

    @Test
    fun `erro de rede fica na bolha e o tentar de novo repete o mesmo turno com o mesmo historico`() = runTest {
        val source = FakeSource()
        val vm = HelpViewModel(source)
        vm.asks("como cancelo?")
        source.reply(0, found("cancelar"))
        vm.asks("como reagendo?")
        source.reply(1, failure)
        assertEquals(HelpTurnStatus.Failed, vm.state.value.turns.last().status)
        assertEquals(HelpAnswerState.Failed, vm.state.value.answer)

        vm.onAction(HelpAction.RetryAsk)
        assertEquals(2, vm.state.value.turns.size, "repetir não cria outro turno")
        assertEquals("como reagendo?", source.pendingAsks[2].first)
        assertEquals(listOf(HelpHistoryTurn("como cancelo?", "cancelar")), source.histories[2])
        source.reply(2, found("reagendar"))
        assertIs<HelpTurnStatus.Found>(vm.state.value.turns.last().status)
    }

    @Test
    fun `tentar de novo so vale para o turno mais recente que falhou`() = runTest {
        val source = FakeSource()
        val vm = HelpViewModel(source)
        vm.asks("primeira")
        source.reply(0, failure)
        vm.asks("segunda")
        source.reply(1, found("b"))
        vm.onAction(HelpAction.RetryAsk)
        assertEquals(2, source.pendingAsks.size)
    }

    @Test
    fun `toque duplo no enviar nao duplica a pergunta`() = runTest {
        val source = FakeSource()
        val vm = HelpViewModel(source)
        vm.asks("como cancelo")
        vm.asks("outra pergunta")
        assertEquals(1, source.pendingAsks.size)
        assertEquals(1, vm.state.value.turns.size)
        assertEquals("outra pergunta", vm.state.value.question, "o texto novo fica no campo para enviar depois")
    }

    @Test
    fun `nova conversa apaga os turnos e descarta a resposta em voo`() = runTest {
        val source = FakeSource()
        val vm = HelpViewModel(source)
        vm.asks("primeira pergunta")
        source.reply(0, found("a"))
        vm.asks("segunda pergunta")
        vm.onAction(HelpAction.NewConversation)
        assertTrue(vm.state.value.turns.isEmpty())
        assertEquals(HelpAnswerState.Idle, vm.state.value.answer)

        source.reply(1, found("atrasada"))
        assertTrue(vm.state.value.turns.isEmpty(), "resposta da conversa apagada não volta")

        vm.asks("recomeço")
        assertEquals(emptyList(), source.histories.last())
    }

    @Test
    fun `ClearAnswer de antes vale como nova conversa`() = runTest {
        val source = FakeSource()
        val vm = HelpViewModel(source)
        vm.asks("como cancelo")
        source.reply(0, found("a"))
        vm.onAction(HelpAction.ClearAnswer)
        assertTrue(vm.state.value.turns.isEmpty())
        assertEquals("", vm.state.value.question)
    }

    @Test
    fun `resposta atrasada de um turno nao cai em outro`() = runTest {
        val source = FakeSource()
        val vm = HelpViewModel(source)
        vm.asks("primeira")
        source.reply(0, found("a"))
        vm.asks("segunda")
        source.reply(1, found("b"))
        val ids = vm.state.value.turns.map { (it.status as HelpTurnStatus.Found).flow.id }
        assertEquals(listOf("a", "b"), ids)
    }

    @Test
    fun `sugestao entra na conversa respondida pelo proprio fluxo e vai no historico`() = runTest {
        val source = FakeSource()
        val vm = HelpViewModel(source)
        vm.onAction(HelpAction.SuggestionSelected(flow("cancelar", title = "Cancelar agendamento")))
        val turn = vm.state.value.turns.single()
        assertEquals("Cancelar agendamento", turn.question)
        assertIs<HelpTurnStatus.Found>(turn.status)
        assertTrue(source.pendingAsks.isEmpty())

        vm.asks("e se já passou do horário?")
        assertEquals(listOf(HelpHistoryTurn("Cancelar agendamento", "cancelar")), source.histories[0])
    }

    @Test
    fun `ver passo a passo abre o fluxo e fechar volta a conversa intacta`() = runTest {
        val source = FakeSource()
        val vm = HelpViewModel(source)
        vm.asks("como cancelo")
        source.reply(0, found("a"))
        val turns = vm.state.value.turns
        vm.onAction(HelpAction.OpenFlow(flow("a")))
        assertEquals("a", vm.state.value.openFlow?.id)
        vm.onAction(HelpAction.CloseFlow)
        assertNull(vm.state.value.openFlow)
        assertEquals(turns, vm.state.value.turns)
    }

    @Test
    fun `a conversa sobrevive a um novo ViewModel na mesma memoria e a pergunta em voo volta como falha`() = runTest {
        val memory = HelpConversationMemory()
        val source = FakeSource()
        val first = HelpViewModel(source, memory)
        first.asks("como cancelo")
        source.reply(0, found("a"))
        first.asks("como reagendo")

        val second = HelpViewModel(FakeSource(), memory)
        assertEquals(listOf("como cancelo", "como reagendo"), second.state.value.turns.map { it.question })
        assertEquals(HelpTurnStatus.Failed, second.state.value.turns.last().status)

        second.onAction(HelpAction.NewConversation)
        assertTrue(HelpViewModel(FakeSource(), memory).state.value.turns.isEmpty())
    }

    @Test
    fun `ids continuam crescendo entre ViewModels da mesma memoria`() = runTest {
        val memory = HelpConversationMemory()
        val source = FakeSource()
        HelpViewModel(source, memory).apply { asks("primeira"); source.reply(0, found("a")) }
        val second = HelpViewModel(source, memory)
        second.asks("segunda")
        val ids = second.state.value.turns.map { it.id }
        assertEquals(ids.distinct(), ids)
        assertTrue(ids[1] > ids[0])
    }

    @Test
    fun `memoria guarda no maximo o teto de turnos, os mais recentes`() = runTest {
        val source = FakeSource()
        val vm = HelpViewModel(source)
        repeat(HELP_CONVERSATION_MAX_TURNS + 5) { i ->
            vm.asks("pergunta numero $i")
            source.reply(i, notFound)
        }
        val turns = vm.state.value.turns
        assertEquals(HELP_CONVERSATION_MAX_TURNS, turns.size)
        assertEquals("pergunta numero ${HELP_CONVERSATION_MAX_TURNS + 4}", turns.last().question)
        assertEquals("pergunta numero 5", turns.first().question)
    }

    @Test
    fun `memorias por conteudo sao independentes e o logout apaga todas`() = runTest {
        val cliente = HelpConversations.forKey("teste|cliente")
        assertTrue(cliente === HelpConversations.forKey("teste|cliente"))
        cliente.write(listOf(HelpTurn(1, "x", HelpTurnStatus.Failed)))
        assertTrue(HelpConversations.forKey("teste|profissional").read().isEmpty())
        clearHelpConversations()
        assertTrue(cliente.read().isEmpty())
        assertTrue(HelpConversations.forKey("teste|cliente").read().isEmpty())
    }

    @Test
    fun `fonte escrita antes da conversa responde pelo default da interface`() = runTest {
        val legacy = LegacySource(HelpAnswer.NotFound)
        val vm = HelpViewModel(legacy)
        vm.asks("como cancelo")
        assertEquals(1, legacy.asked)
        assertEquals(HelpTurnStatus.NotFound(needsDetails = false), vm.state.value.turns.single().status)
    }

    @Test
    fun `turno e estado nao expoem a pergunta`() {
        val turn = HelpTurn(1, "meu cpf é 123", HelpTurnStatus.Asking)
        assertFalse(turn.toString().contains("123"))
        assertFalse(HelpState(turns = listOf(turn)).toString().contains("123"))
        assertFalse(HelpHistoryTurn("meu cpf é 123", null).toString().contains("123"))
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
    fun `true em cache vence depois de falhas alem da validade maxima`() = runTest {
        val time = TestTimeSource()
        val cache = HelpAvailabilityCache(timeSource = time, minInterval = 5.minutes, maxStaleness = 1.hours)
        assertTrue(cache.refresh("k", source(on)))

        time += 59.minutes
        assertTrue(cache.refresh("k", source(failure)))
        time += 1.minutes
        assertFalse(cache.refresh("k", source(failure)))
        assertFalse(cache.values.value.containsKey("k"))

        assertTrue(cache.refresh("k", source(on)), "resposta boa religa")
    }

    @Test
    fun `falso nao vira verdadeiro por falha`() = runTest {
        val time = TestTimeSource()
        val cache = HelpAvailabilityCache(timeSource = time, minInterval = 5.minutes, maxStaleness = 1.hours)
        assertFalse(cache.refresh("k", source(off)))
        time += 2.hours
        assertFalse(cache.refresh("k", source(failure)))
    }

    @Test
    fun `combinacoes de conteudo sao independentes`() = runTest {
        val cache = HelpAvailabilityCache()
        assertTrue(cache.refresh("cliente", source(on)))
        assertFalse(cache.refresh("profissional", source(off)))
        assertEquals(mapOf("cliente" to true, "profissional" to false), cache.values.value)
    }
}
