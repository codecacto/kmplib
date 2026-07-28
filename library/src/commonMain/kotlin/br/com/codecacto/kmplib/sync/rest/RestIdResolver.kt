package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.sync.SyncStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json

/**
 * **Tradutor de identidade do offline-first REST-CRUD** (2.93.0 — GAP-KL-M-RESTCRUD-IDMIGRATION;
 * correlação por conjunto de handles na 2.94.0).
 *
 * Um registro criado sem rede nasce com um id local (`local-…`) e ganha o id do servidor quando
 * sincroniza. A partir daí **os dois ids designam o mesmo registro** — e essa é a única coisa que o
 * app precisa saber sobre a migração de id.
 *
 * O resolvedor lê o **remap durável** do [SyncStore] (persistido no instante da migração), então
 * funciona entre ciclos de sync, entre entidades e depois de o processo morrer.
 *
 * ### A regra: correlacione por CONJUNTO DE HANDLES, nunca por igualdade
 * Enquanto o registro **pai** não sincroniza, os **filhos** guardam a FK com o id local; depois que
 * sincronizam, passam a guardá-la com o id do servidor. E como o drain **pausa** ao perder o sinal
 * (`RestFailureClass.Offline` interrompe o ciclo), **os dois estados convivem na mesma lista**:
 * comparar por igualdade contra qualquer um dos dois valores derruba a outra metade — no app,
 * "sumiu da lista" é registro que não aparece onde deveria.
 *
 * ```kotlin
 * // ERRADO: filtra por igualdade; metade dos filhos some quando o drain é interrompido.
 * val daRota = passageiros.filter { it.rotaId == rotaId }
 *
 * // CERTO: um conjunto de handles resolvido uma vez, usado com `in`.
 * val handles = ids.handlesOf(rotaHandle)
 * val daRota = passageiros.filter { it.rotaId in handles }
 * ```
 *
 * Para consultas **reativas**, use [OfflineFirstRestRepository.observeChildren] — que já reage à
 * migração do id e resolve fora do contexto do coletor.
 *
 * @param store espelho local (fonte do remap durável).
 * @param dispatcher onde as resoluções feitas por operador de fluxo ([resolvingIds],
 *   [OfflineFirstRestRepository.observeHandles]) rodam. Consultar o remap é I/O de banco: fazê-lo
 *   no contexto do coletor significa ler SQLite na thread de UI.
 */
class RestIdResolver(
    private val store: SyncStore,
    internal val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    /**
     * Id **canônico** de um id qualquer: o id do servidor, se o registro já subiu; o próprio id,
     * caso contrário (registro ainda local, ou id que já é do servidor).
     */
    fun canonical(id: String): String =
        if (id.isBlank()) id else store.resolveServerId(id) ?: id

    /** Id local com que o registro nasceu neste aparelho, se ele nasceu aqui. */
    fun clientIdOf(serverId: String): String? =
        if (serverId.isBlank()) null else store.resolveClientId(serverId)

    /**
     * `true` se [a] e [b] designam o **mesmo registro**, ainda que um seja local e o outro do servidor.
     *
     * Custa **duas** consultas ao remap por chamada — bom para comparar dois ids, ruim dentro de um
     * `filter` sobre uma lista. Para listas, resolva [handlesOf] uma vez e use `in`.
     */
    fun same(a: String?, b: String?): Boolean {
        if (a == null || b == null) return a == b
        if (a == b) return true
        if (a.isBlank() || b.isBlank()) return false
        return canonical(a) == canonical(b)
    }

    /** `true` se este id já migrou (existe um id do servidor para ele). */
    fun isMigrated(id: String): Boolean = id.isNotBlank() && store.resolveServerId(id) != null

    // -- Correlação por conjunto de handles (2.94.0) ------------------------

    /**
     * **Todos os ids sob os quais [id] pode ser referenciado neste aparelho**: o próprio, o id do
     * servidor (se já migrou) e o id local com que nasceu (se nasceu aqui).
     *
     * Resolve nos **dois sentidos** de propósito: o handle pode chegar local (tela aberta antes do
     * sync) ou do servidor (tela reaberta pelo histórico depois de um reinício), e a FK do filho
     * pode estar em qualquer um dos dois estados. A interseção dos conjuntos é o único critério que
     * não esvazia a lista no meio do uso.
     *
     * Custo: **duas** consultas ao remap por chamada e **nenhuma por linha** — por isso resolva uma
     * vez por emissão e use `in` no filtro, em vez de [same] linha a linha.
     *
     * Id em branco ⇒ conjunto vazio (nada casa com "sem id").
     */
    fun handlesOf(id: String): Set<String> {
        if (id.isBlank()) return emptySet()
        return buildSet {
            add(id)
            add(canonical(id))
            clientIdOf(id)?.let(::add)
        }
    }

    /** União dos [handlesOf] de vários registros — para filtrar filhos de um conjunto de pais. */
    fun handlesOf(ids: Iterable<String>): Set<String> =
        buildSet { ids.forEach { addAll(handlesOf(it)) } }

    /**
     * Indexa [items] por **todos** os seus handles: o mapa responde tanto pelo id local quanto pelo
     * id do servidor.
     *
     * É o padrão "atributo do cadastro a partir de uma FK congelada": a linha de execução guarda
     * `passageiroId` como estava quando foi criada; se o passageiro migrar de id depois disso,
     * procurar o cadastro por aquele id não acha nada e o dado **some do card** — sem erro nenhum
     * na tela, que é o pior tipo de falha.
     *
     * ```kotlin
     * val cadastro = ids.indexByHandle(passageiros) { it.id }
     * val pontoDeParada = cadastro[linha.passageiroId]?.ponto
     * ```
     *
     * Chaves em branco são ignoradas. Em caso de colisão, vence o **último** item.
     */
    fun <T> indexByHandle(items: Iterable<T>, idOf: (T) -> String): Map<String, T> = buildMap {
        items.forEach { item -> handlesOf(idOf(item)).forEach { handle -> put(handle, item) } }
    }

    /**
     * Agrupa [items] pela **referência ao pai**, tolerante à migração de id dos dois lados: os
     * filhos são agrupados pelo id canônico da FK, e a consulta aceita qualquer handle do pai.
     *
     * Cobre os dois usos recorrentes: filtrar os filhos de um pai e **contá-los** por pai (o
     * "12 passageiros" do card).
     *
     * ```kotlin
     * val porRota = ids.groupByRef(passageiros) { it.rotaId }
     * porRota[rotaHandle]          // os filhos daquela rota
     * porRota.count(rotaHandle)    // a contagem do card
     * ```
     *
     * Filhos com referência nula/em branco ficam de fora (não têm pai).
     */
    fun <T> groupByRef(items: Iterable<T>, refOf: (T) -> String?): RestRefGroups<T> {
        val porCanonico = LinkedHashMap<String, MutableList<T>>()
        items.forEach { item ->
            val ref = refOf(item)?.takeIf { it.isNotBlank() } ?: return@forEach
            porCanonico.getOrPut(canonical(ref)) { mutableListOf() } += item
        }
        return RestRefGroups(this, porCanonico)
    }
}

/**
 * Filhos agrupados pela referência ao pai, indexados pelo **id canônico** — construído por
 * [RestIdResolver.groupByRef].
 *
 * A consulta aceita **qualquer handle** do pai (o id local que a tela carrega desde a navegação ou o
 * id do servidor lido do espelho): o handle é canonizado na hora, com memo, para uma lista de N pais
 * não virar N consultas repetidas ao banco.
 *
 * **Não é thread-safe** — é um objeto de vida curta, construído por emissão de fluxo e descartado.
 */
class RestRefGroups<T> internal constructor(
    private val resolver: RestIdResolver,
    private val porCanonico: Map<String, List<T>>,
) {
    private val memo = HashMap<String, String>()

    private fun canon(handle: String): String = memo.getOrPut(handle) { resolver.canonical(handle) }

    /** Os filhos daquele pai (lista vazia se não há nenhum). */
    operator fun get(parentHandle: String): List<T> =
        if (parentHandle.isBlank()) emptyList() else porCanonico[canon(parentHandle)].orEmpty()

    /** Quantos filhos aquele pai tem. */
    fun count(parentHandle: String): Int = get(parentHandle).size

    /** Contagem por id **canônico** de pai — para alimentar uma lista inteira de uma vez. */
    fun countByCanonicalId(): Map<String, Int> = porCanonico.mapValues { it.value.size }

    /** Ids canônicos de pai que têm ao menos um filho. */
    val canonicalIds: Set<String> get() = porCanonico.keys

    val isEmpty: Boolean get() = porCanonico.isEmpty()
}

/**
 * Aplica [transform] a cada emissão **resolvendo os ids fora do contexto do coletor**
 * ([RestIdResolver.dispatcher]).
 *
 * Resolver handle é consulta ao banco. Feito direto no `map` de um fluxo coletado pela UI, vira
 * leitura de SQLite na thread principal a cada emissão — e uma tela de lista emite muito. Este
 * operador é o lugar certo para o padrão "indexar um mapa por registro":
 *
 * ```kotlin
 * passageiroRepo.observeAll()
 *     .resolvingIds(repo.ids) { lista -> indexByHandle(lista) { it.id } }
 * ```
 */
fun <T, R> Flow<T>.resolvingIds(
    ids: RestIdResolver,
    transform: RestIdResolver.(T) -> R,
): Flow<R> = map { ids.transform(it) }.flowOn(ids.dispatcher)

/**
 * Aplicação **genérica** do remap `clientId → serverId` sobre o corpo JSON que vai ao servidor.
 *
 * Existe para que a correção chegue aos ~14 apps da onda **sem que nenhum precise mudar**: o hook
 * [RestCrudEntity.remapRefs] é opcional e, quando implementado, só enxergava o remap **do ciclo
 * corrente** — um filho que drenasse num ciclo posterior ao do pai subia a FK com o id local, o
 * backend recusava por `FOREIGN KEY`/UUID inválido, a recusa era **terminal** e o dado se perdia.
 *
 * A varredura é feita **depois** do [RestCrudEntity.remapRefs] (que continua tendo prioridade) e só
 * troca valores **string** que o remap conhece — um id que nunca migrou não é tocado. Strings longas
 * (texto livre) são ignoradas por [MAX_ID_LENGTH], e chaves de objeto nunca são reescritas.
 */
internal object RestPayloadRemap {

    /** Acima disto o valor é texto, não id — nem vale consultar o remap. */
    const val MAX_ID_LENGTH: Int = 128

    /**
     * Mapa **honesto e completo** `clientId → serverId` para os ids que aparecem em [payloadJson]:
     * é o que se entrega a [RestCrudEntity.remapRefs] no lugar do antigo remap "só do ciclo".
     * Vazio quando nada no payload migrou.
     */
    fun resolveFor(payloadJson: String, json: Json, resolve: (String) -> String?): Map<String, String> {
        if (payloadJson.isBlank()) return emptyMap()
        val raiz = runCatching { json.parseToJsonElement(payloadJson) }.getOrNull() ?: return emptyMap()
        val encontrados = LinkedHashMap<String, String>()
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> element.values.forEach { walk(it) }
                is JsonArray -> element.forEach { walk(it) }
                is JsonPrimitive -> {
                    val texto = element.takeIf { it.isString }?.content ?: return
                    if (texto.isBlank() || texto.length > MAX_ID_LENGTH) return
                    resolve(texto)?.takeIf { it != texto }?.let { encontrados[texto] = it }
                }
            }
        }
        walk(raiz)
        return encontrados
    }

    /**
     * @param body corpo JSON já produzido por [RestCrudEntity.encodeBody].
     * @param resolve `clientId → serverId` (cache do ciclo + remap durável). `null` = não migrou.
     * @return o corpo com as FKs traduzidas, ou o **corpo original** se nada mudou (ou se ele não
     *   for JSON — a lib não tenta adivinhar formato de fio alheio).
     */
    fun applyToBody(body: String, json: Json, resolve: (String) -> String?): String {
        if (body.isBlank()) return body
        val raiz = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return body
        var mudou = false
        fun walk(element: JsonElement): JsonElement = when (element) {
            is JsonObject -> JsonObject(element.mapValues { (_, valor) -> walk(valor) })
            is JsonArray -> JsonArray(element.map { walk(it) })
            is JsonPrimitive -> {
                val texto = element.takeIf { it.isString }?.content
                val alvo = texto
                    ?.takeIf { it.isNotBlank() && it.length <= MAX_ID_LENGTH }
                    ?.let(resolve)
                if (alvo != null && alvo != texto) {
                    mudou = true
                    JsonPrimitive(alvo)
                } else {
                    element
                }
            }
        }
        val resultado = walk(raiz)
        return if (mudou) json.encodeToString(JsonElement.serializer(), resultado) else body
    }
}
