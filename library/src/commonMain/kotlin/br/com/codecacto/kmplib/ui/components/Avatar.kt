package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.core.format.initialsOf
import kotlin.math.abs

/**
 * Avatar circular com iniciais ou imagem fornecida.
 *
 * Sem imagem: mostra iniciais (`initialsOf(name)`) com cor de fundo derivada
 * deterministicamente do nome (cores estáveis entre execuções).
 *
 * Com imagem: passe um composable em [image]; renderiza ele dentro do círculo.
 *
 * ```kotlin
 * // Só iniciais
 * Avatar(name = "Joao Silva", size = 48.dp)
 *
 * // Com foto (ex.: Coil/Kamel)
 * Avatar(name = user.name, size = 56.dp) {
 *     AsyncImage(model = user.photoUrl, contentDescription = null,
 *                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
 * }
 * ```
 */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    backgroundColor: Color = colorForName(name),
    textColor: Color = Color.White,
    image: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            image()
        } else {
            Text(
                text = initialsOf(name),
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.4f).sp
            )
        }
    }
}

/**
 * Paleta determinística de cores para fundo de avatar, baseada no nome.
 * Cores escolhidas com contraste adequado para texto branco.
 */
private val avatarPalette = listOf(
    Color(0xFF6366F1), // indigo
    Color(0xFF8B5CF6), // violet
    Color(0xFFEC4899), // pink
    Color(0xFFEF4444), // red
    Color(0xFFF59E0B), // amber
    Color(0xFF10B981), // emerald
    Color(0xFF06B6D4), // cyan
    Color(0xFF3B82F6), // blue
    Color(0xFF14B8A6), // teal
    Color(0xFFA855F7)  // purple
)

/** Cor estável derivada do nome (hash → índice da paleta). */
fun colorForName(name: String): Color {
    if (name.isBlank()) return avatarPalette.first()
    val hash = name.trim().lowercase().hashCode()
    return avatarPalette[abs(hash) % avatarPalette.size]
}
