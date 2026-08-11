package br.com.codecacto.kmplib

import android.content.Context
import androidx.fragment.app.FragmentActivity
import br.com.codecacto.kmplib.auth.social.GoogleAuthHolder
import br.com.codecacto.kmplib.core.storage.BlobStoreHolder
import br.com.codecacto.kmplib.platform.BiometricAuthHolder
import br.com.codecacto.kmplib.platform.NotificationSchedulerHolder
import br.com.codecacto.kmplib.platform.ShareHandlerHolder
import br.com.codecacto.kmplib.platform.UrlLauncherHolder
import br.com.codecacto.kmplib.platform.tts.TtsControllerHolder
import br.com.codecacto.kmplib.sync.SyncDatabaseHolder
import br.com.codecacto.kmplib.voice.SpeechRecognizerHolder

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
    GoogleAuthHolder.init(context)
    SyncDatabaseHolder.init(context)
    BlobStoreHolder.init(context)
    TtsControllerHolder.init(context)
    SpeechRecognizerHolder.init(context)
}

/**
 * Inicializa apenas a camada offline-first: o banco de sync (SQLDelight) **e** o
 * [BlobStore][br.com.codecacto.kmplib.core.storage.BlobStore] (binários da fila de upload). Atalho
 * para apps que querem registrar só o Context dessa camada, sem o resto do [init]. Já é chamado
 * por [init].
 */
fun KmpLib.initSync(context: Context) {
    SyncDatabaseHolder.init(context)
    BlobStoreHolder.init(context)
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
    GoogleAuthHolder.setActivity(activity)
}

/**
 * Limpa a referência à Activity.
 * Deve ser chamado no `Activity.onPause()`.
 */
fun KmpLib.clearActivity() {
    BiometricAuthHolder.clearActivity()
    NotificationSchedulerHolder.clearActivity()
    GoogleAuthHolder.clearActivity()
}
