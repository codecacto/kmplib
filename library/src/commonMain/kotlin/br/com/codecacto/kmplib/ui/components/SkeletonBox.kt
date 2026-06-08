package br.com.codecacto.kmplib.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Placeholder de carregamento com animação de pulsação (shimmer simplificado).
 *
 * O alpha oscila continuamente entre 0.3 e 0.7 para indicar conteúdo em
 * carregamento. Use múltiplos [SkeletonBox] para montar esqueletos de listas,
 * cards e textos.
 *
 * @param modifier Modificador (defina tamanho via `.size`/`.height`/`.fillMaxWidth`)
 * @param shape Forma do placeholder
 * @param color Cor base do placeholder
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp),
    color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val animatedAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )

    Box(
        modifier = modifier
            .clip(shape)
            .alpha(animatedAlpha)
            .background(color, shape)
    )
}
