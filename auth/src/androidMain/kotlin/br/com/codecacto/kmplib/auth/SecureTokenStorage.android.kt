package br.com.codecacto.kmplib.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.context.AndroidAppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

actual fun secureTokenStorage(serviceName: String): SecureTokenStorage =
    AndroidSecureTokenStorage(serviceName)

/**
 * Cofre seguro no Android via **`EncryptedSharedPreferences`** (Jetpack Security): as entradas ficam
 * cifradas com AES-256-GCM sob uma [MasterKey] ancorada no Android Keystore.
 *
 * ### Gravação DURÁVEL (2.292.0)
 * Até a 2.291.0 toda escrita era `apply()`: o par fica na memória do processo e vai ao disco depois,
 * numa fila. Com o refresh **rotativo** isso é uma bomba-relógio — o servidor já trocou r1 por r2, o
 * processo morre (o usuário arrasta o app da multitarefa, o sistema mata, o Maestro dá `force-stop`)
 * antes de a fila esvaziar, e a próxima abertura lê r1 do disco. Passada a janela de tolerância do
 * servidor, r1 é **reuso**: a família inteira é revogada e a pessoa cai no login sem ter feito nada.
 * Agora cada escrita é `commit()` (síncrono, em [Dispatchers.IO]) e só retorna depois de o arquivo
 * estar no disco; `commit()` que devolve `false` é repetido uma vez e, persistindo, **lança** — o
 * [AuthSessionStore] registra a falha em vez de ela sumir calada. Vale também para `remove`/`clear`:
 * um logout que não chegou ao disco ressuscitaria a sessão na próxima abertura.
 *
 * ### Abertura uma vez só, e sem apagar por falha passageira (2.292.0)
 * Até a 2.291.0 cada operação refazia `MasterKey` + `EncryptedSharedPreferences.create` (duas idas ao
 * Keystore) e, a **qualquer** exceção, apagava o arquivo e recriava. Uma falha passageira do Keystore
 * — comum logo depois de um `force-stop`, no boot ou com o serviço do Keystore ocupado — virava perda
 * definitiva da sessão. Agora o arquivo é aberto **uma vez por processo** (a recomendação do próprio
 * Jetpack Security: a criação é cara) e a abertura é tentada [OPEN_ATTEMPTS] vezes, com espera
 * crescente, antes de o cofre ser dado como irrecuperável. Só então ele é descartado e recriado —
 * perder o refresh token força um login novo, nunca um crash.
 *
 * **Um processo só.** A instância fica em cache no processo; o cofre não foi feito para ser lido e
 * gravado por dois processos do mesmo app (`android:process`) ao mesmo tempo.
 */
internal class AndroidSecureTokenStorage internal constructor(
    private val serviceName: String,
    /** Abre o arquivo cifrado. Injetável só para teste; o default é o `EncryptedSharedPreferences`. */
    opener: (() -> SharedPreferences)? = null,
    /** Apaga o arquivo cifrado irrecuperável. Injetável só para teste. */
    discarder: (() -> Unit)? = null,
    private val retryBackoffMillis: Long = DEFAULT_RETRY_BACKOFF_MS,
) : SecureTokenStorage {

    private val fileName = "secure_" + serviceName.replace(Regex("[^A-Za-z0-9_]"), "_")
    private val open: () -> SharedPreferences = opener ?: { openEncrypted(context()) }
    private val discard: () -> Unit = discarder ?: { context().deleteSharedPreferences(fileName) }

    private val openLock = Mutex()

    @Volatile
    private var opened: SharedPreferences? = null

    private fun context(): Context = AndroidAppContext.get()
        ?: throw IllegalStateException(
            "KmpLib não foi inicializado. Chame KmpLib.init(context) no Application.onCreate()."
        )

    private fun openEncrypted(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * O arquivo cifrado, aberto uma vez por processo. Falha passageira é tentada de novo; só depois de
     * [OPEN_ATTEMPTS] falhas seguidas o cofre é tido como irrecuperável e recriado vazio.
     */
    private suspend fun prefs(): SharedPreferences {
        opened?.let { return it }
        return openLock.withLock {
            opened ?: openWithRecovery().also { opened = it }
        }
    }

    private suspend fun openWithRecovery(): SharedPreferences {
        var lastError: Exception? = null
        for (attempt in 1..OPEN_ATTEMPTS) {
            try {
                return open()
            } catch (e: Exception) {
                lastError = e
                AppLogger.w(TAG, "Cofre cifrado não abriu (tentativa $attempt de $OPEN_ATTEMPTS): ${e::class.simpleName}")
                if (attempt < OPEN_ATTEMPTS) delay(retryBackoffMillis * attempt)
            }
        }
        AppLogger.e(TAG, "Cofre cifrado irrecuperável após $OPEN_ATTEMPTS tentativas; recriando vazio.", lastError)
        discard()
        return open()
    }

    override suspend fun getString(key: String): String? = withContext(Dispatchers.IO) {
        runCatching { prefs().getString(key, null) }
            .onFailure { AppLogger.w(TAG, "Leitura do cofre falhou: ${it::class.simpleName}") }
            .getOrNull()
    }

    override suspend fun putString(key: String, value: String) {
        withContext(Dispatchers.IO) {
            val prefs = prefs()
            commitDurably("gravar") { prefs.edit().putString(key, value) }
        }
    }

    override suspend fun remove(key: String) {
        withContext(Dispatchers.IO) {
            val prefs = prefs()
            commitDurably("remover") { prefs.edit().remove(key) }
        }
    }

    /**
     * Limpa o cofre — fail-closed: se o `commit()` não gravar, o arquivo cifrado é DESCARTADO (e a
     * instância em cache esquecida). É o último degrau do logout ([AuthSessionStore.clear]).
     */
    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            val prefs = prefs()
            try {
                commitDurably("limpar") { prefs.edit().clear() }
            } catch (e: IllegalStateException) {
                AppLogger.e(TAG, "Cofre não limpou no disco; descartando o arquivo.")
                openLock.withLock {
                    discard()
                    opened = null
                }
            }
        }
    }

    /**
     * `commit()` síncrono — só retorna com o arquivo no disco. `false` (falha de I/O) é repetido uma
     * vez com um editor novo; persistindo, lança para o chamador saber que o disco NÃO tem o valor.
     */
    private fun commitDurably(operation: String, edit: () -> SharedPreferences.Editor) {
        if (edit().commit()) return
        AppLogger.w(TAG, "commit() do cofre falhou ao $operation; tentando de novo.")
        if (edit().commit()) return
        throw IllegalStateException("Cofre seguro não gravou no disco ($operation).")
    }

    companion object {
        private const val TAG = "SecureTokenStorage"

        /** Tentativas de abrir o cofre antes de dá-lo como irrecuperável. */
        internal const val OPEN_ATTEMPTS = 3

        /** Espera base entre tentativas (cresce linearmente: 150 ms, 300 ms). */
        internal const val DEFAULT_RETRY_BACKOFF_MS = 150L
    }
}
