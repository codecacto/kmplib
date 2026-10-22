package br.com.codecacto.kmplib.inspection.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import br.com.codecacto.kmplib.ui.theme.ColorContrast

/**
 * O tom semântico de um valor do kit (§1.2 do Laudo Kit). O significado é de DOMÍNIO, não de marca:
 * verde = conforme/bom, âmbar = atenção, vermelho = problema, cinza = neutro/pendente, marca = tarefa
 * guiada e "enviando".
 */
enum class InspectionTone { GOOD, WARN, BAD, NEUTRAL, BRAND }

/** O par de cores de um tom: [fg] (texto, ícone, borda do selecionado) e [soft] (fundo). */
@Immutable
data class InspectionToneColors(val fg: Color, val soft: Color)

/**
 * O **semáforo fixo** do kit — igual nos dois produtos e **não sobrescrevível** (o produto injeta só
 * marca, fontes e neutros). Os hex são os do protótipo aprovado; `fg` sobre `soft` ≥ 4,5:1 nos dois
 * temas (conferido em `InspectionTonesTest`).
 */
object InspectionTones {
    val GoodLight = InspectionToneColors(Color(0xFF1C7A47), Color(0xFFE3F2E9))
    val WarnLight = InspectionToneColors(Color(0xFF995700), Color(0xFFFBEFD9))
    val BadLight = InspectionToneColors(Color(0xFFBD2F2A), Color(0xFFFBE5E3))
    val NeutralLight = InspectionToneColors(Color(0xFF5F6876), Color(0xFFECEEF1))

    val GoodDark = InspectionToneColors(Color(0xFF4FC48A), Color(0xFF183526))
    val WarnDark = InspectionToneColors(Color(0xFFE8A642), Color(0xFF3A2C14))
    val BadDark = InspectionToneColors(Color(0xFFF27670), Color(0xFF3D1D1C))
    val NeutralDark = InspectionToneColors(Color(0xFF9AA3B1), Color(0xFF252A33))

    /** Alfa do `brandSoft`: `17` (≈ 9 %) no claro, `2E` (≈ 18 %) no escuro. */
    const val BRAND_SOFT_ALPHA_LIGHT: Float = 0x17 / 255f
    const val BRAND_SOFT_ALPHA_DARK: Float = 0x2E / 255f

    /** O par fixo de [tone] no tema pedido. [InspectionTone.BRAND] depende da marca: ver [InspectionTokens.tone]. */
    fun fixed(tone: InspectionTone, dark: Boolean): InspectionToneColors? = when (tone) {
        InspectionTone.GOOD -> if (dark) GoodDark else GoodLight
        InspectionTone.WARN -> if (dark) WarnDark else WarnLight
        InspectionTone.BAD -> if (dark) BadDark else BadLight
        InspectionTone.NEUTRAL -> if (dark) NeutralDark else NeutralLight
        InspectionTone.BRAND -> null
    }
}

/**
 * As fontes que o kit não herda do corpo do app. **Sans** é a do tema do host (`AppTheme(fontFamily)`);
 * [mono] é a de TODA prova (data, hora, hash, GPS, contadores) e [serif] só o título do laudo e da
 * verificação. O kit não embute fonte: o produto passa as dele (Minha Vistoria e Meus Aluguéis =
 * IBM Plex Mono/Serif). Sem elas, as genéricas do sistema.
 */
@Immutable
data class InspectionFonts(
    val mono: FontFamily = FontFamily.Monospace,
    val serif: FontFamily = FontFamily.Serif,
    /** `null` = a família do corpo do tema do host. */
    val sans: FontFamily? = null,
)

/**
 * Superfícies e texto (§1.3). **Vêm do host** (decisão D1): `background`, `surface`, `surfaceVariant`
 * (afundado), `onSurface` (tinta), `onSurfaceVariant` (secundário) e `outline` (linha) do
 * `MaterialTheme` que o `AppTheme` monta — o Meus Aluguéis tem neutros violeta, a Minha Vistoria
 * frios, e o kit acompanha. [scrim] é o fundo de diálogo.
 */
@Immutable
data class InspectionNeutrals(
    val bg: Color,
    val surface: Color,
    val sunken: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val scrim: Color,
) {
    companion object {
        /** Os neutros do protótipo da Minha Vistoria, claro — o default de quem não define os seus. */
        val KitLight = InspectionNeutrals(
            bg = Color(0xFFF4F5F7), surface = Color(0xFFFFFFFF), sunken = Color(0xFFECEEF2),
            ink = Color(0xFF151A22), muted = Color(0xFF5B6474), line = Color(0xFFDADEE5),
            scrim = Color(0x7A0F1218),
        )

        /** Idem, escuro. */
        val KitDark = InspectionNeutrals(
            bg = Color(0xFF0F1217), surface = Color(0xFF171B22), sunken = Color(0xFF1F242C),
            ink = Color(0xFFE7EAEF), muted = Color(0xFF9AA3B1), line = Color(0xFF2B313B),
            scrim = Color(0x9E000000),
        )

        /** Lidos do `MaterialTheme` corrente (o do `AppTheme` do produto). */
        @Composable
        @ReadOnlyComposable
        fun fromHost(dark: Boolean): InspectionNeutrals {
            val cs = MaterialTheme.colorScheme
            return InspectionNeutrals(
                bg = cs.background,
                surface = cs.surface,
                sunken = cs.surfaceVariant,
                ink = cs.onSurface,
                muted = cs.onSurfaceVariant,
                line = cs.outline,
                scrim = if (dark) KitDark.scrim else KitLight.scrim,
            )
        }
    }
}

/**
 * Os tokens resolvidos do kit, para os componentes lerem por [InspectionTheme.tokens]. Nada aqui se
 * escolhe na tela: a marca vem do `AppTheme` do produto, o semáforo é fixo, os neutros são do host.
 */
@Immutable
data class InspectionTokens(
    val isDark: Boolean,
    /** Botão primário, link, progresso, aba ativa, foco. */
    val brand: Color,
    /** Texto/ícone sobre [brand] — o `onPrimary` do `AppTheme`, já escolhido por contraste WCAG. */
    val onBrand: Color,
    /** [brand] com alfa 9 % (claro) / 18 % (escuro): tarefa guiada, item selecionado, "Obrigatório". */
    val brandSoft: Color,
    val neutrals: InspectionNeutrals,
    val good: InspectionToneColors,
    val warn: InspectionToneColors,
    val bad: InspectionToneColors,
    val neutral: InspectionToneColors,
    val fonts: InspectionFonts,
    /** Família do corpo (a do tema do host, ou [InspectionFonts.sans]). */
    val sans: FontFamily,
) {
    val bg: Color get() = neutrals.bg
    val surface: Color get() = neutrals.surface
    val sunken: Color get() = neutrals.sunken
    val ink: Color get() = neutrals.ink
    val muted: Color get() = neutrals.muted
    val line: Color get() = neutrals.line
    val scrim: Color get() = neutrals.scrim

    /** O par de [tone]; [InspectionTone.BRAND] = ([brand], [brandSoft]). */
    fun tone(tone: InspectionTone): InspectionToneColors = when (tone) {
        InspectionTone.GOOD -> good
        InspectionTone.WARN -> warn
        InspectionTone.BAD -> bad
        InspectionTone.NEUTRAL -> neutral
        InspectionTone.BRAND -> InspectionToneColors(brand, brandSoft)
    }

    companion object {
        /** Faixa preta do carimbo sobre a foto (fixa nos dois temas: está sobre imagem). */
        val PhotoStampBackground: Color = Color(0xA8000000)

        /** Faixa grande do carimbo (web/tela cheia): preto 70 %. */
        val PhotoStampLargeBackground: Color = Color(0xB3000000)

        /** Texto sobre foto e sobre o cromo da câmera. */
        val OnPhoto: Color = Color.White

        /** Fundo fixo da câmera (independe do tema, como em toda câmera). */
        val CameraBackground: Color = Color.Black

        /** Trilha da régua de transparência na câmera. */
        val CameraTrack: Color = Color(0xFF3A3F48)

        /** Vaga tracejada da próxima foto na câmera. */
        val CameraEmptySlot: Color = Color(0xFF555B66)

        /** Página do PDF: branca nos dois temas (é papel). */
        val PaperBackground: Color = Color.White

        /** Pílula sobre o preview da câmera. */
        val PhotoPillBackground: Color = Color(0x99000000)

        /** Monta os tokens (pura — é o que os testes usam). */
        fun resolve(
            dark: Boolean,
            brand: Color,
            onBrand: Color,
            neutrals: InspectionNeutrals,
            fonts: InspectionFonts = InspectionFonts(),
            sans: FontFamily = fonts.sans ?: FontFamily.Default,
        ): InspectionTokens = InspectionTokens(
            isDark = dark,
            brand = brand,
            onBrand = onBrand,
            brandSoft = brand.copy(
                alpha = if (dark) InspectionTones.BRAND_SOFT_ALPHA_DARK else InspectionTones.BRAND_SOFT_ALPHA_LIGHT,
            ),
            neutrals = neutrals,
            good = InspectionTones.fixed(InspectionTone.GOOD, dark)!!,
            warn = InspectionTones.fixed(InspectionTone.WARN, dark)!!,
            bad = InspectionTones.fixed(InspectionTone.BAD, dark)!!,
            neutral = InspectionTones.fixed(InspectionTone.NEUTRAL, dark)!!,
            fonts = fonts,
            sans = sans,
        )
    }
}

/** `true` quando a cor de fundo é de tema escuro (luminância relativa abaixo de 0,18). */
fun isDarkBackground(background: Color): Boolean = ColorContrast.relativeLuminance(background) < DARK_LUMINANCE

private const val DARK_LUMINANCE = 0.18

internal val LocalInspectionTokens = staticCompositionLocalOf<InspectionTokens?> { null }

/**
 * **O tema do Laudo Kit** (LK-M01). Envolve as telas de vistoria DENTRO do `AppTheme` do produto:
 *
 * ```kotlin
 * AppTheme(
 *     colorPalette = AppColorPalette(
 *         primary = Color(0xFF1F5FBF),                       // marca no claro
 *         darkBrand = AppBrandColors(primary = Color(0xFF7FA8F2)), // marca própria do escuro
 *         lightSurfaces = …, darkSurfaces = …,               // neutros do produto
 *     ),
 *     fontFamily = plexSans,
 * ) {
 *     InspectionTheme(fonts = InspectionFonts(mono = plexMono, serif = plexSerif)) {
 *         InspectionRoomList(…)
 *     }
 * }
 * ```
 *
 * Lê a marca (`primary`/`onPrimary`), os neutros e o modo do tema do host; o semáforo é fixo. Sem
 * `InspectionTheme` em volta, os componentes resolvem os mesmos tokens sozinhos (com fontes
 * genéricas) — o tema só é obrigatório para trocar a fonte mono/serifa.
 *
 * @param dark tema escuro? Default: deduzido do `background` do host.
 * @param neutrals os neutros; default = os do host (D1).
 */
@Composable
fun InspectionTheme(
    fonts: InspectionFonts = InspectionFonts(),
    dark: Boolean = isDarkBackground(MaterialTheme.colorScheme.background),
    neutrals: InspectionNeutrals = InspectionNeutrals.fromHost(dark),
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val sans = fonts.sans ?: MaterialTheme.typography.bodyLarge.fontFamily ?: FontFamily.Default
    val tokens = remember(dark, cs.primary, cs.onPrimary, neutrals, fonts, sans) {
        InspectionTokens.resolve(dark, cs.primary, cs.onPrimary, neutrals, fonts, sans)
    }
    CompositionLocalProvider(LocalInspectionTokens provides tokens, content = content)
}

/** Acesso aos tokens do kit. */
object InspectionTheme {
    val tokens: InspectionTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalInspectionTokens.current ?: run {
            val cs = MaterialTheme.colorScheme
            val dark = isDarkBackground(cs.background)
            InspectionTokens.resolve(
                dark = dark,
                brand = cs.primary,
                onBrand = cs.onPrimary,
                neutrals = InspectionNeutrals.fromHost(dark),
                sans = MaterialTheme.typography.bodyLarge.fontFamily ?: FontFamily.Default,
            )
        }
}
