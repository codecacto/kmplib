package br.com.codecacto.kmplib.core.prefs

import kotlinx.coroutines.flow.Flow

/**
 * Wrapper KMP para preferências chave/valor.
 *
 * - Android: usa SharedPreferences (cache `app_prefs`)
 * - iOS: usa NSUserDefaults
 *
 * Use como singleton (via DI ou via [appPreferences]). Toda operação `set*`
 * notifica os flows correspondentes em `observe*`.
 *
 * Para testes, use uma implementação fake (in-memory) que implemente esta
 * interface — não tente instanciar a versão real.
 *
 * Exemplos:
 * ```
 * val prefs: AppPreferences = appPreferences()
 *
 * // Suspend get/set
 * prefs.setString("theme", "dark")
 * val theme = prefs.getString("theme", "light")
 *
 * // Reativo
 * prefs.observeBoolean("notifications_enabled", default = true)
 *     .collect { enabled -> ... }
 * ```
 *
 * @see PrefKeys para chaves comuns sugeridas.
 */
interface AppPreferences {
    suspend fun getString(key: String, default: String = ""): String
    suspend fun setString(key: String, value: String)
    suspend fun getBoolean(key: String, default: Boolean = false): Boolean
    suspend fun setBoolean(key: String, value: Boolean)
    suspend fun getInt(key: String, default: Int = 0): Int
    suspend fun setInt(key: String, value: Int)
    suspend fun getLong(key: String, default: Long = 0L): Long
    suspend fun setLong(key: String, value: Long)
    suspend fun getFloat(key: String, default: Float = 0f): Float
    suspend fun setFloat(key: String, value: Float)

    suspend fun has(key: String): Boolean
    suspend fun remove(key: String)
    suspend fun clear()

    fun observeString(key: String, default: String = ""): Flow<String>
    fun observeBoolean(key: String, default: Boolean = false): Flow<Boolean>
    fun observeInt(key: String, default: Int = 0): Flow<Int>
    fun observeLong(key: String, default: Long = 0L): Flow<Long>
}

/**
 * Factory que retorna a implementação real de [AppPreferences] para a plataforma.
 *
 * Para uso em produção. Em testes, use uma fake que implemente diretamente
 * a interface [AppPreferences] e injete via DI.
 */
expect fun appPreferences(): AppPreferences

/**
 * Chaves sugeridas para uso comum entre apps. Cada app pode adicionar suas próprias
 * em um `object` local — estas são apenas convenções recorrentes.
 */
object PrefKeys {
    const val THEME = "theme"
    const val DARK_MODE = "dark_mode"
    const val LANGUAGE = "language"
    const val ONBOARDING_SEEN = "onboarding_seen"
    const val NOTIFICATIONS_ENABLED = "notifications_enabled"
    const val FCM_TOKEN_PENDING = "fcm_token_pending"
    const val LAST_SYNC = "last_sync"
}
