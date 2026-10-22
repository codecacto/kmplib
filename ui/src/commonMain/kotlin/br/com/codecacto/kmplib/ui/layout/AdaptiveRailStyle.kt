package br.com.codecacto.kmplib.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.WindowSizeClass

/**
 * Como o trilho do [AdaptiveScaffold] desenha os destinos (2.322.0, GAP-VIT-T01).
 *
 * - [WIDE] — rail **largo** (≥ 220 dp), rótulo AO LADO do ícone. É o default da EXPANDIDA desde a
 *   2.114.0 e continua sendo: quem não passa nada não vê mudança.
 * - [LABELED] — trilho estreito (88 dp), rótulo EMBAIXO do ícone. É sempre o da MÉDIA; com
 *   `AdaptiveRailStyle(expandedLayout = LABELED)` passa a ser também o da EXPANDIDA — o que pede todo
 *   protótipo de tablet que mantém o mesmo trilho nas duas orientações (Vitalis: 88 dp nas duas).
 */
enum class AdaptiveRailLayout { WIDE, LABELED }

/**
 * Layout efetivo do trilho para a classe de janela: MÉDIA é sempre [AdaptiveRailLayout.LABELED]; a
 * EXPANDIDA segue [expandedLayout]. `null` em COMPACTA (não há trilho — é a barra inferior). Pura.
 */
fun adaptiveRailLayoutFor(
    windowSizeClass: WindowSizeClass,
    expandedLayout: AdaptiveRailLayout,
): AdaptiveRailLayout? = when (windowSizeClass) {
    WindowSizeClass.COMPACTA -> null
    WindowSizeClass.MEDIA -> AdaptiveRailLayout.LABELED
    WindowSizeClass.EXPANDIDA -> expandedLayout
}

/**
 * Desenho de um item do trilho na anatomia do `NavigationRailItem` do Material 3: **indicador** atrás
 * do ícone (pílula 56×32), rótulo embaixo — ou, no [AdaptiveRailLayout.WIDE], o indicador atrás da
 * linha inteira. Cores `Color.Unspecified` caem nos papéis do M3 (`secondaryContainer`,
 * `onSecondaryContainer`, `onSurfaceVariant`), lidos do tema do app.
 *
 * O protótipo do Vitalis, por exemplo:
 * ```kotlin
 * AdaptiveRailItemStyle(
 *     selectedIndicatorColor = cores.primariaSuave,
 *     selectedIconColor = cores.primaria,
 *     selectedLabelColor = cores.primaria,
 *     unselectedIconColor = cores.textoSecundario,
 *     unselectedLabelColor = cores.textoApagado,
 *     labelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
 * )
 * ```
 *
 * Estado nunca é só cor (WCAG 1.4.1): o ativo continua com o ícone preenchido e o rótulo mais pesado
 * ([selectedLabelFontWeight]).
 */
@Immutable
data class AdaptiveRailItemStyle(
    val indicatorShape: Shape = RoundedCornerShape(16.dp),
    val indicatorWidth: Dp = 56.dp,
    val indicatorHeight: Dp = 32.dp,
    val selectedIndicatorColor: Color = Color.Unspecified,
    val selectedIconColor: Color = Color.Unspecified,
    val unselectedIconColor: Color = Color.Unspecified,
    val selectedLabelColor: Color = Color.Unspecified,
    val unselectedLabelColor: Color = Color.Unspecified,
    /** Estilo do rótulo; `null` = `labelMedium` do tema (LABELED) / `labelLarge` (WIDE). */
    val labelStyle: TextStyle? = null,
    /** Peso do rótulo ativo; `null` = o do [labelStyle]. */
    val selectedLabelFontWeight: FontWeight? = FontWeight.SemiBold,
    /** Largura do item no [AdaptiveRailLayout.LABELED] (o alvo de toque). */
    val itemWidth: Dp = 72.dp,
    val iconSize: Dp = 24.dp,
    /** Espaço entre o indicador e o rótulo no LABELED. */
    val labelSpacing: Dp = 4.dp,
)

/**
 * Desenho do trilho (contêiner) do [AdaptiveScaffold]. O default reproduz o trilho de sempre.
 *
 * @param expandedLayout layout na EXPANDIDA ([AdaptiveRailLayout]).
 * @param width largura fixa do trilho; `Dp.Unspecified` = 88 dp (LABELED) / mínimo 220 dp (WIDE).
 * @param containerColor fundo; `Color.Unspecified` = `surfaceContainerLow`.
 * @param dividerColor borda de 1 dp entre o trilho e o conteúdo; `Color.Unspecified` = sem borda.
 * @param itemSpacing espaço entre os destinos.
 * @param verticalPadding respiro no topo e no fim do trilho (depois dos insets).
 * @param item desenho do item; `null` = o desenho de sempre da lib.
 */
@Immutable
data class AdaptiveRailStyle(
    val expandedLayout: AdaptiveRailLayout = AdaptiveRailLayout.WIDE,
    val width: Dp = Dp.Unspecified,
    val containerColor: Color = Color.Unspecified,
    val dividerColor: Color = Color.Unspecified,
    val itemSpacing: Dp = 4.dp,
    val verticalPadding: Dp = 12.dp,
    val item: AdaptiveRailItemStyle? = null,
)

/**
 * O que o slot `railItem` do [AdaptiveScaffold] recebe para desenhar UM destino do trilho do jeito do
 * protótipo do projeto.
 *
 * A lib continua dona do que não é desenho: o toque (`selectable` com papel de aba), o id
 * `nav-item-<id>`, o alvo do tour, a descrição do selo para o leitor de tela e a ordem de foco. O
 * slot só pinta — por isso não recebe `onClick`.
 *
 * [NavIcon] desenha o ícone certo (preenchido/vazado) com o selo da lib por cima; use-o para não
 * reescrever o selo.
 */
@Stable
class AdaptiveRailItemScope internal constructor(
    val destination: AdaptiveDestination,
    val selected: Boolean,
    val layout: AdaptiveRailLayout,
) {
    /** Ícone do destino (ativo = `selectedIcon`, inativo = `unselectedIcon`) com o selo da lib. */
    @Composable
    fun NavIcon(tint: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
        Box(modifier) {
            AdaptiveBadgedIcon(destination.id, destination.badge) {
                Icon(
                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(size),
                )
            }
        }
    }
}
