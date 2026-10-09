package br.com.codecacto.kmplib.ui.form

import androidx.compose.runtime.Immutable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral

/**
 * **O valor de uma resposta** — `string | number | boolean | string[] | ListItem[] | FileRef[]`.
 *
 * Guarda o JSON como veio e só o INTERPRETA à luz da pergunta, porque a forma sozinha é ambígua
 * (`[]` serve para `multiChoice`, `list` e `file`). É o `AnswerValue` do `backlib-forms`: os
 * acessores (`textOrNull()`, `numberOrNull()`…) devolvem `null` quando a forma não bate, e a forma
 * errada vira `WRONG_TYPE` no campo certo em vez de exceção sem dizer qual pergunta.
 *
 * Número é EXATO ([FormDecimal], do literal do JSON) — `72.55` nunca vira `72.54999…`.
 *
 * `toString()` NÃO mostra o valor: resposta de formulário clínico é dado de saúde, e um log
 * descuidado não pode levá-la para o arquivo nem para o GlitchTip.
 */
@Immutable
@Serializable(with = FormAnswerValueSerializer::class)
class FormAnswerValue private constructor(
    /** O JSON cru da resposta (nunca `JsonNull`: apagar é `null` no patch). */
    val json: JsonElement,
) {
    /**
     * Respondida — a régua ÚNICA das três libs: texto que não fica vazio depois do `trim()` do
     * JavaScript, número (inclusive `0`), booleano (inclusive `false`) ou lista não vazia.
     */
    val isAnswered: Boolean get() = json.isAnsweredJson()

    /** `text`/`longText`/`singleChoice`/`date`. Número NÃO vira texto. */
    fun textOrNull(): String? = json.textOrNull()

    /** `number`/`likert` — exato, do literal do JSON. */
    fun numberOrNull(): FormDecimal? = json.toFormDecimalOrNull()

    /** `consent`. */
    fun booleanOrNull(): Boolean? = json.booleanOrNull()

    /** `multiChoice` (`[]` = lista vazia). `null` se algum elemento não é texto. */
    fun stringListOrNull(): List<String>? {
        val array = json as? JsonArray ?: return null
        return array.map { it.textOrNull() ?: return null }
    }

    /** `list`: cada item é um objeto de primitivos (`JsonNull` = campo vazio). */
    fun listItemsOrNull(): List<FormListItem>? {
        val array = json as? JsonArray ?: return null
        return array.map { element ->
            val item = element as? JsonObject ?: return null
            item.mapValues { (_, v) -> v as? JsonPrimitive ?: return null }
        }
    }

    /** `file`: cada elemento é exatamente `{"fileId": "…"}`. */
    fun fileRefsOrNull(): List<FormFileRef>? {
        val array = json as? JsonArray ?: return null
        return array.map { element ->
            val obj = element as? JsonObject ?: return null
            if (obj.keys != setOf("fileId")) return null
            FormFileRef(obj.getValue("fileId").textOrNull() ?: return null)
        }
    }

    override fun equals(other: Any?): Boolean = other is FormAnswerValue && other.json == json

    override fun hashCode(): Int = json.hashCode()

    override fun toString(): String = "FormAnswerValue(<redigido>)"

    companion object {
        /** Embrulha um JSON de resposta. `JsonNull` não é resposta (apagar é `null` no patch). */
        fun fromJson(element: JsonElement): FormAnswerValue {
            require(element !is JsonNull) { "Resposta nula não é valor: apagar é null no patch" }
            return FormAnswerValue(element)
        }

        /** O mesmo, devolvendo `null` para `JsonNull`. */
        fun fromJsonOrNull(element: JsonElement?): FormAnswerValue? =
            if (element == null || element is JsonNull) null else FormAnswerValue(element)

        fun text(value: String): FormAnswerValue = FormAnswerValue(JsonPrimitive(value))

        @OptIn(ExperimentalSerializationApi::class)
        fun number(value: FormDecimal): FormAnswerValue = FormAnswerValue(JsonUnquotedLiteral(value.toPlainString()))

        fun number(value: Long): FormAnswerValue = number(FormDecimal.of(value))

        fun number(value: Int): FormAnswerValue = number(FormDecimal.of(value))

        fun bool(value: Boolean): FormAnswerValue = FormAnswerValue(JsonPrimitive(value))

        fun choices(values: List<String>): FormAnswerValue = FormAnswerValue(JsonArray(values.map { JsonPrimitive(it) }))

        /** Itens de `list` — valor de cada campo: [formListText], [formListNumber] ou `JsonNull`. */
        fun items(items: List<FormListItem>): FormAnswerValue = FormAnswerValue(JsonArray(items.map { JsonObject(it) }))

        fun files(refs: List<FormFileRef>): FormAnswerValue = FormAnswerValue(
            JsonArray(refs.map { JsonObject(mapOf("fileId" to JsonPrimitive(it.fileId))) }),
        )
    }
}

/** Um item de `list`: chave = id do campo do item; `JsonNull` (ou ausente) = vazio. */
typealias FormListItem = Map<String, JsonPrimitive>

/** Valor de texto de um campo de item (`text`, `singleChoice`, `date`). */
fun formListText(value: String): JsonPrimitive = JsonPrimitive(value)

/** Valor numérico EXATO de um campo de item (`number`). */
@OptIn(ExperimentalSerializationApi::class)
fun formListNumber(value: FormDecimal): JsonPrimitive = JsonUnquotedLiteral(value.toPlainString())

/** Arquivo já enviado pela rota de upload do projeto. `fileId` = UUID canônico minúsculo. */
@Immutable
@Serializable
data class FormFileRef(val fileId: String)

/** Quem respondeu — e, no contexto, o público que está vendo (`audience`). */
@Serializable
enum class FormRole { PATIENT, RECEPTION, DOCTOR }

/**
 * Resposta gravada (visão da equipe): o valor, quem respondeu, quando e o vínculo de quem respondeu.
 * `at` é instante ISO-8601 em UTC.
 */
@Immutable
@Serializable
data class FormAnswerEntry(
    val value: FormAnswerValue,
    val by: FormRole,
    val at: String,
    val memberId: String? = null,
)

/** Só os valores de `FormAnswers` (visão da equipe, com `by`/`at`) — para o runner e a validação. */
fun Map<String, FormAnswerEntry>.answerValues(): Map<String, FormAnswerValue> = mapValues { it.value.value }

/**
 * O que o cliente sabe do contexto de quem está sendo atendido. Campo `null` = desconhecido: toda
 * comparação sobre ele é falsa (só `notAnswered` é verdadeira). No app quase sempre vem VAZIO — o
 * servidor já entrega como constante (`{"all":[]}`/`{"any":[]}`) a condição de contexto que o
 * público não avalia.
 *
 * @property ageYears idade em anos completos NA DATA DA CONSULTA.
 * @property audience o público que está vendo (só muda texto `info`).
 */
@Immutable
@Serializable
data class FormContext(
    val sexAtBirth: String? = null,
    val ageYears: Int? = null,
    val pregnancy: String? = null,
    val specialty: String? = null,
    val audience: FormRole? = null,
) {
    /** Sexo, idade e gestação são dado de saúde: fora do log. */
    override fun toString(): String = "FormContext(<redigido>)"
}

/**
 * O mapa de respostas do fio (`{"q1": "sim", "q2": 72.5}`), tolerante: resposta `null` é descartada
 * em vez de derrubar a leitura. Use num DTO do app:
 * `@Serializable(with = FormAnswerValuesSerializer::class) val answers: Map<String, FormAnswerValue>`.
 */
object FormAnswerValuesSerializer : KSerializer<Map<String, FormAnswerValue>> {
    override val descriptor: SerialDescriptor = MapSerializer(String.serializer(), JsonElement.serializer()).descriptor

    override fun deserialize(decoder: Decoder): Map<String, FormAnswerValue> {
        val input = decoder as? JsonDecoder ?: throw SerializationException("Respostas de formulário só são lidas de JSON")
        val obj = input.decodeJsonElement() as? JsonObject ?: return emptyMap()
        val out = LinkedHashMap<String, FormAnswerValue>()
        obj.forEach { (id, raw) -> FormAnswerValue.fromJsonOrNull(raw)?.let { out[id] = it } }
        return out
    }

    override fun serialize(encoder: Encoder, value: Map<String, FormAnswerValue>) {
        val output = encoder as? JsonEncoder ?: throw SerializationException("Respostas de formulário só são gravadas em JSON")
        output.encodeJsonElement(JsonObject(value.mapValues { it.value.json }))
    }
}

/**
 * O `AnswerPatch` do contrato — `{ [questionId]: valor | null }`, com a resposta APAGADA como `null`
 * EXPLÍCITO (é como o servidor apaga a resposta de quem salva; omitir manteria a antiga). Use no DTO
 * do corpo: `@Serializable(with = FormAnswerPatchSerializer::class) val answers: Map<String, FormAnswerValue?>`.
 */
object FormAnswerPatchSerializer : KSerializer<Map<String, FormAnswerValue?>> {
    override val descriptor: SerialDescriptor = MapSerializer(String.serializer(), JsonElement.serializer()).descriptor

    override fun deserialize(decoder: Decoder): Map<String, FormAnswerValue?> {
        val input = decoder as? JsonDecoder ?: throw SerializationException("Patch de formulário só é lido de JSON")
        val obj = input.decodeJsonElement() as? JsonObject ?: return emptyMap()
        return obj.mapValues { (_, raw) -> FormAnswerValue.fromJsonOrNull(raw) }
    }

    override fun serialize(encoder: Encoder, value: Map<String, FormAnswerValue?>) {
        val output = encoder as? JsonEncoder ?: throw SerializationException("Patch de formulário só é gravado em JSON")
        output.encodeJsonElement(value.toFormAnswerPatchJson())
    }
}

/** O patch como objeto JSON, com `null` explícito para a resposta apagada. */
fun Map<String, FormAnswerValue?>.toFormAnswerPatchJson(): JsonObject =
    JsonObject(mapValues { (_, value) -> value?.json ?: JsonNull })

// ---------------------------------------------------------------------------------------------
// Leitura de primitivos JSON — a mesma régua do servidor (`JsonSupport` do backlib-forms)
// ---------------------------------------------------------------------------------------------

/** O texto de um primitivo JSON string; `null` para o resto (número NÃO vira texto). */
internal fun JsonElement.textOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull || !primitive.isString) return null
    return primitive.content
}

/** O booleano de um primitivo JSON NÃO-texto (`true`/`false`); `null` para o resto. */
internal fun JsonElement.booleanOrNull(): Boolean? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull || primitive.isString) return null
    return when (primitive.content) {
        "true" -> true
        "false" -> false
        else -> null
    }
}

/** Respondida (ver [FormAnswerValue.isAnswered]). `null`/`JsonNull` = não. */
internal fun JsonElement?.isAnsweredJson(): Boolean = when (this) {
    null, JsonNull -> false
    is JsonPrimitive -> if (isString) !content.isBlankLikeJs() else true
    is JsonArray -> isNotEmpty()
    is JsonObject -> true
}

/** Respondida — `null` = não. A mesma régua de `answered`, `required` e das pendências. */
fun isFormAnswered(value: FormAnswerValue?): Boolean = value?.isAnswered == true

/**
 * "Em branco" pela MESMA régua do `String.prototype.trim()` do JavaScript (espaço Unicode `Zs`, TAB,
 * VT, FF, BOM e os terminadores de linha) — a weblib e o servidor usam esta régua, e o `isBlank()` do
 * Kotlin difere nas bordas (trata FS/GS/RS/US como espaço e não trata o BOM).
 */
internal fun String.isBlankLikeJs(): Boolean = all { it.isJsTrimmable() }

private fun Char.isJsTrimmable(): Boolean =
    this == '\t' || this == '\n' || this == '\u000B' || this == '\u000C' || this == '\r' ||
        this == ' ' || this == ' ' || this == '﻿' ||
        category == CharCategory.SPACE_SEPARATOR

internal object FormAnswerValueSerializer : KSerializer<FormAnswerValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("br.com.codecacto.kmplib.ui.form.FormAnswerValue")

    override fun serialize(encoder: Encoder, value: FormAnswerValue) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("FormAnswerValue só é gravado em JSON")
        json.encodeJsonElement(value.json)
    }

    override fun deserialize(decoder: Decoder): FormAnswerValue {
        val json = decoder as? JsonDecoder ?: throw SerializationException("FormAnswerValue só é lido de JSON")
        val element = json.decodeJsonElement()
        if (element is JsonNull) throw SerializationException("Resposta nula: para apagar, use null no patch")
        return FormAnswerValue.fromJson(element)
    }
}
