package br.com.codecacto.kmplib.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.codecacto.kmplib.core.network.ConnectivityObserver

/**
 * Textos do aviso de conectividade — **i18n-ready** (defaults pt-BR).
 *
 * O app pode injetar traduções (Compose Resources `stringResource(...)`) montando um
 * [ConnectivityTexts] próprio. Todos os textos têm default em português.
 */
data class ConnectivityTexts(
    val modalTitle: String = "Sem conexão com a internet",
    val modalMessage: String = "Parece que você está offline. Verifique sua conexão e tente novamente.",
    val retryButton: String = "Tentar novamente",
    val bannerText: String = "Sem conexão com a internet",
    val screenTitle: String = "Sem conexão com a internet",
    val screenMessage: String =
        "Não conseguimos carregar o conteúdo agora. Confira o Wi-Fi ou os dados móveis e tente novamente.",
    val checkingButton: String = "Verificando conexão…",
)

/**
 * Estilo de exibição do aviso de conectividade.
 *
 * - [Modal] — **bloqueante** (default): sobrepõe um diálogo não-dispensável enquanto offline.
 * - [Banner] — **não-bloqueante** (para app offline-first que só quer avisar): faixa fina
 *   no topo do conteúdo, o usuário continua usando o app normalmente.
 * - [FullScreen] — **bloqueante, tela inteira** (2.200.0; o recomendado para app online-por-padrão):
 *   a [NoInternetScreen] cobre o app todo. O conteúdo **continua composto por baixo** — a
 *   navegação, a rolagem e o formulário meio preenchido sobrevivem, e quando a conexão volta a
 *   pessoa está exatamente onde parou. Trocar o conteúdo pela tela (em vez de sobrepor) desmontaria
 *   o `NavHost` e a mandaria de volta ao início.
 */
enum class ConnectivityStyle { Modal, Banner, FullScreen }

/** Quanto tempo o botão fica em "Verificando conexão…" depois do toque. */
internal const val NO_INTERNET_CHECK_FEEDBACK_MS = 1_200L

/**
 * Modal **bloqueante** de "sem conexão com a internet" (stateless).
 *
 * Diálogo não-dispensável (sem fechar por toque fora / botão voltar), com ícone, título,
 * mensagem amigável e botão **"Tentar novamente"**. Tema 100% via tokens do [MaterialTheme]
 * (nada hardcoded), acessível e i18n via [texts]. Normalmente não é usado direto — prefira
 * [ConnectivityGate], que o mostra/esconde automaticamente conforme a conectividade.
 *
 * @param onRetry ação do botão "Tentar novamente".
 * @param modifier modificador do cartão.
 * @param texts textos (i18n; defaults pt-BR).
 * @param icon ícone do topo (default `WifiOff`).
 */
@Composable
fun NoInternetModal(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    texts: ConnectivityTexts = ConnectivityTexts(),
    icon: ImageVector = Icons.Filled.WifiOff,
) {
    Dialog(
        onDismissRequest = { /* bloqueante: não dispensa até a conexão voltar */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = modifier,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxSize(),
                    ) {}
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(40.dp),
                    )
                }

                Text(
                    text = texts.modalTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = texts.modalMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                AppButton(
                    text = texts.retryButton,
                    onClick = onRetry,
                    primaryColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/**
 * **Tela inteira** de "sem conexão com a internet" (stateless quanto à rede).
 *
 * Ilustração (o ícone dentro de três círculos concêntricos na cor primária do tema), título,
 * mensagem e botão **"Tentar novamente"** de largura cheia (teto de 320dp). Depois do toque o botão
 * mostra **"Verificando conexão…"** por [NO_INTERNET_CHECK_FEEDBACK_MS]: sem esse retorno, tocar
 * com a rede ainda fora não muda nada na tela, e parece que o botão não funciona.
 *
 * Respeita as barras do sistema (`safeDrawing`) e **rola** em tela pequena ou com fonte grande.
 * Tema 100% via tokens do [MaterialTheme]. Normalmente não é usada direto — prefira
 * [ConnectivityGate] com [ConnectivityStyle.FullScreen], que a sobrepõe e retira sozinho.
 *
 * @param onRetry ação do botão "Tentar novamente".
 * @param modifier modificador do fundo da tela.
 * @param texts textos (i18n; defaults pt-BR) — usa [ConnectivityTexts.screenTitle],
 *   [ConnectivityTexts.screenMessage], [ConnectivityTexts.retryButton] e
 *   [ConnectivityTexts.checkingButton].
 * @param icon ícone da ilustração (default `WifiOff`).
 */
@Composable
fun NoInternetScreen(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    texts: ConnectivityTexts = ConnectivityTexts(),
    icon: ImageVector = Icons.Filled.WifiOff,
) {
    val colors = MaterialTheme.colorScheme
    var tentativas by remember { mutableIntStateOf(0) }
    var verificando by remember { mutableStateOf(false) }

    LaunchedEffect(tentativas) {
        if (tentativas == 0) return@LaunchedEffect
        verificando = true
        delay(NO_INTERNET_CHECK_FEEDBACK_MS)
        verificando = false
    }

    Surface(color = colors.background, modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IlustracaoSemConexao(icon = icon)

                Spacer(Modifier.height(32.dp))
                Text(
                    text = texts.screenTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = texts.screenMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                AppButton(
                    text = if (verificando) texts.checkingButton else texts.retryButton,
                    onClick = {
                        onRetry()
                        tentativas++
                    },
                    isLoading = verificando,
                    icon = Icons.Filled.Refresh,
                    primaryColor = colors.primary,
                    contentColor = colors.onPrimary,
                    modifier = Modifier.widthIn(max = 320.dp),
                )
            }
        }
    }
}

@Composable
private fun IlustracaoSemConexao(icon: ImageVector) {
    val primaria = MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(176.dp)) {
        Box(Modifier.size(176.dp).clip(CircleShape).background(primaria.copy(alpha = 0.06f)))
        Box(Modifier.size(128.dp).clip(CircleShape).background(primaria.copy(alpha = 0.10f)))
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

/**
 * O voltar do sistema enquanto a tela cheia está aberta: **não faz nada**. Sem isto o gesto
 * desempilharia a navegação escondida por baixo — a pessoa voltaria a um lugar que não vê.
 *
 * Isolado aqui para o `@Suppress("DEPRECATION")` não calar outra depreciação: o `BackHandler`
 * multiplataforma está marcado em favor do `NavigationEventHandler`, feito para o *predictive back*
 * com progresso, e o `navigationevent-compose` não publica variante Kotlin/Native.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION")
@Composable
private fun SeguraVoltarDoSistema() {
    BackHandler(enabled = true, onBack = { })
}

/**
 * Observa a conectividade e devolve o estado como [State] reativo, cuidando do ciclo
 * `start()`/`stop()` do [observer]. Útil para **lógica** (habilitar/desabilitar ações,
 * decidir refetch) sem depender da UI do [ConnectivityGate].
 *
 * Seguro com um observer **compartilhado** (Koin): `start()`/`stop()` são contados por
 * referência, então sair de composição não derruba o observer usado pelo auto-sync.
 *
 * ```kotlin
 * val online by rememberIsOnline(observer)
 * Button(enabled = online) { ... }
 * ```
 */
@Composable
fun rememberIsOnline(observer: ConnectivityObserver): State<Boolean> {
    LaunchedEffect(observer) { observer.start() }
    DisposableEffect(observer) { onDispose { observer.stop() } }
    return observer.isOnline.collectAsState()
}

/**
 * Portão de conectividade — **plugue global de 1 linha** para todo app avisar quando está
 * sem internet.
 *
 * Observa a conectividade (via [ConnectivityObserver]) e, enquanto offline, mostra
 * automaticamente o aviso — **[ConnectivityStyle.Modal]** (bloqueante, default) ou
 * **[ConnectivityStyle.Banner]** (não-bloqueante). O aviso **some sozinho** quando a
 * conexão volta (estado reativo). O botão "Tentar novamente" reavalia a rede na hora
 * (`observer.refresh()`) e dispara o [onRetry] opcional (ex.: refazer a carga pendente).
 *
 * Envolva o conteúdo do app no root (dentro do `AppTheme`):
 * ```kotlin
 * AppTheme(...) {
 *     ConnectivityGate {           // gerencia seu próprio observer
 *         AppNavHost()
 *     }
 * }
 * ```
 *
 * Este overload cria e gerencia o próprio [ConnectivityObserver] — não exige DI.
 *
 * @param modifier modificador do container.
 * @param style [ConnectivityStyle.Modal] (default) ou [ConnectivityStyle.Banner].
 * @param texts textos (i18n; defaults pt-BR).
 * @param onRetry ação extra ao tocar "Tentar novamente" (além de reavaliar a rede).
 * @param onOnlineChange callback quando o estado online muda (para lógica/telemetria).
 * @param content conteúdo do app.
 */
@Composable
fun ConnectivityGate(
    modifier: Modifier = Modifier,
    style: ConnectivityStyle = ConnectivityStyle.Modal,
    texts: ConnectivityTexts = ConnectivityTexts(),
    onRetry: (() -> Unit)? = null,
    onOnlineChange: ((Boolean) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val observer = remember { ConnectivityObserver() }
    ConnectivityGate(
        observer = observer,
        modifier = modifier,
        style = style,
        texts = texts,
        onRetry = onRetry,
        onOnlineChange = onOnlineChange,
        content = content,
    )
}

/**
 * Overload do [ConnectivityGate] com [ConnectivityObserver] **explícito** — **este é o overload
 * preferido** quando o app já tem um observer no Koin (compartilhado com `rememberIsOnline`, com o
 * auto-sync do módulo `sync`/`sync.rest`, etc.).
 *
 * É seguro passar o **mesmo** observer usado pelo `RestCrudSyncEngine`: desde a 2.69.0 o
 * `start()`/`stop()` do [ConnectivityObserver] é **contado por referência** — este gate registra
 * um consumidor ao entrar em composição e o libera no `onDispose`, sem registrar um segundo
 * `NetworkCallback` nem desligar o observer dos outros consumidores.
 */
@Composable
fun ConnectivityGate(
    observer: ConnectivityObserver,
    modifier: Modifier = Modifier,
    style: ConnectivityStyle = ConnectivityStyle.Modal,
    texts: ConnectivityTexts = ConnectivityTexts(),
    onRetry: (() -> Unit)? = null,
    onOnlineChange: ((Boolean) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(observer) { observer.start() }
    DisposableEffect(observer) { onDispose { observer.stop() } }

    val isOnline by observer.isOnline.collectAsState()

    LaunchedEffect(isOnline) { onOnlineChange?.invoke(isOnline) }

    val retry: () -> Unit = {
        observer.refresh()
        onRetry?.invoke()
    }

    when (style) {
        ConnectivityStyle.Modal -> {
            Box(modifier.fillMaxSize()) {
                content()
                if (!isOnline) {
                    NoInternetModal(onRetry = retry, texts = texts)
                }
            }
        }

        ConnectivityStyle.Banner -> {
            Column(modifier.fillMaxSize()) {
                OfflineBanner(
                    isOnline = isOnline,
                    text = texts.bannerText,
                    modifier = Modifier.fillMaxWidth(),
                )
                content()
            }
        }

        ConnectivityStyle.FullScreen -> {
            val teclado = LocalSoftwareKeyboardController.current
            val foco = LocalFocusManager.current
            LaunchedEffect(isOnline) {
                // O teclado aberto ficaria por cima da tela de aviso, cobrindo o botão.
                if (!isOnline) {
                    foco.clearFocus()
                    teclado?.hide()
                }
            }
            Box(modifier.fillMaxSize()) {
                // O MESMO nó nos dois estados — só o modifier muda —, então o conteúdo não é
                // remontado. Offline, a árvore de acessibilidade dele some: sem isso o leitor de
                // tela continuaria navegando pelos botões escondidos atrás do aviso.
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(if (isOnline) Modifier else Modifier.clearAndSetSemantics { }),
                ) {
                    content()
                }
                AnimatedVisibility(visible = !isOnline, enter = fadeIn(), exit = fadeOut()) {
                    // `Surface` do M3 consome o toque: nada atravessa para o app por baixo.
                    NoInternetScreen(onRetry = retry, texts = texts)
                    SeguraVoltarDoSistema()
                }
            }
        }
    }
}
