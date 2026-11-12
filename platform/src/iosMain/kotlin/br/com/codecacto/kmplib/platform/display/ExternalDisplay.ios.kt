@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package br.com.codecacto.kmplib.platform.display

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController
import br.com.codecacto.kmplib.core.util.AppLogger
import platform.UIKit.UIApplication
import platform.UIKit.UIInterfaceOrientationMaskAll
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.UIKit.setNeedsUpdateOfSupportedInterfaceOrientations
import kotlin.native.ObjCName

/**
 * A ponte da cena de TELA EXTERNA (iOS 16+). No `Info.plist` do app, em `UIApplicationSceneManifest` →
 * `UISceneConfigurations`, uma entrada `UIWindowSceneSessionRoleExternalDisplayNonInteractive` com o
 * delegate abaixo; no Swift:
 * ```swift
 * final class ExternalSceneDelegate: NSObject, UIWindowSceneDelegate {
 *     var window: UIWindow?
 *     func scene(_ scene: UIScene, willConnectTo s: UISceneSession, options o: UIScene.ConnectionOptions) {
 *         guard let ws = scene as? UIWindowScene else { return }
 *         let w = UIWindow(windowScene: ws); window = w
 *         ExternalDisplayBridge.shared.attach(window: w)
 *     }
 *     func sceneDidDisconnect(_ scene: UIScene) { ExternalDisplayBridge.shared.detach(); window = nil }
 * }
 * ```
 */
@ObjCName("ExternalDisplayBridge")
object ExternalDisplayBridge {
    private var window: UIWindow? = null

    /** A cena externa conectou: a lib desenha nela o conteúdo registrado. */
    fun attach(window: UIWindow) {
        this.window = window
        window.rootViewController = ComposeUIViewController { ExternalDisplay.content?.invoke() }
        window.hidden = false
        ExternalDisplay.setConnected(true)
    }

    /** A cena externa saiu. */
    fun detach() {
        window?.hidden = true
        window?.rootViewController = null
        window = null
        ExternalDisplay.setConnected(false)
    }
}

@Composable
internal actual fun PlatformExternalDisplayHost(enabled: Boolean) = Unit

internal actual fun applyScreenOrientation(orientation: ScreenOrientation) {
    val scene = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>().firstOrNull() ?: return
    val mask = when (orientation) {
        ScreenOrientation.LANDSCAPE -> UIInterfaceOrientationMaskLandscape
        ScreenOrientation.PORTRAIT -> UIInterfaceOrientationMaskPortrait
        ScreenOrientation.UNSPECIFIED -> UIInterfaceOrientationMaskAll
    }
    scene.keyWindow?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
    scene.requestGeometryUpdateWithPreferences(UIWindowSceneGeometryPreferencesIOS(interfaceOrientations = mask)) { error ->
        AppLogger.w("ScreenOrientation", "o sistema recusou a orientação: ${error?.localizedDescription}")
    }
}
