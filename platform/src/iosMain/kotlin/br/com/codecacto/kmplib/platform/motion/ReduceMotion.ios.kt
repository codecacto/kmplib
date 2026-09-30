package br.com.codecacto.kmplib.platform.motion

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification

actual fun isReduceMotionEnabled(): Boolean = UIAccessibilityIsReduceMotionEnabled()

internal actual fun platformReduceMotionChanges(): Flow<Boolean> = callbackFlow {
    val center = NSNotificationCenter.defaultCenter
    val token = center.addObserverForName(
        name = UIAccessibilityReduceMotionStatusDidChangeNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) { _ -> trySend(UIAccessibilityIsReduceMotionEnabled()) }
    trySend(UIAccessibilityIsReduceMotionEnabled())
    awaitClose { center.removeObserver(token) }
}
