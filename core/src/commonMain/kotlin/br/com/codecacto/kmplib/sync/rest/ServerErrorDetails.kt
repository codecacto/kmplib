package br.com.codecacto.kmplib.sync.rest

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Leitura tipada dos `details` **crus** do envelope de erro (2.279.0).
 *
 * O mapa `details: Map<String, String>` de [ServerErrorEnvelope], [DomainResult.Error] e
 * `ApiResult.Error` continua só com os valores primitivos — é a frase do campo, e uma lista ou um
 * objeto não podem virar `toString()` de JSON numa legenda de formulário. Mas o envelope permite
 * `details` com **lista** e **objeto** (`503 PDF_RENDER_UNAVAILABLE {documentIds: [...]}`,
 * `422 ALERTS_NOT_ACKNOWLEDGED {alertIds: [...]}`), e até a 2.278.0 esse dado era descartado. O objeto
 * cru agora viaja em `detailsJson`, e estas funções são a leitura comum aos três tipos.
 *
 * Nenhuma delas lança: chave ausente, `null` ou forma diferente da pedida devolvem `null`.
 */
@PublishedApi
internal object ServerErrorDetails {

    /** Objeto vazio — o `detailsJson` de quem não trouxe `details`. */
    val EMPTY: JsonObject = JsonObject(emptyMap())

    /** Leitor tolerante para [decode]: campo a mais no servidor não quebra o app. */
    @PublishedApi
    internal val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** O valor cru de [key]; `null` se ausente ou `null` no JSON. */
    fun element(details: JsonObject, key: String): JsonElement? =
        details[key]?.takeUnless { it is JsonNull }

    /**
     * [key] como lista de textos.
     *
     * - lista de primitivos → os valores, na ordem (string, número ou booleano viram texto; `null` e
     *   string em branco saem);
     * - lista com objeto/lista dentro → `null` (não é lista de textos — leia com [decode]);
     * - um primitivo sozinho → lista de um (o servidor que manda um id só);
     * - objeto, ausente ou `null` → `null`.
     *
     * Texto com vírgula **não** é dividido: `"a,b"` volta como `["a,b"]`. Lista se manda como lista.
     */
    fun list(details: JsonObject, key: String): List<String>? = when (val valor = element(details, key)) {
        is JsonArray -> {
            if (valor.any { it !is JsonPrimitive }) {
                null
            } else {
                valor.mapNotNull { item ->
                    (item as JsonPrimitive).takeUnless { it is JsonNull }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                }
            }
        }
        is JsonPrimitive -> valor.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { listOf(it) }
        else -> null
    }

    /** [key] como objeto; `null` se não for objeto. */
    fun obj(details: JsonObject, key: String): JsonObject? = element(details, key) as? JsonObject

    /** [key] decodificado como [T]; `null` se ausente ou se não casar com o tipo. */
    inline fun <reified T> decode(details: JsonObject, key: String): T? {
        val valor = element(details, key) ?: return null
        return runCatching { json.decodeFromJsonElement<T>(valor) }.getOrNull()
    }
}
