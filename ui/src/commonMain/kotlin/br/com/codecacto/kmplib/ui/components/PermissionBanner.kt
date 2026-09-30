package br.com.codecacto.kmplib.ui.components

import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_permission_banner_allow
import br.com.codecacto.kmplib.generated.resources.kmplib_permission_banner_open_settings
import br.com.codecacto.kmplib.platform.permission.AppPermission
import br.com.codecacto.kmplib.platform.permission.PermissionState
import br.com.codecacto.kmplib.platform.permission.PermissionStatus
import br.com.codecacto.kmplib.platform.permission.rememberPermissionState
import org.jetbrains.compose.resources.stringResource

/** O que o [PermissionBanner] oferece para um status. */
enum class PermissionBannerAction {
    /** Pedir a permissão (o diálogo do sistema ainda abre). */
    REQUEST,

    /** Abrir as Configurações do sistema (pedir de novo não abre mais nada). */
    OPEN_SETTINGS,
}

/**
 * Decide se o [PermissionBanner] aparece e com qual ação. `null` = escondido. Pura, testável.
 *
 * - Concedida → escondido.
 * - Negada (ainda pode pedir) → [PermissionBannerAction.REQUEST].
 * - Negada em definitivo → [PermissionBannerAction.OPEN_SETTINGS].
 * - Nunca pedida → escondido, a menos que [showWhenNotRequested]: a primeira pergunta é da tela de
 *   contexto do app (o momento em que a pessoa entende por que o app quer), não de uma faixa.
 */
fun permissionBannerAction(
    status: PermissionStatus,
    showWhenNotRequested: Boolean = false,
): PermissionBannerAction? = when (status) {
    PermissionStatus.GRANTED -> null
    PermissionStatus.DENIED -> PermissionBannerAction.REQUEST
    PermissionStatus.PERMANENTLY_DENIED -> PermissionBannerAction.OPEN_SETTINGS
    PermissionStatus.NOT_REQUESTED -> if (showWhenNotRequested) PermissionBannerAction.REQUEST else null
}

/** Rótulos dos botões do [PermissionBanner]. Default: [rememberPermissionBannerTexts]. */
@Immutable
data class PermissionBannerTexts(
    val allow: String,
    val openSettings: String,
)

/** Rótulos padrão do [PermissionBanner], no idioma da tela. */
@Composable
fun rememberPermissionBannerTexts(): PermissionBannerTexts {
    val allow = stringResource(Res.string.kmplib_permission_banner_allow)
    val open = stringResource(Res.string.kmplib_permission_banner_open_settings)
    return remember(allow, open) { PermissionBannerTexts(allow = allow, openSettings = open) }
}

/**
 * **Faixa de "permissão negada"** com o atalho que resolve — "Permitir" enquanto o sistema ainda
 * pergunta, "Abrir configurações" quando a negação é definitiva.
 *
 * É o [AppBanner] (tom de aviso, suave) ligado ao [PermissionState] da lib, e resolve o que cada
 * app repetia e esquecia metade:
 * - **Some sozinha** quando a permissão volta: reconsulta o status a cada `ON_RESUME` — que é
 *   exatamente a volta das Configurações.
 * - **Abre a tela certa**: para notificação, a tela de notificações do app (Android
 *   `ACTION_APP_NOTIFICATION_SETTINGS`, iOS `openNotificationSettingsURLString`); para as demais,
 *   a página do app nas Configurações.
 * - **Lê o status de verdade**: notificação no iOS só se lê de forma assíncrona, e no Android
 *   considera também o interruptor do app nas Configurações (abaixo da API 33 é a única trava).
 * - **Não pede nada ao aparecer**: a faixa é para quem já recusou. Nunca pedida = escondida (ver
 *   [permissionBannerAction]).
 *
 * ```kotlin
 * PermissionBanner(
 *     permission = AppPermission.NOTIFICATIONS,
 *     message = "Ative as notificações para receber seus alertas de preço.",
 * )
 * ```
 *
 * A mensagem é do app — só ele sabe o que deixa de acontecer sem a permissão. Tela MVI que já tem o
 * status no estado usa a sobrecarga sem [PermissionState].
 *
 * @param permission a permissão acompanhada.
 * @param message o que a pessoa perde sem ela (obrigatório).
 * @param title título curto opcional.
 * @param showWhenNotRequested mostra também antes do primeiro pedido (com "Permitir").
 * @param onDismiss quando não-nulo, mostra o "×" (guardar a dispensa é do app).
 * @param state estado da permissão; por default um novo, que **não** pede ao aparecer.
 */
@Composable
fun PermissionBanner(
    permission: AppPermission,
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    showWhenNotRequested: Boolean = false,
    tone: StatusTone = StatusTone.WARNING,
    onDismiss: (() -> Unit)? = null,
    texts: PermissionBannerTexts = rememberPermissionBannerTexts(),
    state: PermissionState = rememberPermissionState(permission, requestOnFirstAppearance = false),
) {
    // Volta das Configurações (ou do 2º plano) = reconsulta. É o que faz a faixa sumir sozinha.
    LifecycleResumeEffect(state) {
        state.refresh()
        onPauseOrDispose { }
    }
    PermissionBanner(
        status = state.status,
        message = message,
        onRequest = state::request,
        onOpenSettings = state::openSettings,
        modifier = modifier,
        title = title,
        showWhenNotRequested = showWhenNotRequested,
        tone = tone,
        onDismiss = onDismiss,
        texts = texts,
    )
}

/**
 * Versão **sem estado** do [PermissionBanner], para tela que guarda o status no próprio ViewModel.
 * Escondida quando [permissionBannerAction] devolve `null`.
 *
 * @param onRequest pedir a permissão (ex.: `PermissionState.request`).
 * @param onOpenSettings abrir as Configurações (ex.: `PermissionState.openSettings`).
 */
@Composable
fun PermissionBanner(
    status: PermissionStatus,
    message: String,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    showWhenNotRequested: Boolean = false,
    tone: StatusTone = StatusTone.WARNING,
    onDismiss: (() -> Unit)? = null,
    texts: PermissionBannerTexts = rememberPermissionBannerTexts(),
) {
    val action = permissionBannerAction(status, showWhenNotRequested) ?: return
    AppBanner(
        message = message,
        modifier = modifier,
        tone = tone,
        title = title,
        onDismiss = onDismiss,
        action = {
            when (action) {
                PermissionBannerAction.REQUEST -> TextButton(onClick = onRequest) { Text(texts.allow) }
                PermissionBannerAction.OPEN_SETTINGS -> TextButton(onClick = onOpenSettings) { Text(texts.openSettings) }
            }
        },
    )
}
