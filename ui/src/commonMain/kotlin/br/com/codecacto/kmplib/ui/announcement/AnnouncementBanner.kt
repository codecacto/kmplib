@file:OptIn(ExperimentalTime::class)

package br.com.codecacto.kmplib.ui.announcement

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import br.com.codecacto.kmplib.core.locale.RegionalFormat
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_announcement_dismiss
import br.com.codecacto.kmplib.generated.resources.kmplib_announcement_kind_cross_platform
import br.com.codecacto.kmplib.generated.resources.kmplib_announcement_kind_info
import br.com.codecacto.kmplib.generated.resources.kmplib_announcement_kind_maintenance
import br.com.codecacto.kmplib.generated.resources.kmplib_announcement_kind_news
import br.com.codecacto.kmplib.generated.resources.kmplib_announcement_kind_update
import br.com.codecacto.kmplib.generated.resources.kmplib_announcement_window_ongoing
import br.com.codecacto.kmplib.generated.resources.kmplib_announcement_window_range
import br.com.codecacto.kmplib.generated.resources.kmplib_announcement_window_same_day
import br.com.codecacto.kmplib.platform.getUrlLauncher
import br.com.codecacto.kmplib.platform.motion.LocalReduceMotion
import br.com.codecacto.kmplib.ui.locale.formatResourceArgs
import br.com.codecacto.kmplib.ui.locale.kmpStringResource
import br.com.codecacto.kmplib.ui.theme.AppColors
import br.com.codecacto.kmplib.ui.theme.ColorContrast
import br.com.codecacto.kmplib.ui.theme.LocalIsCompact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Ids estáveis para o Maestro (`testTagsAsResourceId` na raiz do app). */
object AnnouncementTestTags {
    /** O cartão inteiro. */
    const val ROOT: String = "aviso"

    /** O título. */
    const val TITLE: String = "aviso-titulo"

    /** A linha da janela do evento (manutenção). */
    const val WINDOW: String = "aviso-janela"

    /** O botão de ação. */
    const val ACTION: String = "aviso-btn-acao"

    /** O X de dispensar. */
    const val DISMISS: String = "aviso-btn-dispensar"
}

/**
 * Textos internos do cartão (4 idiomas pelos recursos da lib; [rememberAnnouncementTexts] é o
 * default). Os modelos de janela usam `%1$s`, `%2$s`, `%3$s`.
 *
 * @property windowSameDay "%1$s, das %2$s às %3$s" — dia, hora de início, hora de fim.
 * @property windowRange "De %1$s até %2$s" — data e hora nas duas pontas.
 * @property windowOngoing "Em andamento — até %1$s".
 */
@Immutable
data class AnnouncementTexts(
    val dismiss: String,
    val maintenance: String,
    val update: String,
    val news: String,
    val crossPlatform: String,
    val info: String,
    val windowSameDay: String,
    val windowRange: String,
    val windowOngoing: String,
) {
    /** O rótulo do tipo (a linha pequena acima do título). */
    fun kindLabel(kind: AnnouncementKind): String = when (kind) {
        AnnouncementKind.MAINTENANCE -> maintenance
        AnnouncementKind.UPDATE -> update
        AnnouncementKind.NEWS -> news
        AnnouncementKind.CROSS_PLATFORM -> crossPlatform
        AnnouncementKind.INFO -> info
    }
}

/** Os textos do cartão no idioma da tela. Para trocar uma frase: `rememberAnnouncementTexts().copy(…)`. */
@Composable
fun rememberAnnouncementTexts(): AnnouncementTexts = AnnouncementTexts(
    dismiss = kmpStringResource(Res.string.kmplib_announcement_dismiss),
    maintenance = kmpStringResource(Res.string.kmplib_announcement_kind_maintenance),
    update = kmpStringResource(Res.string.kmplib_announcement_kind_update),
    news = kmpStringResource(Res.string.kmplib_announcement_kind_news),
    crossPlatform = kmpStringResource(Res.string.kmplib_announcement_kind_cross_platform),
    info = kmpStringResource(Res.string.kmplib_announcement_kind_info),
    windowSameDay = kmpStringResource(Res.string.kmplib_announcement_window_same_day),
    windowRange = kmpStringResource(Res.string.kmplib_announcement_window_range),
    windowOngoing = kmpStringResource(Res.string.kmplib_announcement_window_ongoing),
)

/**
 * As cores do cartão, derivadas de UMA cor de destaque sobre a superfície do tema — e conferidas por
 * contraste WCAG, não supostas: o mesmo destaque vale no tema claro e no escuro, e numa paleta de app
 * qualquer (um amarelo de marca não vira texto ilegível).
 *
 * - [container]: o destaque a 12% sobre a superfície.
 * - [content]/[secondaryContent]: texto principal e secundário, ≥ 4,5:1 sobre o container.
 * - [accent]: o destaque usado como TEXTO (rótulo do tipo), ≥ 4,5:1 sobre o container.
 * - [iconContainer]/[icon]: o círculo do ícone e o ícone, ≥ 3:1 (gráfico).
 * - [actionContent]: o texto do botão sobre [iconContainer], ≥ 4,5:1.
 * - [border]: o contorno (decorativo).
 */
@Immutable
data class AnnouncementColors(
    val container: Color,
    val border: Color,
    val content: Color,
    val secondaryContent: Color,
    val accent: Color,
    val iconContainer: Color,
    val icon: Color,
    val actionContent: Color,
)

/** Monta as [AnnouncementColors] de um [accent] sobre [surface] (pura — testável). */
fun announcementColors(
    accent: Color,
    surface: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
): AnnouncementColors {
    val opaqueSurface = surface.copy(alpha = 1f)
    val container = ColorContrast.compositeOver(accent, ANNOUNCEMENT_CONTAINER_ALPHA, opaqueSurface)
    val iconContainer = ColorContrast.compositeOver(accent, ANNOUNCEMENT_ICON_CONTAINER_ALPHA, container)
    return AnnouncementColors(
        container = container,
        border = ColorContrast.compositeOver(accent, ANNOUNCEMENT_BORDER_ALPHA, opaqueSurface),
        content = ColorContrast.adjustForContrast(onSurface.copy(alpha = 1f), container),
        secondaryContent = ColorContrast.adjustForContrast(onSurfaceVariant.copy(alpha = 1f), container),
        accent = ColorContrast.adjustForContrast(accent.copy(alpha = 1f), container),
        iconContainer = iconContainer,
        icon = ColorContrast.adjustForContrast(accent.copy(alpha = 1f), iconContainer, ColorContrast.AA_GRAPHIC),
        actionContent = ColorContrast.adjustForContrast(accent.copy(alpha = 1f), iconContainer),
    )
}

/** A cor de destaque do tipo, do tema: manutenção = `warning`, aviso = `info`, os demais da marca. */
@Composable
fun announcementAccent(kind: AnnouncementKind): Color = when (kind) {
    AnnouncementKind.MAINTENANCE -> AppColors.current.warning
    AnnouncementKind.UPDATE -> MaterialTheme.colorScheme.primary
    AnnouncementKind.CROSS_PLATFORM -> MaterialTheme.colorScheme.secondary
    AnnouncementKind.NEWS -> MaterialTheme.colorScheme.tertiary
    AnnouncementKind.INFO -> AppColors.current.info
}

/** O ícone do tipo. */
fun announcementIcon(kind: AnnouncementKind): ImageVector = when (kind) {
    AnnouncementKind.MAINTENANCE -> Icons.Outlined.Build
    AnnouncementKind.UPDATE -> Icons.Outlined.SystemUpdate
    AnnouncementKind.CROSS_PLATFORM -> Icons.Outlined.Devices
    AnnouncementKind.NEWS -> Icons.Outlined.AutoAwesome
    AnnouncementKind.INFO -> Icons.Outlined.Info
}

/** Abre a URL da ação pelo [getUrlLauncher] da lib (passa pelo portão de pais em app infantil). */
fun openAnnouncementAction(action: AnnouncementAction) {
    getUrlLauncher().openUrl(action.url)
}

/**
 * **Cartão de aviso** — manutenção, atualização, novidade, "também temos a versão web" e aviso geral.
 * Par do `AnnouncementBanner` da weblib, sobre o mesmo [Announcement].
 *
 * Ícone e cor pelo tipo ([announcementAccent], tudo do tema e conferido por contraste AA nos temas
 * claro e escuro — [announcementColors]); rótulo do tipo, título, mensagem, a janela do evento escrita
 * no fuso e na região do aparelho ("sábado, 18 de outubro, das 22:00 às 02:00" / "Em andamento — até
 * 02:00"), o botão de ação e o X de dispensar (só se [Announcement.dismissible]). Fora do telefone
 * ([LocalIsCompact] falso) o cartão tem teto de largura e o botão vai para a direita.
 *
 * É apresentação pura: QUAL aviso mostrar, e lembrar o que foi dispensado, é do
 * [rememberAnnouncementState] — ou use o [AnnouncementHost], que junta os dois.
 *
 * ```kotlin
 * AnnouncementBanner(
 *     announcement = aviso,
 *     onDismiss = { state.dismiss(aviso.id) },
 *     modifier = Modifier.padding(horizontal = 16.dp),
 * )
 * ```
 *
 * @param onDismiss o X (só aparece se o aviso for dispensável).
 * @param onAction o botão de ação; o default abre a URL ([openAnnouncementAction]).
 * @param now o "agora" do texto da janela (o [AnnouncementState.now] no host).
 * @param texts textos internos (default no idioma da tela).
 * @param extraContent conteúdo do APP abaixo da mensagem (ex.: os botões das lojas no aviso de
 *   "também temos o app").
 */
@Composable
fun AnnouncementBanner(
    announcement: Announcement,
    onDismiss: () -> Unit,
    onAction: (AnnouncementAction) -> Unit = ::openAnnouncementAction,
    modifier: Modifier = Modifier,
    now: Instant = Clock.System.now(),
    texts: AnnouncementTexts = rememberAnnouncementTexts(),
    extraContent: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = announcementAccent(announcement.kind)
    val colors = remember(accent, scheme.surface, scheme.onSurface, scheme.onSurfaceVariant) {
        announcementColors(accent, scheme.surface, scheme.onSurface, scheme.onSurfaceVariant)
    }
    val isCompact = LocalIsCompact.current
    val windowText = announcement.window?.let { rememberAnnouncementWindowText(it, now, texts) }
    val horizontalPadding = if (isCompact) 16.dp else 20.dp

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Surface(
            modifier = Modifier
                .widthIn(max = if (isCompact) UNBOUNDED_WIDTH else ANNOUNCEMENT_MAX_WIDTH)
                .fillMaxWidth()
                .testTag(AnnouncementTestTags.ROOT)
                .semantics { liveRegion = LiveRegionMode.Polite },
            shape = RoundedCornerShape(20.dp),
            color = colors.container,
            contentColor = colors.content,
            border = BorderStroke(1.dp, colors.border),
        ) {
            Row(
                modifier = Modifier.padding(
                    start = horizontalPadding,
                    end = if (announcement.dismissible) 4.dp else horizontalPadding,
                    top = 16.dp,
                    bottom = 16.dp,
                ),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(colors.iconContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = announcementIcon(announcement.kind),
                        contentDescription = null, // o rótulo do tipo, logo ao lado, já diz
                        tint = colors.icon,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                        Text(
                            text = texts.kindLabel(announcement.kind),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accent,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = announcement.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.content,
                            modifier = Modifier
                                .testTag(AnnouncementTestTags.TITLE)
                                .semantics { heading() },
                        )
                        announcement.message?.takeIf { it.isNotBlank() }?.let { message ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.secondaryContent,
                            )
                        }
                        if (windowText != null) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .background(colors.iconContainer, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag(AnnouncementTestTags.WINDOW),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Schedule,
                                    contentDescription = null,
                                    tint = colors.icon,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = windowText,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.actionContent,
                                )
                            }
                        }
                    }
                    if (extraContent != null) {
                        Spacer(Modifier.height(10.dp))
                        extraContent()
                    }
                    announcement.action?.takeIf { it.label.isNotBlank() && it.url.isNotBlank() }?.let { action ->
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isCompact) Arrangement.Start else Arrangement.End,
                        ) {
                            FilledTonalButton(
                                onClick = { onAction(action) },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = colors.iconContainer,
                                    contentColor = colors.actionContent,
                                ),
                                modifier = Modifier.testTag(AnnouncementTestTags.ACTION),
                            ) {
                                Text(action.label, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
                if (announcement.dismissible) {
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = colors.secondaryContent),
                        modifier = Modifier.testTag(AnnouncementTestTags.DISMISS),
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = texts.dismiss)
                    }
                }
            }
        }
    }
}

/** O texto da janela no fuso e na região do aparelho. */
@Composable
private fun rememberAnnouncementWindowText(
    window: AnnouncementWindow,
    now: Instant,
    texts: AnnouncementTexts,
): String {
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val description = describeAnnouncementWindow(window, now, timeZone)
    return remember(window, description, texts, timeZone) {
        formatAnnouncementWindow(window, now, description, texts, timeZone)
    }
}

private fun formatAnnouncementWindow(
    window: AnnouncementWindow,
    now: Instant,
    description: AnnouncementWindowDescription,
    texts: AnnouncementTexts,
    timeZone: TimeZone,
): String {
    val startMs = window.start.toEpochMilliseconds()
    val endMs = window.end.toEpochMilliseconds()
    val dateTimeSkeleton = if (description.sameYearAsNow) SKELETON_DATE_TIME else SKELETON_DATE_TIME_YEAR
    return when (description.phase) {
        AnnouncementWindowPhase.ONGOING -> {
            val endsToday = description.end.date == now.toLocalDateTime(timeZone).date
            val until = if (endsToday) {
                RegionalFormat.formatTime(endMs, timeZone)
            } else {
                RegionalFormat.formatDateTime(endMs, dateTimeSkeleton, timeZone)
            }
            formatResourceArgs(texts.windowOngoing, arrayOf(until))
        }
        AnnouncementWindowPhase.UPCOMING, AnnouncementWindowPhase.ENDED ->
            if (description.sameDay) {
                val day = RegionalFormat.formatDateTime(
                    startMs,
                    if (description.sameYearAsNow) SKELETON_DAY else SKELETON_DAY_YEAR,
                    timeZone,
                )
                formatResourceArgs(
                    texts.windowSameDay,
                    arrayOf(day, RegionalFormat.formatTime(startMs, timeZone), RegionalFormat.formatTime(endMs, timeZone)),
                )
            } else {
                formatResourceArgs(
                    texts.windowRange,
                    arrayOf(
                        RegionalFormat.formatDateTime(startMs, dateTimeSkeleton, timeZone),
                        RegionalFormat.formatDateTime(endMs, dateTimeSkeleton, timeZone),
                    ),
                )
            }
    }
}

/**
 * O aviso da vez numa tela — devolvido por [rememberAnnouncementState].
 *
 * @property current o aviso a mostrar (null = nenhum).
 * @property now o instante da última avaliação (texto "em andamento"/"previsto" do cartão).
 */
@Stable
class AnnouncementState internal constructor(
    private val controller: AnnouncementController,
    private val scope: CoroutineScope,
    private val currentState: State<Announcement?>,
    private val nowState: State<Instant>,
) {
    val current: Announcement? get() = currentState.value
    val now: Instant get() = nowState.value

    /** Dispensa [id] (grava, salvo [AnnouncementFrequency.ALWAYS]) e passa ao próximo pendente. */
    fun dismiss(id: String) {
        scope.launch { controller.dismiss(id) }
    }
}

/** O [PreferencesAnnouncementStore] da lib, lembrado na composição. */
@Composable
fun rememberDefaultAnnouncementStore(): AnnouncementStore = remember { PreferencesAnnouncementStore() }

/**
 * Escolhe o aviso da vez de [announcements] e lembra o que foi visto/dispensado em [store].
 *
 * Regras (ver [selectAnnouncement]): o primeiro PENDENTE dentro da janela de visibilidade, por
 * prioridade MAINTENANCE > UPDATE > CROSS_PLATFORM > NEWS > INFO (empate: ordem da lista). Reavalia
 * sozinho quando um aviso entra/sai da janela ou a manutenção começa, e ao voltar ao primeiro plano.
 * Trocar a lista (ex.: chegou do servidor) reavalia na hora.
 *
 * @param clock relógio (troque em teste).
 */
@Composable
fun rememberAnnouncementState(
    announcements: List<Announcement>,
    store: AnnouncementStore = rememberDefaultAnnouncementStore(),
    clock: Clock = Clock.System,
): AnnouncementState {
    val scope = rememberCoroutineScope()
    val controller = remember(store, clock) { AnnouncementController(store, clock) }
    LaunchedEffect(controller, announcements) {
        controller.submit(announcements)
        while (true) {
            val next = controller.nextChangeAt() ?: break
            // Teto de 1 min: o `delay` corre no relógio monotônico, que para com o aparelho dormindo.
            val wait: Duration = (next - clock.now()).coerceIn(Duration.ZERO, 1.minutes)
            delay(wait + 1.milliseconds)
            controller.reevaluate()
        }
    }
    LifecycleResumeEffect(controller) {
        scope.launch { controller.reevaluate() }
        onPauseOrDispose { }
    }
    val currentState = controller.current.collectAsState()
    val nowState = controller.now.collectAsState()
    return remember(controller, scope) { AnnouncementState(controller, scope, currentState, nowState) }
}

/**
 * Conveniência: o aviso do app numa linha. Cada comunicado aparece pela sua
 * [Announcement.presentation] (2.296.0):
 *
 * - [AnnouncementPresentation.BANNER] → [rememberAnnouncementState] + [AnnouncementBanner] aqui, no
 *   lugar do host, com transição suave entre um aviso e o próximo (sem animação com "reduzir
 *   movimento"). Sem banner pendente, não ocupa espaço nenhum.
 * - [AnnouncementPresentation.MODAL] (o default) → [AnnouncementDialogHost]: diálogo centralizado,
 *   um por vez, no máximo 3 por sessão. [dialogReady] segura a abertura até o app estar pronto
 *   (depois da Splash/login); [dialogSuppressed] segura durante paywall, compra ou câmera. Em build
 *   de teste (`AutomationMode`) o modal fechável não abre (2.310.0) — [dialogSuppressInAutomation]
 *   `= false` para a suíte que quer vê-lo.
 *
 * ```kotlin
 * AnnouncementHost(
 *     announcements = avisos,
 *     modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
 * )
 * ```
 *
 * @param onEvent estatística (2.335.0) — exibição, clique na ação e fechamento, do cartão e do modal
 *   ([AnnouncementEvent]). Nada sai do aparelho por aqui; o `RemoteAnnouncementHost` liga ao servidor.
 */
@Composable
fun AnnouncementHost(
    announcements: List<Announcement>,
    modifier: Modifier = Modifier,
    store: AnnouncementStore = rememberDefaultAnnouncementStore(),
    clock: Clock = Clock.System,
    onAction: (Announcement, AnnouncementAction) -> Unit = { _, action -> openAnnouncementAction(action) },
    extraContent: (@Composable (Announcement) -> Unit)? = null,
    dialogReady: Boolean = true,
    dialogSuppressed: Boolean = false,
    onEvent: AnnouncementEventListener? = null,
    dialogSuppressInAutomation: Boolean = true,
) {
    val banners = remember(announcements) { announcements.filter { it.presentation == AnnouncementPresentation.BANNER } }
    val modals = remember(announcements) { announcements.filter { it.presentation == AnnouncementPresentation.MODAL } }
    val state = rememberAnnouncementState(banners, store, clock)
    AnnouncementHost(state = state, modifier = modifier, onAction = onAction, extraContent = extraContent, onEvent = onEvent)
    AnnouncementDialogHost(
        announcements = modals,
        ready = dialogReady,
        suppressed = dialogSuppressed,
        store = store,
        clock = clock,
        onAction = onAction,
        suppressInAutomation = dialogSuppressInAutomation,
        onEvent = onEvent,
    )
}

/**
 * O mesmo [AnnouncementHost], sobre um [AnnouncementState] que a tela já tem — SÓ banner: mostra
 * como cartão o que o estado escolher, qualquer que seja a `presentation`. Para o modal, some um
 * [AnnouncementDialogHost] (ou filtre a lista por `presentation` antes do [rememberAnnouncementState]).
 *
 * @param onEvent estatística (2.335.0): [AnnouncementEvent.SHOWN] quando o cartão de um aviso entra,
 *   [AnnouncementEvent.ACTION_CLICK] e [AnnouncementEvent.DISMISS_CLOSE] (o X).
 */
@Composable
fun AnnouncementHost(
    state: AnnouncementState,
    modifier: Modifier = Modifier,
    onAction: (Announcement, AnnouncementAction) -> Unit = { _, action -> openAnnouncementAction(action) },
    onEvent: AnnouncementEventListener? = null,
    extraContent: (@Composable (Announcement) -> Unit)? = null,
) {
    val reduceMotion = LocalReduceMotion.current
    val listener by rememberUpdatedState(onEvent)
    val current = state.current
    LaunchedEffect(current?.id) {
        if (current != null) listener?.onEvent(current, AnnouncementEvent.SHOWN)
    }
    AnimatedContent(
        targetState = state.current,
        contentKey = { it?.id },
        transitionSpec = {
            if (reduceMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (fadeIn(tween(220)) togetherWith fadeOut(tween(160))).using(SizeTransform(clip = false))
            }
        },
        label = "announcement",
    ) { announcement ->
        if (announcement != null) {
            AnnouncementBanner(
                announcement = announcement,
                onDismiss = {
                    listener?.onEvent(announcement, AnnouncementEvent.DISMISS_CLOSE)
                    state.dismiss(announcement.id)
                },
                onAction = { action ->
                    listener?.onEvent(announcement, AnnouncementEvent.ACTION_CLICK)
                    onAction(announcement, action)
                },
                modifier = modifier,
                now = state.now,
                extraContent = extraContent?.let { content -> { content(announcement) } },
            )
        }
    }
}

private val UNBOUNDED_WIDTH = androidx.compose.ui.unit.Dp.Infinity
private val ANNOUNCEMENT_MAX_WIDTH = 720.dp
private const val ANNOUNCEMENT_CONTAINER_ALPHA = 0.12f
private const val ANNOUNCEMENT_ICON_CONTAINER_ALPHA = 0.18f
private const val ANNOUNCEMENT_BORDER_ALPHA = 0.32f
private const val SKELETON_DAY = "EEEEdMMMM"
private const val SKELETON_DAY_YEAR = "EEEEdMMMMy"
private const val SKELETON_DATE_TIME = "EEEdMMMjjmm"
private const val SKELETON_DATE_TIME_YEAR = "EEEdMMMyjjmm"
