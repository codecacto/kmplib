package br.com.codecacto.kmplib.presence

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Android: `ProcessLifecycleOwner` — `ON_START` do PROCESSO (a primeira Activity visível), não de uma
 * tela; girar o aparelho ou trocar de Activity não dispara de novo. Um observador novo recebe o
 * `ON_START` atual se o app já está na frente (o `LifecycleRegistry` sincroniza o estado), então a
 * abertura conta mesmo que a lib seja inicializada depois.
 *
 * `addObserver` exige a thread principal: chamado de outra, o registro é postado nela.
 */
internal actual fun registerAppForegroundListener(onForeground: () -> Unit): Boolean {
    val mainLooper = Looper.getMainLooper() ?: return false
    val observer = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = onForeground()
    }
    val register = { ProcessLifecycleOwner.get().lifecycle.addObserver(observer) }
    if (Looper.myLooper() == mainLooper) register() else Handler(mainLooper).post(register)
    return true
}
