package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Componente de estado vazio genérico
 *
 * @param icon Ícone a ser exibido
 * @param title Título principal
 * @param modifier Modificador customizado
 * @param description Descrição opcional (subtítulo)
 * @param action Botão/ação opcional
 * @param iconSize Tamanho do ícone
 * @param iconTint Cor do ícone
 * @param titleSize Tamanho do título
 * @param titleColor Cor do título
 * @param descriptionSize Tamanho da descrição
 * @param descriptionColor Cor da descrição
 * @param verticalArrangement Espaçamento vertical entre elementos
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    action: (@Composable () -> Unit)? = null,
    iconSize: Dp = 80.dp,
    // Cores derivadas do TEMA (nunca hex fixo): hex claro fixo somia no dark/tema do app (texto ≈ fundo).
    // Ícone decorativo mais fraco (alpha) que o texto; título/descrição em on-surface p/ contraste AA.
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    titleSize: TextUnit = 18.sp,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    descriptionSize: TextUnit = 14.sp,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(16.dp)
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = verticalArrangement
    ) {
        // Ícone
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = iconTint
        )

        // Título
        Text(
            text = title,
            fontSize = titleSize,
            fontWeight = FontWeight.Medium,
            color = titleColor,
            textAlign = TextAlign.Center
        )

        // Descrição (opcional)
        description?.let {
            Text(
                text = it,
                fontSize = descriptionSize,
                color = descriptionColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Ação (opcional)
        action?.let {
            Spacer(modifier = Modifier.height(8.dp))
            it()
        }
    }
}

/**
 * Variante de EmptyState para tela cheia
 * Centraliza verticalmente no espaço disponível
 */
@Composable
fun FullScreenEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    action: (@Composable () -> Unit)? = null,
    iconSize: Dp = 120.dp,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    titleSize: TextUnit = 20.sp,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    descriptionSize: TextUnit = 16.sp,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        EmptyState(
            icon = icon,
            title = title,
            description = description,
            action = action,
            iconSize = iconSize,
            iconTint = iconTint,
            titleSize = titleSize,
            titleColor = titleColor,
            descriptionSize = descriptionSize,
            descriptionColor = descriptionColor
        )
    }
}
