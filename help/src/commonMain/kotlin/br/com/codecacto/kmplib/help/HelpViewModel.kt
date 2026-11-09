package br.com.codecacto.kmplib.help

import androidx.lifecycle.viewModelScope
import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.ui.mvi.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Estado da [HelpScreen].
 *
 * Só a pergunta mais recente pode preencher a tela: cada nova pergunta, edição do campo ou escolha de
 * sugestão avança [askGeneration], e a resposta de uma geração antiga é descartada ainda que o servidor
 * responda depois.
 */
class HelpViewModel(private val source: HelpSource) : BaseViewModel<HelpState, HelpAction, Nothing>(HelpState()) {

    private var askGeneration = 0L
    private var askJob: Job? = null
    private var askedQuestion: String? = null

    init {
        loadSuggestions()
    }

    override fun onAction(action: HelpAction) {
        when (action) {
            is HelpAction.QuestionChanged -> onQuestionChanged(action.text)
            HelpAction.Submit -> submit()
            is HelpAction.SuggestionSelected -> {
                invalidateAsk()
                updateState { it.copy(answer = HelpAnswerState.Found(action.flow), questionError = false) }
            }
            HelpAction.ClearAnswer -> {
                invalidateAsk()
                updateState { it.copy(question = "", questionError = false, answer = HelpAnswerState.Idle) }
            }
            HelpAction.RetryAsk -> askedQuestion?.let(::ask)
            HelpAction.RetryLoad -> loadSuggestions()
        }
    }

    private fun onQuestionChanged(text: String) {
        val limited = text.take(HELP_QUESTION_MAX_LENGTH)
        // Espaço no fim não muda o que foi perguntado; qualquer outra edição torna a resposta alheia ao campo.
        val staleAnswer = limited.trim() != askedQuestion
        if (staleAnswer) invalidateAsk()
        updateState {
            it.copy(
                question = limited,
                questionError = false,
                answer = if (staleAnswer) HelpAnswerState.Idle else it.answer,
            )
        }
    }

    private fun submit() {
        val question = currentState.question.trim()
        if (question.length < HELP_QUESTION_MIN_LENGTH) {
            updateState { it.copy(questionError = true) }
            return
        }
        if (currentState.answer == HelpAnswerState.Asking && question == askedQuestion) return
        ask(question)
    }

    private fun ask(question: String) {
        invalidateAsk()
        val generation = askGeneration
        askedQuestion = question
        updateState { it.copy(answer = HelpAnswerState.Asking, questionError = false) }
        askJob = viewModelScope.launch {
            val result = source.ask(question)
            if (generation != askGeneration) return@launch
            updateState { it.copy(answer = result.toAnswerState()) }
        }
    }

    private fun invalidateAsk() {
        askGeneration++
        askJob?.cancel()
        askJob = null
        askedQuestion = null
    }

    private fun ApiResult<HelpAnswer>.toAnswerState(): HelpAnswerState = when (this) {
        is ApiResult.Success -> when (val answer = data) {
            is HelpAnswer.Found -> HelpAnswerState.Found(answer.flow)
            HelpAnswer.NotFound -> HelpAnswerState.NotFound
        }
        is ApiResult.Error, ApiResult.Loading -> HelpAnswerState.Failed
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
