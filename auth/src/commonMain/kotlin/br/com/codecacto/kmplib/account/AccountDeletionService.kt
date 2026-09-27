package br.com.codecacto.kmplib.account

import br.com.codecacto.kmplib.core.storage.AccountLocalDataPurger
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.firebase.auth.AuthException
import br.com.codecacto.kmplib.firebase.auth.IAuthRepository
import br.com.codecacto.kmplib.platform.getShareHandler
import br.com.codecacto.kmplib.sync.rest.DomainApiClient
import br.com.codecacto.kmplib.sync.rest.DomainResult
import br.com.codecacto.kmplib.ui.components.clearPrivatePhotoMemoryCache

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
 * @param localData limpeza do que a conta deixou **no aparelho** — ver o KDoc do parâmetro.
 * @param clearSharedFiles apaga as cópias de compartilhamento — ver o KDoc do parâmetro.
 *
 * ### 3. O aparelho (2.217.0)
 * Dado o wipe no servidor, a conta ainda existia **no aparelho**: espelho do sync, outbox, fotos da
 * fila de upload e as cópias exportadas para compartilhar. Agora, depois do passo 2 — **qualquer**
 * que seja o resultado dele, porque os dados do servidor já saíram —, o serviço apaga as cópias de
 * compartilhamento e chama [AccountLocalDataPurger.purgeAccount]. Falha local **não** muda o
 * resultado (a exclusão no servidor aconteceu e não se desfaz); fica no log.
 *
 * ### Conta nomeada e motor segurado (2.218.0)
 * - A conta a limpar é capturada ([AccountLocalDataPurger.activeAccountId]) **antes** do wipe e
 *   passada explicitamente. Até a 2.217.0 a limpeza lia o escopo do espelho **depois** do
 *   `signOut` — e o app que liga o escopo à sessão já o tinha trocado para "sem conta": apagava o
 *   bucket errado e reportava `failures = 0`.
 * - Tudo, do `DELETE` no servidor ao fim da limpeza local, roda dentro de
 *   [AccountLocalDataPurger.withSyncPaused]. Sem isso, um ciclo de sync entre o wipe e a limpeza
 *   subia a outbox e **recriava no servidor** dado da conta recém-apagada (o access token ainda
 *   vale). Wipe que falha solta o motor sem ter apagado nada.
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
    /**
     * **Quem apaga o que a conta deixou no aparelho** (2.217.0). Em app com sync, passe o
     * `SyncAccountDataPurger` (kmplib-sync) com as filas de upload: sem ele o espelho, a outbox e as
     * fotos pendentes da conta excluída **ficam no disco**. `null` = app sem dado local da conta.
     *
     * A conta é capturada antes do wipe (não importa se o app troca o escopo no `signOut`); sem
     * titular declarado no espelho, a limpeza local é **recusada** e registrada como erro — o wipe
     * no servidor segue normalmente.
     */
    private val localData: AccountLocalDataPurger? = null,
    /**
     * Apaga, depois do wipe, **todas** as cópias que o `ShareHandler` materializou para compartilhar
     * (PDF/imagem exportados da conta). Default `true`.
     */
    private val clearSharedFiles: Boolean = true,
) {

    /**
     * Exclui **todos os dados** do usuário (wipe server-side) e, por último, a **conta** de
     * autenticação. Nunca lança — devolve [Result].
     */
    suspend fun deleteAccountAndData(): Result<AccountDeletionResult> {
        auth.currentUserSync?.id ?: return Result.failure(AuthException.NotAuthenticated)

        val purger = localData ?: return excluir(contaLocal = null)
        // Capturada ANTES do wipe e do signOut: depois deles o app pode já ter trocado o escopo.
        val conta = purger.activeAccountId()
        // O motor fica segurado do DELETE até o fim da limpeza: nenhum push recria no servidor o
        // que acabou de ser apagado. Falha no wipe devolve dentro do bloco — nada local é tocado.
        return purger.withSyncPaused { excluir(contaLocal = conta) }
    }

    private suspend fun excluir(contaLocal: String?): Result<AccountDeletionResult> {
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
        val resultado = if (credencialSaiNoWipe) {
            auth.signOut()
            AccountDeletionResult.Completed
        } else {
            val exclusao = auth.deleteAccount()
            val falha = exclusao.exceptionOrNull()
            when {
                falha == null -> AccountDeletionResult.Completed
                falha is AuthException.RequiresRecentLogin -> {
                    AppLogger.w(TAG, "Dados removidos; exclusão da conta exige re-login recente (resíduo benigno)")
                    AccountDeletionResult.DataWipedAccountPending
                }
                else -> {
                    AppLogger.w(TAG, "Dados removidos; exclusão da conta falhou (resíduo benigno: login vazio)", falha)
                    AccountDeletionResult.DataWipedAccountPending
                }
            }
        }

        // 3. O aparelho — ainda com o motor segurado, e com a conta capturada antes do passo 1.
        purgeLocalData(contaLocal)
        return Result.success(resultado)
    }

    /** Passo 3: nunca lança e nunca muda o resultado — a exclusão no servidor já aconteceu. */
    private suspend fun purgeLocalData(conta: String?) {
        if (clearSharedFiles) {
            runCatching { getShareHandler().clearSharedFiles(0L) }
                .onFailure { AppLogger.w(TAG, "Exclusão de conta: cópias de compartilhamento não apagadas", it) }
            runCatching { clearPrivatePhotoMemoryCache() }
                .onFailure { AppLogger.w(TAG, "Exclusão de conta: cache de fotos privadas não limpo", it) }
        }
        val purger = localData ?: return
        if (conta == null) {
            AppLogger.e(TAG, "Exclusão de conta: espelho sem titular declarado; limpeza local recusada")
            return
        }
        try {
            val relatorio = purger.purgeAccount(conta)
            if (!relatorio.isComplete) {
                AppLogger.e(TAG, "Exclusão de conta: limpeza local incompleta (${relatorio.failures} etapa(s))")
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppLogger.e(TAG, "Exclusão de conta: limpeza local falhou", e)
        }
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
