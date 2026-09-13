package br.com.codecacto.kmplib.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Textos da tela de manutenção — **i18n-ready** (defaults pt-BR).
 *
 * [message] é o texto padrão; a mensagem enviada pelo servidor, quando não é branca, vence
 * (ver [resolveMaintenanceMessage]).
 */
data class MaintenanceTexts(
    val title: String = "Estamos em manutenção",
    val message: String = "Estamos fazendo melhorias no aplicativo. Volte daqui a pouco.",
    val retryButton: String = "Tentar novamente",
    val checkingButton: String = "Verificando…",
)

/**
 * A mensagem que a tela de manutenção mostra: a do servidor ([message]) quando existe e não é
 * branca (aparada), senão [MaintenanceTexts.message]. Uma mensagem vazia vinda do painel não pode
 * deixar a tela sem explicação.
 */
fun resolveMaintenanceMessage(message: String?, texts: MaintenanceTexts): String =
    message?.trim()?.takeIf { it.isNotEmpty() } ?: texts.message

/**
 * **Tela inteira** de "estamos em manutenção" (stateless).
 *
 * Mesma casca visual da [NoInternetScreen]: ilustração, título, mensagem e botão que mostra
 * [MaintenanceTexts.checkingButton] por um instante após o toque. Normalmente não é usada direto —
 * prefira [MaintenanceGate], que a sobrepõe ao app sem desmontar a navegação.
 *
 * @param message mensagem do servidor; nula ou branca usa [MaintenanceTexts.message].
 * @param onRetry ação do botão (ex.: reconsultar o config). `null` = **sem botão**.
 * @param modifier modificador do fundo da tela.
 * @param texts textos (i18n; defaults pt-BR).
 * @param icon ícone da ilustração (default `Build`).
 */
@Composable
fun MaintenanceScreen(
    message: String?,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    texts: MaintenanceTexts = MaintenanceTexts(),
    icon: ImageVector = Icons.Filled.Build,
) {
    FullScreenNotice(
        title = texts.title,
        message = resolveMaintenanceMessage(message, texts),
        icon = icon,
        retryLabel = texts.retryButton,
        checkingLabel = texts.checkingButton,
        onRetry = onRetry,
        modifier = modifier,
    )
}

/**
 * Portão de **modo manutenção**: enquanto [active], a [MaintenanceScreen] cobre o app inteiro.
 *
 * Mesma mecânica do [ConnectivityGate] em [ConnectivityStyle.FullScreen]: o [content] **continua
 * composto por baixo** (quando a manutenção acaba, a pessoa está onde parou), a semântica de
 * acessibilidade dele some, o teclado fecha, o voltar do sistema é bloqueado e a tela entra e sai
 * com fade. **Quem decide [active] é o app** — tipicamente o `maintenance.enabled` do config remoto
 * ou uma resposta `503` com `code: "MAINTENANCE"`; a lib não consulta nada.
 *
 * ```kotlin
 * MaintenanceGate(
 *     active = state.maintenance.enabled,
 *     message = state.maintenance.message,
 *     onRetry = { viewModel.onAction(Action.RecarregarConfig) },
 * ) { AppNavHost() }
 * ```
 *
 * @param active se a manutenção está ligada.
 * @param message mensagem do servidor; nula ou branca usa [MaintenanceTexts.message].
 * @param onRetry ação do botão; `null` = sem botão.
 * @param modifier modificador do container.
 * @param texts textos (i18n; defaults pt-BR).
 * @param content conteúdo do app.
 */
@Composable
fun MaintenanceGate(
    active: Boolean,
    message: String?,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    texts: MaintenanceTexts = MaintenanceTexts(),
    content: @Composable () -> Unit,
) {
    BlockingOverlay(
        active = active,
        modifier = modifier,
        overlay = { MaintenanceScreen(message = message, onRetry = onRetry, texts = texts) },
        content = content,
    )
}
