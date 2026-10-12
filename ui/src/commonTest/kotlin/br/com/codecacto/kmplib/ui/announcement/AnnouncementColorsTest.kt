package br.com.codecacto.kmplib.ui.announcement

import androidx.compose.ui.graphics.Color
import br.com.codecacto.kmplib.ui.theme.ColorContrast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnnouncementColorsTest {

    private val accents = listOf(
        Color(0xFFF59E0B), // warning padrão (âmbar)
        Color(0xFF3B82F6), // info padrão
        Color(0xFFEAB308), // amarelo de marca — o caso difícil
        Color(0xFF16A34A),
        Color(0xFF7C3AED),
        Color(0xFF0F172A), // azul-marinho quase preto
        Color(0xFFFFFFFF),
    )

    private data class Theme(val surface: Color, val onSurface: Color, val onSurfaceVariant: Color)

    private val themes = listOf(
        Theme(Color(0xFFFFFFFF), Color(0xFF1C1B1F), Color(0xFF49454F)), // claro
        Theme(Color(0xFF121212), Color(0xFFE6E1E5), Color(0xFFCAC4D0)), // escuro
    )

    @Test
    fun todo_texto_e_icone_atinge_aa_nos_temas_claro_e_escuro() {
        for (theme in themes) for (accent in accents) {
            val c = announcementColors(accent, theme.surface, theme.onSurface, theme.onSurfaceVariant)
            val ctx = "accent=$accent surface=${theme.surface}"
            assertTrue(ColorContrast.contrastRatio(c.content, c.container) >= ColorContrast.AA_TEXT, "content $ctx")
            assertTrue(ColorContrast.contrastRatio(c.secondaryContent, c.container) >= ColorContrast.AA_TEXT, "secondary $ctx")
            assertTrue(ColorContrast.contrastRatio(c.accent, c.container) >= ColorContrast.AA_TEXT, "accent $ctx")
            assertTrue(ColorContrast.contrastRatio(c.icon, c.iconContainer) >= ColorContrast.AA_GRAPHIC, "icon $ctx")
            assertTrue(ColorContrast.contrastRatio(c.actionContent, c.iconContainer) >= ColorContrast.AA_TEXT, "action $ctx")
        }
    }

    @Test
    fun cores_saem_opacas_mesmo_com_superficie_translucida() {
        val c = announcementColors(Color(0xFF3B82F6), Color(0x80FFFFFF), Color(0xFF1C1B1F), Color(0xFF49454F))
        assertEquals(1f, c.container.alpha)
        assertEquals(1f, c.iconContainer.alpha)
    }

    @Test
    fun cada_tipo_tem_icone_proprio_e_prioridade_unica() {
        assertEquals(AnnouncementKind.entries.size, AnnouncementKind.entries.map { announcementIcon(it) }.toSet().size)
        assertEquals(AnnouncementKind.entries.size, AnnouncementKind.entries.map { it.announcementPriority }.toSet().size)
    }
}
