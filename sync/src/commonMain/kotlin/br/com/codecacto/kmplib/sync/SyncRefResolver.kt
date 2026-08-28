package br.com.codecacto.kmplib.sync

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Resolução pura (sem I/O) das FKs por-clientId de uma operação de push.
 *
 * Dado o payload serializado de uma entidade-filha e o mapa `{campoFkNoWire → clientIdLocal
 * do pai}` declarado em [SyncableEntity.refsOf], decide, para cada FK, entre:
 *  - **backfill** no payload: o pai já tem `serverId` conhecido no espelho → o engine
 *    substitui o clientId local pelo serverId direto no payload, e o servidor recebe a FK
 *    canônica (sem precisar de [SyncOp.refs]);
 *  - **[SyncOp.refs]**: o pai ainda está pendente no mesmo lote (sem serverId) → o engine
 *    inclui `campoFk → clientIdLocal` em `refs` para o servidor resolver `clientId→serverId`
 *    em ordem topológica.
 *
 * Objetivo de corretude: **o servidor nunca recebe uma FK que seja um clientId local órfão
 * sem o `refs` correspondente.** Resolve o bug em que `SyncOp.refs` era sempre `null`.
 *
 * Isolada do [SyncStore] (recebe um [resolveServerId] lambda) para ser exercitada em
 * `commonTest` sem driver de banco — e reusada pelo [DefaultSyncEngine] com a busca real.
 */
internal object SyncRefResolver {

    /** Resultado da resolução: payload (possivelmente com backfill) + refs a enviar. */
    data class Resolved(
        val payload: JsonElement?,
        val refs: Map<String, String>?,
    )

    /**
     * @param payload payload serializado da op (NULL em delete → nada a fazer).
     * @param declaredRefs `{campoFkNoWire → clientIdLocal do pai}` de [SyncableEntity.refsOf].
     * @param resolveServerId resolve o `serverId` conhecido de um clientId de pai (NULL se o
     *   pai ainda não foi sincronizado / não está no espelho).
     */
    fun resolve(
        payload: JsonElement?,
        declaredRefs: Map<String, String>,
        resolveServerId: (clientId: String) -> String?,
    ): Resolved {
        if (payload == null || declaredRefs.isEmpty()) {
            return Resolved(payload = payload, refs = null)
        }

        val obj: JsonObject? = (payload as? JsonObject)
        val mutablePayload: MutableMap<String, JsonElement> = obj?.toMutableMap() ?: mutableMapOf()
        val pendingRefs = LinkedHashMap<String, String>()
        var changedPayload = false

        for ((field, parentClientId) in declaredRefs) {
            if (parentClientId.isBlank()) continue

            // O valor da FK realmente presente no payload (pode divergir do declarado se o app
            // já gravou o serverId). Só remapeamos quando o valor no payload == clientId local
            // do pai — qualquer outro valor (ex.: já um serverId) é preservado intacto.
            val current = (mutablePayload[field] as? JsonPrimitive)?.contentIfPresent()
            val fkValue = current ?: parentClientId

            val serverId = resolveServerId(parentClientId)
            if (serverId != null) {
                // Backfill: o pai já está sincronizado → grava o serverId canônico no payload.
                // Só sobrescreve quando o payload ainda traz o clientId local (evita pisar num
                // valor que o app já resolveu para outra coisa).
                if (obj != null && current == parentClientId) {
                    mutablePayload[field] = JsonPrimitive(serverId)
                    changedPayload = true
                }
            } else if (fkValue == parentClientId) {
                // Pai ainda pendente no lote → delega ao servidor via refs.
                pendingRefs[field] = parentClientId
            }
        }

        val newPayload: JsonElement = if (changedPayload) JsonObject(mutablePayload) else payload
        return Resolved(
            payload = newPayload,
            refs = pendingRefs.takeIf { it.isNotEmpty() },
        )
    }

    /** Conteúdo textual do primitivo, ou `null` quando representa JSON `null`. */
    private fun JsonPrimitive.contentIfPresent(): String? =
        if (!isString && content == "null") null else content
}
