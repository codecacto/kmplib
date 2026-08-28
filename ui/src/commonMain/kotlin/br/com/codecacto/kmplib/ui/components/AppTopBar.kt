package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tipo de navegação na TopBar
 */
enum class NavigationType {
    NONE,       // Sem botão de navegação
    BACK,       // Botão voltar
    MENU        // Botão de menu (hambúrguer)
}

/**
 * Barra superior de navegação customizável
 *
 * @param title Título da tela
 * @param modifier Modificador customizado
 * @param navigationType Tipo de botão de navegação
 * @param navigationIcon Ícone customizado de navegação (sobrescreve navigationType)
 * @param onNavigationClick Callback do botão de navegação
 * @param actions Ações à direita (ícones, botões)
 * @param centerTitle Se o título deve ser centralizado
 * @param backgroundColor Cor de fundo
 * @param contentColor Cor do conteúdo (texto e ícones)
 * @param elevation Elevação da barra
 * @param titleSize Tamanho da fonte do título
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationType: NavigationType = NavigationType.NONE,
    navigationIcon: ImageVector? = null,
    onNavigationClick: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    centerTitle: Boolean = false,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    elevation: Dp = 0.dp,
    titleSize: TextUnit = 20.sp
) {
    val navIcon: ImageVector? = navigationIcon ?: when (navigationType) {
        NavigationType.BACK -> Icons.AutoMirrored.Filled.ArrowBack
        NavigationType.MENU -> Icons.Default.Menu
        NavigationType.NONE -> null
    }

    TopAppBar(
        title = {
            if (centerTitle) {
                // Título centralizado
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontSize = titleSize,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                // Título à esquerda
                Text(
                    text = title,
                    fontSize = titleSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        modifier = modifier,
        navigationIcon = {
            navIcon?.let {
                IconButton(onClick = onNavigationClick) {
                    Icon(
                        imageVector = it,
                        contentDescription = when (navigationType) {
                            NavigationType.BACK -> "Voltar"
                            NavigationType.MENU -> "Menu"
                            NavigationType.NONE -> null
                        }
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = backgroundColor,
            titleContentColor = contentColor,
            navigationIconContentColor = contentColor,
            actionIconContentColor = contentColor
        )
    )
}

/**
 * TopBar simples apenas com título
 */
@Composable
fun SimpleTopBar(
    title: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    AppTopBar(
        title = title,
        modifier = modifier,
        navigationType = NavigationType.NONE,
        backgroundColor = backgroundColor,
        contentColor = contentColor
    )
}

/**
 * TopBar com botão voltar
 */
@Composable
fun BackTopBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    AppTopBar(
        title = title,
        modifier = modifier,
        navigationType = NavigationType.BACK,
        onNavigationClick = onBackClick,
        actions = actions,
        backgroundColor = backgroundColor,
        contentColor = contentColor
    )
}

/**
 * TopBar com menu (hambúrguer)
 */
@Composable
fun MenuTopBar(
    title: String,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    AppTopBar(
        title = title,
        modifier = modifier,
        navigationType = NavigationType.MENU,
        onNavigationClick = onMenuClick,
        actions = actions,
        backgroundColor = backgroundColor,
        contentColor = contentColor
    )
}
