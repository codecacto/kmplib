package br.com.codecacto.kmplib.health

import android.content.Context
import androidx.activity.ComponentActivity
import java.lang.ref.WeakReference

/**
 * `applicationContext` registrado por [initKmpLibHealth] — chame no `Application.onCreate()`,
 * como os demais módulos da kmplib (`initKmpLibCore`, `initKmpLibPlatform`...). Vale para o
 * `createHealthRepository()` e para o `createHeartRateMonitor()`.
 */
fun initKmpLibHealth(context: Context) {
    HealthContextHolder.applicationContext = context.applicationContext
}

internal object HealthContextHolder {
    @Volatile
    internal var applicationContext: Context? = null

    fun requireContext(): Context = applicationContext
        ?: error("kmplib-health: chame initKmpLibHealth(context) no Application.onCreate() antes de usar a lib.")
}

/**
 * Holder da Activity, só para o pedido de permissão do Health Connect (que precisa de um
 * `ActivityResultRegistry`). Separado do `PermissionHostHolder` do `kmplib-platform` de propósito
 * — este módulo é um artefato independente e não depende da `kmplib-platform` só por isto.
 *
 * Chame `HealthActivityHolder.setActivity(this)` no `onResume()` e `clearActivity()` no
 * `onPause()`, como os demais holders por módulo da kmplib.
 */
object HealthActivityHolder {
    private var activityRef: WeakReference<ComponentActivity>? = null

    fun setActivity(activity: ComponentActivity) {
        activityRef = WeakReference(activity)
    }

    fun clearActivity() {
        activityRef = null
    }

    internal fun getActivity(): ComponentActivity? = activityRef?.get()
}
