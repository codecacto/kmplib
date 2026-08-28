package br.com.codecacto.kmplib.auth

import android.content.Context
import androidx.fragment.app.FragmentActivity
import br.com.codecacto.kmplib.auth.social.GoogleAuthHolder

/**
 * Registra o `Context` no login com Google (Credential Manager).
 *
 * Chame no `Application.onCreate()`. Ver [br.com.codecacto.kmplib.core.initKmpLibCore] para o
 * porquê de cada módulo ter o seu.
 */
fun initKmpLibAuth(context: Context) {
    GoogleAuthHolder.init(context)
}

/**
 * Entrega a `Activity` em foco ao seletor de contas do Google, que precisa dela para se apresentar.
 * Chame no `Activity.onResume()`.
 */
fun kmpLibAuthOnResume(activity: FragmentActivity) {
    GoogleAuthHolder.setActivity(activity)
}

/** Solta a referência à `Activity`. Chame no `Activity.onPause()`. */
fun kmpLibAuthOnPause() {
    GoogleAuthHolder.clearActivity()
}
