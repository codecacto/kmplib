package br.com.codecacto.kmplib.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Sistema de cores baseado em Material Design 3
 *
 * Suporta Light e Dark mode com paletas personalizáveis.
 */

/**
 * Paleta de cores personalizável
 *
 * As cores de **marca/semântica** (primary…info) são a base histórica. As **superfícies** ([darkSurfaces]
 * / [lightSurfaces]) são **aditivas e opcionais** (2.71.0): quando `null` (default), o esquema usa as
 * superfícies neutras padrão do Material (comportamento anterior, byte-idêntico — nenhum app existente
 * muda de aparência); quando informadas, o app controla o conjunto COMPLETO de superfícies do Material 3
 * (fundo, cartões, containers, contornos e as cores `on*`), permitindo, por exemplo, um tema **preto de
 * verdade** (`#0A0A0A`/`#141414`) em vez do cinza-escuro fixo `#121212`/`#1E1E1E`.
 *
 * @param primary Cor primária (botões, destaques principais)
 * @param secondary Cor secundária (destaques complementares)
 * @param tertiary Cor terciária (elementos adicionais)
 * @param error Cor de erro (avisos, erros de validação)
 * @param success Cor de sucesso (feedback positivo)
 * @param warning Cor de aviso (alertas)
 * @param info Cor informativa (informações neutras)
 * @param darkSurfaces Superfícies do esquema **escuro** (ver [AppSurfaceColors]). `null` = superfícies
 *   padrão do Material (retrocompatível).
 * @param lightSurfaces Superfícies do esquema **claro**. `null` = superfícies padrão do Material.
 */
data class AppColorPalette(
    val primary: Color,
    val secondary: Color = primary,
    val tertiary: Color = primary,
    val error: Color = Color(0xFFDC3545),
    val success: Color = Color(0xFF10B981),
    val warning: Color = Color(0xFFF59E0B),
    val info: Color = Color(0xFF3B82F6),
    val darkSurfaces: AppSurfaceColors? = null,
    val lightSurfaces: AppSurfaceColors? = null
)

/**
 * Conjunto **completo** de superfícies do Material 3, para o app abrir/customizar o fundo e os cartões
 * do tema — cobrindo os tokens de verdade, não só `background`/`surface`, para o próximo projeto não
 * reabrir o mesmo gap. Só [background] é obrigatório; todo o resto tem derivação coesa e **contraste
 * garantido**:
 *
 * - **Escala de elevação** (`surfaceContainer*`, `surfaceDim`/`surfaceBright`): quando `null`, é
 *   derivada por interpolação entre [background] (degrau mais baixo) e a superfície mais alta,
 *   mantendo a **mesma família de tons** — um preto que sobe em degraus quase-pretos, nunca o
 *   cinza-roxo do baseline Material que apareceria se esses roles ficassem sem setar.
 * - **Cores de conteúdo** (`onBackground`/`onSurface`/`onSurfaceVariant`): quando `null`, são
 *   **derivadas por contraste WCAG** ([ColorContrast.pickOnColor]) — escolhendo entre [onColorLight] e
 *   [onColorDark] a que dá maior legibilidade sobre CADA superfície. Assim, um `background` claro nunca
 *   resulta em texto claro ilegível: o `on*` acompanha automaticamente.
 * - **Contornos** (`outline`/`outlineVariant`): derivados a partir da superfície quando `null`.
 *
 * O app pode sobrescrever QUALQUER token explicitamente (ex.: o texto secundário “apagado”
 * `onSurfaceVariant` num cinza de marca), e a lib **valida em debug** o contraste dos que forem
 * passados à mão (ver [surfaceContrastWarnings]).
 *
 * @param background Superfície raiz (fundo da tela). Obrigatório.
 * @param surface Cartões, top bar, folhas. Default = [background].
 * @param surfaceVariant Faixas/linhas/chips (um degrau acima da superfície). `null` = derivado.
 * @param outline Divisórias e contornos. `null` = derivado.
 * @param outlineVariant Contorno forte (seleção). `null` = igual ao [outline] resolvido.
 * @param surfaceContainerLowest .. [surfaceContainerHighest] Escala de elevação. `null` = derivada.
 * @param surfaceDim Superfície “rebaixada”. `null` = [background].
 * @param surfaceBright Superfície “realçada”. `null` = derivada.
 * @param onBackground Conteúdo sobre [background]. `null` = derivado por contraste.
 * @param onSurface Conteúdo sobre [surface]. `null` = derivado por contraste.
 * @param onSurfaceVariant Conteúdo sobre [surfaceVariant] (texto secundário). `null` = derivado.
 * @param onColorLight Candidato **claro** para derivação dos `on*` (default quase-branco `#FAFAFA`).
 * @param onColorDark Candidato **escuro** para derivação dos `on*` (default quase-preto `#0A0A0A`).
 */
data class AppSurfaceColors(
    val background: Color,
    val surface: Color = background,
    val surfaceVariant: Color? = null,
    val outline: Color? = null,
    val outlineVariant: Color? = null,
    val surfaceContainerLowest: Color? = null,
    val surfaceContainerLow: Color? = null,
    val surfaceContainer: Color? = null,
    val surfaceContainerHigh: Color? = null,
    val surfaceContainerHighest: Color? = null,
    val surfaceDim: Color? = null,
    val surfaceBright: Color? = null,
    val onBackground: Color? = null,
    val onSurface: Color? = null,
    val onSurfaceVariant: Color? = null,
    val onColorLight: Color = Color(0xFFFAFAFA),
    val onColorDark: Color = Color(0xFF0A0A0A)
)

/**
 * Superfícies com TODOS os tokens já resolvidos (derivações e cores `on*` computadas). Resultado de
 * [AppSurfaceColors.resolve]; consumido internamente para preencher o [ColorScheme].
 */
internal data class ResolvedSurfaces(
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val surfaceDim: Color,
    val surfaceBright: Color,
    val outline: Color,
    val outlineVariant: Color
)

/**
 * Resolve as derivações e as cores `on*` (por contraste WCAG) de [AppSurfaceColors], produzindo o
 * conjunto completo aplicado ao [ColorScheme]. A direção da derivação (superfície escura sobe para o
 * claro; superfície clara desce para o escuro) é inferida pela luminância do [AppSurfaceColors.background].
 */
internal fun AppSurfaceColors.resolve(): ResolvedSurfaces {
    val isDarkSurface = ColorContrast.relativeLuminance(background) < 0.5
    val toward = if (isDarkSurface) onColorLight else onColorDark

    val resolvedSurfaceVariant = surfaceVariant ?: surface.lerpTo(toward, 0.06f)
    val resolvedOutline = outline ?: surface.lerpTo(toward, 0.22f)
    val resolvedOutlineVariant = outlineVariant ?: resolvedOutline

    fun onColor(bg: Color): Color = ColorContrast.pickOnColor(bg, onColorLight, onColorDark)

    return ResolvedSurfaces(
        background = background,
        onBackground = onBackground ?: onColor(background),
        surface = surface,
        onSurface = onSurface ?: onColor(surface),
        surfaceVariant = resolvedSurfaceVariant,
        onSurfaceVariant = onSurfaceVariant ?: onColor(resolvedSurfaceVariant),
        // Escala de elevação coesa: background (dim) → surfaceVariant (mais alta).
        surfaceContainerLowest = surfaceContainerLowest ?: background,
        surfaceContainerLow = surfaceContainerLow ?: background.lerpTo(resolvedSurfaceVariant, 0.25f),
        surfaceContainer = surfaceContainer ?: surface,
        surfaceContainerHigh = surfaceContainerHigh ?: background.lerpTo(resolvedSurfaceVariant, 0.75f),
        surfaceContainerHighest = surfaceContainerHighest ?: resolvedSurfaceVariant,
        surfaceDim = surfaceDim ?: background,
        surfaceBright = surfaceBright ?: resolvedSurfaceVariant.lerpTo(toward, 0.05f),
        outline = resolvedOutline,
        outlineVariant = resolvedOutlineVariant
    )
}

/**
 * Aplica as superfícies resolvidas sobre um [ColorScheme] base (que já traz as cores de marca). Mantém
 * `surfaceTint`/`scrim` do base; recalcula `inverseSurface`/`inverseOnSurface` a partir das novas
 * superfícies para o par invertido continuar coerente.
 */
internal fun ColorScheme.withSurfaces(s: ResolvedSurfaces): ColorScheme = copy(
    background = s.background,
    onBackground = s.onBackground,
    surface = s.surface,
    onSurface = s.onSurface,
    surfaceVariant = s.surfaceVariant,
    onSurfaceVariant = s.onSurfaceVariant,
    surfaceContainerLowest = s.surfaceContainerLowest,
    surfaceContainerLow = s.surfaceContainerLow,
    surfaceContainer = s.surfaceContainer,
    surfaceContainerHigh = s.surfaceContainerHigh,
    surfaceContainerHighest = s.surfaceContainerHighest,
    surfaceDim = s.surfaceDim,
    surfaceBright = s.surfaceBright,
    outline = s.outline,
    outlineVariant = s.outlineVariant,
    inverseSurface = s.onSurface,
    inverseOnSurface = s.surface
)

/**
 * Verifica o contraste WCAG do **texto** (`on*`) sobre cada superfície e devolve avisos legíveis para os
 * pares que o app informou **à mão** e que ficaram abaixo de [ColorContrast.AA_TEXT] (4.5:1). Só reporta
 * cores `on*` passadas explicitamente — as **derivadas** já saem com contraste garantido por
 * [ColorContrast.pickOnColor], então não são reavaliadas. É o guarda contra o cenário descrito no gap:
 * um `background` claro com um `onBackground` claro (texto ilegível).
 *
 * **Não** valida `outline`/`outlineVariant`: divisórias/contornos são elementos **decorativos** (uma
 * hairline `#2E2E2E` sobre um cartão `#141414` é intencional e correta no design preto), fora do escopo
 * do requisito de contraste de texto — cobrá-los geraria falso-positivo. Puro/testável; usado pelo
 * [AppTheme] para logar em debug (nunca lança, nunca bloqueia render).
 */
fun surfaceContrastWarnings(surfaces: AppSurfaceColors): List<String> {
    val r = surfaces.resolve()
    val warnings = mutableListOf<String>()

    fun checkText(label: String, provided: Color?, fg: Color, bg: Color) {
        if (provided != null && !ColorContrast.meetsTextContrast(fg, bg)) {
            val ratio = ColorContrast.contrastRatio(fg, bg)
            warnings += "$label: razão ${ratio.format2()}:1 < ${ColorContrast.AA_TEXT}:1 (WCAG AA texto)"
        }
    }

    checkText("onBackground/background", surfaces.onBackground, r.onBackground, r.background)
    checkText("onSurface/surface", surfaces.onSurface, r.onSurface, r.surface)
    checkText("onSurfaceVariant/surfaceVariant", surfaces.onSurfaceVariant, r.onSurfaceVariant, r.surfaceVariant)
    return warnings
}

/** Formata um Double com 2 casas (sem depender de locale/String.format do JVM). */
private fun Double.format2(): String {
    val scaled = (this * 100).toLong()
    return "${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
}

/**
 * Cria um ColorScheme Light baseado na paleta customizada.
 *
 * Quando [AppColorPalette.lightSurfaces] é informado, o conjunto completo de superfícies do app é
 * aplicado sobre o esquema (fundo/cartões/containers/contornos + cores `on*` derivadas por contraste);
 * quando `null`, mantém as superfícies neutras padrão (retrocompatível).
 */
fun createLightColorScheme(
    palette: AppColorPalette
): ColorScheme {
    val base = lightColorScheme(
        primary = palette.primary,
        onPrimary = Color.White,
        primaryContainer = palette.primary.copy(alpha = 0.1f),
        onPrimaryContainer = palette.primary,

        secondary = palette.secondary,
        onSecondary = Color.White,
        secondaryContainer = palette.secondary.copy(alpha = 0.1f),
        onSecondaryContainer = palette.secondary,

        tertiary = palette.tertiary,
        onTertiary = Color.White,
        tertiaryContainer = palette.tertiary.copy(alpha = 0.1f),
        onTertiaryContainer = palette.tertiary,

        error = palette.error,
        onError = Color.White,
        errorContainer = palette.error.copy(alpha = 0.1f),
        onErrorContainer = palette.error,

        background = Color(0xFFFAFAFA),
        onBackground = Color(0xFF1C1C1C),

        surface = Color.White,
        onSurface = Color(0xFF1C1C1C),
        surfaceVariant = Color(0xFFF5F5F5),
        onSurfaceVariant = Color(0xFF616161),

        outline = Color(0xFFE0E0E0),
        outlineVariant = Color(0xFFF5F5F5),

        scrim = Color.Black.copy(alpha = 0.32f),

        inverseSurface = Color(0xFF1C1C1C),
        inverseOnSurface = Color(0xFFFAFAFA),
        inversePrimary = palette.primary.copy(alpha = 0.8f),

        surfaceTint = palette.primary
    )
    return palette.lightSurfaces?.let { base.withSurfaces(it.resolve()) } ?: base
}

/**
 * Cria um ColorScheme Dark baseado na paleta customizada.
 *
 * Quando [AppColorPalette.darkSurfaces] é informado, o conjunto completo de superfícies do app é
 * aplicado sobre o esquema (permitindo um preto de verdade `#0A0A0A`/`#141414` em vez do cinza fixo);
 * quando `null`, mantém `background = #121212` / `surface = #1E1E1E` etc. (retrocompatível).
 */
fun createDarkColorScheme(
    palette: AppColorPalette
): ColorScheme {
    val base = darkColorScheme(
        primary = palette.primary.copy(alpha = 0.9f),
        onPrimary = Color(0xFF1C1C1C),
        primaryContainer = palette.primary.copy(alpha = 0.2f),
        onPrimaryContainer = palette.primary.copy(alpha = 0.9f),

        secondary = palette.secondary.copy(alpha = 0.9f),
        onSecondary = Color(0xFF1C1C1C),
        secondaryContainer = palette.secondary.copy(alpha = 0.2f),
        onSecondaryContainer = palette.secondary.copy(alpha = 0.9f),

        tertiary = palette.tertiary.copy(alpha = 0.9f),
        onTertiary = Color(0xFF1C1C1C),
        tertiaryContainer = palette.tertiary.copy(alpha = 0.2f),
        onTertiaryContainer = palette.tertiary.copy(alpha = 0.9f),

        error = palette.error.copy(alpha = 0.9f),
        onError = Color(0xFF1C1C1C),
        errorContainer = palette.error.copy(alpha = 0.2f),
        onErrorContainer = palette.error.copy(alpha = 0.9f),

        background = Color(0xFF121212),
        onBackground = Color(0xFFE0E0E0),

        surface = Color(0xFF1E1E1E),
        onSurface = Color(0xFFE0E0E0),
        surfaceVariant = Color(0xFF2C2C2C),
        onSurfaceVariant = Color(0xFFB0B0B0),

        outline = Color(0xFF424242),
        outlineVariant = Color(0xFF2C2C2C),

        scrim = Color.Black.copy(alpha = 0.6f),

        inverseSurface = Color(0xFFE0E0E0),
        inverseOnSurface = Color(0xFF1C1C1C),
        inversePrimary = palette.primary,

        surfaceTint = palette.primary.copy(alpha = 0.9f)
    )
    return palette.darkSurfaces?.let { base.withSurfaces(it.resolve()) } ?: base
}

/**
 * Cria um ColorScheme Light de **alto contraste** (acessibilidade / baixa visão) derivado da paleta.
 *
 * Diferente do tema claro normal (que já é quase branco/preto — logo trocar por branco/preto puro é
 * imperceptível), este esquema torna a diferença **dramática e legítima** (WCAG AAA nas superfícies):
 * - `background`/`surface` = `paper` (branco), texto `ink` (preto), razão ~21:1;
 * - **`primary` = quase-preto** ([HC_PRIMARY], **não** a cor da marca): superfícies/elementos
 *   preenchidos com `primary` (ex.: `CommunicationTile` no tom `Normal`) passam de "colorido" (tema
 *   normal) para "quase-preto com texto branco" — diferença **inconfundível** ao ligar o alto contraste;
 * - `error` = vermelho escuro forte ([HC_ERROR]) com `onError` branco;
 * - tom Quick (`secondaryContainer`/`onSecondaryContainer`) = container cinza claro
 *   ([HC_TONAL_CONTAINER]) + texto `ink` (~16:1), não some no fundo branco;
 * - **bordas fortes** (`outline` = `ink`, preto) para separar elementos sem depender só de cor.
 *
 * Este é o **par de contraste** selecionado por `AppTheme(highContrast = true)`.
 */
fun createHighContrastLightColorScheme(
    palette: AppColorPalette
): ColorScheme {
    val ink = Color(0xFF000000)
    val paper = Color(0xFFFFFFFF)
    return lightColorScheme(
        primary = HC_PRIMARY,
        onPrimary = paper,
        primaryContainer = paper,
        onPrimaryContainer = ink,

        secondary = ink,
        onSecondary = paper,
        secondaryContainer = HC_TONAL_CONTAINER,
        onSecondaryContainer = ink,

        tertiary = ink,
        onTertiary = paper,
        tertiaryContainer = HC_TONAL_CONTAINER,
        onTertiaryContainer = ink,

        error = HC_ERROR,
        onError = paper,
        errorContainer = paper,
        onErrorContainer = HC_ERROR,

        background = paper,
        onBackground = ink,

        surface = paper,
        onSurface = ink,
        surfaceVariant = Color(0xFFF2F2F2),
        onSurfaceVariant = ink,

        outline = ink,
        outlineVariant = Color(0xFF3A3A3A),

        scrim = ink,

        inverseSurface = ink,
        inverseOnSurface = paper,
        inversePrimary = paper,

        surfaceTint = HC_PRIMARY
    )
}

/** Primária de alto contraste (light): quase-preto — máximo peso com texto branco (WCAG AAA). */
private val HC_PRIMARY = Color(0xFF0A0A0A)

/** Vermelho escuro forte de alto contraste (urgência), com texto branco (WCAG AAA). */
private val HC_ERROR = Color(0xFF8A0000)

/** Container tonal do tom Quick no alto contraste (light): cinza claro distinto do fundo branco. */
private val HC_TONAL_CONTAINER = Color(0xFFE6E6E6)

/**
 * Cria um ColorScheme Dark de **alto contraste** derivado da paleta, coerente com o par light:
 * superfícies pretas puras com texto branco (razão ~21:1, acima do AAA) e contornos brancos fortes;
 * **`primary` = branco** ([HC_PRIMARY_DARK]) com `onPrimary` preto — elementos preenchidos com
 * `primary` (ex.: `CommunicationTile` `Normal`) viram "branco com texto preto", máximo peso; `error`
 * = vermelho claro forte ([HC_ERROR_DARK]); tom Quick = container cinza escuro
 * ([HC_TONAL_CONTAINER_DARK]) + texto branco. Selecionado por
 * `AppTheme(highContrast = true, darkTheme = true)`.
 */
fun createHighContrastDarkColorScheme(
    palette: AppColorPalette
): ColorScheme {
    val ink = Color(0xFF000000)
    val paper = Color(0xFFFFFFFF)
    return darkColorScheme(
        primary = HC_PRIMARY_DARK,
        onPrimary = ink,
        primaryContainer = ink,
        onPrimaryContainer = paper,

        secondary = paper,
        onSecondary = ink,
        secondaryContainer = HC_TONAL_CONTAINER_DARK,
        onSecondaryContainer = paper,

        tertiary = paper,
        onTertiary = ink,
        tertiaryContainer = HC_TONAL_CONTAINER_DARK,
        onTertiaryContainer = paper,

        error = HC_ERROR_DARK,
        onError = ink,
        errorContainer = ink,
        onErrorContainer = HC_ERROR_DARK,

        background = ink,
        onBackground = paper,

        surface = ink,
        onSurface = paper,
        surfaceVariant = Color(0xFF1A1A1A),
        onSurfaceVariant = paper,

        outline = paper,
        outlineVariant = Color(0xFFCFCFCF),

        scrim = ink,

        inverseSurface = paper,
        inverseOnSurface = ink,
        inversePrimary = HC_PRIMARY_DARK,

        surfaceTint = HC_PRIMARY_DARK
    )
}

/** Primária de alto contraste (dark): branco — máximo peso com texto preto (WCAG AAA). */
private val HC_PRIMARY_DARK = Color(0xFFFFFFFF)

/** Vermelho claro forte de alto contraste (dark), com texto preto (WCAG AAA sobre fundo preto). */
private val HC_ERROR_DARK = Color(0xFFFF5A5A)

/** Container tonal do tom Quick no alto contraste (dark): cinza escuro distinto do fundo preto. */
private val HC_TONAL_CONTAINER_DARK = Color(0xFF2A2A2A)

/**
 * Paletas de cores prontas
 */
object AppColorPalettes {
    /**
     * Paleta padrão roxa (Material Design)
     */
    val Default = AppColorPalette(
        primary = Color(0xFF6C63FF)
    )

    /**
     * Paleta laranja (Locadora)
     */
    val Orange = AppColorPalette(
        primary = Color(0xFFF97316)
    )

    /**
     * Paleta verde esmeralda (Advogado)
     */
    val Green = AppColorPalette(
        primary = Color(0xFF10B981)
    )

    /**
     * Paleta azul
     */
    val Blue = AppColorPalette(
        primary = Color(0xFF3B82F6)
    )

    /**
     * Paleta rosa
     */
    val Pink = AppColorPalette(
        primary = Color(0xFFEC4899)
    )

    /**
     * Paleta vermelha
     */
    val Red = AppColorPalette(
        primary = Color(0xFFDC3545)
    )

    /**
     * Paleta **Teal acessível** (CAA / apps de acessibilidade — ex.: Minha Voz).
     *
     * - `primary` teal médio-escuro (`#0F766E`, teal-700): preenchimento sólido com **texto branco
     *   legível** (contraste ~5:1) — serve ao `CommunicationTile` no tom `Normal`.
     * - `secondary` teal um passo mais claro (`#0D9488`, teal-600): tom harmônico e distinto usado
     *   como `secondaryContainer`/`onSecondaryContainer` do tom `Quick` (respostas rápidas).
     * - `error` vermelho forte (`#DC2626`) com texto branco, para o tom `Alert` (urgentes).
     */
    val Teal = AppColorPalette(
        primary = Color(0xFF0F766E),
        secondary = Color(0xFF0D9488),
        error = Color(0xFFDC2626)
    )
}
