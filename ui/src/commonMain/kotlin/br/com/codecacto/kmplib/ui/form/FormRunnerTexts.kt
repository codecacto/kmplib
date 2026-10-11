package br.com.codecacto.kmplib.ui.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_form_answered
import br.com.codecacto.kmplib.generated.resources.kmplib_form_back
import br.com.codecacto.kmplib.generated.resources.kmplib_form_date_label
import br.com.codecacto.kmplib.generated.resources.kmplib_form_estimate
import br.com.codecacto.kmplib.generated.resources.kmplib_form_file_add_pdf
import br.com.codecacto.kmplib.generated.resources.kmplib_form_file_add_photo
import br.com.codecacto.kmplib.generated.resources.kmplib_form_file_camera_denied
import br.com.codecacto.kmplib.generated.resources.kmplib_form_file_camera_unavailable
import br.com.codecacto.kmplib.generated.resources.kmplib_form_file_fallback_name
import br.com.codecacto.kmplib.generated.resources.kmplib_form_file_hint_both
import br.com.codecacto.kmplib.generated.resources.kmplib_form_file_hint_image
import br.com.codecacto.kmplib.generated.resources.kmplib_form_file_hint_pdf
import br.com.codecacto.kmplib.generated.resources.kmplib_form_file_remove
import br.com.codecacto.kmplib.generated.resources.kmplib_form_file_retry
import br.com.codecacto.kmplib.generated.resources.kmplib_form_file_too_big
import br.com.codecacto.kmplib.generated.resources.kmplib_form_file_unreadable
import br.com.codecacto.kmplib.generated.resources.kmplib_form_file_upload_error
import br.com.codecacto.kmplib.generated.resources.kmplib_form_file_uploading
import br.com.codecacto.kmplib.generated.resources.kmplib_form_file_wrong_type_both
import br.com.codecacto.kmplib.generated.resources.kmplib_form_file_wrong_type_image
import br.com.codecacto.kmplib.generated.resources.kmplib_form_file_wrong_type_pdf
import br.com.codecacto.kmplib.generated.resources.kmplib_form_files_pending
import br.com.codecacto.kmplib.generated.resources.kmplib_form_list_add
import br.com.codecacto.kmplib.generated.resources.kmplib_form_list_item_title
import br.com.codecacto.kmplib.generated.resources.kmplib_form_list_remove
import br.com.codecacto.kmplib.generated.resources.kmplib_form_list_remove_item
import br.com.codecacto.kmplib.generated.resources.kmplib_form_missing_one
import br.com.codecacto.kmplib.generated.resources.kmplib_form_missing_other
import br.com.codecacto.kmplib.generated.resources.kmplib_form_needs_attention_one
import br.com.codecacto.kmplib.generated.resources.kmplib_form_needs_attention_other
import br.com.codecacto.kmplib.generated.resources.kmplib_form_next
import br.com.codecacto.kmplib.generated.resources.kmplib_form_opens_externally
import br.com.codecacto.kmplib.generated.resources.kmplib_form_optional
import br.com.codecacto.kmplib.generated.resources.kmplib_form_question_progress
import br.com.codecacto.kmplib.generated.resources.kmplib_form_reserved
import br.com.codecacto.kmplib.generated.resources.kmplib_form_reserved_pending
import br.com.codecacto.kmplib.generated.resources.kmplib_form_reserved_pending_count_one
import br.com.codecacto.kmplib.generated.resources.kmplib_form_reserved_pending_count_other
import br.com.codecacto.kmplib.generated.resources.kmplib_form_score_incomplete
import br.com.codecacto.kmplib.generated.resources.kmplib_form_score_label
import br.com.codecacto.kmplib.generated.resources.kmplib_form_score_label_band
import br.com.codecacto.kmplib.generated.resources.kmplib_form_select_placeholder
import br.com.codecacto.kmplib.generated.resources.kmplib_form_step_done
import br.com.codecacto.kmplib.generated.resources.kmplib_form_step_has_issues
import br.com.codecacto.kmplib.generated.resources.kmplib_form_step_progress
import br.com.codecacto.kmplib.generated.resources.kmplib_form_steps_nav
import br.com.codecacto.kmplib.generated.resources.kmplib_form_submit
import br.com.codecacto.kmplib.generated.resources.kmplib_form_submitting
import br.com.codecacto.kmplib.generated.resources.kmplib_form_unsaved
import br.com.codecacto.kmplib.generated.resources.kmplib_form_unsupported
import br.com.codecacto.kmplib.ui.questionnaire.fillQuestionnaireTemplate
import br.com.codecacto.kmplib.ui.locale.kmpStringResource

/**
 * Os textos que o `FormRunner` desenha sozinho — a MOLDURA (o `FORM_RUNNER_LABELS` da weblib, mais o
 * que o app pede: câmera, arquivo ilegível). Pergunta, opção, seção e termo vêm do SCHEMA (conteúdo
 * do projeto), nunca daqui. Os defaults são pt-BR (teste e uso fora de composição); na tela,
 * [rememberFormRunnerTexts], nos 4 idiomas. Troque frase a frase com `copy(…)`.
 */
@Immutable
data class FormRunnerTexts(
    /** `(2, 6)` → "Etapa 2 de 6". */
    val stepProgress: (current: Int, total: Int) -> String = { c, t -> "Etapa $c de $t" },
    /** `(2, 5)` → "Pergunta 2 de 5" — o ritmo pergunta a pergunta das seções de escala. */
    val questionProgress: (current: Int, total: Int) -> String = { c, t -> "Pergunta $c de $t" },
    /** `(8)` → "Cerca de 8 min" — o `estimatedMinutes` do formulário, no 1º passo. */
    val estimate: (minutes: Int) -> String = { "Cerca de $it min" },
    /** `(12, 30)` → "12 de 30 respondidas". */
    val answered: (answered: Int, total: Int) -> String = { a, t -> "$a de $t respondidas" },
    /** O que ainda falta na seção de ESCALA (o "faltam N" do `QuestionnaireRunner`). */
    val missing: (missing: Int) -> String = { if (it == 1) "Falta 1 resposta nesta etapa." else "Faltam $it respostas nesta etapa." },
    /** Depois de uma tentativa de seguir: quantas perguntas DESTA etapa estão marcadas. */
    val needsAttention: (count: Int) -> String = {
        if (it == 1) "1 pergunta precisa de atenção nesta etapa." else "$it perguntas precisam de atenção nesta etapa."
    },
    val back: String = "Voltar",
    val next: String = "Continuar",
    val submit: String = "Enviar",
    val submitting: String = "Enviando…",
    /** Nome acessível do índice de etapas (tela larga). */
    val stepsNav: String = "Etapas do formulário",
    /** Sufixo lido da etapa completa no índice. */
    val stepDone: String = "concluída",
    /** Sufixo lido da etapa com problema marcado. */
    val stepHasIssues: String = "com pendência",
    /** Marca da pergunta opcional (o resto é obrigatório). */
    val optional: String = "opcional",
    /** Selo da pergunta/seção reservada (só com `markReserved`). */
    val reserved: String = "Reservada",
    /** Reservada sem resposta: o que a equipe faz com ela. Configurável por produto. */
    val reservedPending: String = "Pendente — perguntar na consulta",
    /** `(2)` → "2 reservadas pendentes" — no índice de etapas. */
    val reservedPendingCount: (count: Int) -> String = { if (it == 1) "1 reservada pendente" else "$it reservadas pendentes" },
    /** Seleção sem resposta quando o schema não traz `placeholder` — instrução, não dado. */
    val selectPlaceholder: String = "Selecione",
    /** `("Medicamento")` → "Adicionar medicamento". */
    val listAdd: (itemLabel: String) -> String = { "Adicionar ${it.lowercaseFirst()}" },
    /** `("Medicamento", 2)` → "Medicamento 2" — o título do item. */
    val listItemTitle: (itemLabel: String, position: Int) -> String = { item, n -> "$item $n" },
    /** Rótulo visível do botão de remover item. */
    val listRemove: String = "Remover",
    /** Nome acessível do remover: `("Medicamento", 2)` → "Remover medicamento 2". */
    val listRemoveItem: (itemLabel: String, position: Int) -> String = { item, n -> "Remover ${item.lowercaseFirst()} $n" },
    val fileAddPhoto: String = "Enviar foto",
    val fileAddPdf: String = "Enviar PDF",
    /** A consequência que não se adivinha: formatos e teto (`mb` já formatado). */
    val fileHint: (accept: List<FormFileKind>, mb: String) -> String = { accept, mb ->
        val image = FormFileKind.IMAGE in accept
        val pdf = FormFileKind.PDF in accept
        val kind = if (image && pdf) "Imagem ou PDF" else if (pdf) "PDF" else "Imagem"
        "$kind, até $mb MB cada."
    },
    /** `("exame.pdf")` → "Enviando exame.pdf…". */
    val fileUploading: (name: String) -> String = { "Enviando $it…" },
    /** Nome de anexo já salvo de que a lib não sabe o nome. */
    val fileFallbackName: (position: Int) -> String = { "Arquivo $it" },
    /** `("exame.pdf")` → "Remover exame.pdf". */
    val fileRemove: (name: String) -> String = { "Remover $it" },
    val fileRetry: String = "Tentar de novo",
    val fileUploadError: String = "Não foi possível enviar o arquivo.",
    /** `("10")` → "O arquivo passa de 10 MB.". */
    val fileTooBig: (mb: String) -> String = { "O arquivo passa de $it MB." },
    /** Formato fora do `accept`. */
    val fileWrongType: (accept: List<FormFileKind>) -> String = { accept ->
        val image = FormFileKind.IMAGE in accept
        val pdf = FormFileKind.PDF in accept
        when {
            image && pdf -> "Formato não aceito. Envie imagem ou PDF."
            pdf -> "Formato não aceito. Envie um PDF."
            else -> "Formato não aceito. Envie uma imagem."
        }
    },
    val fileUnreadable: String = "Não foi possível ler o arquivo.",
    val fileCameraDenied: String = "Sem acesso à câmera. Libere a câmera nas configurações do aparelho.",
    val fileCameraUnavailable: String = "A câmera não abriu neste aparelho.",
    /** "Enviar" com anexo ainda subindo. */
    val filesPending: String = "Aguarde o envio dos arquivos terminar.",
    /** Lido depois do link do termo, que abre fora do app. */
    val opensExternally: String = "abre fora do app",
    /** Resultado de escala que o servidor mandou incompleto. */
    val scoreIncomplete: String = "incompleto",
    /** Resultado da escala: `("PHQ-2", "3", "Rastreio positivo")` → "PHQ-2: 3 — Rastreio positivo". */
    val scoreLabel: (label: String, score: String, band: String?) -> String = { label, score, band ->
        if (band.isNullOrBlank()) "$label: $score" else "$label: $score — $band"
    },
    /** Pergunta de tipo que esta versão do app não conhece. */
    val unsupported: String = "Pergunta indisponível nesta versão do app.",
    /** Aviso para o runner quando a [FormAnswerQueue] tem pendência (o default do `useFormAnswerQueue`). */
    val unsavedAnswers: String = "Suas respostas estão nesta tela, mas ainda não foram salvas.",
    /** Rótulo dentro do campo de data (o enunciado fica acima). */
    val dateLabel: String = "Data",
)

private fun String.lowercaseFirst(): String = replaceFirstChar { it.lowercase() }

/** [FormRunnerTexts] no idioma da tela (pt-BR, en, es, pt-PT). */
@Composable
fun rememberFormRunnerTexts(): FormRunnerTexts {
    val stepProgress = kmpStringResource(Res.string.kmplib_form_step_progress)
    val questionProgress = kmpStringResource(Res.string.kmplib_form_question_progress)
    val estimate = kmpStringResource(Res.string.kmplib_form_estimate)
    val answered = kmpStringResource(Res.string.kmplib_form_answered)
    val missingOne = kmpStringResource(Res.string.kmplib_form_missing_one)
    val missingOther = kmpStringResource(Res.string.kmplib_form_missing_other)
    val attentionOne = kmpStringResource(Res.string.kmplib_form_needs_attention_one)
    val attentionOther = kmpStringResource(Res.string.kmplib_form_needs_attention_other)
    val back = kmpStringResource(Res.string.kmplib_form_back)
    val next = kmpStringResource(Res.string.kmplib_form_next)
    val submit = kmpStringResource(Res.string.kmplib_form_submit)
    val submitting = kmpStringResource(Res.string.kmplib_form_submitting)
    val stepsNav = kmpStringResource(Res.string.kmplib_form_steps_nav)
    val stepDone = kmpStringResource(Res.string.kmplib_form_step_done)
    val stepHasIssues = kmpStringResource(Res.string.kmplib_form_step_has_issues)
    val optional = kmpStringResource(Res.string.kmplib_form_optional)
    val reserved = kmpStringResource(Res.string.kmplib_form_reserved)
    val reservedPending = kmpStringResource(Res.string.kmplib_form_reserved_pending)
    val reservedCountOne = kmpStringResource(Res.string.kmplib_form_reserved_pending_count_one)
    val reservedCountOther = kmpStringResource(Res.string.kmplib_form_reserved_pending_count_other)
    val selectPlaceholder = kmpStringResource(Res.string.kmplib_form_select_placeholder)
    val listAdd = kmpStringResource(Res.string.kmplib_form_list_add)
    val listItemTitle = kmpStringResource(Res.string.kmplib_form_list_item_title)
    val listRemove = kmpStringResource(Res.string.kmplib_form_list_remove)
    val listRemoveItem = kmpStringResource(Res.string.kmplib_form_list_remove_item)
    val fileAddPhoto = kmpStringResource(Res.string.kmplib_form_file_add_photo)
    val fileAddPdf = kmpStringResource(Res.string.kmplib_form_file_add_pdf)
    val fileHintImage = kmpStringResource(Res.string.kmplib_form_file_hint_image)
    val fileHintPdf = kmpStringResource(Res.string.kmplib_form_file_hint_pdf)
    val fileHintBoth = kmpStringResource(Res.string.kmplib_form_file_hint_both)
    val fileUploading = kmpStringResource(Res.string.kmplib_form_file_uploading)
    val fileFallbackName = kmpStringResource(Res.string.kmplib_form_file_fallback_name)
    val fileRemove = kmpStringResource(Res.string.kmplib_form_file_remove)
    val fileRetry = kmpStringResource(Res.string.kmplib_form_file_retry)
    val fileUploadError = kmpStringResource(Res.string.kmplib_form_file_upload_error)
    val fileTooBig = kmpStringResource(Res.string.kmplib_form_file_too_big)
    val wrongImage = kmpStringResource(Res.string.kmplib_form_file_wrong_type_image)
    val wrongPdf = kmpStringResource(Res.string.kmplib_form_file_wrong_type_pdf)
    val wrongBoth = kmpStringResource(Res.string.kmplib_form_file_wrong_type_both)
    val fileUnreadable = kmpStringResource(Res.string.kmplib_form_file_unreadable)
    val cameraDenied = kmpStringResource(Res.string.kmplib_form_file_camera_denied)
    val cameraUnavailable = kmpStringResource(Res.string.kmplib_form_file_camera_unavailable)
    val filesPending = kmpStringResource(Res.string.kmplib_form_files_pending)
    val opensExternally = kmpStringResource(Res.string.kmplib_form_opens_externally)
    val scoreIncomplete = kmpStringResource(Res.string.kmplib_form_score_incomplete)
    val scoreLabel = kmpStringResource(Res.string.kmplib_form_score_label)
    val scoreLabelBand = kmpStringResource(Res.string.kmplib_form_score_label_band)
    val unsupported = kmpStringResource(Res.string.kmplib_form_unsupported)
    val unsaved = kmpStringResource(Res.string.kmplib_form_unsaved)
    val dateLabel = kmpStringResource(Res.string.kmplib_form_date_label)
    val all = listOf(
        stepProgress, questionProgress, estimate, answered, missingOne, missingOther, attentionOne, attentionOther, back,
        next, submit, submitting, stepsNav, stepDone, stepHasIssues, optional, reserved, reservedPending,
        reservedCountOne, reservedCountOther, selectPlaceholder, listAdd, listItemTitle, listRemove, listRemoveItem,
        fileAddPhoto, fileAddPdf, fileHintImage, fileHintPdf, fileHintBoth, fileUploading, fileFallbackName, fileRemove,
        fileRetry, fileUploadError, fileTooBig, wrongImage, wrongPdf, wrongBoth, fileUnreadable, cameraDenied,
        cameraUnavailable, filesPending, opensExternally, scoreIncomplete, scoreLabel, scoreLabelBand, unsupported,
        unsaved, dateLabel,
    )
    return remember(all) {
        FormRunnerTexts(
            stepProgress = { c, t -> fillQuestionnaireTemplate(stepProgress, c, t) },
            questionProgress = { c, t -> fillQuestionnaireTemplate(questionProgress, c, t) },
            estimate = { fillQuestionnaireTemplate(estimate, it) },
            answered = { a, t -> fillQuestionnaireTemplate(answered, a, t) },
            missing = { if (it == 1) missingOne else fillQuestionnaireTemplate(missingOther, it) },
            needsAttention = { if (it == 1) attentionOne else fillQuestionnaireTemplate(attentionOther, it) },
            back = back,
            next = next,
            submit = submit,
            submitting = submitting,
            stepsNav = stepsNav,
            stepDone = stepDone,
            stepHasIssues = stepHasIssues,
            optional = optional,
            reserved = reserved,
            reservedPending = reservedPending,
            reservedPendingCount = { if (it == 1) reservedCountOne else fillQuestionnaireTemplate(reservedCountOther, it) },
            selectPlaceholder = selectPlaceholder,
            listAdd = { fillQuestionnaireTemplate(listAdd, it.lowercaseFirst()) },
            listItemTitle = { item, n -> fillQuestionnaireTemplate(listItemTitle, item, n) },
            listRemove = listRemove,
            listRemoveItem = { item, n -> fillQuestionnaireTemplate(listRemoveItem, item.lowercaseFirst(), n) },
            fileAddPhoto = fileAddPhoto,
            fileAddPdf = fileAddPdf,
            fileHint = { accept, mb ->
                val image = FormFileKind.IMAGE in accept
                val pdf = FormFileKind.PDF in accept
                fillQuestionnaireTemplate(if (image && pdf) fileHintBoth else if (pdf) fileHintPdf else fileHintImage, mb)
            },
            fileUploading = { fillQuestionnaireTemplate(fileUploading, it) },
            fileFallbackName = { fillQuestionnaireTemplate(fileFallbackName, it) },
            fileRemove = { fillQuestionnaireTemplate(fileRemove, it) },
            fileRetry = fileRetry,
            fileUploadError = fileUploadError,
            fileTooBig = { fillQuestionnaireTemplate(fileTooBig, it) },
            fileWrongType = { accept ->
                val image = FormFileKind.IMAGE in accept
                val pdf = FormFileKind.PDF in accept
                if (image && pdf) wrongBoth else if (pdf) wrongPdf else wrongImage
            },
            fileUnreadable = fileUnreadable,
            fileCameraDenied = cameraDenied,
            fileCameraUnavailable = cameraUnavailable,
            filesPending = filesPending,
            opensExternally = opensExternally,
            scoreIncomplete = scoreIncomplete,
            scoreLabel = { label, score, band ->
                if (band.isNullOrBlank()) fillQuestionnaireTemplate(scoreLabel, label, score)
                else fillQuestionnaireTemplate(scoreLabelBand, label, score, band)
            },
            unsupported = unsupported,
            unsavedAnswers = unsaved,
            dateLabel = dateLabel,
        )
    }
}

/** Medidas e tempos do runner. */
object FormRunnerDefaults {
    /** Largura do índice de etapas na tela larga. */
    const val StepsIndexWidthDp: Int = 240

    /** A partir desta largura de conteúdo, as perguntas curtas dividem a linha em duas colunas. */
    const val TwoColumnsMinWidthDp: Int = 560
}

/**
 * Ids de automação (`testTag`) do runner — Maestro toca por id, nunca por texto (4 idiomas). Os ids
 * de pergunta são POR PERGUNTA: um id fixo de opção apareceria N vezes e o teste tocaria na errada.
 *
 * ⚠️ A seção `likert` é desenhada pelo `QuestionnaireRunner`: lá valem os ids dele
 * (`questionario-<id>-opcao-<ponto>`, `questionario-btn-continuar`, `questionario-btn-voltar`).
 */
object FormRunnerTestTags {
    const val ROOT: String = "formulario"
    const val STEP: String = "formulario-passo"
    const val SECTION_TITLE: String = "formulario-secao-titulo"
    const val PROGRESS: String = "formulario-progresso"
    const val NOTICE: String = "formulario-aviso"
    const val STATUS: String = "formulario-status"
    const val NEXT: String = "formulario-btn-continuar"
    const val SUBMIT: String = "formulario-btn-enviar"
    const val BACK: String = "formulario-btn-voltar"
    const val STEPS_NAV: String = "formulario-etapas"
    const val SECTION_RESERVED: String = "formulario-secao-reservada"

    /** Uma etapa no índice da tela larga. */
    fun stepItem(sectionId: String): String = "formulario-etapa-$sectionId"

    /** O resultado da escala (do servidor) da seção. */
    fun score(sectionId: String): String = "formulario-escore-$sectionId"

    /** O bloco inteiro da pergunta. */
    fun question(questionId: String): String = "formulario-pergunta-$questionId"

    /** O controle de resposta (campo, grupo, régua, caixa do termo). */
    fun answer(questionId: String): String = "formulario-$questionId"

    /** Uma opção (escolha, régua — `3` / `0.5`). */
    fun option(questionId: String, value: String): String = "formulario-$questionId-opcao-$value"

    fun error(questionId: String): String = "formulario-$questionId-erro"
    fun reserved(questionId: String): String = "formulario-$questionId-reservada"
    fun reservedPending(questionId: String): String = "formulario-$questionId-pendente"
    fun locked(questionId: String): String = "formulario-$questionId-travada"

    fun listAdd(questionId: String): String = "formulario-$questionId-btn-adicionar"
    fun listItem(questionId: String, index: Int): String = "formulario-$questionId-item-$index"
    fun listItemRemove(questionId: String, index: Int): String = "formulario-$questionId-item-$index-btn-remover"
    fun listItemField(questionId: String, index: Int, fieldId: String): String = "formulario-$questionId-item-$index-$fieldId"

    /** O botão de anexar: `-foto` (câmera/galeria) ou `-pdf`. */
    fun fileAdd(questionId: String, kind: FormFileKind): String =
        "formulario-$questionId-btn-anexar-${if (kind == FormFileKind.IMAGE) "foto" else "pdf"}"

    /** Um anexo já salvo (posição 0-based). */
    fun file(questionId: String, index: Int): String = "formulario-$questionId-arquivo-$index"
    fun fileRemove(questionId: String, index: Int): String = "formulario-$questionId-arquivo-$index-btn-remover"

    /** Um envio em curso ou recusado (o número da chave do envio). */
    fun upload(uploadKey: String): String = "formulario-${uploadKey.substringBefore('#')}-envio-${uploadKey.substringAfterLast('#')}"
    fun uploadRetry(uploadKey: String): String = upload(uploadKey) + "-btn-tentar"
    fun uploadDismiss(uploadKey: String): String = upload(uploadKey) + "-btn-remover"

    /** Um link do termo. */
    fun consentLink(questionId: String, index: Int): String = "formulario-$questionId-link-$index"
}
