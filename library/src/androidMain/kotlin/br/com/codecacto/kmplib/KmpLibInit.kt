package br.com.codecacto.kmplib

import android.content.Context
import androidx.fragment.app.FragmentActivity
import br.com.codecacto.kmplib.auth.initKmpLibAuth
import br.com.codecacto.kmplib.auth.kmpLibAuthOnPause
import br.com.codecacto.kmplib.auth.kmpLibAuthOnResume
import br.com.codecacto.kmplib.core.initKmpLibCore
import br.com.codecacto.kmplib.core.storage.BlobStoreHolder
import br.com.codecacto.kmplib.media.initKmpLibMedia
import br.com.codecacto.kmplib.platform.initKmpLibPlatform
import br.com.codecacto.kmplib.platform.kmpLibPlatformOnPause
import br.com.codecacto.kmplib.platform.kmpLibPlatformOnResume
import br.com.codecacto.kmplib.sync.SyncDatabaseHolder
import br.com.codecacto.kmplib.sync.initKmpLibSync

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
    // Cada módulo tem o seu init desde a 2.163.0, e este aqui é a soma deles — é o que mantém a
    // chamada única funcionando para quem consome o artefato umbrella. Um app que declara só os
    // módulos que usa chama os inits correspondentes e não passa por aqui.
    initKmpLibCore(context)
    initKmpLibPlatform(context)
    initKmpLibAuth(context)
    initKmpLibSync(context)
    initKmpLibMedia(context)
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
    kmpLibPlatformOnResume(activity)
    kmpLibAuthOnResume(activity)
}

/**
 * Limpa a referência à Activity.
 * Deve ser chamado no `Activity.onPause()`.
 */
fun KmpLib.clearActivity() {
    kmpLibPlatformOnPause()
    kmpLibAuthOnPause()
}
