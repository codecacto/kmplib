package br.com.codecacto.kmplib.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.components.BottomNavDefaults
import br.com.codecacto.kmplib.ui.components.BottomNavEmphasis
import br.com.codecacto.kmplib.ui.components.BottomNavItem
import br.com.codecacto.kmplib.ui.components.BottomNavItemState
import br.com.codecacto.kmplib.ui.components.bottomNavItemState
import br.com.codecacto.kmplib.ui.components.resolveEmphasisContainerColor
import br.com.codecacto.kmplib.ui.components.resolveEmphasisContentColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Item em destaque + item desabilitado da [br.com.codecacto.kmplib.ui.components.AppBottomNavBar].
 */
class BottomNavEmphasisTest {

    private fun item(
        route: String = "publicar",
        emphasis: BottomNavEmphasis? = null,
        enabled: Boolean = true,
        badge: Int? = null,
        contentDescription: String? = null
    ) = BottomNavItem(
        icon = Icons.Default.Add,
        label = "Publicar",
        route = route,
        badge = badge,
        emphasis = emphasis,
        enabled = enabled,
        contentDescription = contentDescription
    )

    // --- Retrocompatibilidade -------------------------------------------------------------

    @Test
    fun `item comum continua sem realce e habilitado`() {
        val comum = BottomNavItem(icon = Icons.Default.Home, label = "Início", route = "home")
        assertNull(comum.emphasis)
        assertTrue(comum.enabled)
        assertNull(comum.contentDescription)
        assertNull(comum.badge)
    }

    @Test
    fun `copy preserva o realce`() {
        val original = item(emphasis = BottomNavEmphasis())
        val comBadge = original.copy(badge = 3)
        assertEquals(original.emphasis, comBadge.emphasis)
        assertEquals(3, comBadge.badge)
    }

    // --- Acessibilidade -------------------------------------------------------------------

    @Test
    fun `contentDescription cai no label quando nao informado`() {
        assertEquals("Publicar", item().effectiveContentDescription)
    }

    @Test
    fun `contentDescription customizado prevalece`() {
        assertEquals(
            "Publicar um anúncio",
            item(contentDescription = "Publicar um anúncio").effectiveContentDescription
        )
    }

    @Test
    fun `alvo de toque minimo segue o Material`() {
        assertEquals(48.dp, BottomNavDefaults.MinTouchTargetSize)
        // A pill é menor que o alvo de toque de propósito: quem recebe o clique é a célula do
        // item, que ocupa a altura da barra — a pill é só o desenho.
        assertTrue(BottomNavDefaults.EmphasisHeight < BottomNavDefaults.MinTouchTargetSize)
    }

    // --- Estado visual --------------------------------------------------------------------

    @Test
    fun `rota igual seleciona o item`() {
        assertEquals(BottomNavItemState.Selected, bottomNavItemState(item(), "publicar"))
    }

    @Test
    fun `rota diferente nao seleciona`() {
        assertEquals(BottomNavItemState.Unselected, bottomNavItemState(item(), "home"))
    }

    @Test
    fun `desabilitado vence selecionado`() {
        assertEquals(
            BottomNavItemState.Disabled,
            bottomNavItemState(item(enabled = false), "publicar")
        )
    }

    @Test
    fun `desabilitado tambem vence quando a rota difere`() {
        assertEquals(
            BottomNavItemState.Disabled,
            bottomNavItemState(item(enabled = false), "home")
        )
    }

    // --- Badge ----------------------------------------------------------------------------

    @Test
    fun `badge so aparece com contagem positiva`() {
        assertFalse(item().hasBadge)
        assertFalse(item(badge = 0).hasBadge)
        assertFalse(item(badge = -1).hasBadge)
        assertTrue(item(badge = 1).hasBadge)
    }

    // --- Tokens de forma ------------------------------------------------------------------

    @Test
    fun `pill usa os tokens padrao`() {
        val emphasis = BottomNavEmphasis()
        assertEquals(BottomNavDefaults.EmphasisWidth, emphasis.width)
        assertEquals(BottomNavDefaults.EmphasisHeight, emphasis.height)
        assertEquals(BottomNavDefaults.EmphasisCornerRadius, emphasis.cornerRadius)
        assertEquals(BottomNavDefaults.EmphasisIconSize, emphasis.iconSize)
        assertEquals(44.dp, emphasis.width)
        assertEquals(34.dp, emphasis.height)
        assertEquals(14.dp, emphasis.cornerRadius)
    }

    @Test
    fun `pill aceita forma customizada`() {
        val emphasis = BottomNavEmphasis(width = 56.dp, height = 40.dp, cornerRadius = 20.dp)
        assertEquals(56.dp, emphasis.width)
        assertEquals(40.dp, emphasis.height)
        assertEquals(20.dp, emphasis.cornerRadius)
    }

    // --- Cores --------------------------------------------------------------------------

    private val temaContainer = Color(0xFF116B3A)
    private val temaConteudo = Color(0xFFFFFFFF)
    private val marca = Color(0xFFF0BB3B)

    @Test
    fun `sem cor propria a pill usa a cor do tema`() {
        val emphasis = BottomNavEmphasis()
        assertEquals(
            temaContainer,
            resolveEmphasisContainerColor(emphasis, temaContainer, BottomNavItemState.Unselected)
        )
        assertEquals(
            temaConteudo,
            resolveEmphasisContentColor(emphasis, temaConteudo, BottomNavItemState.Selected)
        )
    }

    @Test
    fun `cor propria prevalece sobre a do tema`() {
        val emphasis = BottomNavEmphasis(containerColor = marca, contentColor = Color.Black)
        assertEquals(
            marca,
            resolveEmphasisContainerColor(emphasis, temaContainer, BottomNavItemState.Selected)
        )
        assertEquals(
            Color.Black,
            resolveEmphasisContentColor(emphasis, temaConteudo, BottomNavItemState.Selected)
        )
    }

    @Test
    fun `desabilitado esmaece contêiner e conteúdo`() {
        val emphasis = BottomNavEmphasis(containerColor = marca, contentColor = Color.Black)

        val container =
            resolveEmphasisContainerColor(emphasis, temaContainer, BottomNavItemState.Disabled)
        assertEquals(marca.red, container.red)
        // `Color` sRGB quantiza os canais em 8 bits — comparar alpha com tolerância.
        assertEquals(BottomNavDefaults.DisabledContainerAlpha, container.alpha, TOLERANCIA_ALPHA)

        val content =
            resolveEmphasisContentColor(emphasis, temaConteudo, BottomNavItemState.Disabled)
        assertEquals(BottomNavDefaults.DisabledContentAlpha, content.alpha, TOLERANCIA_ALPHA)
    }

    @Test
    fun `alpha do desabilitado é relativo à cor original`() {
        val semiTransparente = marca.copy(alpha = 0.5f)
        val emphasis = BottomNavEmphasis(containerColor = semiTransparente)
        val container =
            resolveEmphasisContainerColor(emphasis, temaContainer, BottomNavItemState.Disabled)
        assertEquals(
            0.5f * BottomNavDefaults.DisabledContainerAlpha,
            container.alpha,
            TOLERANCIA_ALPHA
        )
    }

    private companion object {
        /** `Color` sRGB guarda 8 bits por canal: 0.12f volta como 30/255. */
        const val TOLERANCIA_ALPHA = 0.01f
    }
}
