package br.com.codecacto.kmplib.ui.form

import androidx.compose.runtime.Immutable

/**
 * O que está visível num formulário, dadas as respostas e o contexto.
 *
 * @property visibleSectionIds/[visibleQuestionIds] na ORDEM DO DOCUMENTO (as fixtures comparam a ordem).
 * @property effectiveAnswers as respostas que CONTAM: só de pergunta visível, que recebe resposta
 *   (não `info`) e que existe no schema — o que sobra do descarte ("invisível = descartada no envio")
 *   e o que o servidor pontua. Na ordem do documento.
 */
@Immutable
class FormVisibility internal constructor(
    val visibleSectionIds: List<String>,
    val visibleQuestionIds: List<String>,
    val effectiveAnswers: Map<String, FormAnswerValue>,
) {
    private val sectionSet = visibleSectionIds.toHashSet()
    private val questionSet = visibleQuestionIds.toHashSet()

    fun isSectionVisible(sectionId: String): Boolean = sectionId in sectionSet

    fun isQuestionVisible(questionId: String): Boolean = questionId in questionSet

    override fun toString(): String = "FormVisibility(sections=$visibleSectionIds, questions=$visibleQuestionIds)"
}

/** `info` é texto para ler: não recebe resposta. */
val FormQuestion.isAnswerable: Boolean get() = type != FormQuestionType.INFO

/**
 * Obrigatória por configuração (`required` ausente = sim; `info` nunca). A visibilidade decide se ela
 * BARRA — e só no envio.
 */
val FormQuestion.isRequired: Boolean get() = isAnswerable && required

/**
 * O cliente sabe desenhar e conferir esta pergunta. [FormQuestionType.UNSUPPORTED] (tipo que o
 * servidor conhece e esta versão do app não) continua valendo para as CONDIÇÕES — a resposta dela,
 * se houver, abre e fecha o que depende dela, como no servidor —, mas não é desenhada, validada nem
 * exigida aqui: quem confere é o servidor.
 */
val FormQuestion.isHandledByClient: Boolean get() = isAnswerable && type != FormQuestionType.UNSUPPORTED

/**
 * **Visibilidade** (regra 1 do contrato §7.1.2): a pergunta é visível se a seção e ela passam no
 * `visibleIf`. Invisível ⇒ não obrigatória e DESCARTADA no envio.
 *
 * Uma passada, na ordem do documento — dá certo porque condição só aponta para pergunta ANTERIOR (o
 * lint do servidor garante). E é em **cascata**: a condição enxerga só as respostas das perguntas que
 * ficaram visíveis antes dela, então resposta gravada de pergunta escondida (o "estou grávida" que
 * ficou de quando o sexo estava errado) não reabre o que dependia dela. Id repetido: vale a primeira
 * ocorrência, como no servidor.
 *
 * @param answers respostas por id; `null`/ausente = sem resposta.
 * @param context o que o cliente sabe do contexto — quase sempre vazio (ver [FormContext]).
 */
fun FormSchemaV1.evaluateVisibility(
    answers: Map<String, FormAnswerValue?>,
    context: FormContext? = null,
): FormVisibility {
    val sections = ArrayList<String>()
    val sectionSeen = HashSet<String>()
    val questions = LinkedHashSet<String>()
    val effective = LinkedHashMap<String, FormAnswerValue>()
    val lookup: (String) -> FormAnswerValue? = { effective[it] }

    for (section in this.sections) {
        if (section.visibleIf?.evaluate(context, lookup) == false) continue
        if (sectionSeen.add(section.id)) sections += section.id
        for (question in section.questions) {
            if (question.visibleIf?.evaluate(context, lookup) == false) continue
            if (!questions.add(question.id)) continue
            if (!question.isAnswerable) continue
            answers[question.id]?.let { effective[question.id] = it }
        }
    }
    return FormVisibility(sections, questions.toList(), effective)
}

/**
 * O **descarte do envio**: só ficam as respostas de pergunta visível que recebe resposta, na ordem do
 * documento. Some também o id que não existe mais no schema e a resposta "dada" a um `info`.
 */
fun FormSchemaV1.pruneAnswers(
    answers: Map<String, FormAnswerValue?>,
    context: FormContext? = null,
): Map<String, FormAnswerValue> = evaluateVisibility(answers, context).effectiveAnswers
