package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.sync.SyncStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json

/**
 * **Tradutor de identidade do offline-first REST-CRUD** (2.93.0 — GAP-KL-M-RESTCRUD-IDMIGRATION).
 *
 * Um registro criado sem rede nasce com um id local (`local-…`) e ganha o id do servidor quando
 * sincroniza. A partir daí **os dois ids designam o mesmo registro** — e essa é a única coisa que o
 * app precisa saber sobre a migração de id.
 *
 * O resolvedor lê o **remap durável** do [SyncStore] (persistido no instante da migração), então
 * funciona entre ciclos de sync, entre entidades e depois de o processo morrer.
 *
 * ### Onde isto importa no app
 * Enquanto o registro **pai** não sincroniza, os **filhos** guardam a FK com o id local; depois que
 * sincronizam, passam a guardá-la com o id do servidor. Comparar id com `==` faz a lista de filhos
 * esvaziar no meio do uso. Compare por aqui:
 * ```kotlin
 * val passageirosDaRota = passageiros.filter { ids.same(it.rotaId, rotaHandle) }
 * ```
 * Para consultas **reativas**, prefira [OfflineFirstRestRepository.observeCanonicalId], que emite o
 * id novo assim que ele migra e faz a tela recarregar sozinha.
 */
class RestIdResolver(private val store: SyncStore) {

    /**
     * Id **canônico** de um id qualquer: o id do servidor, se o registro já subiu; o próprio id,
     * caso contrário (registro ainda local, ou id que já é do servidor).
     */
    fun canonical(id: String): String =
        if (id.isBlank()) id else store.resolveServerId(id) ?: id

    /** Id local com que o registro nasceu neste aparelho, se ele nasceu aqui. */
    fun clientIdOf(serverId: String): String? =
        if (serverId.isBlank()) null else store.resolveClientId(serverId)

    /** `true` se [a] e [b] designam o **mesmo registro**, ainda que um seja local e o outro do servidor. */
    fun same(a: String?, b: String?): Boolean {
        if (a == null || b == null) return a == b
        if (a == b) return true
        if (a.isBlank() || b.isBlank()) return false
        return canonical(a) == canonical(b)
    }

    /** `true` se este id já migrou (existe um id do servidor para ele). */
    fun isMigrated(id: String): Boolean = id.isNotBlank() && store.resolveServerId(id) != null
}

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
