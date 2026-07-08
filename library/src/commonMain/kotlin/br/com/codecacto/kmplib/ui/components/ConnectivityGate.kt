package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
)

/**
 * Estilo de exibição do aviso de conectividade.
 *
 * - [Modal] — **bloqueante** (default para app online-por-padrão): sobrepõe um diálogo
 *   não-dispensável enquanto offline, forçando o usuário a recuperar a conexão.
 * - [Banner] — **não-bloqueante** (para app offline-first que só quer avisar): faixa fina
 *   no topo do conteúdo, o usuário continua usando o app normalmente.
 */
enum class ConnectivityStyle { Modal, Banner }

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
 * Observa a conectividade e devolve o estado como [State] reativo, cuidando do ciclo
 * `start()`/`stop()` do [observer]. Útil para **lógica** (habilitar/desabilitar ações,
 * decidir refetch) sem depender da UI do [ConnectivityGate].
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
 * Overload do [ConnectivityGate] com [ConnectivityObserver] **explícito** — use quando o
 * observer é injetado (Koin) e compartilhado com a lógica do app (`rememberIsOnline`,
 * auto-sync do módulo `sync`, etc.).
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
    }
}
