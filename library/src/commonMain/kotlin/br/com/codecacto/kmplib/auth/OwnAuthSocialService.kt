package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.firebase.auth.User

/**
 * Login social do **own-auth** (Google/Apple contra o nosso IdP, sem Firebase).
 *
 * É uma interface **nova e separada**, não um método a mais no
 * [IAuthRepository][br.com.codecacto.kmplib.firebase.auth.IAuthRepository] nem no [OwnAuthService]:
 * acrescentar método abstrato a interface pública quebraria toda fake que os apps mantêm em
 * `commonTest`. Quem precisa do fluxo social pede este tipo no Koin
 * (`single<OwnAuthSocialService> { get<OwnAuth>().social }`).
 *
 * ### O fluxo completo, na ordem (a ordem importa)
 * ```kotlin
 * // 1) O NONCE VEM DO SERVIDOR. Sempre. Nunca gerado no aparelho.
 * val nonce = social.socialNonce().getOrElse { return@launch mostrarErro(it) }.nonce
 *
 * // 2) O provedor nativo embute o nonce no idToken.
 * val r = GoogleAuthProvider(webClientId).signIn(nonce)      // ou AppleAuthProvider().signIn(nonce)
 * if (!r.isSuccess) return@launch mostrarErro(r.error)
 *
 * // 3) O backend verifica assinatura, `aud`, `azp`, `exp` e o nonce, e devolve os NOSSOS tokens.
 * social.signInWithSocial(SocialProvider.GOOGLE, r.idToken!!, nonce, r.displayName, r.email)
 * ```
 *
 * ### Por que o nonce não pode nascer no device
 * O nonce serve para amarrar *aquele* `idToken` a *aquela* tentativa de login. Se quem escolhe o
 * valor é o próprio cliente, ele não amarra nada: um `idToken` vazado (log, proxy, outro app no
 * mesmo aparelho) é reapresentado com o mesmo nonce e passa. Só o emissor que também verifica pode
 * afirmar "este token foi emitido para esta sessão, e uma vez só".
 */
interface OwnAuthSocialService {

    /**
     * `GET {authBasePath}/social/nonce` — obtém o nonce de uso único do servidor.
     *
     * O valor também fica **guardado internamente** como "nonce em voo", para que o
     * `IAuthRepository.signInWithGoogle(idToken, accessToken)` (cuja assinatura, herdada do
     * Firebase, não tem campo de nonce) consiga completar a troca. É consumido no primeiro
     * [signInWithSocial] bem-sucedido.
     */
    suspend fun socialNonce(): Result<SocialNonce>

    /**
     * `POST {authBasePath}/social` — troca o `idToken` do provedor pelos tokens próprios e **adota a
     * sessão** (o usuário sai logado daqui).
     *
     * @param idToken **somente** o ID token do provedor. Um access token do Google não é prova de
     *   identidade e o backend o recusa.
     * @param nonce o valor **cru** devolvido por [socialNonce] (o mesmo passado ao provider nativo).
     * @param name/@param email só surtem efeito na **criação** da identidade; o servidor os ignora
     *   em login subsequente. Passe o que o provedor devolveu (a Apple só manda na 1ª autorização).
     */
    suspend fun signInWithSocial(
        provider: SocialProvider,
        idToken: String,
        nonce: String,
        name: String? = null,
        email: String? = null,
    ): Result<User>

    /**
     * `POST {authBasePath}/social/exchange` — o par do [signInWithSocial] no fluxo **conduzido pelo
     * backend**: troca o código que voltou no *deep link* pela sessão, e **adota** a sessão (o
     * usuário sai logado daqui).
     *
     * Sem ele havia uma assimetria calada entre os dois modos: no nativo a lib adotava a sessão, no
     * fluxo pelo navegador o app recebia tokens crus e tinha de adotá-los na mão — e um app que
     * esquecesse esse passo terminava o login com o usuário ainda deslogado, sem erro nenhum.
     *
     * @param codeVerifier o par do desafio enviado em `socialStartUrl`. Sem ele o backend recusa: é
     *   o que impede que outro aplicativo capaz de reivindicar o mesmo esquema de URL troque um
     *   código interceptado pela sessão.
     *
     * **Implementação default recusa**, de propósito: a interface é pública e os apps mantêm fakes
     * dela em `commonTest` — método abstrato novo quebraria todos de uma vez. Quem usa o fluxo pelo
     * backend recebe a implementação real (a da lib), não este default.
     */
    suspend fun signInWithSocialCode(code: String, codeVerifier: String): Result<User> =
        Result.failure(
            IllegalStateException(
                "Este OwnAuthSocialService não implementa a troca do código do login social " +
                    "pelo backend (POST .../social/exchange)."
            )
        )
}
