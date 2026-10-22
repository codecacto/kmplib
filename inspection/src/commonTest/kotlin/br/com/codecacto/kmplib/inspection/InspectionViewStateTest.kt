package br.com.codecacto.kmplib.inspection

import br.com.codecacto.kmplib.inspection.model.Divergence
import br.com.codecacto.kmplib.inspection.model.ExitResult
import br.com.codecacto.kmplib.inspection.model.InspectionMediaKind
import br.com.codecacto.kmplib.inspection.model.InspectionMediaSource
import br.com.codecacto.kmplib.inspection.model.InspectionMediaThumb
import br.com.codecacto.kmplib.inspection.model.ReportStatus
import br.com.codecacto.kmplib.inspection.model.ReviewState
import br.com.codecacto.kmplib.inspection.model.RoomProgress
import br.com.codecacto.kmplib.inspection.model.SignatureEvent
import br.com.codecacto.kmplib.inspection.preview.InspectionSamples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InspectionViewStateTest {

    @Test
    fun cinco_de_oito_prontos_como_no_prototipo() {
        val s = InspectionSamples.rooms
        assertEquals(5, s.readyCount)
        assertEquals(0.625f, s.overallFraction)
    }

    @Test
    fun andamento_do_comodo() {
        val parcial = RoomProgress("q", "Quarto 1", 4, 10)
        assertEquals(0.4f, parcial.fraction)
        assertEquals(6, parcial.remaining)
        assertFalse(parcial.isComplete)
        assertTrue(RoomProgress("s", "Sala", 12, 12).isComplete)
        assertFalse(RoomProgress("b", "Banheiro", 0, 14).isStarted)
        assertFalse(RoomProgress("v", "Vazio", 0, 0).isComplete)
        assertEquals(1f, RoomProgress("x", "X", 15, 10).fraction)
    }

    @Test
    fun tarefa_guiada_so_salva_com_o_minimo_de_fotos_da_camera() {
        val base = InspectionSamples.itemSheet.copy(isGuidedTask = true, minPhotos = 1, media = emptyList())
        assertFalse(base.canSave)
        // Anexo da galeria NÃO conta: é anexo, não prova.
        val soGaleria = base.copy(media = listOf(InspectionMediaThumb("g", null, origin = InspectionMediaSource.GALLERY)))
        assertFalse(soGaleria.canSave)
        val comFoto = base.copy(media = listOf(InspectionMediaThumb("c", null)))
        assertTrue(comFoto.canSave)
        val video = base.copy(media = listOf(InspectionMediaThumb("v", null, kind = InspectionMediaKind.VIDEO)))
        assertFalse(video.canSave)
        assertFalse(comFoto.copy(isSaving = true).canSave)
        assertFalse(comFoto.copy(readOnly = true).canSave)
        assertTrue(InspectionSamples.itemSheet.canSave)
    }

    @Test
    fun divergencia_so_aparece_e_so_e_obrigatoria_fora_do_conforme() {
        val s = InspectionSamples.exit
        assertTrue(s.divergenceVisible)
        assertFalse(s.divergenceMissing)
        assertTrue(s.copy(divergence = null).divergenceMissing)
        val conforme = s.copy(result = ExitResult.CONFORMING, divergence = null)
        assertFalse(conforme.divergenceVisible)
        assertFalse(conforme.divergenceMissing)
        assertFalse(s.copy(result = null).divergenceVisible)
        assertEquals(Divergence.DAMAGE, s.divergence)
    }

    @Test
    fun finalizar_desligado_com_pendencia() {
        val r: ReviewState = InspectionSamples.review
        assertFalse(r.canFinish)
        assertTrue(r.copy(pendencies = emptyList()).canFinish)
        assertFalse(r.copy(pendencies = emptyList(), isFinishing = true).canFinish)
    }

    @Test
    fun gerando_o_laudo() {
        val g = ReportStatus.Generating(uploaded = 177, total = 191)
        assertEquals(14, g.remaining)
        assertEquals(177f / 191f, g.fraction)
        assertEquals(0f, ReportStatus.Generating(0, 0).fraction)
    }

    @Test
    fun toString_sem_dado_pessoal_nem_caminho() {
        val assinatura = InspectionSamples.signature.toString()
        assertFalse("Mariana" in assinatura)
        assertFalse("481903" in assinatura)
        val envio = SignatureEvent.Submit(byteArrayOf(1, 2, 3))
        assertEquals("Submit(stroke=3 bytes)", envio.toString())
        assertEquals(envio, SignatureEvent.Submit(byteArrayOf(1, 2, 3)))
        assertNotEquals(envio, SignatureEvent.Submit(null))
        val midia = InspectionMediaThumb("id1", br.com.codecacto.kmplib.ui.components.PhotoSource.Url("https://x.com/a.jpg?token=SEGREDO")).toString()
        assertFalse("SEGREDO" in midia)
    }
}
