package br.com.codecacto.kmplib.ui.tour

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize

/**
 * Um passo do tour guiado: destaca o elemento marcado com `Modifier.tourTarget(targetId)` e mostra
 * um balão com [title] e [description].
 *
 * - [targetId] `null` = passo sem alvo: o balão aparece **centralizado** sobre a tela escurecida
 *   (boas-vindas, encerramento).
 * - Alvo que **não está na tela** quando o tour começa (item que o perfil esconde, aba que não
 *   existe naquele tamanho de tela) é **pulado sozinho** — e não entra na contagem "N de M".
 *
 * Os textos são do app (compose-resources dele, no idioma do aparelho); a lib só traduz os botões.
 */
@Immutable
data class TourStep(
    val targetId: String?,
    val title: String,
    val description: String,
)

/** Como o tour terminou — o que o app recebe em `rememberProductTourState(onEnd = …)`. */
enum class TourEndReason {
    /** A pessoa chegou ao último passo e tocou em "Começar a usar". */
    COMPLETED,

    /** "Pular" ou o voltar do sistema. */
    SKIPPED,

    /** Nenhum passo tinha alvo na tela (e nenhum era sem alvo): o tour nem abriu. */
    NOTHING_TO_SHOW,
}

/**
 * Os passos que de fato entram no tour: os sem alvo e os cujo alvo está visível agora.
 * A ordem é preservada. Regra pura — o `ProductTourState` a aplica no início do tour.
 */
internal fun resolveTourSteps(
    steps: List<TourStep>,
    isTargetVisible: (String) -> Boolean,
): List<TourStep> = steps.filter { step -> step.targetId == null || isTargetVisible(step.targetId) }

/** De que lado do alvo o balão ficou. */
internal enum class TourBalloonSide { ABOVE, BELOW, CENTER }

/**
 * Onde desenhar o balão, em pixels do host.
 *
 * @property offset canto superior esquerdo do balão.
 * @property arrowCenterX centro da seta, relativo à borda esquerda do balão; `null` = sem seta
 *   (passo centralizado, ou balão que precisou cobrir o alvo por falta de espaço).
 */
internal data class TourBalloonPlacement(
    val offset: IntOffset,
    val side: TourBalloonSide,
    val arrowCenterX: Int?,
)

/**
 * Decide a posição do balão — regra pura, testada sem tela.
 *
 * - Sem alvo: centralizado em [bounds].
 * - Com alvo: **abaixo** se couber; senão **acima** se couber; senão do lado com mais espaço,
 *   preso dentro de [bounds] (pode cobrir parte do alvo, e então perde a seta).
 * - Na horizontal, centrado no alvo e preso entre as margens — nunca sai da tela. Alvo na barra de
 *   baixo (aba, banner) cai naturalmente no "acima".
 *
 * @param target área destacada (já com o respiro), em pixels do host; `null` = passo sem alvo.
 * @param balloon tamanho medido do balão.
 * @param bounds área segura do host (sem barra de status, recorte da câmera e barra de gestos).
 * @param gap distância entre o destaque e o balão (inclui a seta).
 * @param margin margem mínima entre o balão e as bordas de [bounds].
 * @param arrowInset distância mínima entre o centro da seta e as quinas do balão.
 */
internal fun placeTourBalloon(
    target: IntRect?,
    balloon: IntSize,
    bounds: IntRect,
    gap: Int,
    margin: Int,
    arrowInset: Int,
): TourBalloonPlacement {
    val left = bounds.left + margin
    val right = bounds.right - margin
    val top = bounds.top + margin
    val bottom = bounds.bottom - margin
    val w = balloon.width
    val h = balloon.height

    fun clampX(x: Int): Int = if (w >= right - left) left else x.coerceIn(left, right - w)
    fun clampY(y: Int): Int = if (h >= bottom - top) top else y.coerceIn(top, bottom - h)

    if (target == null) {
        val x = clampX(left + (right - left - w) / 2)
        val y = clampY(top + (bottom - top - h) / 2)
        return TourBalloonPlacement(IntOffset(x, y), TourBalloonSide.CENTER, arrowCenterX = null)
    }

    val x = clampX(target.center.x - w / 2)
    val belowY = target.bottom + gap
    val aboveY = target.top - gap - h
    val fitsBelow = belowY + h <= bottom
    val fitsAbove = aboveY >= top

    val side: TourBalloonSide
    val y: Int
    when {
        fitsBelow -> { side = TourBalloonSide.BELOW; y = belowY }
        fitsAbove -> { side = TourBalloonSide.ABOVE; y = aboveY }
        else -> {
            val spaceBelow = bottom - target.bottom
            val spaceAbove = target.top - top
            if (spaceBelow >= spaceAbove) {
                side = TourBalloonSide.BELOW; y = clampY(belowY)
            } else {
                side = TourBalloonSide.ABOVE; y = clampY(aboveY)
            }
        }
    }

    // A seta só faz sentido se o balão de fato ficou do lado dito, sem cobrir o alvo.
    val clearOfTarget = when (side) {
        TourBalloonSide.BELOW -> y >= target.bottom
        TourBalloonSide.ABOVE -> y + h <= target.top
        TourBalloonSide.CENTER -> false
    }
    val arrow = if (clearOfTarget && w > 2 * arrowInset) {
        (target.center.x - x).coerceIn(arrowInset, w - arrowInset)
    } else {
        null
    }
    return TourBalloonPlacement(IntOffset(x, y), side, arrow)
}
