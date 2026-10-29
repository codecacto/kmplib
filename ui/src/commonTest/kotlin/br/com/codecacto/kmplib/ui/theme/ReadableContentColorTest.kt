package br.com.codecacto.kmplib.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 2.333.0 — texto do botão calculado pela cor EFETIVA do botão (confirmar do `AppAlertDialog`,
 * `AppInputDialog`, `ConfirmationDialog`, `AppButton`, `AppFab`). Caso de origem: OnMore (App do
 * Personal, 29/out/2026) — primária clara, `onPrimary` escuro, "Sair" com
 * `confirmButtonColor = colorScheme.error` saía PRETO sobre o vermelho.
 */
class ReadableContentColorTest {

    private val nearBlack = Color(0xFF1C1C1C)
    private val lime = Color(0xFFA3E635)

    private fun assertAa(fg: Color, bg: Color, label: String) {
        val r = ColorContrast.contrastRatio(fg, bg)
        assertTrue(r >= ColorContrast.AA_TEXT, "$label: contraste $r < 4.5")
    }

    @Test
    fun `vermelho de acao destrutiva leva texto branco`() {
        for (red in listOf(Color(0xFFDC2626), Color(0xFFDC3545), Color(0xFFB3261E), Color(0xFFB91C1C))) {
            assertEquals(Color.White, ColorContrast.readableOn(red), "vermelho $red")
        }
    }

    @Test
    fun `cor clara leva texto escuro`() {
        assertEquals(nearBlack, ColorContrast.readableOn(Color(0xFFFACC15)))
        assertEquals(nearBlack, ColorContrast.readableOn(lime))
        assertEquals(nearBlack, ColorContrast.readableOn(Color.White))
    }

    @Test
    fun `luminancia media cai no preto puro e ainda passa de AA`() {
        // Cinza ~0,19 de luminância: nem branco nem #1C1C1C chegam a 4,5:1.
        val mid = Color(0xFF7A7A7A)
        val on = ColorContrast.readableOn(mid)
        assertAa(on, mid, "cinza médio")
    }

    @Test
    fun `qualquer cor opaca sai com AA`() {
        val steps = listOf(0x00, 0x33, 0x66, 0x80, 0x99, 0xBB, 0xDD, 0xFF)
        for (r in steps) for (g in steps) for (b in steps) {
            val c = Color(red = r / 255f, green = g / 255f, blue = b / 255f)
            assertAa(ColorContrast.readableOn(c), c, "cor $r,$g,$b")
        }
    }

    @Test
    fun `preenchimento translucido e composto sobre o fundo antes da conta`() {
        val translucentRed = Color(0xFFDC2626).copy(alpha = 0.3f)
        val shown = ColorContrast.compositeOver(translucentRed, 1f, Color.White)
        val on = ColorContrast.readableOn(translucentRed, backdrop = Color.White)
        assertAa(on, shown, "vermelho 30% sobre branco")
        assertEquals(nearBlack, on)
    }

    @Test
    fun `cor do tema devolve o par on do tema`() {
        val scheme = lightColorScheme(
            primary = lime, onPrimary = nearBlack,
            error = Color(0xFFDC2626), onError = Color.White,
        )
        assertEquals(nearBlack, readableContentColorFor(lime, scheme))
        assertEquals(Color.White, readableContentColorFor(Color(0xFFDC2626), scheme))
        assertEquals(scheme.onSurface, readableContentColorFor(Color.Unspecified, scheme))
        assertEquals(scheme.onSurface, readableContentColorFor(scheme.surface, scheme))
    }

    @Test
    fun `cor fora do tema e calculada pelo contraste e nao herda onPrimary`() {
        val scheme = lightColorScheme(primary = lime, onPrimary = nearBlack)
        val red = Color(0xFFC62828)
        assertEquals(Color.White, readableContentColorFor(red, scheme))
    }

    @Test
    fun `OnMore - primaria clara do AppTheme e Sair no error sai branco`() {
        val scheme = createLightColorScheme(AppColorPalette(primary = lime))
        // A primária clara tem texto escuro (o que o confirmar herdava até a 2.332.0)...
        assertEquals(false, scheme.onPrimary == Color.White)
        // ...mas o botão vermelho tem o par do error.
        val on = readableContentColorFor(scheme.error, scheme)
        assertEquals(Color.White, on)
        assertAa(on, scheme.error, "Sair")
    }

    @Test
    fun `texto do cancelar em onSurface passa de AA nos dois temas`() {
        val p = AppColorPalette(primary = lime)
        for (scheme in listOf(createLightColorScheme(p), createDarkColorScheme(p))) {
            assertAa(scheme.onSurface, scheme.surface, "cancelar")
        }
    }
}
