@file:Suppress("DEPRECATION")

package br.com.codecacto.kmplib.ui.questionnaire

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.round
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/*
 * ============================================================================================
 *  O CONTRATO — o mesmo JSON que o `QuestionnaireRunner` da weblib consome (GAP-VIT-K05)
 * ============================================================================================
 *
 * Um questionário é **modelo → bloco → pergunta**, e o servidor manda o MESMO documento para a web e
 * para o app. A base é exatamente a da weblib (`QuestionnaireBlock`/`QuestionnaireQuestion`/
 * `QuestionnaireScale`, `@codecacto/weblib/ui`): `blocks[].{id,title,context,questions[]}`,
 * `questions[].{id,text,required,hint}` e a régua `scale.{min,max,step,optionLabels,startAnchor,
 * endAnchor}`. Um documento escrito para a weblib de hoje roda aqui sem mudar uma vírgula.
 *
 * O que este lado ACRESCENTA é todo opcional, com default que reproduz o comportamento de lá (uma
 * régua Likert por pergunta, sem condição, sem pontuação):
 *
 * - `type` por pergunta (`scale` — o default, a régua de sempre — `choice`, `multi-choice`,
 *   `number`, `text`, `date`), com `options`, `min`/`max`/`decimals`/`unit`, `multiline`/`maxLength`
 *   e uma `scale` própria por pergunta;
 * - `visibleIf` em bloco e em pergunta (condição sobre resposta anterior, sobre o CONTEXTO que o app
 *   informa — sexo, idade — ou sobre um escore);
 * - `scores[]` no questionário: soma ponderada com faixas (`bands`), para escalas validadas;
 * - `reserved` em bloco e em pergunta: a marca de "perguntar pessoalmente". **Quem filtra é o
 *   servidor** — a pergunta reservada some do documento de quem não pode vê-la; o runner só exibe a
 *   marca quando ela vem.
 *
 * Tipo de pergunta que o app não conhece (versão nova do servidor) NÃO derruba a leitura: vira
 * [QuestionnaireQuestionType.UNSUPPORTED], aparece como aviso e não trava o envio.
 *
 * ⚠️ **Depreciado na 2.268.0 (D11 do Vitalis): tudo o que é EXTENSÃO acima** — `type` e a configuração
 * de cada tipo, `options` com `score`, `visibleIf`, `reserved`, `scores[]` e a parte de escore da
 * avaliação. O formato canônico de formulário nas três libs é o **FormSchema v1**, que roda no
 * `FormRunner` (`br.com.codecacto.kmplib.ui.form`), e **pontuação clínica é só do servidor**. Quem já
 * usa continua compilando e funcionando (aviso de compilação); documento novo vai no FormSchema v1.
 * O `QuestionnaireRunner` Likert puro — o documento da weblib, sem extensão — continua.
 */

/**
 * O questionário inteiro — o envelope que a weblib recebe em duas props (`blocks` e `scale`).
 *
 * @property blocks os blocos, na ordem de exibição. No celular, cada bloco é uma tela.
 * @property scale a régua padrão das perguntas `scale` sem régua própria. Obrigatória na weblib
 *   (prop `scale`); aqui é opcional porque um formulário sem nenhuma pergunta `scale` não precisa dela.
 * @property scores escores calculados a partir das respostas (soma ponderada + faixas).
 * @property id identificador do modelo (informativo; o runner não o usa).
 * @property title título do modelo (informativo; quem mostra é a tela do app).
 */
@Immutable
@Serializable
data class Questionnaire(
    val blocks: List<QuestionnaireBlock> = emptyList(),
    val scale: QuestionnaireScale? = null,
    @Deprecated(DEPRECATED_SCORE)
    val scores: List<QuestionnaireScore> = emptyList(),
    val id: String? = null,
    val title: String? = null,
)

/**
 * Um bloco — a categoria de perguntas (no celular, uma tela; "Sono", "Hábitos").
 *
 * @property context frase de contexto do instrumento ("Nas últimas duas semanas…").
 * @property visibleIf o bloco só existe quando a condição vale (ex.: só para o contexto `sexo = F`).
 *   Bloco oculto não conta no progresso e é pulado na navegação.
 * @property reserved todas as perguntas do bloco são reservadas ("reservar o bloco inteiro").
 */
@Immutable
@Serializable
data class QuestionnaireBlock(
    val id: String,
    val title: String = "",
    val context: String? = null,
    val questions: List<QuestionnaireQuestion> = emptyList(),
    @Deprecated(DEPRECATED_CONDITION)
    val visibleIf: QuestionnaireCondition? = null,
    @Deprecated(DEPRECATED_RESERVED)
    val reserved: Boolean = false,
)

/**
 * Uma pergunta.
 *
 * Os quatro primeiros campos são os da weblib e valem para todo tipo. Os demais são opcionais e só
 * fazem sentido no tipo indicado.
 *
 * @property required default `true` (como na weblib): só pergunta obrigatória barra o avanço.
 * @property type o tipo; default [QuestionnaireQuestionType.SCALE] — a régua Likert de sempre.
 * @property scale régua própria (tipo `scale`) — IPSS tem 0–5 nos itens e 0–6 na qualidade de vida.
 *   Sem ela, vale a `scale` do questionário.
 * @property options alternativas (`choice`/`multi-choice`), cada uma com pontos opcionais.
 * @property min menor valor aceito (`number`), inclusivo.
 * @property max maior valor aceito (`number`), inclusivo.
 * @property decimals casas decimais aceitas (`number`); `0` = só inteiro.
 * @property unit unidade exibida no campo (`number`: "kg", "cm", "min/dia").
 * @property multiline caixa de várias linhas (`text`).
 * @property maxLength teto de caracteres (`text`).
 * @property visibleIf a pergunta só existe quando a condição vale.
 * @property reserved pergunta reservada — exibida com a marca "pendente" enquanto não respondida.
 */
@Immutable
@Serializable
data class QuestionnaireQuestion(
    val id: String,
    val text: String = "",
    val required: Boolean = true,
    val hint: String? = null,
    @Deprecated(DEPRECATED_QUESTION_TYPE)
    val type: QuestionnaireQuestionType = QuestionnaireQuestionType.SCALE,
    val scale: QuestionnaireScale? = null,
    @Deprecated(DEPRECATED_QUESTION_TYPE)
    val options: List<QuestionnaireOption> = emptyList(),
    @Deprecated(DEPRECATED_QUESTION_TYPE)
    val min: Double? = null,
    @Deprecated(DEPRECATED_QUESTION_TYPE)
    val max: Double? = null,
    @Deprecated(DEPRECATED_QUESTION_TYPE)
    val decimals: Int = 0,
    @Deprecated(DEPRECATED_QUESTION_TYPE)
    val unit: String? = null,
    @Deprecated(DEPRECATED_QUESTION_TYPE)
    val multiline: Boolean = false,
    @Deprecated(DEPRECATED_QUESTION_TYPE)
    val maxLength: Int? = null,
    @Deprecated(DEPRECATED_CONDITION)
    val visibleIf: QuestionnaireCondition? = null,
    @Deprecated(DEPRECATED_RESERVED)
    val reserved: Boolean = false,
)

/**
 * Tipo de pergunta. No fio é texto em kebab-case (o mesmo estilo de `pace: "one-by-one"` da
 * weblib). Valor desconhecido vira [UNSUPPORTED] em vez de exceção — o servidor pode estar uma
 * versão à frente do app instalado.
 */
@Deprecated(DEPRECATED_QUESTION_TYPE)
@Serializable(with = QuestionnaireQuestionTypeSerializer::class)
enum class QuestionnaireQuestionType(val wireName: String) {
    /** Régua Likert — o tipo da weblib. Resposta: número (o ponto). */
    SCALE("scale"),

    /** Uma alternativa. Resposta: o `value` da opção (texto). */
    CHOICE("choice"),

    /** Várias alternativas. Resposta: lista de `value`. */
    MULTI_CHOICE("multi-choice"),

    /** Número. Resposta: número. */
    NUMBER("number"),

    /** Texto livre. Resposta: texto. */
    TEXT("text"),

    /** Data. Resposta: texto ISO-8601 (`2026-10-08`). */
    DATE("date"),

    /** Tipo que esta versão não conhece: aparece como aviso, nunca é obrigatório. */
    UNSUPPORTED("unsupported"),
    ;

    companion object {
        /** Tipo a partir do nome do fio; vazio = [SCALE] (o default da weblib), desconhecido = [UNSUPPORTED]. */
        fun fromWireName(name: String?): QuestionnaireQuestionType =
            if (name.isNullOrBlank()) SCALE else entries.firstOrNull { it.wireName == name } ?: UNSUPPORTED
    }
}

/**
 * A régua de uma pergunta `scale` — o `QuestionnaireScale` da weblib, campo a campo.
 *
 * @property optionLabels rótulo de cada ponto, do menor ao maior (`optionLabels[0]` = [min]).
 */
@Immutable
@Serializable
data class QuestionnaireScale(
    val min: Double = 1.0,
    val max: Double = 5.0,
    val step: Double = 1.0,
    val optionLabels: List<String> = emptyList(),
    val startAnchor: String? = null,
    val endAnchor: String? = null,
)

/**
 * Mensagens das depreciações da 2.268.0 (D11 do Vitalis): o FormSchema v1 é o formato canônico de
 * formulário nas três libs, e a extensão que o questionário ganhou na 2.265.0 migra para o
 * `FormRunner` (`br.com.codecacto.kmplib.ui.form`). O `QuestionnaireRunner` continua — Likert puro,
 * compatível com o documento da weblib.
 */
internal const val DEPRECATED_QUESTION_TYPE: String =
    "Tipo de pergunta é do FormSchema v1 (D11): use o FormRunner (br.com.codecacto.kmplib.ui.form) com " +
        "FormQuestion.type. O QuestionnaireRunner fica só com a régua Likert, como o da weblib."
internal const val DEPRECATED_CONDITION: String =
    "Condição é do FormSchema v1 (D11): use o FormRunner com FormSection.visibleIf/FormQuestion.visibleIf " +
        "(FormCondition), avaliada em cascata como no servidor."
internal const val DEPRECATED_RESERVED: String =
    "Reservada é do FormSchema v1 (D11), resolvida pelo SERVIDOR para o público: use o FormRunner com " +
        "FormRunnerState.markReserved (FormSection.reserved/FormQuestion.reserved)."
internal const val DEPRECATED_SCORE: String =
    "Pontuação clínica é só do servidor (D11): o cliente exibe o resultado pronto (FormScoreResult em " +
        "FormRunnerState.scores, o ScoreResultDto do contrato) e nunca soma."

/** Teto de pontos de uma régua — o mesmo guarda-chuva contra dado absurdo que a weblib usa. */
const val QUESTIONNAIRE_MAX_SCALE_POINTS: Int = 101

/**
 * Pontos da régua, do menor ao maior — a mesma conta do `likertPoints(min, max, step)` da weblib
 * (tolerância de ponto flutuante na ponta, para o `step` 0,5 não perder o último ponto).
 *
 * Lista vazia = régua impossível: limites invertidos, `step` não positivo, valor não finito, um ponto
 * só (não é escolha, é afirmação — a mesma regra do `LikertScaleField`) ou mais que
 * [QUESTIONNAIRE_MAX_SCALE_POINTS].
 */
fun QuestionnaireScale.points(): List<Double> {
    if (!min.isFinite() || !max.isFinite() || !step.isFinite()) return emptyList()
    if (step <= 0.0 || max < min) return emptyList()
    val tolerance = step / 1000
    val points = ArrayList<Double>()
    var index = 0
    while (true) {
        // Multiplicar em vez de acumular: a soma repetida de 0,1 erra na décima casa já no 3º ponto.
        val value = min + step * index
        if (value > max + tolerance) break
        points += roundQuestionnaireNumber(value)
        index++
        if (points.size > QUESTIONNAIRE_MAX_SCALE_POINTS) return emptyList()
    }
    return if (points.size < 2) emptyList() else points
}

/**
 * Uma alternativa de `choice`/`multi-choice`.
 *
 * @property value o que vai para a resposta. Número no fio (`"value": 0`) é aceito e lido como texto.
 * @property label o que a pessoa lê. Sem ele, aparece o [value].
 * @property score pontos da alternativa num escore (ver [QuestionnaireScore]). Sem ele, 0.
 */
@Deprecated(DEPRECATED_QUESTION_TYPE)
@Immutable
@Serializable
data class QuestionnaireOption(
    @Serializable(with = QuestionnaireLenientStringSerializer::class)
    val value: String,
    val label: String? = null,
    val score: Double? = null,
) {
    /** O texto exibido: [label] quando há, senão o [value]. */
    val displayLabel: String get() = label?.takeIf { it.isNotBlank() } ?: value
}

/**
 * Condição de exibição (`visibleIf`) ou de faixa (`bands[].when`).
 *
 * **Sujeito** — no máximo um: [question] (resposta de uma pergunta VISÍVEL), [context] (um valor que o
 * app informa ao runner: `sexo`, `idade`…) ou [score] (o valor de um escore COMPLETO).
 *
 * **Operadores** — todos os informados precisam valer (E): [equalTo] (`equals`), [notEqualTo]
 * (`notEquals`), [oneOf] (`in`), [gt], [gte], [lt], [lte], [contains] e [answered]. Sujeito sem
 * operador = "respondida".
 *
 * **Combinação** — [all] (todas), [any] (alguma) e [not] (nega). Valem junto com o sujeito (E).
 * Condição vazia (`{}`) vale sempre.
 *
 * **Sem resposta, toda comparação é FALSA** — inclusive `notEquals`. "Mostrar o item 2 se o item 1
 * não for 'nunca'" é `{"question":"q1","notEquals":"nunca"}`: falso enquanto q1 está em branco, que é
 * o que se quer. `{"not": {"question":"q1","equals":"nunca"}}` seria verdadeiro com q1 em branco.
 *
 * Exemplos no fio:
 * ```json
 * {"question": "fuma", "equals": "sim"}
 * {"context": "sexo", "equals": "F"}
 * {"all": [{"context": "sexo", "equals": "M"}, {"context": "idade", "gte": 40}]}
 * {"question": "comorbidades", "contains": "has"}
 * {"score": "phq2", "gte": 3}
 * ```
 */
@Deprecated(DEPRECATED_CONDITION)
@Immutable
@Serializable
data class QuestionnaireCondition(
    val question: String? = null,
    val context: String? = null,
    val score: String? = null,
    @SerialName("equals") val equalTo: JsonPrimitive? = null,
    @SerialName("notEquals") val notEqualTo: JsonPrimitive? = null,
    @SerialName("in") val oneOf: List<JsonPrimitive>? = null,
    val gt: Double? = null,
    val gte: Double? = null,
    val lt: Double? = null,
    val lte: Double? = null,
    val contains: JsonPrimitive? = null,
    val answered: Boolean? = null,
    val all: List<QuestionnaireCondition>? = null,
    val any: List<QuestionnaireCondition>? = null,
    val not: QuestionnaireCondition? = null,
)

/**
 * Um escore — soma ponderada das respostas, com faixas de interpretação.
 *
 * **O que entra na soma:**
 * - [questions] — os ids somados. Cada pergunta vale: `scale` → o ponto escolhido; `choice` → o
 *   `score` da opção; `multi-choice` → a soma dos `score` marcados; `number` → o próprio número.
 * - [products] — termos `peso × resposta × resposta` (frequência × duração: o MET-min/semana do IPAQ).
 * - Sem [questions] e sem [products], soma **todas** as perguntas `scale` e as `choice`/
 *   `multi-choice` com pontos (o caso do instrumento de uma régua só).
 *
 * **Completo** só com todas as perguntas aplicáveis respondidas (oculta por condição = não
 * aplicável; ausente do documento = faltando — é o caso da reservada, que o servidor removeu). Escore
 * incompleto tem valor parcial e **não tem faixa**: 2 de 3 itens não dizem "negativo".
 *
 * A primeira [bands] cujo intervalo (e `when`, se houver) vale é a faixa.
 */
@Deprecated(DEPRECATED_SCORE)
@Immutable
@Serializable
data class QuestionnaireScore(
    val id: String,
    val label: String? = null,
    val questions: List<String>? = null,
    val products: List<QuestionnaireScoreProduct> = emptyList(),
    val bands: List<QuestionnaireScoreBand> = emptyList(),
)

/** Termo `weight × Π(resposta)` de um escore. Fator oculto por condição zera o termo (não se aplica). */
@Deprecated(DEPRECATED_SCORE)
@Immutable
@Serializable
data class QuestionnaireScoreProduct(
    val questions: List<String> = emptyList(),
    val weight: Double = 1.0,
)

/**
 * Faixa de interpretação de um escore: [min]..[max] inclusivos (qualquer ponta pode faltar) e,
 * opcionalmente, uma condição ([condition], `when` no fio) — "≥ 4 em homens, ≥ 3 em mulheres" são
 * duas faixas com `when` sobre o contexto.
 */
@Deprecated(DEPRECATED_SCORE)
@Immutable
@Serializable
data class QuestionnaireScoreBand(
    val label: String,
    val min: Double? = null,
    val max: Double? = null,
    val tone: QuestionnaireTone = QuestionnaireTone.NEUTRAL,
    @SerialName("when") val condition: QuestionnaireCondition? = null,
)

/** Tom de uma faixa — vira o tom semântico do tema (`StatusTone`). Desconhecido = [NEUTRAL]. */
@Deprecated(DEPRECATED_SCORE)
@Serializable(with = QuestionnaireToneSerializer::class)
enum class QuestionnaireTone(val wireName: String) {
    NEUTRAL("neutral"),
    SUCCESS("success"),
    INFO("info"),
    WARNING("warning"),
    DANGER("danger"),
    ;

    companion object {
        fun fromWireName(name: String?): QuestionnaireTone = entries.firstOrNull { it.wireName == name } ?: NEUTRAL
    }
}

/**
 * Uma resposta. No fio é o valor cru, sem envelope — `{"q1": 2, "q2": "sim", "q3": ["has","dm"]}` —,
 * e a régua (`scale`) responde **número**, exatamente como a weblib (`Record<string, number>`).
 *
 * - [Number]: `scale` e `number`.
 * - [Text]: `choice` (o `value` da opção), `text` e `date` (ISO-8601).
 * - [Choices]: `multi-choice`.
 */
@Immutable
@Serializable(with = QuestionnaireValueSerializer::class)
sealed class QuestionnaireValue {
    @Immutable
    data class Number(val value: Double) : QuestionnaireValue()

    @Immutable
    data class Text(val value: String) : QuestionnaireValue()

    @Immutable
    data class Choices(val values: List<String>) : QuestionnaireValue()

    companion object {
        fun of(value: Double): QuestionnaireValue = Number(value)
        fun of(value: Int): QuestionnaireValue = Number(value.toDouble())
        fun of(value: String): QuestionnaireValue = Text(value)

        /** Booleano de CONTEXTO (`gestante`): vira o texto `"true"`/`"false"`, que é o que `equals: true` compara. */
        fun of(value: Boolean): QuestionnaireValue = Text(value.toString())
        fun of(values: List<String>): QuestionnaireValue = Choices(values)

        /**
         * Lê um valor do fio. `null` = ausente ou forma que não é resposta (objeto, `null`).
         * Número não finito também é descartado.
         */
        fun fromJson(element: JsonElement): QuestionnaireValue? = when (element) {
            is JsonNull -> null
            is JsonPrimitive -> when {
                element.isString -> Text(element.content)
                element.booleanOrNull != null -> Text(element.content)
                else -> element.doubleOrNull?.takeIf { it.isFinite() }?.let { Number(it) }
            }
            is JsonArray -> Choices(element.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content })
            is JsonObject -> null
        }
    }
}

/**
 * O valor no fio. Número inteiro sai SEM casa decimal (`2`, não `2.0`) — é o que o JavaScript
 * escreve, e o documento das duas plataformas fica idêntico byte a byte.
 */
fun QuestionnaireValue.toJsonElement(): JsonElement = when (this) {
    is QuestionnaireValue.Number -> {
        val rounded = round(value)
        if (value == rounded && abs(value) < MAX_SAFE_INTEGER) JsonPrimitive(rounded.toLong()) else JsonPrimitive(value)
    }
    is QuestionnaireValue.Text -> JsonPrimitive(value)
    is QuestionnaireValue.Choices -> JsonArray(values.map { JsonPrimitive(it) })
}

/** O maior inteiro que um `number` do JavaScript representa sem perda (2^53). */
private const val MAX_SAFE_INTEGER = 9_007_199_254_740_992.0

/**
 * Um item do lote de salvamento — o `AnswerBatchItem` da weblib (`{questionId, value}`).
 *
 * `value` nulo (ou ausente no fio) = a resposta foi APAGADA (texto esvaziado, número apagado, opções
 * todas desmarcadas). É a única extensão do item: na weblib a régua não tem como apagar.
 */
@Immutable
@Serializable
data class QuestionnaireAnswerItem(
    val questionId: String,
    val value: QuestionnaireValue? = null,
)

/**
 * Leitura e escrita do contrato em JSON, com a configuração tolerante que um documento vindo do
 * servidor exige: campo desconhecido é ignorado (o servidor pode estar uma versão à frente) e
 * enum desconhecido não derruba a leitura.
 */
object QuestionnaireJson {

    /** O [Json] do contrato. Também serve para quem decodifica o questionário dentro de um DTO maior. */
    val format: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = false
    }

    fun decode(json: String): Questionnaire = format.decodeFromString(Questionnaire.serializer(), json)

    fun encode(questionnaire: Questionnaire): String =
        format.encodeToString(Questionnaire.serializer(), questionnaire)

    /** Respostas no formato da weblib (`{"q1": 2}`). Entrada nula ou de forma estranha é descartada. */
    fun decodeAnswers(json: String): Map<String, QuestionnaireValue> = answersFrom(format.parseToJsonElement(json))

    fun encodeAnswers(answers: Map<String, QuestionnaireValue>): String = answersToJson(answers).toString()

    fun answersFrom(element: JsonElement): Map<String, QuestionnaireValue> {
        val obj = element as? JsonObject ?: return emptyMap()
        val out = LinkedHashMap<String, QuestionnaireValue>()
        obj.forEach { (id, raw) -> QuestionnaireValue.fromJson(raw)?.let { out[id] = it } }
        return out
    }

    fun answersToJson(answers: Map<String, QuestionnaireValue>): JsonObject =
        JsonObject(answers.mapValues { (_, value) -> value.toJsonElement() })
}

/**
 * Serializador tolerante do mapa de respostas, para usar num DTO do app:
 * `@Serializable(with = QuestionnaireAnswersSerializer::class) val respostas: Map<String, QuestionnaireValue>`.
 * Diferente do mapa padrão, resposta `null` no fio não derruba a leitura — é descartada.
 */
object QuestionnaireAnswersSerializer : KSerializer<Map<String, QuestionnaireValue>> {
    override val descriptor: SerialDescriptor =
        MapSerializer(String.serializer(), JsonElement.serializer()).descriptor

    override fun deserialize(decoder: Decoder): Map<String, QuestionnaireValue> {
        val input = decoder as? JsonDecoder
            ?: throw SerializationException("Respostas de questionário só são lidas de JSON")
        return QuestionnaireJson.answersFrom(input.decodeJsonElement())
    }

    override fun serialize(encoder: Encoder, value: Map<String, QuestionnaireValue>) {
        val output = encoder as? JsonEncoder
            ?: throw SerializationException("Respostas de questionário só são gravadas em JSON")
        output.encodeJsonElement(QuestionnaireJson.answersToJson(value))
    }
}

// ---------------------------------------------------------------------------------------------
// Serializadores internos do fio
// ---------------------------------------------------------------------------------------------

internal object QuestionnaireValueSerializer : KSerializer<QuestionnaireValue> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("br.com.codecacto.kmplib.ui.questionnaire.QuestionnaireValue")

    override fun deserialize(decoder: Decoder): QuestionnaireValue {
        val input = decoder as? JsonDecoder
            ?: throw SerializationException("QuestionnaireValue só é lido de JSON")
        val element = input.decodeJsonElement()
        return QuestionnaireValue.fromJson(element)
            ?: throw SerializationException("Resposta de questionário inválida: $element")
    }

    override fun serialize(encoder: Encoder, value: QuestionnaireValue) {
        val output = encoder as? JsonEncoder
            ?: throw SerializationException("QuestionnaireValue só é gravado em JSON")
        output.encodeJsonElement(value.toJsonElement())
    }
}

/** Texto que também aceita número no fio (`"value": 0` vira `"0"`) — opção escrita à mão no banco. */
internal object QuestionnaireLenientStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("br.com.codecacto.kmplib.ui.questionnaire.LenientString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element is JsonPrimitive && element !is JsonNull) return element.content
            throw SerializationException("Esperava texto ou número, veio $element")
        }
        return decoder.decodeString()
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

internal object QuestionnaireQuestionTypeSerializer : KSerializer<QuestionnaireQuestionType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("br.com.codecacto.kmplib.ui.questionnaire.QuestionnaireQuestionType", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): QuestionnaireQuestionType =
        QuestionnaireQuestionType.fromWireName(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: QuestionnaireQuestionType) = encoder.encodeString(value.wireName)
}

internal object QuestionnaireToneSerializer : KSerializer<QuestionnaireTone> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("br.com.codecacto.kmplib.ui.questionnaire.QuestionnaireTone", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): QuestionnaireTone = QuestionnaireTone.fromWireName(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: QuestionnaireTone) = encoder.encodeString(value.wireName)
}

/** Arredonda na 6ª casa — a mesma precisão do `toFixed(6)` da weblib. */
internal fun roundQuestionnaireNumber(value: Double): Double = round(value * 1_000_000.0) / 1_000_000.0
