package br.com.codecacto.kmplib.auth.ui

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import br.com.codecacto.kmplib.ui.screens.login.LoginBackground
import br.com.codecacto.kmplib.ui.screens.login.LoginBackgroundDefaults
import br.com.codecacto.kmplib.ui.screens.login.loginScrimColors
import br.com.codecacto.kmplib.ui.screens.login.loginSystemBarsBackground
import br.com.codecacto.kmplib.ui.theme.needsDarkSystemBarIcons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Login com imagem de fundo (2.299.0): defaults, degradê e cor das barras. */
class LoginBackgroundTest {

    private val painter = ColorPainter(Color.Red)

    @Test
    fun `defaults cobrem a tela com degrade escuro e cartao na superficie do tema`() {
        val bg = LoginBackground(painter)
        assertEquals(ContentScale.Crop, bg.contentScale)
        assertEquals(Alignment.Center, bg.alignment)
        assertEquals(Color.Black, bg.scrimColor)
        assertNull(bg.cardColor, "null = a superfície opaca do LoginColors")
        assertTrue(bg.scrimBottomAlpha > bg.scrimTopAlpha, "a base escurece mais, onde fica o cartão")
        assertEquals("login-cartao", LoginBackgroundDefaults.CARD_TEST_TAG)
        assertEquals("login-fundo", LoginBackgroundDefaults.IMAGE_TEST_TAG)
    }

    @Test
    fun `degrade usa a opacidade pedida`() {
        val (top, bottom) = loginScrimColors(LoginBackground(painter, scrimTopAlpha = 0.3f, scrimBottomAlpha = 0.7f))
        assertEquals(0.3f, top.alpha, 0.01f)
        assertEquals(0.7f, bottom.alpha, 0.01f)
        assertEquals(Color.Black.copy(alpha = 0.3f).red, top.red)
    }

    @Test
    fun `opacidade fora da faixa ou NaN fica entre 0 e 1`() {
        val (top, bottom) = loginScrimColors(LoginBackground(painter, scrimTopAlpha = -1f, scrimBottomAlpha = 4f))
        assertEquals(0f, top.alpha)
        assertEquals(1f, bottom.alpha)
        val (nan, _) = loginScrimColors(LoginBackground(painter, scrimTopAlpha = Float.NaN))
        assertEquals(0f, nan.alpha)
    }

    @Test
    fun `degrade escuro deixa os icones das barras claros e o claro os deixa escuros`() {
        assertFalse(needsDarkSystemBarIcons(loginSystemBarsBackground(LoginBackground(painter))))
        val claro = LoginBackground(painter, scrimColor = Color.White.copy(alpha = 0.2f))
        assertEquals(1f, loginSystemBarsBackground(claro).alpha)
        assertTrue(needsDarkSystemBarIcons(loginSystemBarsBackground(claro)))
    }
}
