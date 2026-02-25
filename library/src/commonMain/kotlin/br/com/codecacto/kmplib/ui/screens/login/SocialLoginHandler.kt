package br.com.codecacto.kmplib.ui.screens.login

/**
 * Interface para implementar Social Login com Google
 *
 * Cada plataforma deve implementar esta interface usando suas próprias
 * bibliotecas de Google Sign-In.
 *
 * Exemplo Android:
 * ```kotlin
 * class AndroidGoogleLoginHandler : GoogleLoginHandler {
 *     override suspend fun signIn(
 *         onSuccess: (GoogleSignInResult) -> Unit,
 *         onError: (String) -> Unit
 *     ) {
 *         // Usar Google Identity Services
 *         val googleIdOption = GetGoogleIdOption.Builder()
 *             .setServerClientId(WEB_CLIENT_ID)
 *             .build()
 *         // ... implementação
 *     }
 * }
 * ```
 *
 * Exemplo iOS:
 * ```kotlin
 * class IosGoogleLoginHandler : GoogleLoginHandler {
 *     override suspend fun signIn(
 *         onSuccess: (GoogleSignInResult) -> Unit,
 *         onError: (String) -> Unit
 *     ) {
 *         // Usar GoogleSignInBridge
 *         // ... implementação
 *     }
 * }
 * ```
 */
interface GoogleLoginHandler {
    /**
     * Inicia o fluxo de autenticação com Google
     *
     * @param onSuccess Chamado com idToken e accessToken quando bem-sucedido
     * @param onError Chamado com mensagem de erro em caso de falha
     */
    suspend fun signIn(
        onSuccess: (GoogleSignInResult) -> Unit,
        onError: (String) -> Unit
    )
}

/**
 * Interface para implementar Social Login com Apple
 *
 * Cada plataforma deve implementar esta interface usando suas próprias
 * bibliotecas de Apple Sign-In.
 *
 * Exemplo iOS:
 * ```kotlin
 * class IosAppleLoginHandler : AppleLoginHandler {
 *     override suspend fun signIn(
 *         onSuccess: (AppleSignInResult) -> Unit,
 *         onError: (String) -> Unit
 *     ) {
 *         // Usar AuthenticationServices
 *         // ... implementação
 *     }
 * }
 * ```
 *
 * Exemplo Android (via WebView ou biblioteca de terceiros):
 * ```kotlin
 * class AndroidAppleLoginHandler : AppleLoginHandler {
 *     override suspend fun signIn(
 *         onSuccess: (AppleSignInResult) -> Unit,
 *         onError: (String) -> Unit
 *     ) {
 *         // Implementação via WebView ou biblioteca
 *         // ... implementação
 *     }
 * }
 * ```
 */
interface AppleLoginHandler {
    /**
     * Inicia o fluxo de autenticação com Apple
     *
     * @param onSuccess Chamado com idToken e nonce quando bem-sucedido
     * @param onError Chamado com mensagem de erro em caso de falha
     */
    suspend fun signIn(
        onSuccess: (AppleSignInResult) -> Unit,
        onError: (String) -> Unit
    )
}

/**
 * Configuração de handlers de Social Login
 *
 * Use esta classe para configurar os handlers de Google e Apple
 * específicos da sua plataforma.
 *
 * Exemplo de uso:
 * ```kotlin
 * val socialLoginConfig = SocialLoginConfig(
 *     googleHandler = AndroidGoogleLoginHandler(context),
 *     appleHandler = AndroidAppleLoginHandler(context)
 * )
 * ```
 */
data class SocialLoginConfig(
    val googleHandler: GoogleLoginHandler? = null,
    val appleHandler: AppleLoginHandler? = null
)
