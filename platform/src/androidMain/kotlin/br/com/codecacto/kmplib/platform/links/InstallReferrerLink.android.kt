package br.com.codecacto.kmplib.platform.links

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
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
private const val KEY_LIDO = "lido"
private const val TIMEOUT_MS = 5_000L

private sealed interface Resposta {
    data class Ok(val referrer: String?, val cliqueEmSegundos: Long, val instalacaoEmSegundos: Long) : Resposta

    /** O aparelho não tem o serviço (loja que não é a Play, aparelho sem Play): não adianta insistir. */
    data object Definitiva : Resposta

    /** Play indisponível agora, conexão caiu, tempo esgotado: tenta na próxima abertura. */
    data object Passageira : Resposta
}

actual suspend fun readInstallReferrerLinkOnce(key: String, maxAgeSeconds: Long): String? {
    val context = InstallReferrerHolder.contextOrNull() ?: return null
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (prefs.getBoolean(KEY_LIDO, false)) return null

    return when (val resposta = withTimeoutOrNull(TIMEOUT_MS) { consultar(context) } ?: Resposta.Passageira) {
        Resposta.Passageira -> null
        Resposta.Definitiva -> {
            prefs.edit().putBoolean(KEY_LIDO, true).apply()
            null
        }
        is Resposta.Ok -> {
            prefs.edit().putBoolean(KEY_LIDO, true).apply()
            // Quando a Play não informa o clique, o início da instalação é a melhor aproximação.
            val instante = resposta.cliqueEmSegundos.takeIf { it > 0 } ?: resposta.instalacaoEmSegundos
            val agora = System.currentTimeMillis() / 1000
            if (isInstallReferrerFresh(instante, agora, maxAgeSeconds)) {
                parseInstallReferrerLink(resposta.referrer, key)
            } else {
                null
            }
        }
    }
}

private suspend fun consultar(context: Context): Resposta = suspendCancellableCoroutine { cont ->
    val client = InstallReferrerClient.newBuilder(context).build()
    val entregar: (Resposta) -> Unit = { resposta ->
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
                                Resposta.Passageira
                            } else {
                                Resposta.Ok(
                                    referrer = detalhes.installReferrer,
                                    cliqueEmSegundos = detalhes.referrerClickTimestampSeconds,
                                    instalacaoEmSegundos = detalhes.installBeginTimestampSeconds,
                                )
                            },
                        )
                    }
                    InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED,
                    InstallReferrerClient.InstallReferrerResponse.DEVELOPER_ERROR,
                    InstallReferrerClient.InstallReferrerResponse.PERMISSION_ERROR,
                    -> entregar(Resposta.Definitiva)
                    else -> entregar(Resposta.Passageira)
                }
            }

            override fun onInstallReferrerServiceDisconnected() {
                entregar(Resposta.Passageira)
            }
        })
    }.onFailure { entregar(Resposta.Passageira) }
}
