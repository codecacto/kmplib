package br.com.codecacto.kmplib.ui.form

import androidx.compose.runtime.Immutable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
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

/**
 * **Condição de exibição** (`visibleIf`) — a gramática do contrato, campo a campo:
 *
 * ```json
 * {"all": [ … ]}   {"any": [ … ]}   {"not": { … }}
 * {"question": "fuma", "op": "eq", "value": "sim"}
 * {"context": "ageYears", "op": "gte", "value": 40}
 * ```
 *
 * A semântica é a MESMA do `ConditionEvaluator` do `backlib-forms` e do `evaluateFormCondition` da
 * weblib (fixtures `conditions`):
 * - **Sem resposta, toda comparação é FALSA** — `eq`, `neq`, `in`, `notIn`, `gt…`, `includes`.
 *   "Mostrar se fuma ≠ não" fica escondida até a pessoa responder; quem quer o contrário escreve
 *   `{"not": {… "eq" …}}`, que é verdadeira sem resposta.
 * - **Sem conversão de tipo**: o texto `"7"` não é o número `7` (`eq` falsa, `neq` verdadeira).
 *   Número compara por valor (`7` = `7.0`); texto compara exato (maiúscula conta).
 * - `eq`/`neq`/`gt…`/`includes` levam UM escalar; `in`/`notIn` uma lista; `answered`/`notAnswered`
 *   nenhum. Forma errada = falsa. `gt…` só número com número; `includes` só lista de textos.
 * - `{"all": []}` é verdadeira e `{"any": []}` é falsa — a constante que o servidor entrega quando
 *   ELE resolveu a condição (contexto que o público não avalia).
 *
 * Leitura TOTAL: condição malformada (chave desconhecida, duas formas no mesmo objeto, operador ou
 * contexto desconhecido) vira [Invalid] — sempre falsa, nunca exceção. Quem barra condição mal
 * escrita é o lint do servidor, na autoria; o app não pode deixar de abrir o formulário por isso.
 * O JSON original é preservado na escrita.
 */
@Immutable
@Serializable(with = FormConditionSerializer::class)
sealed interface FormCondition {

    /** Todas verdadeiras. Vazia = verdadeira. */
    @Immutable
    data class All(val conditions: List<FormCondition>) : FormCondition

    /** Alguma verdadeira. Vazia = falsa. (`Any` é palavra reservada do Kotlin.) */
    @Immutable
    data class AnyOf(val conditions: List<FormCondition>) : FormCondition

    @Immutable
    data class Not(val condition: FormCondition) : FormCondition

    /**
     * Sobre a resposta de uma pergunta ANTERIOR. [value] é o JSON cru do operando (escalar, lista,
     * ou `null` = ausente) — interpretado na avaliação, como na weblib.
     */
    @Immutable
    data class Question(val questionId: String, val op: FormCondOp, val value: JsonElement? = null) : FormCondition

    /** Sobre o contexto de quem responde ([FormContext]). */
    @Immutable
    data class Context(val key: FormContextKey, val op: FormCondOp, val value: JsonElement? = null) : FormCondition

    /** Forma que o contrato não reconhece. Sempre falsa. */
    @Immutable
    data class Invalid(val json: JsonElement) : FormCondition

    companion object {
        /** `{"all": []}` — sempre verdadeira. */
        val ALWAYS: FormCondition = All(emptyList())

        /** `{"any": []}` — sempre falsa. */
        val NEVER: FormCondition = AnyOf(emptyList())

        /** Atalho: `FormCondition.question("fuma", FormCondOp.EQ, JsonPrimitive("sim"))`. */
        fun question(questionId: String, op: FormCondOp, value: JsonElement? = null): FormCondition =
            Question(questionId, op, value)

        /** Atalho: `FormCondition.context(FormContextKey.AGE_YEARS, FormCondOp.GTE, JsonPrimitive(40))`. */
        fun context(key: FormContextKey, op: FormCondOp, value: JsonElement? = null): FormCondition =
            Context(key, op, value)
    }
}

/** Os operadores do contrato. */
enum class FormCondOp(val wireName: String) {
    EQ("eq"),
    NEQ("neq"),
    IN("in"),
    NOT_IN("notIn"),
    GT("gt"),
    GTE("gte"),
    LT("lt"),
    LTE("lte"),
    ANSWERED("answered"),
    NOT_ANSWERED("notAnswered"),
    INCLUDES("includes"),
    ;

    companion object {
        fun fromWireName(name: String?): FormCondOp? = entries.firstOrNull { it.wireName == name }
    }
}

/** O contexto que uma condição pode ler. */
enum class FormContextKey(val wireName: String) {
    SEX_AT_BIRTH("sexAtBirth"),
    AGE_YEARS("ageYears"),
    PREGNANCY("pregnancy"),
    SPECIALTY("specialty"),
    AUDIENCE("audience"),
    ;

    companion object {
        fun fromWireName(name: String?): FormContextKey? = entries.firstOrNull { it.wireName == name }
    }
}

/**
 * **Avalia a condição** contra respostas e contexto. Puro e TOTAL: forma errada = `false`.
 *
 * Para seguir a regra "pergunta escondida não responde", passe só as respostas das perguntas
 * visíveis — é o que [evaluateVisibility] faz.
 *
 * @param answerOf a resposta de uma pergunta (`null` = sem resposta).
 */
fun FormCondition.evaluate(context: FormContext?, answerOf: (String) -> FormAnswerValue?): Boolean = when (this) {
    is FormCondition.All -> conditions.all { it.evaluate(context, answerOf) }
    is FormCondition.AnyOf -> conditions.any { it.evaluate(context, answerOf) }
    is FormCondition.Not -> !condition.evaluate(context, answerOf)
    is FormCondition.Question -> compare(Subject.of(answerOf(questionId)?.json), op, value)
    is FormCondition.Context -> compare(Subject.ofContext(context, key), op, value)
    is FormCondition.Invalid -> false
}

/** Os ids de pergunta que a condição lê (em qualquer profundidade), sem repetição, na ordem. */
fun FormCondition.questionIds(): List<String> {
    val out = LinkedHashSet<String>()
    fun walk(c: FormCondition) {
        when (c) {
            is FormCondition.All -> c.conditions.forEach(::walk)
            is FormCondition.AnyOf -> c.conditions.forEach(::walk)
            is FormCondition.Not -> walk(c.condition)
            is FormCondition.Question -> out += c.questionId
            is FormCondition.Context, is FormCondition.Invalid -> Unit
        }
    }
    walk(this)
    return out.toList()
}

/** O lado esquerdo de uma comparação, já normalizado. */
private sealed interface Subject {
    data object Absent : Subject
    data class Text(val value: String) : Subject
    data class Num(val value: FormDecimal) : Subject
    data class Bool(val value: Boolean) : Subject
    data class TextList(val values: List<String>) : Subject

    /** Respondida, mas sem forma comparável (itens de lista, anexos). */
    data object Opaque : Subject

    companion object {
        fun of(json: JsonElement?): Subject = when (json) {
            null, JsonNull -> Absent
            is JsonPrimitive -> when {
                json.isString -> if (json.content.isBlankLikeJs()) Absent else Text(json.content)
                else -> json.booleanOrNull()?.let(::Bool) ?: json.toFormDecimalOrNull()?.let(::Num) ?: Absent
            }
            is JsonArray -> when {
                json.isEmpty() -> Absent
                json.all { it.textOrNull() != null } -> TextList(json.map { it.textOrNull()!! })
                else -> Opaque
            }
            is JsonObject -> Opaque
        }

        fun ofContext(context: FormContext?, key: FormContextKey): Subject = when (key) {
            FormContextKey.SEX_AT_BIRTH -> text(context?.sexAtBirth)
            FormContextKey.AGE_YEARS -> context?.ageYears?.let { Num(FormDecimal.of(it)) } ?: Absent
            FormContextKey.PREGNANCY -> text(context?.pregnancy)
            FormContextKey.SPECIALTY -> text(context?.specialty)
            FormContextKey.AUDIENCE -> context?.audience?.let { Text(it.name) } ?: Absent
        }

        private fun text(value: String?): Subject = if (value == null || value.isBlankLikeJs()) Absent else Text(value)
    }
}

/** `string | number | boolean` do operando. */
private sealed interface Scalar {
    data class Text(val value: String) : Scalar
    data class Num(val value: FormDecimal) : Scalar
    data class Bool(val value: Boolean) : Scalar

    companion object {
        /** Um escalar JSON; `null` para lista, objeto, `null` ou número ilegível. */
        fun of(json: JsonElement?): Scalar? {
            val primitive = json as? JsonPrimitive ?: return null
            if (primitive is JsonNull) return null
            if (primitive.isString) return Text(primitive.content)
            primitive.booleanOrNull()?.let { return Bool(it) }
            return primitive.toFormDecimalOrNull()?.let(::Num)
        }
    }
}

private fun compare(subject: Subject, op: FormCondOp, value: JsonElement?): Boolean {
    when (op) {
        FormCondOp.ANSWERED -> return subject !is Subject.Absent
        FormCondOp.NOT_ANSWERED -> return subject is Subject.Absent
        else -> Unit
    }
    if (subject is Subject.Absent) return false
    val one = Scalar.of(value)
    return when (op) {
        FormCondOp.EQ -> subject.isScalar() && one != null && scalarEquals(subject, one)
        FormCondOp.NEQ -> subject.isScalar() && one != null && !scalarEquals(subject, one)
        FormCondOp.IN -> subject.isScalar() && value is JsonArray && value.any { element -> Scalar.of(element)?.let { scalarEquals(subject, it) } == true }
        FormCondOp.NOT_IN -> subject.isScalar() && value is JsonArray && value.none { element -> Scalar.of(element)?.let { scalarEquals(subject, it) } == true }
        FormCondOp.GT -> numeric(subject, one) { it > 0 }
        FormCondOp.GTE -> numeric(subject, one) { it >= 0 }
        FormCondOp.LT -> numeric(subject, one) { it < 0 }
        FormCondOp.LTE -> numeric(subject, one) { it <= 0 }
        FormCondOp.INCLUDES -> subject is Subject.TextList && one is Scalar.Text && one.value in subject.values
        FormCondOp.ANSWERED, FormCondOp.NOT_ANSWERED -> false // tratados acima
    }
}

private fun numeric(subject: Subject, one: Scalar?, test: (Int) -> Boolean): Boolean {
    if (subject !is Subject.Num || one !is Scalar.Num) return false
    return test(subject.value.compareTo(one.value))
}

/** Igualdade SEM conversão de tipo: texto com texto, número com número, booleano com booleano. */
private fun scalarEquals(subject: Subject, scalar: Scalar): Boolean = when {
    subject is Subject.Text && scalar is Scalar.Text -> subject.value == scalar.value
    subject is Subject.Num && scalar is Scalar.Num -> subject.value.compareTo(scalar.value) == 0
    subject is Subject.Bool && scalar is Scalar.Bool -> subject.value == scalar.value
    else -> false
}

private fun Subject.isScalar(): Boolean = this is Subject.Text || this is Subject.Num || this is Subject.Bool

/**
 * JSON da [FormCondition], nos dois sentidos. TOTAL na leitura (forma desconhecida = [FormCondition.Invalid],
 * com o JSON preservado), com teto de aninhamento como guarda de pilha.
 */
internal object FormConditionJson {
    /** Teto de aninhamento na leitura — o mesmo do servidor. */
    const val MAX_DEPTH: Int = 32

    private val QUESTION_KEYS = setOf("question", "op", "value")
    private val CONTEXT_KEYS = setOf("context", "op", "value")

    fun fromJson(element: JsonElement, depth: Int = 1): FormCondition {
        val obj = element as? JsonObject ?: return FormCondition.Invalid(element)
        if (depth > MAX_DEPTH) return FormCondition.Invalid(element)
        val keys = obj.keys
        return when {
            keys == setOf("all") -> list(obj.getValue("all"), depth)?.let(FormCondition::All) ?: FormCondition.Invalid(element)
            keys == setOf("any") -> list(obj.getValue("any"), depth)?.let(FormCondition::AnyOf) ?: FormCondition.Invalid(element)
            keys == setOf("not") -> {
                val inner = obj.getValue("not")
                if (inner is JsonObject) FormCondition.Not(fromJson(inner, depth + 1)) else FormCondition.Invalid(element)
            }
            "question" in keys && "context" !in keys && QUESTION_KEYS.containsAll(keys) -> {
                val questionId = obj.getValue("question").textOrNull()
                val op = FormCondOp.fromWireName(obj["op"]?.textOrNull())
                if (questionId == null || op == null) FormCondition.Invalid(element) else FormCondition.Question(questionId, op, obj["value"].nullIfJsonNull())
            }
            "context" in keys && "question" !in keys && CONTEXT_KEYS.containsAll(keys) -> {
                val key = FormContextKey.fromWireName(obj.getValue("context").textOrNull())
                val op = FormCondOp.fromWireName(obj["op"]?.textOrNull())
                if (key == null || op == null) FormCondition.Invalid(element) else FormCondition.Context(key, op, obj["value"].nullIfJsonNull())
            }
            else -> FormCondition.Invalid(element)
        }
    }

    private fun list(element: JsonElement, depth: Int): List<FormCondition>? {
        val array = element as? JsonArray ?: return null
        return array.map { fromJson(it, depth + 1) }
    }

    private fun JsonElement?.nullIfJsonNull(): JsonElement? = if (this == null || this is JsonNull) null else this

    fun toJson(condition: FormCondition): JsonElement = when (condition) {
        is FormCondition.All -> JsonObject(mapOf("all" to JsonArray(condition.conditions.map(::toJson))))
        is FormCondition.AnyOf -> JsonObject(mapOf("any" to JsonArray(condition.conditions.map(::toJson))))
        is FormCondition.Not -> JsonObject(mapOf("not" to toJson(condition.condition)))
        is FormCondition.Question -> leaf("question", condition.questionId, condition.op, condition.value)
        is FormCondition.Context -> leaf("context", condition.key.wireName, condition.op, condition.value)
        is FormCondition.Invalid -> condition.json
    }

    private fun leaf(subjectKey: String, subject: String, op: FormCondOp, value: JsonElement?): JsonObject {
        val fields = LinkedHashMap<String, JsonElement>()
        fields[subjectKey] = JsonPrimitive(subject)
        fields["op"] = JsonPrimitive(op.wireName)
        if (value != null) fields["value"] = value
        return JsonObject(fields)
    }
}

internal object FormConditionSerializer : KSerializer<FormCondition> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("br.com.codecacto.kmplib.ui.form.FormCondition")

    override fun serialize(encoder: Encoder, value: FormCondition) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("FormCondition só é escrita em JSON")
        json.encodeJsonElement(FormConditionJson.toJson(value))
    }

    override fun deserialize(decoder: Decoder): FormCondition {
        val json = decoder as? JsonDecoder ?: throw SerializationException("FormCondition só é lida de JSON")
        return FormConditionJson.fromJson(json.decodeJsonElement())
    }
}
