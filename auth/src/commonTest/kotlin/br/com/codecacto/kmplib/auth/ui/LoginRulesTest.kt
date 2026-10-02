package br.com.codecacto.kmplib.ui.screens.login

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import br.com.codecacto.kmplib.ui.screens.loginColorsFrom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regras puras da tela de login (2.241.0): de onde sai o rótulo do identificador, quando a marca do
 * app é desenhada e como as cores saem do tema.
 */
class LoginRulesTest {

    // ── Rótulo do identificador ──────────────────────────────────────────────────────────────────

    @Test
    fun `sem rotulo do servidor vale o texto local`() {
        assertEquals("Email or username", resolveIdentifierLabel("", "Email or username"))
        assertEquals("Email or username", resolveIdentifierLabel("   ", "Email or username"))
    }

    @Test
    fun `default do servidor em portugues cede ao texto local traduzido`() {
        // O defeito do Meu Estacionamento: o servidor nunca manda vazio — manda o default do modo.
        listOf("E-mail ou usuário", "E-mail", "Usuário", "e-mail ou usuario", " E-MAIL OU USUÁRIO ").forEach {
            assertEquals("Email or username", resolveIdentifierLabel(it, "Email or username"), it)
        }
    }

    @Test
    fun `rotulo proprio do produto continua vencendo`() {
        assertEquals("Matrícula", resolveIdentifierLabel("Matrícula", "Email or username"))
        assertEquals("Código do operador", resolveIdentifierLabel(" Código do operador ", "E-mail ou usuário"))
    }

    // ── Marca do app ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `sem logo e sem titulo a tela desenha a marca do app`() {
        assertTrue(loginShowsAppBrand(appBrand = true, hasLogo = false, hasTitle = false, withBrandPanel = false))
    }

    @Test
    fun `quem ja traz marca nao recebe uma segunda`() {
        assertFalse(loginShowsAppBrand(appBrand = true, hasLogo = true, hasTitle = false, withBrandPanel = false))
        assertFalse(loginShowsAppBrand(appBrand = true, hasLogo = false, hasTitle = true, withBrandPanel = false))
        // Janela expandida: o painel lateral É a marca.
        assertFalse(loginShowsAppBrand(appBrand = true, hasLogo = false, hasTitle = false, withBrandPanel = true))
    }

    @Test
    fun `appBrand false desliga`() {
        assertFalse(loginShowsAppBrand(appBrand = false, hasLogo = false, hasTitle = false, withBrandPanel = false))
    }

    // ── Cores a partir do tema ───────────────────────────────────────────────────────────────────

    @Test
    fun `cores do login saem do esquema do tema - claro e escuro`() {
        val claro = lightColorScheme(primary = Color(0xFF0A7D3B), background = Color(0xFFFAFAFA))
        val escuro = darkColorScheme(primary = Color(0xFF7FE0A2), background = Color(0xFF101410))
        listOf(claro, escuro).forEach { scheme ->
            val colors = loginColorsFrom(scheme)
            assertEquals(scheme.primary, colors.primary)
            assertEquals(scheme.onPrimary, colors.onPrimary)
            assertEquals(scheme.background, colors.background)
            assertEquals(scheme.surface, colors.surface)
            assertEquals(scheme.error, colors.error)
            assertEquals(scheme.onBackground, colors.textPrimary)
            assertEquals(scheme.onSurfaceVariant, colors.textSecondary)
            // A mesma borda do `AppTextField` fora do login.
            assertEquals(scheme.outlineVariant, colors.border)
        }
    }
}
