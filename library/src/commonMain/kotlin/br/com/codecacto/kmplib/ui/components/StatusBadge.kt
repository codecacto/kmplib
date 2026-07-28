package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.ui.theme.AppColors

@Composable
fun StatusBadge(
    text: String,
    textColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = textColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/**
 * Tom semântico de um selo de status — evita cada tela escolher um hex/token diferente para dizer a
 * MESMA coisa ("ativo" saía charcoal em uma tela e cinza em outra).
 */
enum class StatusTone { SUCCESS, NEUTRAL, WARNING, DANGER, INFO }

/**
 * Resolve um [StatusTone] para o **token de cor** correspondente da camada de tema
 * ([AppColors]/[MaterialTheme.colorScheme]) — nunca `Color(...)` hardcoded.
 *
 * É a **fonte única** do mapeamento tom → cor do ecossistema: [StatusBadge], [ChecklistItem] e
 * [AppBanner] leem daqui, para que "SUCCESS" seja o MESMO verde em qualquer componente e em qualquer
 * paleta do app. Também é público para a tela que precise tingir um elemento próprio de forma coerente
 * (ex.: um ícone ao lado de um selo).
 *
 * `NEUTRAL` mapeia para `onSurfaceVariant` — a "ausência de tom" (cinza do tema), não uma cor de marca.
 */
@Composable
fun statusToneColor(tone: StatusTone): Color = when (tone) {
    StatusTone.SUCCESS -> AppColors.current.success
    StatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    StatusTone.WARNING -> AppColors.current.warning
    StatusTone.DANGER -> MaterialTheme.colorScheme.error
    StatusTone.INFO -> AppColors.current.info
}

/**
 * `StatusBadge` **semântico**: você diz o significado, a lib resolve a cor pelo tema (com o fundo em
 * 15% de opacidade, padrão dos selos do ecossistema).
 *
 * Use este overload para o par ativo/inativo, o mais comum de todos: `SUCCESS` (verde de
 * `AppColors.current.success`) para ativo, `NEUTRAL` para inativo.
 *
 * ```kotlin
 * StatusBadge("Ativo", if (item.active) StatusTone.SUCCESS else StatusTone.NEUTRAL)
 * ```
 */
@Composable
fun StatusBadge(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val color = statusToneColor(tone)
    StatusBadge(
        text = text,
        textColor = color,
        backgroundColor = color.copy(alpha = 0.15f),
        modifier = modifier,
    )
}
