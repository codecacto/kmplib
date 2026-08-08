package br.com.codecacto.kmplib.auth.social

/**
 * **Sign in with Apple não é oferecido no Android — decisão consciente, não lacuna esquecida.**
 *
 * A Apple não publica SDK Android; a única implementação possível seria o fluxo web (Custom Tabs +
 * Services ID + domínio verificado + deep link de retorno), que acrescenta uma superfície de ataque
 * própria (interceptação do redirect por outro app registrado no mesmo esquema) e um segundo
 * conjunto de credenciais a manter, para atender um caso que nenhum app do portfólio tem. O padrão
 * de mercado é não exibir o botão no Android.
 *
 * A falha é **explícita e legível** — nunca um resultado vazio que a tela interprete como
 * "cancelado". O app deve esconder o botão da Apple no Android.
 */
actual class AppleAuthProvider actual constructor() {

    actual suspend fun signIn(): AppleSignInResult = unavailable()

    actual suspend fun signIn(nonce: String): AppleSignInResult = unavailable()

    private fun unavailable() = AppleSignInResult(
        idToken = null,
        nonce = null,
        error = "Login com Apple não disponível no Android. Use o login com Google."
    )
}
