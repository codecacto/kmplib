package br.com.codecacto.kmplib.platform.links

import android.content.Context
import android.content.SharedPreferences
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** O `Context` da aplicação, registrado por `initKmpLibPlatform`. */
internal object InstallReferrerHolder {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun contextOrNull(): Context? = appContext
}

private const val PREFS = "kmplib_install_referrer"

/** Mesma chave da 2.201.0: quem já consultou naquela versão não consulta de novo. */
private const val KEY_CONSULTADO = "lido"
private const val KEY_PENDENTE = "link_pendente"
private const val KEY_INSTANTE = "link_instante"
private const val TIMEOUT_MS = 5_000L

/** Duas aberturas não consultam a Play duas vezes (a tela e um efeito, por exemplo). */
private val trava = Mutex()

private class PrefsInstallReferrerLinkStore(private val prefs: SharedPreferences) : InstallReferrerLinkStore {
    override val consulted: Boolean get() = prefs.getBoolean(KEY_CONSULTADO, false)
    override val pendingUrl: String? get() = prefs.getString(KEY_PENDENTE, null)
    override val pendingEpochSeconds: Long get() = prefs.getLong(KEY_INSTANTE, 0L)

    override fun recordAnswer(pendingUrl: String?, epochSeconds: Long) {
        // `commit`, não `apply`: é a gravação que precisa estar no disco se o processo morrer logo
        // depois — que é o caso inteiro que esta API existe para cobrir. Roda fora da main thread.
        prefs.edit()
            .putBoolean(KEY_CONSULTADO, true)
            .apply { if (pendingUrl != null) putString(KEY_PENDENTE, pendingUrl) else remove(KEY_PENDENTE) }
            .putLong(KEY_INSTANTE, epochSeconds)
            .commit()
    }

    override fun clearPending() {
        prefs.edit().remove(KEY_PENDENTE).remove(KEY_INSTANTE).commit()
    }
}

private fun prefsOrNull(): SharedPreferences? =
    InstallReferrerHolder.contextOrNull()?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

actual suspend fun peekInstallReferrerLink(key: String, maxAgeSeconds: Long): String? {
    val context = InstallReferrerHolder.contextOrNull() ?: return null
    return withContext(Dispatchers.IO) {
        trava.withLock {
            val store = PrefsInstallReferrerLinkStore(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
            peekInstallReferrerLinkWith(
                store = store,
                nowEpochSeconds = System.currentTimeMillis() / 1000,
                key = key,
                maxAgeSeconds = maxAgeSeconds,
            ) {
                withTimeoutOrNull(TIMEOUT_MS) { consultar(context) } ?: InstallReferrerAnswer.Transient
            }
        }
    }
}

actual fun markInstallReferrerLinkConsumed() {
    // `apply`: perder esta escrita num processo que morre no mesmo instante só faz o destino abrir
    // mais uma vez — o oposto (perder o link) é o que não pode acontecer.
    prefsOrNull()?.edit()?.remove(KEY_PENDENTE)?.remove(KEY_INSTANTE)?.apply()
}

private suspend fun consultar(context: Context): InstallReferrerAnswer = suspendCancellableCoroutine { cont ->
    val client = InstallReferrerClient.newBuilder(context).build()
    val entregar: (InstallReferrerAnswer) -> Unit = { resposta ->
        if (cont.isActive) cont.resume(resposta)
        runCatching { client.endConnection() }
    }
    cont.invokeOnCancellation { runCatching { client.endConnection() } }

    runCatching {
        client.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                when (responseCode) {
                    InstallReferrerClient.InstallReferrerResponse.OK -> {
                        val detalhes = runCatching { client.installReferrer }.getOrNull()
                        entregar(
                            if (detalhes == null) {
                                InstallReferrerAnswer.Transient
                            } else {
                                InstallReferrerAnswer.Ok(
                                    referrer = detalhes.installReferrer,
                                    clickEpochSeconds = detalhes.referrerClickTimestampSeconds,
                                    installBeginEpochSeconds = detalhes.installBeginTimestampSeconds,
                                )
                            },
                        )
                    }
                    InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED,
                    InstallReferrerClient.InstallReferrerResponse.DEVELOPER_ERROR,
                    InstallReferrerClient.InstallReferrerResponse.PERMISSION_ERROR,
                    -> entregar(InstallReferrerAnswer.Definitive)
                    else -> entregar(InstallReferrerAnswer.Transient)
                }
            }

            override fun onInstallReferrerServiceDisconnected() {
                entregar(InstallReferrerAnswer.Transient)
            }
        })
    }.onFailure { entregar(InstallReferrerAnswer.Transient) }
}
