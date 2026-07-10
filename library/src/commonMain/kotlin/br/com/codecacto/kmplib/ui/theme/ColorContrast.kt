package br.com.codecacto.kmplib.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * Matemática de contraste **WCAG 2.x** pura (sem Compose runtime), espelhando 1:1 o par web
 * (`Meu Barbeiro/portal/src/lib/agenda/status-contrast.ts`): luminância relativa, razão de contraste
 * e **composição de alpha** (`compositeOver`) — porque a cor realmente exibida de um preenchimento
 * semitransparente (ex.: `primaryContainer` = primária @20% sobre a superfície) é a MISTURA, não a
 * cor crua. É contra essa mistura que o texto precisa contrastar.
 *
 * É a base do contraste ser **garantido** (não prometido) quando a lib deriva as cores `on*` de uma
 * superfície customizada — ver [AppSurfaceColors] e [pickOnColor].
 *
 * Alvos WCAG: texto normal ≥ [AA_TEXT] (4.5:1); elemento gráfico / texto grande ≥ [AA_GRAPHIC] (3:1).
 */
object ColorContrast {

    /** Razão mínima WCAG AA para texto normal. */
    const val AA_TEXT: Double = 4.5

    /** Razão mínima WCAG AA para elemento gráfico (borda, ícone) / texto grande. */
    const val AA_GRAPHIC: Double = 3.0

    /** Lineariza um canal sRGB (0..1) conforme a curva WCAG. */
    private fun channelLuminance(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    /**
     * Luminância relativa WCAG (0..1) de uma cor **opaca**. O canal alpha é ignorado — se a cor tiver
     * transparência, componha antes com [compositeOver].
     */
    fun relativeLuminance(color: Color): Double =
        0.2126 * channelLuminance(color.red) +
            0.7152 * channelLuminance(color.green) +
            0.0722 * channelLuminance(color.blue)

    /** Razão de contraste WCAG (1..21) entre duas cores **opacas**. */
    fun contrastRatio(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /**
     * Compõe [foreground] à opacidade [alpha] (0..1) sobre [background] (source-over) → a cor **opaca**
     * de fato exibida. Se [foreground] já carrega alpha próprio, ele é multiplicado por [alpha].
     */
    fun compositeOver(foreground: Color, alpha: Float, background: Color): Color {
        val a = (alpha * foreground.alpha).coerceIn(0f, 1f)
        return Color(
            red = foreground.red * a + background.red * (1 - a),
            green = foreground.green * a + background.green * (1 - a),
            blue = foreground.blue * a + background.blue * (1 - a),
            alpha = 1f
        )
    }

    /**
     * Escolhe, entre [light] e [dark], a cor de **texto/ícone** com MAIOR contraste sobre [background]
     * — garantindo a melhor legibilidade possível dado o par de candidatos. É o que a lib usa para
     * derivar `onBackground`/`onSurface` quando o app não os informa: se alguém passar um `background`
     * claro, o `on*` sai escuro **automaticamente**, nunca "claro sobre claro".
     *
     * @param background superfície onde o conteúdo aparece (deve ser opaca; componha antes se preciso).
     * @param light candidato claro (default quase-branco).
     * @param dark candidato escuro (default quase-preto).
     */
    fun pickOnColor(
        background: Color,
        light: Color = Color(0xFFFAFAFA),
        dark: Color = Color(0xFF0A0A0A)
    ): Color =
        if (contrastRatio(light, background) >= contrastRatio(dark, background)) light else dark

    /** `true` se [foreground] atinge o alvo de **texto** (≥ [AA_TEXT]) sobre [background] opaco. */
    fun meetsTextContrast(foreground: Color, background: Color): Boolean =
        contrastRatio(foreground, background) >= AA_TEXT

    /** `true` se [foreground] atinge o alvo **gráfico** (≥ [AA_GRAPHIC]) sobre [background] opaco. */
    fun meetsGraphicContrast(foreground: Color, background: Color): Boolean =
        contrastRatio(foreground, background) >= AA_GRAPHIC
}

/**
 * Interpola linearmente entre [this] e [other] em [fraction] (0..1), no espaço sRGB, resultado
 * **opaco**. Usado para derivar a escala de elevação de superfícies (`surfaceContainer*`) entre o
 * fundo e a superfície mais alta, mantendo a família de tons coesa (ex.: um preto que sobe em degraus
 * quase-pretos, não um cinza-roxo do baseline Material).
 */
internal fun Color.lerpTo(other: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = red + (other.red - red) * t,
        green = green + (other.green - green) * t,
        blue = blue + (other.blue - blue) * t,
        alpha = 1f
    )
}
