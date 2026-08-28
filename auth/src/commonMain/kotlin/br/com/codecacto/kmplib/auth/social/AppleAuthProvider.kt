package br.com.codecacto.kmplib.auth.social

/**
 * Fluxo nativo de Sign in with Apple, **sem nenhuma dependência de Firebase** — o resultado
 * ([AppleSignInResult]) serve tanto o `AuthRepository` (Firebase) quanto o own-auth
 * (`POST /auth/social`).
 *
 * - **iOS:** `ASAuthorizationController` (AuthenticationServices), com o nonce enviado à Apple já
 *   em **SHA-256 hex** e devolvido cru em [AppleSignInResult.nonce].
 * - **Android: NÃO EXISTE, por decisão.** A Apple não publica SDK Android para Sign in with Apple; a
 *   única forma seria o fluxo web (Custom Tabs + Services ID + domínio verificado + deep link de
 *   retorno), com uma superfície de ataque própria (interceptação do redirect) para atender um caso
 *   que nenhum app do portfólio tem. O padrão de mercado é **não oferecer o botão no Android**, e é
 *   o que fazemos: [signIn] devolve um erro explícito, nunca um resultado vazio silencioso. O app
 *   esconde o botão no Android.
 */
expect class AppleAuthProvider() {

    /**
     * Abre o Sign in with Apple gerando o nonce **no aparelho**.
     *
     * Use apenas com Firebase Auth (que compara o nonce cru contra o hash da claim). Para
     * **own-auth**, use [signIn] com o nonce do servidor.
     */
    suspend fun signIn(): AppleSignInResult

    /**
     * Abre o Sign in with Apple usando o [nonce] **cru emitido pelo servidor**
     * (`GET /auth/social/nonce`).
     *
     * A lib envia à Apple o `SHA-256` hex deste valor (é o que a Apple grava na claim `nonce` do
     * `identityToken`) e devolve o valor **cru** em [AppleSignInResult.nonce], que é o que o backend
     * precisa para refazer o hash e conferir. Nonce gerado no device não prova nada: quem replicar
     * um token roubado escolhe o mesmo valor.
     */
    suspend fun signIn(nonce: String): AppleSignInResult
}
