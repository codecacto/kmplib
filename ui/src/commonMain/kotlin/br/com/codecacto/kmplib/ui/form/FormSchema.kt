package br.com.codecacto.kmplib.ui.form

import androidx.compose.runtime.Immutable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/*
 * ============================================================================================
 *  FormSchema v1 — o formato de transporte ÚNICO de formulário (servidor, portal e app)
 * ============================================================================================
 *
 * O MESMO JSON do `backlib-forms` (servidor) e do `FormRunner` da weblib, campo a campo — o contrato
 * é o do Vitalis (`contratos-api.md` §7.1). Mudou lá, muda nas três libs na mesma rodada, e as três
 * passam nas MESMAS fixtures (`form-schema-v1.fixtures.json`, cópia sem edição do `backlib-forms`).
 *
 * Uma seção `kind: "likert"` é exatamente o `QuestionnaireBlock` do `QuestionnaireRunner` com a régua
 * `scale` = `QuestionnaireScale` e resposta numérica: o `FormRunner` delega essa seção a ele.
 *
 * Leitura TOLERANTE (o app instalado pode estar uma versão atrás do servidor): campo desconhecido é
 * ignorado ([FormJson]), tipo de pergunta desconhecido vira [FormQuestionType.UNSUPPORTED] (aviso na
 * tela, sem travar o envio — quem confere é o servidor), `display` desconhecido vira o padrão, e tipo
 * de anexo desconhecido é descartado. O que o cliente NÃO faz é decidir o que esconder: o schema já
 * vem resolvido para o público (reservada removida para a recepção, `scoring` só para o médico).
 */

/** A versão do formato que este módulo lê. */
const val FORM_SCHEMA_VERSION: Int = 1

/**
 * O formulário.
 *
 * @property id id estável da linhagem (`"preconsulta.nutrologia"`).
 * @property estimatedMinutes "Cerca de N min", no primeiro passo.
 * @property sections a ordem é a dos passos no celular.
 */
@Immutable
@Serializable
data class FormSchemaV1(
    val schemaVersion: Int = FORM_SCHEMA_VERSION,
    val id: String,
    val title: String,
    val description: String? = null,
    val estimatedMinutes: Int? = null,
    val sections: List<FormSection> = emptyList(),
) {
    /** A pergunta de topo pelo id (a primeira ocorrência, como no servidor). */
    fun findQuestion(questionId: String): FormQuestion? =
        sections.firstNotNullOfOrNull { section -> section.questions.firstOrNull { it.id == questionId } }

    /** A seção que contém a pergunta de topo. */
    fun sectionOf(questionId: String): FormSection? =
        sections.firstOrNull { section -> section.questions.any { it.id == questionId } }

    fun findSection(sectionId: String): FormSection? = sections.firstOrNull { it.id == sectionId }
}

/**
 * Uma seção — no celular, um passo.
 *
 * @property kind [FormSectionKind.LIKERT] = régua única para todas as perguntas ([scale]).
 * @property reserved EFETIVO, resolvido pelo servidor para o público (o cliente não decide o que
 *   esconder — só mostra o selo, com `markReserved`).
 * @property scoring como a seção pontua. A CONTA é só do servidor; o resultado chega como
 *   [FormScoreResult] (médico) e nunca volta ao paciente nem à recepção.
 * @property instrument escala validada (licença e citação).
 */
@Immutable
@Serializable
data class FormSection(
    val id: String,
    val title: String,
    val context: String? = null,
    val kind: FormSectionKind = FormSectionKind.FIELDS,
    val scale: FormLikertScale? = null,
    val questions: List<FormQuestion> = emptyList(),
    val visibleIf: FormCondition? = null,
    val reserved: Boolean = false,
    val scoring: FormScoring? = null,
    val instrument: FormInstrument? = null,
)

/** `fields` (uma pergunta por tipo) ou `likert` (régua única). Desconhecido = [FIELDS]. */
@Serializable(with = FormSectionKindSerializer::class)
enum class FormSectionKind(val wireName: String) {
    FIELDS("fields"),
    LIKERT("likert"),
    ;

    companion object {
        fun fromWireName(name: String?): FormSectionKind = entries.firstOrNull { it.wireName == name } ?: FIELDS
    }
}

/**
 * A régua Likert — o `QuestionnaireScale` da weblib, campo a campo. Os pontos são
 * `min, min+step, …, max` (passo default 1), no máximo [FORM_LIKERT_MAX_POINTS] ([points]).
 */
@Immutable
@Serializable
data class FormLikertScale(
    val min: FormDecimal,
    val max: FormDecimal,
    val step: FormDecimal? = null,
    val optionLabels: List<String> = emptyList(),
    val startAnchor: String? = null,
    val endAnchor: String? = null,
)

/** Teto de pontos de uma régua — o mesmo do `likertPoints` da weblib e do `LikertScale` do servidor. */
const val FORM_LIKERT_MAX_POINTS: Int = 101

/**
 * Os pontos da régua, do menor ao maior, pela conta EXATA do servidor. Vazio = régua impossível:
 * passo não positivo, limites invertidos ou iguais, passo que não fecha em `max`, ou mais de
 * [FORM_LIKERT_MAX_POINTS] pontos.
 */
fun FormLikertScale.points(): List<FormDecimal> {
    val stepValue = step ?: FormDecimal.ONE
    if (stepValue.signum <= 0 || max <= min) return emptyList()
    val scale = maxOf(min.decimalPlaces, max.decimalPlaces, stepValue.decimalPlaces)
    val low = min.scaledInteger(scale) ?: return emptyList()
    val high = max.scaledInteger(scale) ?: return emptyList()
    val increment = stepValue.scaledInteger(scale) ?: return emptyList()
    val out = ArrayList<FormDecimal>()
    var current = low
    while (true) {
        out += FormDecimal.fromScaled(current, scale)
        if (out.size > FORM_LIKERT_MAX_POINTS) return emptyList()
        if (current.compareTo(high) == 0) return out
        current += increment
        if (current > high) return emptyList()
    }
}

/**
 * O tipo de pergunta e a forma da resposta de cada um:
 * `text`/`longText`/`singleChoice`/`date` → texto (`date` = `"AAAA-MM-DD"`) · `number`/`likert` →
 * número · `multiChoice` → lista de textos · `list` → itens · `file` → `[{fileId}]` · `consent` →
 * booleano · `info` → nenhuma.
 *
 * [UNSUPPORTED] = tipo que esta versão do app não conhece (o servidor está à frente): a tela mostra um
 * aviso, o cliente não o valida nem o exige — a conferência fica com o servidor.
 */
@Serializable(with = FormQuestionTypeSerializer::class)
enum class FormQuestionType(val wireName: String) {
    TEXT("text"),
    LONG_TEXT("longText"),
    NUMBER("number"),
    DATE("date"),
    SINGLE_CHOICE("singleChoice"),
    MULTI_CHOICE("multiChoice"),
    LIKERT("likert"),
    LIST("list"),
    FILE("file"),
    CONSENT("consent"),
    INFO("info"),
    UNSUPPORTED("unsupported"),
    ;

    companion object {
        fun fromWireName(name: String?): FormQuestionType =
            entries.firstOrNull { it != UNSUPPORTED && it.wireName == name } ?: UNSUPPORTED
    }
}

/**
 * Uma pergunta. Só a configuração do PRÓPRIO tipo vale (o lint do servidor recusa o resto).
 *
 * @property id estável na linhagem (`"habitos.tabagismo"`) — chave das respostas e das reservadas.
 * @property placeholder instrução ou formato (`"Nome e dose de cada um"`) — NUNCA dado real.
 * @property required default `true` (como o `QuestionnaireRunner`); `info` ignora.
 * @property reserved EFETIVO (ver [FormSection.reserved]).
 * @property bind destino estruturado no prontuário (`"patient.medications"`) — texto livre: o conjunto
 *   é do projeto, e só vira prontuário depois de o médico confirmar (regra do projeto).
 * @property exclusiveValues `multiChoice`: a opção que desmarca as outras ("nenhuma").
 * @property maxLength `text`/`longText`. Sem ele: [FORM_TEXT_DEFAULT_MAX_LENGTH] /
 *   [FORM_LONG_TEXT_DEFAULT_MAX_LENGTH] (unidades UTF-16).
 * @property scale `likert` dentro de seção `fields` (régua própria).
 */
@Immutable
@Serializable
data class FormQuestion(
    val id: String,
    val type: FormQuestionType,
    val text: String,
    val hint: String? = null,
    val placeholder: String? = null,
    val required: Boolean = true,
    val visibleIf: FormCondition? = null,
    val reserved: Boolean = false,
    val bind: String? = null,
    val options: List<FormChoiceOption> = emptyList(),
    val display: FormChoiceDisplay? = null,
    val exclusiveValues: List<String> = emptyList(),
    val number: FormNumberConfig? = null,
    val maxLength: Int? = null,
    val date: FormDateConfig? = null,
    val list: FormListConfig? = null,
    val file: FormFileConfig? = null,
    val consent: FormConsentConfig? = null,
    val scale: FormLikertScale? = null,
)

/** Uma alternativa de escolha. [imageKey] = figura (Bristol), resolvida pelo projeto. */
@Immutable
@Serializable
data class FormChoiceOption(
    val value: String,
    val label: String,
    val imageKey: String? = null,
    /** Pontos num [FormScoring] — o paciente e a recepção não o recebem. O cliente não soma. */
    val score: FormDecimal? = null,
)

/** Como desenhar uma escolha. Desconhecido = [BUTTONS] (o desenho padrão). */
@Serializable(with = FormChoiceDisplaySerializer::class)
enum class FormChoiceDisplay(val wireName: String) {
    BUTTONS("buttons"),
    CHIPS("chips"),
    SELECT("select"),
    IMAGES("images"),
    ;

    companion object {
        fun fromWireName(name: String?): FormChoiceDisplay = entries.firstOrNull { it.wireName == name } ?: BUTTONS
    }
}

/** `number`: sem [decimals], só inteiro. [step] conta a partir de [min] (ou de 0), como no HTML. */
@Immutable
@Serializable
data class FormNumberConfig(
    val min: FormDecimal? = null,
    val max: FormDecimal? = null,
    val step: FormDecimal? = null,
    val decimals: Int? = null,
    val unit: String? = null,
) {
    val effectiveDecimals: Int get() = decimals ?: 0
}

/** Limites de `date`: `"AAAA-MM-DD"` ou `"today"` (o dia de referência de quem decide). */
@Immutable
@Serializable
data class FormDateConfig(
    val min: String? = null,
    val max: String? = null,
)

/**
 * Lista repetível ("nome e dose de cada medicamento"). Campos do item: só `text`, `number`,
 * `singleChoice` e `date` — sem lista/arquivo aninhados, sem condição, sem reserva.
 *
 * @property maxItems sem ele, o teto é [FORM_LIST_DEFAULT_MAX_ITEMS].
 */
@Immutable
@Serializable
data class FormListConfig(
    val itemLabel: String,
    val minItems: Int? = null,
    val maxItems: Int? = null,
    val fields: List<FormQuestion> = emptyList(),
)

/** O que um anexo aceita. */
enum class FormFileKind(val wireName: String) {
    IMAGE("image"),
    PDF("pdf"),
    ;

    companion object {
        fun fromWireName(name: String?): FormFileKind? = entries.firstOrNull { it.wireName == name }
    }
}

/**
 * Anexo. A resposta é a lista de `{fileId}` que a rota de upload DO PROJETO devolveu — a lib não
 * conhece rota: o envio é o callback do app ([FormFileUploader]).
 *
 * @property accept tipo de anexo desconhecido (versão nova do servidor) é descartado na leitura.
 * @property purpose texto livre (`"LAB_REPORT"`, `"MED_BOX_PHOTO"`): o conjunto é do projeto, que o
 *   manda à rota de upload.
 */
@Immutable
@Serializable
data class FormFileConfig(
    @Serializable(with = FormFileKindListSerializer::class)
    val accept: List<FormFileKind> = emptyList(),
    val maxFiles: Int,
    val maxSizeMb: FormDecimal,
    val purpose: String,
)

/** Consentimento: a versão do documento aceito e os links para lê-lo. */
@Immutable
@Serializable
data class FormConsentConfig(
    val documentVersion: String,
    val links: List<FormConsentLink> = emptyList(),
)

@Immutable
@Serializable
data class FormConsentLink(val label: String, val href: String)

/** Escala validada: código, nome, nota de licença e citação da fonte publicada. */
@Immutable
@Serializable
data class FormInstrument(
    val code: String,
    val name: String,
    val licenseNote: String = "",
    val citation: String = "",
)

/**
 * Como a seção pontua — só para EXIBIR a configuração. **A conta é do servidor** (D11): o cliente
 * nunca soma, e o resultado chega pronto como [FormScoreResult].
 *
 * @property method `sum`, `mean` ou `rule` (texto: o vocabulário é do servidor).
 * @property rule a regra do instrumento (`PHQ2`, `GAD2`, `AUDIT_C`, …), com `method = rule`.
 */
@Immutable
@Serializable
data class FormScoring(
    val method: String,
    val items: List<String>? = null,
    val reverse: List<String>? = null,
    val rule: String? = null,
    val bands: List<FormScoreBand> = emptyList(),
)

/** Faixa de interpretação `[min, max]`; [severity] 0 (sem alerta) … 3 (o mais grave). */
@Immutable
@Serializable
data class FormScoreBand(
    val min: FormDecimal,
    val max: FormDecimal,
    val label: String,
    val severity: Int,
)

/**
 * Resultado de escala calculado pelo SERVIDOR (o `ScoreResultDto` do contrato) — o runner só exibe,
 * nunca calcula. `complete = false` = faltou item: aí `band = ""` e `severity = 0`.
 */
@Immutable
@Serializable
data class FormScoreResult(
    val sectionId: String,
    val instrumentCode: String? = null,
    val label: String,
    val score: FormDecimal,
    val band: String = "",
    val severity: Int = 0,
    val complete: Boolean,
    val computedAt: String? = null,
)

/**
 * Leitura e escrita do contrato em JSON, com a tolerância que um documento vindo do servidor exige
 * (campo desconhecido ignorado, nulo explícito omitido na escrita).
 *
 * Quem decodifica o schema dentro de um DTO maior (`PublicPreconsultDto`) usa [format] — ou um `Json`
 * próprio com `ignoreUnknownKeys = true`, senão um campo novo do servidor derruba a tela.
 */
object FormJson {
    val format: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }

    fun decodeSchema(json: String): FormSchemaV1 = format.decodeFromString(FormSchemaV1.serializer(), json)

    fun encodeSchema(schema: FormSchemaV1): String = format.encodeToString(FormSchemaV1.serializer(), schema)
}

// ---------------------------------------------------------------------------------------------
// Serializadores tolerantes de enum (não dependem da configuração do `Json` de quem lê)
// ---------------------------------------------------------------------------------------------

internal object FormSectionKindSerializer : KSerializer<FormSectionKind> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("br.com.codecacto.kmplib.ui.form.FormSectionKind", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): FormSectionKind = FormSectionKind.fromWireName(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: FormSectionKind) = encoder.encodeString(value.wireName)
}

internal object FormQuestionTypeSerializer : KSerializer<FormQuestionType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("br.com.codecacto.kmplib.ui.form.FormQuestionType", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): FormQuestionType = FormQuestionType.fromWireName(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: FormQuestionType) = encoder.encodeString(value.wireName)
}

internal object FormChoiceDisplaySerializer : KSerializer<FormChoiceDisplay> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("br.com.codecacto.kmplib.ui.form.FormChoiceDisplay", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): FormChoiceDisplay = FormChoiceDisplay.fromWireName(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: FormChoiceDisplay) = encoder.encodeString(value.wireName)
}

/** `accept` com o tipo desconhecido DESCARTADO (o app não sabe escolher o que não conhece). */
internal object FormFileKindListSerializer : KSerializer<List<FormFileKind>> {
    private val delegate = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<FormFileKind> {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement() as? JsonArray
                ?: throw SerializationException("\"accept\" precisa ser uma lista")
            return element.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                .mapNotNull(FormFileKind::fromWireName)
                .distinct()
        }
        return delegate.deserialize(decoder).mapNotNull(FormFileKind::fromWireName).distinct()
    }

    override fun serialize(encoder: Encoder, value: List<FormFileKind>) =
        delegate.serialize(encoder, value.map { it.wireName })
}
