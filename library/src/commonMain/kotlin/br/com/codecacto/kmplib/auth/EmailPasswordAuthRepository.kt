package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.firebase.auth.AuthException
import br.com.codecacto.kmplib.firebase.auth.IAuthRepository
import br.com.codecacto.kmplib.firebase.auth.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementação own-auth do [IAuthRepository] — e-mail + senha contra o backend REST próprio (sem
 * Firebase). É **aditiva**: um `IAuthRepository` a mais ao lado do `AuthRepository` (Firebase); o app
 * escolhe qual bindar no Koin (ver [AuthProvider]/[ownAuth]). Nenhum app Firebase é afetado.
 *
 * Também implementa [OwnAuthService] (registro com termos + reset por token do convite).
 *
 * ### currentUser a partir do `sub`
 * O JWT próprio carrega só `sub` (id da conta) e o backend não expõe `GET /me`. O [User] é remontado
 * da [OwnAuthSession]: `id = accountId` (o `sub` decodificado), `email`/`displayName` capturados no
 * login/registro, `providerId = "password"`. Não inventamos endpoint de perfil.
 *
 * ### Operações não suportadas pelo backend (falham explicitamente, nunca em silêncio)
 * `signInWithGoogle`/`signInWithApple`/`updateProfile`/`changePassword`/`deleteAccount`/
 * `sendEmailVerification` não têm endpoint no contrato own-auth atual → `Result.failure(
 * AuthException.UnknownError)`. `signUpWithEmail` também falha de propósito: registre por
 * [OwnAuthService.register] (que exige `acceptedTerms`).
 */
class EmailPasswordAuthRepository(
    private val api: OwnAuthApi,
    private val tokenManager: OwnAuthTokenManager,
    private val texts: OwnAuthTexts = OwnAuthTexts(),
) : IAuthRepository, OwnAuthService {

    override val currentUser: Flow<User?> = tokenManager.session.map { it?.toUser() }

    override val isLoggedIn: Flow<Boolean> = tokenManager.session.map { it != null }

    override val currentUserSync: User? get() = tokenManager.session.value?.toUser()

    override val isLoggedInSync: Boolean get() = tokenManager.session.value != null

    // ---- Login / sessão --------------------------------------------------

    override suspend fun signInWithEmail(email: String, password: String): Result<User> =
        api.login(email, password).mapAndAdopt(email)

    override suspend fun getIdToken(forceRefresh: Boolean): Result<String> {
        val token = tokenManager.accessToken(forceRefresh)
            ?: return Result.failure(AuthException.NotAuthenticated)
        return Result.success(token)
    }

    override suspend fun signOut() {
        // Revoga a família do refresh no servidor (best-effort) e limpa a sessão local.
        tokenManager.session.value?.refreshToken?.let { api.logout(it) }
        tokenManager.clear()
    }

    // ---- OwnAuthService --------------------------------------------------

    override suspend fun register(
        name: String,
        email: String,
        password: String,
        acceptedTerms: Boolean,
    ): Result<User> = api.register(name, email, password, acceptedTerms).mapAndAdopt(email, name)

    override suspend fun requestPasswordReset(email: String): Result<Unit> =
        api.requestPasswordReset(email).mapAuthError()

    override suspend fun confirmPasswordReset(token: String, newPassword: String): Result<Unit> =
        api.confirmPasswordReset(token, newPassword).mapAuthError()

    /** `IAuthRepository.sendPasswordResetEmail` mapeia para `password/forgot` (mesma mecânica). */
    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        requestPasswordReset(email)

    // ---- Não suportadas pelo backend own-auth ----------------------------

    override suspend fun signUpWithEmail(email: String, password: String, displayName: String?): Result<User> =
        unsupported("Cadastro own-auth exige aceite de termos — use OwnAuthService.register(...).")

    override suspend fun signInWithGoogle(idToken: String, accessToken: String?): Result<User> =
        unsupported("Login com Google não disponível na autenticação por e-mail e senha.")

    override suspend fun signInWithApple(idToken: String, nonce: String): Result<User> =
        unsupported("Login com Apple não disponível na autenticação por e-mail e senha.")

    override suspend fun updateProfile(displayName: String?, photoUrl: String?): Result<Unit> =
        Result.failure(AuthException.UnknownError(texts.unsupported))

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
        Result.failure(AuthException.UnknownError(texts.unsupported))

    override suspend fun deleteAccount(password: String?): Result<Unit> =
        Result.failure(AuthException.UnknownError(texts.unsupported))

    override suspend fun sendEmailVerification(): Result<Unit> =
        Result.failure(AuthException.UnknownError(texts.unsupported))

    // ---- Helpers ---------------------------------------------------------

    private suspend fun Result<OwnAuthTokens>.mapAndAdopt(email: String, name: String = ""): Result<User> =
        fold(
            onSuccess = { tokens ->
                tokenManager.adopt(tokens, email = email, name = name)
                Result.success(tokenManager.session.value?.toUser() ?: fallbackUser(email, name))
            },
            onFailure = { Result.failure(it.toAuthException()) },
        )

    private fun <T> Result<T>.mapAuthError(): Result<T> =
        fold(onSuccess = { Result.success(it) }, onFailure = { Result.failure(it.toAuthException()) })

    private fun <T> unsupported(message: String): Result<T> =
        Result.failure(AuthException.UnknownError(message))

    private fun fallbackUser(email: String, name: String) =
        User(id = "", email = email, displayName = name.ifBlank { null })

    private fun OwnAuthSession.toUser(): User = User(
        id = accountId,
        email = email,
        displayName = name.ifBlank { null },
        photoUrl = null,
        isEmailVerified = false,
        providerId = "password",
    )

    /** Converte a [OwnAuthException] tipada para a [AuthException] canônica que os apps já tratam. */
    private fun Throwable.toAuthException(): AuthException = when (this) {
        is AuthException -> this
        is OwnAuthException -> when (this) {
            is OwnAuthException.InvalidCredentials, is OwnAuthException.NotAuthenticated ->
                AuthException.InvalidCredentials
            is OwnAuthException.EmailAlreadyInUse -> AuthException.EmailAlreadyInUse
            is OwnAuthException.WeakPassword -> AuthException.WeakPassword
            is OwnAuthException.TooManyRequests -> AuthException.TooManyRequests
            is OwnAuthException.Network -> AuthException.NetworkError
            else -> AuthException.UnknownError(message)
        }
        else -> AuthException.UnknownError(message ?: "Erro desconhecido")
    }
}
