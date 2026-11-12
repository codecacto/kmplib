package br.com.codecacto.kmplib.analytics

import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlin.math.roundToInt

/**
 * Android: `Build.VERSION.RELEASE` e a tela pelos `Resources.getSystem()` (não precisa de `Context`,
 * vale antes do `initKmpLibCore`). Tablet = `smallestScreenWidthDp` ≥ 600, a régua do próprio Android
 * para `sw600dp`.
 */
internal actual fun currentAnalyticsDevice(): AnalyticsDevice {
    val res = runCatching { Resources.getSystem() }.getOrNull()
    val metrics = runCatching { res?.displayMetrics }.getOrNull()
    val config = runCatching { res?.configuration }.getOrNull()
    val screen = if (metrics != null && metrics.density > 0f && metrics.widthPixels > 0 && metrics.heightPixels > 0) {
        "${(metrics.widthPixels / metrics.density).roundToInt()}x${(metrics.heightPixels / metrics.density).roundToInt()}"
    } else {
        null
    }
    val sw = config?.smallestScreenWidthDp ?: 0
    return AnalyticsDevice(
        os = AnalyticsDevice.OS_ANDROID,
        osVersion = runCatching { Build.VERSION.RELEASE }.getOrNull()?.takeIf { it.isNotBlank() },
        tablet = sw >= 600,
        screen = screen,
    )
}

/**
 * `ProcessLifecycleOwner`: `ON_START`/`ON_STOP` do PROCESSO (a primeira Activity visível / a última a
 * sair), não de uma tela — girar o aparelho não dispara. `addObserver` exige a thread principal.
 */
internal actual fun registerAnalyticsLifecycle(onForeground: () -> Unit, onBackground: () -> Unit): Boolean {
    val mainLooper = Looper.getMainLooper() ?: return false
    val observer = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = onForeground()
        override fun onStop(owner: LifecycleOwner) = onBackground()
    }
    val register = { ProcessLifecycleOwner.get().lifecycle.addObserver(observer) }
    if (Looper.myLooper() == mainLooper) register() else Handler(mainLooper).post(register)
    return true
}
