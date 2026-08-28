package br.com.codecacto.kmplib.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Escala de fonte **global** de acessibilidade, aplicada à árvore inteira pelo [AppTheme]
 * (`fontScale`). Diferente do `ReaderFontSize` (que escala só o corpo do leitor `ui/reader`), este
 * degrau multiplica toda a [AppTypography].
 *
 * Degraus (referência: `ReaderFontSize`), do menor ao maior:
 * - [Small] 0.9× — texto compacto.
 * - [Medium] 1.0× — padrão (comportamento atual, nada muda).
 * - [Large] 1.3× — fonte grande.
 * - [ExtraLarge] 1.6× — baixa visão.
 *
 * Use [next]/[previous] para controles P/M/G/GG e [scale] para persistir a preferência (o app cuida
 * da persistência, ex.: `AppPreferences`), alimentando `AppTheme(fontScale = ...)`.
 */
enum class AppFontScale(val scale: Float) {
    Small(0.9f),
    Medium(1.0f),
    Large(1.3f),
    ExtraLarge(1.6f);

    /** Próximo degrau maior; satura em [ExtraLarge]. */
    fun next(): AppFontScale = entries.getOrElse(ordinal + 1) { this }

    /** Degrau anterior menor; satura em [Small]. */
    fun previous(): AppFontScale = entries.getOrElse(ordinal - 1) { this }

    companion object {
        /** Resolve um fator livre para o degrau mais próximo. */
        fun fromScale(scale: Float): AppFontScale =
            entries.minBy { kotlin.math.abs(it.scale - scale) }
    }
}

/** Fator mínimo aceito de escala de fonte. */
const val MIN_FONT_SCALE: Float = 0.8f

/** Fator máximo aceito de escala de fonte. */
const val MAX_FONT_SCALE: Float = 2.0f

/**
 * Clampa um fator de escala de fonte em [MIN_FONT_SCALE]..[MAX_FONT_SCALE]. Entrada não-finita ou
 * `<= 0` → `1f`. Regra pura, testável (commonTest).
 */
fun clampFontScale(scale: Float): Float =
    if (scale.isNaN() || scale.isInfinite() || scale <= 0f) 1f
    else scale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)

/**
 * Fator de escala de fonte efetivo em vigor na árvore (fornecido pelo [AppTheme]). Default `1f`
 * (sem escala) para consumidores que não passam `fontScale` — nada quebra.
 */
val LocalFontScale = staticCompositionLocalOf { 1f }
