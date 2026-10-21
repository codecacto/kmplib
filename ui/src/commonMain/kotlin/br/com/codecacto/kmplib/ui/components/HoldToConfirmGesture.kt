package br.com.codecacto.kmplib.ui.components

/**
 * A regra do **pressione e segure para confirmar** (2.315.0), pura e testável — o
 * [HoldToConfirmButton] só mede o tempo (relógio monotônico, quadro a quadro) e pergunta aqui o que
 * fazer.
 *
 * - O caminho até confirmar leva [durationMillis]; soltar antes é [HoldToConfirmStep.Cancelled].
 * - O caminho é dividido em [hapticSteps] degraus iguais; a cada degrau **intermediário** alcançado,
 *   [HoldToConfirmStep.Holding.stepHaptic] pede o toque curto (quem segura SENTE que está indo, sem
 *   olhar a tela). O último degrau é a própria confirmação, que tem háptico próprio.
 *
 * Origem: o botão de pânico do RondAki (`ConfirmacaoPorSegurar`), promovido à lib.
 */
class HoldToConfirmGesture(
    val durationMillis: Long = HoldToConfirmDefaults.DURATION_MILLIS,
    val hapticSteps: Int = HoldToConfirmDefaults.HAPTIC_STEPS,
) {
    init {
        require(durationMillis in HoldToConfirmDefaults.MIN_DURATION_MILLIS..HoldToConfirmDefaults.MAX_DURATION_MILLIS) {
            "durationMillis precisa estar entre ${HoldToConfirmDefaults.MIN_DURATION_MILLIS} e " +
                "${HoldToConfirmDefaults.MAX_DURATION_MILLIS} ms (recebeu $durationMillis)"
        }
        require(hapticSteps in 0..HoldToConfirmDefaults.MAX_HAPTIC_STEPS) {
            "hapticSteps precisa estar entre 0 e ${HoldToConfirmDefaults.MAX_HAPTIC_STEPS} (recebeu $hapticSteps)"
        }
    }

    /** Fração 0..1 do caminho, com o dedo pressionado há [elapsedMillis]. Tempo negativo conta 0. */
    fun progress(elapsedMillis: Long): Float =
        (elapsedMillis.coerceAtLeast(0L).toDouble() / durationMillis).coerceIn(0.0, 1.0).toFloat()

    /** Segurou o bastante. */
    fun isConfirmed(elapsedMillis: Long): Boolean = elapsedMillis >= durationMillis

    /** Degrau alcançado em [progress] (0 = nenhum; [hapticSteps] = completo). Sem degraus, sempre 0. */
    fun stepAt(progress: Float): Int =
        if (hapticSteps == 0) 0 else (progress.coerceIn(0f, 1f) * hapticSteps).toInt().coerceAtMost(hapticSteps)

    /**
     * Um passo do gesto. [lastStep] é o degrau já sentido (comece em 0); [pressed] = o dedo continua.
     * Soltar depois do fim conta como confirmado (o botão normalmente já confirmou no quadro em que o
     * tempo venceu, e não chama de novo).
     */
    fun advance(elapsedMillis: Long, lastStep: Int, pressed: Boolean): HoldToConfirmStep {
        if (isConfirmed(elapsedMillis)) return HoldToConfirmStep.Confirmed
        if (!pressed) return HoldToConfirmStep.Cancelled
        val p = progress(elapsedMillis)
        val step = stepAt(p)
        return HoldToConfirmStep.Holding(progress = p, step = step, stepHaptic = step > lastStep && step < hapticSteps)
    }
}

/** O que o botão faz agora — ver [HoldToConfirmGesture.advance]. */
sealed interface HoldToConfirmStep {
    /** Ainda segurando: desenhe [progress]; [stepHaptic] = tocar o háptico de degrau e guardar [step]. */
    data class Holding(val progress: Float, val step: Int, val stepHaptic: Boolean) : HoldToConfirmStep

    /** Segurou o tempo todo: háptico de conclusão + ação. */
    data object Confirmed : HoldToConfirmStep

    /** Soltou (ou o dedo saiu do botão / a rolagem levou o gesto) antes do fim: zera, sem ação. */
    data object Cancelled : HoldToConfirmStep
}

/**
 * O que o leitor de tela lê no [HoldToConfirmButton]: o rótulo, a linha de apoio (quando difere da
 * dica) e a dica do gesto ("toque duas vezes e segure…"). Pura, para o teste.
 */
internal fun holdToConfirmContentDescription(text: String, supportingText: String?, accessibilityHint: String): String =
    listOfNotNull(text, supportingText?.takeIf { it.isNotBlank() }, accessibilityHint)
        .joinToString(". ")
