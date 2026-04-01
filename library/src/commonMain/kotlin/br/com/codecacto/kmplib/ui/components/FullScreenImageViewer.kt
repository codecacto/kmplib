package br.com.codecacto.kmplib.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Container com suporte a pinch-to-zoom e pan (arrastar).
 *
 * Envolva qualquer conteudo com este composable para adicionar gestos de zoom:
 * - Pinch-to-zoom (dois dedos)
 * - Pan/arrastar quando zoom > 1x
 * - Duplo toque para alternar entre zoom 1x e 2.5x
 *
 * Uso:
 * ```
 * ZoomableBox {
 *     Image(painter = myPainter, contentDescription = null)
 * }
 * ```
 *
 * @param modifier Modifier para o container
 * @param minScale Escala minima permitida (padrao: 1f)
 * @param maxScale Escala maxima permitida (padrao: 5f)
 * @param content Conteudo a ser exibido com zoom
 */
@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    minScale: Float = 1f,
    maxScale: Float = 5f,
    content: @Composable BoxScope.() -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                    scale = newScale

                    if (newScale > 1f) {
                        // Limitar o pan para nao sair da tela
                        val maxOffsetX = (newScale - 1f) * size.width / 2f
                        val maxOffsetY = (newScale - 1f) * size.height / 2f
                        offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                        offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.1f) {
                            // Reset zoom
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            // Zoom in
                            scale = 2.5f
                        }
                    }
                )
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Visualizador de imagem em tela cheia com pinch-to-zoom.
 *
 * Exibe um Dialog fullscreen com fundo escuro, suporte a zoom e botao de fechar.
 *
 * Uso:
 * ```
 * var showViewer by remember { mutableStateOf(false) }
 *
 * // Thumbnail clicavel
 * AsyncImage(
 *     model = imageUrl,
 *     modifier = Modifier.clickable { showViewer = true }
 * )
 *
 * // Viewer fullscreen
 * if (showViewer) {
 *     FullScreenImageViewer(onDismiss = { showViewer = false }) {
 *         AsyncImage(
 *             model = imageUrl,
 *             contentDescription = null,
 *             modifier = Modifier.fillMaxSize(),
 *             contentScale = ContentScale.Fit
 *         )
 *     }
 * }
 * ```
 *
 * @param onDismiss Callback chamado ao fechar o viewer (botao X ou toque no fundo)
 * @param content Conteudo da imagem (ex: AsyncImage, Image, etc.)
 */
@Composable
fun FullScreenImageViewer(
    onDismiss: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Imagem com zoom
            ZoomableBox(
                modifier = Modifier.fillMaxSize()
            ) {
                content()
            }

            // Botao fechar
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Fechar"
                )
            }
        }
    }
}
