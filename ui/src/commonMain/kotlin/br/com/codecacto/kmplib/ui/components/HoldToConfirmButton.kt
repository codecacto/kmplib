package br.com.codecacto.kmplib.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_cancel
import br.com.codecacto.kmplib.generated.resources.kmplib_hold_to_confirm_a11y_action
import br.com.codecacto.kmplib.generated.resources.kmplib_hold_to_confirm_a11y_hint
import br.com.codecacto.kmplib.generated.resources.kmplib_hold_to_confirm_dialog_message
import br.com.codecacto.kmplib.generated.resources.kmplib_hold_to_confirm_hint
import br.com.codecacto.kmplib.generated.resources.kmplib_hold_to_confirm_hold_action
import br.com.codecacto.kmplib.generated.resources.kmplib_hold_to_confirm_holding
import br.com.codecacto.kmplib.platform.haptics.HapticPlayer
import br.com.codecacto.kmplib.platform.haptics.HapticUsage
import br.com.codecacto.kmplib.platform.haptics.VibrationPattern
import br.com.codecacto.kmplib.platform.haptics.rememberHaptics
import br.com.codecacto.kmplib.platform.motion.LocalReduceMotion
import br.com.codecacto.kmplib.ui.locale.kmpStringResource
import br.com.codecacto.kmplib.ui.theme.AppColors
import br.com.codecacto.kmplib.ui.theme.ColorContrast
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/** A cor do [HoldToConfirmButton] (2.315.0). [DANGER] é o default: ação crítica costuma ser destrutiva. */
enum class HoldToConfirmVariant {
    /** Vermelho do tema (`colorScheme.error`): pânico, excluir, encerrar. */
    DANGER,

    /** Âmbar (`AppColors.warning`), texto escolhido por contraste WCAG. */
    WARNING,

    /** Cor de marca (`colorScheme.primary`): ação importante, não destrutiva. */
    PRIMARY,
}

/** Cores do [HoldToConfirmButton]. Monte com [HoldToConfirmDefaults.colors]. */
@Immutable
data class HoldToConfirmColors(
    val container: Color,
    val content: Color,
    /** A faixa que enche enquanto se segura (desenhada sobre [container]). */
    val progress: Color,
    val disabledContainer: Color,
    val disabledContent: Color,
)

/** Textos do [HoldToConfirmButton]; [rememberHoldToConfirmTexts] devolve no idioma da tela (4 idiomas). */
@Immutable
data class HoldToConfirmTexts(
    /** Linha de apoio parada ("Toque e segure"). */
    val hint: String = "Toque e segure",
    /** Linha de apoio e estado para o leitor de tela enquanto se segura. */
    val holding: String = "Continue segurando…",
    /** Lido pelo TalkBack/VoiceOver depois do rótulo: o gesto que o leitor de tela repassa. */
    val accessibilityHint: String = "Toque duas vezes e segure para confirmar",
    /** Rótulo da ação acessível ALTERNATIVA (toque duplo simples), que abre a confirmação. */
    val accessibleAction: String = "Confirmar sem segurar",
    /** Rótulo da ação de toque longo do serviço de acessibilidade (= segurar). */
    val holdAction: String = "Confirmar",
    /** Mensagem do diálogo da ação alternativa (o título é o rótulo do botão). */
    val dialogMessage: String = "Confirme para executar esta ação agora.",
    val cancel: String = "Cancelar",
)

@Composable
fun rememberHoldToConfirmTexts(): HoldToConfirmTexts {
    val hint = kmpStringResource(Res.string.kmplib_hold_to_confirm_hint)
    val holding = kmpStringResource(Res.string.kmplib_hold_to_confirm_holding)
    val a11yHint = kmpStringResource(Res.string.kmplib_hold_to_confirm_a11y_hint)
    val a11yAction = kmpStringResource(Res.string.kmplib_hold_to_confirm_a11y_action)
    val holdAction = kmpStringResource(Res.string.kmplib_hold_to_confirm_hold_action)
    val message = kmpStringResource(Res.string.kmplib_hold_to_confirm_dialog_message)
    val cancel = kmpStringResource(Res.string.kmplib_cancel)
    return remember(hint, holding, a11yHint, a11yAction, holdAction, message, cancel) {
        HoldToConfirmTexts(hint, holding, a11yHint, a11yAction, holdAction, message, cancel)
    }
}

object HoldToConfirmDefaults {
    /** 2 s: longo o bastante para não ser esbarrão, curto o bastante para uma emergência. */
    const val DURATION_MILLIS: Long = 2_000L
    const val MIN_DURATION_MILLIS: Long = 300L
    const val MAX_DURATION_MILLIS: Long = 10_000L

    /** Quatro degraus: um toque curto a 25 %, 50 % e 75 %; o 4º é a confirmação. */
    const val HAPTIC_STEPS: Int = 4
    const val MAX_HAPTIC_STEPS: Int = 20

    /** Alvo de toque generoso: ação crítica não pode exigir pontaria. */
    val MinHeight: Dp = 64.dp
    val Shape: Shape = RoundedCornerShape(16.dp)

    /** Háptico de cada degrau intermediário. */
    val StepHaptic: VibrationPattern = VibrationPattern.Tick

    /** Háptico da conclusão. Para alarme/pânico, passe `VibrationPattern.Alert`. */
    val ConfirmHaptic: VibrationPattern = VibrationPattern.Confirm

    @Composable
    fun colors(variant: HoldToConfirmVariant = HoldToConfirmVariant.DANGER): HoldToConfirmColors {
        val scheme = MaterialTheme.colorScheme
        val (container, content) = when (variant) {
            HoldToConfirmVariant.DANGER -> scheme.error to scheme.onError
            HoldToConfirmVariant.PRIMARY -> scheme.primary to scheme.onPrimary
            HoldToConfirmVariant.WARNING -> AppColors.current.warning.let { it to ColorContrast.pickOnColor(it) }
        }
        return HoldToConfirmColors(
            container = container,
            content = content,
            progress = content.copy(alpha = 0.28f),
            disabledContainer = scheme.surfaceVariant,
            disabledContent = scheme.onSurfaceVariant,
        )
    }
}

/** Ids para o Maestro. O diálogo da ação alternativa usa os `DialogTestTags` (`dialogo-*`). */
object HoldToConfirmTestTags {
    const val BUTTON = "segurar-confirmar"
}

/**
 * **Ação crítica confirmada por PRESSIONAR E SEGURAR** (2.315.0) — pânico, encerrar turno, excluir
 * em massa: um toque, um esbarrão no bolso ou a rolagem da tela não chegam lá.
 *
 * - A faixa enche da borda inicial (respeita RTL) enquanto o dedo fica, por [holdDurationMillis];
 *   um háptico curto ([stepHaptic]) a cada um dos [hapticSteps] degraus e o de conclusão
 *   ([confirmHaptic]) ao confirmar. Soltar antes — ou arrastar para fora, ou a rolagem do pai levar
 *   o gesto — **cancela** e a faixa volta a zero (sem animação com "reduzir movimento").
 * - O tempo é monotônico e medido a cada quadro (`withFrameMillis`), nunca por soma de `delay`.
 * - [onConfirm] roda UMA vez por gesto; confirmar de novo exige soltar e segurar outra vez.
 *
 * **Acessibilidade** — segurar é difícil com TalkBack/VoiceOver, então há três caminhos:
 * 1. o leitor lê "rótulo. Toque duas vezes e segure para confirmar" e o gesto de toque duplo
 *    sustentado (que os dois leitores repassam como toque mantido) funciona igual;
 * 2. o serviço que traduz o gesto em ação de toque longo (`onLongClick`) confirma direto — é o
 *    mesmo gesto deliberado;
 * 3. o toque duplo SIMPLES (ação de clique, rótulo [HoldToConfirmTexts.accessibleAction]) abre um
 *    diálogo de confirmação — a alternativa para quem não consegue segurar
 *    ([accessibleConfirmation] = `false` a desliga).
 *
 * @param stepHaptic `null` = sem háptico de degrau. @param confirmHaptic `null` = sem háptico final.
 * @param hapticUsage `TOUCH` segue "vibração ao tocar" do sistema; pânico/alarme pode usar `ALARM`.
 * @param supportingText linha de apoio fixa no lugar de "Toque e segure" (o "Continue segurando…"
 *   aparece igual durante o gesto).
 */
@Composable
fun HoldToConfirmButton(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    holdDurationMillis: Long = HoldToConfirmDefaults.DURATION_MILLIS,
    variant: HoldToConfirmVariant = HoldToConfirmVariant.DANGER,
    colors: HoldToConfirmColors = HoldToConfirmDefaults.colors(variant),
    icon: ImageVector? = null,
    supportingText: String? = null,
    hapticSteps: Int = HoldToConfirmDefaults.HAPTIC_STEPS,
    stepHaptic: VibrationPattern? = HoldToConfirmDefaults.StepHaptic,
    confirmHaptic: VibrationPattern? = HoldToConfirmDefaults.ConfirmHaptic,
    hapticUsage: HapticUsage = HapticUsage.TOUCH,
    haptics: HapticPlayer = rememberHaptics(),
    accessibleConfirmation: Boolean = true,
    shape: Shape = HoldToConfirmDefaults.Shape,
    texts: HoldToConfirmTexts = rememberHoldToConfirmTexts(),
    testTag: String = HoldToConfirmTestTags.BUTTON,
) {
    val gesture = remember(holdDurationMillis, hapticSteps) { HoldToConfirmGesture(holdDurationMillis, hapticSteps) }
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val isEnabled by rememberUpdatedState(enabled)
    val currentStepHaptic by rememberUpdatedState(stepHaptic)
    val currentConfirmHaptic by rememberUpdatedState(confirmHaptic)
    val currentUsage by rememberUpdatedState(hapticUsage)
    val currentHaptics by rememberUpdatedState(haptics)
    val reduceMotion by rememberUpdatedState(LocalReduceMotion.current)

    fun confirmNow() {
        currentConfirmHaptic?.let { currentHaptics.vibrate(it, currentUsage) }
        currentOnConfirm()
    }

    val container = if (enabled) colors.container else colors.disabledContainer
    val content = if (enabled) colors.content else colors.disabledContent
    val fill = colors.progress
    val description = holdToConfirmContentDescription(text, supportingText, texts.accessibilityHint)

    Box(
        modifier
            .heightIn(min = HoldToConfirmDefaults.MinHeight)
            .clip(shape)
            .background(container)
            .drawBehind {
                val w = size.width * progress.value
                if (w > 0f) {
                    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f
                    drawRect(fill, topLeft = Offset(x, 0f), size = Size(w, size.height))
                }
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
                if (holding) stateDescription = texts.holding
                if (!enabled) {
                    disabled()
                } else {
                    onLongClick(label = texts.holdAction) {
                        confirmNow()
                        true
                    }
                    if (accessibleConfirmation) {
                        onClick(label = texts.accessibleAction) {
                            showDialog = true
                            true
                        }
                    }
                }
            }
            .pointerInput(gesture) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!isEnabled) return@awaitEachGesture
                    down.consume()
                    holding = true
                    val start = TimeSource.Monotonic.markNow()
                    val loop = scope.launch {
                        var lastStep = 0
                        while (isActive) {
                            withFrameMillis { }
                            val elapsed = start.elapsedNow().inWholeMilliseconds
                            when (val step = gesture.advance(elapsed, lastStep, pressed = true)) {
                                is HoldToConfirmStep.Holding -> {
                                    progress.snapTo(step.progress)
                                    if (step.stepHaptic) {
                                        lastStep = step.step
                                        currentStepHaptic?.let { currentHaptics.vibrate(it, currentUsage) }
                                    }
                                }
                                HoldToConfirmStep.Confirmed -> {
                                    progress.snapTo(1f)
                                    confirmNow()
                                    return@launch
                                }
                                HoldToConfirmStep.Cancelled -> return@launch
                            }
                        }
                    }
                    // Soltou, saiu do botão ou a rolagem levou o gesto: para o laço e zera a faixa.
                    waitForUpOrCancellation()
                    loop.cancel()
                    holding = false
                    scope.launch {
                        if (reduceMotion) progress.snapTo(0f) else progress.animateTo(0f, tween(RESET_MILLIS))
                    }
                }
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
            Column {
                Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = content)
                Text(
                    if (holding) texts.holding else supportingText ?: texts.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = content,
                )
            }
        }
    }

    if (accessibleConfirmation) {
        ConfirmationDialog(
            show = showDialog && enabled,
            title = text,
            message = texts.dialogMessage,
            confirmText = text,
            cancelText = texts.cancel,
            onConfirm = {
                showDialog = false
                confirmNow()
            },
            onDismiss = { showDialog = false },
            primaryColor = colors.container,
            primaryContentColor = colors.content,
        )
    }
}

private const val RESET_MILLIS = 180
