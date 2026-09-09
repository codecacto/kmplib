package br.com.codecacto.kmplib.video

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * `PlayerView` do `media3-ui`, **com os controles dele desligados**.
 *
 * Por que a view do Media3 e não um `SurfaceView` cru: ela resolve três coisas que ninguém quer
 * reescrever — a troca de superfície entre faixas de resolução diferentes (o HLS troca de variante
 * no meio da aula), a razão de aspecto do vídeo dentro do retângulo que o Compose deu, e o
 * `SubtitleView`, que desenha a legenda **embutida** com o estilo de legenda que o usuário
 * configurou no Android (tamanho, contraste — acessibilidade que não se reimplementa).
 *
 * `useController = false` porque os controles são os nossos, em Compose: os do `PlayerView` são
 * views Android e ficariam **por cima** de qualquer sobreposição do app, inclusive da marca d'água.
 */
@OptIn(UnstableApi::class)
@Composable
actual fun VideoSurface(state: VideoPlayerState, modifier: Modifier) {
    val player = state.exoPlayerOrNull()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                // O vídeo cabe inteiro no retângulo, sem cortar. Quem decide o formato da caixa é
                // o app, pelo `modifier` (16:9 numa aula).
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { view -> view.player = player },
        // Soltar o player da VIEW no descarte. Sem isto o `PlayerView` segura a superfície e o
        // ExoPlayer, e a tela seguinte herda um quadro congelado do vídeo anterior.
        onRelease = { view -> view.player = null },
    )
}
