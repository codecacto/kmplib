package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Overlay de loading fullscreen com fundo escurecido.
 *
 * - Bloqueia interação com a UI por trás (capta clicks).
 * - Mostra spinner e texto opcional em card.
 *
 * Coloque por cima do conteúdo (em um `Box`):
 *
 * ```kotlin
 * Box(Modifier.fillMaxSize()) {
 *     MyScreen(state, onAction)
 *     LoadingOverlay(show = state.isLoading, text = "Salvando...")
 * }
 * ```
 *
 * Para loading inline (não bloqueia tela toda), use `CircularProgressIndicator` direto.
 */
@Composable
fun LoadingOverlay(
    show: Boolean,
    modifier: Modifier = Modifier,
    text: String? = null,
    scrimColor: Color = Color.Black.copy(alpha = 0.5f)
) {
    if (!show) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scrimColor)
            .pointerInput(Unit) { /* consome clicks */ },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                if (!text.isNullOrBlank()) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
