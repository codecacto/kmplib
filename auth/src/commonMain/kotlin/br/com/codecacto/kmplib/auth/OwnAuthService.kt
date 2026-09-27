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
        /**
         * Telefone/WhatsApp, quando a tela pediu — a `RegisterScreen` já traz o campo por default
         * (`RegisterFields.showPhoneField`). Vai **como a pessoa digitou**, com máscara.
         *
         * Default `null` para não quebrar quem implementa esta porta: até a 2.122.0 o número era
         * coletado na tela e descartado no `ViewModel`, porque o parâmetro não existia.
         */
        phone: String? = null,
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

    /**
     * **Troca voluntária de senha** (Configurações → "Alterar senha"), contra o `POST
     * {authBasePath}/password/change` da backlib-auth-local (2.216.0).
     *
     * ### A sessão continua de pé — e é a lib que garante isso
     * O servidor derruba **todas** as sessões da conta ao trocar a senha, inclusive a de quem
     * trocou (é o ponto da troca: um aparelho esquecido logado perde o acesso). Sem cuidado, a
     * pessoa salva a senha nova e é jogada no login no refresh seguinte — o que lê como falha. Por
     * isso, com a troca aceita, a implementação **entra de novo com a senha nova** e adota a sessão
     * fresca: [PasswordChangeOutcome.SessionRenewed]. Quando não dá (conta sem identificador de
     * login, rede caiu entre as duas chamadas), a sessão local é encerrada e o resultado é
     * [PasswordChangeOutcome.SignInRequired] — a senha **foi** trocada, e a tela deve dizer isso
     * antes de levar ao login.
     *
     * Conta ainda com a **senha temporária** (primeiro acesso) é desviada para [completeFirstAccess]
     * — o servidor não aceita `password/change` para ela.
     *
     * ### Erros (tipados, com a frase do servidor)
     * - [OwnAuthException.InvalidCredentials] — a senha **atual** não confere → marque o campo
     *   "Senha atual".
     * - [OwnAuthException.WeakPassword] — a senha **nova** foi recusada (curta demais, igual à
     *   atual); a `message` é o motivo real → marque o campo "Nova senha".
     * - [OwnAuthException.TooManyRequests], [OwnAuthException.Network],
     *   [OwnAuthException.NotAuthenticated] — banner junto do botão.
     *
     * Default: falha com [OwnAuthException.Unsupported] — mantém compilando quem implementa esta
     * porta fora da lib.
     */
    suspend fun changeOwnPassword(currentPassword: String, newPassword: String): Result<PasswordChangeOutcome> =
        Result.failure(OwnAuthException.Unsupported("changeOwnPassword não implementado."))

    /**
     * **Primeiro acesso**: troca a senha temporária (posta pelo administrador) pela do titular,
     * contra o `POST {authBasePath}/password/first-access` (2.216.0).
     *
     * Faz as três coisas que o app fazia à mão com o [OwnAuthApi.firstAccessPasswordChange]: pega um
     * access token válido (renovando se a pessoa ficou parada no diálogo além dos 15 minutos), chama
     * a rota e **adota os tokens novos** preservando nome, identificador e origem do login da sessão
     * — a troca revoga as sessões, e sem adotar o par novo a pessoa é deslogada no toque seguinte.
     * Ao fim, `session.passwordChangeRequired` vira `false` e o `ForcePasswordChangeDialog` fecha.
     *
     * Erros: [OwnAuthException.WeakPassword] com o motivo do servidor, e os de rede/sessão.
     */
    suspend fun completeFirstAccess(newPassword: String): Result<User> =
        Result.failure(OwnAuthException.Unsupported("completeFirstAccess não implementado."))
}

/** Desfecho de uma troca de senha aceita pelo servidor — ver [OwnAuthService.changeOwnPassword]. */
sealed interface PasswordChangeOutcome {

    /** Senha trocada **e** sessão renovada com ela: a pessoa continua dentro do app. */
    data class SessionRenewed(val user: User) : PasswordChangeOutcome

    /**
     * Senha trocada, mas não foi possível entrar de novo sozinho — a sessão local foi encerrada.
     * A tela avisa que a senha foi alterada e leva ao login (o `currentUser` já emitiu `null`).
     */
    data object SignInRequired : PasswordChangeOutcome
}
