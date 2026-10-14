@file:OptIn(ExperimentalTime::class)

package br.com.codecacto.kmplib.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * # Exclusão de conta com CARÊNCIA de 30 dias (2.301.0)
 *
 * A conta não some no toque de "Excluir": o servidor (backlib) programa a exclusão, bloqueia a conta e
 * guarda os dados. Entrando de novo, a pessoa vê "Sua conta será excluída definitivamente após 30 dias
 * do pedido" (+ "Faltam X dias para liberar a exclusão definitiva") e pode **restaurar**; passado o
 * prazo, a eliminação definitiva fica liberada (é manual e pode acontecer depois do dia 30).
 *
 * Contrato HTTP (fixo, par da backlib 0.181.0; `{me}` = `/v1/me/data` por default, `/<slug>/v1/me/data`
 * quando o backend é servido sob o slug do projeto):
 * - `DELETE {me}` com `{"confirmacao":"EXCLUIR"}` → 202
 *   `{"status":"PENDING_DELETION","requestedAt","scheduledFor","daysRemaining"}`; 400
 *   `CONFIRMATION_REQUIRED`; 401 `REAUTH_REQUIRED` (autenticação com mais de 5 min)
 * - `GET {me}/deletion` → `{"pending":bool,"requestedAt"?,"scheduledFor"?,"daysRemaining"?}`
 * - `POST {me}/deletion/restore` → `{"pending":false}`
 * - qualquer rota autenticada com a conta pendente — inclusive `GET /v1/auth/me` — → **403**
 *   `{"message","code":"ACCOUNT_PENDING_DELETION","timestamp","path","details":{"requestedAt","scheduledFor","daysRemaining"}}`
 *   (os campos vêm DENTRO de `details`).
 *
 * **Datas são informação, não promessa:** a eliminação definitiva é feita depois do prazo e pode
 * acontecer após o dia 30. A tela nunca diz "será excluída em X dias" nem "hoje".
 *
 * Este arquivo (kmplib-core) é a parte sem UI: o modelo, a leitura das respostas e o estado único que
 * a rede alimenta ([AccountDeletionDetector]) e o `AccountDeletionGate` (kmplib-auth) lê.
 */

/** O `code` do 403 de conta com exclusão programada. */
const val ACCOUNT_PENDING_DELETION_CODE: String = "ACCOUNT_PENDING_DELETION"

/** O status HTTP da recusa de conta com exclusão programada. */
const val ACCOUNT_PENDING_DELETION_STATUS: Int = 403

/** O `status` da resposta do `DELETE {me}` (202). */
const val PENDING_DELETION_STATUS_VALUE: String = "PENDING_DELETION"

private const val MILLIS_PER_DAY: Long = 24L * 60 * 60 * 1000

/**
 * A situação da exclusão da conta, como o servidor a descreve (2.301.0).
 *
 * As datas ficam como o servidor mandou (ISO-8601) — quem decide é ele; o app só exibe.
 *
 * @property pending `true` = exclusão programada (conta bloqueada, dados guardados, dá para restaurar).
 * @property requestedAt quando o pedido foi feito.
 * @property scheduledFor quando a exclusão vira definitiva.
 * @property daysRemaining dias que faltam, contados pelo servidor (nunca negativo).
 */
data class AccountDeletionStatus(
    val pending: Boolean,
    val requestedAt: String? = null,
    val scheduledFor: String? = null,
    val daysRemaining: Int? = null,
) {
    /** [scheduledFor] em epoch millis, ou `null` se ausente/ilegível. */
    val scheduledForEpochMillis: Long?
        get() = scheduledFor?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

    /**
     * Os dias a mostrar na tela: o [daysRemaining] do servidor quando veio; senão, contados de
     * [scheduledFor] até [nowEpochMillis], arredondando para CIMA (faltando 1h, ainda é "1 dia" —
     * "0 dias" diria que já foi). `null` quando nenhum dos dois veio.
     */
    fun daysRemainingAt(nowEpochMillis: Long): Int? {
        daysRemaining?.let { return it.coerceAtLeast(0) }
        val alvo = scheduledForEpochMillis ?: return null
        val restante = alvo - nowEpochMillis
        if (restante <= 0) return 0
        return ((restante + MILLIS_PER_DAY - 1) / MILLIS_PER_DAY).toInt()
    }

    companion object {
        /** Sem exclusão programada. */
        val NotPending: AccountDeletionStatus = AccountDeletionStatus(pending = false)
    }
}

private val deletionJson = Json { ignoreUnknownKeys = true; isLenient = true }

private fun JsonObject.texto(nome: String): String? =
    (this[nome] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.inteiro(nome: String): Int? {
    val p = this[nome] as? JsonPrimitive ?: return null
    val bruto = p.contentOrNull?.trim() ?: return null
    return (bruto.toIntOrNull() ?: bruto.toDoubleOrNull()?.toInt())?.coerceAtLeast(0)
}

private fun parseObject(body: String?): JsonObject? = runCatching {
    val texto = body?.trim().orEmpty()
    if (!texto.startsWith("{")) return@runCatching null
    deletionJson.parseToJsonElement(texto).jsonObject
}.getOrNull()

/**
 * Lê a resposta do `GET {me}/deletion`, do `DELETE {me}` (202) ou do `POST {me}/deletion/restore`.
 *
 * `pending` (booleano) vence; sem ele, `status == "PENDING_DELETION"` conta como pendente. Corpo
 * ilegível, não-JSON ou sem nenhum dos dois ⇒ `null` (quem chama trata como falha). Nunca lança.
 */
fun parseAccountDeletionStatus(body: String?): AccountDeletionStatus? {
    val raiz = parseObject(body) ?: return null
    val pending = (raiz["pending"] as? JsonPrimitive)?.booleanOrNull
        ?: raiz.texto("status")?.let { it.equals(PENDING_DELETION_STATUS_VALUE, ignoreCase = true) }
        ?: return null
    return AccountDeletionStatus(
        pending = pending,
        requestedAt = raiz.texto("requestedAt"),
        scheduledFor = raiz.texto("scheduledFor"),
        daysRemaining = raiz.inteiro("daysRemaining"),
    )
}

/**
 * Lê o **403 `ACCOUNT_PENDING_DELETION`** (2.301.0). `code` na raiz (o `ErrorResponse` da backlib) ou
 * aninhado em `error`; `scheduledFor`/`daysRemaining`/`requestedAt` procurados em `details`, depois no
 * mesmo objeto do `code`. Outro status, outro `code`, corpo vazio ou não-JSON ⇒ `null`. Nunca lança.
 */
fun parseAccountPendingDeletion(status: Int, body: String?): AccountDeletionStatus? {
    if (status != ACCOUNT_PENDING_DELETION_STATUS) return null
    val raiz = parseObject(body) ?: return null
    val alvo = (raiz["error"] as? JsonObject) ?: raiz
    val code = alvo.texto("code") ?: return null
    if (!code.equals(ACCOUNT_PENDING_DELETION_CODE, ignoreCase = true)) return null
    val detalhes = alvo["details"] as? JsonObject
    fun campoTexto(nome: String): String? = detalhes?.texto(nome) ?: alvo.texto(nome)
    fun campoInteiro(nome: String): Int? = detalhes?.inteiro(nome) ?: alvo.inteiro(nome)
    return AccountDeletionStatus(
        pending = true,
        requestedAt = campoTexto("requestedAt"),
        scheduledFor = campoTexto("scheduledFor"),
        daysRemaining = campoInteiro("daysRemaining"),
    )
}

/** `true` se o [code]/[serverCode] é o da conta com exclusão programada. */
fun isAccountPendingDeletion(code: Int, serverCode: String?): Boolean =
    code == ACCOUNT_PENDING_DELETION_STATUS &&
        serverCode?.trim().equals(ACCOUNT_PENDING_DELETION_CODE, ignoreCase = true)

/**
 * **O estado único "a conta está com exclusão programada"** (2.301.0) — o ponto comum entre a rede
 * (que vê o 403) e a tela (o `AccountDeletionGate`, kmplib-auth, que cobre a área logada).
 *
 * Quem alimenta: o [AccountDeletionDetector] do `HttpClient` (ligado por default no
 * `createHttpClient` — `HttpClientOptions.accountDeletion`), e a consulta `GET {me}/deletion` que o
 * gate faz logo depois do login. Quem limpa: a restauração, o "Sair", a troca de conta e um 2xx de
 * rota protegida do mesmo servidor.
 *
 * Não é a autoridade — quem bloqueia é o servidor. O app nunca usa este estado para liberar nada.
 */
class AccountDeletionState {
    private val _pending = MutableStateFlow<AccountDeletionStatus?>(null)

    /** A exclusão programada vigente, ou `null` (conta normal, ou ainda sem resposta do servidor). */
    val pending: StateFlow<AccountDeletionStatus?> = _pending.asStateFlow()

    /** `true` enquanto houver exclusão programada. */
    val isPending: Boolean get() = _pending.value != null

    /** Registra a situação: pendente cobre a área logada; não pendente limpa. */
    fun report(status: AccountDeletionStatus) {
        _pending.value = status.takeIf { it.pending }
    }

    /** Registra a partir de uma resposta crua; `true` se era o 403 de conta pendente. */
    fun reportIfPending(status: Int, body: String?): Boolean {
        val pendente = parseAccountPendingDeletion(status, body) ?: return false
        report(pendente)
        return true
    }

    /** Tira o bloqueio (restaurou, saiu, trocou de conta). */
    fun clear() {
        _pending.value = null
    }

    companion object {
        /** A instância do processo — a que o `createHttpClient` e o `AccountDeletionGate` usam por default. */
        val Shared: AccountDeletionState = AccountDeletionState()
    }
}
