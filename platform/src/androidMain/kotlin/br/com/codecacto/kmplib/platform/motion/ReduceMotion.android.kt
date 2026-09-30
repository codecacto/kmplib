package br.com.codecacto.kmplib.platform.motion

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import java.lang.ref.WeakReference

/**
 * Contexto de aplicação para ler as escalas de animação — holder da casa, inicializado por
 * `initKmpLibPlatform`/`KmpLib.init`.
 */
object ReduceMotionHolder {
    private var contextRef: WeakReference<Context>? = null

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    internal fun getContext(): Context? = contextRef?.get()
}

private fun Context.readScale(name: String): Float? =
    try {
        Settings.Global.getFloat(contentResolver, name)
    } catch (_: Settings.SettingNotFoundException) {
        null // chave nunca escrita = escala padrão
    }

private fun Context.readReduceMotion(): Boolean = reduceMotionFromAnimationScales(
    animatorScale = readScale(Settings.Global.ANIMATOR_DURATION_SCALE),
    transitionScale = readScale(Settings.Global.TRANSITION_ANIMATION_SCALE),
)

actual fun isReduceMotionEnabled(): Boolean =
    ReduceMotionHolder.getContext()?.readReduceMotion() ?: false

internal actual fun platformReduceMotionChanges(): Flow<Boolean> {
    val context = ReduceMotionHolder.getContext() ?: return flowOf(false)
    return callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(context.readReduceMotion())
            }
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE), false, observer)
        // Depois de registrar: uma mudança entre a leitura e o registro não se perde.
        trySend(context.readReduceMotion())
        awaitClose { resolver.unregisterContentObserver(observer) }
    }
}
