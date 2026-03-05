package br.com.codecacto.kmplib.firebase.ads

import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.UISceneActivationStateForegroundActive

/**
 * Obtém o rootViewController da window scene ativa (foreground).
 */
internal fun getRootViewController(): UIViewController? {
    val windowScene = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
        ?: UIApplication.sharedApplication.connectedScenes
            .firstOrNull() as? UIWindowScene
    return windowScene?.keyWindow?.rootViewController
        ?: windowScene?.windows?.firstOrNull()
            ?.let { (it as? UIWindow)?.rootViewController }
}
