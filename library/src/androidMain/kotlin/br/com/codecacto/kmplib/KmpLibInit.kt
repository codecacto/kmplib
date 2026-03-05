package br.com.codecacto.kmplib

import android.content.Context
import androidx.fragment.app.FragmentActivity
import br.com.codecacto.kmplib.firebase.ads.AdManagerHolder
import br.com.codecacto.kmplib.platform.BiometricAuthHolder
import br.com.codecacto.kmplib.platform.NotificationSchedulerHolder
import br.com.codecacto.kmplib.platform.ShareHandlerHolder
import br.com.codecacto.kmplib.platform.UrlLauncherHolder

/**
 * Inicializa a KmpLib no Android.
 *
 * Deve ser chamado no `Application.onCreate()`:
 *
 * ```kotlin
 * class MyApplication : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         KmpLib.init(this)
 *     }
 * }
 * ```
 */
fun KmpLib.init(context: Context) {
    UrlLauncherHolder.init(context)
    ShareHandlerHolder.init(context)
    NotificationSchedulerHolder.init(context)
    AdManagerHolder.init(context)
}

/**
 * Define a Activity atual para funcionalidades que precisam dela (ex: biometria).
 *
 * Deve ser chamado no `Activity.onResume()`:
 *
 * ```kotlin
 * override fun onResume() {
 *     super.onResume()
 *     KmpLib.setActivity(this)
 * }
 *
 * override fun onPause() {
 *     super.onPause()
 *     KmpLib.clearActivity()
 * }
 * ```
 */
fun KmpLib.setActivity(activity: FragmentActivity) {
    BiometricAuthHolder.setActivity(activity)
    NotificationSchedulerHolder.setActivity(activity)
    AdManagerHolder.setActivity(activity)
}

/**
 * Limpa a referência à Activity.
 * Deve ser chamado no `Activity.onPause()`.
 */
fun KmpLib.clearActivity() {
    BiometricAuthHolder.clearActivity()
    NotificationSchedulerHolder.clearActivity()
    AdManagerHolder.clearActivity()
}
