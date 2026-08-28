package br.com.codecacto.kmplib.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import br.com.codecacto.kmplib.ui.theme.ColorContrast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Testes das regras puras do [AppBanner] — o que garante a paridade com o `Banner` da weblib
 * (erro sólido + `role="alert"`) e a legibilidade do texto sobre fundo preenchido.
 */
class AppBannerTest {

    @Test
    fun `erro e sempre solido por padrao`() {
        // Padrão do ecossistema (memória error-banner-solid-standard / weblib 0.67.0).
        assertEquals(BannerStyle.SOLID, defaultBannerStyle(StatusTone.DANGER))
    }

    @Test
    fun `demais tons nascem suaves`() {
        listOf(StatusTone.INFO, StatusTone.SUCCESS, StatusTone.WARNING, StatusTone.NEUTRAL)
            .forEach { assertEquals(BannerStyle.SOFT, defaultBannerStyle(it), "$it") }
    }

    @Test
    fun `cada tom tem icone e o erro nao repete o do aviso`() {
        assertNotEquals(
            bannerDefaultIcon(StatusTone.DANGER).name,
            bannerDefaultIcon(StatusTone.WARNING).name,
        )
        assertNotEquals(
            bannerDefaultIcon(StatusTone.SUCCESS).name,
            bannerDefaultIcon(StatusTone.INFO).name,
        )
    }

    @Test
    fun `neutro reusa o icone informativo`() {
        assertEquals(
            bannerDefaultIcon(StatusTone.INFO).name,
            bannerDefaultIcon(StatusTone.NEUTRAL).name,
        )
    }

    @Test
    fun `erro interrompe o leitor de tela e o resto e polido`() {
        // Equivalente ao role="alert" vs role="status" da weblib.
        assertEquals(LiveRegionMode.Assertive, bannerLiveRegion(StatusTone.DANGER))
        StatusTone.entries.filter { it != StatusTone.DANGER }.forEach {
            assertEquals(LiveRegionMode.Polite, bannerLiveRegion(it), "$it")
        }
    }

    @Test
    fun `texto de banner solido atinge o contraste AA em qualquer tom da paleta`() {
        // Os tons custom (success/warning/info) não têm par `on*` no Material: a cor do texto é
        // derivada por contraste. Este teste é a garantia de que âmbar sólido não recebe branco.
        // DANGER fica de fora de propósito: usa o par oficial `error`/`onError` do ColorScheme,
        // não a derivação (é o que mantém o vermelho-com-branco do padrão web).
        val tones = listOf(
            Color(0xFF10B981), // success default
            Color(0xFFF59E0B), // warning default
            Color(0xFF3B82F6), // info default
        )
        tones.forEach { bg ->
            val fg = ColorContrast.pickOnColor(bg)
            assertTrue(
                ColorContrast.meetsTextContrast(fg, bg),
                "contraste insuficiente sobre $bg: ${ColorContrast.contrastRatio(fg, bg)}",
            )
        }
    }

    @Test
    fun `fundo suave nao chega a esconder o texto do tema`() {
        // 12% de tingimento mantém a superfície praticamente intacta (texto segue com o contraste
        // do próprio tema); a borda a 40% é só a delimitação visual.
        assertTrue(BANNER_SOFT_CONTAINER_ALPHA in 0.05f..0.2f)
        assertTrue(BANNER_SOFT_BORDER_ALPHA > BANNER_SOFT_CONTAINER_ALPHA)
    }
}
