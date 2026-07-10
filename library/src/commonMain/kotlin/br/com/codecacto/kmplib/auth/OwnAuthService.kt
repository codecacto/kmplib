package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.firebase.auth.User

/**
 * Operações own-auth que ficam **fora** do [IAuthRepository][br.com.codecacto.kmplib.firebase.auth.IAuthRepository]
 * (registro com aceite de termos + fluxo de definição/redefinição de senha do convite).
 *
 * O `IAuthRepository` da lib nasceu para o Firebase (cujo `signUpWithEmail` não tem `acceptedTerms`,
 * e cujo reset é por e-mail de sistema). Para não distorcer aquele contrato — e manter a
 * retrocompatibilidade com os apps Firebase — o registro rico e o reset por token vivem aqui, num
 * serviço próprio. A implementação ([EmailPasswordAuthRepository]) satisfaz os dois.
 */
interface OwnAuthService {

    /**
     * Cria a conta (own-auth) e **já deixa o usuário logado** (persiste a sessão devolvida). O
     * `acceptedTerms` é exigência legal — passe o valor real coletado na tela de registro.
     */
    suspend fun register(
        name: String,
        email: String,
        password: String,
        acceptedTerms: Boolean,
    ): Result<User>

    /**
     * Dispara o e-mail de definição/redefinição de senha (`password/forgot`). SEMPRE resolve como
     * sucesso genérico do lado do servidor (não revela se o e-mail existe). Serve tanto o "esqueci a
     * senha" quanto o **convite** de conta criada pelo dono.
     */
    suspend fun requestPasswordReset(email: String): Result<Unit>

    /**
     * Confirma a nova senha a partir do `token` recebido por e-mail (`password/reset`, uso único).
     * Não faz login automático — o app leva o usuário à tela de login em seguida.
     */
    suspend fun confirmPasswordReset(token: String, newPassword: String): Result<Unit>
}
