package br.com.codecacto.kmplib.help

import androidx.compose.runtime.Composable
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_help_ask
import br.com.codecacto.kmplib.generated.resources.kmplib_help_ask_error
import br.com.codecacto.kmplib.generated.resources.kmplib_help_inactive
import br.com.codecacto.kmplib.generated.resources.kmplib_help_library_empty
import br.com.codecacto.kmplib.generated.resources.kmplib_help_library_flows
import br.com.codecacto.kmplib.generated.resources.kmplib_help_library_search
import br.com.codecacto.kmplib.generated.resources.kmplib_help_library_search_empty
import br.com.codecacto.kmplib.generated.resources.kmplib_help_library_title
import br.com.codecacto.kmplib.generated.resources.kmplib_help_library_videos
import br.com.codecacto.kmplib.generated.resources.kmplib_help_load_error
import br.com.codecacto.kmplib.generated.resources.kmplib_help_needs_details
import br.com.codecacto.kmplib.generated.resources.kmplib_help_new_conversation
import br.com.codecacto.kmplib.generated.resources.kmplib_help_not_found
import br.com.codecacto.kmplib.generated.resources.kmplib_help_open_library
import br.com.codecacto.kmplib.generated.resources.kmplib_help_question_placeholder
import br.com.codecacto.kmplib.generated.resources.kmplib_help_question_too_short
import br.com.codecacto.kmplib.generated.resources.kmplib_help_refresh_failed
import br.com.codecacto.kmplib.generated.resources.kmplib_help_retry
import br.com.codecacto.kmplib.generated.resources.kmplib_help_searching
import br.com.codecacto.kmplib.generated.resources.kmplib_help_see_steps
import br.com.codecacto.kmplib.generated.resources.kmplib_help_steps
import br.com.codecacto.kmplib.generated.resources.kmplib_help_suggestions
import br.com.codecacto.kmplib.generated.resources.kmplib_help_support
import br.com.codecacto.kmplib.generated.resources.kmplib_help_title
import br.com.codecacto.kmplib.generated.resources.kmplib_help_you_asked
import br.com.codecacto.kmplib.ui.locale.kmpStringResource

/** Textos da ajuda. Defaults pt-BR; [rememberHelpTexts] traz os 4 idiomas, no idioma da tela. */
data class HelpTexts(
    val title: String = "Ajuda",
    val questionPlaceholder: String = "Qual é a sua dúvida?",
    val ask: String = "Perguntar",
    val questionTooShort: String = "Escreva pelo menos 3 letras.",
    val suggestions: String = "Dúvidas comuns",
    val steps: String = "Passo a passo",
    val notFound: String = "Não encontrei isso aqui",
    val support: String = "Falar com o suporte",
    val openLibrary: String = "Ver a Biblioteca",
    val askError: String = "Não foi possível buscar a resposta.",
    val loadError: String = "Não foi possível carregar a ajuda.",
    val inactive: String = "Ajuda indisponível",
    val libraryTitle: String = "Biblioteca",
    val librarySearch: String = "Buscar na Biblioteca",
    val libraryEmpty: String = "Nenhum conteúdo por aqui ainda",
    val librarySearchEmpty: String = "Nada encontrado",
    val libraryVideos: String = "Vídeos",
    val libraryFlows: String = "Como fazer",
    val refreshFailed: String = "Não foi possível atualizar.",
    /** Ação do top bar que apaga a conversa (2.367.0). */
    val newConversation: String = "Nova conversa",
    /** Bolha de "sem resposta" quando a pergunta ficou vaga (`pedirDetalhes`, 2.367.0). */
    val needsDetails: String = "Não entendi, pode explicar melhor?",
    /** Bolha enquanto o servidor responde (2.367.0). */
    val searching: String = "Buscando…",
    /** Botão da resposta que abre o fluxo inteiro (2.367.0). */
    val seeSteps: String = "Ver passo a passo",
    /** Botão da bolha de erro (2.367.0). */
    val retry: String = "Tentar de novo",
    /** Rótulo de leitor de tela da bolha da pergunta (2.367.0). */
    val youAsked: String = "Você perguntou",
)

/** Os textos da ajuda no idioma da tela (pt-BR, en, es, pt-PT). */
@Composable
fun rememberHelpTexts(): HelpTexts = HelpTexts(
    title = kmpStringResource(Res.string.kmplib_help_title),
    questionPlaceholder = kmpStringResource(Res.string.kmplib_help_question_placeholder),
    ask = kmpStringResource(Res.string.kmplib_help_ask),
    questionTooShort = kmpStringResource(Res.string.kmplib_help_question_too_short),
    suggestions = kmpStringResource(Res.string.kmplib_help_suggestions),
    steps = kmpStringResource(Res.string.kmplib_help_steps),
    notFound = kmpStringResource(Res.string.kmplib_help_not_found),
    support = kmpStringResource(Res.string.kmplib_help_support),
    openLibrary = kmpStringResource(Res.string.kmplib_help_open_library),
    askError = kmpStringResource(Res.string.kmplib_help_ask_error),
    loadError = kmpStringResource(Res.string.kmplib_help_load_error),
    inactive = kmpStringResource(Res.string.kmplib_help_inactive),
    libraryTitle = kmpStringResource(Res.string.kmplib_help_library_title),
    librarySearch = kmpStringResource(Res.string.kmplib_help_library_search),
    libraryEmpty = kmpStringResource(Res.string.kmplib_help_library_empty),
    librarySearchEmpty = kmpStringResource(Res.string.kmplib_help_library_search_empty),
    libraryVideos = kmpStringResource(Res.string.kmplib_help_library_videos),
    libraryFlows = kmpStringResource(Res.string.kmplib_help_library_flows),
    refreshFailed = kmpStringResource(Res.string.kmplib_help_refresh_failed),
    newConversation = kmpStringResource(Res.string.kmplib_help_new_conversation),
    needsDetails = kmpStringResource(Res.string.kmplib_help_needs_details),
    searching = kmpStringResource(Res.string.kmplib_help_searching),
    seeSteps = kmpStringResource(Res.string.kmplib_help_see_steps),
    retry = kmpStringResource(Res.string.kmplib_help_retry),
    youAsked = kmpStringResource(Res.string.kmplib_help_you_asked),
)

/** Ids de automação (`resource-id` no Android, `accessibilityIdentifier` no iOS). */
object HelpTestTags {
    const val TOP_BAR_ACTION = "ajuda-btn-abrir"
    const val HELP_SCREEN = "ajuda-tela"
    const val QUESTION_INPUT = "ajuda-input-pergunta"
    const val ASK_BUTTON = "ajuda-btn-perguntar"
    const val ANSWER = "ajuda-resposta"
    const val NOT_FOUND = "ajuda-sem-resposta"
    const val SUPPORT_BUTTON = "ajuda-btn-suporte"
    const val OPEN_LIBRARY_BUTTON = "ajuda-btn-biblioteca"
    const val LIBRARY_SCREEN = "biblioteca-tela"
    const val LIBRARY_SEARCH = "biblioteca-input-busca"
    const val LIBRARY_DETAIL = "biblioteca-detalhe"

    /** A lista de bolhas (2.367.0). */
    const val CONVERSATION = "ajuda-conversa"
    const val NEW_CONVERSATION_BUTTON = "ajuda-btn-nova-conversa"
    const val ASKING = "ajuda-buscando"
    const val NEEDS_DETAILS = "ajuda-pedir-detalhes"
    const val ASK_ERROR = "ajuda-erro"
    const val RETRY_BUTTON = "ajuda-btn-tentar-de-novo"
    const val SEE_STEPS_BUTTON = "ajuda-btn-passo-a-passo"
    const val FLOW_DETAIL = "ajuda-fluxo-detalhe"

    /** "Falar com o suporte" dentro da bolha de sem resposta (o de baixo da conversa é [SUPPORT_BUTTON]). */
    const val SUPPORT_IN_ANSWER_BUTTON = "ajuda-btn-suporte-resposta"

    fun suggestion(slug: String): String = "ajuda-sugestao-$slug"

    /** Bolha da pergunta do turno de [turnId] (2.367.0). */
    fun questionBubble(turnId: Long): String = "ajuda-bolha-pergunta-$turnId"

    /** Bolha da resposta do turno de [turnId] (2.367.0). */
    fun answerBubble(turnId: Long): String = "ajuda-bolha-resposta-$turnId"
    fun libraryFlow(slug: String): String = "biblioteca-fluxo-$slug"
    fun libraryVideo(id: String): String = "biblioteca-video-$id"
}
