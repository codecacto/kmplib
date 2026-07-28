package br.com.codecacto.kmplib.permissions

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Ponte JSON do envelope canônico de permissões:
 *
 * ```json
 * { "modules": { "AGENDA": "EDIT", "FINANCE": "VIEW" }, "contentsPost": true }
 * ```
 *
 * As flags são **irmãs** de `modules` no objeto (é o formato já em produção no Influencer), mas a
 * lib não fixa quais são — daí o trabalho ser sobre `JsonObject`, e não sobre um `@Serializable`
 * com campos fixos (que voltaria a vazar domínio de app para dentro da lib).
 *
 * Apps que já têm o próprio DTO (`MemberPermissionsDto(modules, contentsPost)`) **não precisam
 * disto**: usam [PermissionMatrixWire.parse] + [toModuleMap]/[toFlagMap] diretamente.
 *
 * **Nada aqui lança** — entrada ilegível vira estado vazio + log.
 */
object PermissionMatrixJson {

    private const val LOG_TAG = "PermissionMatrix"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Serializa a matriz **já normalizada** no envelope `{ modules, ...flags }`. */
    fun toJsonObject(
        state: PermissionMatrixState,
        flags: List<PermissionFlagSpec> = emptyList(),
    ): JsonObject {
        val normalized = state.normalized(flags)
        return buildJsonObject {
            put(
                PermissionMatrixWire.MODULES_FIELD,
                buildJsonObject {
                    normalized.levels.forEach { (key, level) -> put(key, level.name) }
                },
            )
            normalized.flags.forEach { (key, value) -> put(key, value) }
        }
    }

    /**
     * Lê o envelope. Tolerante: `modules` ausente/de outro tipo ⇒ sem módulos; valores que não são
     * string/booleano são ignorados; níveis desconhecidos são descartados com log (mesma regra de
     * [PermissionMatrixWire.parse]).
     */
    fun fromJsonObject(source: JsonObject?): PermissionMatrixState {
        if (source == null) return PermissionMatrixState.Empty

        val rawModules = LinkedHashMap<String, String?>()
        (source[PermissionMatrixWire.MODULES_FIELD] as? JsonObject)?.forEach { (key, value) ->
            val primitive = value as? JsonPrimitive
            rawModules[key] = if (primitive != null && primitive.isString) primitive.content else null
        }

        val rawFlags = LinkedHashMap<String, Boolean>()
        source.forEach { (key, value) ->
            if (key == PermissionMatrixWire.MODULES_FIELD) return@forEach
            val flag = (value as? JsonPrimitive)?.booleanOrNull ?: return@forEach
            rawFlags[key] = flag
        }

        return PermissionMatrixWire.parse(rawModules, rawFlags)
    }

    /** Serializa em texto JSON (ver [toJsonObject]). */
    fun encodeToString(
        state: PermissionMatrixState,
        flags: List<PermissionFlagSpec> = emptyList(),
    ): String = json.encodeToString(JsonObject.serializer(), toJsonObject(state, flags))

    /** Lê de texto JSON. Texto nulo/vazio/inválido ⇒ [PermissionMatrixState.Empty] (nunca lança). */
    fun decodeFromString(raw: String?): PermissionMatrixState {
        if (raw.isNullOrBlank()) return PermissionMatrixState.Empty
        val parsed = runCatching { json.decodeFromString(JsonObject.serializer(), raw) }.getOrElse {
            AppLogger.w(LOG_TAG, "JSON de permissoes ilegivel (ignorado): ${it.message}")
            return PermissionMatrixState.Empty
        }
        return fromJsonObject(parsed)
    }
}
