package br.com.codecacto.kmplib.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Matemática de contraste WCAG 2.x — paridade com o par web (`status-contrast.ts`). Garante que a
 * derivação automática de cores `on*` (base de "contraste é responsabilidade da lib") escolhe sempre
 * a cor legível.
 */
class ColorContrastTest {

    private val white = Color(0xFFFFFFFF)
    private val black = Color(0xFF000000)

    @Test
    fun `contraste branco sobre preto e o maximo 21 para 1`() {
        assertEquals(21.0, ColorContrast.contrastRatio(white, black), 0.01)
        // Simétrico (ordem não importa).
        assertEquals(21.0, ColorContrast.contrastRatio(black, white), 0.01)
    }

    @Test
    fun `luminancia relativa de branco e preto`() {
        assertEquals(1.0, ColorContrast.relativeLuminance(white), 0.001)
        assertEquals(0.0, ColorContrast.relativeLuminance(black), 0.001)
    }

    @Test
    fun `pickOnColor escolhe claro sobre superficie escura e escuro sobre clara`() {
        // #0A0A0A (fundo do Meu Barbeiro) → texto claro.
        assertEquals(Color(0xFFFAFAFA), ColorContrast.pickOnColor(Color(0xFF0A0A0A)))
        // #FAFAFA (fundo claro) → texto escuro (nunca "claro sobre claro").
        assertEquals(Color(0xFF0A0A0A), ColorContrast.pickOnColor(Color(0xFFFAFAFA)))
    }

    @Test
    fun `compositeOver mistura foreground semitransparente sobre o fundo`() {
        // 50% branco sobre preto = cinza médio (tolerância p/ a quantização sRGB 8-bit do Compose).
        val mid = ColorContrast.compositeOver(white, 0.5f, black)
        assertEquals(0.5f, mid.red, 0.01f)
        assertEquals(1f, mid.alpha, 0.001f)
        // alpha 0 = fundo puro; alpha 1 = foreground puro.
        assertEquals(black, ColorContrast.compositeOver(white, 0f, black))
        assertEquals(1f, ColorContrast.compositeOver(white, 1f, black).red, 0.01f)
    }

    @Test
    fun `preenchimento a 15pct sobre preto (padrao da agenda) tem contraste AA com texto branco`() {
        // Espelha o teste web: cor de status @15% composta sobre o fundo preto, texto branco ≥ 4.5:1.
        val fill = ColorContrast.compositeOver(Color(0xFF38BDF8), 0.15f, Color(0xFF0A0A0A))
        assertTrue(ColorContrast.meetsTextContrast(white, fill))
    }

    @Test
    fun `lerpTo interpola preto ate cinza claro em degraus opacos`() {
        val step = Color(0xFF0A0A0A).lerpTo(Color(0xFF1F1F1F), 0.5f)
        // Ponto médio entre os dois cinzas escuros.
        assertEquals(1f, step.alpha, 0.001f)
        assertTrue(step.red > Color(0xFF0A0A0A).red && step.red < Color(0xFF1F1F1F).red)
    }
}
