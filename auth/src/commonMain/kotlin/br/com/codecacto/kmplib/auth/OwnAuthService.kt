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
     * [register] com o **idioma da conta** (2.219.0, par da backlib 0.134.0): BCP 47 — `pt-BR`,
     * `en`, `es`, `pt-PT`. É por ele que o servidor escreve e-mail, PDF e push no idioma da pessoa.
     *
     * ```kotlin
     * service.register(nome, email, senha, aceitou, telefone, locale = appLanguageTag())
     * ```
     *
     * Tem implementação default (ignora o idioma e chama o [register] de sempre) para não quebrar
     * quem implementa esta porta — dublês de teste, por exemplo. A implementação da lib manda o campo.
     */
    suspend fun register(
        name: String,
        email: String,
        password: String,
        acceptedTerms: Boolean,
        phone: String?,
        locale: String?,
    ): Result<User> = register(name, email, password, acceptedTerms, phone)

    /**
     * [register] com o **idioma da tela do cadastro** (2.376.0) — o campo `idioma` do corpo, que liga
     * no backend a regra "WhatsApp obrigatório no cadastro em pt-BR" (constituição, 16/nov/2026): com
     * idioma do Brasil e sem número, o servidor responde 400 `WHATSAPP_OBRIGATORIO` com
     * `details.telefone`. `null` não manda o campo (corpo idêntico ao de antes).
     *
     * Os erros saem como [AuthException][br.com.codecacto.kmplib.firebase.auth.AuthException], como nos
     * outros `register` (a recusa de regra vira `UnknownError` com a frase do servidor). Para o erro
     * **no campo**, use [registerWithPassword].
     *
     * Implementação default ignora o idioma, para não quebrar quem implementa esta porta.
     */
    suspend fun register(
        name: String,
        email: String,
        password: String,
        acceptedTerms: Boolean,
        phone: String?,
        locale: String?,
        idioma: String?,
    ): Result<User> = register(name, email, password, acceptedTerms, phone, locale)

    /**
     * **Cadastro com o erro TIPADO** (2.376.0) — o mesmo [register] (adota a sessão do mesmo jeito),
     * mas a falha chega como a [OwnAuthException] do [OwnAuthApi], em vez da `AuthException` canônica
     * (constante, sem campo). É o caminho para a tela marcar o WhatsApp:
     *
     * ```kotlin
     * service.registerWithPassword(nome, email, senha, aceitou, telefone, idioma = appLanguageTag())
     *     .onFailure { e ->
     *         val r = e as? OwnAuthException.RegisterRejected
     *         if (r?.serverCode == OwnAuthErrorCodes.WHATSAPP_OBRIGATORIO) erroNoCampo = r.fieldError("telefone")
     *     }
     * ```
     *
     * ### Erros
     * - [OwnAuthException.RegisterRejected] — 400/422 com `code` próprio ou `details` (por campo).
     * - [OwnAuthException.WeakPassword] — 400/422 genérico (a senha curta da backlib).
     * - [OwnAuthException.EmailAlreadyInUse], [OwnAuthException.TooManyRequests],
     *   [OwnAuthException.Network], [OwnAuthException.Server].
     *
     * Implementação default cai no [register] com idioma (erros como `AuthException`), para não
     * quebrar quem implementa esta porta — dublês de teste, por exemplo.
     */
    suspend fun registerWithPassword(
        name: String,
        email: String,
        password: String,
        acceptedTerms: Boolean,
        phone: String? = null,
        locale: String? = null,
        idioma: String? = null,
    ): Result<User> = register(name, email, password, acceptedTerms, phone, locale, idioma)

    /**
     * Dispara o e-mail de definição/redefinição de senha (`password/forgot`). SEMPRE resolve como
     * sucesso genérico do lado do servidor (não revela se o e-mail existe). Serve tanto o "esqueci a
     * senha" quanto o **convite** de conta criada pelo dono.
     */
    suspend fun requestPasswordReset(email: String): Result<Unit>

    /**
     * **Login por usuário ou e-mail + senha com o erro TIPADO** (2.366.0) — o mesmo login do
     * `IAuthRepository.signInWithEmail` (adota a sessão do mesmo jeito), mas a falha chega como a
     * [OwnAuthException] do [OwnAuthApi], no idioma da tela, em vez da `AuthException` canônica
     * (constante, pt-BR, sem detalhe). É o caminho para a tela de login mostrar o PRAZO do bloqueio:
     *
     * ```kotlin
     * service.signInWithPassword(identificador, senha).onFailure { e ->
     *     val segundos = (e as? OwnAuthException.TooManyRequests)?.retryAfterSeconds
     *     // segundos != null → "Tente de novo em N minutos" (e.message já diz isso)
     * }
     * ```
     *
     * ### Erros
     * - [OwnAuthException.InvalidCredentials] — usuário/e-mail ou senha não conferem.
     * - [OwnAuthException.TooManyRequests] — bloqueio por excesso de tentativas;
     *   `retryAfterSeconds` = o `Retry-After` do servidor (segundos ou data HTTP), `null` se não veio.
     * - [OwnAuthException.Network], [OwnAuthException.Server].
     *
     * Default: falha com [OwnAuthException.Unsupported] — mantém compilando quem implementa esta
     * porta fora da lib.
     */
    suspend fun signInWithPassword(identifier: String, password: String): Result<User> =
        Result.failure(OwnAuthException.Unsupported("signInWithPassword não implementado."))

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

    /**
     * **Editar o próprio perfil** (Configurações → "Meus dados"), contra o `PATCH
     * {authBasePath}/me` da backlib-auth-local ≥ 0.140.0 (2.229.0).
     *
     * Parcial: parâmetro `null` não vai no corpo e o servidor o deixa como está. O [username] só é
     * aceito se o backend ligou `usernameEditable` — sem isso vem 403 `USERNAME_CHANGE_DISABLED`,
     * nunca ignorado em silêncio. Com sucesso, a sessão guardada recebe o perfil **que o servidor
     * gravou** (nome já aparado, usuário já normalizado) e o `currentUser` emite o [User] novo — a
     * tela que mostra o nome no topo se atualiza sozinha.
     *
     * ```kotlin
     * service.updateOwnProfile(name = nome)
     *     .onSuccess { user -> /* fechar a edição */ }
     *     .onFailure { e ->
     *         val campo = (e as? OwnAuthException.ProfileRejected)?.fieldError("name")
     *         // campo != null → erro NO campo "Nome"; senão banner junto do botão (e.message)
     *     }
     * ```
     *
     * ### Erros
     * - [OwnAuthException.ProfileRejected] — `fieldError("name")`/`fieldError("username")` com a
     *   frase do servidor; `serverCode` em [OwnAuthErrorCodes]: `NOTHING_TO_UPDATE` (nada informado),
     *   `PASSWORD_CHANGE_REQUIRED` (senha temporária — conclua [completeFirstAccess] antes),
     *   `USERNAME_CHANGE_DISABLED`, `USERNAME_TAKEN`.
     * - [OwnAuthException.Unsupported] — o backend não publica a rota (404).
     * - [OwnAuthException.NotAuthenticated], [OwnAuthException.TooManyRequests],
     *   [OwnAuthException.Network] — banner junto do botão.
     *
     * Chamar com os dois nulos falha **localmente** com `ProfileRejected(NOTHING_TO_UPDATE)`, sem
     * ida ao servidor. Default: falha com [OwnAuthException.Unsupported] — mantém compilando quem
     * implementa esta porta fora da lib.
     */
    suspend fun updateOwnProfile(name: String? = null, username: String? = null): Result<User> =
        Result.failure(OwnAuthException.Unsupported("updateOwnProfile não implementado."))

    /**
     * Relê o perfil no servidor (`GET {authBasePath}/me`) e atualiza o `currentUser` (2.229.0) —
     * para a tela de perfil abrir com o nome e o **nome de usuário** atuais mesmo que tenham mudado
     * noutro aparelho ou no portal. A implementação da lib já faz esta leitura sozinha depois de
     * cada login (sem bloquear o login se ela falhar); chamar aqui é para o `ON_RESUME` da tela.
     *
     * Default: falha com [OwnAuthException.Unsupported].
     */
    suspend fun refreshOwnProfile(): Result<User> =
        Result.failure(OwnAuthException.Unsupported("refreshOwnProfile não implementado."))
}

/** Desfecho de uma troca de senha aceita pelo servidor — ver [OwnAuthService.changeOwnPassword]. */
sealed interface PasswordChangeOutcome {

    /** Senha trocada **e** sessão renovada com ela: a pessoa continua dentro do app. */
    data class SessionRenewed(val user: User) : PasswordChangeOutcome

    /**
     * Senha trocada, mas não foi possível entrar de novo sozinho — a sessão local foi encerrada.
     * A tela avisa que a senha foi alterada e leva ao login (o `currentUser` já emitiu `null`).
     *
     * Vem também (2.218.0) quando o `password/change` falhou por **rede/5xx** e não deu para
     * confirmar se o servidor aplicou a troca: a sessão pode estar revogada, então é encerrada. Aí a
     * senha **pode** ter mudado — a tela diz para entrar de novo, com a senha nova e, se não
     * funcionar, com a antiga.
     */
    data object SignInRequired : PasswordChangeOutcome
}
