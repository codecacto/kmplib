package br.com.codecacto.kmplib.firebase.auth

import android.app.Activity
import android.content.Context
import java.lang.ref.WeakReference

/**
 * Holder para o contexto do Android necessário para Google Sign-In.
 *
 * Inicializado automaticamente via [KmpLib.init] e [KmpLib.setActivity].
 * O CredentialManager requer Activity context para exibir o seletor de contas.
 */
object GoogleAuthHolder {
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

    internal fun getActivity(): Activity? = activityRef?.get()

    internal fun getContext(): Context? = activityRef?.get() ?: contextRef?.get()
}
