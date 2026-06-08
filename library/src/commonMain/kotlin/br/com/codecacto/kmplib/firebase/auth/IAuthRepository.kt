package br.com.codecacto.kmplib.firebase.auth

import kotlinx.coroutines.flow.Flow

/**
 * Contrato de autenticação.
 *
 * Abstrai a implementação concreta ([AuthRepository], baseada em Firebase) para que
 * ViewModels possam depender apenas da interface. Isso permite testar ViewModels em
 * `commonTest` com fakes/mocks, sem exigir Firebase real.
 *
 * Uso:
 * ```kotlin
 * class MyViewModel(private val auth: IAuthRepository) { ... }
 *
 * // produção: bind AuthRepository concreto
 * // testes: FakeAuthRepository : IAuthRepository
 * ```
 */
interface IAuthRepository {

    /**
     * Flow do usuário atual (null se não autenticado).
     */
    val currentUser: Flow<User?>

    /**
     * Flow indicando se há usuário autenticado.
     */
    val isLoggedIn: Flow<Boolean>

    /**
     * Obtém o usuário atual de forma síncrona (pode ser null).
     */
    val currentUserSync: User?

    /**
     * Verifica se há usuário autenticado de forma síncrona.
     */
    val isLoggedInSync: Boolean

    /**
     * Login com email e senha.
     */
    suspend fun signInWithEmail(email: String, password: String): Result<User>

    /**
     * Login com Google (idToken obtido do Google Sign-In nativo).
     */
    suspend fun signInWithGoogle(idToken: String, accessToken: String? = null): Result<User>

    /**
     * Login com Apple (idToken e nonce obtidos do Apple Sign-In nativo).
     */
    suspend fun signInWithApple(idToken: String, nonce: String): Result<User>

    /**
     * Cadastro com email e senha.
     */
    suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String? = null
    ): Result<User>

    /**
     * Envia email de recuperação de senha.
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    /**
     * Atualiza o perfil do usuário.
     */
    suspend fun updateProfile(displayName: String? = null, photoUrl: String? = null): Result<Unit>

    /**
     * Altera a senha do usuário (requer reautenticação recente).
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>

    /**
     * Exclui a conta do usuário (requer reautenticação recente).
     */
    suspend fun deleteAccount(password: String? = null): Result<Unit>

    /**
     * Logout.
     */
    suspend fun signOut()

    /**
     * Envia email de verificação.
     */
    suspend fun sendEmailVerification(): Result<Unit>

    /**
     * Obtém o ID token do usuário atual (para uso em APIs backend).
     */
    suspend fun getIdToken(forceRefresh: Boolean = false): Result<String>
}
