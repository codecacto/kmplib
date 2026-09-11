@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("ktlint:standard:no-wildcard-imports")

package br.com.codecacto.kmplib.video.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.coroutines.delay
import platform.AVFoundation.*
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIView

/**
 * `AVPlayerLayer` num `UIView`, embutido pelo `UIKitView` — a forma que a Apple indica para pôr vídeo
 * numa hierarquia própria (o `AVPlayerViewController` traria os controles da AVKit).
 *
 * - **Não interativo** (`UIKitInteropProperties(isInteractive = false)`): o toque segue para o
 *   Compose, onde moram o `onClick` do post e o botão de som. Interativo, a view nativa engoliria o
 *   toque e o post deixaria de abrir.
 * - **Primeiro quadro = `readyForDisplay` da camada.** É a propriedade que a Apple documenta para
 *   "a camada já tem o que mostrar"; enquanto ela é `false`, a capa fica por cima.
 * - `videoGravity` `ResizeAspectFill` (corte) ou `ResizeAspect` (encaixe), conforme [FeedVideoScale].
 */
@Composable
internal actual fun FeedVideoSurface(
    engine: FeedVideoEngine,
    scale: FeedVideoScale,
    modifier: Modifier,
    onFrameVisibleChange: (Boolean) -> Unit,
) {
    val player = (engine as AvFeedVideoEngine).player
    val gravidade = when (scale) {
        FeedVideoScale.Crop -> AVLayerVideoGravityResizeAspectFill
        FeedVideoScale.Fit -> AVLayerVideoGravityResizeAspect
    }
    val avisar = rememberUpdatedState(onFrameVisibleChange)
    val view = remember { FeedVideoUIView() }

    UIKitView(
        factory = { view },
        modifier = modifier,
        update = { v ->
            v.playerLayer.player = player
            v.playerLayer.videoGravity = gravidade
        },
        // Soltar a camada no descarte: sem isto ela segura o AVPlayer, que o pool já emprestou a
        // outro item — e os dois lugares mostrariam o mesmo vídeo.
        onRelease = { v -> v.playerLayer.player = null },
        properties = UIKitInteropProperties(isInteractive = false, isNativeAccessibilityEnabled = false),
    )

    // `readyForDisplay` se LÊ (KVO não tem forma pública de override no Kotlin/Native 2.x — o
    // `observeValueForKeyPath` chega como extensão de categoria). A leitura para no primeiro quadro.
    val fonte = engine.loadedUrl
    LaunchedEffect(view, player, fonte) {
        avisar.value(false)
        while (!view.playerLayer.readyForDisplay) delay(FRAME_POLL_MILLIS)
        avisar.value(true)
    }
}

/**
 * O `UIView` que hospeda a camada. `CALayer` não participa do Auto Layout: sem o `layoutSubviews` a
 * camada ficaria com frame zero (tela preta, áudio tocando). E a troca de frame vai **sem animação
 * implícita** — uma subcamada anima a mudança de tamanho por padrão, e na rolagem o vídeo "escorregaria"
 * atrás da caixa dele.
 */
private class FeedVideoUIView : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    val playerLayer: AVPlayerLayer = AVPlayerLayer()

    init {
        backgroundColor = UIColor.clearColor
        layer.addSublayer(playerLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        playerLayer.setFrame(bounds)
        CATransaction.commit()
    }
}

private const val FRAME_POLL_MILLIS = 50L
