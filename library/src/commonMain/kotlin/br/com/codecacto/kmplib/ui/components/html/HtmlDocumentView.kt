package br.com.codecacto.kmplib.ui.components.html

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.getUrlLauncher
import br.com.codecacto.kmplib.ui.components.ErrorState
import br.com.codecacto.kmplib.ui.theme.LocalFontScale

/**
 * **Visualizador de documento HTML vindo do backend** — para exibir na tela, dentro do app, um
 * documento cujo layout precisa ser **idêntico** ao do PDF equivalente (laudo, relatório, contrato,
 * fatura, termo).
 *
 * ### Por que não redesenhar o documento em Compose
 * Quando o requisito é fidelidade, reimplementar as seções na UI nativa é justamente o que produz a
 * divergência: o PDF é gerado pelo servidor a partir do mesmo HTML, e duas implementações do mesmo
 * documento **sempre** acabam diferentes — primeiro num detalhe, depois num número. Uma fonte só,
 * renderizada como ela é.
 *
 * ### Padrão-ouro: o componente nativo de cada plataforma
 * `android.webkit.WebView` no Android e `WKWebView` no iOS, e nada além disso — nenhum renderizador
 * de HTML próprio, nenhuma conversão para markdown, nenhum truque de `Text` com `AnnotatedString`.
 *
 * ### Isto é um visualizador de documento, não um navegador embutido
 * As três travas abaixo são o que separa uma coisa da outra, e vêm ligadas por padrão:
 * - **JavaScript desligado** ([allowJavaScript] = `false`). Um documento é conteúdo; conteúdo que
 *   executa código dentro do app é superfície de ataque, e um relatório não precisa de script.
 * - **Navegação externa interceptada** ([allowExternalNavigation] = `false`): link para fora é
 *   devolvido ao app e **aberto no navegador do sistema**, com barra de endereço e botão de voltar.
 *   Abrir dentro deixaria a pessoa presa numa janela sem saída — e sem saber em que site está.
 *   Âncoras internas (`#secao`) continuam funcionando: são navegação **dentro** do documento.
 * - **Esquemas perigosos recusados sempre** (`javascript:`, `file:`, `content:`, `data:`, `blob:`),
 *   inclusive com [allowExternalNavigation] ligado.
 * - **Sem armazenamento persistente**: no iOS o `WKWebView` usa um `WKWebsiteDataStore` não
 *   persistente; no Android o `WebView` recusa cookies de terceiros, `localStorage` fica desligado
 *   junto com o JavaScript e o acesso a arquivos do aparelho é bloqueado. Documento sensível não
 *   deixa rastro em disco depois de fechado.
 *
 * ### Autenticação — as duas formas, e qual escolher
 * Ver [HtmlDocumentSource]. Em resumo: **URL assinada de curta duração** ([HtmlDocumentSource.Url])
 * ou — preferível — **buscar o HTML com o `DomainApiClient`** e passar a string
 * ([HtmlDocumentSource.Html]), que é o que dá renovação de token, tratamento de 402 e cache local
 * para releitura offline.
 *
 * ### Zoom acompanha o tamanho de fonte do app
 * [zoom] vem de `LocalFontScale` (o `AppTheme(fontScale = ...)`), então o ajuste de "letra maior" do
 * app vale **dentro** do documento — que costuma ser longo e lido por quem está cansado.
 * **Diferença de plataforma, declarada:** o Android tem `textZoom` e amplia **só o texto** (o layout
 * se re-flui); o `WKWebView` não tem equivalente sem executar JavaScript, então o iOS usa a API
 * oficial `pageZoom` e amplia a **página inteira** (o layout é preservado, com rolagem horizontal se
 * o documento tiver largura fixa). Nenhuma das duas exige JavaScript ligado, que era o requisito.
 *
 * ### Altura e rolagem
 * O documento **rola sozinho, por dentro**. Dê a ele uma altura definida — `Modifier.weight(1f)`
 * dentro de uma `Column`, ou o espaço todo:
 *
 * ```kotlin
 * Column(Modifier.fillMaxSize()) {
 *     BackTopBar(title = "Relatório completo", onBack = onBack)
 *     HtmlDocumentView(
 *         source = HtmlDocumentSource.Html(state.html),
 *         modifier = Modifier.weight(1f),
 *         onStateChange = { vm.dispatch(Action.DocumentoMudou(it)) },
 *     )
 *     AppButton(text = "Baixar em PDF", onClick = onBaixar)
 * }
 * ```
 *
 * **Nunca** o coloque dentro de `Modifier.verticalScroll` ou de um `LazyColumn`: dois roláveis no
 * mesmo eixo dão ao filho altura infinita, e o resultado é um documento de uma linha de altura — ou
 * um que simplesmente não rola. É o erro mais comum com componente nativo embutido, e ele não
 * aparece em build nenhum.
 *
 * ### Ciclo de vida
 * O componente nativo é liberado com a tela (`onRelease`): carregamento interrompido, delegates
 * soltos e o `WebView` destruído. Sem isso, sair da tela deixa um `WebView` vivo segurando o
 * documento — que é dado pessoal — em memória.
 *
 * @param source de onde vem o documento.
 * @param allowJavaScript ligue **só** com motivo escrito. Documento de relatório não precisa.
 * @param allowExternalNavigation `true` transforma o componente num mini-navegador (segue links web
 *   por dentro). Para documento, mantenha `false`.
 * @param zoom multiplicador de tamanho. O default segue o tema; passe `1f` para ignorar o ajuste.
 * @param onLinkClick o que fazer com um link para fora. `null` (default) abre no **navegador do
 *   sistema** via `UrlLauncher` — o comportamento certo em praticamente todos os casos. Passe um
 *   callback quando o app precisar tratar deep links próprios.
 * @param onStateChange acompanha carregando/pronto/falhou — use para desabilitar o botão "Baixar
 *   PDF" enquanto carrega, ou para reportar a falha ao GlitchTip.
 * @param texts textos i18n do carregamento e do erro.
 * @param showLoadingIndicator `false` esconde o indicador (a tela já mostra o seu).
 * @param loadingContent substitui o indicador padrão; recebe a fração ou `null` (indeterminado).
 * @param errorContent substitui o estado de erro padrão; recebe o erro e a ação de **recarregar**.
 */
@Composable
fun HtmlDocumentView(
    source: HtmlDocumentSource,
    modifier: Modifier = Modifier,
    allowJavaScript: Boolean = false,
    allowExternalNavigation: Boolean = false,
    zoom: Float = LocalFontScale.current,
    onLinkClick: ((String) -> Unit)? = null,
    onStateChange: (HtmlDocumentState) -> Unit = {},
    texts: HtmlDocumentTexts = HtmlDocumentTexts(),
    showLoadingIndicator: Boolean = true,
    loadingContent: (@Composable (Float?) -> Unit)? = null,
    errorContent: (@Composable (HtmlDocumentError, () -> Unit) -> Unit)? = null,
) {
    var state by remember(source) { mutableStateOf<HtmlDocumentState>(HtmlDocumentState.Loading()) }
    var reloadKey by remember(source) { mutableIntStateOf(0) }

    val currentOnStateChange by rememberUpdatedState(onStateChange)
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)

    Box(modifier = Modifier.fillMaxSize().then(modifier)) {
        if (state !is HtmlDocumentState.Failed) {
            HtmlDocumentWebView(
                source = source,
                allowJavaScript = allowJavaScript,
                allowExternalNavigation = allowExternalNavigation,
                zoom = clampHtmlDocumentZoom(zoom),
                reloadKey = reloadKey,
                onState = { novo ->
                    state = novo
                    currentOnStateChange(novo)
                },
                onDecision = { decision ->
                    when (decision) {
                        is HtmlLinkDecision.OpenExternally -> {
                            val abrir = currentOnLinkClick
                            if (abrir != null) abrir(decision.url) else openInSystemBrowser(decision.url)
                        }

                        is HtmlLinkDecision.Block -> AppLogger.w(
                            "HtmlDocumentView",
                            "Navegação recusada (${decision.reason}) dentro do documento.",
                        )

                        HtmlLinkDecision.Allow -> Unit
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        when (val atual = state) {
            is HtmlDocumentState.Loading ->
                if (showLoadingIndicator) {
                    if (loadingContent != null) {
                        loadingContent(atual.progress)
                    } else {
                        DefaultHtmlDocumentLoading(progress = atual.progress, texts = texts)
                    }
                }

            is HtmlDocumentState.Failed -> {
                val recarregar = {
                    reloadKey += 1
                    state = HtmlDocumentState.Loading()
                }
                if (errorContent != null) {
                    errorContent(atual.error, recarregar)
                } else {
                    ErrorState(
                        message = texts.errorMessage,
                        onRetry = recarregar,
                        title = texts.errorTitle,
                        retryLabel = texts.retry,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            HtmlDocumentState.Ready -> Unit
        }
    }
}

/**
 * Sobrecarga de conveniência para o caso mais comum: o app já buscou o HTML (com o `DomainApiClient`,
 * autenticado) e tem a string em mãos.
 */
@Composable
fun HtmlDocumentView(
    html: String,
    modifier: Modifier = Modifier,
    baseUrl: String? = null,
    allowJavaScript: Boolean = false,
    allowExternalNavigation: Boolean = false,
    zoom: Float = LocalFontScale.current,
    onLinkClick: ((String) -> Unit)? = null,
    onStateChange: (HtmlDocumentState) -> Unit = {},
    texts: HtmlDocumentTexts = HtmlDocumentTexts(),
    showLoadingIndicator: Boolean = true,
    loadingContent: (@Composable (Float?) -> Unit)? = null,
    errorContent: (@Composable (HtmlDocumentError, () -> Unit) -> Unit)? = null,
) {
    val source = remember(html, baseUrl) { HtmlDocumentSource.Html(html = html, baseUrl = baseUrl) }
    HtmlDocumentView(
        source = source,
        modifier = modifier,
        allowJavaScript = allowJavaScript,
        allowExternalNavigation = allowExternalNavigation,
        zoom = zoom,
        onLinkClick = onLinkClick,
        onStateChange = onStateChange,
        texts = texts,
        showLoadingIndicator = showLoadingIndicator,
        loadingContent = loadingContent,
        errorContent = errorContent,
    )
}

/** Indicador padrão. Determinado quando a plataforma informa a fração; indeterminado quando não. */
@Composable
private fun DefaultHtmlDocumentLoading(progress: Float?, texts: HtmlDocumentTexts) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clearAndSetSemantics { contentDescription = texts.loading },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            if (progress == null) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
            } else {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(36.dp),
                )
            }
            Text(
                text = texts.loading,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Abre no navegador do sistema. Isolado para o caminho ser um só, e nunca lançar sobre a UI. */
private fun openInSystemBrowser(url: String) {
    runCatching { getUrlLauncher().openUrl(url) }
        .onFailure { AppLogger.w("HtmlDocumentView", "Falha ao abrir link externo: ${it.message}") }
}

/**
 * Ponte para o componente nativo. `internal` de propósito: quem consome usa o [HtmlDocumentView],
 * que é onde moram os estados, o retry e a política de links — um `WebView` cru exposto viraria o
 * "AndroidView solto no app" que este componente existe para eliminar.
 *
 * @param reloadKey muda a cada pedido de recarga (o "tentar novamente").
 * @param onDecision recebe **toda** decisão de navegação já resolvida por [htmlLinkDecision].
 */
@Composable
internal expect fun HtmlDocumentWebView(
    source: HtmlDocumentSource,
    allowJavaScript: Boolean,
    allowExternalNavigation: Boolean,
    zoom: Float,
    reloadKey: Int,
    onState: (HtmlDocumentState) -> Unit,
    onDecision: (HtmlLinkDecision) -> Unit,
    modifier: Modifier,
)
