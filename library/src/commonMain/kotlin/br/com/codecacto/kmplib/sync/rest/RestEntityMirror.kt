package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.sync.SyncOpType
import br.com.codecacto.kmplib.sync.SyncStore
import br.com.codecacto.kmplib.sync.db.Synced_entity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Espelho local (offline-first) de UMA entidade de domínio, sobre o [SyncStore] SQLDelight da lib
 * (tabela `synced_entity`). Reusa a MESMA infra de persistência do módulo `sync`, mas com **semântica
 * REST-CRUD** (não o protocolo `/pull`+`/push` do
 * [DefaultSyncEngine][br.com.codecacto.kmplib.sync.DefaultSyncEngine], que backends REST-CRUD não
 * oferecem): escritas gravam **limpo** quando confirmadas online, ou **sujo** (outbox) quando offline;
 * a reconciliação é por **GET de lista** (não por delta/cursor).
 *
 * `pending_op` marca a operação pendente na outbox (`create`|`update`|`delete`, do [SyncOpType.wire]);
 * `dirty=1` = há mudança local ainda não confirmada. É a peça de baixo nível usada pelo
 * [OfflineFirstRestRepository]; expõe operações finas para os endpoints **custom** do app (ex.: um
 * `PATCH /.../status`) reconciliarem o espelho sem passar pela CRUD genérica.
 *
 * ### Handle estável (2.93.0 — GAP-KL-M-RESTCRUD-IDMIGRATION)
 * **Todo id aceito aqui é um `handle`**, não necessariamente a chave física da linha: a busca casa
 * por `local_id`, `client_id` **ou** `server_id`. O id que o app recebeu no `create` offline
 * (`local-…`) continua reencontrando o registro **depois** de o drain migrar o id — porque
 * `client_id` deixou de ser sobrescrito pelo id do servidor e virou a âncora permanente da linha.
 *
 * Antes, `markSynced` apagava a linha do id local, reinseria sob o id do servidor **e** gravava o id
 * do servidor também em `client_id`: qualquer consulta pelo id que a UI carregava no back stack
 * passava a devolver vazio no meio do uso.
 *
 * @param T modelo de domínio (`@Serializable`).
 * @param name nome lógico estável da entidade (chave no espelho). Ex.: "empresa".
 * @param store espelho local (SQLDelight).
 * @param serializer serializer kotlinx do modelo (payload_json).
 * @param idOf id canônico do modelo (server id quando sincronizado; client id offline).
 * @param json Json tolerante (default [restMirrorJson]).
 */
class RestEntityMirror<T : Any>(
    private val name: String,
    private val store: SyncStore,
    private val serializer: KSerializer<T>,
    private val idOf: (T) -> String,
    private val json: Json = restMirrorJson,
) {
    // -- Leitura -----------------------------------------------------------

    fun observeVisible(): Flow<List<T>> =
        store.observeVisible(name).map { rows -> rows.mapNotNull { decode(it.payload_json) } }

    /** Observa a linha por **handle** — segue emitindo o mesmo registro depois de o id migrar. */
    fun observeVisibleById(localId: String): Flow<T?> =
        store.observeVisibleByHandle(name, localId).map { row -> row?.let { decode(it.payload_json) } }

    fun getVisible(): List<T> = store.getVisible(name).mapNotNull { decode(it.payload_json) }

    fun get(localId: String): T? = rowFor(localId)?.let { decode(it.payload_json) }

    /** Linha crua do espelho para um **handle** (`local_id`/`client_id`/`server_id`). */
    fun rowFor(handle: String): Synced_entity? = store.getByHandle(name, handle)

    /**
     * **Chave física** da linha para um handle (o `local_id` da tabela) — o id que as operações de
     * escrita de baixo nível do [SyncStore] esperam. Cai no próprio handle quando a linha não existe.
     */
    fun rowIdOf(handle: String): String = rowFor(handle)?.local_id ?: handle

    /**
     * **Id canônico** de um handle: o id do servidor quando o registro já subiu, o id local enquanto
     * não subiu. É o id a usar em URL de endpoint custom do app (`PATCH /v1/x/{id}/status`) e como
     * chave de correlação de filhos.
     *
     * Resolve também quando a linha já saiu do espelho, pelo **remap durável**
     * ([SyncStore.resolveServerId]).
     */
    fun canonicalIdOf(handle: String): String {
        rowFor(handle)?.let { return it.server_id ?: it.local_id }
        return store.resolveServerId(handle) ?: handle
    }

    /**
     * [canonicalIdOf] **reativo**: emite o handle enquanto o registro só existe local e o id do
     * servidor assim que ele migra — sem que a tela precise ser recriada.
     *
     * Serve para montar a **URL** de um endpoint custom (`PATCH /v1/rotas/{id}/encerrar`), onde há um
     * id só e ele tem de ser o que o servidor conhece.
     *
     * ### NÃO use isto para correlacionar filhos por igualdade de id
     * ```kotlin
     * // ERRADO — esvazia a lista no meio do uso:
     * observeCanonicalId(handle).flatMapLatest { id -> filhos.map { l -> l.filter { it.paiId == id } } }
     * ```
     * O drain **pausa** ao perder o sinal (`RestFailureClass.Offline`), então filhos já migrados
     * (FK = id do servidor) e filhos ainda locais (FK = id local) convivem na mesma lista: comparar
     * por igualdade contra QUALQUER um dos dois derruba a outra metade. Correlacione por **conjunto
     * de handles** — ver [OfflineFirstRestRepository.observeChildren] e
     * [RestIdResolver.handlesOf].
     */
    fun observeCanonicalId(handle: String): Flow<String> =
        store.observeVisibleByHandle(name, handle)
            .map { row -> row?.let { it.server_id ?: it.local_id } ?: store.resolveServerId(handle) ?: handle }
            .distinctUntilChanged()

    /** Server id conhecido para um handle. `null` se o registro ainda não foi sincronizado. */
    fun serverIdOf(localId: String): String? =
        rowFor(localId)?.server_id ?: store.resolveServerId(localId)

    /**
     * A linha existe no espelho e **nunca foi confirmada pelo servidor** (`server_id == null`): ela
     * só existe neste aparelho, sob um id local. Toda escrita sobre ela ainda é uma **criação**, e
     * apagá-la se resolve **localmente** — nenhum `PUT`/`DELETE` em `/…/local-…` faz sentido (o
     * servidor não conhece esse id e responderia 404).
     */
    fun isLocalOnly(localId: String): Boolean {
        val linha = rowFor(localId) ?: return false
        return linha.server_id == null
    }

    /** Linhas sujas (outbox) desta entidade, mais antigas primeiro — **inclusive as recusadas**. */
    fun dirtyRows(): List<Synced_entity> = store.getDirty(name)

    /**
     * Linhas **drenáveis** (sujas que ainda não foram recusadas de forma terminal). É o que o push
     * consome: uma linha `Failed` só volta por retry explícito ([clearFailure]).
     */
    fun drainableRows(): List<Synced_entity> = store.getDrainable(name)

    // -- Estado de escrita por linha (2.91.0) ------------------------------

    /** Estado de sync de uma linha (por **handle**): pendente / falhou / sincronizada. */
    fun stateOf(localId: String): RestRowState =
        rowFor(localId)?.toRestRowState() ?: RestRowState.Synced

    /** Espelho visível **com o estado de cada linha** — a lista que a UI renderiza sem overlay próprio. */
    fun observeVisibleWithState(): Flow<List<RestRow<T>>> =
        store.observeVisible(name).map { rows -> rows.mapNotNull { it.toRestRow() } }

    /** Uma linha visível com o seu estado (por **handle**). */
    fun observeVisibleWithStateById(localId: String): Flow<RestRow<T>?> =
        store.observeVisibleByHandle(name, localId).map { row -> row?.toRestRow() }

    fun getVisibleWithState(): List<RestRow<T>> = store.getVisible(name).mapNotNull { it.toRestRow() }

    /** Linhas que o servidor **recusou** (com o erro preservado). */
    fun failedRows(): List<RestRow<T>> = store.getFailed(name).mapNotNull { it.toRestRow() }

    /** Marca a linha como recusada de forma terminal, preservando código e mensagem do servidor. */
    fun markFailed(localId: String, code: Int, message: String?) =
        store.markFailed(name, rowIdOf(localId), code, message)

    /** Devolve a linha recusada à outbox drenável (retry explícito do app). */
    fun clearFailure(localId: String) = store.clearFailed(name, rowIdOf(localId))

    /** Conta uma tentativa que falhou de forma **retentável** (a linha continua na outbox). */
    fun bumpAttempt(localId: String, error: String?) = store.bumpAttempt(name, rowIdOf(localId), error)

    private fun Synced_entity.toRestRow(): RestRow<T>? =
        decode(payload_json)?.let { RestRow(it, toRestRowState()) }

    // -- Escrita -----------------------------------------------------------

    /**
     * Grava/atualiza LIMPO (confirmado pelo servidor). [serverId] = id do servidor.
     *
     * **Preserva o `client_id`** de uma linha que já existia — a âncora do handle.
     *
     * @param replacingHandle id **local** da linha que esta confirmação substitui, quando o app
     *   confirmou a criação por um endpoint **próprio** (fora da CRUD genérica). Informando-o, a
     *   linha local **migra** para o id do servidor e o remap durável é registrado — exatamente como
     *   no drain. Sem ele, uma criação confirmada por fora deixaria a linha local órfã ao lado da
     *   nova, e o handle que a UI carrega apontaria para o registro errado.
     */
    fun putClean(
        model: T,
        serverId: String = idOf(model),
        updatedAt: String? = null,
        replacingHandle: String? = null,
    ) {
        writeClean(model, serverId, updatedAt, handle = replacingHandle ?: idOf(model))
    }

    /** Reconcilia o conjunto vindo do servidor: substitui as linhas LIMPAS, preservando as sujas. */
    fun reconcile(serverModels: List<T>) {
        store.transaction {
            val serverIds = serverModels.mapTo(mutableSetOf()) { idOf(it) }
            // Remove linhas limpas locais que sumiram do servidor (soft-delete server-side refletido).
            store.getVisible(name).forEach { row ->
                val stillClean = row.dirty == 0L && row.server_id != null
                if (stillClean && row.server_id !in serverIds) store.deleteHard(name, row.local_id)
            }
            // Upsert limpo dos itens do servidor que NÃO têm edição local pendente.
            serverModels.forEach { model ->
                val id = idOf(model)
                val existing = store.getByHandle(name, id)
                if (existing == null || existing.dirty == 0L) writeClean(model, id, null, handle = id)
            }
        }
    }

    /**
     * Upsert LIMPO de [serverModels] **preservando** linhas com edição local pendente (dirty) — a
     * metade "aditiva" da [reconcile], **sem** remover nada. Serve o refresh **upsert-only** de domínios
     * com paginação/busca **server-side pura** (o espelho é cache PARCIAL de janelas visitadas, não o
     * dataset completo): ali apagar "o que não veio nesta página" removeria itens de outras páginas.
     * Diferente da [reconcile], que assume o conjunto COMPLETO e pode `deleteHard` os ausentes.
     */
    fun mergeClean(serverModels: List<T>) {
        store.transaction {
            serverModels.forEach { model ->
                val id = idOf(model)
                val existing = store.getByHandle(name, id)
                if (existing == null || existing.dirty == 0L) writeClean(model, id, null, handle = id)
            }
        }
    }

    /**
     * Grava SUJO (offline) enfileirando [op] na outbox — **preservando a operação pendente enquanto
     * a linha ainda não subiu**. `localId` = id local (client id no create).
     *
     * A operação efetivamente gravada sai de [resolveOutboxOp]: enquanto `server_id == null` a linha
     * continua pendente de **CREATE** (um `update` só troca o payload), e um `delete` sobre ela
     * **remove a linha localmente** em vez de enfileirar um `DELETE` que só produziria 404.
     */
    fun putDirty(model: T, op: SyncOpType) {
        val handle = idOf(model)
        val existing = rowFor(handle)
        val efetiva = resolveOutboxOp(
            requested = op,
            knownLocally = existing != null,
            hasServerId = existing?.server_id != null,
        ) ?: run { store.deleteHard(name, existing?.local_id ?: handle); return }
        store.upsert(
            row(
                model,
                localId = existing?.local_id ?: handle,
                serverId = existing?.server_id,
                dirty = true,
                pendingOp = efetiva.wire,
                clientId = existing?.client_id ?: handle,
                updatedAt = null,
                // Escrita do usuário: limpa o estado ATUAL da falha (a intenção nova substitui a
                // recusa) mas PRESERVA o histórico de entrega — senão o toque seguinte apagaria a
                // prova de que o servidor já recusou este registro.
                delivery = existing.deliveryHistory(),
            ),
        )
    }

    /**
     * Marca exclusão (tombstone) mantendo o payload para replay do delete na reconexão.
     *
     * **Linha que nunca subiu** (`server_id == null` — ainda pendente de CREATE) é **removida de
     * vez**: não há nada a apagar no servidor, e enfileirar um `DELETE /…/local-…` só renderia um
     * 404 previsível, que a máquina de estados classificaria como recusa terminal.
     */
    fun tombstone(localId: String) {
        val existing = rowFor(localId) ?: return
        val efetiva = resolveOutboxOp(
            requested = SyncOpType.DELETE,
            knownLocally = true,
            hasServerId = existing.server_id != null,
        )
        if (efetiva == null) {
            store.deleteHard(name, existing.local_id)
            return
        }
        store.upsert(
            existing.copy(
                dirty = 1L,
                pending_op = SyncOpType.DELETE.wire,
                deleted = 1L,
                // Intenção nova do usuário: sai do estado "recusado" e volta à outbox drenável.
                failed = 0L,
                fail_code = null,
                last_error = null,
            ),
        )
    }

    /**
     * Após confirmar no servidor: migra o id local (client id) para o server id e grava LIMPO.
     *
     * Duas garantias novas na 2.93.0, ambas necessárias para o registro **não sumir** da UI nem
     * quebrar a FK dos filhos:
     * 1. **`client_id` é preservado** — a linha migra de `local_id`, mas continua alcançável pelo id
     *    que o app carregou na navegação ([observeVisibleById]/[get]/[stateOf] por handle);
     * 2. **o remap `clientId → serverId` é gravado de forma durável** ([SyncStore.rememberServerId]),
     *    para um filho que drene em OUTRO ciclo — ou depois de o processo morrer — ainda conseguir
     *    traduzir a FK do pai. Antes, essa tradução vivia numa variável do ciclo de sync.
     */
    fun markSynced(oldLocalId: String, serverModel: T) {
        writeClean(serverModel, idOf(serverModel), updatedAt = null, handle = oldLocalId)
    }

    /** Confirma um update/status (mesmo id) como LIMPO. */
    fun confirm(model: T) {
        writeClean(model, idOf(model), updatedAt = null, handle = idOf(model))
    }

    /**
     * Caminho ÚNICO de escrita LIMPA (confirmada pelo servidor) — usado por [putClean], [confirm],
     * [markSynced], [reconcile] e [mergeClean]. Concentra as invariantes que antes cada caminho
     * repetia (ou esquecia):
     * - a chave física da linha passa a ser o **id do servidor**;
     * - **`client_id` nunca muda** depois de atribuído (âncora do handle);
     * - migração de id (`client_id != server_id`) é **registrada de forma durável**;
     * - o **histórico de entrega é zerado** (`delivery = NONE`) — este é o ÚNICO ponto que apaga a
     *   evidência de recusa, e apaga porque o servidor **aceitou**: o registro existe do outro lado
     *   e não há mais nada de que desconfiar.
     */
    private fun writeClean(model: T, serverId: String, updatedAt: String?, handle: String) {
        // O handle vem primeiro: numa migração ele identifica **a linha que está migrando**, e é ela
        // que precisa ser removida e ter o `client_id` herdado.
        val existing = rowFor(handle) ?: rowFor(serverId)
        val clientId = existing?.client_id ?: handle.takeIf { it.isNotBlank() } ?: serverId
        store.transaction {
            val antiga = existing?.local_id
            if (antiga != null && antiga != serverId) store.deleteHard(name, antiga)
            store.upsert(
                row(
                    model,
                    localId = serverId,
                    serverId = serverId,
                    dirty = false,
                    pendingOp = null,
                    clientId = clientId,
                    updatedAt = updatedAt,
                ),
            )
            if (clientId != serverId) store.rememberServerId(name, clientId, serverId)
        }
    }

    /**
     * Atualiza o payload de um registro **preservando** seu estado de sync (dirty/pending_op/server_id).
     * Usado para ajustes locais que não devem virar nova operação de outbox nem apagar uma pendente
     * (ex.: contagem denormalizada de filhos após upload/remoção online).
     */
    fun patch(model: T) {
        val existing = rowFor(idOf(model)) ?: run { confirm(model); return }
        store.upsert(existing.copy(payload_json = json.encodeToString(serializer, model)))
    }

    /** Remoção física local (após confirmar o delete no servidor, ou reconciliação). */
    fun removeHard(localId: String) = store.deleteHard(name, rowIdOf(localId))

    /**
     * Monta a linha do espelho. `account_id` sai em branco de propósito: **quem escopa é o
     * [SyncStore]**, que grava sempre na conta corrente — assim nenhuma linha pode ser escrita no
     * bucket de outra conta por engano.
     *
     * Escrever (limpo ou sujo) **zera o estado ATUAL da falha** (`failed`/`fail_code`/`last_error`):
     * uma alteração nova do usuário substitui a recusa anterior e volta à outbox drenável.
     *
     * O **histórico de entrega** ([delivery]) é outra coisa e segue outra regra: [putDirty] o carrega
     * adiante, [writeClean] o zera. Ver [DeliveryHistory].
     */
    private fun row(
        model: T,
        localId: String,
        serverId: String?,
        dirty: Boolean,
        pendingOp: String?,
        clientId: String = idOf(model),
        updatedAt: String?,
        delivery: DeliveryHistory = DeliveryHistory.NONE,
    ): Synced_entity = Synced_entity(
        account_id = "",
        entity = name,
        local_id = localId,
        server_id = serverId,
        client_id = clientId,
        payload_json = json.encodeToString(serializer, model),
        updated_at = updatedAt,
        dirty = if (dirty) 1L else 0L,
        pending_op = pendingOp,
        deleted = 0L,
        base_updated_at = null,
        last_error = null,
        failed = 0L,
        fail_code = null,
        attempts = delivery.attempts,
        rejections = delivery.rejections,
        reject_code = delivery.rejectCode,
        reject_error = delivery.rejectError,
    )

    fun decode(payloadJson: String): T? =
        runCatching { json.decodeFromString(serializer, payloadJson) }.getOrNull()

    /**
     * **Histórico de entrega** de uma linha (2.94.0 — GAP-KL-M-RESTCRUD-REJECTHISTORY): quantas
     * tentativas de push já houve e o que o servidor já respondeu de recusa.
     *
     * Vive separado do estado ATUAL da falha porque tem tempo de vida diferente: o estado atual é
     * substituído pela próxima escrita do usuário; o histórico só é zerado quando o servidor
     * **aceita** a linha ([writeClean]). Sem essa separação, qualquer toque posterior apagava a
     * evidência de que o registro havia sido recusado, e a pendência resultante era indistinguível
     * de uma pendência nova legítima — que o app dá por boa.
     */
    private data class DeliveryHistory(
        val attempts: Long,
        val rejections: Long,
        val rejectCode: Long?,
        val rejectError: String?,
    ) {
        companion object {
            /** Linha nova, ou linha que o servidor acabou de aceitar: nada a lembrar. */
            val NONE = DeliveryHistory(attempts = 0L, rejections = 0L, rejectCode = null, rejectError = null)
        }
    }

    private fun Synced_entity?.deliveryHistory(): DeliveryHistory =
        if (this == null) {
            DeliveryHistory.NONE
        } else {
            DeliveryHistory(
                attempts = attempts,
                rejections = rejections,
                rejectCode = reject_code,
                rejectError = reject_error,
            )
        }
}

/** Json tolerante do espelho REST-CRUD (mesma política do módulo `sync`). */
val restMirrorJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
}
