package br.com.codecacto.kmplib.sync

import kotlinx.serialization.KSerializer

/**
 * Tipo de operação local de sync (outbox). Distinto do DTO de fio [SyncOp]:
 * este é o enum da API pública do [SyncEngine]; o wire usa a string canônica.
 */
enum class SyncOpType(val wire: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete");

    companion object {
        fun fromWire(value: String): SyncOpType =
            entries.firstOrNull { it.wire == value } ?: UPDATE
    }
}

/**
 * Descreve como uma entidade de domínio do app participa do sync, sem acoplar a lib
 * ao domínio. O app implementa uma instância por tipo e registra no [SyncEngine].
 *
 * @param T tipo do modelo de domínio (DTO por-papel do app, `@Serializable`).
 */
interface SyncableEntity<T : Any> {
    /** Nome lógico estável da entidade (chave no espelho e no wire). Ex.: "frete". */
    val name: String

    /** Serializer kotlinx do modelo — usado para (de)serializar o `payload_json`. */
    val serializer: KSerializer<T>

    /** clientId idempotente do modelo (uuid gerado pelo app). */
    fun clientIdOf(model: T): String

    /** serverId do modelo, ou `null` se ainda não confirmado pelo servidor. */
    fun serverIdOf(model: T): String?

    /** updatedAt do modelo (ISO-8601), ou `null` se ainda não tem. */
    fun updatedAtOf(model: T): String?

    /**
     * **FKs por-clientId deste modelo** para outras entidades sincronizáveis.
     *
     * Retorna `{ nomeDoCampoFkNoWire → clientIdLocal do pai }`. Ex.: para um frete que
     * referencia um cliente e um caminhão criados offline:
     * `mapOf("clientRef" to model.clientRef, "truckId" to model.truckId)`.
     *
     * O [SyncEngine] usa este mapa no push para **nunca enviar ao servidor uma FK que seja
     * um clientId local órfão**:
     *  - se o pai já tem `serverId` conhecido no espelho (já sincronizado), o engine faz
     *    **backfill** — substitui o clientId local pelo serverId direto no payload enviado;
     *  - se o pai ainda está pendente no mesmo lote, o engine inclui a entrada em
     *    [SyncOp.refs] para o servidor resolver `clientId→serverId` em ordem topológica.
     *
     * O **nome da chave** deve ser exatamente o campo da FK como ele aparece no `payload`
     * serializado (wire). O valor deve ser o `clientId` local do pai (o mesmo que
     * [clientIdOf] retorna para a entidade-pai). Entradas com valor em branco/nulo são
     * ignoradas pelo engine. Default `emptyMap()` (entidade sem FKs, ex.: client/truck).
     *
     * **Importante:** o valor aqui é sempre o **clientId local** do pai (não o serverId);
     * o engine decide entre backfill e [SyncOp.refs] consultando o espelho. Se o app já
     * resolveu a FK para o serverId (registro veio do servidor), passe esse valor mesmo
     * assim — o engine só remapeia clientIds locais conhecidos no espelho e mantém qualquer
     * outro valor intacto no payload.
     */
    fun refsOf(model: T): Map<String, String> = emptyMap()
}

/**
 * Transporte de sync fornecido pelo **app**, sobre o ApiClient/HttpClient dele.
 * A lib não conhece baseUrl, auth nem engine HTTP — o app encapsula isso aqui.
 */
interface SyncPort {
    /** Baixa deltas do servidor. */
    suspend fun pull(req: SyncPullRequest): SyncPullResponse

    /** Envia operações da outbox e devolve o resultado por operação. */
    suspend fun push(req: SyncPushRequest): SyncPushResponse
}

/**
 * Estado agregado do motor de sync, exposto como StateFlow para a UI
 * ([br.com.codecacto.kmplib.ui.components.SyncBanner]).
 */
sealed interface SyncState {
    /** Em repouso, sem pendências conhecidas. */
    data object Idle : SyncState

    /** Sincronizando agora; [pending] = itens restantes na outbox. */
    data class Syncing(val pending: Int) : SyncState

    /** Sem rede — sync adiado até voltar a conexão. */
    data object Offline : SyncState

    /** Falha de sync (rede/servidor). [message] é amigável. */
    data class Error(val message: String) : SyncState
}

/** Status de um item da fila visível de sync. */
enum class SyncItemStatus { Pending, Syncing, Conflict, Error }

/**
 * Item da fila de sync exibido pela UI ([br.com.codecacto.kmplib.ui.components.SyncQueueView]).
 * Modelo de UI puro derivado do espelho local.
 */
data class SyncQueueItem(
    val localId: String,
    val entity: String,
    val op: SyncOpType,
    val status: SyncItemStatus,
    val label: String,
    val error: String? = null,
)

/**
 * Resumo de uma rodada de sync (push e/ou pull).
 *
 * @param pushed nº de operações aceitas no push (applied).
 * @param conflicts nº de operações em conflito (servidor venceu, payload aplicado).
 * @param rejected nº de operações rejeitadas pelo servidor.
 * @param pulled nº de deltas aplicados do pull.
 * @param deletedApplied nº de tombstones aplicados do pull.
 * @param failed `true` se a rodada falhou por rede/erro (ver [error]).
 * @param error mensagem de falha, se houver.
 */
data class SyncResultSummary(
    val pushed: Int = 0,
    val conflicts: Int = 0,
    val rejected: Int = 0,
    val pulled: Int = 0,
    val deletedApplied: Int = 0,
    val failed: Boolean = false,
    val error: String? = null,
) {
    val ok: Boolean get() = !failed

    operator fun plus(other: SyncResultSummary): SyncResultSummary = SyncResultSummary(
        pushed = pushed + other.pushed,
        conflicts = conflicts + other.conflicts,
        rejected = rejected + other.rejected,
        pulled = pulled + other.pulled,
        deletedApplied = deletedApplied + other.deletedApplied,
        failed = failed || other.failed,
        error = error ?: other.error,
    )

    companion object {
        val EMPTY = SyncResultSummary()
        fun failure(message: String) = SyncResultSummary(failed = true, error = message)
    }
}
