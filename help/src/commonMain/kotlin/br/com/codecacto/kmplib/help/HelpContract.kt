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

/**
 * A resposta do turno MAIS RECENTE da conversa (o modelo de "uma pergunta → uma resposta" de antes da 2.367.0).
 * A tela desenha [HelpState.turns]; este resumo segue derivado para quem o lia.
 */
sealed interface HelpAnswerState {
    data object Idle : HelpAnswerState
    data object Asking : HelpAnswerState
    data class Found(val flow: HelpFlow) : HelpAnswerState
    data object NotFound : HelpAnswerState
    data object Failed : HelpAnswerState
}

/** Como está a resposta de um turno da conversa (2.367.0). */
sealed interface HelpTurnStatus {
    /** Esperando o servidor: a bolha diz "Buscando…". */
    data object Asking : HelpTurnStatus

    /** O fluxo que responde: título, texto (ou resumo), vídeo e "Ver passo a passo". */
    data class Found(val flow: HelpFlow) : HelpTurnStatus

    /**
     * Nenhum fluxo responde. [needsDetails] = a pergunta ficou vaga ("Não entendi, pode explicar melhor?");
     * senão "Não encontrei isso aqui". Nos dois, "Falar com o suporte".
     */
    data class NotFound(val needsDetails: Boolean = false) : HelpTurnStatus

    /** Erro de rede ou resposta ilegível: a bolha oferece "Tentar de novo". Não entra no histórico. */
    data object Failed : HelpTurnStatus
}

/**
 * Um turno da conversa: a pergunta (bolha à direita) e a resposta (bolha à esquerda).
 *
 * @property id crescente na conversa, estável enquanto ela existe — chave da lista e do `testTag`.
 */
data class HelpTurn(
    val id: Long,
    val question: String,
    val status: HelpTurnStatus,
) {
    /** A pergunta é texto livre: pode trazer dado pessoal, e não vai para log. */
    override fun toString(): String = "HelpTurn(id=$id, status=${status::class.simpleName})"
}

data class HelpState(
    val question: String = "",
    val questionError: Boolean = false,
    val status: HelpContentStatus = HelpContentStatus.Loading,
    val suggestions: List<HelpFlow> = emptyList(),
    /** Resumo do turno mais recente; derivado de [turns]. */
    val answer: HelpAnswerState = HelpAnswerState.Idle,
    /** A conversa, mais antigo primeiro (2.367.0). Vazia = a tela mostra as sugestões. */
    val turns: List<HelpTurn> = emptyList(),
    /** Fluxo aberto inteiro pelo "Ver passo a passo"; `null` = a conversa está à vista (2.367.0). */
    val openFlow: HelpFlow? = null,
) {
    /** Há uma pergunta esperando o servidor: um novo envio é ignorado até ela voltar. */
    val isAsking: Boolean get() = turns.lastOrNull()?.status == HelpTurnStatus.Asking

    /** A pergunta é texto livre: pode trazer dado pessoal, e não vai para log. */
    override fun toString(): String =
        "HelpState(status=$status, answer=${answer::class.simpleName}, turns=${turns.size}, openFlow=${openFlow != null})"
}

sealed interface HelpAction {
    data class QuestionChanged(val text: String) : HelpAction {
        /** O texto é livre e pode trazer dado pessoal. */
        override fun toString(): String = "QuestionChanged"
    }
    data object Submit : HelpAction

    /** Sugestão tocada: entra na conversa como pergunta (o título) já respondida pelo próprio fluxo. */
    data class SuggestionSelected(val flow: HelpFlow) : HelpAction

    /** Mesmo efeito de [NewConversation] (o nome de antes da 2.367.0). */
    data object ClearAnswer : HelpAction

    /** Repete a pergunta do turno mais recente, quando ele falhou. */
    data object RetryAsk : HelpAction
    data object RetryLoad : HelpAction

    /** Apaga a conversa (memória) e volta às sugestões (2.367.0). */
    data object NewConversation : HelpAction

    /** "Ver passo a passo": abre o fluxo inteiro por cima da conversa (2.367.0). */
    data class OpenFlow(val flow: HelpFlow) : HelpAction

    /** Fecha o fluxo aberto e volta à conversa como estava (2.367.0). */
    data object CloseFlow : HelpAction
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
