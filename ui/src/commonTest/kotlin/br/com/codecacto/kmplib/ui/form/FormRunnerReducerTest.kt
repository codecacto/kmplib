package br.com.codecacto.kmplib.ui.form

import br.com.codecacto.kmplib.ui.questionnaire.QuestionnaireRunnerAction
import br.com.codecacto.kmplib.ui.questionnaire.QuestionnaireValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** A regra do runner (navegação, erro no campo, envio, anexo, seção de escala) — pura, sem tela. */
class FormRunnerReducerTest {

    private val schemas = Json.parseToJsonElement(FORM_SCHEMA_FIXTURES_JSON).jsonObject.getValue("schemas").jsonObject
    private fun schema(name: String): FormSchemaV1 = FormJson.format.decodeFromJsonElement(FormSchemaV1.serializer(), schemas.getValue(name))

    private val today = LocalDate(2026, 10, 12)
    private val preconsulta = schema("preconsulta")
    private val tipos = schema("tipos")

    private fun text(v: String) = FormAnswerValue.text(v)
    private fun num(v: String) = FormAnswerValue.number(FormDecimal.parse(v)!!)
    private val uuid = "3f1c2a4e-0b7d-4c1a-9e2f-1a2b3c4d5e6f"

    /** Pré-consulta sem contexto: etapas inicio, habitos, intimo, humor (escala), termo. */
    private fun start(answers: Map<String, FormAnswerValue> = emptyMap(), resume: Boolean = false, exitEnabled: Boolean = false) =
        FormRunnerState.start(preconsulta, answers, today, resume = resume, exitEnabled = exitEnabled)

    private fun FormRunnerState.step(): String = steps()[stepIndex()].sectionId

    private fun FormRunnerState.go(vararg actions: FormRunnerAction): FormRunnerUpdate {
        var update = FormRunnerUpdate(this)
        for (action in actions) update = update.state.reduce(action)
        return update
    }

    private val habitosCompletos = mapOf(
        "queixa" to text("Enxaqueca"),
        "fuma" to text("nao"),
        "sintomas" to FormAnswerValue.choices(listOf("nenhum")),
        "substancias" to text("nao"),
    )

    @Test
    fun `retomada abre na primeira etapa com pendencia`() {
        assertEquals("inicio", start(resume = true).step())
        assertEquals("intimo", start(habitosCompletos, resume = true).step())
        assertEquals(listOf("inicio", "habitos", "intimo", "humor", "termo"), start().steps().map { it.sectionId })
    }

    @Test
    fun `responder guarda - emite - normaliza o vazio e limpa so o erro da pergunta`() {
        val marked = start().reduce(FormRunnerAction.Next).state
        assertEquals(listOf(FormIssueCode.REQUIRED), marked.issues["queixa"]?.map { it.code })
        val typed = marked.reduce(FormRunnerAction.Answer("queixa", text("Dor")))
        assertEquals(listOf(FormRunnerEvent.Answered("queixa", text("Dor"))), typed.events)
        assertNull(typed.state.issues["queixa"], "mexer limpa o erro DELA")
        val cleared = typed.state.reduce(FormRunnerAction.Answer("queixa", text("")))
        assertNull(cleared.state.answers["queixa"], "texto vazio é apagar")
        assertEquals(listOf(FormRunnerEvent.Answered("queixa", null)), cleared.events)
        assertTrue(cleared.state.reduce(FormRunnerAction.Answer("queixa", null)).events.isEmpty(), "sem mudança, sem evento")
    }

    @Test
    fun `pergunta info - inexistente ou travada nao recebe resposta`() {
        val locked = start().copy(lockedQuestions = mapOf("queixa" to "Respondida pelo paciente"))
        assertTrue(locked.reduce(FormRunnerAction.Answer("queixa", text("x"))).events.isEmpty())
        assertTrue(start().reduce(FormRunnerAction.Answer("termo-aviso", text("x"))).events.isEmpty())
        assertTrue(start().reduce(FormRunnerAction.Answer("fantasma", text("x"))).events.isEmpty())
    }

    @Test
    fun `sair de um campo editado confere o formato na hora - so se editado`() {
        val state = start().copy(stepId = "inicio")
        assertTrue(state.reduce(FormRunnerAction.Blur("peso")).state.issues.isEmpty(), "não tocado: nada")
        val typed = state.reduce(FormRunnerAction.Answer("peso", num("400"))).state.reduce(FormRunnerAction.Blur("peso")).state
        assertEquals(listOf(FormIssueCode.NUMBER_OUT_OF_RANGE), typed.issues["peso"]?.map { it.code })
        assertEquals(FormDecimal.parse("300"), typed.issues["peso"]!!.first().params.maxValue)
        val fixed = typed.reduce(FormRunnerAction.Answer("peso", num("72.5"))).state.reduce(FormRunnerAction.Blur("peso")).state
        assertNull(fixed.issues["peso"])
    }

    @Test
    fun `continuar confere a etapa - marca no campo e pede o foco na primeira`() {
        val state = start(mapOf("queixa" to text("Enxaqueca"))).copy(stepId = "habitos")
        val update = state.reduce(FormRunnerAction.Answer("fuma", text("sim"))).state.reduce(FormRunnerAction.Next)
        assertEquals("habitos", update.state.step(), "com pendência não avança")
        assertEquals(setOf("cigarros-dia", "sintomas", "substancias"), update.state.issues.keys, "a que a condição abriu também")
        assertEquals("cigarros-dia", update.state.focusRequest?.questionId)
        assertTrue(update.events.isEmpty())
        val done = update.state.go(
            FormRunnerAction.Answer("cigarros-dia", num("10")),
            FormRunnerAction.Answer("sintomas", FormAnswerValue.choices(listOf("febre"))),
            FormRunnerAction.Answer("substancias", text("nao")),
            FormRunnerAction.Next,
        )
        assertEquals("intimo", done.state.step())
        assertEquals(listOf(FormRunnerEvent.StepChanged("intimo")), done.events)
        assertTrue(done.state.issues.isEmpty())
    }

    @Test
    fun `consentimento nao barra o continuar - so o envio`() {
        val state = FormRunnerState.start(tipos, today = today)
        val filled = state.go(
            FormRunnerAction.Answer("nome", text("Ana")),
            FormRunnerAction.Answer("peso", num("72.5")),
            FormRunnerAction.Answer("dias", num("3")),
            FormRunnerAction.Answer("nascimento", text("1990-05-20")),
            FormRunnerAction.Answer("cor", text("a")),
            FormRunnerAction.Answer("sintomas", FormAnswerValue.choices(listOf("nenhum"))),
            FormRunnerAction.Answer("dor", num("4")),
            FormRunnerAction.Answer(
                "remedios",
                FormAnswerValue.items(listOf(mapOf("nome" to formListText("Losartana")), mapOf("nome" to formListText("AAS")))),
            ),
        ).state
        val refused = filled.reduce(FormRunnerAction.Submit)
        assertEquals(listOf(FormIssueCode.CONSENT_REQUIRED), refused.state.issues["aceite"]?.map { it.code })
        assertTrue(refused.events.isEmpty())
        val sent = refused.state.go(FormRunnerAction.Answer("aceite", FormAnswerValue.bool(true)), FormRunnerAction.Next)
        val submit = assertIs<FormRunnerEvent.Submit>(sent.events.single(), "na última etapa, Continuar = Enviar")
        assertEquals(true, submit.answers["aceite"]?.booleanOrNull())
    }

    @Test
    fun `enviar confere tudo - leva a etapa da primeira pendencia e envia ja descartado`() {
        val state = start(habitosCompletos + mapOf("vida-ativa" to text("nao"), "phq2-1" to num("1"), "phq2-2" to num("0")))
            .copy(stepId = "termo")
        val refused = state.reduce(FormRunnerAction.Answer("aceite", FormAnswerValue.bool(true))).state
            .reduce(FormRunnerAction.Answer("queixa", null)).state
            .reduce(FormRunnerAction.Submit)
        assertEquals("inicio", refused.state.step())
        assertEquals("queixa", refused.state.focusRequest?.questionId)
        assertEquals(listOf(FormRunnerEvent.StepChanged("inicio")), refused.events)

        // Resposta gravada de pergunta escondida (gestação, sem a seção da mulher) sai do envio.
        val ready = state.copy(answers = state.answers + ("gestante" to text("sim")) + ("aceite" to FormAnswerValue.bool(true)))
        val sent = ready.reduce(FormRunnerAction.Submit)
        val submit = assertIs<FormRunnerEvent.Submit>(sent.events.single())
        assertFalse("gestante" in submit.answers)
        assertEquals(listOf("queixa", "fuma", "sintomas", "substancias", "vida-ativa", "phq2-1", "phq2-2", "aceite"), submit.answers.keys.toList())
    }

    @Test
    fun `triagem da recepcao - escopo sem as travadas e sem consentimento`() {
        val state = FormRunnerState.start(
            preconsulta,
            answers = habitosCompletos - "queixa",
            today = today,
            requireConsent = false,
            lockedQuestions = mapOf("queixa" to "Respondida pelo paciente"),
        ).copy(stepId = "termo")
        assertFalse("queixa" in state.scope!!)
        val progress = state.progress()
        assertEquals(3 + 1, progress.answered, "a travada conta como respondida")
        val refused = state.reduce(FormRunnerAction.Submit)
        assertEquals(setOf("vida-ativa", "phq2-1", "phq2-2"), refused.state.issues.keys, "nem a travada nem o consentimento")
    }

    @Test
    fun `voltar - etapa anterior - sair na primeira so com exitEnabled`() {
        val state = start().copy(stepId = "habitos")
        assertEquals("inicio", state.reduce(FormRunnerAction.Back).state.step())
        assertTrue(start().reduce(FormRunnerAction.Back).events.isEmpty())
        assertEquals(listOf<FormRunnerEvent>(FormRunnerEvent.Exited), start(exitEnabled = true).reduce(FormRunnerAction.Back).events)
    }

    @Test
    fun `ocupado trava a tela mas o resultado do anexo ainda chega`() {
        val state = start().copy(stepId = "habitos")
        val picked = state.reduce(FormRunnerAction.PickFiles("exames", listOf(FormPickedFile("exame.pdf", "application/pdf", ByteArray(10)))))
        val key = assertIs<FormRunnerEvent.UploadRequested>(picked.events.single()).request.key
        val busy = picked.state.copy(busy = true)
        assertTrue(busy.reduce(FormRunnerAction.Answer("fuma", text("sim"))).events.isEmpty())
        val done = busy.reduce(FormRunnerAction.UploadSucceeded(key, FormFileRef(uuid)))
        assertEquals(listOf(FormFileRef(uuid)), done.state.answers["exames"]?.fileRefsOrNull())
    }

    @Test
    fun `anexo - recusa tipo - tamanho e teto aqui e manda o resto para a rota do projeto`() {
        val state = FormRunnerState.start(tipos, today = today).copy(answers = mapOf("exames" to FormAnswerValue.files(listOf(FormFileRef(uuid)))))
        val update = state.reduce(
            FormRunnerAction.PickFiles(
                "exames",
                listOf(
                    FormPickedFile("foto.jpg", "image/jpeg", ByteArray(1)),
                    FormPickedFile("grande.pdf", "application/pdf", ByteArray(10 * 1024 * 1024 + 1)),
                    FormPickedFile("a.pdf", "application/pdf", ByteArray(1)),
                    FormPickedFile("b.pdf", "application/pdf", ByteArray(1)),
                ),
            ),
        )
        assertEquals(
            listOf(FormUploadProblem.WrongType, FormUploadProblem.TooBig, null, FormUploadProblem.TooMany),
            update.state.uploads.map { it.problem },
        )
        val request = assertIs<FormRunnerEvent.UploadRequested>(update.events.single()).request
        assertEquals("LAB_REPORT", request.purpose)
        assertEquals("a.pdf", request.file.name)

        val progressing = update.state.reduce(FormRunnerAction.UploadProgress(request.key, 0.4f)).state
        assertEquals(0.4f, progressing.uploads.first { it.key == request.key }.progress)

        val failed = progressing.reduce(FormRunnerAction.UploadFailed(request.key, "Arquivo corrompido.")).state
        val failedRow = failed.uploads.first { it.key == request.key }
        assertEquals(FormUploadProblem.UploadFailed("Arquivo corrompido."), failedRow.problem)
        assertTrue(failedRow.isRetryable)

        val retried = failed.reduce(FormRunnerAction.RetryUpload(request.key))
        assertEquals(request.key, assertIs<FormRunnerEvent.UploadRequested>(retried.events.single()).request.key)
        val second = "4a2b3c4d-5e6f-4a1b-8c2d-3e4f5a6b7c8d"
        val stored = retried.state.reduce(FormRunnerAction.UploadSucceeded(request.key, FormFileRef(second)))
        assertEquals(listOf(FormFileRef(uuid), FormFileRef(second)), stored.state.answers["exames"]?.fileRefsOrNull())
        assertEquals("a.pdf", stored.state.fileNames[second])
        assertIs<FormRunnerEvent.Answered>(stored.events.single())
        assertTrue(stored.state.uploads.none { it.key == request.key })
    }

    @Test
    fun `anexo - cancelar o que sobe avisa o app - fileId fora do contrato vira falha`() {
        val state = start().copy(stepId = "habitos")
        val picked = state.reduce(FormRunnerAction.PickFiles("exames", listOf(FormPickedFile("x.pdf", "application/pdf", ByteArray(1)))))
        val key = assertIs<FormRunnerEvent.UploadRequested>(picked.events.single()).request.key
        val dismissed = picked.state.reduce(FormRunnerAction.DismissUpload(key))
        assertEquals(listOf<FormRunnerEvent>(FormRunnerEvent.UploadCancelled(key)), dismissed.events)
        assertTrue(dismissed.state.reduce(FormRunnerAction.UploadSucceeded(key, FormFileRef(uuid))).state.answers["exames"] == null, "chegou depois de cancelado: ignora")

        val invalid = picked.state.reduce(FormRunnerAction.UploadSucceeded(key, FormFileRef("ABC")))
        assertIs<FormUploadProblem.UploadFailed>(invalid.state.uploads.single().problem)
        assertNull(invalid.state.answers["exames"])
    }

    @Test
    fun `enviar espera o anexo subir`() {
        val state = start(habitosCompletos).copy(stepId = "habitos")
        val picked = state.reduce(FormRunnerAction.PickFiles("exames", listOf(FormPickedFile("x.pdf", "application/pdf", ByteArray(1)))))
        val waiting = picked.state.reduce(FormRunnerAction.Submit)
        assertTrue(waiting.state.waitingFiles)
        assertTrue(waiting.events.isEmpty())
        val key = picked.state.uploads.single().key
        assertFalse(waiting.state.reduce(FormRunnerAction.UploadSucceeded(key, FormFileRef(uuid))).state.waitingFiles)
    }

    @Test
    fun `erro do servidor leva a etapa dele e sai quando a pessoa mexe`() {
        val update = start().copy(stepId = "termo").reduce(FormRunnerAction.ServerErrors(mapOf("cigarros-dia" to "x", "fuma" to "Escolha uma opção.")))
        assertEquals("habitos", update.state.step())
        assertEquals("fuma", update.state.focusRequest?.questionId)
        assertEquals("Escolha uma opção.", update.state.serverErrorOf("fuma"))
        val edited = update.state.reduce(FormRunnerAction.Answer("fuma", text("sim"))).state
        assertNull(edited.serverErrorOf("fuma"))
        assertNull(edited.reduce(FormRunnerAction.FocusHandled(update.state.focusRequest!!.serial)).state.focusRequest)
    }

    @Test
    fun `secao de escala delegada ao QuestionnaireRunner - mesma regua - valor exato - faltam e avanço`() {
        val state = start(habitosCompletos + ("vida-ativa" to text("nao"))).copy(stepId = "humor")
        val step = state.steps()[state.stepIndex()]
        val delegate = assertNotNull(state.likertDelegate(step, state.stepIndex()))
        assertEquals(listOf("phq2-1", "phq2-2"), delegate.questionIds)
        assertTrue(delegate.runner.exitEnabled, "não é a primeira etapa: o Voltar do runner volta etapa")

        val answered = state.reduce(FormRunnerAction.Likert(QuestionnaireRunnerAction.Answer("phq2-1", QuestionnaireValue.Number(2.0))))
        assertEquals(num("2"), answered.state.answers["phq2-1"], "o ponto EXATO da régua do schema")
        assertEquals(listOf(FormRunnerEvent.Answered("phq2-1", num("2"))), answered.events)

        val missing = answered.state.reduce(FormRunnerAction.Likert(QuestionnaireRunnerAction.Next(oneByOne = false)))
        assertEquals(listOf(FormIssueCode.REQUIRED), missing.state.issues["phq2-2"]?.map { it.code }, "o \"faltam\" vira pendência do formulário")
        assertEquals("phq2-2", missing.state.focusRequest?.questionId)
        assertEquals("humor", missing.state.step())

        val next = missing.state.go(
            FormRunnerAction.Likert(QuestionnaireRunnerAction.Answer("phq2-2", QuestionnaireValue.Number(0.0))),
            FormRunnerAction.Likert(QuestionnaireRunnerAction.Next(oneByOne = false)),
        )
        assertEquals("termo", next.state.step())
        assertNull(next.state.issues["phq2-2"])
        assertEquals(listOf(FormRunnerEvent.StepChanged("termo")), next.events)
    }

    @Test
    fun `secao de escala - voltar do comeco volta etapa e voltar para ela cai na ultima pergunta`() {
        val state = start(habitosCompletos + ("vida-ativa" to text("nao"))).copy(stepId = "humor")
        val back = state.reduce(FormRunnerAction.Likert(QuestionnaireRunnerAction.Back(oneByOne = true)))
        assertEquals("intimo", back.state.step())
        val again = back.state.copy(stepId = "termo").reduce(FormRunnerAction.Back)
        assertEquals("humor", again.state.step())
        assertEquals("phq2-2", again.state.likertQuestionId)
    }

    @Test
    fun `secao de escala na ultima etapa - concluir e enviar`() {
        val schema = FormJson.decodeSchema(
            """{"id":"f","title":"F","sections":[{"id":"h","title":"H","kind":"likert","scale":{"min":0,"max":1,"step":0.5},
               "questions":[{"id":"q","type":"likert","text":"Q"}]}]}""",
        )
        val state = FormRunnerState.start(schema, today = today)
        val delegate = assertNotNull(state.likertDelegate(state.steps().single(), 0))
        assertEquals(listOf(0.0, 0.5, 1.0), delegate.runner.questionnaire.scale?.let { listOf(it.min, it.min + it.step, it.max) })
        val sent = state.go(
            FormRunnerAction.Likert(QuestionnaireRunnerAction.Answer("q", QuestionnaireValue.Number(0.5))),
            FormRunnerAction.Likert(QuestionnaireRunnerAction.Next(oneByOne = false)),
        )
        val submit = assertIs<FormRunnerEvent.Submit>(sent.events.single())
        assertEquals(num("0.5"), submit.answers["q"])
    }

    @Test
    fun `regua impossivel nao delega - a etapa vira campo a campo`() {
        val schema = FormJson.decodeSchema(
            """{"id":"f","title":"F","sections":[{"id":"h","title":"H","kind":"likert","scale":{"min":0,"max":1,"step":0.3},
               "questions":[{"id":"q","type":"likert","text":"Q"}]}]}""",
        )
        val state = FormRunnerState.start(schema, today = today)
        assertNull(state.likertDelegate(state.steps().single(), 0))
    }

    @Test
    fun `reservadas pendentes da etapa - o que o medico ve`() {
        val state = start().copy(markReserved = true)
        val steps = state.steps()
        assertEquals(listOf("substancias"), pendingReservedIds(steps.first { it.sectionId == "habitos" }, state.answers))
        assertEquals(listOf("vida-ativa"), pendingReservedIds(steps.first { it.sectionId == "intimo" }, state.answers), "a seção inteira é reservada; parcerias está escondida")
        assertTrue(pendingReservedIds(steps.first { it.sectionId == "intimo" }, mapOf("vida-ativa" to text("nao"))).isEmpty())
    }

    @Test
    fun `o estado nao leva resposta para o log`() {
        val text = start(mapOf("queixa" to text("dado de saúde"))).toString()
        assertFalse("dado de saúde" in text)
        assertFalse("exame-joao" in FormPickedFile("exame-joao.pdf", "application/pdf", ByteArray(1)).toString())
    }
}
