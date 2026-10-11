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
            .onFailure { AppLogger.w(TAG, "Falha ao gravar sessão no cofre: ${it::class.simpleName}") }
    }

    /**
     * Encerra a sessão no cofre — **fail-closed** (2.292.0): se apagar a chave falhar, apaga o cofre
     * inteiro (o Android descarta o arquivo cifrado se nem isso gravar). Um logout que não chegou ao
     * disco ressuscitaria a sessão na próxima abertura.
     */
    suspend fun clear() {
        runCatching { storage.remove(key) }.onFailure { first ->
            AppLogger.w(TAG, "Falha ao apagar sessão do cofre (${first::class.simpleName}); limpando o cofre inteiro.")
            runCatching { storage.clear() }
                .onFailure { AppLogger.e(TAG, "Cofre não pôde ser limpo: ${it::class.simpleName}") }
        }
    }

    companion object {
        private const val DEFAULT_KEY = "own_auth_session"
        private const val TAG = "AuthSessionStore"
    }
}
