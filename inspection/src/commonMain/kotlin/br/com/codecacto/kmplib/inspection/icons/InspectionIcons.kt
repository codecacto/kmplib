package br.com.codecacto.kmplib.inspection.icons

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Os vetores do [InspectionIcon], montados uma vez e guardados (o `ImageVector` é imutável e pode ser
 * compartilhado entre telas).
 */
object InspectionIcons {
    private val outlinedCache = HashMap<InspectionIcon, ImageVector>()
    private val filledCache = HashMap<InspectionIcon, ImageVector>()

    /** O vetor de [icon] — contorno (`FILL 0`, ação/neutro) ou cheio (`FILL 1`, chip, tile, estado). */
    fun vector(icon: InspectionIcon, filled: Boolean = false): ImageVector {
        val cache = if (filled) filledCache else outlinedCache
        return cache.getOrPut(icon) { buildSymbol(icon.symbol + if (filled) "_fill1" else "", if (filled) icon.filled else icon.outlined) }
    }

    /** O vetor pelo nome do símbolo (`"check_circle"`); `null` se não faz parte do subconjunto. */
    fun bySymbol(symbol: String, filled: Boolean = false): ImageVector? =
        InspectionIcon.entries.firstOrNull { it.symbol == symbol }?.let { vector(it, filled) }
}

/**
 * Converte um caminho do Material Symbols (viewBox `0 -960 960 960`) num [ImageVector] 24 dp.
 * A janela do SVG começa em y = −960; a translação de +960 a traz para a janela do vetor.
 */
internal fun buildSymbol(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = SYMBOL_VIEWPORT,
        viewportHeight = SYMBOL_VIEWPORT,
    ).addGroup(translationY = SYMBOL_VIEWPORT)
        .addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
        .clearGroup()
        .build()

private const val SYMBOL_VIEWPORT = 960f

/**
 * Um símbolo do kit. Decorativo por padrão (`contentDescription = null`): no kit o ícone sempre vem
 * acompanhado de texto, e o texto é que é lido.
 *
 * @param filled `FILL 1` (dentro de chip, tile selecionado, ícone de estado) ou `FILL 0` (ação).
 * @param size 16 (chip) · 17–18 (tile/linha) · 20–22 (ação) · 24 (barra) · 44 (hero).
 */
@Composable
fun InspectionIconView(
    icon: InspectionIcon,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    Icon(
        imageVector = InspectionIcons.vector(icon, filled),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}
