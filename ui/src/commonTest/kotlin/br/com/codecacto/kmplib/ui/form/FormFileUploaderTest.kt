package br.com.codecacto.kmplib.ui.form

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** O anexo por CALLBACK: o app sobe pela rota dele; o desfecho volta como ação do runner. */
@OptIn(ExperimentalCoroutinesApi::class)
class FormFileUploaderTest {

    private val uuid = "3f1c2a4e-0b7d-4c1a-9e2f-1a2b3c4d5e6f"
    private fun request(key: String = "exames#1") =
        FormUploadRequest(key, "exames", "LAB_REPORT", FormPickedFile("exame.pdf", "application/pdf", ByteArray(4)))

    @Test
    fun `progresso e sucesso voltam como acoes - com a rota do projeto recebendo pergunta e finalidade`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var seenPurpose: String? = null
        val uploader = FormFileUploader(backgroundScope) { file, context ->
            seenPurpose = "${context.questionId}/${context.purpose}/${file.name}"
            context.onProgress(0.5f)
            gate.await()
            FormFileRef(uuid)
        }
        val actions = mutableListOf<FormRunnerAction>()
        uploader.start(request()) { actions += it }
        runCurrent()
        assertEquals("exames/LAB_REPORT/exame.pdf", seenPurpose)
        assertEquals(listOf<FormRunnerAction>(FormRunnerAction.UploadProgress("exames#1", 0.5f)), actions)
        gate.complete(Unit)
        runCurrent()
        assertEquals(FormRunnerAction.UploadSucceeded("exames#1", FormFileRef(uuid)), actions.last())
    }

    @Test
    fun `falha com frase do servidor passa a frase - outra falha vira a da lib`() = runTest {
        val actions = mutableListOf<FormRunnerAction>()
        val withMessage = FormFileUploader(backgroundScope) { _, _ -> throw FormUploadException("Arquivo maior que 10 MB.") }
        withMessage.start(request()) { actions += it }
        runCurrent()
        assertEquals(FormRunnerAction.UploadFailed("exames#1", "Arquivo maior que 10 MB."), actions.single())
        actions.clear()
        val technical = FormFileUploader(backgroundScope) { _, _ -> throw IllegalStateException("Failed to connect to 10.0.0.1") }
        technical.start(request()) { actions += it }
        runCurrent()
        assertEquals(FormRunnerAction.UploadFailed("exames#1", null), actions.single(), "mensagem técnica nunca vai à tela")
    }

    @Test
    fun `cancelar nao reporta falha`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val uploader = FormFileUploader(backgroundScope) { _, _ ->
            gate.await()
            FormFileRef(uuid)
        }
        val actions = mutableListOf<FormRunnerAction>()
        uploader.start(request()) { actions += it }
        runCurrent()
        uploader.cancel("exames#1")
        runCurrent()
        assertTrue(actions.isEmpty())
        uploader.start(request("exames#2")) { actions += it }
        runCurrent()
        uploader.cancelAll()
        gate.complete(Unit)
        runCurrent()
        assertTrue(actions.isEmpty())
    }
}
