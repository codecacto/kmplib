package br.com.codecacto.kmplib.ui.components.html

import androidx.compose.runtime.Immutable
import kotlin.math.roundToInt

/**
 * De onde vem o documento exibido pelo [HtmlDocumentView].
 *
 * O documento costuma ser **dado pessoal sensível** (um laudo, um relatório clínico, um extrato), o
 * que faz da autenticação parte do contrato — e não há uma forma só de autenticar. As duas que
 * existem no ecossistema estão aqui.
 */
@Immutable
sealed interface HtmlDocumentSource {

    /**
     * HTML já em memória — **o caminho preferido**, e por três motivos concretos:
     *
     * 1. **A autenticação fica onde ela funciona.** O `DomainApiClient` já trata `Bearer`, renova
     *    token em 401 e devolve 402 como paywall; um cabeçalho passado ao WebView não faz nada disso
     *    e ainda esbarra na limitação de plataforma descrita em [Url].
     * 2. **O documento pode ser guardado** para leitura offline depois de aberto uma vez — que é o
     *    comportamento esperado de "o meu relatório".
     * 3. **O zoom funciona nas duas plataformas** (ver [HtmlDocumentView]).
     *
     * @param html o documento. Vem do backend inteiro; a lib **não** injeta CSS, cabeçalho ou tema
     *   nele — se o requisito é "idêntico ao PDF", qualquer coisa que o app acrescente é divergência.
     * @param baseUrl base para resolver caminhos relativos (imagens, folhas de estilo) e para decidir
     *   o que é link "de fora". `null` faz o documento ser tratado como isolado: **todo** link com
     *   destino vira navegação externa.
     */
    @Immutable
    data class Html(
        val html: String,
        val baseUrl: String? = null,
    ) : HtmlDocumentSource

    /**
     * Endereço carregado diretamente pelo componente nativo — para **URL assinada de curta duração**,
     * que é a forma correta de servir documento sensível sem sessão embutida no visualizador.
     *
     * @param url endereço absoluto (`https://`).
     * @param headers cabeçalhos do request **principal** (ex.: `Authorization`).
     *   **Limitação de plataforma, não da lib:** nem o `WebView` nem o `WKWebView` propagam
     *   cabeçalhos para os **subrecursos** (imagens, CSS, fontes) — se o documento carregar imagens
     *   autenticadas, elas virão vazias. Documento com subrecurso protegido pede [Html] (busque com
     *   o `DomainApiClient`) ou URL assinada por recurso.
     */
    @Immutable
    data class Url(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
    ) : HtmlDocumentSource
}

/** Endereço do documento corrente, quando existe — usado para decidir o que é âncora e o que é link. */
val HtmlDocumentSource.documentUrl: String?
    get() = when (this) {
        is HtmlDocumentSource.Html -> baseUrl
        is HtmlDocumentSource.Url -> url
    }

/** Falha ao carregar o documento. */
@Immutable
data class HtmlDocumentError(
    /** Mensagem técnica da plataforma (para log/GlitchTip), não texto de tela. */
    val message: String,
    /** Código HTTP quando a falha veio do servidor; `0` para falha de transporte/render. */
    val code: Int = 0,
    /** Endereço que falhou, quando conhecido. */
    val url: String? = null,
)

/** Estado do carregamento do documento. */
@Immutable
sealed interface HtmlDocumentState {

    /**
     * Carregando.
     *
     * @param progress fração `0f..1f`, ou `null` quando **indeterminado**. O Android informa o
     *   progresso real; no iOS o `WKWebView` só o expõe por observação de propriedade (KVO), que a
     *   lib não instala — então lá é sempre `null`. Trate `null` como "gire um indicador", nunca como
     *   zero: uma barra parada em 0% diz à pessoa que nada está acontecendo.
     */
    @Immutable
    data class Loading(val progress: Float? = null) : HtmlDocumentState

    /** Documento renderizado. */
    data object Ready : HtmlDocumentState

    /** Falhou. O componente exibe o estado de erro com "tentar novamente". */
    @Immutable
    data class Failed(val error: HtmlDocumentError) : HtmlDocumentState
}

/** Textos do [HtmlDocumentView] (i18n; defaults pt-BR). */
@Immutable
data class HtmlDocumentTexts(
    val loading: String = "Carregando documento…",
    val errorTitle: String = "Não foi possível abrir o documento",
    val errorMessage: String = "Verifique sua conexão e tente novamente.",
    val retry: String = "Tentar novamente",
)

/** O que fazer com uma navegação pedida de dentro do documento. */
@Immutable
sealed interface HtmlLinkDecision {

    /** Segue dentro do visualizador (o próprio documento, ou uma âncora dele). */
    data object Allow : HtmlLinkDecision

    /**
     * Não navega aqui: o endereço é devolvido ao app, que abre no navegador do sistema (ou trata
     * como deep link). O visualizador é conteúdo, não um navegador embutido — abrir um site dentro
     * dele deixaria a pessoa presa numa janela sem barra de endereço e sem como voltar.
     */
    @Immutable
    data class OpenExternally(val url: String) : HtmlLinkDecision

    /** Recusado sem entregar a ninguém. */
    @Immutable
    data class Block(val reason: HtmlBlockReason) : HtmlLinkDecision
}

/** Por que uma navegação foi recusada. */
enum class HtmlBlockReason {
    /**
     * Esquema que não deve sair de dentro de um documento: `javascript:` (execução), `file:` e
     * `content:` (leitura do armazenamento do aparelho), `data:` e `blob:` (conteúdo forjado que
     * herda a origem do documento). Um HTML vindo do servidor é conteúdo, e conteúdo pode ter sido
     * adulterado no caminho.
     */
    UnsupportedScheme,

    /** Endereço vazio ou impossível de interpretar. */
    InvalidUrl,
}

/** Esquemas recusados sempre — ver [HtmlBlockReason.UnsupportedScheme]. */
private val BLOCKED_SCHEMES = setOf("javascript", "file", "content", "data", "blob", "about")

/** Esquemas que **navegam** quando a navegação externa está liberada. */
private val WEB_SCHEMES = setOf("http", "https")

/**
 * Decide o destino de uma navegação pedida de dentro do documento. Lógica pura — testável, e é ela
 * que os dois `actual` consultam, para que Android e iOS não divirjam na regra de segurança.
 *
 * Ordem das regras (a primeira que casa vence):
 * 1. Endereço vazio/sem esquema reconhecível ⇒ [HtmlBlockReason.InvalidUrl].
 * 2. Esquema perigoso ⇒ [HtmlBlockReason.UnsupportedScheme] — **antes** de qualquer permissão, para
 *    que `allowExternalNavigation = true` não abra a porta de `javascript:`.
 * 3. É o **próprio documento**, ou uma **âncora** dele (`#secao`) ⇒ [HtmlLinkDecision.Allow]. Sem
 *    esta regra o índice de seções do documento pararia de funcionar, que é o defeito mais provável
 *    de um visualizador que "bloqueia links".
 * 4. `allowExternalNavigation` e esquema web ⇒ [HtmlLinkDecision.Allow].
 * 5. Qualquer outro (site, `mailto:`, `tel:`, `whatsapp:`) ⇒ [HtmlLinkDecision.OpenExternally].
 *
 * @param documentUrl endereço do documento carregado (ver [HtmlDocumentSource.documentUrl]).
 * @param isDocumentLoad `true` no carregamento do próprio documento (o WebView chama o mesmo gancho).
 */
fun htmlLinkDecision(
    url: String,
    documentUrl: String?,
    allowExternalNavigation: Boolean,
    isDocumentLoad: Boolean = false,
): HtmlLinkDecision {
    val target = url.trim()
    if (target.isEmpty()) return HtmlLinkDecision.Block(HtmlBlockReason.InvalidUrl)

    val scheme = htmlUrlScheme(target)
    if (scheme != null && scheme in BLOCKED_SCHEMES) {
        // `about:blank` é o que o próprio componente carrega ao ser liberado; nunca é um link.
        return HtmlLinkDecision.Block(HtmlBlockReason.UnsupportedScheme)
    }
    if (scheme == null) {
        // Sem esquema não há como decidir para onde vai (nem entregar ao navegador do sistema).
        return HtmlLinkDecision.Block(HtmlBlockReason.InvalidUrl)
    }

    if (isDocumentLoad || htmlIsSameDocument(target, documentUrl)) return HtmlLinkDecision.Allow
    if (allowExternalNavigation && scheme in WEB_SCHEMES) return HtmlLinkDecision.Allow

    return HtmlLinkDecision.OpenExternally(target)
}

/**
 * `true` quando [url] aponta para o **mesmo documento** que [documentUrl] — igual, ou diferindo só
 * pelo fragmento (`#secao`). É o que preserva a navegação por âncoras dentro do relatório.
 */
fun htmlIsSameDocument(url: String, documentUrl: String?): Boolean {
    if (documentUrl.isNullOrBlank()) return false
    return url.substringBefore('#') == documentUrl.substringBefore('#')
}

/**
 * Esquema de uma URL em minúsculas (`https`, `mailto`), ou `null` quando não há esquema válido.
 * Aceita só o formato do RFC 3986 (letra seguida de letras/dígitos/`+`/`-`/`.`), então um caminho
 * relativo (`/secao`) ou um texto qualquer devolve `null` em vez de virar esquema inventado.
 */
fun htmlUrlScheme(url: String): String? {
    val separator = url.indexOf(':')
    if (separator <= 0) return null
    val scheme = url.substring(0, separator)
    if (!scheme[0].isLetter()) return null
    if (!scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }) return null
    return scheme.lowercase()
}

/** Menor zoom aceito (50%). */
const val MIN_HTML_DOCUMENT_ZOOM: Float = 0.5f

/** Maior zoom aceito (300%) — acima disso um documento de largura fixa deixa de caber na tela. */
const val MAX_HTML_DOCUMENT_ZOOM: Float = 3f

/** Zoom válido a partir do multiplicador do tema, tolerando valor absurdo ou não-numérico. */
fun clampHtmlDocumentZoom(zoom: Float): Float = when {
    zoom.isNaN() -> 1f
    else -> zoom.coerceIn(MIN_HTML_DOCUMENT_ZOOM, MAX_HTML_DOCUMENT_ZOOM)
}

/**
 * Zoom em **porcentagem inteira**, no formato que o `WebSettings.setTextZoom` do Android espera.
 * O `AppTheme(fontScale = 1.6f)` vira `160`.
 */
fun htmlDocumentZoomPercent(zoom: Float): Int = (clampHtmlDocumentZoom(zoom) * 100).roundToInt()
