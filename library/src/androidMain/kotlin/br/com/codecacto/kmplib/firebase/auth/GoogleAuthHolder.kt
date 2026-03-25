package br.com.codecacto.kmplib.firebase.auth

import android.content.Context
import java.lang.ref.WeakReference

/**
 * Holder para o contexto do Android necessário para Google Sign-In.
 *
 * Inicializado automaticamente via [KmpLib.init].
 */
object GoogleAuthHolder {
    private var contextRef: WeakReference<Context>? = null

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    internal fun getContext(): Context? = contextRef?.get()
}
