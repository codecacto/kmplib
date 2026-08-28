package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.serialization.json.Json

/**
 * Persistência da [OwnAuthSession] no cofre seguro da plataforma ([SecureTokenStorage]) — serializa a
 * sessão inteira (tokens + identidade) como um único valor JSON sob uma chave. Best-effort: leitura
 * corrompida vira `null` (usuário deslogado), nunca lança.
 */
class AuthSessionStore(
    private val storage: SecureTokenStorage,
    private val key: String = DEFAULT_KEY,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    suspend fun load(): OwnAuthSession? {
        val raw = runCatching { storage.getString(key) }.getOrNull() ?: return null
        return runCatching { json.decodeFromString(OwnAuthSession.serializer(), raw) }
            .onFailure { AppLogger.w(TAG, "Sessão corrompida no cofre; ignorando.") }
            .getOrNull()
    }

    suspend fun save(session: OwnAuthSession) {
        val raw = json.encodeToString(OwnAuthSession.serializer(), session)
        runCatching { storage.putString(key, raw) }
            .onFailure { AppLogger.w(TAG, "Falha ao gravar sessão no cofre: ${it.message}") }
    }

    suspend fun clear() {
        runCatching { storage.remove(key) }
    }

    companion object {
        private const val DEFAULT_KEY = "own_auth_session"
        private const val TAG = "AuthSessionStore"
    }
}
