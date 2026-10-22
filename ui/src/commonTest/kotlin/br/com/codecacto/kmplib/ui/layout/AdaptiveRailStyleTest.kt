package br.com.codecacto.kmplib.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.WindowSizeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Trilho desenhável do `AdaptiveScaffold` (2.322.0, GAP-VIT-T01) — a regra pura e a compatibilidade. */
class AdaptiveRailStyleTest {

    @Test
    fun `media e sempre trilho com rotulo embaixo`() {
        AdaptiveRailLayout.entries.forEach {
            assertEquals(AdaptiveRailLayout.LABELED, adaptiveRailLayoutFor(WindowSizeClass.MEDIA, it))
        }
    }

    @Test
    fun `expandida segue o estilo e o default continua rail largo`() {
        assertEquals(AdaptiveRailLayout.WIDE, adaptiveRailLayoutFor(WindowSizeClass.EXPANDIDA, AdaptiveRailStyle().expandedLayout))
        assertEquals(
            AdaptiveRailLayout.LABELED,
            adaptiveRailLayoutFor(WindowSizeClass.EXPANDIDA, AdaptiveRailLayout.LABELED),
        )
    }

    @Test
    fun `compacta nao tem trilho`() {
        assertNull(adaptiveRailLayoutFor(WindowSizeClass.COMPACTA, AdaptiveRailLayout.LABELED))
    }

    @Test
    fun `defaults nao mudam o trilho de quem nao passa estilo`() {
        val padrao = AdaptiveRailStyle()
        assertNull(padrao.item)
        assertEquals(Color.Unspecified, padrao.dividerColor)
        assertEquals(Color.Unspecified, padrao.containerColor)
        assertEquals(4.dp, padrao.itemSpacing)
    }
}

/** Compila = a chamada de antes da 2.322.0 continua resolvendo. Nunca é executada. */
@Suppress("unused")
@Composable
private fun chamadaAntigaDoAdaptiveScaffold() {
    AdaptiveScaffold(
        destinations = listOf(AdaptiveDestination("hoje", "Hoje", Icons.Filled.Home, Icons.Filled.Home)),
        selectedId = "hoje",
        onSelect = {},
    ) { _ -> Box {} }
}

/** Compila = os slots novos e o desenho livre do item. Nunca é executada. */
@Suppress("unused")
@Composable
private fun chamadaComTrilhoDoProjeto() {
    AdaptiveScaffold(
        destinations = listOf(AdaptiveDestination("hoje", "Hoje", Icons.Filled.Home, Icons.Filled.Home)),
        selectedId = "hoje",
        onSelect = {},
        railHeader = { Text("V") },
        railFooter = { Text("Config") },
        railStyle = AdaptiveRailStyle(
            expandedLayout = AdaptiveRailLayout.LABELED,
            width = 88.dp,
            item = AdaptiveRailItemStyle(selectedIndicatorColor = Color.Cyan),
        ),
        railItem = {
            NavIcon(tint = if (selected) Color.Blue else Color.Gray)
            Text(destination.label)
        },
    ) { _ -> Box {} }
}
