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
 * - **iOS:** Keychain Services (`kSecClassGenericPassword`), acessível após o primeiro unlock. Como o
 *   Keychain sobrevive à desinstalação, a primeira operação de cada instalação descarta o que ficou
 *   de uma instalação anterior (marca no `UserDefaults`, 2.205.0).
 *
 * Só guarda **String**; o [AuthSessionStore] serializa a sessão inteira (tokens + identidade) num
 * único valor JSON sob uma chave. Toda operação é `suspend` (I/O de disco/keystore). Leitura é
 * **best-effort** (falha devolve `null`); escrita é **durável ou lança** (ver [putString]).
 */
interface SecureTokenStorage {
    /** Lê o valor da [key], ou `null` se ausente/ilegível. */
    suspend fun getString(key: String): String?

    /**
     * Grava (ou substitui) o valor da [key] no cofre — e **só retorna com ele DURÁVEL** (2.292.0): o
     * refresh token é rotativo, e um par que ficou só na memória quando o processo morreu faz a
     * próxima abertura reapresentar o anterior (reuso → família revogada → logout). Falha de gravação
     * **lança**; quem chama ([AuthSessionStore]) registra e não propaga à UI.
     */
    suspend fun putString(key: String, value: String)

    /** Remove a [key] do cofre, de forma durável (um logout que não chegou ao disco ressuscita). Idempotente. */
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
