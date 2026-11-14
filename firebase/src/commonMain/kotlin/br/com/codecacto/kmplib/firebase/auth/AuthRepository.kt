package br.com.codecacto.kmplib.firebase.auth

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.EmailAuthProvider
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
import kotlinx.coroutines.flow.onEach
import br.com.codecacto.kmplib.core.identity.SignedInAccount
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
     * O estado de login do Firebase, publicando o `uid` em [SignedInAccount] a cada emissão
     * (2.364.0): é o que faz o `CrashReporter` mandar o `user.id` do GlitchTip sem o app chamar
     * `setUser`. Vale enquanto o app OBSERVA [currentUser] ou [isLoggedIn] (o que todo app com login
     * faz para decidir a rota); app que nunca observa chama `SignedInAccount.publish(uid)` ele mesmo.
     * Publicar é idempotente — vários coletores mandam o mesmo valor.
     */
    private val authState: Flow<FirebaseUser?> = auth.authStateChanged.onEach { firebaseUser ->
        SignedInAccount.publish(firebaseUser?.uid)
    }

    /**
     * Flow do usuário atual (null se não autenticado).
     */
    override val currentUser: Flow<User?> = authState.map { firebaseUser ->
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
        authState.map { it != null }.distinctUntilChanged()

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
     * Altera a senha do usuário.
     *
     * Reconfirma a senha atual pelo caminho oficial do Firebase —
     * `currentUser.reauthenticate(EmailAuthProvider.credential(email, senhaAtual))` — e só então
     * chama `updatePassword`. Não abre sessão nova (até a 2.340.1 fazia `signInWithEmailAndPassword`).
     * Erros: senha atual errada → [AuthException.InvalidCredentials]; nova fraca →
     * [AuthException.WeakPassword]; excesso de tentativas → [AuthException.TooManyRequests];
     * sem rede → [AuthException.NetworkError].
     */
    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        val result = changePasswordReauthenticated(
            target = auth.currentUser?.let(::FirebaseReauthTarget),
            currentPassword = currentPassword,
            newPassword = newPassword,
        )
        result.fold(
            onSuccess = { AppLogger.d(TAG, "Senha alterada com sucesso") },
            onFailure = { AppLogger.e(TAG, "Erro ao alterar senha", it) },
        )
        return result
    }

    /**
     * Exclui a conta do usuário. Com [password] (conta de e-mail/senha), reconfirma a credencial pelo
     * caminho oficial (`reauthenticate`) antes do `delete`.
     */
    override suspend fun deleteAccount(password: String?): Result<Unit> {
        val result = deleteAccountReauthenticated(
            target = auth.currentUser?.let(::FirebaseReauthTarget),
            password = password,
        )
        result.fold(
            onSuccess = { AppLogger.d(TAG, "Conta excluída") },
            onFailure = { AppLogger.e(TAG, "Erro ao excluir conta", it) },
        )
        return result
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

    private fun mapAuthException(e: Exception): AuthException = mapFirebaseAuthException(e)

    /** Adapta o `FirebaseUser` do GitLive à costura [ReauthTarget]. */
    private class FirebaseReauthTarget(private val user: FirebaseUser) : ReauthTarget {
        override val email: String? get() = user.email

        override suspend fun reauthenticateWithPassword(email: String, password: String) {
            user.reauthenticate(EmailAuthProvider.credential(email, password))
        }

        override suspend fun updatePassword(newPassword: String) {
            user.updatePassword(newPassword)
        }

        override suspend fun delete() {
            user.delete()
        }
    }
}

// `AuthException` mora no `kmplib-core` desde a 2.291.0 (mesmo pacote) — ver AuthException.kt.
