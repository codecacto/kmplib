package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Estilo do badge
 */
enum class BadgeStyle {
    CIRCULAR,  // Badge circular (para números)
    PILL,      // Badge pill/retangular (para texto)
    DOT        // Apenas um ponto (sem texto)
}

/**
 * Badge/Distintivo para notificações e contadores
 *
 * @param count Número a ser exibido (usado apenas se text for null)
 * @param modifier Modificador customizado
 * @param text Texto customizado (sobrescreve count se fornecido)
 * @param style Estilo do badge (Circular, Pill ou Dot)
 * @param backgroundColor Cor de fundo do badge
 * @param textColor Cor do texto
 * @param maxCount Contagem máxima a exibir (ex: 99+)
 * @param size Tamanho do badge (usado para DOT style)
 * @param fontSize Tamanho da fonte
 */
@Composable
fun AppBadge(
    count: Int = 0,
    modifier: Modifier = Modifier,
    text: String? = null,
    style: BadgeStyle = BadgeStyle.CIRCULAR,
    backgroundColor: Color = MaterialTheme.colorScheme.error,
    textColor: Color = Color.White,
    maxCount: Int = 99,
    size: Dp = 20.dp,
    fontSize: TextUnit = 11.sp
) {
    when (style) {
        BadgeStyle.DOT -> {
            // Badge simples como ponto
            Box(
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(backgroundColor)
            )
        }

        BadgeStyle.CIRCULAR -> {
            // Badge circular para números
            val displayText = text ?: when {
                count > maxCount -> "$maxCount+"
                count > 0 -> count.toString()
                else -> ""
            }

            if (displayText.isNotEmpty()) {
                Box(
                    modifier = modifier
                        .defaultMinSize(minWidth = size, minHeight = size)
                        .clip(CircleShape)
                        .background(backgroundColor)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayText,
                        color = textColor,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        BadgeStyle.PILL -> {
            // Badge pill/retangular para texto
            val displayText = text ?: count.toString()

            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(backgroundColor)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayText,
                    color = textColor,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Badge sobreposto a outro composable (ex: ícone)
 *
 * @param badge Conteúdo do badge (@Composable)
 * @param modifier Modificador customizado
 * @param content Conteúdo principal (ex: ícone)
 */
@Composable
fun BadgedBox(
    badge: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
        Box(
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            badge()
        }
    }
}
