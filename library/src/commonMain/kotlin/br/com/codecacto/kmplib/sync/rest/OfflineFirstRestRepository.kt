package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.core.util.currentTimeMillis
import br.com.codecacto.kmplib.sync.SyncOpType
import br.com.codecacto.kmplib.sync.SyncStore
import kotlinx.coroutines.flow.Flow
import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Descreve como uma entidade de domínio participa da CRUD offline-first REST — sem acoplar a lib ao
 * domínio (paralelo ao [SyncableEntity][br.com.codecacto.kmplib.sync.SyncableEntity] do engine
 * `/pull`+`/push`, mas para backends **REST-CRUD**). O app implementa uma instância por tipo.
 *
 * O modelo de domínio [T] é o que fica no espelho (payload). O corpo enviado ao backend ([encodeBody])
 * e a resposta lida dele ([decodeModel]/[decodeList]) podem ser DTOs de fio distintos — o app faz o
 * mapeamento aqui (ex.: `Empresa` ↔ `UpsertEmpresaRequest`/`EmpresaResponse`).
 *
 * @param T modelo de domínio (`@Serializable`).
 */
interface RestCrudEntity<T : Any> {
    /** Nome lógico estável da entidade (chave no espelho). Ex.: "empresa". */
    val name: String

    /** Serializer kotlinx do modelo — usado para o `payload_json` do espelho. */
    val serializer: KSerializer<T>

    /** Id canônico do modelo (server id quando sincronizado; client id offline). */
    fun idOf(model: T): String

    /** Corpo JSON para `POST` (create) e `PUT` (update). Tipicamente o "upsert" do app. */
    fun encodeBody(model: T): String

    /** Decodifica a resposta (item) do servidor em modelo de domínio. */
    fun decodeModel(body: String): T

    /** Decodifica a resposta de lista do servidor em modelos de domínio. */
    fun decodeList(body: String): List<T>

    /**
     * Decodifica a resposta de lista **com os metadados de paginação** do envelope
     * (`PageResponse { data, page, size, total }`) num [RestPage]. É o que permite o
     * [OfflineFirstRestRepository.refresh] **paginar o dataset COMPLETO** antes de reconciliar: no
     * offline-first o espelho local precisa conter TODOS os registros (senão o app não funciona
     * offline e o `deleteHard` da [RestEntityMirror.reconcile] apagaria tudo além da 1ª página).
     *
     * **Retrocompatível:** o default deriva de [decodeList] com metadados **desconhecidos** (`null`).
     * Nesse caso o `refresh` ainda pagina, parando quando uma página vem **incompleta**
     * (`items.size < size`) — o que basta para backends que devolvem `?page=&size=` corretamente.
     * Descritores cujo backend expõe o envelope devem **sobrescrever** para também devolver
     * `page`/`size`/`total`, habilitando a parada precisa por `total` (e evitando 1 GET extra).
     */
    fun decodePage(body: String): RestPage<T> = RestPage(items = decodeList(body))

    /**
     * Produz a instância local para um create **offline**: aplica o [clientId] gerado e quaisquer
     * timestamps provisórios (`createdAt`/`updatedAt`). O id migra para o do servidor no drain.
     */
    fun withLocalId(model: T, clientId: String): T

    /**
     * Aplica um id ao modelo. Usado quando o app opera um registro pelo **handle** que carregou na
     * navegação e o id já migrou para o do servidor: a lib normaliza o modelo para o id canônico
     * antes de falar com a rede.
     *
     * Default: delega a [withLocalId] — que já é exatamente "copie o modelo com este id". Sobrescreva
     * só se aplicar um client id envolver algo a mais (ex.: carimbar `createdAt` provisório).
     */
    fun withId(model: T, id: String): T = withLocalId(model, id)

    /**
     * Remapeia as **FKs pendentes** do modelo antes do push (clientId local do pai → serverId), a
     * partir do [remap] acumulado no ciclo de sync. Ex.: um lançamento criado offline referencia uma
     * empresa também criada offline — quando a empresa é confirmada, seu clientId vira serverId e este
     * hook corrige a FK antes de enviar o lançamento. Default: identidade (entidade sem FKs).
     *
     * **Desde a 2.93.0 o [remap] recebido também enxerga o remap DURÁVEL** (mapeamentos de ciclos
     * anteriores e de outras execuções do app), não só o do ciclo corrente — e, mesmo quando este
     * hook não é implementado, o corpo enviado passa por uma tradução genérica
     * ([RestPayloadRemap]). Nenhum app precisa mudar para receber a correção.
     */
    fun remapRefs(model: T, remap: Map<String, String>): T = model
}

/**
 * Uma página de uma resposta de lista REST-CRUD: os [items] já decodificados + os metadados de
 * paginação do envelope (`PageResponse { data, page, size, total }`), quando o backend os expõe.
 * Devolvido por [RestCrudEntity.decodePage] para o [OfflineFirstRestRepository] paginar o dataset
 * completo (refresh) ou dirigir uma paginação server-side pura ([OfflineFirstRestRepository.refreshPage]).
 *
 * @param items itens desta página (o `.data` do envelope, já como modelo de domínio).
 * @param page número 1-based desta página (`null` se o descriptor não expõe — cai no fallback por tamanho).
 * @param size tamanho de página **do servidor** (`null` se desconhecido).
 * @param total total de registros no servidor (`null` se desconhecido).
 */
data class RestPage<T>(
    val items: List<T>,
    val page: Int? = null,
    val size: Int? = null,
    val total: Long? = null,
) {
    /**
     * `true`/`false` se há mais páginas depois desta (requer [page]/[size]/[total]); `null` se os
     * metadados não vieram no envelope (paginação por tamanho de página).
     */
    val hasNextPage: Boolean?
        get() = if (page != null && size != null && total != null) page.toLong() * size < total else null
}

/**
 * **Repositório genérico offline-first sobre um backend REST-CRUD.** Peça de topo do bloco promovido na
 * kmplib 2.63.0 (piloto MinhasHoras da Onda 3), reusável pelos ~14 apps que migram Firestore → backend
 * REST-CRUD central e precisavam reescrever esta camada à mão.
 *
 * - **Leitura** serve do **espelho local** ([RestEntityMirror]/SQLDelight) — funciona offline; a UI
 *   nunca espera rede.
 * - **Escrita otimista + sync:** [create]/[update]/[delete] — ver [RestWriteMode] para os dois
 *   caminhos ([RestWriteMode.OnlineFirst], default, e [RestWriteMode.LocalFirst]).
 * - **Reconciliação por GET** ([refresh]) e **remap de FK** (id temporário local → id do servidor) no
 *   [drainOutbox], via [RestCrudEntity.remapRefs].
 *
 * ### A escrita NUNCA some (2.91.0 — GAP-KL-M-RESTCRUD-LOCALFIRST)
 * Até a 2.90.0 só a falha de **transporte** (código sentinela [DomainResult.OFFLINE_CODE]) caía na
 * outbox: com rede presente e servidor respondendo **5xx/timeout**, a escrita do usuário
 * **desaparecia**. Agora toda falha é classificada ([classifyRestFailure]):
 *
 * | Falha | O que acontece com a escrita |
 * |---|---|
 * | transporte / 5xx / 408 / 429 / 401 pós-refresh | vai para a **outbox** e retenta (devolve `Success`) |
 * | 4xx de validação / 403 / 404 | `OnlineFirst`: nada persistido, devolve `Error` (o formulário corrige) · `LocalFirst`: linha fica [RestRowState.Failed], com o erro **preservado e visível** |
 * | **402** | `DomainResult.Quota` (Paywall) — semântica intacta |
 *
 * ### O registro criado offline sobe como CREATE (2.92.0 — GAP-KL-M-RESTCRUD-PENDINGOP)
 * A operação pendente de uma linha é derivada do **estado dela** ([resolveOutboxOp]), não da última
 * chamada: enquanto `server_id == null` a linha **nunca existiu no servidor**, então `update()` só
 * troca o payload (a operação segue `CREATE`) e `delete()` a apaga **localmente**, sem tocar a rede.
 * Antes, o primeiro `update()` sobre um registro criado offline trocava `CREATE` por `UPDATE` e o
 * drain fazia `PUT /…/local-…` → **404** → linha marcada como recusada: a execução offline inteira
 * (iniciar a rota sem rede e marcar embarques) nunca subia. O drain **cura** linhas já gravadas
 * assim por versões anteriores.
 *
 * ### O id migra; o handle do app, não (2.93.0 — GAP-KL-M-RESTCRUD-IDMIGRATION)
 * Um registro criado offline nasce com id local e ganha o id do servidor quando sincroniza. Até a
 * 2.92.0 essa migração **quebrava tudo que apontasse para o id local**:
 * - a tela aberta com o id local (congelado no back stack) passava a consultar uma linha que já não
 *   existia — no "Todos a Bordo", as telas de execução mostravam "nenhum passageiro" e a conferência
 *   final calculava sobre lista vazia, dando "Tudo certo!" com as crianças ainda no veículo;
 * - um **filho** que não drenasse no mesmo ciclo do pai subia a FK com o id local (o remap vivia
 *   numa variável do ciclo), o backend recusava por `FOREIGN KEY`/UUID, a recusa era **terminal** e
 *   o registro ficava `Failed` para sempre — perda definitiva de dado.
 *
 * Agora: `client_id` é a **âncora permanente** da linha (nunca é sobrescrito pelo id do servidor) e
 * toda leitura/escrita aceita **handle** — o id que o app recebeu no [create] continua valendo para
 * [observeById]/[getCached]/[stateOf]/[update]/[delete] depois da migração. A tradução
 * `clientId → serverId` é gravada de forma **durável** no instante da migração, então sobrevive a
 * ciclos, a drenagem parcial e a reinício de processo. Para correlacionar **filhos**, use
 * [observeCanonicalId] (reativo) ou [ids]`.same(...)`.
 *
 * ### Estado por linha (a UI não precisa de overlay próprio)
 * [observeAllWithState]/[stateOf] expõem `pendente / falhou / sincronizado` por registro
 * ([RestRowState]); [requeueFailed]/[discardFailed] resolvem a linha recusada. Antes disso cada app
 * inventava um overlay em memória — que morria com o processo, levando junto a escrita recusada.
 *
 * ### Padrão de uso (composição — recomendado)
 * O app **compõe** este repositório dentro do seu repositório de domínio (que implementa a interface
 * usada pelos ViewModels) e traduz [DomainResult] → `Result`/exceções do app (ex.: `Quota` →
 * `QuotaException`). Endpoints **custom** (ex.: `PATCH /.../status`) usam [mirror] + o [DomainApiClient]
 * diretamente. Registre no [RestCrudSyncEngine] passando os repositórios **na ordem de dependência**
 * (pais primeiro), para o remap de FK fluir.
 *
 * @param api cliente REST de domínio (Bearer Firebase + 401-refresh + 402→quota).
 * @param descriptor descritor da entidade (mapeamento domínio ↔ fio).
 * @param store espelho local (SQLDelight) — 1 por app, compartilhado entre entidades.
 * @param collectionPath caminho da coleção (sem barra final). Ex.: "/v1/empresas".
 * @param json Json tolerante do espelho (default [restMirrorJson]).
 * @param clientIdFactory gerador de id local para create offline (default [newRestClientId]).
 * @param refreshPageSize tamanho de página usado pelo [refresh] paginado (`?size=`). Default
 *   [DEFAULT_REFRESH_PAGE_SIZE] = teto do contrato REST-CRUD (100). **Não deve exceder o máximo de
 *   página do backend** (senão o servidor clampa e a parada por "página incompleta" dispararia cedo).
 * @param writeMode caminho da escrita — ver [RestWriteMode]. Default [RestWriteMode.OnlineFirst]
 *   (compatível). Use [RestWriteMode.LocalFirst] quando o toque do usuário **é o registro**.
 */
open class OfflineFirstRestRepository<T : Any>(
    protected val api: DomainApiClient,
    protected val descriptor: RestCrudEntity<T>,
    protected val store: SyncStore,
    protected val collectionPath: String,
    protected val json: Json = restMirrorJson,
    private val clientIdFactory: () -> String = ::newRestClientId,
    protected val refreshPageSize: Int = DEFAULT_REFRESH_PAGE_SIZE,
    protected val writeMode: RestWriteMode = RestWriteMode.OnlineFirst,
) : RestCrudSyncParticipant {

    /** Espelho local da entidade — exposto para os endpoints custom do app reconciliarem o cache. */
    val mirror: RestEntityMirror<T> = RestEntityMirror(
        name = descriptor.name,
        store = store,
        serializer = descriptor.serializer,
        idOf = descriptor::idOf,
        json = json,
    )

    /**
     * Tradutor de identidade (`id local ⇄ id do servidor`) — use no lugar de `==` ao comparar ids que
     * podem ter migrado (tipicamente a **FK de um filho** contra o handle do pai). Ver [RestIdResolver].
     */
    val ids: RestIdResolver = RestIdResolver(store)

    private val collection: String = "/" + collectionPath.trim('/')
    protected fun itemPath(id: String): String = "$collection/$id"

    /** Caminho da coleção com paginação (`?page=&size=`) — teto do contrato: `size` 1..100. */
    protected fun pagedPath(page: Int, size: Int): String {
        val sep = if (collection.contains('?')) '&' else '?'
        return "$collection${sep}page=$page&size=$size"
    }

    // -- Leitura (espelho, offline) ----------------------------------------

    fun observeAll(): Flow<List<T>> = mirror.observeVisible()

    /**
     * Observa um registro pelo seu **handle estável** — o id que o app recebeu no [create] e carrega
     * na navegação. Continua emitindo o mesmo registro **depois** de o id migrar de local para
     * servidor (2.93.0); antes, a tela aberta com um id local esvaziava no meio do uso, assim que o
     * drain migrava o id.
     */
    fun observeById(id: String): Flow<T?> = mirror.observeVisibleById(id)

    fun getCached(id: String): T? = mirror.get(id)
    fun getAllCached(): List<T> = mirror.getVisible()

    // -- Identidade estável (2.93.0) ---------------------------------------

    /**
     * Id **canônico** de um handle: o id do servidor quando o registro já subiu, o id local enquanto
     * não subiu.
     *
     * Use ao montar a URL de um endpoint **custom** do app (`PATCH /v1/rotas/{id}/encerrar`) e como
     * chave de correlação de **filhos** — o app nunca precisa saber *quando* a migração aconteceu,
     * só perguntar o id corrente.
     */
    fun canonicalId(handle: String): String = mirror.canonicalIdOf(handle)

    /**
     * [canonicalId] **reativo**: emite o handle enquanto o registro é só local e o id do servidor
     * assim que ele migra. É a forma correta de uma tela de execução consultar filhos:
     * ```kotlin
     * rotaRepo.observeCanonicalId(rotaHandle)
     *     .flatMapLatest { rotaId -> passageiroRepo.observeAll().map { l -> l.filter { it.rotaId == rotaId } } }
     * ```
     */
    fun observeCanonicalId(handle: String): Flow<String> = mirror.observeCanonicalId(handle)

    // -- Estado de escrita por linha (2.91.0) ------------------------------

    /**
     * Espelho visível **com o estado de sync de cada linha** (`pendente / falhou / sincronizado`) —
     * é o que permite a lista mostrar "enviando…" e "não salvo" **sem overlay em memória** no app.
     */
    fun observeAllWithState(): Flow<List<RestRow<T>>> = mirror.observeVisibleWithState()

    /** Uma linha visível com o seu estado de sync. */
    fun observeByIdWithState(id: String): Flow<RestRow<T>?> = mirror.observeVisibleWithStateById(id)

    fun getAllCachedWithState(): List<RestRow<T>> = mirror.getVisibleWithState()

    /** Estado de sync de uma linha (síncrono). [RestRowState.Synced] se ela não existe localmente. */
    fun stateOf(id: String): RestRowState = mirror.stateOf(id)

    /** Linhas que o servidor **recusou** (4xx terminal ou 402), com o erro preservado. */
    fun failedRows(): List<RestRow<T>> = mirror.failedRows()

    /**
     * Retry **explícito** de uma linha recusada: devolve-a à outbox drenável preservando o conteúdo
     * local. O envio acontece no próximo ciclo — chame `engine.syncNow()` em seguida para tentar já.
     *
     * @return `false` se não havia linha recusada com esse id.
     */
    fun requeueFailed(id: String): Boolean {
        if (!stateOf(id).isFailed) return false
        mirror.clearFailure(id)
        return true
    }

    /** Retry explícito de **todas** as linhas recusadas desta entidade. Devolve quantas voltaram à fila. */
    fun requeueAllFailed(): Int {
        val recusadas = mirror.failedRows().map { descriptor.idOf(it.model) }
        recusadas.forEach { mirror.clearFailure(it) }
        return recusadas.size
    }

    /**
     * Descarta uma escrita recusada (o usuário desistiu de corrigir): remove a linha local. Um
     * `create` recusado simplesmente some (nunca existiu no servidor); um `update`/`delete` recusado
     * volta na versão do servidor no próximo [refresh].
     *
     * @return `false` se não havia linha recusada com esse id.
     */
    fun discardFailed(id: String): Boolean {
        if (!stateOf(id).isFailed) return false
        mirror.removeHard(id)
        return true
    }

    /** Cache-first: se não estiver no espelho, busca por `GET {collection}/{id}`. */
    suspend fun getById(id: String): DomainResult<T?> {
        mirror.get(id)?.let { return DomainResult.Success(it) }
        return when (val r = api.getJson(itemPath(canonicalId(id)))) {
            is DomainResult.Success -> DomainResult.Success(decodeOrNull(r.data))
            is DomainResult.Quota -> r
            is DomainResult.Error -> if (r.code == 404) DomainResult.Success(null) else r
        }
    }

    // -- Escrita otimista + sync -------------------------------------------

    /**
     * Cria o registro. Em [RestWriteMode.OnlineFirst] chama o servidor primeiro e grava LIMPO o que
     * ele devolveu; em [RestWriteMode.LocalFirst] grava na outbox **antes** de tocar a rede e
     * reconcilia depois. Em ambos, falha **retentável** (rede/5xx/timeout) fica na outbox e devolve
     * `Success` com o modelo local — a escrita nunca some.
     */
    suspend fun create(model: T): DomainResult<T> =
        if (writeMode == RestWriteMode.LocalFirst) createLocalFirst(model) else createOnlineFirst(model)

    private suspend fun createOnlineFirst(model: T): DomainResult<T> =
        when (val r = api.postJson(collection, descriptor.encodeBody(model))) {
            is DomainResult.Success -> {
                val saved = decodeOrNull(r.data) ?: return DomainResult.Error(INVALID_RESPONSE_CODE, INVALID_RESPONSE_MESSAGE)
                mirror.putClean(saved); DomainResult.Success(saved)
            }
            is DomainResult.Quota -> r
            is DomainResult.Error -> if (classifyRestFailure(r.code).isRetryable) {
                // Rede ausente OU servidor instável: a escrita vai para a outbox e converge sozinha.
                val local = descriptor.withLocalId(model, clientIdFactory())
                mirror.putDirty(local, SyncOpType.CREATE)
                if (!r.isOffline) mirror.bumpAttempt(descriptor.idOf(local), r.message)
                DomainResult.Success(local)
            } else {
                r // terminal/402: nada persistido — o formulário mostra o erro e corrige.
            }
        }

    private suspend fun createLocalFirst(model: T): DomainResult<T> {
        val local = descriptor.withLocalId(model, clientIdFactory())
        val localId = descriptor.idOf(local)
        mirror.putDirty(local, SyncOpType.CREATE) // grava ANTES da rede: o toque já está salvo.
        return when (val r = api.postJson(collection, descriptor.encodeBody(local))) {
            is DomainResult.Success -> {
                val saved = decodeOrNull(r.data)
                if (saved == null) {
                    // O servidor aceitou, mas a resposta é ilegível: retentar duplicaria o registro.
                    mirror.markFailed(localId, INVALID_RESPONSE_CODE, INVALID_RESPONSE_MESSAGE)
                    DomainResult.Error(INVALID_RESPONSE_CODE, INVALID_RESPONSE_MESSAGE)
                } else {
                    mirror.markSynced(localId, saved)
                    DomainResult.Success(saved)
                }
            }
            is DomainResult.Quota -> { mirror.markFailed(localId, 402, quotaMessage(r)); r }
            is DomainResult.Error -> resolveWriteFailure(localId, local, r)
        }
    }

    /**
     * Atualiza o registro — mesma política de falha do [create] (ver [RestWriteMode]).
     *
     * **Linha ainda pendente de criação** (criada offline, `server_id == null`): não há o que
     * atualizar no servidor — um `PUT /…/local-…` renderia 404 e marcaria a escrita como recusada.
     * A alteração grava no espelho e a operação pendente **continua CREATE** ([resolveOutboxOp]); o
     * drain envia **um único POST** com o payload mais recente. Devolve `Success` com o modelo local,
     * porque a escrita **foi aceita** (o estado da linha é [RestRowState.Pending]).
     */
    suspend fun update(model: T): DomainResult<T> {
        val alvo = normalizeHandle(model)
        if (mirror.isLocalOnly(descriptor.idOf(alvo))) {
            mirror.putDirty(alvo, SyncOpType.UPDATE) // resolveOutboxOp preserva o CREATE pendente
            return DomainResult.Success(alvo)
        }
        return if (writeMode == RestWriteMode.LocalFirst) updateLocalFirst(alvo) else updateOnlineFirst(alvo)
    }

    /**
     * Normaliza um modelo cujo id é um **handle antigo** (o id local com que o registro nasceu) para
     * o id canônico atual. Sem isto, o app que guardou o modelo em memória antes do sync faria
     * `PUT /…/local-…` — 404 — mesmo com o registro já existindo no servidor.
     */
    private fun normalizeHandle(model: T): T {
        val handle = descriptor.idOf(model)
        val canonico = mirror.canonicalIdOf(handle)
        return if (canonico == handle) model else descriptor.withId(model, canonico)
    }

    private suspend fun updateOnlineFirst(model: T): DomainResult<T> =
        when (val r = api.putJson(itemPath(descriptor.idOf(model)), descriptor.encodeBody(model))) {
            is DomainResult.Success -> {
                val saved = decodeOrNull(r.data) ?: model
                mirror.confirm(saved); DomainResult.Success(saved)
            }
            is DomainResult.Quota -> r
            is DomainResult.Error -> if (classifyRestFailure(r.code).isRetryable) {
                mirror.putDirty(model, SyncOpType.UPDATE)
                if (!r.isOffline) mirror.bumpAttempt(descriptor.idOf(model), r.message)
                DomainResult.Success(model)
            } else {
                r
            }
        }

    private suspend fun updateLocalFirst(model: T): DomainResult<T> {
        val id = descriptor.idOf(model)
        mirror.putDirty(model, SyncOpType.UPDATE) // grava ANTES da rede.
        return when (val r = api.putJson(itemPath(id), descriptor.encodeBody(model))) {
            is DomainResult.Success -> {
                val saved = decodeOrNull(r.data) ?: model
                mirror.confirm(saved); DomainResult.Success(saved)
            }
            is DomainResult.Quota -> { mirror.markFailed(id, 402, quotaMessage(r)); r }
            is DomainResult.Error -> resolveWriteFailure(id, model, r)
        }
    }

    /**
     * Exclui o registro. 404 = já não existe no servidor ⇒ remove local e devolve sucesso. Falha
     * retentável vira **tombstone** (replay no reconnect). Em [RestWriteMode.LocalFirst] o tombstone
     * é gravado **antes** da rede e uma recusa terminal **reexibe** a linha marcada como não-salva —
     * o registro continua existindo no servidor, e esconder isso seria mentir para o usuário.
     *
     * **Linha que nunca subiu** (criada offline, `server_id == null`): a exclusão é **local e
     * imediata**, sem tocar a rede — o servidor não conhece o id `local-…` e só devolveria um 404
     * previsível (que a máquina de estados leria como recusa terminal).
     */
    suspend fun delete(handle: String): DomainResult<Unit> {
        if (mirror.isLocalOnly(handle)) {
            mirror.removeHard(handle)
            return DomainResult.Success(Unit)
        }
        val id = canonicalId(handle)
        if (writeMode == RestWriteMode.LocalFirst) mirror.tombstone(id)
        return when (val r = api.delete(itemPath(id))) {
            is DomainResult.Success -> { mirror.removeHard(id); DomainResult.Success(Unit) }
            is DomainResult.Quota -> {
                if (writeMode == RestWriteMode.LocalFirst) mirror.markFailed(id, 402, quotaMessage(r))
                r
            }
            is DomainResult.Error -> when {
                r.code == 404 -> { mirror.removeHard(id); DomainResult.Success(Unit) }
                classifyRestFailure(r.code).isRetryable -> {
                    mirror.tombstone(id)
                    if (!r.isOffline) mirror.bumpAttempt(id, r.message)
                    DomainResult.Success(Unit)
                }
                else -> {
                    // Terminal (403/409/422…): em local-first a linha volta a aparecer, sinalizada.
                    if (writeMode == RestWriteMode.LocalFirst) mirror.markFailed(id, r.code, r.message)
                    r
                }
            }
        }
    }

    /**
     * Política única de falha de escrita do caminho **local-first**: retentável ⇒ a linha fica na
     * outbox e a chamada devolve `Success` (a escrita **foi** aceita, o estado é [RestRowState.Pending]);
     * terminal ⇒ a linha vira [RestRowState.Failed] com o erro preservado e a chamada devolve `Error`.
     */
    private fun resolveWriteFailure(localId: String, local: T, error: DomainResult.Error): DomainResult<T> =
        when (classifyRestFailure(error.code)) {
            RestFailureClass.Offline -> DomainResult.Success(local)
            RestFailureClass.Retryable -> { mirror.bumpAttempt(localId, error.message); DomainResult.Success(local) }
            RestFailureClass.Terminal, RestFailureClass.Quota -> {
                mirror.markFailed(localId, error.code, error.message); error
            }
        }

    private fun quotaMessage(quota: DomainResult.Quota): String =
        "Limite do plano atingido${quota.quota.feature.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""}."

    // -- Sync (RestCrudSyncParticipant) ------------------------------------

    /**
     * Baixa **o dataset COMPLETO** do servidor paginando o envelope (`GET {collection}?page=&size=` até
     * esgotar) e reconcilia o espelho (preserva pendências locais). Só reconcilia após acumular TODAS as
     * páginas — o `deleteHard` da [RestEntityMirror.reconcile] só é seguro sobre o conjunto completo;
     * caso contrário todo registro além da 1ª página seria apagado a cada refresh (defeito B1). Erro em
     * qualquer página aborta sem reconciliar (não deleta nada com base num conjunto parcial).
     *
     * Parada: página vazia, **página incompleta** (`items.size < size`), `total` alcançado
     * (`page*size >= total`, quando o envelope o expõe), ou **nenhum id novo** (salvaguarda contra
     * backend que ignora `?page=` e repete a lista). Limite duro [MAX_REFRESH_PAGES] anti-loop.
     */
    override suspend fun refresh(): Boolean {
        val accumulated = LinkedHashMap<String, T>()
        var page = 1
        while (page <= MAX_REFRESH_PAGES) {
            when (val r = api.getJson(pagedPath(page, refreshPageSize))) {
                is DomainResult.Success -> {
                    val decoded = runCatching { descriptor.decodePage(r.data) }.getOrNull() ?: return false
                    val before = accumulated.size
                    decoded.items.forEach { accumulated[descriptor.idOf(it)] = it }
                    val gainedNew = accumulated.size > before
                    val effectiveSize = decoded.size ?: refreshPageSize
                    val shortPage = decoded.items.size < effectiveSize
                    val reachedTotal = decoded.total?.let { page.toLong() * effectiveSize >= it } ?: false
                    if (decoded.items.isEmpty() || shortPage || reachedTotal || !gainedNew) break
                    page++
                }
                else -> return false // Quota/Error: não reconcilia sobre conjunto parcial.
            }
        }
        mirror.reconcile(accumulated.values.toList())
        return true
    }

    /**
     * Recarrega **UMA página** do servidor (`GET {collection}?page=&size=`) e faz **upsert-only**
     * ([RestEntityMirror.mergeClean], preservando linhas dirty), **sem** reconcile-delete. Para domínios
     * com **paginação/busca server-side pura** — onde o espelho é um cache PARCIAL de janelas visitadas e
     * apagar "o que não veio nesta página" removeria itens de outras páginas. Devolve o [RestPage] (itens
     * + metadados) para a UI paginar (`hasNextPage`). Distinto do [refresh] (dataset completo + reconcile).
     * Candidato C-02 do ReciboFácil.
     */
    suspend fun refreshPage(page: Int = 1, size: Int = refreshPageSize): DomainResult<RestPage<T>> =
        when (val r = api.getJson(pagedPath(page, size))) {
            is DomainResult.Success -> {
                val decoded = runCatching { descriptor.decodePage(r.data) }.getOrNull()
                    ?: return DomainResult.Error(INVALID_RESPONSE_CODE, INVALID_RESPONSE_MESSAGE)
                mirror.mergeClean(decoded.items)
                DomainResult.Success(decoded)
            }
            is DomainResult.Quota -> r
            is DomainResult.Error -> r
        }

    /**
     * Drena a outbox desta entidade (create/update/delete) contra o backend, aplicando o [parentRemap]
     * acumulado (FK de pais já sincronizados). Retorna o `clientId → serverId` dos **creates** que
     * confirmou, para o [RestCrudSyncEngine] alimentar o remap dos filhos. Pausa (retorna o que já fez)
     * ao perder a rede — a outbox é preservada e retentada no próximo ciclo.
     *
     * **Consome só as linhas drenáveis** ([RestEntityMirror.drainableRows]): o que o servidor recusou
     * de forma terminal fica de fora até o app pedir [requeueFailed]. Antes da 2.91.0 uma linha
     * recusada por validação era retentada **em todo ciclo, para sempre**, sem nunca convergir e sem
     * ninguém saber.
     *
     * ### FK de pai que sincronizou em OUTRO ciclo (2.93.0)
     * O [parentRemap] cobre só o ciclo corrente. Se o filho não drenou junto do pai (queda de sinal
     * no meio do drain, app fechado entre os dois `POST`s), no ciclo seguinte não há mais nada a
     * drenar no pai e o remap chega **vazio** — a FK subia apontando para o id local, o backend
     * recusava por `FOREIGN KEY`/UUID e a linha ficava `Failed` para sempre. Agora a resolução
     * consulta também o **remap durável** ([SyncStore.resolveServerId]), gravado no instante da
     * migração de cada id, e o corpo enviado passa por uma tradução genérica ([RestPayloadRemap]) —
     * o que faz a correção valer inclusive para quem não implementa [RestCrudEntity.remapRefs].
     */
    override suspend fun drainOutbox(parentRemap: Map<String, String>): Map<String, String> {
        val added = mutableMapOf<String, String>()
        // Cache do ciclo: um id resolvido uma vez não volta ao banco (nem repete o "não migrou").
        val cache = HashMap<String, String?>()
        var temRemap = parentRemap.isNotEmpty() || store.countIdRemap() > 0L
        fun resolveRef(id: String): String? =
            added[id] ?: parentRemap[id] ?: cache.getOrPut(id) { store.resolveServerId(id) }

        /** Corpo do push com as FKs já traduzidas (hook do app primeiro, varredura genérica depois). */
        fun bodyOf(model: T): String {
            val bruto = descriptor.encodeBody(model)
            return if (temRemap) RestPayloadRemap.applyToBody(bruto, json, ::resolveRef) else bruto
        }

        for (rowItem in mirror.drainableRows()) {
            val decoded = mirror.decode(rowItem.payload_json)
            if (decoded == null) {
                // Payload ilegível (schema mudou sob a linha): não há o que enviar — para e mostra.
                mirror.markFailed(rowItem.local_id, INVALID_PAYLOAD_CODE, INVALID_PAYLOAD_MESSAGE)
                continue
            }
            // O hook do app enxerga o remap do ciclo **e** os mapeamentos duráveis dos ids que este
            // payload realmente referencia (mapa materializado — nada de `Map` mentiroso/preguiçoso).
            val remapDaLinha =
                if (temRemap) parentRemap + RestPayloadRemap.resolveFor(rowItem.payload_json, json, ::resolveRef)
                else parentRemap
            val model = descriptor.remapRefs(decoded, remapDaLinha)
            // A operação enviada sai do ESTADO da linha, não só do que foi gravado em `pending_op`:
            // sem `server_id` a linha nunca existiu no servidor, logo é sempre um POST (e um delete
            // sobre ela se resolve local). Isto também **cura** linhas gravadas por versões
            // anteriores, que trocavam o CREATE pendente por UPDATE e ficavam presas em 404.
            val op = resolveOutboxOp(
                requested = rowItem.pending_op?.let { SyncOpType.fromWire(it) } ?: SyncOpType.UPDATE,
                knownLocally = true,
                hasServerId = rowItem.server_id != null,
            )
            if (op == null) {
                mirror.removeHard(rowItem.local_id) // delete de algo que nunca subiu: nada a enviar
                continue
            }
            when (op) {
                SyncOpType.CREATE -> when (val r = api.postJson(collection, bodyOf(model))) {
                    is DomainResult.Success -> {
                        val saved = decodeOrNull(r.data)
                        if (saved == null) {
                            // Aceito, resposta ilegível: retentar duplicaria o registro no servidor.
                            mirror.markFailed(rowItem.local_id, INVALID_RESPONSE_CODE, INVALID_RESPONSE_MESSAGE)
                        } else {
                            val serverId = descriptor.idOf(saved)
                            added[rowItem.client_id] = serverId
                            added[rowItem.local_id] = serverId
                            // markSynced grava o remap DURÁVEL: o filho que drenar num ciclo
                            // futuro (ou noutra execução do app) ainda acha este id.
                            mirror.markSynced(rowItem.local_id, saved)
                            if (rowItem.client_id != serverId) temRemap = true
                        }
                    }
                    is DomainResult.Error -> if (!applyDrainFailure(rowItem.local_id, r)) return added
                    is DomainResult.Quota -> mirror.markFailed(rowItem.local_id, 402, quotaMessage(r))
                }
                // A URL usa o id do SERVIDOR da linha (a chave que ele conhece), não o id que por
                // acaso está dentro do payload — que pode ser um handle antigo.
                SyncOpType.UPDATE -> when (
                    val r = api.putJson(itemPath(rowItem.server_id ?: descriptor.idOf(model)), bodyOf(model))
                ) {
                    is DomainResult.Success -> mirror.confirm(decodeOrNull(r.data) ?: model)
                    is DomainResult.Error -> if (!applyDrainFailure(rowItem.local_id, r)) return added
                    is DomainResult.Quota -> mirror.markFailed(rowItem.local_id, 402, quotaMessage(r))
                }
                SyncOpType.DELETE -> when (val r = api.delete(itemPath(rowItem.server_id ?: descriptor.idOf(model)))) {
                    is DomainResult.Success -> mirror.removeHard(rowItem.local_id)
                    is DomainResult.Error -> when {
                        r.code == 404 -> mirror.removeHard(rowItem.local_id) // já não existe lá
                        else -> if (!applyDrainFailure(rowItem.local_id, r)) return added
                    }
                    is DomainResult.Quota -> mirror.markFailed(rowItem.local_id, 402, quotaMessage(r))
                }
            }
        }
        return added
    }

    /**
     * Aplica ao espelho a falha de uma linha durante o push.
     * @return `false` quando o ciclo deve **pausar** (sem rede) — a outbox fica intacta.
     */
    private fun applyDrainFailure(localId: String, error: DomainResult.Error): Boolean =
        when (classifyRestFailure(error.code)) {
            RestFailureClass.Offline -> false
            RestFailureClass.Retryable -> { mirror.bumpAttempt(localId, error.message); true }
            RestFailureClass.Terminal, RestFailureClass.Quota -> {
                mirror.markFailed(localId, error.code, error.message); true
            }
        }

    protected fun decodeOrNull(body: String): T? =
        runCatching { descriptor.decodeModel(body) }.getOrNull()

    companion object {
        /** Tamanho de página default do [refresh] paginado = teto do contrato REST-CRUD (`?size` 1..100). */
        const val DEFAULT_REFRESH_PAGE_SIZE: Int = 100

        /** Salvaguarda anti-loop: máximo de páginas que um único [refresh] percorre. */
        const val MAX_REFRESH_PAGES: Int = 10_000

        /**
         * Sentinela: o servidor aceitou a escrita mas devolveu um corpo que não decodifica.
         * Classificado como **terminal** de propósito — repetir o POST duplicaria o registro.
         */
        const val INVALID_RESPONSE_CODE: Int = -2
        const val INVALID_RESPONSE_MESSAGE: String = "Resposta inválida do servidor."

        /** Sentinela: o payload local não decodifica (schema mudou sob a linha). Terminal. */
        const val INVALID_PAYLOAD_CODE: Int = -3
        const val INVALID_PAYLOAD_MESSAGE: String = "Registro local ilegível (formato antigo)."
    }
}

/**
 * Gerador de id de cliente para entidades criadas **offline** — o **handle estável** do registro:
 * o app pode carregá-lo na navegação e continuar consultando por ele depois de o id migrar para o do
 * servidor (ver [OfflineFirstRestRepository.observeById]/[OfflineFirstRestRepository.canonicalId]).
 *
 * Precisa ser **único no aparelho**, porque virou chave do remap durável `clientId → serverId`: o
 * contador reinicia com o processo, então o sufixo aleatório é o que impede colisão entre duas
 * execuções que gerem um id no mesmo milissegundo.
 */
fun newRestClientId(): String =
    "local-${currentTimeMillis()}-${restClientIdCounter++}-${Random.nextInt(0, 0xFFFF).toString(16)}"

private var restClientIdCounter: Long = 0L
