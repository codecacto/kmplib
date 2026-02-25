package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Item da barra de navegação inferior
 *
 * @param icon Ícone do item
 * @param label Texto do item
 * @param route Rota de navegação
 * @param badge Contador de notificações (null = sem badge)
 */
data class BottomNavItem(
    val icon: ImageVector,
    val label: String,
    val route: String,
    val badge: Int? = null
)

/**
 * Barra de navegação inferior customizável
 *
 * @param items Lista de itens de navegação
 * @param selectedRoute Rota atualmente selecionada
 * @param onItemClick Callback quando um item é clicado
 * @param modifier Modificador customizado
 * @param containerColor Cor de fundo da barra
 * @param contentColor Cor do conteúdo não selecionado
 * @param selectedContentColor Cor do conteúdo selecionado
 * @param indicatorColor Cor do indicador de seleção
 * @param tonalElevation Elevação tonal
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
    tonalElevation: Dp = 3.dp
) {
    NavigationBar(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation
    ) {
        items.forEach { item ->
            val selected = item.route == selectedRoute

            NavigationBarItem(
                icon = {
                    if (item.badge != null && item.badge > 0) {
                        // Ícone com badge
                        BadgedBox(
                            badge = {
                                AppBadge(
                                    count = item.badge,
                                    size = 16.dp,
                                    fontSize = 10.sp
                                )
                            }
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label
                            )
                        }
                    } else {
                        // Ícone sem badge
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label
                        )
                    }
                },
                label = {
                    Text(text = item.label)
                },
                selected = selected,
                onClick = { onItemClick(item) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = selectedContentColor,
                    selectedTextColor = selectedContentColor,
                    indicatorColor = indicatorColor,
                    unselectedIconColor = contentColor,
                    unselectedTextColor = contentColor
                )
            )
        }
    }
}
