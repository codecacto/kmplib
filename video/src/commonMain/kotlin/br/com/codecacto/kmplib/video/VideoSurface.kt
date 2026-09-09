package br.com.codecacto.kmplib.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A superfície onde os quadros aparecem — **a única parte do player que é código nativo**.
 *
 * - **Android:** `PlayerView` do `media3-ui` dentro de um `AndroidView`, com `useController =
 *   false`. Ela cuida da superfície (`SurfaceView`), da razão de aspecto e do `SubtitleView` que
 *   desenha a legenda **embutida**; os controles são os nossos, em Compose.
 * - **iOS:** um `UIView` com `AVPlayerLayer` dentro de um `UIKitView`. A camada é a forma
 *   recomendada pela Apple para embutir vídeo numa hierarquia de views própria — o
 *   `AVPlayerViewController` traria os controles da AVKit por cima dos nossos.
 *
 * Não a use direto: ela é a base do [VideoPlayer], que acrescenta controles, legenda externa,
 * estados e o slot de sobreposição.
 */
@Composable
expect fun VideoSurface(state: VideoPlayerState, modifier: Modifier)
