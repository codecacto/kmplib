package br.com.codecacto.kmplib.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.addPathNodes
import br.com.codecacto.kmplib.ui.theme.ColorContrast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WhatsAppIconButtonTest {

    @Test
    fun `o glifo oficial tem os seis arcos com as flags certas`() {
        val nodes = addPathNodes(WHATSAPP_ICON_PATH)
        val arcs = nodes.filter { it is PathNode.RelativeArcTo || it is PathNode.ArcTo }
        assertEquals(6, arcs.size)
        val flags = arcs.map { node ->
            when (node) {
                is PathNode.RelativeArcTo -> node.isMoreThanHalf to node.isPositiveArc
                is PathNode.ArcTo -> node.isMoreThanHalf to node.isPositiveArc
                else -> error("não é arco")
            }
        }
        assertEquals(
            listOf(false to true, false to true, false to true, false to false, false to false, false to false),
            flags,
        )
        assertTrue(nodes.last() is PathNode.Close)
    }

    @Test
    fun `o glifo tem o viewport 24`() {
        assertEquals(24f, WhatsAppGlyph.viewportWidth)
        assertEquals(24f, WhatsAppGlyph.viewportHeight)
    }

    @Test
    fun `fundo escuro usa o verde da marca`() {
        assertEquals(WhatsAppColors.Green, whatsAppIconTint(Color(0xFF121212)))
        assertEquals(WhatsAppColors.Green, whatsAppIconTint(Color.Black))
    }

    @Test
    fun `fundo claro usa o verde azulado com contraste de objeto grafico`() {
        val tint = whatsAppIconTint(Color.White)
        assertEquals(WhatsAppColors.TealGreen, tint)
        assertTrue(ColorContrast.contrastRatio(tint, Color.White) >= 3.0)
    }

    @Test
    fun `a cor escolhida sempre tem contraste minimo em fundos comuns`() {
        listOf(Color.White, Color(0xFFF5F5F5), Color(0xFFFFFBFE), Color(0xFF1C1B1F), Color.Black).forEach { bg ->
            assertTrue(ColorContrast.contrastRatio(whatsAppIconTint(bg), bg) >= 3.0, "fundo $bg")
        }
    }
}
