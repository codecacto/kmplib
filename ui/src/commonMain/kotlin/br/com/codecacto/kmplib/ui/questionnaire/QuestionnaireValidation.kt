package br.com.codecacto.kmplib.ui.questionnaire

import androidx.compose.runtime.Immutable

/**
 * Um defeito do DOCUMENTO do questionário (não da resposta): id repetido, condição apontando para
 * pergunta que não existe, régua impossível. É o que faz "a pergunta não aparece" ter uma explicação
 * no log em vez de virar mistério.
 *
 * @property path onde está (`blocks[2].questions[0]`, `scores[1].bands[0]`).
 * @property message o que está errado, para quem cadastrou o modelo.
 */
@Immutable
data class QuestionnaireIssue(val path: String, val message: String)

/**
 * Confere o documento. Nada aqui impede o runner de funcionar — ele tolera tudo o que esta função
 * acusa (pergunta indesenhável vira aviso e não é obrigatória; condição para id inexistente é falsa).
 * Serve ao log do app e ao teste do construtor de modelos.
 */
fun Questionnaire.validate(): List<QuestionnaireIssue> {
    val issues = ArrayList<QuestionnaireIssue>()
    val questionIds = HashSet<String>()
    val blockIds = HashSet<String>()
    val scoreIds = scores.map { it.id }.toSet()
    val allQuestionIds = blocks.flatMap { b -> b.questions.map { it.id } }.toSet()

    fun checkCondition(condition: QuestionnaireCondition?, path: String, ownId: String?) {
        if (condition == null) return
        val subjects = listOfNotNull(condition.question, condition.context, condition.score)
        if (subjects.size > 1) issues += QuestionnaireIssue(path, "condição com mais de um sujeito (question/context/score) — vale o primeiro")
        condition.question?.let { ref ->
            if (ref !in allQuestionIds) issues += QuestionnaireIssue(path, "condição aponta para a pergunta '$ref', que não existe")
            if (ref == ownId) issues += QuestionnaireIssue(path, "a pergunta '$ref' depende de si mesma (ciclo) — fica oculta")
        }
        condition.score?.let { ref ->
            if (ref !in scoreIds) issues += QuestionnaireIssue(path, "condição aponta para o escore '$ref', que não existe")
        }
        condition.all?.forEachIndexed { i, c -> checkCondition(c, "$path.all[$i]", ownId) }
        condition.any?.forEachIndexed { i, c -> checkCondition(c, "$path.any[$i]", ownId) }
        checkCondition(condition.not, "$path.not", ownId)
    }

    blocks.forEachIndexed { b, block ->
        val blockPath = "blocks[$b]"
        if (!blockIds.add(block.id)) issues += QuestionnaireIssue(blockPath, "bloco '${block.id}' repetido")
        checkCondition(block.visibleIf, "$blockPath.visibleIf", null)
        block.questions.forEachIndexed { q, question ->
            val path = "$blockPath.questions[$q]"
            if (!questionIds.add(question.id)) issues += QuestionnaireIssue(path, "pergunta '${question.id}' repetida — vale a primeira")
            when (question.type) {
                QuestionnaireQuestionType.UNSUPPORTED ->
                    issues += QuestionnaireIssue(path, "tipo de pergunta desconhecido nesta versão — exibida como aviso")
                QuestionnaireQuestionType.SCALE -> if (scaleOf(question)?.points().isNullOrEmpty()) {
                    issues += QuestionnaireIssue(path, "régua ausente ou impossível (min/max/step) — exibida como aviso")
                }
                QuestionnaireQuestionType.CHOICE, QuestionnaireQuestionType.MULTI_CHOICE -> {
                    if (question.options.isEmpty()) issues += QuestionnaireIssue(path, "escolha sem opções — exibida como aviso")
                    val values = question.options.map { it.value }
                    if (values.size != values.toSet().size) issues += QuestionnaireIssue(path, "opções com `value` repetido")
                }
                QuestionnaireQuestionType.NUMBER -> if (question.min != null && question.max != null && question.min > question.max) {
                    issues += QuestionnaireIssue(path, "min maior que max — nenhum valor é aceito")
                }
                QuestionnaireQuestionType.TEXT, QuestionnaireQuestionType.DATE -> Unit
            }
            checkCondition(question.visibleIf, "$path.visibleIf", question.id)
        }
    }

    val seenScores = HashSet<String>()
    scores.forEachIndexed { s, score ->
        val path = "scores[$s]"
        if (!seenScores.add(score.id)) issues += QuestionnaireIssue(path, "escore '${score.id}' repetido — vale o primeiro")
        score.questions?.forEach { ref ->
            if (ref !in allQuestionIds) issues += QuestionnaireIssue(path, "soma a pergunta '$ref', que não existe — o escore nunca fica completo")
        }
        score.products.forEachIndexed { p, product ->
            if (product.questions.isEmpty()) issues += QuestionnaireIssue("$path.products[$p]", "produto sem perguntas")
            product.questions.forEach { ref ->
                if (ref !in allQuestionIds) issues += QuestionnaireIssue("$path.products[$p]", "multiplica a pergunta '$ref', que não existe")
            }
        }
        score.bands.forEachIndexed { i, band ->
            val bandPath = "$path.bands[$i]"
            if (band.min != null && band.max != null && band.min > band.max) {
                issues += QuestionnaireIssue(bandPath, "faixa com min maior que max — nunca casa")
            }
            checkCondition(band.condition, "$bandPath.when", null)
        }
    }
    return issues
}
