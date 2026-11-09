package br.com.codecacto.kmplib.help

import br.com.codecacto.kmplib.core.text.matchesSearch

/** Situação do conteúdo carregado do servidor. */
enum class HelpContentStatus {
    Loading,
    Ready,

    /** Projeto com a ajuda desligada (`ativo:false`). */
    Inactive,
    Failed,
}

/** O que a tela mostra como resposta à dúvida. */
sealed interface HelpAnswerState {
    data object Idle : HelpAnswerState
    data object Asking : HelpAnswerState
    data class Found(val flow: HelpFlow) : HelpAnswerState
    data object NotFound : HelpAnswerState
    data object Failed : HelpAnswerState
}

data class HelpState(
    val question: String = "",
    val questionError: Boolean = false,
    val status: HelpContentStatus = HelpContentStatus.Loading,
    val suggestions: List<HelpFlow> = emptyList(),
    val answer: HelpAnswerState = HelpAnswerState.Idle,
) {
    /** A pergunta é texto livre: pode trazer dado pessoal, e não vai para log. */
    override fun toString(): String = "HelpState(status=$status, answer=${answer::class.simpleName})"
}

sealed interface HelpAction {
    data class QuestionChanged(val text: String) : HelpAction {
        /** O texto é livre e pode trazer dado pessoal. */
        override fun toString(): String = "QuestionChanged"
    }
    data object Submit : HelpAction
    data class SuggestionSelected(val flow: HelpFlow) : HelpAction

    /** Volta das respostas para as sugestões. */
    data object ClearAnswer : HelpAction

    /** Repete a última pergunta que falhou. */
    data object RetryAsk : HelpAction
    data object RetryLoad : HelpAction
}

/** O que está aberto no detalhe da Biblioteca. */
sealed interface HelpLibrarySelection {
    data class Flow(val flow: HelpFlow) : HelpLibrarySelection
    data class Video(val video: HelpVideo) : HelpLibrarySelection
}

data class HelpLibraryState(
    val status: HelpContentStatus = HelpContentStatus.Loading,
    val isRefreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    val query: String = "",
    val flows: List<HelpFlow> = emptyList(),
    val videos: List<HelpVideo> = emptyList(),
    val visibleFlows: List<HelpFlow> = emptyList(),
    val visibleVideos: List<HelpVideo> = emptyList(),
    val selection: HelpLibrarySelection? = null,
) {
    val hasContent: Boolean get() = flows.isNotEmpty() || videos.isNotEmpty()
    val hasResults: Boolean get() = visibleFlows.isNotEmpty() || visibleVideos.isNotEmpty()

    /** O termo de busca é livre e pode trazer dado pessoal. */
    override fun toString(): String =
        "HelpLibraryState(status=$status, flows=${flows.size}, videos=${videos.size}, detail=${selection != null})"
}

sealed interface HelpLibraryAction {
    /** Recarga silenciosa de quem volta à tela: não troca a lista por indicador de carregamento. */
    data object Resume : HelpLibraryAction

    /** O gesto de puxar para atualizar. */
    data object Refresh : HelpLibraryAction
    data object Retry : HelpLibraryAction
    data class QueryChanged(val query: String) : HelpLibraryAction {
        /** O termo de busca é livre e pode trazer dado pessoal. */
        override fun toString(): String = "QueryChanged"
    }
    data class FlowSelected(val flow: HelpFlow) : HelpLibraryAction
    data class VideoSelected(val video: HelpVideo) : HelpLibraryAction
    data object CloseDetail : HelpLibraryAction
}

/** Busca por partes, sem acento, em que as palavras se somam. Asteriscos não contam, nem na consulta. */
internal fun filterHelpFlows(flows: List<HelpFlow>, query: String): List<HelpFlow> {
    val cleanQuery = withoutHelpMarks(query)
    return flows.filter { flow ->
        val fields = listOf(flow.title, flow.summary, flow.text) + flow.steps
        matchesSearch(cleanQuery, *fields.map { it?.let(::withoutHelpMarks) }.toTypedArray())
    }
}

/** Mesma regra de [filterHelpFlows], nos vídeos. */
internal fun filterHelpVideos(videos: List<HelpVideo>, query: String): List<HelpVideo> {
    val cleanQuery = withoutHelpMarks(query)
    return videos.filter { video ->
        matchesSearch(cleanQuery, withoutHelpMarks(video.title), video.description?.let(::withoutHelpMarks))
    }
}
