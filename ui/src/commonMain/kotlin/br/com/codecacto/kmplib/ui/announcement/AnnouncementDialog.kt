@file:OptIn(ExperimentalTime::class, ExperimentalMaterial3Api::class)

package br.com.codecacto.kmplib.ui.announcement

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import br.com.codecacto.kmplib.platform.automation.AutomationMode
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import br.com.codecacto.kmplib.core.locale.RegionalFormat
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_announcement_dialog_auto_close
import br.com.codecacto.kmplib.generated.resources.kmplib_announcement_dialog_close
import br.com.codecacto.kmplib.generated.resources.kmplib_announcement_dialog_counter
import br.com.codecacto.kmplib.generated.resources.kmplib_announcement_dialog_duration_hours
import br.com.codecacto.kmplib.generated.resources.kmplib_announcement_dialog_duration_hours_minutes
import br.com.codecacto.kmplib.generated.resources.kmplib_announcement_dialog_duration_minutes
import br.com.codecacto.kmplib.generated.resources.kmplib_announcement_dialog_duration_prefix
import br.com.codecacto.kmplib.generated.resources.kmplib_announcement_dialog_got_it
import br.com.codecacto.kmplib.generated.resources.kmplib_announcement_dialog_ongoing
import br.com.codecacto.kmplib.generated.resources.kmplib_announcement_dialog_opens_outside
import br.com.codecacto.kmplib.generated.resources.kmplib_announcement_dialog_window_at
import br.com.codecacto.kmplib.generated.resources.kmplib_announcement_dialog_window_label
import br.com.codecacto.kmplib.generated.resources.kmplib_announcement_dialog_window_label_maintenance
import br.com.codecacto.kmplib.platform.motion.LocalReduceMotion
import br.com.codecacto.kmplib.ui.components.AppBasicAlertDialog
import br.com.codecacto.kmplib.ui.components.AppButton
import br.com.codecacto.kmplib.ui.components.AppOutlinedButton
import br.com.codecacto.kmplib.ui.locale.formatResourceArgs
import br.com.codecacto.kmplib.ui.locale.kmpStringResource
import br.com.codecacto.kmplib.ui.theme.ColorContrast
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * ─────────────────────── Comunicado em MODAL (2.296.0) ───────────────────────
 *
 * Spec de design compartilhada com a weblib: `Lib/weblib/docs/design/comunicado-modal.md`.
 * Cartão RETRATO centralizado: foto 1:1 no topo (ou a faixa colorida do tipo), selo, título,
 * mensagem (a região que rola), bloco da janela, botões fixos no rodapé.
 */

/** Ids estáveis do modal para o Maestro (a janela já religa `testTagsAsResourceId`). */
object AnnouncementDialogTestTags {
    const val ROOT: String = "aviso-modal"
    const val TITLE: String = "aviso-modal-titulo"
    const val MESSAGE: String = "aviso-modal-mensagem"
    const val IMAGE: String = "aviso-modal-imagem"
    const val HERO: String = "aviso-modal-faixa"
    const val WINDOW: String = "aviso-modal-janela"
    const val ONGOING: String = "aviso-modal-em-andamento"
    const val COUNTER: String = "aviso-modal-contador"
    const val ACTION: String = "aviso-modal-acao"
    const val GOT_IT: String = "aviso-modal-entendi"
    const val CLOSE: String = "aviso-modal-fechar"
    const val AUTO_CLOSE: String = "aviso-modal-fecha-sozinho"
}

/**
 * Textos do modal (4 idiomas pelos recursos da lib; [rememberAnnouncementDialogTexts] é o default).
 *
 * @property counter "%1$s de %2$s" — posição e total da fila.
 * @property windowAt "%1$s às %2$s" — dia e hora (janela que cruza a meia-noite).
 * @property durationHours / durationMinutes / durationHoursMinutes — "4 h", "30 min", "1 h 30 min".
 * @property durationPrefix "Duração: %1$s".
 */
@Immutable
data class AnnouncementDialogTexts(
    val base: AnnouncementTexts,
    val gotIt: String,
    val close: String,
    val counter: String,
    val autoClose: String,
    val windowLabelMaintenance: String,
    val windowLabel: String,
    val ongoing: String,
    val windowAt: String,
    val durationHours: String,
    val durationMinutes: String,
    val durationHoursMinutes: String,
    val durationPrefix: String,
    val opensOutside: String,
) {
    fun counterText(position: Int, total: Int): String = formatResourceArgs(counter, arrayOf(position.toString(), total.toString()))
}

/** Os textos do modal no idioma da tela. Para trocar uma frase: `rememberAnnouncementDialogTexts().copy(…)`. */
@Composable
fun rememberAnnouncementDialogTexts(): AnnouncementDialogTexts = AnnouncementDialogTexts(
    base = rememberAnnouncementTexts(),
    gotIt = kmpStringResource(Res.string.kmplib_announcement_dialog_got_it),
    close = kmpStringResource(Res.string.kmplib_announcement_dialog_close),
    counter = kmpStringResource(Res.string.kmplib_announcement_dialog_counter),
    autoClose = kmpStringResource(Res.string.kmplib_announcement_dialog_auto_close),
    windowLabelMaintenance = kmpStringResource(Res.string.kmplib_announcement_dialog_window_label_maintenance),
    windowLabel = kmpStringResource(Res.string.kmplib_announcement_dialog_window_label),
    ongoing = kmpStringResource(Res.string.kmplib_announcement_dialog_ongoing),
    windowAt = kmpStringResource(Res.string.kmplib_announcement_dialog_window_at),
    durationHours = kmpStringResource(Res.string.kmplib_announcement_dialog_duration_hours),
    durationMinutes = kmpStringResource(Res.string.kmplib_announcement_dialog_duration_minutes),
    durationHoursMinutes = kmpStringResource(Res.string.kmplib_announcement_dialog_duration_hours_minutes),
    durationPrefix = kmpStringResource(Res.string.kmplib_announcement_dialog_duration_prefix),
    opensOutside = kmpStringResource(Res.string.kmplib_announcement_dialog_opens_outside),
)

/** Duração da janela em horas e minutos (pura). Arredonda o minuto para cima; mínimo 1 min. */
data class AnnouncementDuration(val hours: Long, val minutes: Int)

/** A duração de [window] ([AnnouncementDuration]). */
fun announcementDuration(window: AnnouncementWindow): AnnouncementDuration {
    val totalSeconds = (window.end - window.start).inWholeSeconds.coerceAtLeast(0)
    val totalMinutes = ((totalSeconds + 59) / 60).coerceAtLeast(1)
    return AnnouncementDuration(hours = totalMinutes / 60, minutes = (totalMinutes % 60).toInt())
}

/** "4 h", "30 min", "1 h 30 min" no idioma de [texts]. */
fun formatAnnouncementDuration(window: AnnouncementWindow, texts: AnnouncementDialogTexts): String {
    val d = announcementDuration(window)
    return when {
        d.hours == 0L -> formatResourceArgs(texts.durationMinutes, arrayOf(d.minutes.toString()))
        d.minutes == 0 -> formatResourceArgs(texts.durationHours, arrayOf(d.hours.toString()))
        else -> formatResourceArgs(texts.durationHoursMinutes, arrayOf(d.hours.toString(), d.minutes.toString()))
    }
}

/**
 * Cores do modal, derivadas do destaque do tipo sobre a superfície do tema e conferidas por contraste
 * WCAG (pura — testada nos temas claro e escuro).
 *
 * - [card]/[content]/[secondaryContent]: o cartão e os textos (≥ 4,5:1).
 * - [heroTop]/[heroBottom]: o degradê da faixa sem foto (destaque a 22% → 6% sobre o cartão).
 * - [heroCircle]/[heroIcon]: o círculo na cor sólida do tipo e o ícone (≥ 3:1, na prática ≥ 4,5:1).
 * - [badge]/[badgeContent]: o selo do tipo (texto ≥ 4,5:1).
 * - [windowContainer]/[windowBorder]/[windowContent]/[windowSecondary]/[windowAccent]: o bloco da
 *   janela (textos ≥ 4,5:1; o ícone/ponto [windowAccent] ≥ 3:1).
 * - [chip]/[chipContent]: o X e o "1 de 3" sobre a foto — legíveis sobre QUALQUER foto (≥ 4,5:1 com
 *   o chip composto sobre preto e sobre branco).
 * - [border]: o contorno de 1 dp (recorta o cartão no tema escuro).
 */
@Immutable
data class AnnouncementDialogColors(
    val card: Color,
    val content: Color,
    val secondaryContent: Color,
    val border: Color,
    val heroTop: Color,
    val heroBottom: Color,
    val heroCircle: Color,
    val heroIcon: Color,
    val badge: Color,
    val badgeContent: Color,
    val windowContainer: Color,
    val windowBorder: Color,
    val windowContent: Color,
    val windowSecondary: Color,
    val windowAccent: Color,
    val chip: Color,
    val chipContent: Color,
)

/** Monta as [AnnouncementDialogColors] de [accent] sobre [surface] (pura). */
fun announcementDialogColors(
    accent: Color,
    surface: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
): AnnouncementDialogColors {
    val card = surface.copy(alpha = 1f)
    val solid = accent.copy(alpha = 1f)
    val badge = ColorContrast.compositeOver(solid, 0.14f, card)
    val windowContainer = ColorContrast.compositeOver(solid, 0.08f, card)
    val chip = card.copy(alpha = DIALOG_CHIP_ALPHA)
    val chipWorst = listOf(Color.Black, Color.White).map { ColorContrast.compositeOver(card, DIALOG_CHIP_ALPHA, it) }
    var chipContent = onSurface.copy(alpha = 1f)
    for (bg in chipWorst) chipContent = ColorContrast.adjustForContrast(chipContent, bg)
    return AnnouncementDialogColors(
        card = card,
        content = ColorContrast.adjustForContrast(onSurface.copy(alpha = 1f), card),
        secondaryContent = ColorContrast.adjustForContrast(onSurfaceVariant.copy(alpha = 1f), card),
        border = ColorContrast.compositeOver(onSurface.copy(alpha = 1f), 0.12f, card),
        heroTop = ColorContrast.compositeOver(solid, 0.22f, card),
        heroBottom = ColorContrast.compositeOver(solid, 0.06f, card),
        heroCircle = solid,
        heroIcon = ColorContrast.pickOnColor(solid),
        badge = badge,
        badgeContent = ColorContrast.adjustForContrast(solid, badge),
        windowContainer = windowContainer,
        windowBorder = ColorContrast.compositeOver(solid, 0.35f, card),
        windowContent = ColorContrast.adjustForContrast(onSurface.copy(alpha = 1f), windowContainer),
        windowSecondary = ColorContrast.adjustForContrast(onSurfaceVariant.copy(alpha = 1f), windowContainer),
        windowAccent = ColorContrast.adjustForContrast(solid, windowContainer, ColorContrast.AA_GRAPHIC),
        chip = chip,
        chipContent = chipContent,
    )
}

@Composable
private fun rememberDialogColors(kind: AnnouncementKind): AnnouncementDialogColors {
    val scheme = MaterialTheme.colorScheme
    val accent = announcementAccent(kind)
    return remember(accent, scheme.surface, scheme.onSurface, scheme.onSurfaceVariant) {
        announcementDialogColors(accent, scheme.surface, scheme.onSurface, scheme.onSurfaceVariant)
    }
}

/**
 * **O cartão do comunicado**, sem a janela do diálogo — o que o [AnnouncementDialog] desenha, exposto
 * para pré-visualização (`@Preview`, tela de "como vai ficar"). Mede-se pelo [maxWidth]/[maxHeight] da
 * área disponível (a janela do app, no diálogo).
 *
 * @param position/total o contador "1 de 3" (só aparece com `total > 1`).
 * @param dismissible se oferece X/"Entendi" ([isDialogDismissibleAt]).
 * @param onDismiss X e "Entendi".
 * @param onAction o botão de ação (o host abre a URL e fecha).
 * @param onClose o X, quando precisa ser distinguido do "Entendi" (estatística, 2.335.0); `null` = [onDismiss].
 */
@Composable
fun AnnouncementDialogCard(
    announcement: Announcement,
    onDismiss: () -> Unit,
    onAction: (AnnouncementAction) -> Unit,
    maxWidth: Dp,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
    position: Int = 1,
    total: Int = 1,
    dismissible: Boolean = announcement.dismissible,
    now: Instant = Clock.System.now(),
    texts: AnnouncementDialogTexts = rememberAnnouncementDialogTexts(),
    onClose: (() -> Unit)? = null,
) {
    val closeAction: () -> Unit = onClose ?: onDismiss
    val colors = rememberDialogColors(announcement.kind)
    val landscape = maxHeight < LANDSCAPE_MAX_HEIGHT && maxWidth > maxHeight
    val compact = maxWidth < COMPACT_MAX_WIDTH
    val cardMaxHeight = min(maxHeight * 0.85f, CARD_MAX_HEIGHT)
    val cardWidth = if (landscape) min(maxWidth * 0.92f, LANDSCAPE_CARD_WIDTH) else min(maxWidth * 0.92f, CARD_WIDTH)
    val shape = RoundedCornerShape(if (compact) 20.dp else 24.dp)
    var imageFailed by remember(announcement.displayImage?.url) { mutableStateOf(false) }
    val image = announcement.displayImage?.takeIf { !imageFailed }
    val action = announcement.action?.takeIf { it.label.isNotBlank() && it.url.isNotBlank() }
    val minHeight = if (!compact && !landscape && image == null) min(NO_PHOTO_MIN_HEIGHT, cardMaxHeight) else 0.dp

    Surface(
        modifier = modifier
            .width(cardWidth)
            .heightIn(min = minHeight, max = cardMaxHeight)
            .shadow(24.dp, shape)
            .testTag(AnnouncementDialogTestTags.ROOT),
        shape = shape,
        color = colors.card,
        contentColor = colors.content,
        border = BorderStroke(1.dp, colors.border),
    ) {
        if (landscape) {
            Row(Modifier.fillMaxWidth()) {
                if (image != null) {
                    Box(Modifier.fillMaxWidth(0.4f).fillMaxHeight()) {
                        DialogPhoto(image, Modifier.fillMaxWidth().fillMaxHeight(), onError = { imageFailed = true })
                        DialogCounter(position, total, colors, texts, Modifier.align(Alignment.TopStart).padding(12.dp))
                    }
                }
                Column(Modifier.weight(1f).padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (image == null) DialogCounter(position, total, colors, texts, Modifier)
                        Spacer(Modifier.weight(1f))
                        if (dismissible) DialogCloseButton(colors, texts, closeAction, chip = false)
                    }
                    DialogBody(announcement, colors, texts, now, compact = true, Modifier.weight(1f, fill = false).padding(end = 12.dp))
                    DialogFooter(announcement, action, dismissible, colors, texts, onDismiss, onAction, row = true, Modifier.padding(end = 12.dp, top = 12.dp))
                }
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth()) {
                    if (image != null) {
                        val photoHeight = min(cardWidth, maxHeight * if (compact) 0.44f else 0.46f)
                        DialogPhoto(image, Modifier.fillMaxWidth().height(photoHeight), onError = { imageFailed = true })
                    } else {
                        DialogHero(announcement.kind, colors, if (compact) 104.dp else 120.dp, if (compact) 56.dp else 64.dp)
                    }
                    DialogCounter(position, total, colors, texts, Modifier.align(Alignment.TopStart).padding(12.dp))
                    if (dismissible) {
                        DialogCloseButton(colors, texts, closeAction, chip = true, Modifier.align(Alignment.TopEnd).padding(8.dp))
                    }
                }
                val hPad = if (compact) 20.dp else 24.dp
                DialogBody(
                    announcement, colors, texts, now, compact,
                    Modifier.weight(1f, fill = false).padding(start = hPad, end = hPad, top = 20.dp),
                )
                DialogFooter(
                    announcement, action, dismissible, colors, texts, onDismiss, onAction, row = false,
                    Modifier.padding(start = hPad, end = hPad, top = 20.dp, bottom = hPad),
                )
            }
        }
    }
}

@Composable
private fun DialogPhoto(image: AnnouncementImage, modifier: Modifier, onError: () -> Unit) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        AsyncImage(
            model = image.url,
            contentDescription = image.alt?.takeIf { it.isNotBlank() },
            contentScale = ContentScale.Crop,
            alignment = when (image.focus) {
                AnnouncementImageFocus.TOP -> Alignment.TopCenter
                AnnouncementImageFocus.CENTER -> Alignment.Center
                AnnouncementImageFocus.BOTTOM -> Alignment.BottomCenter
            },
            onError = { onError() },
            modifier = Modifier.fillMaxWidth().fillMaxHeight().testTag(AnnouncementDialogTestTags.IMAGE),
        )
    }
}

@Composable
private fun DialogHero(kind: AnnouncementKind, colors: AnnouncementDialogColors, height: Dp, circle: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(Brush.verticalGradient(listOf(colors.heroTop, colors.heroBottom)))
            .testTag(AnnouncementDialogTestTags.HERO),
        contentAlignment = Alignment.Center,
    ) {
        // Motivo discreto: dois anéis concêntricos atrás do círculo.
        Box(Modifier.size(circle * 2.2f).border(1.dp, colors.heroCircle.copy(alpha = 0.08f), CircleShape))
        Box(Modifier.size(circle * 1.6f).border(1.dp, colors.heroCircle.copy(alpha = 0.10f), CircleShape))
        Box(Modifier.size(circle).background(colors.heroCircle, CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = announcementIcon(kind),
                contentDescription = null, // o selo do tipo, logo abaixo, diz o mesmo
                tint = colors.heroIcon,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun DialogCounter(position: Int, total: Int, colors: AnnouncementDialogColors, texts: AnnouncementDialogTexts, modifier: Modifier) {
    if (total <= 1) return
    Box(
        modifier = modifier
            .height(28.dp)
            .shadow(2.dp, RoundedCornerShape(14.dp))
            .background(colors.chip, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp)
            .testTag(AnnouncementDialogTestTags.COUNTER),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = texts.counterText(position, total),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.chipContent,
        )
    }
}

@Composable
private fun DialogCloseButton(
    colors: AnnouncementDialogColors,
    texts: AnnouncementDialogTexts,
    onDismiss: () -> Unit,
    chip: Boolean,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onDismiss,
        modifier = modifier.size(44.dp).testTag(AnnouncementDialogTestTags.CLOSE),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (chip) colors.chip else Color.Transparent,
            contentColor = if (chip) colors.chipContent else colors.secondaryContent,
        ),
    ) {
        Icon(Icons.Outlined.Close, contentDescription = texts.close)
    }
}

@Composable
private fun DialogBody(
    announcement: Announcement,
    colors: AnnouncementDialogColors,
    texts: AnnouncementDialogTexts,
    now: Instant,
    compact: Boolean,
    modifier: Modifier,
) {
    val scroll = rememberScrollState()
    Box(modifier) {
        Column(Modifier.verticalScroll(scroll)) {
            // Selo do tipo.
            Row(
                modifier = Modifier
                    .background(colors.badge, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(announcementIcon(announcement.kind), contentDescription = null, tint = colors.badgeContent, modifier = Modifier.size(14.dp))
                Text(
                    text = texts.base.kindLabel(announcement.kind).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = colors.badgeContent,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = announcement.title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = if (compact) 20.sp else 24.sp,
                    lineHeight = if (compact) 26.sp else 30.sp,
                ),
                fontWeight = FontWeight.SemiBold,
                color = colors.content,
                modifier = Modifier.testTag(AnnouncementDialogTestTags.TITLE).semantics { heading() },
            )
            announcement.message?.takeIf { it.isNotBlank() }?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = if (compact) 22.sp else 24.sp),
                    color = colors.secondaryContent,
                    modifier = Modifier.testTag(AnnouncementDialogTestTags.MESSAGE),
                )
            }
            announcement.window?.takeIf { it.end > it.start }?.let { window ->
                Spacer(Modifier.height(16.dp))
                DialogWindowBlock(announcement.kind, window, now, colors, texts, compact)
            }
            Spacer(Modifier.height(2.dp))
        }
        // Sombra interna (12 dp) onde há texto escondido — mostra que a região rola.
        if (scroll.canScrollBackward) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(12.dp)
                .background(Brush.verticalGradient(listOf(colors.card, colors.card.copy(alpha = 0f)))))
        }
        if (scroll.canScrollForward) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(12.dp)
                .background(Brush.verticalGradient(listOf(colors.card.copy(alpha = 0f), colors.card))))
        }
    }
}

@Composable
private fun DialogWindowBlock(
    kind: AnnouncementKind,
    window: AnnouncementWindow,
    now: Instant,
    colors: AnnouncementDialogColors,
    texts: AnnouncementDialogTexts,
    compact: Boolean,
) {
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val description = describeAnnouncementWindow(window, now, timeZone)
    val startMs = window.start.toEpochMilliseconds()
    val endMs = window.end.toEpochMilliseconds()
    val daySkeleton = if (description.sameYearAsNow) SKELETON_SHORT_DAY else SKELETON_SHORT_DAY_YEAR
    val startDay = RegionalFormat.formatDateTime(startMs, daySkeleton, timeZone)
    val startTime = RegionalFormat.formatTime(startMs, timeZone)
    val endTime = RegionalFormat.formatTime(endMs, timeZone)
    val duration = formatAnnouncementDuration(window, texts)
    val ongoing = description.phase == AnnouncementWindowPhase.ONGOING
    val tabular = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.windowContainer, RoundedCornerShape(16.dp))
            .border(1.dp, colors.windowBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag(AnnouncementDialogTestTags.WINDOW)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = colors.windowAccent, modifier = Modifier.size(16.dp))
            Text(
                text = if (kind == AnnouncementKind.MAINTENANCE) texts.windowLabelMaintenance else texts.windowLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = colors.windowSecondary,
            )
        }
        Spacer(Modifier.height(6.dp))
        if (description.sameDay) {
            Text(startDay, style = tabular.copy(fontSize = 16.sp, lineHeight = 22.sp), fontWeight = FontWeight.SemiBold, color = colors.windowContent)
            Text(
                text = "$startTime → $endTime",
                style = tabular.copy(fontSize = if (compact) 24.sp else 28.sp, lineHeight = if (compact) 30.sp else 32.sp),
                fontWeight = FontWeight.SemiBold,
                color = colors.windowContent,
            )
        } else {
            val endDay = RegionalFormat.formatDateTime(endMs, daySkeleton, timeZone)
            Text(formatResourceArgs(texts.windowAt, arrayOf(startDay, startTime)), style = tabular.copy(fontSize = 16.sp, lineHeight = 22.sp), fontWeight = FontWeight.SemiBold, color = colors.windowContent)
            Text(formatResourceArgs(texts.windowAt, arrayOf(endDay, endTime)), style = tabular.copy(fontSize = 16.sp, lineHeight = 22.sp), fontWeight = FontWeight.SemiBold, color = colors.windowContent)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatResourceArgs(texts.durationPrefix, arrayOf(duration)),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.windowSecondary,
            )
            if (ongoing) {
                Spacer(Modifier.width(10.dp))
                OngoingBadge(colors, texts)
            }
        }
    }
}

@Composable
private fun OngoingBadge(colors: AnnouncementDialogColors, texts: AnnouncementDialogTexts) {
    val reduceMotion = LocalReduceMotion.current
    val pulse = if (reduceMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "aviso-em-andamento")
        val value by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "aviso-pulso",
        )
        value
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.testTag(AnnouncementDialogTestTags.ONGOING),
    ) {
        Box(Modifier.size(8.dp).alpha(pulse).background(colors.windowAccent, CircleShape))
        Text(texts.ongoing, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = colors.windowContent)
    }
}

@Composable
private fun DialogFooter(
    announcement: Announcement,
    action: AnnouncementAction?,
    dismissible: Boolean,
    colors: AnnouncementDialogColors,
    texts: AnnouncementDialogTexts,
    onDismiss: () -> Unit,
    onAction: (AnnouncementAction) -> Unit,
    row: Boolean,
    modifier: Modifier,
) {
    if (action == null && !dismissible) {
        // Não-fechável sem ação: nenhum botão. Em manutenção em andamento, a consequência invisível.
        if (announcement.kind == AnnouncementKind.MAINTENANCE) {
            Text(
                text = texts.autoClose,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryContent,
                modifier = modifier.testTag(AnnouncementDialogTestTags.AUTO_CLOSE),
            )
        } else {
            Spacer(modifier)
        }
        return
    }
    val actionButton: @Composable (Modifier) -> Unit = { m ->
        if (action != null) {
            AppButton(
                text = action.label,
                onClick = { onAction(action) },
                height = 48.dp,
                fontSize = 15.sp,
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                modifier = m
                    .testTag(AnnouncementDialogTestTags.ACTION)
                    .semantics { contentDescription = "${action.label}, ${texts.opensOutside}" },
            )
        }
    }
    val gotItButton: @Composable (Modifier) -> Unit = { m ->
        if (dismissible) {
            val tagged = m.testTag(AnnouncementDialogTestTags.GOT_IT)
            if (action == null) {
                AppButton(text = texts.gotIt, onClick = onDismiss, height = 48.dp, fontSize = 15.sp, modifier = tagged)
            } else {
                AppOutlinedButton(text = texts.gotIt, onClick = onDismiss, height = 48.dp, fontSize = 15.sp, modifier = tagged)
            }
        }
    }
    if (row) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (dismissible) gotItButton(Modifier.weight(1f))
            if (action != null) actionButton(Modifier.weight(1f))
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actionButton(Modifier)
            gotItButton(Modifier)
        }
    }
}

/**
 * Abre a ação e, se o modal for fechável agora, dispensa o comunicado (a pessoa já agiu).
 * Em manutenção que bloqueia, só abre a URL.
 */
private fun runDialogAction(
    action: AnnouncementAction,
    dismissible: Boolean,
    onAction: (AnnouncementAction) -> Unit,
    onDismiss: (AnnouncementDismissReason) -> Unit,
) {
    onAction(action)
    if (dismissible) onDismiss(AnnouncementDismissReason.AFTER_ACTION)
}

/** Por que o modal fechou — separa "Entendi", X/voltar/fora e o fechamento que segue a ação. */
internal enum class AnnouncementDismissReason { GOT_IT, CLOSE, AFTER_ACTION }

/** O evento de estatística de um fechamento (`null` = o que segue a ação, já contado como clique). */
internal fun AnnouncementDismissReason.toEvent(): AnnouncementEvent? = when (this) {
    AnnouncementDismissReason.GOT_IT -> AnnouncementEvent.DISMISS_GOT_IT
    AnnouncementDismissReason.CLOSE -> AnnouncementEvent.DISMISS_CLOSE
    AnnouncementDismissReason.AFTER_ACTION -> null
}

/**
 * **Comunicado em MODAL** — cartão retrato centralizado, com foto opcional (Coil; falhou → faixa
 * colorida do tipo), selo do tipo, título, mensagem que rola, bloco da janela (data e hora grandes,
 * duração, "Em andamento") e botões fixos (ação primária + "Entendi"; sem ação, "Entendi" é o primário).
 *
 * Não fechável ([dismissible] falso): sem X, sem "Entendi", voltar e toque fora ignorados. Entrada
 * 220 ms (fade + escala .96→1), só fade com "reduzir movimento" ([LocalReduceMotion]).
 *
 * Para a FILA (um por vez, "1 de 3", teto por sessão, momento de abrir) use o [AnnouncementDialogHost];
 * este composable mostra UM comunicado enquanto estiver na composição.
 *
 * @param onAction o botão de ação; default abre a URL ([openAnnouncementAction], portão de pais em app
 *   infantil). Depois dele o comunicado é dispensado (se fechável).
 */
@Composable
fun AnnouncementDialog(
    announcement: Announcement,
    onDismiss: () -> Unit,
    onAction: (AnnouncementAction) -> Unit = ::openAnnouncementAction,
    position: Int = 1,
    total: Int = 1,
    now: Instant = Clock.System.now(),
    dismissible: Boolean = announcement.isDialogDismissibleAt(now),
    texts: AnnouncementDialogTexts = rememberAnnouncementDialogTexts(),
) {
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = true
    AnnouncementDialogWindow(
        visibleState = visibleState,
        ui = AnnouncementDialogUiState(announcement, position, total, dismissible, now),
        onDismiss = { onDismiss() },
        onAction = onAction,
        texts = texts,
    )
}

@Composable
private fun AnnouncementDialogWindow(
    visibleState: MutableTransitionState<Boolean>,
    ui: AnnouncementDialogUiState,
    onDismiss: (AnnouncementDismissReason) -> Unit,
    onAction: (AnnouncementAction) -> Unit,
    texts: AnnouncementDialogTexts,
) {
    val reduceMotion = LocalReduceMotion.current
    val dismissible = ui.dismissible
    AppBasicAlertDialog(
        onDismissRequest = { if (dismissible) onDismiss(AnnouncementDismissReason.CLOSE) },
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
            usePlatformDefaultWidth = false,
        ),
    ) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val maxW = if (maxWidth == Dp.Infinity) CARD_WIDTH / 0.92f else maxWidth
            val maxH = if (maxHeight == Dp.Infinity) CARD_MAX_HEIGHT / 0.85f else maxHeight
            AnimatedVisibility(
                visibleState = visibleState,
                enter = if (reduceMotion) fadeIn(tween(120)) else fadeIn(tween(220)) + scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.96f),
                exit = if (reduceMotion) fadeOut(tween(120)) else fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.98f),
            ) {
                AnimatedContent(
                    targetState = ui,
                    contentKey = { it.current?.id },
                    transitionSpec = {
                        if (reduceMotion) {
                            fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                        } else {
                            // O atual sai (150 ms), pausa de 120 ms, o próximo entra — o véu fica.
                            (fadeIn(tween(220, delayMillis = 270)) + scaleIn(tween(220, delayMillis = 270), initialScale = 0.96f)) togetherWith
                                fadeOut(tween(150))
                        }.using(SizeTransform(clip = false))
                    },
                    label = "aviso-modal",
                ) { shown ->
                    val announcement = shown.current ?: return@AnimatedContent
                    AnnouncementDialogCard(
                        announcement = announcement,
                        onDismiss = { onDismiss(AnnouncementDismissReason.GOT_IT) },
                        onClose = { onDismiss(AnnouncementDismissReason.CLOSE) },
                        onAction = { action -> runDialogAction(action, shown.dismissible, onAction, onDismiss) },
                        maxWidth = maxW,
                        maxHeight = maxH,
                        position = shown.position,
                        total = shown.total,
                        dismissible = shown.dismissible,
                        now = shown.now,
                        texts = texts,
                    )
                }
            }
        }
    }
}

/**
 * A fila do modal numa tela — devolvida por [rememberAnnouncementDialogState].
 *
 * @property ui o comunicado da vez, a posição na fila e se fecha agora.
 */
@Stable
class AnnouncementDialogState internal constructor(
    private val controller: AnnouncementDialogController,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val uiState: State<AnnouncementDialogUiState>,
) {
    val ui: AnnouncementDialogUiState get() = uiState.value

    /** Fecha [id] e passa ao próximo (ignorado se ele bloqueia agora). */
    fun dismiss(id: String) {
        scope.launch { controller.dismiss(id) }
    }
}

/**
 * Monta a fila do modal de [announcements] (só os de `presentation = MODAL`) e lembra o visto/fechado
 * em [store] (aparelho) e em [session] (processo). Reavalia sozinha quando a janela começa/termina e
 * ao voltar ao primeiro plano.
 *
 * @param maxPerSession teto de comunicados por sessão (default [DEFAULT_ANNOUNCEMENT_DIALOGS_PER_SESSION]).
 */
@Composable
fun rememberAnnouncementDialogState(
    announcements: List<Announcement>,
    store: AnnouncementStore = rememberDefaultAnnouncementStore(),
    clock: Clock = Clock.System,
    session: AnnouncementDialogSession = AnnouncementDialogSession.Shared,
    maxPerSession: Int = DEFAULT_ANNOUNCEMENT_DIALOGS_PER_SESSION,
): AnnouncementDialogState {
    val scope = rememberCoroutineScope()
    val controller = remember(store, clock, session, maxPerSession) {
        AnnouncementDialogController(store, clock, session, maxPerSession)
    }
    LaunchedEffect(controller, announcements) {
        controller.submit(announcements)
        while (true) {
            val next = controller.nextChangeAt() ?: break
            val wait: Duration = (next - clock.now()).coerceIn(Duration.ZERO, 1.minutes)
            delay(wait + 1.milliseconds)
            controller.reevaluate()
        }
    }
    LifecycleResumeEffect(controller) {
        scope.launch { controller.reevaluate() }
        onPauseOrDispose { }
    }
    val uiState = controller.state.collectAsState()
    return remember(controller, scope) { AnnouncementDialogState(controller, scope, uiState) }
}

/**
 * **Comunicados em modal, com fila** — o que o app põe UMA vez na tela principal (o
 * [AnnouncementHost] e o `RemoteAnnouncementHost` já o fazem para os de `presentation = MODAL`).
 *
 * - Um por vez, por prioridade; contador "1 de N"; no máximo [maxPerSession] por sessão.
 * - Abre só com [ready] (app pronto: depois da Splash/login) e [openDelay] (~800 ms) depois; nunca
 *   com [suppressed] (paywall, compra, câmera) nem com o teclado aberto — exceto a manutenção que
 *   bloqueia, que abre na hora.
 * - Não fechável só bloqueia DURANTE a janela; antes dela, fecha por sessão.
 * - **Build de teste não abre comunicado** (2.310.0): com o `AutomationMode` ligado (dublê da loja,
 *   Test Harness, marca do runner de QA em binário depurável), o modal fechável não abre — como o
 *   pedido de avaliação e a atualização opcional. A manutenção que BLOQUEIA continua abrindo (como o
 *   Hard update). Release nunca lê a marca. A suíte que quer ver o comunicado passa
 *   `suppressInAutomation = false` (ou roda sem a marca). Nada é gravado como visto: com o modo
 *   desligado, o comunicado aparece normalmente.
 *
 * ```kotlin
 * AnnouncementDialogHost(announcements = avisos, ready = usuarioLogado)
 * ```
 *
 * @param suppressInAutomation `false` deixa o modal fechável abrir também em build de teste.
 * @param onEvent estatística (2.335.0): [AnnouncementEvent.SHOWN] quando o modal de um comunicado abre
 *   de fato, [AnnouncementEvent.ACTION_CLICK], [AnnouncementEvent.DISMISS_GOT_IT] e
 *   [AnnouncementEvent.DISMISS_CLOSE] (X, voltar, toque fora). O fechamento que segue a ação não vira
 *   evento próprio.
 */
@Composable
fun AnnouncementDialogHost(
    announcements: List<Announcement>,
    ready: Boolean = true,
    suppressed: Boolean = false,
    openDelay: Duration = DEFAULT_ANNOUNCEMENT_DIALOG_OPEN_DELAY,
    store: AnnouncementStore = rememberDefaultAnnouncementStore(),
    clock: Clock = Clock.System,
    session: AnnouncementDialogSession = AnnouncementDialogSession.Shared,
    maxPerSession: Int = DEFAULT_ANNOUNCEMENT_DIALOGS_PER_SESSION,
    onAction: (Announcement, AnnouncementAction) -> Unit = { _, action -> openAnnouncementAction(action) },
    texts: AnnouncementDialogTexts = rememberAnnouncementDialogTexts(),
    suppressInAutomation: Boolean = true,
    onEvent: AnnouncementEventListener? = null,
) {
    val state = rememberAnnouncementDialogState(announcements, store, clock, session, maxPerSession)
    AnnouncementDialogHost(state, ready, suppressed, openDelay, onAction, texts, suppressInAutomation, onEvent)
}

/**
 * O mesmo [AnnouncementDialogHost], sobre um [AnnouncementDialogState] que a tela já tem.
 *
 * @param suppressInAutomation `false` deixa o modal fechável abrir também em build de teste.
 * @param onEvent estatística (2.335.0) — ver a outra sobrecarga.
 */
@Composable
fun AnnouncementDialogHost(
    state: AnnouncementDialogState,
    ready: Boolean = true,
    suppressed: Boolean = false,
    openDelay: Duration = DEFAULT_ANNOUNCEMENT_DIALOG_OPEN_DELAY,
    onAction: (Announcement, AnnouncementAction) -> Unit = { _, action -> openAnnouncementAction(action) },
    texts: AnnouncementDialogTexts = rememberAnnouncementDialogTexts(),
    suppressInAutomation: Boolean = true,
    onEvent: AnnouncementEventListener? = null,
) {
    val ui = state.ui
    val current = ui.current
    val blocking = current != null && !ui.dismissible
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var allowed by remember { mutableStateOf(false) }
    // Lido a cada composição: o AutomationMode já guarda a leitura da marca por alguns segundos.
    val automationMuted = suppressInAutomation && current != null && AutomationMode.suppressesAutomaticPrompts
    val canOpen = announcementDialogCanOpen(
        hasCurrent = current != null,
        blocking = blocking,
        ready = ready,
        suppressed = suppressed,
        imeVisible = imeVisible,
        automationMuted = automationMuted,
    )

    LaunchedEffect(canOpen, blocking) {
        if (canOpen && !allowed) {
            if (!blocking) delay(openDelay)
            allowed = true
        }
    }

    val show = allowed && current != null && (blocking || (!suppressed && !automationMuted))
    val listener by rememberUpdatedState(onEvent)
    // Uma vez por comunicado que de fato abre (a chave é o id do da vez, não cada recomposição).
    val opened = if (show) current else null
    LaunchedEffect(opened?.id) {
        if (opened != null) listener?.onEvent(opened, AnnouncementEvent.SHOWN)
    }
    // O último conteúdo exibido — é ele que anima a SAÍDA depois que a fila esvazia.
    val lastShown = remember { arrayOfNulls<AnnouncementDialogUiState>(1) }
    if (show) lastShown[0] = ui
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = show
    val closed = !show && visibleState.isIdle && !visibleState.currentState
    LaunchedEffect(closed, current == null) {
        // Fechou de vez: a próxima chegada espera de novo o momento certo (pronto, atraso, teclado).
        if (closed && current == null) allowed = false
    }
    if (closed) return
    val shown = if (show) ui else lastShown[0] ?: return
    AnnouncementDialogWindow(
        visibleState = visibleState,
        ui = shown,
        onDismiss = { reason ->
            shown.current?.let { a ->
                reason.toEvent()?.let { e -> listener?.onEvent(a, e) }
                state.dismiss(a.id)
            }
        },
        onAction = { action ->
            shown.current?.let { a ->
                listener?.onEvent(a, AnnouncementEvent.ACTION_CLICK)
                onAction(a, action)
            }
        },
        texts = texts,
    )
}

/**
 * O modal pode abrir agora? A manutenção que bloqueia abre sempre; o fechável espera o app pronto,
 * sem [suppressed], sem teclado e fora do modo automação ([automationMuted], 2.310.0).
 */
internal fun announcementDialogCanOpen(
    hasCurrent: Boolean,
    blocking: Boolean,
    ready: Boolean,
    suppressed: Boolean,
    imeVisible: Boolean,
    automationMuted: Boolean,
): Boolean = hasCurrent && (blocking || (ready && !suppressed && !imeVisible && !automationMuted))

/** Espera default entre o app ficar pronto e o modal abrir. */
val DEFAULT_ANNOUNCEMENT_DIALOG_OPEN_DELAY: Duration = 800.milliseconds

private val CARD_WIDTH = 440.dp
private val LANDSCAPE_CARD_WIDTH = 640.dp
private val CARD_MAX_HEIGHT = 760.dp
private val NO_PHOTO_MIN_HEIGHT = 400.dp
private val COMPACT_MAX_WIDTH = 600.dp
private val LANDSCAPE_MAX_HEIGHT = 500.dp
private const val DIALOG_CHIP_ALPHA = 0.92f
private const val SKELETON_SHORT_DAY = "EEEdMMM"
private const val SKELETON_SHORT_DAY_YEAR = "EEEdMMMy"
