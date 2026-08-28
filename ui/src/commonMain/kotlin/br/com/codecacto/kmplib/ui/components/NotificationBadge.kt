package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Icone de sino com badge de contagem sobreposto.
 *
 * @param count Numero de notificacoes a exibir
 * @param onClick Callback de clique
 * @param modifier Modificador customizado
 * @param iconTint Cor do icone do sino
 * @param badgeColor Cor de fundo do badge
 * @param badgeTextColor Cor do texto do badge
 */
@Composable
fun NotificationBadge(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.White,
    badgeColor: Color = MaterialTheme.colorScheme.error,
    badgeTextColor: Color = Color.White,
) {
    Box(modifier = modifier) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Notificacoes",
                tint = iconTint
            )
        }

        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = 4.dp)
                    .size(if (count > 9) 20.dp else 18.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (count > 99) "99+" else count.toString(),
                    color = badgeTextColor,
                    fontSize = if (count > 9) 9.sp else 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
