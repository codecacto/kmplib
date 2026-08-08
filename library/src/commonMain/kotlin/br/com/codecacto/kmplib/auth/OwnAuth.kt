package br.com.codecacto.kmplib.auth

/**
 * Modo de autenticação que o `AppConfig` de um app declara para o DI escolher qual [IAuthRepository]
 * [br.com.codecacto.kmplib.firebase.auth.IAuthRepository] prover.
 *
 * **Default e apps existentes = [FIREBASE]** (nada muda). Apps novos sem Firebase (piloto Meu Barbeiro)
 * declaram [OWN].
 *
 * ```kotlin
 * // AppConfig do app:
 * object AppConfig { val authProvider = AuthProvider.OWN /* ou FIREBASE */ }
 *
 * // Koin:
 * when (AppConfig.authProvider) {
 *     AuthProvider.FIREBASE -> single { AuthRepository() } bind IAuthRepository::class
 *     AuthProvider.OWN -> {
 *         single { ownAuth(OwnAuthConfig(httpClient = get(), baseUrl = API_BASE, authBasePath = "/v1/staff/auth")) }
 *         single<IAuthRepository> { get<OwnAuth>().repository }
 *         single<OwnAuthService> { get<OwnAuth>().service }
 *         single<OwnAuthSocialService> { get<OwnAuth>().social }   // se o backend tem /auth/social
 *     }
 * }
 * ```
 */
enum class AuthProvider { FIREBASE, OWN }

/**
 * Conjunto pronto de peças da autenticação própria, montado por [ownAuth]. Exponha no Koin como um
 * `single` e derive os bindings ([repository] como `IAuthRepository`, [service] como [OwnAuthService]).
 *
 * Semeie a sessão do cofre no bootstrap: `koin.get<OwnAuth>().restore()` (uma vez, antes de decidir a
 * rota inicial login vs. home).
 */
class OwnAuth internal constructor(
    val api: OwnAuthApi,
    val tokenManager: OwnAuthTokenManager,
    private val repositoryImpl: EmailPasswordAuthRepository,
) {
    /** O [IAuthRepository] own-auth (bindar a interface no Koin). */
    val repository: EmailPasswordAuthRepository get() = repositoryImpl

    /** O serviço de registro + reset (bindar [OwnAuthService] no Koin, mesma instância). */
    val service: OwnAuthService get() = repositoryImpl

    /**
     * O login social Google/Apple (bindar [OwnAuthSocialService] no Koin, mesma instância).
     * Só faz sentido quando o backend expõe `POST {authBasePath}/social`.
     */
    val social: OwnAuthSocialService get() = repositoryImpl

    /** Lê o cofre e semeia a sessão em memória. Chamar uma vez no start do app. */
    suspend fun restore(): OwnAuthSession? = tokenManager.restore()
}

/**
 * Monta a autenticação própria a partir de [config], usando o cofre seguro da plataforma para os
 * tokens. Fábrica única para o DI do app.
 *
 * @param config base/paths/httpClient/skew/textos.
 * @param storage cofre de tokens (default: [secureTokenStorage] da plataforma — EncryptedSharedPreferences
 *   no Android, Keychain no iOS). Em testes, injete uma fake em memória.
 */
fun ownAuth(
    config: OwnAuthConfig,
    storage: SecureTokenStorage = secureTokenStorage(),
): OwnAuth {
    val api = OwnAuthApi(config)
    val sessionStore = AuthSessionStore(storage)
    val tokenManager = OwnAuthTokenManager(api, sessionStore, config.refreshSkewSeconds)
    val repository = EmailPasswordAuthRepository(api, tokenManager, config.texts)
    return OwnAuth(api, tokenManager, repository)
}
