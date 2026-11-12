package br.com.codecacto.kmplib.familia.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import br.com.codecacto.kmplib.ui.theme.AppBrandColors
import br.com.codecacto.kmplib.ui.theme.AppColorPalette
import br.com.codecacto.kmplib.ui.theme.AppSurfaceColors
import br.com.codecacto.kmplib.ui.theme.AppTheme
import br.com.codecacto.kmplib.ui.theme.LocalTextScale
import br.com.codecacto.kmplib.ui.theme.TextScale

/** A paleta em vigor (claro ou escuro, do app). */
val LocalFamilyColors = staticCompositionLocalOf<FamilyPalette> { error("Sem FamilyTheme na árvore") }

/** A tipografia em vigor (já com o A− A+). */
val LocalFamilyTypography = staticCompositionLocalOf<FamilyTypography> { error("Sem FamilyTheme na árvore") }

/** O app em vigor. */
val LocalFamilyApp = staticCompositionLocalOf<FamilyApp> { error("Sem FamilyTheme na árvore") }

/** `true` no tema escuro. */
val LocalFamilyDark = staticCompositionLocalOf { false }

/** Acesso curto ao tema da família: `FamilyTheme.colors.ac`, `FamilyTheme.type.h1`. */
object FamilyTheme {
    val colors: FamilyPalette
        @Composable @ReadOnlyComposable get() = LocalFamilyColors.current
    val type: FamilyTypography
        @Composable @ReadOnlyComposable get() = LocalFamilyTypography.current
    val app: FamilyApp
        @Composable @ReadOnlyComposable get() = LocalFamilyApp.current
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalFamilyDark.current
}

/**
 * Tema de um app da família: paleta do [app] (claro/escuro), fontes Fraunces + Atkinson e o tamanho de
 * letra [textScale] (A− A+). Por baixo é o `AppTheme` da kmplib — as telas da lib (feedback, Sobre,
 * Desenvolvido por, paywall) saem na cor do app, e a cor de AÇÃO muda no escuro (`lightBrand`/`darkBrand`).
 *
 * Na raiz do app:
 * ```kotlin
 * val mode by themeController.mode.collectAsState()
 * val scale by textScaleController.scale.collectAsState()
 * FamilyTheme(FamilyApp.VersiculoHoje, darkTheme = mode.isDarkTheme(), textScale = scale) { … }
 * ```
 *
 * @param scheme paleta — default a do protótipo para [app]; só troque em teste/pré-visualização.
 */
@Composable
fun FamilyTheme(
    app: FamilyApp,
    darkTheme: Boolean = isSystemInDarkTheme(),
    textScale: TextScale = TextScale.STANDARD,
    scheme: FamilyColorScheme = FamilyPalettes.of(app),
    content: @Composable () -> Unit,
) {
    val palette = scheme.forTheme(darkTheme)
    val fonts = rememberFamilyFonts()
    val typography = remember(fonts, textScale) { FamilyTypography.create(fonts, textScale.factor) }
    val appPalette = remember(scheme) { scheme.toAppColorPalette() }
    AppTheme(
        darkTheme = darkTheme,
        colorPalette = appPalette,
        fontFamily = fonts.atkinson,
        displayFontFamily = fonts.frauncesDisplay,
        fontScale = textScale.factor,
    ) {
        // Os contêineres "suaves" do protótipo (acs/acd) — o AppTheme os derivaria da marca.
        val material = MaterialTheme.colorScheme.copy(
            primaryContainer = palette.acs,
            onPrimaryContainer = palette.acd,
            secondaryContainer = palette.acs,
            onSecondaryContainer = palette.acd,
        )
        MaterialTheme(colorScheme = material, typography = MaterialTheme.typography) {
            CompositionLocalProvider(
                LocalFamilyColors provides palette,
                LocalFamilyTypography provides typography,
                LocalFamilyApp provides app,
                LocalFamilyDark provides darkTheme,
                LocalTextScale provides textScale,
                content = content,
            )
        }
    }
}

/**
 * A ponte para a paleta da kmplib: `primary = ac` (com o `onPrimary = aci`) em cada tema, superfícies
 * `bg`/`sf`/`sf2`, texto `fg`/`mu`, linha `ln`, secundária `ac2`. Pura.
 */
fun FamilyColorScheme.toAppColorPalette(): AppColorPalette = AppColorPalette(
    primary = light.ac,
    secondary = light.ac2,
    tertiary = light.ac2,
    lightSurfaces = light.toSurfaces(),
    darkSurfaces = dark.toSurfaces(),
    lightBrand = AppBrandColors(primary = light.ac, onPrimary = light.aci, secondary = light.ac2, tertiary = light.ac2),
    darkBrand = AppBrandColors(primary = dark.ac, onPrimary = dark.aci, secondary = dark.ac2, tertiary = dark.ac2),
)

private fun FamilyPalette.toSurfaces(): AppSurfaceColors = AppSurfaceColors(
    background = bg,
    surface = sf,
    surfaceVariant = sf2,
    outline = ln,
    outlineVariant = ln,
    surfaceContainerLowest = sf,
    surfaceContainerLow = sf,
    surfaceContainer = sf,
    surfaceContainerHigh = sf2,
    surfaceContainerHighest = sf2,
    onBackground = fg,
    onSurface = fg,
    onSurfaceVariant = mu,
)
