package br.com.codecacto.kmplib.analytics

import kotlin.concurrent.Volatile
import kotlin.math.roundToInt
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSThread
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIDevice
import platform.UIKit.UIScreen
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Lido UMA vez na thread principal (UIKit) e guardado; fora dela, sem leitura ainda, sai o básico. */
@Volatile
private var cachedDevice: AnalyticsDevice? = null

@OptIn(ExperimentalForeignApi::class)
private fun readDeviceOnMain(): AnalyticsDevice {
    val screen = runCatching {
        UIScreen.mainScreen.bounds.useContents { "${size.width.roundToInt()}x${size.height.roundToInt()}" }
    }.getOrNull()
    val tablet = runCatching { UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad }.getOrDefault(false)
    return AnalyticsDevice(
        os = AnalyticsDevice.OS_IOS,
        osVersion = runCatching { UIDevice.currentDevice.systemVersion }.getOrNull()?.takeIf { it.isNotBlank() },
        tablet = tablet,
        screen = screen,
    ).also { cachedDevice = it }
}

/**
 * iOS: `UIDevice`/`UIScreen` (pontos, a unidade do CSS px do site). UIKit só na thread principal: fora
 * dela, sem leitura guardada, a versão do sistema sai do `NSProcessInfo` (seguro em qualquer thread) e
 * a tela fica `null` até o [registerAnalyticsLifecycle] ler na principal.
 */
internal actual fun currentAnalyticsDevice(): AnalyticsDevice {
    cachedDevice?.let { return it }
    if (NSThread.isMainThread) return readDeviceOnMain()
    val v = runCatching {
        NSProcessInfo.processInfo.operatingSystemVersionString
            .substringAfter("Version ", "").substringBefore(' ').takeIf { it.isNotBlank() }
    }.getOrNull()
    return AnalyticsDevice(os = AnalyticsDevice.OS_IOS, osVersion = v, tablet = false, screen = null)
}

/**
 * `UIApplicationDidBecomeActiveNotification` (inclui a abertura) e `…DidEnterBackgroundNotification`.
 * O registro vai para a fila principal, que é também onde o aparelho é lido e guardado.
 */
internal actual fun registerAnalyticsLifecycle(onForeground: () -> Unit, onBackground: () -> Unit): Boolean {
    dispatch_async(dispatch_get_main_queue()) {
        runCatching { readDeviceOnMain() }
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> onForeground() }
        center.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> onBackground() }
        if (UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive) {
            onForeground()
        }
    }
    return true
}
