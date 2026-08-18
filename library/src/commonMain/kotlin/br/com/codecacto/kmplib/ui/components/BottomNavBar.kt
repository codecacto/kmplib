package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Realce visual de um item da [AppBottomNavBar] — a "pill" preenchida que destaca a ação principal
 * (tipicamente **criar/publicar**, no centro da barra).
 *
 * O item realçado **continua sendo um item de navegação normal**: mesma semântica de aba, mesmo
 * label embaixo, mesma regra de seleção e de desabilitado. O que muda é só a apresentação do ícone —
 * ele passa a ser desenhado dentro de um retângulo arredondado preenchido, em vez do indicador
 * padrão do Material (que é suprimido para este item, para não haver dois fundos).
 *
 * **Não é um FAB flutuante**: o realce vive *dentro* da barra, ocupando a mesma célula dos demais
 * itens, e portanto herda o alvo de toque da célula (≥ [BottomNavDefaults.MinTouchTargetSize]).
 * Para um FAB sobreposto, use `Scaffold(floatingActionButton = ...)` — é outro padrão.
 *
 * As cores, quando `null`, vêm do **tema** (parâmetros `emphasis*Color` de [AppBottomNavBar], que por
 * sua vez usam `primaryContainer`/`onPrimaryContainer`). Passe uma cor de marca **do tema**
 * (`AppColors.current.*` / `MaterialTheme.colorScheme.*`) quando quiser outra — nunca um hex solto.
 *
 * @param width Largura da pill. Padrão [BottomNavDefaults.EmphasisWidth] (44dp).
 * @param height Altura da pill. Padrão [BottomNavDefaults.EmphasisHeight] (34dp).
 * @param cornerRadius Raio dos cantos. Padrão [BottomNavDefaults.EmphasisCornerRadius] (14dp).
 * @param iconSize Tamanho do ícone dentro da pill. Padrão [BottomNavDefaults.EmphasisIconSize].
 * @param containerColor Cor de fundo da pill. `null` = cor de realce da barra (tema).
 * @param contentColor Cor do ícone dentro da pill. `null` = cor de conteúdo de realce da barra (tema).
 */
data class BottomNavEmphasis(
    val width: Dp = BottomNavDefaults.EmphasisWidth,
    val height: Dp = BottomNavDefaults.EmphasisHeight,
    val cornerRadius: Dp = BottomNavDefaults.EmphasisCornerRadius,
    val iconSize: Dp = BottomNavDefaults.EmphasisIconSize,
    val containerColor: Color? = null,
    val contentColor: Color? = null
)

/**
 * Estado visual de um item da barra inferior. Derivado por [bottomNavItemState].
 *
 * `Disabled` **vence** `Selected`: um item desligado não deve parecer ativo mesmo que a rota atual
 * coincida (acontece durante uma navegação de volta, ou quando a feature é desligada por flag).
 */
enum class BottomNavItemState { Selected, Unselected, Disabled }

/**
 * Item da barra de navegação inferior
 *
 * @param icon Ícone do item
 * @param label Texto do item — **sempre exibido**; a barra nunca fica só com ícones
 * @param route Rota de navegação
 * @param badge Contador de notificações (null = sem badge)
 * @param emphasis Realce visual (pill preenchida) — `null` (padrão) = item comum. Ver [BottomNavEmphasis]
 * @param enabled `false` desabilita o item (sem clique, cor esmaecida, semântica de desabilitado).
 *   Útil para feature que ainda vai ligar numa próxima onda. Padrão `true`
 * @param contentDescription Descrição do ícone para leitor de tela. `null` (padrão) = usa o [label]
 */
data class BottomNavItem(
    val icon: ImageVector,
    val label: String,
    val route: String,
    val badge: Int? = null,
    val emphasis: BottomNavEmphasis? = null,
    val enabled: Boolean = true,
    val contentDescription: String? = null
) {
    /** Descrição efetiva do ícone para acessibilidade: a customizada, ou o próprio label. */
    val effectiveContentDescription: String get() = contentDescription ?: label

    /** `true` quando há badge com contagem positiva (0 e negativos não desenham badge). */
    val hasBadge: Boolean get() = (badge ?: 0) > 0
}

/** Tokens e defaults da [AppBottomNavBar]. */
object BottomNavDefaults {
    /** Alvo mínimo de toque recomendado (Material 3 / WCAG 2.5.5). A célula do item respeita isto. */
    val MinTouchTargetSize: Dp = 48.dp

    /** Largura padrão da pill de realce. */
    val EmphasisWidth: Dp = 44.dp

    /** Altura padrão da pill de realce. */
    val EmphasisHeight: Dp = 34.dp

    /** Raio padrão dos cantos da pill de realce. */
    val EmphasisCornerRadius: Dp = 14.dp

    /** Tamanho padrão do ícone dentro da pill de realce. */
    val EmphasisIconSize: Dp = 20.dp

    /** Opacidade do CONTEÚDO desabilitado (Material 3 `state-layer` / disabled content). */
    const val DisabledContentAlpha: Float = 0.38f

    /** Opacidade do CONTÊINER desabilitado (Material 3 disabled container). */
    const val DisabledContainerAlpha: Float = 0.12f
}

/**
 * Estado visual do item, dado o item e a rota selecionada.
 *
 * Regra: desabilitado primeiro; depois rota igual = selecionado; senão, não selecionado.
 */
fun bottomNavItemState(item: BottomNavItem, selectedRoute: String): BottomNavItemState = when {
    !item.enabled -> BottomNavItemState.Disabled
    item.route == selectedRoute -> BottomNavItemState.Selected
    else -> BottomNavItemState.Unselected
}

/** Cor de fundo efetiva da pill de realce, já considerando o estado desabilitado. */
internal fun resolveEmphasisContainerColor(
    emphasis: BottomNavEmphasis,
    fallback: Color,
    state: BottomNavItemState
): Color {
    val base = emphasis.containerColor ?: fallback
    return if (state == BottomNavItemState.Disabled) {
        base.copy(alpha = base.alpha * BottomNavDefaults.DisabledContainerAlpha)
    } else {
        base
    }
}

/** Cor do ícone efetiva dentro da pill de realce, já considerando o estado desabilitado. */
internal fun resolveEmphasisContentColor(
    emphasis: BottomNavEmphasis,
    fallback: Color,
    state: BottomNavItemState
): Color {
    val base = emphasis.contentColor ?: fallback
    return if (state == BottomNavItemState.Disabled) {
        base.copy(alpha = base.alpha * BottomNavDefaults.DisabledContentAlpha)
    } else {
        base
    }
}

/**
 * Barra de navegação inferior customizável
 *
 * Suporta **item em destaque** (`BottomNavItem.emphasis`) — a ação principal do app (criar/publicar)
 * desenhada como pill preenchida *dentro* da barra, com o label embaixo como qualquer outro item — e
 * **item desabilitado** (`BottomNavItem.enabled = false`).
 *
 * ```kotlin
 * AppBottomNavBar(
 *     items = listOf(
 *         BottomNavItem(Icons.Default.Home, "Início", "home"),
 *         BottomNavItem(Icons.Default.Search, "Buscar", "buscar"),
 *         BottomNavItem(
 *             icon = Icons.Default.Add,
 *             label = "Publicar",
 *             route = "publicar",
 *             emphasis = BottomNavEmphasis(containerColor = AppColors.current.warning),
 *             enabled = publicacaoLiberada
 *         ),
 *         BottomNavItem(Icons.Default.LocationCity, "Cidade", "cidade"),
 *         BottomNavItem(Icons.Default.Person, "Perfil", "perfil")
 *     ),
 *     selectedRoute = rotaAtual,
 *     onItemClick = { navegar(it.route) }
 * )
 * ```
 *
 * @param items Lista de itens de navegação
 * @param selectedRoute Rota atualmente selecionada
 * @param onItemClick Callback quando um item é clicado (não dispara para item desabilitado)
 * @param modifier Modificador customizado
 * @param containerColor Cor de fundo da barra
 * @param contentColor Cor do conteúdo não selecionado
 * @param selectedContentColor Cor do conteúdo selecionado
 * @param indicatorColor Cor do indicador de seleção (não se aplica a item com realce)
 * @param tonalElevation Elevação tonal
 * @param disabledContentColor Cor do conteúdo de item desabilitado
 * @param emphasisContainerColor Cor de fundo padrão da pill de realce (tema)
 * @param emphasisContentColor Cor do ícone padrão dentro da pill de realce (tema)
 */
@Composable
fun AppBottomNavBar(
    items: List<BottomNavItem>,
    selectedRoute: String,
    onItemClick: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContentColor: Color = MaterialTheme.colorScheme.primary,
    indicatorColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    tonalElevation: Dp = 3.dp,
    disabledContentColor: Color = contentColor.copy(alpha = BottomNavDefaults.DisabledContentAlpha),
    emphasisContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    emphasisContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    NavigationBar(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation
    ) {
        items.forEach { item ->
            val state = bottomNavItemState(item, selectedRoute)
            val emphasis = item.emphasis

            NavigationBarItem(
                icon = {
                    BottomNavItemIcon(
                        item = item,
                        state = state,
                        emphasisContainerColor = emphasisContainerColor,
                        emphasisContentColor = emphasisContentColor
                    )
                },
                label = {
                    Text(text = item.label)
                },
                selected = state == BottomNavItemState.Selected,
                enabled = item.enabled,
                onClick = { onItemClick(item) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = selectedContentColor,
                    selectedTextColor = selectedContentColor,
                    // Item com realce desenha o próprio fundo (a pill): suprimir o indicador do
                    // Material evita dois fundos empilhados no mesmo ícone.
                    indicatorColor = if (emphasis != null) Color.Transparent else indicatorColor,
                    unselectedIconColor = contentColor,
                    unselectedTextColor = contentColor,
                    disabledIconColor = disabledContentColor,
                    disabledTextColor = disabledContentColor
                )
            )
        }
    }
}

/**
 * Ícone de um item da barra: pill preenchida quando há realce, ícone simples caso contrário —
 * envolvido por [BadgedBox] quando há badge. Extraído para manter [AppBottomNavBar] legível.
 */
@Composable
private fun BottomNavItemIcon(
    item: BottomNavItem,
    state: BottomNavItemState,
    emphasisContainerColor: Color,
    emphasisContentColor: Color
) {
    val content: @Composable () -> Unit = {
        val emphasis = item.emphasis
        if (emphasis != null) {
            Box(
                modifier = Modifier
                    .size(width = emphasis.width, height = emphasis.height)
                    .clip(RoundedCornerShape(emphasis.cornerRadius))
                    .background(
                        resolveEmphasisContainerColor(emphasis, emphasisContainerColor, state)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.effectiveContentDescription,
                    tint = resolveEmphasisContentColor(emphasis, emphasisContentColor, state),
                    modifier = Modifier.size(emphasis.iconSize)
                )
            }
        } else {
            Icon(
                imageVector = item.icon,
                contentDescription = item.effectiveContentDescription
            )
        }
    }

    if (item.hasBadge) {
        BadgedBox(
            badge = {
                AppBadge(
                    count = item.badge ?: 0,
                    size = 16.dp,
                    fontSize = 10.sp
                )
            },
            content = { content() }
        )
    } else {
        content()
    }
}
