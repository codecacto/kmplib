package br.com.codecacto.kmplib.firebase.crashlytics

import android.content.Context
import java.lang.ref.WeakReference

/**
 * Holder para o contexto do Android necessário para Crashlytics.
 *
 * Inicializado automaticamente via [KmpLib.init].
 */
object CrashlyticsHolder {
    private var contextRef: WeakReference<Context>? = null

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    internal fun getContext(): Context? = contextRef?.get()
}
