package br.com.codecacto.kmplib.firebase.auth

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.OAuthProvider
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import br.com.codecacto.kmplib.core.util.AppLogger

/**
 * Repositório de autenticação Firebase.
 *
 * Uso:
 * ```kotlin
 * val authRepository = AuthRepository()
 *
 * // Observar estado de autenticação
 * authRepository.currentUser.collect { user ->
 *     if (user != null) {
 *         // Usuário logado
 *     }
 * }
 *
 * // Login com email/senha
 * val result = authRepository.signInWithEmail("email@exemplo.com", "senha123")
 * result.fold(
 *     onSuccess = { user -> /* sucesso */ },
 *     onFailure = { error -> /* erro */ }
 * )
 * ```
 */
class AuthRepository : IAuthRepository {

    private val auth: FirebaseAuth = Firebase.auth

    companion object {
        private const val TAG = "AuthRepository"
    }

    /**
     * Flow do usuário atual (null se não autenticado).
     */
    override val currentUser: Flow<User?> = auth.authStateChanged.map { firebaseUser ->
        firebaseUser?.toUser()
    }

    /**
     * Flow indicando se há usuário autenticado.
     *
     * ⚠️ **`distinctUntilChanged` não é otimização — é correção** (28/ago/2026). A fonte emite a
     * cada mudança do usuário (renovação de token, atualização de perfil, releitura do estado), e
     * `map { it != null }` transforma vários desses eventos no MESMO `true`. Quem observa este flow
     * para recarregar a tela — que é o uso natural dele — dispara uma requisição por emissão: no
     * Cidade Conectada foram **quatro chamadas da mesma rota em 300 ms**, e o app inteiro bateu no
     * rate limit do servidor com **um único usuário** navegando.
     *
     * Isto é estado, não evento: "está logado?" só interessa quando a resposta MUDA.
     */
    override val isLoggedIn: Flow<Boolean> =
        auth.authStateChanged.map { it != null }.distinctUntilChanged()

    /**
     * Obtém o usuário atual de forma síncrona (pode ser null).
     */
    override val currentUserSync: User?
        get() = auth.currentUser?.toUser()

    /**
     * Verifica se há usuário autenticado de forma síncrona.
     */
    override val isLoggedInSync: Boolean
        get() = auth.currentUser != null

    // ========================
    // LOGIN
    // ========================

    /**
     * Login com email e senha.
     */
    override suspend fun signInWithEmail(email: String, password: String): Result<User> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password)
            val user = result.user?.toUser()
                ?: return Result.failure(AuthException.UnknownError("Usuário não encontrado"))
            AppLogger.d(TAG, "Login com email realizado")
            Result.success(user)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro no login com email", e)
            Result.failure(mapAuthException(e))
        }
    }

    /**
     * Login com Google (idToken obtido do Google Sign-In nativo).
     */
    override suspend fun signInWithGoogle(idToken: String, accessToken: String?): Result<User> {
        return try {
            val credential = GoogleAuthProvider.credential(idToken, accessToken)
            val result = auth.signInWithCredential(credential)
            val user = result.user?.toUser()
                ?: return Result.failure(AuthException.UnknownError("Usuário não encontrado"))
            AppLogger.d(TAG, "Login com Google realizado")
            Result.success(user)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro no login com Google", e)
            Result.failure(mapAuthException(e))
        }
    }

    /**
     * Login com Apple (idToken e nonce obtidos do Apple Sign-In nativo).
     */
    override suspend fun signInWithApple(idToken: String, nonce: String): Result<User> {
        return try {
            val credential = OAuthProvider.credential(
                providerId = "apple.com",
                idToken = idToken,
                rawNonce = nonce
            )
            val result = auth.signInWithCredential(credential)
            val user = result.user?.toUser()
                ?: return Result.failure(AuthException.UnknownError("Usuário não encontrado"))
            AppLogger.d(TAG, "Login com Apple realizado")
            Result.success(user)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro no login com Apple", e)
            Result.failure(mapAuthException(e))
        }
    }

    // ========================
    // CADASTRO
    // ========================

    /**
     * Cadastro com email e senha.
     */
    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String?
    ): Result<User> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password)
            val firebaseUser = result.user
                ?: return Result.failure(AuthException.UnknownError("Falha ao criar usuário"))

            // Atualiza o displayName se fornecido
            if (displayName != null) {
                firebaseUser.updateProfile(displayName = displayName)
            }

            val user = firebaseUser.toUser()
            AppLogger.d(TAG, "Cadastro realizado")
            Result.success(user)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro no cadastro", e)
            Result.failure(mapAuthException(e))
        }
    }

    // ========================
    // RECUPERAÇÃO DE SENHA
    // ========================

    /**
     * Envia email de recuperação de senha.
     */
    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email)
            AppLogger.d(TAG, "Email de recuperação enviado")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao enviar email de recuperação", e)
            Result.failure(mapAuthException(e))
        }
    }

    // ========================
    // GERENCIAMENTO DE CONTA
    // ========================

    /**
     * Atualiza o perfil do usuário.
     */
    override suspend fun updateProfile(displayName: String?, photoUrl: String?): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(AuthException.NotAuthenticated)

            currentUser.updateProfile(displayName = displayName, photoUrl = photoUrl)
            AppLogger.d(TAG, "Perfil atualizado")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao atualizar perfil", e)
            Result.failure(mapAuthException(e))
        }
    }

    /**
     * Altera a senha do usuário (requer reautenticação recente).
     */
    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(AuthException.NotAuthenticated)

            val email = currentUser.email
                ?: return Result.failure(AuthException.UnknownError("Email não encontrado"))

            // Reautentica o usuário
            auth.signInWithEmailAndPassword(email, currentPassword)

            // Altera a senha
            currentUser.updatePassword(newPassword)
            AppLogger.d(TAG, "Senha alterada com sucesso")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao alterar senha", e)
            Result.failure(mapAuthException(e))
        }
    }

    /**
     * Exclui a conta do usuário (requer reautenticação recente).
     */
    override suspend fun deleteAccount(password: String?): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(AuthException.NotAuthenticated)

            // Se for login com email/senha, reautentica
            val email = currentUser.email
            if (password != null && email != null) {
                auth.signInWithEmailAndPassword(email, password)
            }

            currentUser.delete()
            AppLogger.d(TAG, "Conta excluída")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao excluir conta", e)
            Result.failure(mapAuthException(e))
        }
    }

    /**
     * Logout.
     */
    override suspend fun signOut() {
        try {
            auth.signOut()
            AppLogger.d(TAG, "Logout realizado")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro no logout", e)
        }
    }

    /**
     * Envia email de verificação.
     */
    override suspend fun sendEmailVerification(): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(AuthException.NotAuthenticated)

            currentUser.sendEmailVerification()
            AppLogger.d(TAG, "Email de verificação enviado")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao enviar email de verificação", e)
            Result.failure(mapAuthException(e))
        }
    }

    /**
     * Obtém o ID token do usuário atual (para uso em APIs backend).
     */
    override suspend fun getIdToken(forceRefresh: Boolean): Result<String> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(AuthException.NotAuthenticated)

            val token = currentUser.getIdToken(forceRefresh)
                ?: return Result.failure(AuthException.UnknownError("Token não disponível"))

            Result.success(token)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao obter token", e)
            Result.failure(mapAuthException(e))
        }
    }

    // ========================
    // HELPERS
    // ========================

    private fun FirebaseUser.toUser(): User {
        return User(
            id = uid,
            email = email ?: "",
            displayName = displayName,
            photoUrl = photoURL,
            isEmailVerified = isEmailVerified,
            providerId = providerData.firstOrNull()?.providerId ?: "password"
        )
    }

    private fun mapAuthException(e: Exception): AuthException {
        val message = e.message?.lowercase() ?: ""

        return when {
            "invalid-email" in message || "badly formatted" in message ->
                AuthException.InvalidEmail

            "wrong-password" in message || "invalid-credential" in message ||
                    "invalid credential" in message ->
                AuthException.InvalidCredentials

            "user-not-found" in message || "no user record" in message ->
                AuthException.UserNotFound

            "email-already-in-use" in message || "already in use" in message ->
                AuthException.EmailAlreadyInUse

            "weak-password" in message || "should be at least" in message ->
                AuthException.WeakPassword

            "too-many-requests" in message || "blocked" in message ->
                AuthException.TooManyRequests

            "network" in message || "connection" in message ->
                AuthException.NetworkError

            "requires-recent-login" in message || "recent" in message ->
                AuthException.RequiresRecentLogin

            else -> AuthException.UnknownError(e.message ?: "Erro desconhecido")
        }
    }
}

/**
 * Exceções de autenticação.
 */
sealed class AuthException(message: String) : Exception(message) {
    data object InvalidEmail : AuthException("Email inválido")
    data object InvalidCredentials : AuthException("Email ou senha incorretos")
    data object UserNotFound : AuthException("Usuário não encontrado")
    data object EmailAlreadyInUse : AuthException("Este email já está em uso")
    data object WeakPassword : AuthException("Senha muito fraca. Use pelo menos 6 caracteres")
    data object TooManyRequests : AuthException("Muitas tentativas. Tente novamente mais tarde")
    data object NetworkError : AuthException("Erro de conexão. Verifique sua internet")
    data object RequiresRecentLogin : AuthException("Por segurança, faça login novamente")
    data object NotAuthenticated : AuthException("Usuário não autenticado")
    data class UnknownError(override val message: String) : AuthException(message)
}
