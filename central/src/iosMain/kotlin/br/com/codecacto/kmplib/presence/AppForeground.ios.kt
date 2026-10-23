package br.com.codecacto.kmplib.presence

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationState
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS: `UIApplicationDidBecomeActiveNotification` (inclui a abertura). Se o app já está ativo no
 * registro, conta a vinda atual também. `UIApplication` só se lê na thread principal — o registro e a
 * leitura do estado vão para a fila principal.
 */
internal actual fun registerAppForegroundListener(onForeground: () -> Unit): Boolean {
    dispatch_async(dispatch_get_main_queue()) {
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> onForeground() }
        if (UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive) {
            onForeground()
        }
    }
    return true
}
