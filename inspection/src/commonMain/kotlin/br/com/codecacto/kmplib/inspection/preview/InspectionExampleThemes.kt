package br.com.codecacto.kmplib.inspection.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import br.com.codecacto.kmplib.inspection.theme.InspectionFonts
import br.com.codecacto.kmplib.inspection.theme.InspectionTheme
import br.com.codecacto.kmplib.ui.theme.AppBrandColors
import br.com.codecacto.kmplib.ui.theme.AppColorPalette
import br.com.codecacto.kmplib.ui.theme.AppSurfaceColors
import br.com.codecacto.kmplib.ui.theme.AppTheme

/**
 * Os dois temas de EXEMPLO do kit (previews e testes) — o que cada produto declara no próprio
 * `AppTheme`. **Não é API**: a marca é do produto, nunca da lib. As fontes (IBM Plex / Source Sans 3)
 * entram no produto pelo `Res.font`; aqui ficam as genéricas.
 */
internal object InspectionExampleThemes {
    /** Minha Vistoria: azul `#1F5FBF` (claro) / `#7FA8F2` (escuro), neutros frios. */
    val MinhaVistoria = AppColorPalette(
        primary = Color(0xFF1F5FBF),
        darkBrand = AppBrandColors(primary = Color(0xFF7FA8F2)),
        lightSurfaces = AppSurfaceColors(
            background = Color(0xFFF4F5F7),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFECEEF2),
            outline = Color(0xFFDADEE5),
            onSurface = Color(0xFF151A22),
            onSurfaceVariant = Color(0xFF5B6474),
        ),
        darkSurfaces = AppSurfaceColors(
            background = Color(0xFF0F1217),
            surface = Color(0xFF171B22),
            surfaceVariant = Color(0xFF1F242C),
            outline = Color(0xFF2B313B),
            onSurface = Color(0xFFE7EAEF),
            onSurfaceVariant = Color(0xFF9AA3B1),
        ),
    )

    /** Meus Aluguéis: índigo `#4B44C8` / `#A19CF7`, neutros violeta (D1 + §9). */
    val MeusAlugueis = AppColorPalette(
        primary = Color(0xFF4B44C8),
        darkBrand = AppBrandColors(primary = Color(0xFFA19CF7)),
        lightSurfaces = AppSurfaceColors(
            background = Color(0xFFF6F6F8),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFEDEDF2),
            outline = Color(0xFFDCDCE4),
            onSurface = Color(0xFF17161F),
            onSurfaceVariant = Color(0xFF585766),
        ),
        darkSurfaces = AppSurfaceColors(
            background = Color(0xFF100F17),
            surface = Color(0xFF18171F),
            surfaceVariant = Color(0xFF201F29),
            outline = Color(0xFF2D2C38),
            onSurface = Color(0xFFECEBF3),
            onSurfaceVariant = Color(0xFFA09FB0),
        ),
    )

    /** As marcas (claro, escuro) de cada exemplo — usadas no teste de contraste. */
    val Brands: List<Pair<Color, Color>> = listOf(
        Color(0xFF1F5FBF) to Color(0xFF7FA8F2),
        Color(0xFF4B44C8) to Color(0xFFA19CF7),
    )
}

/** Envolve um preview no tema de exemplo do produto. */
@Composable
internal fun InspectionExampleTheme(palette: AppColorPalette, dark: Boolean, content: @Composable () -> Unit) {
    AppTheme(darkTheme = dark, colorPalette = palette, systemBars = false) {
        InspectionTheme(fonts = InspectionFonts(), dark = dark, content = content)
    }
}
