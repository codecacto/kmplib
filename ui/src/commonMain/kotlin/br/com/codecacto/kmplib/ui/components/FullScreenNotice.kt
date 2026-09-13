package br.com.codecacto.kmplib.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Casca comum das telas cheias de aviso bloqueante ([NoInternetScreen], [MaintenanceScreen]).
 *
 * Ilustração (o ícone dentro de três círculos concêntricos na cor primária do tema), título,
 * mensagem e — só quando há [onRetry] — botão de largura cheia (teto de 320dp) que mostra
 * [checkingLabel] por [NO_INTERNET_CHECK_FEEDBACK_MS] depois do toque: sem esse retorno, tocar com
 * a causa ainda de pé não muda nada na tela, e parece que o botão não funciona.
 *
 * Respeita as barras do sistema (`safeDrawing`) e **rola** em tela pequena ou com fonte grande.
 */
@Composable
internal fun FullScreenNotice(
    title: String,
    message: String,
    icon: ImageVector,
    retryLabel: String,
    checkingLabel: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
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
                NoticeIllustration(icon = icon)

                Spacer(Modifier.height(32.dp))
                Text(
                    text = title,
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
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (onRetry != null) {
                    Spacer(Modifier.height(32.dp))
                    AppButton(
                        text = if (verificando) checkingLabel else retryLabel,
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
}

@Composable
private fun NoticeIllustration(icon: ImageVector) {
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
 * Sobreposição **bloqueante** de tela cheia sobre o [content] — a mecânica comum do
 * [ConnectivityGate] em [ConnectivityStyle.FullScreen] e do [MaintenanceGate].
 *
 * - O conteúdo é composto **no mesmo nó** nos dois estados (só o modifier muda): navegação, rolagem
 *   e formulário meio preenchido sobrevivem. Trocar o conteúdo pelo aviso desmontaria o `NavHost`.
 * - Ativo, a árvore de acessibilidade do conteúdo some (`clearAndSetSemantics`) — senão o leitor de
 *   tela continuaria navegando pelos botões escondidos atrás do aviso.
 * - Ao ativar, fecha o teclado (ele ficaria por cima do aviso, cobrindo o botão).
 * - Enquanto [active], o voltar do sistema não faz nada — e só enquanto [active]: durante o fade-out
 *   o gate já liberou, e o voltar volta a funcionar.
 * - Entra e sai com fade. O `Surface` do M3 do aviso consome o toque: nada atravessa.
 */
@Composable
internal fun BlockingOverlay(
    active: Boolean,
    modifier: Modifier = Modifier,
    overlay: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val teclado = LocalSoftwareKeyboardController.current
    val foco = LocalFocusManager.current
    LaunchedEffect(active) {
        if (active) {
            foco.clearFocus()
            teclado?.hide()
        }
    }
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (active) Modifier.clearAndSetSemantics { } else Modifier),
        ) {
            content()
        }
        AnimatedVisibility(visible = active, enter = fadeIn(), exit = fadeOut()) {
            overlay()
        }
        // FORA da animação, e ligado por [active] — não pela presença do aviso na tela (2.203.0).
        // Dentro do `AnimatedVisibility` ele continuava composto durante o fade-out inteiro: a rede
        // voltava, o gate já estava liberado, e o primeiro "voltar" da pessoa era engolido sem fazer
        // nada. O bloqueio acompanha a regra, não o desenho.
        SeguraVoltarDoSistema(enabled = active)
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
private fun SeguraVoltarDoSistema(enabled: Boolean) {
    BackHandler(enabled = enabled, onBack = { })
}
