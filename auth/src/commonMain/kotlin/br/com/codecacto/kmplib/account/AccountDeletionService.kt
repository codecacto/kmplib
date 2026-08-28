package br.com.codecacto.kmplib.account

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.firebase.auth.AuthException
import br.com.codecacto.kmplib.firebase.auth.IAuthRepository
import br.com.codecacto.kmplib.sync.rest.DomainApiClient
import br.com.codecacto.kmplib.sync.rest.DomainResult

/**
 * Serviço de **direito ao esquecimento + portabilidade (LGPD/GDPR)** — exclusão de todos os dados
 * do usuário e da conta de autenticação, e exportação dos dados.
 *
 * Padrão canônico da Onda 3 (migração Firestore → backend central): o wipe é **atômico
 * server-side** via `DELETE {dataPath}` (o backend deriva o `tenant = uid` do Firebase ID token e
 * apaga tudo — entidades + blobs — numa transação). O cliente **não** apaga documento por documento
 * nem depende de Rules. Promovido para a kmplib por ser praticamente idêntico em ≥3 apps
 * (MinhaAgenda / MinhaOS / QuemMeDeve / Meu Plantão / MinhasHoras) — só mudavam os textos de log.
 *
 * **Ordem inegociável (segurança):**
 * 1. (**autenticado**) `DELETE {dataPath}` — apaga os dados; exige o Bearer válido, por isso vem antes.
 * 2. por **ÚLTIMO**, a **conta** de autenticação via [IAuthRepository.deleteAccount] (Firebase Auth).
 *
 * Se o wipe de **dados** falhar (rede/servidor), **aborta sem excluir a conta** — o usuário segue
 * logado e pode repetir. Se o wipe teve êxito mas `deleteAccount()` exigir re-login recente, os dados
 * pessoais já foram removidos (LGPD satisfeita) → [AccountDeletionResult.DataWipedAccountPending]
 * (resíduo benigno: só o registro de login vazio permanece).
 *
 * @param api cliente do backend de domínio `/v1` (Bearer = Firebase ID token).
 * @param auth repositório de autenticação (para a exclusão da conta por último).
 * @param dataPath rota do wipe atômico. Default `"/v1/me/data"`.
 * @param exportPath rota de exportação. Default `"/v1/me/export"`.
 * @param texts mensagens amigáveis (i18n; defaults pt-BR).
 * @param credencialSaiNoWipe **`true` em projeto own-auth** — ver o KDoc do parâmetro.
 */
class AccountDeletionService(
    private val api: DomainApiClient,
    private val auth: IAuthRepository,
    private val dataPath: String = DEFAULT_DATA_PATH,
    private val exportPath: String = DEFAULT_EXPORT_PATH,
    private val texts: AccountDeletionTexts = AccountDeletionTexts(),
    /**
     * **A credencial de login já sai no próprio wipe?** `true` em projeto **own-auth**
     * (`backlib-auth-local`), onde a senha mora na MESMA base que o `DELETE {dataPath}` apaga —
     * então não há segundo passo a dar: o serviço só encerra a sessão local e devolve
     * [AccountDeletionResult.Completed].
     *
     * Existe porque o default (`false`, Firebase) **mente em own-auth**: o
     * [IAuthRepository.deleteAccount] do `EmailPasswordAuthRepository` responde
     * `UnsupportedOperation` — de propósito, já que não há IdP externo a chamar —, e o serviço
     * traduzia essa recusa em [AccountDeletionResult.DataWipedAccountPending], fazendo o app dizer
     * *"entre novamente para remover o login"* de um login que **já não existe**. A pessoa
     * apagava a conta e saía achando que sobrou alguma coisa.
     */
    private val credencialSaiNoWipe: Boolean = false,
) {

    /**
     * Exclui **todos os dados** do usuário (wipe server-side) e, por último, a **conta** de
     * autenticação. Nunca lança — devolve [Result].
     */
    suspend fun deleteAccountAndData(): Result<AccountDeletionResult> {
        auth.currentUserSync?.id ?: return Result.failure(AuthException.NotAuthenticated)

        // 1. Wipe atômico server-side (entidades + blobs), ainda autenticado.
        when (val r = api.delete(dataPath)) {
            is DomainResult.Success -> Unit
            is DomainResult.Quota -> {
                AppLogger.e(TAG, "Resposta inesperada (quota) no wipe LGPD; conta preservada")
                return Result.failure(IllegalStateException(texts.deleteFailed))
            }
            is DomainResult.Error -> {
                AppLogger.e(TAG, "Falha no wipe server-side LGPD (conta preservada): ${r.message}")
                return Result.failure(IllegalStateException(r.message.ifBlank { texts.deleteFailed }))
            }
        }

        // 2. Conta de autenticação por ÚLTIMO — dados pessoais já removidos neste ponto.
        //    Em own-auth a credencial saiu junto no passo 1: resta encerrar a sessão local, e o
        //    resultado é COMPLETO (nada ficou pendente).
        if (credencialSaiNoWipe) {
            auth.signOut()
            return Result.success(AccountDeletionResult.Completed)
        }
        auth.deleteAccount().onFailure {
            if (it is AuthException.RequiresRecentLogin) {
                AppLogger.w(TAG, "Dados removidos; exclusão da conta exige re-login recente (resíduo benigno)")
                return Result.success(AccountDeletionResult.DataWipedAccountPending)
            }
            AppLogger.w(TAG, "Dados removidos; exclusão da conta falhou (resíduo benigno: login vazio)", it)
            return Result.success(AccountDeletionResult.DataWipedAccountPending)
        }

        return Result.success(AccountDeletionResult.Completed)
    }

    /**
     * Exporta **todos os dados do usuário** (LGPD/portabilidade) via `GET {exportPath}`. Retorna o
     * corpo bruto (JSON) para a camada de UI compartilhar/salvar. Nunca lança.
     */
    suspend fun exportData(): Result<String> {
        auth.currentUserSync?.id ?: return Result.failure(AuthException.NotAuthenticated)

        return when (val r = api.getJson(exportPath)) {
            is DomainResult.Success -> Result.success(r.data)
            is DomainResult.Quota -> {
                AppLogger.e(TAG, "Resposta inesperada (quota) na exportação LGPD")
                Result.failure(IllegalStateException(texts.exportFailed))
            }
            is DomainResult.Error -> {
                AppLogger.e(TAG, "Falha na exportação de dados LGPD: ${r.message}")
                Result.failure(IllegalStateException(r.message.ifBlank { texts.exportFailed }))
            }
        }
    }

    companion object {
        private const val TAG = "AccountDeletion"
        const val DEFAULT_DATA_PATH = "/v1/me/data"
        const val DEFAULT_EXPORT_PATH = "/v1/me/export"
    }
}

/**
 * Resultado (sucesso) da exclusão LGPD. Distingue exclusão total de exclusão com resíduo benigno
 * (login vazio pendente de re-login), para a UI dar a mensagem correta ao usuário.
 */
enum class AccountDeletionResult {
    /** Dados E conta de autenticação removidos. */
    Completed,

    /**
     * **Dados removidos** (LGPD satisfeita), mas a exclusão da conta exigiu re-login recente.
     * Resta apenas um registro de auth vazio; o usuário o elimina entrando de novo e repetindo.
     */
    DataWipedAccountPending,
}

/** Mensagens amigáveis do serviço (i18n; defaults pt-BR). */
data class AccountDeletionTexts(
    val deleteFailed: String = "Não foi possível excluir seus dados. Tente novamente.",
    val exportFailed: String = "Não foi possível exportar seus dados. Tente novamente.",
)
