package br.com.codecacto.kmplib.inspection

import br.com.codecacto.kmplib.inspection.icons.InspectionIcon
import br.com.codecacto.kmplib.inspection.icons.InspectionIcons
import br.com.codecacto.kmplib.inspection.model.Divergence
import br.com.codecacto.kmplib.inspection.model.ExitResult
import br.com.codecacto.kmplib.inspection.model.Functioning
import br.com.codecacto.kmplib.inspection.model.ItemCondition
import br.com.codecacto.kmplib.inspection.model.MediaUploadStatus
import br.com.codecacto.kmplib.inspection.model.SignatureStatus
import br.com.codecacto.kmplib.inspection.model.VerificationOutcome
import br.com.codecacto.kmplib.inspection.ui.media.formatBytes
import br.com.codecacto.kmplib.inspection.ui.media.formatMediaDuration
import br.com.codecacto.kmplib.inspection.ui.signature.formatCountdown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InspectionFormatsTest {

    @Test
    fun modelos_com_um_e_varios_parametros() {
        assertEquals("5 de 8 prontos", "%1\$s de %2\$s prontos".fmt(5, 8))
        assertEquals("Mínimo: 2 fotos · 1 de 2", InspectionCameraTexts().minimumProgress.fmt(2, 1))
        assertEquals("1 foto", plural(1, "%1\$s foto", "%1\$s fotos"))
        assertEquals("2 fotos", plural(2, "%1\$s foto", "%1\$s fotos"))
        assertEquals("0 fotos", plural(0, "%1\$s foto", "%1\$s fotos"))
        assertEquals("7", "%1\$d".fmt(7))
    }

    @Test
    fun hash_curto() {
        val h = "9f2c4a7b1e5d3c8a0b6f2e9d4c7a1b3e5f8d2c6a9b4e7f1a3c5d8e2b6f9a1ce1"
        assertEquals("9f2c…e1", shortHash(h))
        assertEquals("abc", shortHash("abc"))
    }

    @Test
    fun coordenada_com_virgula_e_menos_tipografico() {
        assertEquals("−23,5505 −46,6333", formatCoordinates(-23.55052, -46.63331))
        assertEquals("−23,55052", formatCoordinate(-23.55052, 5))
        assertEquals("0,0000", formatCoordinate(-0.00001))
        assertEquals("12,0000", formatCoordinate(11.99999))
        assertEquals("−1", formatCoordinate(-1.2, 0))
    }

    @Test
    fun duracoes_e_tamanhos() {
        assertEquals("0:42", formatMediaDuration(42_000))
        assertEquals("12:05", formatMediaDuration(725_000))
        assertEquals("1:02:03", formatMediaDuration(3_723_000))
        assertEquals("0:42", formatCountdown(42))
        assertEquals("1:00", formatCountdown(60))
        assertEquals("12 MB", formatBytes(12L * 1024 * 1024))
        assertEquals("1,4 GB", formatBytes((1.4 * 1024 * 1024 * 1024).toLong()))
        assertEquals("512 B", formatBytes(512))
    }

    @Test
    fun todo_valor_tem_rotulo_e_chave_unica() {
        val v = InspectionValuesTexts()
        ItemCondition.entries.forEach { assertTrue(v.label(it).isNotBlank()) }
        ExitResult.entries.forEach { assertTrue(v.label(it).isNotBlank()) }
        Functioning.entries.forEach { assertTrue(v.tileLabel(it).isNotBlank()); assertTrue(v.chipLabel(it).isNotBlank()) }
        Divergence.entries.forEach { assertTrue(v.label(it).isNotBlank()) }
        MediaUploadStatus.entries.forEach { assertTrue(v.label(it).isNotBlank()) }
        SignatureStatus.entries.forEach { assertTrue(v.label(it).isNotBlank()) }
        VerificationOutcome.entries.forEach { assertTrue(v.label(it).isNotBlank()) }
        assertEquals(ItemCondition.entries.size, ItemCondition.entries.map { it.key }.toSet().size)
        assertEquals("nconf", ExitResult.NON_CONFORMING.key)
        assertEquals("Sim", v.tileLabel(Functioning.WORKS))
        assertEquals("Funciona", v.chipLabel(Functioning.WORKS))
    }

    @Test
    fun icones_do_subconjunto_montam_nas_duas_variacoes() {
        InspectionIcon.entries.forEach {
            assertNotNull(InspectionIcons.vector(it, filled = false))
            assertNotNull(InspectionIcons.vector(it, filled = true))
        }
        assertEquals(InspectionIcon.entries.size, InspectionIcon.entries.map { it.symbol }.toSet().size)
        assertNotNull(InspectionIcons.bySymbol("check_circle"))
        // Os ícones de estado da §1.2 estão no subconjunto.
        listOf("new_releases", "do_not_disturb_on", "hourglass_bottom", "broken_image", "search_off", "cleaning_services",
            "cloud_done", "sms", "verified", "update", "gpp_bad", "link_off", "task_alt", "edit_note").forEach {
            assertNotNull(InspectionIcons.bySymbol(it), it)
        }
    }
}
