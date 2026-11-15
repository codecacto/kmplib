package br.com.codecacto.kmplib.help

import androidx.lifecycle.viewModelScope
import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.ui.mvi.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Estado da [HelpScreen]: uma CONVERSA (2.367.0). Cada pergunta vira um turno; a resposta é sempre um fluxo
 * escrito (nada gerado) ou "sem resposta".
 *
 * - Toda pergunta leva o histórico dos turnos anteriores ([HelpSource.ask] com histórico): só os que tiveram
 *   resposta do servidor (achou ou não achou), nunca os que falharam por rede; a fonte corta nos últimos 5.
 * - A conversa mora na memória do processo ([HelpConversationMemory]): o ViewModel morre ao sair da tela, a
 *   conversa não. Pergunta que estava no ar quando a tela fechou volta como falha, com "Tentar de novo".
 * - Anti-corrida: um envio por vez (o segundo toque é ignorado enquanto o primeiro espera), e "Nova conversa"
 *   avança [askGeneration] — a resposta de uma geração antiga é descartada ainda que o servidor responda depois.
 */
class HelpViewModel internal constructor(
    private val source: HelpSource,
    private val conversation: HelpConversationMemory,
) : BaseViewModel<HelpState, HelpAction, Nothing>(HelpState()) {

    /** Conversa só deste ViewModel, sem sobreviver a ele (a [HelpScreen] usa a da memória do processo). */
    constructor(source: HelpSource) : this(source, HelpConversationMemory())

    private var askGeneration = 0L
    private var askJob: Job? = null

    init {
        val restored = conversation.read().map { turn ->
            if (turn.status == HelpTurnStatus.Asking) turn.copy(status = HelpTurnStatus.Failed) else turn
        }
        if (restored.isNotEmpty()) setTurns(restored)
        loadSuggestions()
    }

    override fun onAction(action: HelpAction) {
        when (action) {
            is HelpAction.QuestionChanged -> updateState {
                it.copy(question = action.text.take(HELP_QUESTION_MAX_LENGTH), questionError = false)
            }
            HelpAction.Submit -> submit()
            is HelpAction.SuggestionSelected -> selectSuggestion(action.flow)
            HelpAction.ClearAnswer, HelpAction.NewConversation -> newConversation()
            HelpAction.RetryAsk -> retryLast()
            HelpAction.RetryLoad -> loadSuggestions()
            is HelpAction.OpenFlow -> updateState { it.copy(openFlow = action.flow) }
            HelpAction.CloseFlow -> updateState { it.copy(openFlow = null) }
        }
    }

    private fun submit() {
        val question = currentState.question.trim()
        if (question.length < HELP_QUESTION_MIN_LENGTH) {
            updateState { it.copy(questionError = true) }
            return
        }
        if (currentState.isAsking) return
        val turn = HelpTurn(id = conversation.newTurnId(), question = question, status = HelpTurnStatus.Asking)
        val history = historyBefore(currentState.turns)
        updateState { it.copy(question = "", questionError = false) }
        setTurns(currentState.turns + turn)
        ask(turn, history)
    }

    private fun selectSuggestion(flow: HelpFlow) {
        if (currentState.isAsking) return
        val turn = HelpTurn(id = conversation.newTurnId(), question = flow.title, status = HelpTurnStatus.Found(flow))
        setTurns(currentState.turns + turn)
    }

    private fun retryLast() {
        val last = currentState.turns.lastOrNull() ?: return
        if (last.status != HelpTurnStatus.Failed) return
        val history = historyBefore(currentState.turns.dropLast(1))
        setTurns(currentState.turns.dropLast(1) + last.copy(status = HelpTurnStatus.Asking))
        ask(last, history)
    }

    private fun ask(turn: HelpTurn, history: List<HelpHistoryTurn>) {
        askGeneration++
        askJob?.cancel()
        val generation = askGeneration
        askJob = viewModelScope.launch {
            val result = source.ask(turn.question, history)
            if (generation != askGeneration) return@launch
            val status = result.toTurnStatus()
            setTurns(currentState.turns.map { if (it.id == turn.id) it.copy(status = status) else it })
        }
    }

    private fun newConversation() {
        askGeneration++
        askJob?.cancel()
        askJob = null
        conversation.clear()
        updateState { it.copy(question = "", questionError = false, openFlow = null) }
        setTurns(emptyList())
    }

    /** Grava na memória (com o teto) e publica, recalculando o resumo [HelpState.answer]. */
    private fun setTurns(turns: List<HelpTurn>) {
        conversation.write(turns)
        val kept = conversation.read()
        updateState { it.copy(turns = kept, answer = kept.lastOrNull().toAnswerState()) }
    }

    private fun ApiResult<HelpReply>.toTurnStatus(): HelpTurnStatus = when (this) {
        is ApiResult.Success -> when (val answer = data.answer) {
            is HelpAnswer.Found -> HelpTurnStatus.Found(answer.flow)
            HelpAnswer.NotFound -> HelpTurnStatus.NotFound(needsDetails = data.needsDetails)
        }
        is ApiResult.Error, ApiResult.Loading -> HelpTurnStatus.Failed
    }

    private fun loadSuggestions() {
        updateState { it.copy(status = HelpContentStatus.Loading) }
        viewModelScope.launch {
            val status: HelpContentStatus
            var suggestions = emptyList<HelpFlow>()
            when (val result = source.loadCatalog()) {
                is ApiResult.Success -> {
                    status = if (result.data.active) HelpContentStatus.Ready else HelpContentStatus.Inactive
                    suggestions = result.data.flows.take(HELP_SUGGESTION_COUNT)
                }
                is ApiResult.Error, ApiResult.Loading -> status = HelpContentStatus.Failed
            }
            updateState { it.copy(status = status, suggestions = suggestions) }
        }
    }

    private companion object {
        const val HELP_SUGGESTION_COUNT = 5
    }
}

/** Os turnos que vão ao servidor como contexto: os que ele respondeu, nunca os que falharam ou esperam. */
internal fun historyBefore(turns: List<HelpTurn>): List<HelpHistoryTurn> = turns.mapNotNull { turn ->
    when (val status = turn.status) {
        is HelpTurnStatus.Found -> HelpHistoryTurn(turn.question, status.flow.slug)
        is HelpTurnStatus.NotFound -> HelpHistoryTurn(turn.question, null)
        HelpTurnStatus.Asking, HelpTurnStatus.Failed -> null
    }
}.takeLast(HELP_HISTORY_MAX_TURNS)

private fun HelpTurn?.toAnswerState(): HelpAnswerState = when (val status = this?.status) {
    null -> HelpAnswerState.Idle
    HelpTurnStatus.Asking -> HelpAnswerState.Asking
    is HelpTurnStatus.Found -> HelpAnswerState.Found(status.flow)
    is HelpTurnStatus.NotFound -> HelpAnswerState.NotFound
    HelpTurnStatus.Failed -> HelpAnswerState.Failed
}

/** Estado da [HelpLibraryScreen]. A carga mais recente vence, como na pergunta da ajuda. */
class HelpLibraryViewModel(
    private val source: HelpSource,
) : BaseViewModel<HelpLibraryState, HelpLibraryAction, Nothing>(HelpLibraryState()) {

    private var loadGeneration = 0L
    private var loadJob: Job? = null

    override fun onAction(action: HelpLibraryAction) {
        when (action) {
            HelpLibraryAction.Resume -> load()
            HelpLibraryAction.Refresh -> {
                updateState { it.copy(isRefreshing = true, refreshFailed = false) }
                load()
            }
            HelpLibraryAction.Retry -> {
                updateState { it.copy(status = HelpContentStatus.Loading) }
                load()
            }
            is HelpLibraryAction.QueryChanged -> updateState {
                it.copy(
                    query = action.query,
                    visibleFlows = filterHelpFlows(it.flows, action.query),
                    visibleVideos = filterHelpVideos(it.videos, action.query),
                )
            }
            is HelpLibraryAction.FlowSelected -> updateState {
                it.copy(selection = HelpLibrarySelection.Flow(action.flow))
            }
            is HelpLibraryAction.VideoSelected -> selectVideo(action.video)
            HelpLibraryAction.CloseDetail -> updateState { it.copy(selection = null) }
        }
    }

    /** Vídeo de um fluxo abre o fluxo, que traz o vídeo e os passos. */
    private fun selectVideo(video: HelpVideo) {
        val flow = video.flowId?.let { id -> currentState.flows.firstOrNull { it.id == id } }
        val selection = if (flow != null) HelpLibrarySelection.Flow(flow) else HelpLibrarySelection.Video(video)
        updateState { it.copy(selection = selection) }
    }

    private fun load() {
        loadGeneration++
        loadJob?.cancel()
        val generation = loadGeneration
        loadJob = viewModelScope.launch {
            val result = source.loadCatalog()
            if (generation != loadGeneration) return@launch
            updateState { it.applying(result).copy(isRefreshing = false) }
        }
    }

    private fun HelpLibraryState.applying(result: ApiResult<HelpCatalog>): HelpLibraryState = when (result) {
        is ApiResult.Success -> if (result.data.active) {
            copy(
                status = HelpContentStatus.Ready,
                refreshFailed = false,
                flows = result.data.flows,
                videos = result.data.videos,
                visibleFlows = filterHelpFlows(result.data.flows, query),
                visibleVideos = filterHelpVideos(result.data.videos, query),
            )
        } else {
            copy(
                status = HelpContentStatus.Inactive,
                refreshFailed = false,
                flows = emptyList(),
                videos = emptyList(),
                visibleFlows = emptyList(),
                visibleVideos = emptyList(),
                selection = null,
            )
        }
        is ApiResult.Error, ApiResult.Loading -> if (status == HelpContentStatus.Ready) {
            // A lista que já está na tela fica; só quem puxou para atualizar recebe o aviso.
            copy(refreshFailed = isRefreshing)
        } else {
            copy(status = HelpContentStatus.Failed)
        }
    }
}
