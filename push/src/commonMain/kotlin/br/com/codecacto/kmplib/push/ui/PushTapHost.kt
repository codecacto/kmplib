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
 * existir), o toque fica pendente em [PushEvents] e é entregue quando ele liga. No logout,
 * `PushEvents.clearPendingTap()`.
 *
 * **Quem é dono de qual toque (2.363.0).** O host recebe SEMPRE o toque de campanha (`cc_acao`) — e só
 * ele: o ouvinte do push operacional do app (`createPushNotificationService(listener)`, `addListener`
 * com o padrão) não recebe mais campanha, então não "rouba" o toque a frio. O toque que NÃO é de campanha
 * só vem ao host quando [onOtherTap] é passado; sem ele (`null`), fica para o ouvinte do próprio app.
 * Escolha UM dono para o toque operacional: ou [onOtherTap], ou o ouvinte do serviço do app
 * (`receiveTaps = true`) — os dois juntos entregam o mesmo toque duas vezes.
 *
 * ⚠️ **Android: sem `PushActivityHooks` na Activity o toque nunca chega aqui.** O KMPNotifier só dispara
 * o toque pelo `NotifierManager.onCreateOrOnNewIntent(intent)`; chame
 * `PushActivityHooks.onCreate(this, savedInstanceState)` no `onCreate` e
 * `PushActivityHooks.onNewIntent(this, intent)` no `onNewIntent` da MainActivity (que deve ser
 * `singleTop`/`singleTask`). Sem isso a notificação aparece e o toque só abre o app.
 *
 * @param onOtherTap toque que não é de campanha (deep link próprio do app); `null` (padrão) = o host
 *   não o recebe e ele segue para o ouvinte do serviço de push do app (ou fica pendente).
 * @param announcementClient de onde buscar o comunicado; default o do `CentralServices`.
 * @param openUrl abre o link (default: `getUrlLauncher().openUrl`).
 */
@Composable
fun PushTapHost(
    onOpenPremium: () -> Unit,
    enabled: Boolean = true,
    onOtherTap: ((Map<String, String>) -> Unit)? = null,
    announcementClient: AnnouncementClient? = AnnouncementService.client,
    openUrl: (String) -> Unit = { getUrlLauncher().openUrl(it) },
    onAnnouncementAction: (AnnouncementAction) -> Unit = ::openAnnouncementAction,
) {
    val premium by rememberUpdatedState(onOpenPremium)
    val other by rememberUpdatedState(onOtherTap)
    val open by rememberUpdatedState(openUrl)
    var shown by remember { mutableStateOf<Announcement?>(null) }

    val wantsOtherTaps = onOtherTap != null

    LaunchedEffect(enabled, announcementClient, wantsOtherTaps) {
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
            onOtherTap = { data -> other?.invoke(data) },
        )
        val registration = PushEvents.addListener(
            listener,
            receivePendingTap = true,
            receiveTaps = wantsOtherTaps,
            receiveCampaignTaps = true,
        )
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
