package br.com.codecacto.kmplib.sync

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.sync.db.SyncDatabase
import br.com.codecacto.kmplib.sync.db.Synced_entity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Espelho local do sync (outbox + estado por registro). Abstração fina sobre a
 * persistência: a impl default ([SqlDelightSyncStore]) usa SQLDelight, mas o contrato
 * é uma interface para permitir fakes em `commonTest` (o módulo não tem driver de banco
 * em teste comum) e exercitar o [DefaultSyncEngine] pelo caminho real.
 *
 * **Compatibilidade:** construir com `SyncStore(db)` continua válido — o `operator fun
 * invoke` no companion devolve a impl SQLDelight (call-site idêntico ao anterior, quando
 * `SyncStore` era a classe concreta).
 *
 * ### Escopo de conta (2.91.0 — GAP-KL-M-SYNC-ACCOUNTSCOPE)
 * Toda leitura/escrita é **filtrada pela conta corrente** ([accountScope]). Sem isso, trocar de
 * usuário no mesmo aparelho (van que passa ao motorista substituto, tablet de equipe, celular de
 * loja) vazava dado entre contas na LEITURA e — pior — na ESCRITA: o ciclo faz PUSH antes do PULL,
 * então a outbox do usuário A subia inteira **para a conta de B** no servidor, de forma permanente.
 *
 * O isolamento é por **bucket**, não por apagamento: cada conta tem o seu espelho e a sua outbox, e
 * **trocar de conta e voltar preserva a fila pendente de cada uma**. O app declara o titular no
 * bootstrap/login — **antes** de qualquer tela coletar e antes do motor de sync arrancar:
 *
 * ```kotlin
 * store.setAccountScope(session.accountId)   // isola; NÃO apaga nada
 * engine.start()
 * ```
 *
 * ### Handle estável + remap durável (2.93.0 — GAP-KL-M-RESTCRUD-IDMIGRATION)
 * `client_id` é a **âncora permanente** da linha: ele nunca é sobrescrito pelo id do servidor, e
 * [getByHandle]/[observeVisibleByHandle] reencontram o registro por `local_id` **ou** `client_id`
 * **ou** `server_id` — o id que o app carregou na navegação continua valendo depois de o id migrar.
 * Em paralelo, [rememberServerId]/[resolveServerId] persistem a tradução `clientId → serverId`, que
 * antes só existia dentro de um ciclo de sync (e por isso se perdia entre pai e filho).
 *
 * Todas as APIs abaixo têm **implementação default** (no-op/compat) para não quebrar os `SyncStore`
 * falsos que os apps já mantêm em `commonTest`.
 */
interface SyncStore {

    // -- Escopo de conta ----------------------------------------------------

    /**
     * Conta dona do escopo corrente ([NO_ACCOUNT] = "sem escopo", bucket legado). Reativo: as
     * leituras por `Flow` acompanham a troca de titular automaticamente.
     */
    val accountScope: StateFlow<String> get() = NO_ACCOUNT_SCOPE

    /**
     * Define a conta dona do espelho a partir de agora. Não apaga nada da conta anterior — o dado
     * dela apenas deixa de ser visível/drenável até ela voltar.
     *
     * @param accountId id **opaco** da conta (nunca e-mail/CPF — o valor é persistido em claro no
     *   SQLite do aparelho). `null`/vazio volta ao bucket sem escopo.
     * @param legacy o que fazer com as linhas gravadas **antes** desta versão da lib
     *   (`account_id = ''`) — ver [LegacyRowsPolicy]. Só tem efeito na primeira vez que uma conta
     *   reivindica o bucket legado.
     */
    fun setAccountScope(accountId: String?, legacy: LegacyRowsPolicy = LegacyRowsPolicy.Adopt) {
        AppLogger.w(
            "SyncStore",
            "setAccountScope ignorado: impl sem escopo de conta (${this::class.simpleName}).",
        )
    }

    /** Quantas linhas ainda estão no bucket legado (sem escopo). Diagnóstico de migração. */
    fun countLegacyRows(): Long = 0L

    /**
     * Apaga o espelho + outbox de **uma** conta (exclusão de conta/LGPD), preservando as demais.
     * Distinto de [deleteAll], que zera o aparelho inteiro.
     */
    fun deleteAccountData(accountId: String) = Unit

    // -- Leitura reativa (Flow) --------------------------------------------
    fun observeVisible(entity: String): Flow<List<Synced_entity>>
    fun observeVisibleById(entity: String, localId: String): Flow<Synced_entity?>
    fun observeAllDirty(): Flow<List<Synced_entity>>

    /**
     * Observa a linha visível por **handle estável** (2.93.0): casa por `local_id` **OU**
     * `client_id` **OU** `server_id`, e por isso **continua emitindo a mesma linha depois de o id
     * migrar** de local para servidor.
     *
     * É o que permite a UI carregar na navegação o id que recebeu no `create` offline e seguir
     * consultando por ele para sempre. Antes, o drain apagava a linha do id local e reinseria sob o
     * id do servidor: a tela aberta com o id local ficava **vazia** no meio do uso.
     *
     * Default (compat): [observeVisibleById] — segue funcionando, sem acompanhar a migração.
     */
    fun observeVisibleByHandle(entity: String, handle: String): Flow<Synced_entity?> =
        observeVisibleById(entity, handle)

    // -- Leitura pontual ----------------------------------------------------
    /** Lista (síncrona) todos os registros visíveis (não-deletados) de uma entidade. */
    fun getVisible(entity: String): List<Synced_entity>
    fun getByLocalId(entity: String, localId: String): Synced_entity?
    fun getByServerId(entity: String, serverId: String): Synced_entity?
    fun getByClientId(entity: String, clientId: String): Synced_entity?

    /**
     * Busca a linha por **handle estável** (2.93.0): `local_id` **OU** `client_id` **OU**
     * `server_id`. Versão síncrona da [observeVisibleByHandle] (inclui linhas em tombstone).
     *
     * Default (compat): a composição das três buscas — correta em qualquer impl, inclusive nos
     * `SyncStore` falsos que os apps mantêm em `commonTest`.
     */
    fun getByHandle(entity: String, handle: String): Synced_entity? =
        getByLocalId(entity, handle) ?: getByServerId(entity, handle) ?: getByClientId(entity, handle)

    fun getDirty(entity: String): List<Synced_entity>
    fun getAllDirty(): List<Synced_entity>
    fun countDirty(): Long

    /**
     * Outbox **drenável**: sujos que ainda NÃO foram recusados de forma terminal pelo servidor
     * (`failed = 0`). É o que o push consome; uma linha recusada só volta por retry explícito do app
     * ([clearFailed]) — retentar 4xx de validação em loop nunca converge.
     *
     * Default (compat): [getDirty] filtrado por `failed == 0L`.
     */
    fun getDrainable(entity: String): List<Synced_entity> = getDirty(entity).filter { it.failed == 0L }

    /** Linhas recusadas pelo servidor (4xx terminal) — a UI exibe e oferece nova tentativa. */
    fun getFailed(entity: String): List<Synced_entity> = getDirty(entity).filter { it.failed != 0L }

    // -- Escrita ------------------------------------------------------------
    fun upsert(row: Synced_entity)
    fun markClean(entity: String, localId: String, serverId: String?, updatedAt: String?, baseUpdatedAt: String?)
    fun setError(entity: String, localId: String, error: String?)
    fun deleteHard(entity: String, localId: String)

    /** Apaga TUDO, de TODAS as contas (wipe do aparelho). Para uma conta só, use [deleteAccountData]. */
    fun deleteAll()
    fun transaction(body: () -> Unit)

    /**
     * Marca a linha como **recusada de forma terminal** pelo servidor (4xx de validação, 403…):
     * sai da outbox drenável, conta a tentativa e **preserva o erro** para o app exibir. Um delete
     * recusado volta a ficar visível (o registro continua existindo no servidor).
     *
     * Default (compat): grava só a mensagem via [setError].
     */
    fun markFailed(entity: String, localId: String, code: Int, error: String?) =
        setError(entity, localId, error)

    /** Retry explícito: devolve a linha à outbox drenável, preservando o payload local. */
    fun clearFailed(entity: String, localId: String) = setError(entity, localId, null)

    /** Falha **retentável** (5xx/timeout/rede): conta a tentativa e guarda o erro, sem sair da outbox. */
    fun bumpAttempt(entity: String, localId: String, error: String?) = setError(entity, localId, error)

    // -- Remap durável clientId → serverId (2.93.0) -------------------------

    /**
     * Registra que o id local [clientId] passou a ser conhecido no servidor como [serverId].
     * Gravado no **mesmo instante** em que a linha migra de id ([markClean]/`markSynced`).
     *
     * Existe porque o remap vivia numa variável do ciclo de sync: um registro **filho** que não
     * drenasse junto do pai perdia a tradução e subia com a FK apontando para o id local — o
     * backend recusava (`FOREIGN KEY`/UUID), a recusa era terminal e o dado ficava perdido para
     * sempre. Persistido, o mapeamento sobrevive a ciclos, a **reinício de processo** e a
     * drenagem parcial (queda de sinal no meio do drain).
     *
     * [clientId] é único no aparelho ([newRestClientId][br.com.codecacto.kmplib.sync.rest.newRestClientId]),
     * por isso a resolução é **cross-entity** — que é justamente o caso de uso (o filho resolve o
     * id do PAI, de outra entidade).
     */
    fun rememberServerId(entity: String, clientId: String, serverId: String) = Unit

    /** Id do servidor conhecido para um id local. `null` se ele nunca migrou (ou já é um id do servidor). */
    fun resolveServerId(clientId: String): String? = null

    /** Id local com que um registro do servidor nasceu neste aparelho, se foi criado aqui. */
    fun resolveClientId(serverId: String): String? = null

    /** Quantos mapeamentos duráveis existem na conta corrente (guarda barata: `0` ⇒ nada a remapear). */
    fun countIdRemap(): Long = 0L

    /** Esquece um mapeamento (o registro deixou de existir dos dois lados). */
    fun forgetServerId(clientId: String) = Unit

    // -- Cursor -------------------------------------------------------------
    fun getCursor(entity: String): String?
    fun setCursor(entity: String, cursor: String?)

    companion object {
        /** Escopo "sem conta" — bucket legado (base criada antes do escopo existir). */
        const val NO_ACCOUNT: String = ""

        /**
         * Cria o espelho default sobre SQLDelight. Mantém o call-site histórico
         * `SyncStore(createSyncDatabase())`.
         */
        operator fun invoke(
            db: SyncDatabase,
            ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
        ): SyncStore = SqlDelightSyncStore(db, ioDispatcher)
    }
}

/** Escopo imutável "sem conta" — default das impls que não implementam escopo. */
private val NO_ACCOUNT_SCOPE: StateFlow<String> = MutableStateFlow(SyncStore.NO_ACCOUNT).asStateFlow()

/**
 * O que fazer com as linhas gravadas **antes** de o espelho ter escopo de conta (`account_id = ''`),
 * quando a primeira conta reivindica o aparelho.
 *
 * Existe porque a migração não pode dropar a base dos apps em produção: o dado local é a única cópia
 * do que ainda não subiu.
 */
enum class LegacyRowsPolicy {
    /**
     * **Default.** A conta que entra **adota** o bucket legado (uma vez só). Preserva o espelho e,
     * sobretudo, a **outbox** de quem estava usando o app quando ele foi atualizado — que é o dado
     * insubstituível. É o comportamento correto para o caso normal (o app foi atualizado embaixo do
     * mesmo usuário).
     */
    Adopt,

    /**
     * Mantém o bucket legado **intacto e invisível**. Nada é apagado e nada é adotado: o espelho da
     * conta que entra nasce vazio e é repovoado pelo primeiro `refresh()`. Escolha para aparelho
     * **compartilhado** com dado sensível, em que adotar poderia atribuir o dado do usuário anterior
     * a quem entrou agora.
     */
    Isolate,

    /**
     * Apaga o bucket legado. Fail-closed: para quem prefere perder o cache local a correr o risco de
     * misturar contas (era o que o contorno `MirrorOwnerGuard` fazia no "Todos a Bordo").
     */
    Discard,
}

/**
 * Impl default do [SyncStore] sobre as queries do [SyncDatabase] — isola o SQLDelight do
 * [DefaultSyncEngine]/[SyncableRepository] e centraliza a conversão de tipos
 * (Long↔Boolean do espelho). Não contém regra de sync; só persistência local.
 */
class SqlDelightSyncStore(
    db: SyncDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SyncStore {
    private val q = db.syncEntityQueries

    private val _accountScope = MutableStateFlow(SyncStore.NO_ACCOUNT)
    override val accountScope: StateFlow<String> = _accountScope.asStateFlow()

    private val account: String get() = _accountScope.value

    override fun setAccountScope(accountId: String?, legacy: LegacyRowsPolicy) {
        val titular = accountId?.trim().orEmpty()
        if (titular.isEmpty()) {
            _accountScope.value = SyncStore.NO_ACCOUNT
            return
        }
        val legadas = q.countLegacy().executeAsOne()
        if (legadas > 0L) {
            // Nunca logar o accountId (é identidade do usuário) — só o efeito.
            when (legacy) {
                LegacyRowsPolicy.Adopt -> q.transaction {
                    q.adoptLegacy(titular)
                    q.adoptLegacyCursors(titular)
                    q.adoptLegacyRemap(titular)
                }
                LegacyRowsPolicy.Discard -> q.transaction {
                    q.deleteAccount(SyncStore.NO_ACCOUNT)
                    q.deleteAccountCursors(SyncStore.NO_ACCOUNT)
                    q.deleteAccountRemap(SyncStore.NO_ACCOUNT)
                }
                LegacyRowsPolicy.Isolate -> Unit
            }
            AppLogger.i(TAG, "escopo de conta aplicado ($legacy) sobre $legadas linha(s) legada(s).")
        }
        _accountScope.value = titular
    }

    override fun countLegacyRows(): Long = q.countLegacy().executeAsOne()

    override fun deleteAccountData(accountId: String) {
        q.transaction {
            q.deleteAccount(accountId)
            q.deleteAccountCursors(accountId)
            q.deleteAccountRemap(accountId)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeVisible(entity: String): Flow<List<Synced_entity>> =
        _accountScope.flatMapLatest { acc ->
            q.selectAllVisible(acc, entity).asFlow().mapToList(ioDispatcher)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeVisibleById(entity: String, localId: String): Flow<Synced_entity?> =
        _accountScope.flatMapLatest { acc ->
            q.selectVisibleById(acc, entity, localId).asFlow().mapToOneOrNull(ioDispatcher)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeVisibleByHandle(entity: String, handle: String): Flow<Synced_entity?> =
        _accountScope.flatMapLatest { acc ->
            q.selectVisibleByHandle(acc, entity, handle).asFlow().mapToOneOrNull(ioDispatcher)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAllDirty(): Flow<List<Synced_entity>> =
        _accountScope.flatMapLatest { acc ->
            q.selectAllDirty(acc).asFlow().mapToList(ioDispatcher)
        }

    override fun getVisible(entity: String): List<Synced_entity> =
        q.selectAllVisible(account, entity).executeAsList()

    override fun getByLocalId(entity: String, localId: String): Synced_entity? =
        q.selectByLocalId(account, entity, localId).executeAsOneOrNull()

    override fun getByServerId(entity: String, serverId: String): Synced_entity? =
        q.selectByServerId(account, entity, serverId).executeAsOneOrNull()

    override fun getByClientId(entity: String, clientId: String): Synced_entity? =
        q.selectByClientId(account, entity, clientId).executeAsOneOrNull()

    override fun getByHandle(entity: String, handle: String): Synced_entity? =
        q.selectByHandle(account, entity, handle).executeAsOneOrNull()

    override fun getDirty(entity: String): List<Synced_entity> =
        q.selectDirty(account, entity).executeAsList()

    override fun getDrainable(entity: String): List<Synced_entity> =
        q.selectDrainable(account, entity).executeAsList()

    override fun getFailed(entity: String): List<Synced_entity> =
        q.selectFailed(account, entity).executeAsList()

    override fun getAllDirty(): List<Synced_entity> =
        q.selectAllDirty(account).executeAsList()

    override fun countDirty(): Long = q.countDirty(account).executeAsOne()

    override fun upsert(row: Synced_entity) {
        q.upsert(
            accountId = account,
            entity = row.entity,
            localId = row.local_id,
            serverId = row.server_id,
            clientId = row.client_id,
            payloadJson = row.payload_json,
            updatedAt = row.updated_at,
            dirty = row.dirty,
            pendingOp = row.pending_op,
            deleted = row.deleted,
            baseUpdatedAt = row.base_updated_at,
            lastError = row.last_error,
            failed = row.failed,
            failCode = row.fail_code,
            attempts = row.attempts,
        )
    }

    override fun markClean(entity: String, localId: String, serverId: String?, updatedAt: String?, baseUpdatedAt: String?) {
        q.markClean(serverId, updatedAt, baseUpdatedAt, account, entity, localId)
    }

    override fun setError(entity: String, localId: String, error: String?) {
        q.setError(error, account, entity, localId)
    }

    override fun markFailed(entity: String, localId: String, code: Int, error: String?) {
        q.markFailed(code.toLong(), error, account, entity, localId)
    }

    override fun clearFailed(entity: String, localId: String) {
        q.clearFailed(account, entity, localId)
    }

    override fun bumpAttempt(entity: String, localId: String, error: String?) {
        q.bumpAttempt(error, account, entity, localId)
    }

    override fun deleteHard(entity: String, localId: String) {
        q.deleteHard(account, entity, localId)
    }

    override fun rememberServerId(entity: String, clientId: String, serverId: String) {
        if (clientId.isBlank() || serverId.isBlank() || clientId == serverId) return
        q.rememberRemap(account, clientId, serverId, entity)
    }

    override fun resolveServerId(clientId: String): String? =
        q.selectRemapByClientId(account, clientId).executeAsOneOrNull()

    override fun resolveClientId(serverId: String): String? =
        q.selectRemapByServerId(account, serverId).executeAsOneOrNull()

    override fun countIdRemap(): Long = q.countRemap(account).executeAsOne()

    override fun forgetServerId(clientId: String) {
        q.forgetRemap(account, clientId)
    }

    override fun deleteAll() {
        q.deleteAll()
        q.clearCursors()
        q.clearRemap()
    }

    override fun transaction(body: () -> Unit) {
        q.transaction { body() }
    }

    override fun getCursor(entity: String): String? =
        q.getCursor(account, entity).executeAsOneOrNull()?.cursor

    override fun setCursor(entity: String, cursor: String?) {
        q.setCursor(account, entity, cursor)
    }

    private companion object {
        const val TAG = "SyncStore"
    }
}

/** Helpers de conversão Long↔Boolean do espelho. */
internal fun Boolean.toDbLong(): Long = if (this) 1L else 0L
internal fun Long.toDbBoolean(): Boolean = this != 0L
