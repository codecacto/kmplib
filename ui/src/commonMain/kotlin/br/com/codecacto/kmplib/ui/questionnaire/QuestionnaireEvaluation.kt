package br.com.codecacto.kmplib.ui.questionnaire

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive

/**
 * Por que uma pergunta visível e aplicável ainda não pode seguir — decidido aqui, exibido no campo.
 */
sealed class QuestionnaireFieldError {
    /** Obrigatória e sem resposta (ou com resposta que não é mais uma opção válida). */
    data object Required : QuestionnaireFieldError()

    /** Número fora de [min]..[max] (qualquer ponta pode faltar). */
    data class OutOfRange(val min: Double?, val max: Double?) : QuestionnaireFieldError()
}

/**
 * O resultado de um [QuestionnaireScore].
 *
 * @property value a soma (parcial, se incompleto), arredondada na 6ª casa.
 * @property answered quantas perguntas aplicáveis do escore têm resposta válida.
 * @property total quantas perguntas do escore se aplicam (oculta por condição não conta; ausente do
 *   documento conta, e falta).
 * @property band a faixa — só quando [isComplete].
 */
@Immutable
data class QuestionnaireScoreResult(
    val score: QuestionnaireScore,
    val value: Double,
    val answered: Int,
    val total: Int,
    val band: QuestionnaireScoreBand?,
) {
    val isComplete: Boolean get() = answered == total
}

/**
 * O progresso do questionário inteiro.
 *
 * @property answered perguntas visíveis respondidas sem erro.
 * @property total perguntas visíveis que dá para responder (obrigatórias e opcionais — como a weblib).
 * @property pending perguntas visíveis com erro (obrigatória em branco, número fora da faixa).
 * @property reservedPending reservadas visíveis ainda sem resposta — o "N perguntas reservadas
 *   pendentes" do status do formulário.
 */
@Immutable
data class QuestionnaireProgress(
    val answered: Int,
    val total: Int,
    val pending: Int,
    val reservedPending: Int,
) {
    /** Nada impede concluir. */
    val isComplete: Boolean get() = pending == 0

    /** `answered / total`, `0` quando não há pergunta. */
    val fraction: Float get() = if (total == 0) 0f else answered.toFloat() / total
}

/** Um passo do questionário: o bloco e a pergunta. */
@Immutable
data class QuestionnaireStep(val blockId: String, val questionId: String)

/** Avalia o questionário contra as respostas e o contexto. Ver [QuestionnaireEvaluation]. */
fun Questionnaire.evaluate(
    answers: Map<String, QuestionnaireValue> = emptyMap(),
    context: Map<String, QuestionnaireValue> = emptyMap(),
): QuestionnaireEvaluation = QuestionnaireEvaluation(this, answers, context)

/** A régua de uma pergunta `scale`: a dela, senão a do questionário. */
fun Questionnaire.scaleOf(question: QuestionnaireQuestion): QuestionnaireScale? = question.scale ?: scale

/**
 * Pode ser respondida nesta versão: tipo conhecido e configuração desenhável (régua com pontos,
 * escolha com opções). Pergunta que não pode NUNCA é obrigatória e não entra na contagem.
 */
fun Questionnaire.isAnswerable(question: QuestionnaireQuestion): Boolean = when (question.type) {
    QuestionnaireQuestionType.SCALE -> scaleOf(question)?.points()?.isNotEmpty() == true
    QuestionnaireQuestionType.CHOICE, QuestionnaireQuestionType.MULTI_CHOICE -> question.options.isNotEmpty()
    QuestionnaireQuestionType.NUMBER, QuestionnaireQuestionType.TEXT, QuestionnaireQuestionType.DATE -> true
    QuestionnaireQuestionType.UNSUPPORTED -> false
}

/**
 * O questionário avaliado: o que aparece, o que está respondido, o que falta, quanto vale cada escore.
 *
 * **É a regra, inteira, num lugar só** — e é pura: a mesma avaliação serve à tela, ao ViewModel (que
 * decide se pode concluir) e ao teste. Imutável depois de construída: crie uma por mudança de
 * resposta ou de contexto (a tela faz isso com `remember(answers, context)`).
 *
 * ### Visibilidade
 * Bloco visível = `visibleIf` vale. Pergunta visível = bloco visível **e** o `visibleIf` dela vale.
 * Resposta de pergunta OCULTA não existe para as condições — então ocultar uma pergunta oculta, em
 * cascata, as que dependem dela (a resposta continua guardada até a conclusão, para quem volta atrás).
 * Bloco que não sobra nenhuma pergunta respondível também é pulado ([visibleBlocks]).
 *
 * Condição em ciclo (pergunta que depende de si mesma, direta ou indiretamente) trata a referência
 * circular como "sem resposta" — e `validate()` acusa.
 */
class QuestionnaireEvaluation internal constructor(
    val questionnaire: Questionnaire,
    private val answers: Map<String, QuestionnaireValue>,
    private val context: Map<String, QuestionnaireValue>,
) {

    private class Located(val block: QuestionnaireBlock, val question: QuestionnaireQuestion, val order: Int)

    private val index: Map<String, Located>
    private val scoreDefinitions: Map<String, QuestionnaireScore>
    private val blockVisibleMemo = HashMap<String, Boolean>()
    private val questionVisibleMemo = HashMap<String, Boolean>()
    private val scoreValueMemo = HashMap<String, ScoreValue>()
    private val evaluating = HashSet<String>()

    /** Blocos que aparecem: visíveis e com ao menos uma pergunta respondível visível. */
    val visibleBlocks: List<QuestionnaireBlock>

    private val visibleByBlock: Map<String, List<QuestionnaireQuestion>>
    private val navigableByBlock: Map<String, List<QuestionnaireQuestion>>
    private val usable = HashMap<String, QuestionnaireValue>()
    private val errors = HashMap<String, QuestionnaireFieldError>()

    /** Todos os escores do questionário, na ordem declarada. */
    val scores: List<QuestionnaireScoreResult>

    /** O progresso do questionário inteiro. */
    val progress: QuestionnaireProgress

    init {
        val located = LinkedHashMap<String, Located>()
        var order = 0
        questionnaire.blocks.forEach { block ->
            block.questions.forEach { question ->
                // Id repetido: vale a primeira ocorrência (o `validate()` acusa a segunda).
                if (question.id !in located) located[question.id] = Located(block, question, order)
                order++
            }
        }
        index = located
        scoreDefinitions = LinkedHashMap<String, QuestionnaireScore>().also { map ->
            questionnaire.scores.forEach { if (it.id !in map) map[it.id] = it }
        }

        val visible = LinkedHashMap<String, List<QuestionnaireQuestion>>()
        val navigable = LinkedHashMap<String, List<QuestionnaireQuestion>>()
        val blocks = ArrayList<QuestionnaireBlock>()
        val seenBlocks = HashSet<String>()
        questionnaire.blocks.forEach { block ->
            if (!seenBlocks.add(block.id)) return@forEach
            val shown = if (blockVisible(block)) {
                block.questions.filter { index[it.id]?.question === it && questionVisible(it.id) }
            } else {
                emptyList()
            }
            val answerable = shown.filter { questionnaire.isAnswerable(it) }
            visible[block.id] = shown
            navigable[block.id] = answerable
            if (answerable.isNotEmpty()) blocks += block
        }
        visibleBlocks = blocks
        visibleByBlock = visible
        navigableByBlock = navigable

        var answeredCount = 0
        var totalCount = 0
        var pendingCount = 0
        var reservedCount = 0
        navigable.values.flatten().forEach { question ->
            totalCount++
            val value = usableAnswer(question)
            if (value != null) usable[question.id] = value
            val error = computeError(question, value)
            if (error != null) {
                errors[question.id] = error
                pendingCount++
            } else if (value != null) {
                answeredCount++
            }
            if (value == null && isReserved(question.id)) reservedCount++
        }
        progress = QuestionnaireProgress(answeredCount, totalCount, pendingCount, reservedCount)
        scores = scoreDefinitions.values.map { resultOf(it) }
    }

    // -----------------------------------------------------------------------------------------
    // Consulta
    // -----------------------------------------------------------------------------------------

    /** A pergunta pelo id (a primeira, se o id se repete), visível ou não. */
    fun question(questionId: String): QuestionnaireQuestion? = index[questionId]?.question

    /** O bloco que contém a pergunta. */
    fun blockOf(questionId: String): QuestionnaireBlock? = index[questionId]?.block

    /** `true` se a pergunta existe e está visível agora. */
    fun isVisible(questionId: String): Boolean = index.containsKey(questionId) && questionVisible(questionId)

    /** Perguntas visíveis do bloco, na ordem — inclusive as que esta versão não sabe desenhar. */
    fun visibleQuestions(block: QuestionnaireBlock): List<QuestionnaireQuestion> = visibleByBlock[block.id].orEmpty()

    /** Perguntas visíveis do bloco que dá para responder — é por elas que a navegação anda. */
    fun navigableQuestions(block: QuestionnaireBlock): List<QuestionnaireQuestion> =
        navigableByBlock[block.id].orEmpty()

    /**
     * A resposta UTILIZÁVEL de uma pergunta visível: normalizada para o tipo (ponto da régua, `value`
     * de opção existente, data ISO válida, texto não vazio). `null` = sem resposta.
     */
    fun answerOf(questionId: String): QuestionnaireValue? = usable[questionId]

    fun isAnswered(questionId: String): Boolean = usable.containsKey(questionId) && !errors.containsKey(questionId)

    /** O erro da pergunta agora (independe de já ter sido exibido — quem decide exibir é a tela). */
    fun errorOf(questionId: String): QuestionnaireFieldError? = errors[questionId]

    /** Perguntas do bloco que impedem avançar, na ordem — a primeira é para onde a tela leva o foco. */
    fun pendingIn(block: QuestionnaireBlock): List<QuestionnaireQuestion> =
        navigableQuestions(block).filter { errors.containsKey(it.id) }

    /** A pergunta é reservada — por ela mesma ou pelo bloco. */
    fun isReserved(questionId: String): Boolean = index[questionId]?.let { it.question.reserved || it.block.reserved } == true

    /** Reservadas visíveis ainda sem resposta, na ordem. */
    val reservedPending: List<QuestionnaireQuestion>
        get() = visibleBlocks.flatMap { navigableQuestions(it) }.filter { isReserved(it.id) && !usable.containsKey(it.id) }

    fun score(scoreId: String): QuestionnaireScoreResult? = scores.firstOrNull { it.score.id == scoreId }

    /**
     * Os escores exibidos ao fim deste bloco: os que têm a ÚLTIMA pergunta (na ordem do documento)
     * dentro dele. É onde o escore fica completo para quem responde na ordem.
     */
    fun scoresOwnedBy(block: QuestionnaireBlock): List<QuestionnaireScoreResult> =
        scores.filter { ownerBlockId(it.score) == block.id }

    /**
     * As respostas que valem: só de perguntas visíveis, já normalizadas. É o que a conclusão grava.
     */
    val effectiveAnswers: Map<String, QuestionnaireValue>
        get() = LinkedHashMap<String, QuestionnaireValue>().also { out ->
            visibleBlocks.forEach { block -> navigableQuestions(block).forEach { q -> usable[q.id]?.let { out[q.id] = it } } }
        }

    /**
     * Perguntas que têm resposta guardada mas estão OCULTAS — a resposta deixou de se aplicar
     * ("grávida?" depois de o contexto virar masculino). A conclusão as descarta.
     */
    val hiddenAnsweredIds: List<String>
        get() = answers.keys.filter { id -> index.containsKey(id) && !questionVisible(id) }

    /**
     * O primeiro passo que ainda precisa de resposta — é por onde a retomada abre (o
     * `firstUnansweredStep` da weblib). Tudo respondido: o último bloco, na primeira pergunta.
     */
    fun firstUnansweredStep(): QuestionnaireStep? {
        visibleBlocks.forEach { block ->
            navigableQuestions(block).firstOrNull { !isAnswered(it.id) }?.let { return QuestionnaireStep(block.id, it.id) }
        }
        val last = visibleBlocks.lastOrNull() ?: return null
        val first = navigableQuestions(last).firstOrNull() ?: return null
        return QuestionnaireStep(last.id, first.id)
    }

    /** Avalia uma condição contra este estado — a mesma regra do `visibleIf` e do `when` das faixas. */
    fun holds(condition: QuestionnaireCondition): Boolean = conditionHolds(condition)

    // -----------------------------------------------------------------------------------------
    // Visibilidade (memoizada, com guarda de ciclo)
    // -----------------------------------------------------------------------------------------

    private fun blockVisible(block: QuestionnaireBlock): Boolean {
        blockVisibleMemo[block.id]?.let { return it }
        val key = "b:" + block.id
        if (!evaluating.add(key)) return false
        val result = block.visibleIf?.let { conditionHolds(it) } ?: true
        evaluating.remove(key)
        blockVisibleMemo[block.id] = result
        return result
    }

    private fun questionVisible(questionId: String): Boolean {
        questionVisibleMemo[questionId]?.let { return it }
        val located = index[questionId] ?: return false
        val key = "q:$questionId"
        if (!evaluating.add(key)) return false
        val result = blockVisible(located.block) && (located.question.visibleIf?.let { conditionHolds(it) } ?: true)
        evaluating.remove(key)
        questionVisibleMemo[questionId] = result
        return result
    }

    // -----------------------------------------------------------------------------------------
    // Respostas
    // -----------------------------------------------------------------------------------------

    /** A resposta guardada, coagida ao tipo da pergunta. Não olha visibilidade. */
    private fun usableAnswer(question: QuestionnaireQuestion): QuestionnaireValue? {
        val raw = answers[question.id] ?: return null
        return coerceQuestionnaireAnswer(questionnaire, question, raw)
    }

    private fun computeError(question: QuestionnaireQuestion, value: QuestionnaireValue?): QuestionnaireFieldError? {
        if (value == null) return if (question.required) QuestionnaireFieldError.Required else null
        if (question.type == QuestionnaireQuestionType.NUMBER) {
            val number = (value as QuestionnaireValue.Number).value
            val belowMin = question.min != null && number < question.min - EPSILON
            val aboveMax = question.max != null && number > question.max + EPSILON
            if (belowMin || aboveMax) return QuestionnaireFieldError.OutOfRange(question.min, question.max)
        }
        return null
    }

    /** O valor de uma pergunta para as CONDIÇÕES: só se visível. Número fora da faixa ainda conta. */
    private fun answerForCondition(questionId: String): QuestionnaireValue? {
        val located = index[questionId] ?: return null
        if (!questionVisible(questionId)) return null
        return usable[questionId] ?: usableAnswer(located.question)
    }

    // -----------------------------------------------------------------------------------------
    // Condições
    // -----------------------------------------------------------------------------------------

    private fun conditionHolds(condition: QuestionnaireCondition): Boolean {
        val hasSubject = condition.question != null || condition.context != null || condition.score != null
        if (hasSubject) {
            val value: QuestionnaireValue? = when {
                condition.question != null -> answerForCondition(condition.question)
                condition.context != null -> context[condition.context]?.let(::normalizedOrNull)
                else -> scoreValueFor(condition.score!!)
            }
            if (!subjectHolds(value, condition)) return false
        }
        condition.all?.let { list -> if (!list.all { conditionHolds(it) }) return false }
        condition.any?.let { list -> if (list.none { conditionHolds(it) }) return false }
        condition.not?.let { if (conditionHolds(it)) return false }
        return true
    }

    private fun subjectHolds(value: QuestionnaireValue?, c: QuestionnaireCondition): Boolean {
        val hasValueOperator = c.equalTo != null || c.notEqualTo != null || c.oneOf != null ||
            c.gt != null || c.gte != null || c.lt != null || c.lte != null || c.contains != null
        if (!hasValueOperator && c.answered == null) return value != null
        c.answered?.let { if ((value != null) != it) return false }
        if (!hasValueOperator) return true
        if (value == null) return false
        c.equalTo?.let { if (!valueEquals(value, it)) return false }
        c.notEqualTo?.let { if (valueEquals(value, it)) return false }
        c.oneOf?.let { operands -> if (!valueIn(value, operands)) return false }
        c.contains?.let { if (!valueContains(value, it)) return false }
        if (c.gt != null || c.gte != null || c.lt != null || c.lte != null) {
            val number = numericOf(value) ?: return false
            c.gt?.let { if (number <= it + EPSILON) return false }
            c.gte?.let { if (number < it - EPSILON) return false }
            c.lt?.let { if (number >= it - EPSILON) return false }
            c.lte?.let { if (number > it + EPSILON) return false }
        }
        return true
    }

    // -----------------------------------------------------------------------------------------
    // Escores
    // -----------------------------------------------------------------------------------------

    private class ScoreValue(val value: Double, val answered: Int, val total: Int)

    /** Valor de um escore para as condições: só quando completo. */
    private fun scoreValueFor(scoreId: String): QuestionnaireValue? {
        val computed = scoreValue(scoreId) ?: return null
        return if (computed.answered == computed.total) QuestionnaireValue.Number(computed.value) else null
    }

    private fun scoreValue(scoreId: String): ScoreValue? {
        scoreValueMemo[scoreId]?.let { return it }
        val definition = scoreDefinitions[scoreId] ?: return null
        val key = "s:$scoreId"
        if (!evaluating.add(key)) return null
        val result = computeScoreValue(definition)
        evaluating.remove(key)
        scoreValueMemo[scoreId] = result
        return result
    }

    private fun scoredQuestionIds(definition: QuestionnaireScore): List<String> =
        definition.questions ?: if (definition.products.isEmpty()) {
            index.values.filter { isImplicitlyScored(it.question) }.map { it.question.id }
        } else {
            emptyList()
        }

    private fun computeScoreValue(definition: QuestionnaireScore): ScoreValue {
        var sum = 0.0
        var total = 0
        var answered = 0
        val counted = HashSet<String>()

        fun count(questionId: String): Double? {
            val located = index[questionId]
            if (located == null) {
                // Ausente do documento: falta (a reservada que o servidor removeu deixa o escore pendente).
                if (counted.add(questionId)) total++
                return null
            }
            val points = pointsOf(located.question)
            if (counted.add(questionId)) {
                total++
                if (points != null) answered++
            }
            return points
        }

        scoredQuestionIds(definition).forEach { questionId ->
            val located = index[questionId]
            if (located != null) {
                if (!questionVisible(questionId) || !isScorable(located.question)) return@forEach
            }
            count(questionId)?.let { sum += it }
        }
        definition.products.forEach { product ->
            if (product.questions.isEmpty()) return@forEach
            // Fator oculto por condição: o termo não se aplica (frequência 0 esconde a duração).
            val notApplicable = product.questions.any { id -> index.containsKey(id) && !questionVisible(id) }
            if (notApplicable) return@forEach
            var term = product.weight
            var complete = true
            product.questions.forEach { id ->
                val points = count(id)
                if (points == null) complete = false else term *= points
            }
            if (complete) sum += term
        }
        return ScoreValue(roundQuestionnaireNumber(sum), answered, total)
    }

    private fun resultOf(definition: QuestionnaireScore): QuestionnaireScoreResult {
        val computed = scoreValue(definition.id) ?: ScoreValue(0.0, 0, 1)
        val complete = computed.answered == computed.total
        val band = if (complete) {
            definition.bands.firstOrNull { band ->
                (band.min == null || computed.value >= band.min - EPSILON) &&
                    (band.max == null || computed.value <= band.max + EPSILON) &&
                    (band.condition?.let { conditionHolds(it) } ?: true)
            }
        } else {
            null
        }
        return QuestionnaireScoreResult(definition, computed.value, computed.answered, computed.total, band)
    }

    private fun ownerBlockId(definition: QuestionnaireScore): String? {
        val ids = scoredQuestionIds(definition) + definition.products.flatMap { it.questions }
        return ids.mapNotNull { index[it] }.maxByOrNull { it.order }?.block?.id
    }

    /** Pergunta que entra na soma "de todas" (sem `questions` nem `products` no escore). */
    private fun isImplicitlyScored(question: QuestionnaireQuestion): Boolean = when (question.type) {
        QuestionnaireQuestionType.SCALE -> questionnaire.isAnswerable(question)
        QuestionnaireQuestionType.CHOICE, QuestionnaireQuestionType.MULTI_CHOICE -> question.options.any { it.score != null }
        else -> false
    }

    /** Pergunta que pode entrar numa soma explícita (`number` entra: dias, minutos). */
    private fun isScorable(question: QuestionnaireQuestion): Boolean = when (question.type) {
        QuestionnaireQuestionType.SCALE, QuestionnaireQuestionType.CHOICE,
        QuestionnaireQuestionType.MULTI_CHOICE, QuestionnaireQuestionType.NUMBER -> questionnaire.isAnswerable(question)
        else -> false
    }

    /** Pontos da resposta; `null` = sem resposta válida (inclusive número fora da faixa). */
    private fun pointsOf(question: QuestionnaireQuestion): Double? {
        if (!questionVisible(question.id)) return null
        val value = usable[question.id] ?: usableAnswer(question) ?: return null
        return when (question.type) {
            QuestionnaireQuestionType.SCALE -> (value as? QuestionnaireValue.Number)?.value
            QuestionnaireQuestionType.CHOICE -> {
                val chosen = (value as? QuestionnaireValue.Text)?.value
                question.options.firstOrNull { it.value == chosen }?.let { it.score ?: 0.0 }
            }
            QuestionnaireQuestionType.MULTI_CHOICE -> (value as? QuestionnaireValue.Choices)?.values
                ?.sumOf { chosen -> question.options.firstOrNull { it.value == chosen }?.score ?: 0.0 }
            QuestionnaireQuestionType.NUMBER -> {
                val number = (value as? QuestionnaireValue.Number)?.value ?: return null
                if (computeError(question, value) != null) null else number
            }
            else -> null
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Regras puras de valor — compartilhadas com o reducer
// ---------------------------------------------------------------------------------------------

private const val EPSILON = 1e-9

/** Texto em branco e lista vazia são "sem resposta"; número não finito também. */
internal fun normalizedOrNull(value: QuestionnaireValue): QuestionnaireValue? = when (value) {
    is QuestionnaireValue.Text -> value.takeIf { it.value.isNotBlank() }
    is QuestionnaireValue.Choices -> value.takeIf { it.values.isNotEmpty() }
    is QuestionnaireValue.Number -> value.takeIf { it.value.isFinite() }
}

/**
 * Coage uma resposta ao tipo da pergunta. `null` = não é uma resposta válida para ela (opção que não
 * existe mais, ponto fora da régua, data ilegível, texto em branco).
 */
internal fun coerceQuestionnaireAnswer(
    questionnaire: Questionnaire,
    question: QuestionnaireQuestion,
    raw: QuestionnaireValue,
): QuestionnaireValue? = when (question.type) {
    QuestionnaireQuestionType.SCALE -> {
        val number = numericOf(raw)
        val points = questionnaire.scaleOf(question)?.points().orEmpty()
        points.firstOrNull { number != null && abs(it - number) < 1e-6 }?.let { QuestionnaireValue.Number(it) }
    }
    QuestionnaireQuestionType.CHOICE -> {
        val scalar = scalarOf(raw)
        question.options.firstOrNull { scalar != null && sameScalar(it.value, scalar) }?.let { QuestionnaireValue.Text(it.value) }
    }
    QuestionnaireQuestionType.MULTI_CHOICE -> {
        val chosen = when (raw) {
            is QuestionnaireValue.Choices -> raw.values
            is QuestionnaireValue.Text -> listOf(raw.value)
            is QuestionnaireValue.Number -> listOf(scalarOf(raw).orEmpty())
        }
        // Ordem das OPÇÕES, não a do toque: a mesma seleção grava sempre a mesma lista.
        val values = question.options.filter { option -> chosen.any { sameScalar(option.value, it) } }.map { it.value }
        if (values.isEmpty()) null else QuestionnaireValue.Choices(values)
    }
    QuestionnaireQuestionType.NUMBER -> numericOf(raw)?.takeIf { it.isFinite() }?.let { QuestionnaireValue.Number(it) }
    QuestionnaireQuestionType.TEXT -> when (raw) {
        is QuestionnaireValue.Text -> raw.takeIf { it.value.isNotBlank() }
        is QuestionnaireValue.Number -> QuestionnaireValue.Text(scalarOf(raw).orEmpty())
        is QuestionnaireValue.Choices -> null
    }
    QuestionnaireQuestionType.DATE -> (raw as? QuestionnaireValue.Text)?.value?.trim()
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.let { QuestionnaireValue.Text(it.toString()) }
    QuestionnaireQuestionType.UNSUPPORTED -> null
}

/** Número da resposta (texto numérico também conta — `"3"` de uma opção vale 3). */
internal fun numericOf(value: QuestionnaireValue): Double? = when (value) {
    is QuestionnaireValue.Number -> value.value
    is QuestionnaireValue.Text -> value.value.trim().toDoubleOrNull()
    is QuestionnaireValue.Choices -> null
}

/** Forma textual de um valor escalar (número inteiro sem ",0"). `null` para lista. */
internal fun scalarOf(value: QuestionnaireValue): String? = when (value) {
    is QuestionnaireValue.Text -> value.value
    is QuestionnaireValue.Number -> (value.toJsonElement() as JsonPrimitive).content
    is QuestionnaireValue.Choices -> null
}

/** Dois escalares iguais — como texto, ou como número quando os dois são numéricos (`"1"` = `1.0`). */
internal fun sameScalar(a: String, b: String): Boolean {
    if (a == b) return true
    val x = a.trim().toDoubleOrNull() ?: return false
    val y = b.trim().toDoubleOrNull() ?: return false
    return abs(x - y) < EPSILON
}

private fun valueEquals(value: QuestionnaireValue, operand: JsonPrimitive): Boolean = when (value) {
    is QuestionnaireValue.Choices -> value.values.size == 1 && sameScalar(value.values[0], operand.content)
    else -> scalarOf(value)?.let { sameScalar(it, operand.content) } == true
}

private fun valueIn(value: QuestionnaireValue, operands: List<JsonPrimitive>): Boolean = when (value) {
    is QuestionnaireValue.Choices -> value.values.any { chosen -> operands.any { sameScalar(chosen, it.content) } }
    else -> operands.any { valueEquals(value, it) }
}

private fun valueContains(value: QuestionnaireValue, operand: JsonPrimitive): Boolean = when (value) {
    is QuestionnaireValue.Choices -> value.values.any { sameScalar(it, operand.content) }
    else -> valueEquals(value, operand)
}
