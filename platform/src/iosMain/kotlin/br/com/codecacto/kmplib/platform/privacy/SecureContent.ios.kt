package br.com.codecacto.kmplib.platform.privacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIScreen
import platform.UIKit.UIScreenCapturedDidChangeNotification
import platform.UIKit.UIWindowScene

/**
 * `UIScreen.captured` da tela da cena ativa, acompanhado por `UIScreenCapturedDidChangeNotification`
 * — a API que a Apple documenta para reagir a gravação de tela, espelhamento e AirPlay.
 */
@Composable
internal actual fun rememberScreenCaptured(): State<Boolean> {
    val state = remember { mutableStateOf(isScreenCaptured()) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(
            name = UIScreenCapturedDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> state.value = isScreenCaptured() }
        state.value = isScreenCaptured()
        onDispose { center.removeObserver(observer) }
    }
    return state
}

private fun isScreenCaptured(): Boolean {
    val sceneScreen = UIApplication.sharedApplication.connectedScenes
        .firstOrNull { it is UIWindowScene }
        ?.let { (it as UIWindowScene).screen }
    return (sceneScreen ?: UIScreen.mainScreen).captured
}
