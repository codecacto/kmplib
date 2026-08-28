package br.com.codecacto.kmplib

import android.content.Context
import androidx.fragment.app.FragmentActivity
import br.com.codecacto.kmplib.auth.social.GoogleAuthHolder
import br.com.codecacto.kmplib.platform.DeviceLocaleHolder
import br.com.codecacto.kmplib.platform.permission.PermissionHostHolder
import br.com.codecacto.kmplib.core.storage.BlobStoreHolder
import br.com.codecacto.kmplib.media.SoundEffectPlayerHolder
import br.com.codecacto.kmplib.platform.BatteryMonitorHolder
import br.com.codecacto.kmplib.platform.BiometricAuthHolder
import br.com.codecacto.kmplib.platform.NotificationSchedulerHolder
import br.com.codecacto.kmplib.platform.ScreenBrightnessHolder
import br.com.codecacto.kmplib.platform.ShakeDetectorHolder
import br.com.codecacto.kmplib.platform.ShareHandlerHolder
import br.com.codecacto.kmplib.core.context.AndroidAppContext
import br.com.codecacto.kmplib.platform.audio.AudioCaptureHolder
import br.com.codecacto.kmplib.platform.tts.TtsControllerHolder
import br.com.codecacto.kmplib.sync.SyncDatabaseHolder
import br.com.codecacto.kmplib.torch.TorchControllerHolder
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
    AndroidAppContext.init(context)
    ShareHandlerHolder.init(context)
    NotificationSchedulerHolder.init(context)
    GoogleAuthHolder.init(context)
    SyncDatabaseHolder.init(context)
    BlobStoreHolder.init(context)
    TtsControllerHolder.init(context)
    SpeechRecognizerHolder.init(context)
    TorchControllerHolder.init(context)
    BatteryMonitorHolder.init(context)
    ShakeDetectorHolder.init(context)
    SoundEffectPlayerHolder.init(context)
    AudioCaptureHolder.init(context)
    DeviceLocaleHolder.init(context)
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
    ScreenBrightnessHolder.setActivity(activity)
    NotificationSchedulerHolder.setActivity(activity)
    GoogleAuthHolder.setActivity(activity)
    // **Permissão de runtime entrou aqui em 2.154.0, e a ausência dela era muda:** sem esta linha,
    // `PermissionManager.requestPermission` não abre diálogo nenhum — registra um aviso e devolve o
    // status que já tinha. O botão "Permitir" existe, é tocável, e não acontece nada, com build
    // verde. Levantamento de 26/ago/2026: dos 26 apps do portfólio que pedem permissão, **9 já
    // registravam este holder por conta própria** — nove equipes descobriram o mesmo furo e
    // escreveram o mesmo contorno. Chamar duas vezes é inofensivo (o holder só guarda a referência),
    // então quem já contorna não precisa remover nada para subir de versão.
    PermissionHostHolder.setActivity(activity)
}

/**
 * Limpa a referência à Activity.
 * Deve ser chamado no `Activity.onPause()`.
 */
fun KmpLib.clearActivity() {
    BiometricAuthHolder.clearActivity()
    ScreenBrightnessHolder.clearActivity()
    NotificationSchedulerHolder.clearActivity()
    GoogleAuthHolder.clearActivity()
    PermissionHostHolder.clearActivity()
}
