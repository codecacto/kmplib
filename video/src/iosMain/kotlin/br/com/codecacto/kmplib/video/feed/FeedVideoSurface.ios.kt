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
 * A camada do player **hospedada** num `UIView`, embutido pelo `UIKitView` — a forma que a Apple
 * indica para pôr vídeo numa hierarquia de views própria (o `AVPlayerViewController` traria os
 * controles da AVKit).
 *
 * ⚠️ **A camada não é criada aqui.** Ela nasce junto com o player, dentro do [AvFeedVideoEngine], e
 * esta função apenas a **adota**. O motivo está no KDoc de lá: item que vira `currentItem` de um
 * player sem camada faz a AVFoundation montar um pipeline só de áudio. Criando a camada na
 * composição, ela chegaria sempre depois do `load()`.
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
    val motor = engine as AvFeedVideoEngine
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
            v.adotar(motor.playerLayer)
            motor.playerLayer.videoGravity = gravidade
        },
        // Soltar a camada no descarte: sem isto ela continuaria pendurada nesta view, e o pool já
        // emprestou o player a outro item — os dois lugares mostrariam o mesmo vídeo.
        onRelease = { v -> v.soltar() },
        properties = UIKitInteropProperties(isInteractive = false, isNativeAccessibilityEnabled = false),
    )

    // `readyForDisplay` se LÊ (KVO não tem forma pública de override no Kotlin/Native 2.x — o
    // `observeValueForKeyPath` chega como extensão de categoria). A leitura para no primeiro quadro.
    val fonte = motor.loadedUrl
    LaunchedEffect(view, motor, fonte) {
        avisar.value(false)
        while (!motor.playerLayer.readyForDisplay) delay(FRAME_POLL_MILLIS)
        avisar.value(true)
    }
}

/**
 * O `UIView` que hospeda a camada **do player que o pool emprestou a este item**.
 *
 * `CALayer` não participa do Auto Layout: sem o `layoutSubviews` a camada ficaria com frame zero
 * (tela preta, áudio tocando). E a troca de frame vai **sem animação implícita** — uma subcamada
 * anima a mudança de tamanho por padrão, e na rolagem o vídeo "escorregaria" atrás da caixa dele.
 */
private class FeedVideoUIView : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    private var camada: AVPlayerLayer? = null

    init {
        backgroundColor = UIColor.clearColor
    }

    /**
     * Passa a hospedar [nova].
     *
     * O `removeFromSuperlayer()` antes de adicionar não é redundante: uma `CALayer` só tem **uma**
     * superlayer, e quando o pool tira o player de um item e o dá a outro, esta mesma camada ainda
     * está pendurada na view do item anterior. Sem soltar antes, o UIKit a move e a view antiga fica
     * preta sem que nada erre.
     */
    fun adotar(nova: AVPlayerLayer) {
        if (camada === nova) return
        camada?.removeFromSuperlayer()
        nova.removeFromSuperlayer()
        camada = nova
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        layer.addSublayer(nova)
        nova.setFrame(bounds)
        CATransaction.commit()
    }

    fun soltar() {
        camada?.removeFromSuperlayer()
        camada = null
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        val atual = camada ?: return
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        atual.setFrame(bounds)
        CATransaction.commit()
    }
}

private const val FRAME_POLL_MILLIS = 50L
