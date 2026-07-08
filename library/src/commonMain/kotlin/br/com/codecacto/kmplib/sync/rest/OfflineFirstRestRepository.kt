package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.core.util.currentTimeMillis
import br.com.codecacto.kmplib.sync.SyncOpType
import br.com.codecacto.kmplib.sync.SyncStore
import kotlinx.coroutines.flow.Flow
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
     * Remapeia as **FKs pendentes** do modelo antes do push (clientId local do pai → serverId), a
     * partir do [remap] acumulado no ciclo de sync. Ex.: um lançamento criado offline referencia uma
     * empresa também criada offline — quando a empresa é confirmada, seu clientId vira serverId e este
     * hook corrige a FK antes de enviar o lançamento. Default: identidade (entidade sem FKs).
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
 * - **Escrita otimista + sync:** [create]/[update]/[delete] chamam o backend **primeiro** (para o
 *   **402 de quota** abrir o Paywall na hora e o id/valores derivados virem do servidor) e gravam o
 *   resultado LIMPO no espelho; **offline** (sem rede) gravam SUJO na outbox e o [RestCrudSyncEngine]
 *   drena ao reconectar.
 * - **Reconciliação por GET** ([refresh]) e **remap de FK** (id temporário local → id do servidor) no
 *   [drainOutbox], via [RestCrudEntity.remapRefs].
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
 */
open class OfflineFirstRestRepository<T : Any>(
    protected val api: DomainApiClient,
    protected val descriptor: RestCrudEntity<T>,
    store: SyncStore,
    protected val collectionPath: String,
    protected val json: Json = restMirrorJson,
    private val clientIdFactory: () -> String = ::newRestClientId,
    protected val refreshPageSize: Int = DEFAULT_REFRESH_PAGE_SIZE,
) : RestCrudSyncParticipant {

    /** Espelho local da entidade — exposto para os endpoints custom do app reconciliarem o cache. */
    val mirror: RestEntityMirror<T> = RestEntityMirror(
        name = descriptor.name,
        store = store,
        serializer = descriptor.serializer,
        idOf = descriptor::idOf,
        json = json,
    )

    private val collection: String = "/" + collectionPath.trim('/')
    protected fun itemPath(id: String): String = "$collection/$id"

    /** Caminho da coleção com paginação (`?page=&size=`) — teto do contrato: `size` 1..100. */
    protected fun pagedPath(page: Int, size: Int): String {
        val sep = if (collection.contains('?')) '&' else '?'
        return "$collection${sep}page=$page&size=$size"
    }

    // -- Leitura (espelho, offline) ----------------------------------------

    fun observeAll(): Flow<List<T>> = mirror.observeVisible()
    fun observeById(id: String): Flow<T?> = mirror.observeVisibleById(id)
    fun getCached(id: String): T? = mirror.get(id)
    fun getAllCached(): List<T> = mirror.getVisible()

    /** Cache-first: se não estiver no espelho, busca por `GET {collection}/{id}`. */
    suspend fun getById(id: String): DomainResult<T?> {
        mirror.get(id)?.let { return DomainResult.Success(it) }
        return when (val r = api.getJson(itemPath(id))) {
            is DomainResult.Success -> DomainResult.Success(decodeOrNull(r.data))
            is DomainResult.Quota -> r
            is DomainResult.Error -> if (r.code == 404) DomainResult.Success(null) else r
        }
    }

    // -- Escrita otimista + sync -------------------------------------------

    /** Cria online-first; offline → grava otimista na outbox com id local. */
    suspend fun create(model: T): DomainResult<T> =
        when (val r = api.postJson(collection, descriptor.encodeBody(model))) {
            is DomainResult.Success -> {
                val saved = decodeOrNull(r.data) ?: return DomainResult.Error(-2, "Resposta inválida do servidor.")
                mirror.putClean(saved); DomainResult.Success(saved)
            }
            is DomainResult.Quota -> r
            is DomainResult.Error -> if (r.isOffline) {
                val local = descriptor.withLocalId(model, clientIdFactory())
                mirror.putDirty(local, SyncOpType.CREATE)
                DomainResult.Success(local)
            } else {
                r
            }
        }

    /** Atualiza online-first; offline → grava otimista na outbox. */
    suspend fun update(model: T): DomainResult<T> =
        when (val r = api.putJson(itemPath(descriptor.idOf(model)), descriptor.encodeBody(model))) {
            is DomainResult.Success -> {
                val saved = decodeOrNull(r.data) ?: model
                mirror.confirm(saved); DomainResult.Success(saved)
            }
            is DomainResult.Quota -> r
            is DomainResult.Error -> if (r.isOffline) {
                mirror.putDirty(model, SyncOpType.UPDATE); DomainResult.Success(model)
            } else {
                r
            }
        }

    /** Exclui online-first; offline → tombstone (replay no reconnect); 404 → remove local. */
    suspend fun delete(id: String): DomainResult<Unit> =
        when (val r = api.delete(itemPath(id))) {
            is DomainResult.Success -> { mirror.removeHard(id); DomainResult.Success(Unit) }
            is DomainResult.Quota -> r
            is DomainResult.Error -> when {
                r.code == 404 -> { mirror.removeHard(id); DomainResult.Success(Unit) }
                r.isOffline -> { mirror.tombstone(id); DomainResult.Success(Unit) }
                else -> r
            }
        }

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
                    ?: return DomainResult.Error(-2, "Resposta inválida do servidor.")
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
     */
    override suspend fun drainOutbox(parentRemap: Map<String, String>): Map<String, String> {
        val added = mutableMapOf<String, String>()
        for (rowItem in mirror.dirtyRows()) {
            val decoded = mirror.decode(rowItem.payload_json) ?: continue
            val model = descriptor.remapRefs(decoded, parentRemap)
            when (rowItem.pending_op) {
                SyncOpType.CREATE.wire -> when (val r = api.postJson(collection, descriptor.encodeBody(model))) {
                    is DomainResult.Success -> {
                        val saved = decodeOrNull(r.data) ?: continue
                        added[rowItem.local_id] = descriptor.idOf(saved)
                        mirror.markSynced(rowItem.local_id, saved)
                    }
                    is DomainResult.Error -> if (r.isOffline) return added // sem rede: pausa
                    is DomainResult.Quota -> Unit // mantém sujo; usuário resolve no paywall/próximo online
                }
                SyncOpType.UPDATE.wire -> when (val r = api.putJson(itemPath(descriptor.idOf(model)), descriptor.encodeBody(model))) {
                    is DomainResult.Success -> mirror.confirm(decodeOrNull(r.data) ?: model)
                    is DomainResult.Error -> if (r.isOffline) return added
                    is DomainResult.Quota -> Unit
                }
                SyncOpType.DELETE.wire -> when (val r = api.delete(itemPath(descriptor.idOf(model)))) {
                    is DomainResult.Success -> mirror.removeHard(rowItem.local_id)
                    is DomainResult.Error -> when {
                        r.code == 404 -> mirror.removeHard(rowItem.local_id)
                        r.isOffline -> return added
                        else -> Unit
                    }
                    is DomainResult.Quota -> Unit
                }
            }
        }
        return added
    }

    protected fun decodeOrNull(body: String): T? =
        runCatching { descriptor.decodeModel(body) }.getOrNull()

    companion object {
        /** Tamanho de página default do [refresh] paginado = teto do contrato REST-CRUD (`?size` 1..100). */
        const val DEFAULT_REFRESH_PAGE_SIZE: Int = 100

        /** Salvaguarda anti-loop: máximo de páginas que um único [refresh] percorre. */
        const val MAX_REFRESH_PAGES: Int = 10_000
    }
}

/** Gerador de id de cliente para entidades criadas **offline** (migra p/ id do servidor no drain). */
fun newRestClientId(): String = "local-${currentTimeMillis()}-${restClientIdCounter++}"

private var restClientIdCounter: Long = 0L
