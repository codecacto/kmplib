@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("ktlint:standard:no-wildcard-imports")

package br.com.codecacto.kmplib.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import platform.AVFoundation.*
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIColor
import platform.UIKit.UIView

/**
 * `AVPlayerLayer` dentro de um `UIView`, embutido pelo `UIKitView`.
 *
 * A camada é o caminho da Apple para colocar vídeo numa hierarquia de views própria — e é o que
 * permite os controles serem do Compose e a marca d'água do app ficar **entre** o vídeo e eles. Um
 * `AVPlayerViewController` traria a barra da AVKit por cima de tudo.
 *
 * `videoGravity = ResizeAspect`: o vídeo cabe inteiro na caixa, sem cortar. Quem escolhe a forma da
 * caixa é o app, pelo `modifier` (16:9 numa aula).
 *
 * A **legenda embutida** é desenhada pela própria camada quando uma `AVMediaSelectionOption`
 * legível está selecionada — não há view de legenda a montar aqui.
 */
@Composable
actual fun VideoSurface(state: VideoPlayerState, modifier: Modifier) {
    val player = state.avPlayerOrNull()

    UIKitView(
        modifier = modifier,
        factory = { VideoPlayerUIView() },
        update = { view -> (view as VideoPlayerUIView).playerLayer.player = player },
        // Soltar a camada no descarte: sem isto ela segura o AVPlayer e o último quadro fica
        // congelado por baixo da tela seguinte.
        onRelease = { view -> (view as VideoPlayerUIView).playerLayer.player = null },
    )
}

/**
 * `UIView` que hospeda a camada de vídeo e a redimensiona ao próprio `bounds`.
 *
 * O `layoutSubviews` não é detalhe: `CALayer` **não** participa do Auto Layout, então sem este
 * override a camada fica com o frame zero que recebeu no `init` e a tela mostra preto — com o áudio
 * tocando.
 */
private class VideoPlayerUIView : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    val playerLayer: AVPlayerLayer = AVPlayerLayer().apply {
        videoGravity = AVLayerVideoGravityResizeAspect
    }

    init {
        backgroundColor = UIColor.blackColor
        layer.addSublayer(playerLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        playerLayer.setFrame(bounds)
    }
}
