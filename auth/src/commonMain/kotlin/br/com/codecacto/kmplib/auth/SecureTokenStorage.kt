package br.com.codecacto.kmplib.auth

/**
 * Armazenamento **seguro** de segredos chave/valor (tokens de autenticação própria).
 *
 * Diferente de [AppPreferences][br.com.codecacto.kmplib.core.prefs.AppPreferences] (SharedPreferences/
 * NSUserDefaults em texto claro), esta abstração usa o **cofre nativo de cada plataforma** — o
 * padrão-ouro para guardar credenciais de longa duração como o `refreshToken` rotativo:
 *
 * - **Android:** `EncryptedSharedPreferences` (Jetpack Security), cifrado com uma `MasterKey`
 *   ancorada no Android Keystore (AES-256-GCM).
 * - **iOS:** Keychain Services (`kSecClassGenericPassword`), acessível após o primeiro unlock.
 *
 * Só guarda **String**; o [AuthSessionStore] serializa a sessão inteira (tokens + identidade) num
 * único valor JSON sob uma chave. Toda operação é `suspend` (I/O de disco/keystore) e **best-effort**:
 * uma falha de leitura devolve `null` (usuário tratado como deslogado), nunca lança para a UI.
 */
interface SecureTokenStorage {
    /** Lê o valor da [key], ou `null` se ausente/ilegível. */
    suspend fun getString(key: String): String?

    /** Grava (ou substitui) o valor da [key] no cofre. */
    suspend fun putString(key: String, value: String)

    /** Remove a [key] do cofre. Idempotente. */
    suspend fun remove(key: String)

    /** Apaga tudo o que este cofre (identificado por `serviceName`) guarda. */
    suspend fun clear()
}

/**
 * Cofre seguro da plataforma para a [key][SecureTokenStorage] chave/valor.
 *
 * @param serviceName namespace do cofre — vira o nome do arquivo de `EncryptedSharedPreferences`
 *   no Android e o `kSecAttrService` no Keychain do iOS. Apps distintos (ou usos distintos) usam
 *   namespaces distintos para não colidir.
 */
expect fun secureTokenStorage(serviceName: String = DEFAULT_SECURE_STORAGE_SERVICE): SecureTokenStorage

/** Namespace default do cofre de tokens da own-auth. */
const val DEFAULT_SECURE_STORAGE_SERVICE: String = "kmplib.ownauth"
