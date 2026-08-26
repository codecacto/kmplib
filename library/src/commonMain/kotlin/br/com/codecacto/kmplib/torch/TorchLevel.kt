package br.com.codecacto.kmplib.torch

import kotlin.math.roundToInt

/**
 * **Conversão entre a intensidade que o app manipula e a que o hardware entende.**
 *
 * O app trabalha sempre com uma **fração de 0f a 1f** — é o que o slider produz e o que se guarda
 * na preferência do usuário. O hardware fala duas línguas diferentes: o Android quer um **degrau
 * inteiro** (`1..FLASH_INFO_STRENGTH_MAXIMUM_LEVEL`), o iOS quer o **próprio float** (0..1). A
 * tradução mora aqui, em código comum e testado, para não ser reescrita em cada `actual` — nem em
 * cada app.
 */
object TorchLevel {

    /** Intensidade máxima. É o default de quem só quer "acender". */
    const val MAX: Float = 1f

    /**
     * Intensidade mínima **utilizável**: acender com fração 0 seria acender apagado. Toda fração
     * abaixo disto é elevada a este piso ([clamp]).
     */
    const val MIN: Float = 0.01f

    /** Prende [level] na faixa utilizável (`MIN..MAX`). `NaN` vira [MAX]. */
    fun clamp(level: Float): Float = when {
        level.isNaN() -> MAX
        level < MIN -> MIN
        level > MAX -> MAX
        else -> level
    }

    /**
     * Fração → **degrau do Android** (`1..levelCount`).
     *
     * Arredonda para o degrau mais próximo e nunca devolve `0`: no `turnOnTorchWithStrengthLevel`,
     * nível `0` é argumento inválido, não "apagado" (para apagar existe `setTorchMode(false)`).
     */
    fun toStep(level: Float, levelCount: Int): Int {
        if (levelCount <= 1) return 1
        val step = (clamp(level) * levelCount).roundToInt()
        return step.coerceIn(1, levelCount)
    }

    /** Degrau do Android → fração, para refletir na UI o nível que o SO informou. */
    fun toFraction(step: Int, levelCount: Int): Float {
        if (levelCount <= 1) return MAX
        return (step.coerceIn(1, levelCount).toFloat() / levelCount).coerceIn(MIN, MAX)
    }

    /**
     * Alinha a fração ao degrau mais próximo que o hardware consegue de fato aplicar. Faixa
     * contínua ([TorchCapabilities.CONTINUOUS]) devolve a própria fração, só limitada.
     *
     * É o que evita o estado mentir: o slider em 0,37 num LED de 5 níveis vira 0,40 no estado, que
     * é a luz que a pessoa está vendo.
     */
    fun align(level: Float, capabilities: TorchCapabilities): Float {
        val clamped = clamp(level)
        if (!capabilities.supportsIntensity) return MAX
        if (capabilities.isContinuous) return clamped
        return toFraction(toStep(clamped, capabilities.levelCount), capabilities.levelCount)
    }
}
