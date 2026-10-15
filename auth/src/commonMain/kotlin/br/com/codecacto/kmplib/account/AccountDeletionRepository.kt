package br.com.codecacto.kmplib.account

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.sync.rest.DomainApiClient
import br.com.codecacto.kmplib.sync.rest.DomainResult

/**
 * As rotas da exclusão com carência (2.301.0), isoladas para cada backend apontar as suas.
 *
 * O contrato da backlib é relativo a um único `{me}`: `DELETE {me}`, `GET {me}/deletion` e
 * `POST {me}/deletion/restore`. Contrato da backlib 0.181.0: `{me}` = `/v1/me/data` (o `mePath` do
 * servidor; default da casca-backend). Na casca-mobile, servida sob o slug: `/<projectSlug>/v1/me/data`.
 *
 * @property request a rota do pedido (`DELETE`, responde 202).
 * @property status a consulta (`GET`).
 * @property restore a restauração (`POST`).
 */
data class AccountDeletionPaths(
    val request: String,
    val status: String = "${request.trimEnd('/')}/deletion",
    val restore: String = "${request.trimEnd('/')}/deletion/restore",
) {
    companion object {
        /** `{me}` padrão de backend sem prefixo de projeto. */
        const val DEFAULT_ME: String = "/v1/me/data"

        /** As três rotas a partir do `{me}` do backend (ex.: `"/meu-app/v1/me/data"`). */
        fun forMe(me: String = DEFAULT_ME): AccountDeletionPaths = AccountDeletionPaths(request = me)
    }
}

/**
 * Falha de uma chamada da exclusão com carência.
 *
 * A [message] é **a frase para a tela** (2.302.0): a do **servidor** ([serverMessage]) quando ele
 * recusou por regra de negócio — 4xx, exceto 401 e 429, a mesma régua do `DomainResult.Error.userMessage`
 * (ex.: 409 `OWNER_HAS_ACTIVE_ESTABLISHMENT` → "Transfira ou encerre o estabelecimento antes…") —, e
 * a frase local ([localMessage], idioma da tela, do `DomainApiClient`) no resto. Até a 2.301.0 a
 * [message] era sempre a local ("Erro do servidor (409)") e a frase do servidor se perdia.
 *
 * [httpCode] = `-1` quando não houve resposta (sem conexão). 5xx nunca mostra a frase do servidor.
 *
 * @param message a frase LOCAL (vira [localMessage]); a [message] do objeto é a escolhida para a tela.
 * @property serverCode o código de negócio do envelope (`{"code": …}`), ou `null`.
 * @property serverMessage a `message` do envelope, crua, ou `null` (2.302.0).
 */
class AccountDeletionRequestException(
    val httpCode: Int,
    message: String,
    val serverCode: String? = null,
    val serverMessage: String? = null,
) : Exception(accountDeletionUserMessage(httpCode, message, serverMessage)) {
    /** A frase local (genérica) que o `DomainApiClient` montou (2.302.0). */
    val localMessage: String = message

    /** `true` quando nem chegou ao servidor (sem conexão/timeout). */
    val isOffline: Boolean get() = httpCode == DomainResult.OFFLINE_CODE

    /** `true` quando a [message] é a frase do servidor (2.302.0). */
    val hasServerMessage: Boolean
        get() = accountDeletionServerMessageApplies(httpCode, serverMessage)
}

/** A régua do `DomainResult.Error.userMessage`: frase do servidor em recusa 4xx (exceto 401/429). */
internal fun accountDeletionServerMessageApplies(httpCode: Int, serverMessage: String?): Boolean =
    !serverMessage.isNullOrBlank() && httpCode in 400..499 && httpCode != 401 && httpCode != 429

internal fun accountDeletionUserMessage(httpCode: Int, localMessage: String, serverMessage: String?): String =
    if (accountDeletionServerMessageApplies(httpCode, serverMessage)) serverMessage!!.trim() else localMessage

/**
 * **A exclusão de conta com carência de 30 dias** (2.301.0) — as três chamadas do contrato.
 *
 * Nenhuma lança: tudo volta em [Result]. Rota protegida por autenticação recente (backlib
 * `requireRecentAuth`) devolve `ReauthRequiredException` — passe pelo `RecentAuthState.run { }`.
 */
interface AccountDeletionRepository {
    /**
     * `DELETE {me}` — programa a exclusão (202). [confirmation] = o que a pessoa digitou ("EXCLUIR"),
     * enviado no corpo (`{"confirmacao": …}`); a backlib 0.181.0 recusa sem ela (400
     * `CONFIRMATION_REQUIRED`). `null` = sem corpo. Autenticação com mais de 5 min ⇒ 401
     * `REAUTH_REQUIRED` ⇒ `ReauthRequiredException` (passe pelo `RecentAuthState.run { }`).
     *
     * **Não** mexe no [AccountDeletionState]: quem pediu está saindo (o `AccountDeletionService`
     * encerra a sessão), e cobrir a tela de conta com o portão antes disso seria o portão errado.
     */
    suspend fun requestDeletion(confirmation: String? = null): Result<AccountDeletionStatus>

    /** `GET {me}/deletion` — a situação atual. Atualiza o [AccountDeletionState] (pendente cobre; não pendente limpa). */
    suspend fun deletionStatus(): Result<AccountDeletionStatus>

    /** `POST {me}/deletion/restore` — desiste da exclusão. Sucesso limpa o [AccountDeletionState]. */
    suspend fun restore(): Result<AccountDeletionStatus>
}

/**
 * [AccountDeletionRepository] sobre o [DomainApiClient] (Bearer, refresh no 401, textos no idioma da
 * tela). Uma linha no Koin:
 *
 * ```kotlin
 * single<AccountDeletionRepository> {
 *     RestAccountDeletionRepository(get(), AccountDeletionPaths.forMe("/${AppConfig.projectSlug}/v1/me/data"))
 * }
 * ```
 *
 * @param state o estado que o `AccountDeletionGate` lê. Default: o do processo.
 * @param confirmationField o campo do corpo do `DELETE` com a palavra digitada (`{"confirmacao": …}`).
 */
class RestAccountDeletionRepository(
    private val api: DomainApiClient,
    private val paths: AccountDeletionPaths = AccountDeletionPaths.forMe(),
    private val state: AccountDeletionState = AccountDeletionState.Shared,
    private val confirmationField: String = AccountDeletionService.DEFAULT_CONFIRMATION_FIELD,
) : AccountDeletionRepository {

    override suspend fun requestDeletion(confirmation: String?): Result<AccountDeletionStatus> {
        val resposta = if (confirmation == null) {
            api.deleteJson(paths.request)
        } else {
            api.deleteJson(paths.request, accountDeletionConfirmationBody(confirmationField, confirmation))
        }
        return resposta.toStatus("pedido de exclusão") { corpo ->
            // 202 sem corpo legível ainda é exclusão programada: o servidor aceitou o pedido.
            parseAccountDeletionStatus(corpo) ?: AccountDeletionStatus(pending = true)
        }
    }

    override suspend fun deletionStatus(): Result<AccountDeletionStatus> {
        val resposta = api.getJson(paths.status)
        // Backend que protege até a consulta com o portão responde o próprio 403: é a resposta.
        if (resposta is DomainResult.Error && resposta.isAccountPendingDeletion) {
            val pendente = AccountDeletionStatus(
                pending = true,
                scheduledFor = resposta.details["scheduledFor"],
                daysRemaining = resposta.details["daysRemaining"]?.toIntOrNull()?.coerceAtLeast(0),
                requestedAt = resposta.details["requestedAt"],
            )
            state.report(pendente)
            return Result.success(pendente)
        }
        return resposta.toStatus("consulta da exclusão") { parseAccountDeletionStatus(it) }
            .onSuccess { state.report(it) }
    }

    override suspend fun restore(): Result<AccountDeletionStatus> =
        api.postJson(paths.restore, "{}")
            .toStatus("restauração da conta") { parseAccountDeletionStatus(it) ?: AccountDeletionStatus.NotPending }
            .onSuccess { status ->
                // O servidor diz quem decide: se ainda respondeu pendente, o portão continua.
                if (status.pending) state.report(status) else state.clear()
            }

    private inline fun DomainResult<String>.toStatus(
        etapa: String,
        ler: (String) -> AccountDeletionStatus?,
    ): Result<AccountDeletionStatus> = when (this) {
        is DomainResult.Success -> ler(data)?.let { Result.success(it) } ?: run {
            AppLogger.e(TAG, "Resposta ilegível na $etapa")
            Result.failure(AccountDeletionRequestException(200, ""))
        }
        is DomainResult.Quota -> {
            AppLogger.e(TAG, "Resposta inesperada (402) na $etapa")
            Result.failure(AccountDeletionRequestException(402, ""))
        }
        is DomainResult.Error -> {
            if (isReauthRequired) {
                Result.failure(toReauthRequiredException())
            } else {
                // Só status e código de negócio no log: a frase do servidor pode citar dado da conta.
                AppLogger.w(TAG, "Falha na $etapa: HTTP $code ${serverCode.orEmpty()}")
                Result.failure(AccountDeletionRequestException(code, message, serverCode, serverMessage))
            }
        }
    }

    private companion object {
        const val TAG = "AccountDeletion"
    }
}
