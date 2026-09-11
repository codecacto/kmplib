package br.com.codecacto.kmplib.video.feed

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState

/**
 * A superfície do vídeo de feed — **a integração Compose oficial da Media3** (`media3-ui-compose`).
 *
 * - **`PlayerSurface`** liga e desliga o player da superfície junto com a composição: quando o pool
 *   passa o player para outro item, a superfície deste é solta sozinha.
 * - **`TextureView`, não `SurfaceView`.** O `SurfaceView` é mais econômico, mas é um "furo" na janela
 *   que o clip do Compose não recorta: no modo [FeedVideoScale.Crop] a superfície é MAIOR que a
 *   caixa, e sairia por cima do post vizinho durante a rolagem. É o caso que a própria documentação
 *   da Media3 aponta para o `TextureView` (clip, animação, lista).
 * - **`rememberPresentationState`** diz quando o primeiro quadro chegou (`coverSurface`) — é o sinal
 *   para a capa sair — e o tamanho do vídeo, que o `resizeWithContentScale` usa para cortar/encaixar.
 */
@OptIn(UnstableApi::class)
@Composable
internal actual fun FeedVideoSurface(
    engine: FeedVideoEngine,
    scale: FeedVideoScale,
    modifier: Modifier,
    onFrameVisibleChange: (Boolean) -> Unit,
) {
    val player = (engine as ExoFeedVideoEngine).player
    val apresentacao = rememberPresentationState(player)
    val avisar = rememberUpdatedState(onFrameVisibleChange)

    PlayerSurface(
        player = player,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        modifier = modifier.resizeWithContentScale(
            contentScale = when (scale) {
                FeedVideoScale.Crop -> ContentScale.Crop
                FeedVideoScale.Fit -> ContentScale.Fit
            },
            sourceSizeDp = apresentacao.videoSizeDp,
        ),
    )

    val cobrir = apresentacao.coverSurface
    LaunchedEffect(cobrir) { avisar.value(!cobrir) }
}
