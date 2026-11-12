package br.com.codecacto.kmplib.familia.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.familia.generated.resources.Res
import br.com.codecacto.kmplib.familia.generated.resources.atkinson_hyperlegible_bold
import br.com.codecacto.kmplib.familia.generated.resources.atkinson_hyperlegible_italic
import br.com.codecacto.kmplib.familia.generated.resources.atkinson_hyperlegible_regular
import br.com.codecacto.kmplib.familia.generated.resources.fraunces_variable
import org.jetbrains.compose.resources.Font

/**
 * As famílias de fonte do kit, embarcadas (SIL OFL 1.1 — as licenças vão em `files/OFL-*.txt`):
 * **Atkinson Hyperlegible** (400, 700, itálico 400) para texto e interface e **Fraunces** (variável)
 * para títulos, versículos e números.
 *
 * A Fraunces é a fonte VARIÁVEL do Google Fonts: o protótipo usa o tamanho óptico automático
 * (`opsz` = tamanho em px), então há uma família por faixa de tamanho — [frauncesText] (≈ 20–24),
 * [frauncesDisplay] (≈ 27–30) e [frauncesBig] (≈ 44) — com os pesos 400/500/600/700 do eixo `wght`.
 */
@Immutable
class FamilyFonts(
    val atkinson: FontFamily,
    val frauncesText: FontFamily,
    val frauncesDisplay: FontFamily,
    val frauncesBig: FontFamily,
)

/** Carrega as fontes do kit (uma vez por composição; o Compose guarda o recurso). */
@Composable
fun rememberFamilyFonts(): FamilyFonts {
    val atkinson = FontFamily(
        Font(Res.font.atkinson_hyperlegible_regular, FontWeight.Normal, FontStyle.Normal),
        Font(Res.font.atkinson_hyperlegible_bold, FontWeight.Bold, FontStyle.Normal),
        Font(Res.font.atkinson_hyperlegible_italic, FontWeight.Normal, FontStyle.Italic),
    )
    val text = fraunces(opticalSize = 22f)
    val display = fraunces(opticalSize = 30f)
    val big = fraunces(opticalSize = 44f)
    return remember(atkinson, text, display, big) { FamilyFonts(atkinson, text, display, big) }
}

@Composable
private fun fraunces(opticalSize: Float): FontFamily {
    val weights = listOf(400, 500, 600, 700)
    val fonts = weights.map { w ->
        Font(
            Res.font.fraunces_variable,
            FontWeight(w),
            FontStyle.Normal,
            FontVariation.Settings(FontVariation.weight(w), FontVariation.Setting("opsz", opticalSize)),
        )
    }
    return remember(opticalSize, fonts) { FontFamily(fonts) }
}

/**
 * Os estilos nomeados da escala do protótipo (`01-sistema-de-design.md` §2), já multiplicados pelo
 * tamanho da letra do app (A− A+). `base` é 17 sp × fator.
 */
@Immutable
data class FamilyTypography(
    /** `.h1` — Fraunces 600, 1,75em, linha 1,14. */
    val h1: TextStyle,
    /** `.h2` — Fraunces 600, 1,25em, linha 1,22. */
    val h2: TextStyle,
    /** `.h3` — Atkinson 700, 1em. */
    val h3: TextStyle,
    /** `.vs` (versículo) — Fraunces 500, 1,6em, linha 1,3. */
    val vs: TextStyle,
    /** `.big` (número de destaque) — Fraunces 600, 2,6em, linha 1. */
    val big: TextStyle,
    /** `.hd-t` (título do topo) — Fraunces 600, 1,3em. */
    val hdT: TextStyle,
    /** `.ey` (sobretítulo) — Atkinson 700, 0,74em, espaçamento 0,08em; CAIXA ALTA via [FamilyEyebrow]. */
    val ey: TextStyle,
    /** Texto corrido — Atkinson 400, 1em, linha 1,45. */
    val body: TextStyle,
    /** `.sm` — 0,86em. */
    val sm: TextStyle,
    /** `.xs` — 0,76em. */
    val xs: TextStyle,
    /** `.lab` (rótulo de campo) — 0,82em 700. */
    val lab: TextStyle,
    /** `.row-t` — 1em 700, linha 1,25. */
    val rowT: TextStyle,
    /** `.row-s` — 0,84em, linha 1,3. */
    val rowS: TextStyle,
    /** `.tab` (rótulo da aba) — 0,72em 700. */
    val tab: TextStyle,
    /** `.plan-p` (preço do plano) — Fraunces 600, 1,2em. */
    val planPrice: TextStyle,
    /** Botão — Atkinson 700, 1em. */
    val button: TextStyle,
    /** O tamanho-base em uso (17 sp × fator). */
    val base: TextUnit,
) {
    companion object {
        /** Base do sistema da família, antes do A− A+. */
        const val BASE_SP: Float = 17f

        /** A escala para [fonts] com o multiplicador [scale] (1, 19/17, 21/17). */
        fun create(fonts: FamilyFonts, scale: Float = 1f): FamilyTypography {
            val base = BASE_SP * scale
            fun sz(em: Float) = (base * em).sp
            fun lh(em: Float, line: Float) = (base * em * line).sp
            val a = fonts.atkinson
            return FamilyTypography(
                h1 = TextStyle(fontFamily = fonts.frauncesDisplay, fontWeight = FontWeight.SemiBold, fontSize = sz(1.75f), lineHeight = lh(1.75f, 1.14f), letterSpacing = (-0.015f).em),
                h2 = TextStyle(fontFamily = fonts.frauncesText, fontWeight = FontWeight.SemiBold, fontSize = sz(1.25f), lineHeight = lh(1.25f, 1.22f)),
                h3 = TextStyle(fontFamily = a, fontWeight = FontWeight.Bold, fontSize = sz(1f), lineHeight = lh(1f, 1.3f)),
                vs = TextStyle(fontFamily = fonts.frauncesDisplay, fontWeight = FontWeight.Medium, fontSize = sz(1.6f), lineHeight = lh(1.6f, 1.3f)),
                big = TextStyle(fontFamily = fonts.frauncesBig, fontWeight = FontWeight.SemiBold, fontSize = sz(2.6f), lineHeight = lh(2.6f, 1f), letterSpacing = (-0.02f).em),
                hdT = TextStyle(fontFamily = fonts.frauncesText, fontWeight = FontWeight.SemiBold, fontSize = sz(1.3f), lineHeight = lh(1.3f, 1.2f), letterSpacing = (-0.01f).em),
                ey = TextStyle(fontFamily = a, fontWeight = FontWeight.Bold, fontSize = sz(0.74f), lineHeight = lh(0.74f, 1.3f), letterSpacing = 0.08f.em),
                body = TextStyle(fontFamily = a, fontWeight = FontWeight.Normal, fontSize = sz(1f), lineHeight = lh(1f, 1.45f)),
                sm = TextStyle(fontFamily = a, fontWeight = FontWeight.Normal, fontSize = sz(0.86f), lineHeight = lh(0.86f, 1.4f)),
                xs = TextStyle(fontFamily = a, fontWeight = FontWeight.Normal, fontSize = sz(0.76f), lineHeight = lh(0.76f, 1.4f)),
                lab = TextStyle(fontFamily = a, fontWeight = FontWeight.Bold, fontSize = sz(0.82f), lineHeight = lh(0.82f, 1.3f)),
                rowT = TextStyle(fontFamily = a, fontWeight = FontWeight.Bold, fontSize = sz(1f), lineHeight = lh(1f, 1.25f)),
                rowS = TextStyle(fontFamily = a, fontWeight = FontWeight.Normal, fontSize = sz(0.84f), lineHeight = lh(0.84f, 1.3f)),
                tab = TextStyle(fontFamily = a, fontWeight = FontWeight.Bold, fontSize = sz(0.72f), lineHeight = lh(0.72f, 1.2f)),
                planPrice = TextStyle(fontFamily = fonts.frauncesText, fontWeight = FontWeight.SemiBold, fontSize = sz(1.2f), lineHeight = lh(1.2f, 1.2f)),
                button = TextStyle(fontFamily = a, fontWeight = FontWeight.Bold, fontSize = sz(1f), lineHeight = lh(1f, 1.2f)),
                base = base.sp,
            )
        }
    }
}
