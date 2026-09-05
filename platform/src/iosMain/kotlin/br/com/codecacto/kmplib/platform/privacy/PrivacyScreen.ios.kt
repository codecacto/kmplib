package br.com.codecacto.kmplib.platform.privacy

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyleSystemMaterial
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIVisualEffectView
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObjectProtocol

/**
 * **Desfoque sobre a janela em `applicationWillResignActive`** — a forma correta de esconder o
 * conteúdo do seletor de apps no iOS.
 *
 * Não existe `FLAG_SECURE` no iOS, e o truque conhecido (embutir a tela na camada de um
 * `UITextField` com `isSecureTextEntry`) é uso indevido de detalhe interno do UIKit: não é
 * documentado, muda de comportamento entre versões e é exatamente o tipo de atalho que esta
 * biblioteca não adota. O caminho oficial é cobrir a janela no instante em que o sistema tira a
 * foto para a multitarefa — `UIApplicationWillResignActiveNotification` — e descobrir em
 * `UIApplicationDidBecomeActiveNotification`.
 *
 * O `resignActive` cobre também a barra de controle puxada de cima e a ligação recebida; o app
 * volta descoberto sozinho.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS):** o build Kotlin/Native iOS não roda no servidor Linux.
 */
object IosPrivacyScreen : PrivacyScreen {

    private const val OVERLAY_TAG = 0x4B4D_5053L // "KMPS" — para achar e remover a própria cobertura

    private var hidden: Boolean = false
    private var observers: List<NSObjectProtocol> = emptyList()

    override val isSupported: Boolean = true

    override val isHidden: Boolean get() = hidden

    override fun setHidden(hidden: Boolean) {
        if (this.hidden == hidden) return
        this.hidden = hidden
        if (hidden) startObserving() else {
            stopObserving()
            uncover()
        }
    }

    private fun startObserving() {
        if (observers.isNotEmpty()) return
        val center = NSNotificationCenter.defaultCenter
        observers = listOf(
            center.addObserverForName(
                name = UIApplicationWillResignActiveNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _ -> cover() },
            center.addObserverForName(
                name = UIApplicationDidBecomeActiveNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _ -> uncover() },
        )
    }

    private fun stopObserving() {
        val center = NSNotificationCenter.defaultCenter
        observers.forEach { center.removeObserver(it) }
        observers = emptyList()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun cover() {
        windows().forEach { window ->
            if (overlayIn(window) != null) return@forEach
            val overlay = UIVisualEffectView(effect = UIBlurEffect.effectWithStyle(UIBlurEffectStyleSystemMaterial))
            overlay.tag = OVERLAY_TAG
            overlay.setFrame(window.bounds)
            overlay.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
            window.addSubview(overlay)
            window.bringSubviewToFront(overlay)
        }
    }

    private fun uncover() {
        windows().forEach { window -> overlayIn(window)?.removeFromSuperview() }
    }

    private fun overlayIn(window: UIWindow): UIView? = window.viewWithTag(OVERLAY_TAG)

    /**
     * As janelas em que a cobertura entra.
     *
     * Sai das `connectedScenes` (iOS 13+) em vez de `UIApplication.windows`, que está obsoleta
     * desde o iOS 15 — e não de `keyWindow`, porque um app com teclado ou alerta de sistema tem
     * mais de uma janela e cobrir só a principal deixaria a outra na foto.
     */
    private fun windows(): List<UIWindow> =
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
}

actual fun getPrivacyScreen(): PrivacyScreen = IosPrivacyScreen
