package br.com.codecacto.kmplib.push.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import br.com.codecacto.kmplib.announcement.AnnouncementClient
import br.com.codecacto.kmplib.announcement.AnnouncementService
import br.com.codecacto.kmplib.platform.getUrlLauncher
import br.com.codecacto.kmplib.push.PushEvents
import br.com.codecacto.kmplib.push.PushNotificationListener
import br.com.codecacto.kmplib.push.action.PushTapRouter
import br.com.codecacto.kmplib.push.action.findAnnouncementById
import br.com.codecacto.kmplib.ui.announcement.Announcement
import br.com.codecacto.kmplib.ui.announcement.AnnouncementAction
import br.com.codecacto.kmplib.ui.announcement.AnnouncementDialog
import br.com.codecacto.kmplib.ui.announcement.openAnnouncementAction
import kotlinx.coroutines.channels.Channel

/**
 * **Roteador do toque das notificações do Nexus** (2.360.0) — uma linha na raiz do app, dentro do
 * tema e da navegação:
 *
 * ```kotlin
 * PushTapHost(
 *     onOpenPremium = { navController.navigate(PremiumRoute) },
 *     enabled = pilhaPronta,          // depois da Splash/login
 * )
 * ```
 *
 * Lê `cc_acao` do toque: `app` → só abre · `premium` → [onOpenPremium] · `comunicado` → abre o
 * comunicado `cc_comunicado` no MESMO modal dos Comunicados (`AnnouncementDialog`, buscado pelo
 * [findAnnouncementById]) · `link` → `cc_url` no navegador externo (só `http(s)`; em app infantil passa
 * pelo portão de pais do `getUrlLauncher`).
 *
 * Inclusive o toque que **abriu o app a frio**: enquanto [enabled] for `false` (ou antes de o host
 * existir), o toque fica pendente em [PushEvents] e é entregue quando ele liga. Toque de push que não
 * é de campanha vai para [onOtherTap] (o deep link próprio do app). No logout, `PushEvents.clearPendingTap()`.
 *
 * @param announcementClient de onde buscar o comunicado; default o do `CentralServices`.
 * @param openUrl abre o link (default: `getUrlLauncher().openUrl`).
 */
@Composable
fun PushTapHost(
    onOpenPremium: () -> Unit,
    enabled: Boolean = true,
    onOtherTap: (Map<String, String>) -> Unit = {},
    announcementClient: AnnouncementClient? = AnnouncementService.client,
    openUrl: (String) -> Unit = { getUrlLauncher().openUrl(it) },
    onAnnouncementAction: (AnnouncementAction) -> Unit = ::openAnnouncementAction,
) {
    val premium by rememberUpdatedState(onOpenPremium)
    val other by rememberUpdatedState(onOtherTap)
    val open by rememberUpdatedState(openUrl)
    var shown by remember { mutableStateOf<Announcement?>(null) }

    LaunchedEffect(enabled, announcementClient) {
        if (!enabled) return@LaunchedEffect
        val taps = Channel<Map<String, String>>(Channel.UNLIMITED)
        val listener = object : PushNotificationListener {
            override fun onNotificationClicked(data: Map<String, String>) {
                taps.trySend(data)
            }
        }
        val router = PushTapRouter(
            onOpenPremium = { premium() },
            openUrl = { open(it) },
            findAnnouncement = { id -> findAnnouncementById(announcementClient, id) },
            showAnnouncement = { shown = it },
            onOtherTap = { other(it) },
        )
        val registration = PushEvents.addListener(listener, receivePendingTap = true)
        try {
            for (data in taps) router.route(data)
        } finally {
            registration.remove()
            taps.close()
        }
    }

    shown?.let { announcement ->
        AnnouncementDialog(
            announcement = announcement,
            onDismiss = { shown = null },
            onAction = onAnnouncementAction,
        )
    }
}
