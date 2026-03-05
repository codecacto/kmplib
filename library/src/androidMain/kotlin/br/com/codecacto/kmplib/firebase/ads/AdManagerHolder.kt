package br.com.codecacto.kmplib.firebase.ads

import android.app.Activity
import android.content.Context
import java.lang.ref.WeakReference

/**
 * Holder para Context e Activity necessários para ads no Android.
 *
 * Inicializado automaticamente via [KmpLib.init].
 * Activity atualizada via [KmpLib.setActivity] / [KmpLib.clearActivity].
 */
object AdManagerHolder {
    private var contextRef: WeakReference<Context>? = null
    private var activityRef: WeakReference<Activity>? = null

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    fun setActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun clearActivity() {
        activityRef = null
    }

    internal fun getContext(): Context? = contextRef?.get()
    internal fun getActivity(): Activity? = activityRef?.get()
}
