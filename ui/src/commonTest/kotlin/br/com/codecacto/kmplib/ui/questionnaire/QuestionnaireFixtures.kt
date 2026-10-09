package br.com.codecacto.kmplib.ui.questionnaire

/** Construtores curtos para os testes do questionário. */

internal fun cond(json: String): QuestionnaireCondition =
    QuestionnaireJson.format.decodeFromString(QuestionnaireCondition.serializer(), json)

internal fun question(
    id: String,
    type: QuestionnaireQuestionType = QuestionnaireQuestionType.SCALE,
    required: Boolean = true,
    options: List<QuestionnaireOption> = emptyList(),
    scale: QuestionnaireScale? = null,
    min: Double? = null,
    max: Double? = null,
    decimals: Int = 0,
    visibleIf: QuestionnaireCondition? = null,
    reserved: Boolean = false,
    multiline: Boolean = false,
): QuestionnaireQuestion = QuestionnaireQuestion(
    id = id,
    text = "Pergunta $id",
    required = required,
    type = type,
    scale = scale,
    options = options,
    min = min,
    max = max,
    decimals = decimals,
    visibleIf = visibleIf,
    reserved = reserved,
    multiline = multiline,
)

internal fun block(
    id: String,
    vararg questions: QuestionnaireQuestion,
    visibleIf: QuestionnaireCondition? = null,
    reserved: Boolean = false,
): QuestionnaireBlock = QuestionnaireBlock(id = id, title = "Bloco $id", questions = questions.toList(), visibleIf = visibleIf, reserved = reserved)

internal fun option(value: String, score: Double? = null): QuestionnaireOption = QuestionnaireOption(value = value, label = value, score = score)

internal fun choice(id: String, vararg options: QuestionnaireOption, visibleIf: QuestionnaireCondition? = null, required: Boolean = true): QuestionnaireQuestion =
    question(id, type = QuestionnaireQuestionType.CHOICE, options = options.toList(), visibleIf = visibleIf, required = required)

/** Sim/não com pontos no "sim". */
internal fun yesNo(id: String, yesScore: Double = 1.0, visibleIf: QuestionnaireCondition? = null): QuestionnaireQuestion =
    choice(id, option("sim", yesScore), option("nao", 0.0), visibleIf = visibleIf)

internal fun number(id: String, min: Double? = null, max: Double? = null, visibleIf: QuestionnaireCondition? = null, required: Boolean = true): QuestionnaireQuestion =
    question(id, type = QuestionnaireQuestionType.NUMBER, min = min, max = max, visibleIf = visibleIf, required = required)

internal fun scale(min: Int, max: Int, vararg labels: String): QuestionnaireScale =
    QuestionnaireScale(min = min.toDouble(), max = max.toDouble(), optionLabels = labels.toList())

internal fun band(label: String, min: Double? = null, max: Double? = null, tone: QuestionnaireTone = QuestionnaireTone.NEUTRAL, condition: QuestionnaireCondition? = null) =
    QuestionnaireScoreBand(label = label, min = min, max = max, tone = tone, condition = condition)

/** Respostas a partir de pares: Int/Double → número, String → texto, List → múltipla. */
internal fun answersOf(vararg pairs: Pair<String, Any>): Map<String, QuestionnaireValue> =
    pairs.associate { (id, value) ->
        id to when (value) {
            is Int -> QuestionnaireValue.of(value)
            is Double -> QuestionnaireValue.of(value)
            is String -> QuestionnaireValue.of(value)
            is Boolean -> QuestionnaireValue.of(value)
            is List<*> -> QuestionnaireValue.of(value.map { it.toString() })
            else -> error("tipo de resposta não suportado no teste: $value")
        }
    }

internal fun QuestionnaireRunnerState.act(action: QuestionnaireRunnerAction): QuestionnaireRunnerUpdate = reduce(action)
