package br.com.codecacto.kmplib.auth.social

/**
 * Fluxo nativo de login com Google, **sem nenhuma dependência de Firebase** — o resultado
 * ([GoogleSignInResult]) serve tanto o `AuthRepository` (Firebase) quanto o own-auth
 * (`POST /auth/social`).
 *
 * - **Android:** Credential Manager + `GetGoogleIdOption` (a API oficial atual; a `GoogleSignIn`
 *   legada está deprecada pelo Google). Requer `KmpLib.setActivity(this)` no `onResume()`.
 * - **iOS:** `GoogleSignIn-iOS` via SPM, alimentado pelo Swift através do
 *   [GoogleSignInBridge][br.com.codecacto.kmplib.auth.social.GoogleSignInBridge] (ver o KDoc do
 *   bridge para o passo a passo).
 *
 * @param webClientId o **client ID do tipo Web** do projeto no Google Cloud. É ele que vai na claim
 *   `aud` do `idToken` quando se pede `serverClientId`/`serverClientID` — ou seja, é o identificador
 *   que o **backend** confere. Não use o client ID do Android/iOS aqui.
 */
expect class GoogleAuthProvider(webClientId: String) {

    /**
     * Abre o seletor de contas do Google **sem nonce**.
     *
     * Use apenas com Firebase Auth (que faz a própria proteção do fluxo). Para **own-auth**, prefira
     * [signIn] com o nonce emitido pelo servidor — sem ele o `idToken` não tem como ser amarrado à
     * requisição que o resgata, e o backend recusa.
     */
    suspend fun signIn(): GoogleSignInResult

    /**
     * Abre o seletor de contas do Google amarrando o `idToken` ao [nonce].
     *
     * O valor é embutido pelo Google na claim `nonce` do JWT (Android:
     * `GetGoogleIdOption.setNonce`; iOS: parâmetro `nonce` do `GIDSignIn`). **O nonce tem de vir do
     * servidor** (`GET /auth/social/nonce`), nunca ser gerado no aparelho: um nonce escolhido pelo
     * cliente não prova nada — quem quiser replicar um `idToken` roubado escolhe o mesmo valor.
     */
    suspend fun signIn(nonce: String): GoogleSignInResult
}
