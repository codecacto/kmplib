package br.com.codecacto.kmplib.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.context.AndroidAppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun secureTokenStorage(serviceName: String): SecureTokenStorage =
    AndroidSecureTokenStorage(serviceName)

/**
 * Cofre seguro no Android via **`EncryptedSharedPreferences`** (Jetpack Security): as entradas ficam
 * cifradas com AES-256-GCM sob uma [MasterKey] ancorada no Android Keystore. É o caminho recomendado
 * pelo Google para persistir segredos localmente.
 *
 * A criação do arquivo cifrado é **preguiçosa** e tolerante: se o Keystore corromper (raro, mas
 * documentado em migrações de dispositivo/backup), apaga o arquivo e recria uma vez — perder o
 * refresh token só força um novo login, nunca crasha.
 */
internal class AndroidSecureTokenStorage(
    private val serviceName: String,
) : SecureTokenStorage {

    private val fileName = "secure_" + serviceName.replace(Regex("[^A-Za-z0-9_]"), "_")

    private fun context(): Context = AndroidAppContext.get()
        ?: throw IllegalStateException(
            "KmpLib não foi inicializado. Chame KmpLib.init(context) no Application.onCreate()."
        )

    private fun open(context: Context): SharedPreferences {
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

    /** Abre o arquivo cifrado; se o Keystore estiver inconsistente, recria uma vez. */
    private fun prefs(): SharedPreferences {
        val context = context()
        return try {
            open(context)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Cofre cifrado inconsistente; recriando. ${e.message}")
            context.deleteSharedPreferences(fileName)
            open(context)
        }
    }

    override suspend fun getString(key: String): String? = withContext(Dispatchers.IO) {
        runCatching { prefs().getString(key, null) }.getOrNull()
    }

    override suspend fun putString(key: String, value: String) {
        withContext(Dispatchers.IO) {
            runCatching { prefs().edit().putString(key, value).apply() }
        }
    }

    override suspend fun remove(key: String) {
        withContext(Dispatchers.IO) {
            runCatching { prefs().edit().remove(key).apply() }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            runCatching { prefs().edit().clear().apply() }
        }
    }

    companion object {
        private const val TAG = "SecureTokenStorage"
    }
}
