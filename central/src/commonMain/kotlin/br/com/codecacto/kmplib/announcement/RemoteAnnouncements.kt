package br.com.codecacto.kmplib.announcement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import br.com.codecacto.kmplib.core.locale.appLanguageTag
import br.com.codecacto.kmplib.core.util.currentPlatform
import br.com.codecacto.kmplib.ui.announcement.Announcement
import br.com.codecacto.kmplib.ui.announcement.AnnouncementAction
import br.com.codecacto.kmplib.ui.announcement.AnnouncementHost
import br.com.codecacto.kmplib.ui.announcement.AnnouncementStore
import br.com.codecacto.kmplib.ui.announcement.openAnnouncementAction
import br.com.codecacto.kmplib.ui.announcement.rememberDefaultAnnouncementStore
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.launch

/** Intervalo mínimo default entre duas leituras do servidor (o apps-api responde com 60 s de cache). */
val DefaultAnnouncementRefreshInterval: Duration = 5.minutes

/**
 * Os avisos do **Nexus → Avisos** para esta tela: busca ao entrar e a cada volta ao primeiro plano
 * (`ON_RESUME`), no máximo uma vez a cada [minRefreshInterval] — voltar do segundo plano dez vezes
 * em um minuto não faz dez requisições. A lista é a do processo ([AnnouncementClient.latest]): outra
 * tela que já buscou entrega o aviso no primeiro quadro; uma falha (sem rede) mantém o que já havia.
 *
 * @param client o cliente; default o do [AnnouncementService] (que o `CentralServices` inicializa).
 *   `null` (serviço não inicializado) → lista vazia, sem requisição.
 * @param platform `"android"`/`"ios"` — default, o alvo em que o app roda.
 * @param locale tag BCP 47 — default, o idioma da TELA ([appLanguageTag]).
 */
@Composable
fun rememberRemoteAnnouncements(
    client: AnnouncementClient? = AnnouncementService.client,
    minRefreshInterval: Duration = DefaultAnnouncementRefreshInterval,
    platform: String = currentPlatform,
    locale: String = appLanguageTag(),
): State<List<Announcement>> {
    if (client == null) return remember { mutableStateOf(emptyList()) }
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(client, minRefreshInterval, platform, locale) {
        val job = scope.launch { client.refresh(minRefreshInterval, platform, locale) }
        onPauseOrDispose { job.cancel() }
    }
    return client.latest.collectAsState()
}

/**
 * O aviso do app numa linha: os avisos do servidor central ([rememberRemoteAnnouncements]) somados aos
 * [local] do app ([mergeAnnouncements] — mesmo `id`, vale o remoto) e mostrados pelo
 * [AnnouncementHost] (prioridade, janela, frequência, visto/dispensado, animação). Cada um aparece pela
 * sua `presentation` (2.296.0): `MODAL` (default) em diálogo centralizado com fila e teto de 3 por
 * sessão; `BANNER` no lugar do host. Sem aviso pendente não ocupa espaço.
 *
 * @param dialogReady segura o modal até o app estar pronto (depois da Splash/login).
 * @param dialogSuppressed segura o modal durante paywall, compra ou câmera (a manutenção que bloqueia
 *   abre mesmo assim).
 *
 * ```kotlin
 * RemoteAnnouncementHost(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
 * ```
 *
 * Exige o [AnnouncementService] inicializado — o `CentralServices.initialize(...)` do bootstrap já faz.
 */
@Composable
fun RemoteAnnouncementHost(
    local: List<Announcement> = emptyList(),
    modifier: Modifier = Modifier,
    client: AnnouncementClient? = AnnouncementService.client,
    minRefreshInterval: Duration = DefaultAnnouncementRefreshInterval,
    store: AnnouncementStore = rememberDefaultAnnouncementStore(),
    clock: Clock = Clock.System,
    onAction: (Announcement, AnnouncementAction) -> Unit = { _, action -> openAnnouncementAction(action) },
    extraContent: (@Composable (Announcement) -> Unit)? = null,
    dialogReady: Boolean = true,
    dialogSuppressed: Boolean = false,
) {
    val remote = rememberRemoteAnnouncements(client = client, minRefreshInterval = minRefreshInterval).value
    val merged = remember(local, remote) { mergeAnnouncements(local, remote) }
    AnnouncementHost(
        announcements = merged,
        modifier = modifier,
        store = store,
        clock = clock,
        onAction = onAction,
        extraContent = extraContent,
        dialogReady = dialogReady,
        dialogSuppressed = dialogSuppressed,
    )
}
