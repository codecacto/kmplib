package br.com.codecacto.kmplib.ui.layout

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import br.com.codecacto.kmplib.ui.theme.WindowSizeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Regras puras do `SupportingPaneScaffold` (2.322.0, GAP-VIT-T02). */
class SupportingPaneScaffoldTest {

    @Test
    fun `expandida mostra ao lado ou recolhido conforme o estado`() {
        assertEquals(SupportingPaneMode.SIDE_BY_SIDE, supportingPaneModeFor(WindowSizeClass.EXPANDIDA, expandedOpen = true, overlayOpen = false))
        assertEquals(SupportingPaneMode.COLLAPSED, supportingPaneModeFor(WindowSizeClass.EXPANDIDA, expandedOpen = false, overlayOpen = false))
    }

    @Test
    fun `nao recolhivel nunca recolhe na expandida`() {
        assertEquals(
            SupportingPaneMode.SIDE_BY_SIDE,
            supportingPaneModeFor(WindowSizeClass.EXPANDIDA, expandedOpen = false, overlayOpen = false, collapsible = false),
        )
    }

    @Test
    fun `aberto ao lado na paisagem nao vira sobreposto no retrato`() {
        // Os dois estados são separados: o painel ao lado não pode cobrir o editor ao girar.
        assertEquals(SupportingPaneMode.HIDDEN, supportingPaneModeFor(WindowSizeClass.MEDIA, expandedOpen = true, overlayOpen = false))
        assertEquals(SupportingPaneMode.OVERLAY, supportingPaneModeFor(WindowSizeClass.MEDIA, expandedOpen = false, overlayOpen = true))
        assertEquals(SupportingPaneMode.OVERLAY, supportingPaneModeFor(WindowSizeClass.COMPACTA, expandedOpen = true, overlayOpen = true))
    }

    @Test
    fun `aba invalida cai na primeira e sem abas nao ha aba`() {
        val abas = listOf(
            SupportingPaneTab("historico", "Histórico", Icons.Filled.Home),
            SupportingPaneTab("exames", "Exames", Icons.Filled.Home),
        )
        assertEquals("exames", resolveSupportingPaneTab(abas, "exames"))
        assertEquals("historico", resolveSupportingPaneTab(abas, "sumiu"))
        assertEquals("historico", resolveSupportingPaneTab(abas, null))
        assertNull(resolveSupportingPaneTab(emptyList(), "exames"))
    }

    @Test
    fun `arrastar fecha por distancia ou por velocidade`() {
        assertFalse(shouldDismissSupportingPane(dragOffsetPx = 100f, velocityPx = 0f, paneWidthPx = 400f))
        assertTrue(shouldDismissSupportingPane(dragOffsetPx = 150f, velocityPx = 0f, paneWidthPx = 400f))
        assertTrue(shouldDismissSupportingPane(dragOffsetPx = 10f, velocityPx = 2000f, paneWidthPx = 400f))
        // Arrastar para DENTRO (velocidade negativa) não fecha.
        assertFalse(shouldDismissSupportingPane(dragOffsetPx = 10f, velocityPx = -3000f, paneWidthPx = 400f))
    }

    @Test
    fun `estado abre na aba pedida e fecha cada modo separadamente`() {
        val s = SupportingPaneState()
        assertTrue(s.isExpandedOpen)
        assertFalse(s.isOverlayOpen)
        s.collapse()
        assertFalse(s.isExpandedOpen)
        s.expand("exames")
        assertTrue(s.isExpandedOpen)
        assertEquals("exames", s.selectedTabId)
        s.openOverlay("arquivos")
        assertTrue(s.isOverlayOpen)
        assertEquals("arquivos", s.selectedTabId)
        s.closeOverlay()
        assertFalse(s.isOverlayOpen)
        assertTrue(s.isExpandedOpen)
    }
}
