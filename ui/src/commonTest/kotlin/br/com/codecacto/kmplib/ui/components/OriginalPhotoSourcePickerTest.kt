package br.com.codecacto.kmplib.ui.components

import br.com.codecacto.kmplib.platform.photos.OriginalPhotoSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OriginalPhotoSourcePickerTest {

    @Test
    fun padraoDaFabricaAbreAFolhaComGaleriaEArquivoNestaOrdem() {
        val plano = originalPhotoPickerPlan(OriginalPhotoSource.Standard)
        assertTrue(plano.showsSheet)
        assertEquals(listOf(AttachmentSource.GALLERY, AttachmentSource.FILE), plano.options)
    }

    @Test
    fun cameraNuncaEntraNaFolhaDeOriginais() {
        OriginalPhotoSource.entries.forEach { origem ->
            assertFalse(AttachmentSource.CAMERA in originalPhotoPickerPlan(listOf(origem)).options)
        }
        assertFalse(AttachmentSource.CAMERA in originalPhotoPickerPlan(OriginalPhotoSource.Standard).options)
    }

    @Test
    fun umaOrigemSoAbreDiretoSemFolha() {
        val galeria = originalPhotoPickerPlan(listOf(OriginalPhotoSource.GALLERY))
        assertFalse(galeria.showsSheet)
        assertEquals(listOf(AttachmentSource.GALLERY), galeria.options)
        val arquivo = originalPhotoPickerPlan(listOf(OriginalPhotoSource.FILE, OriginalPhotoSource.FILE))
        assertFalse(arquivo.showsSheet, "repetida conta uma vez")
        assertEquals(listOf(AttachmentSource.FILE), arquivo.options)
    }

    @Test
    fun ordemPedidaERespeitada() {
        val plano = originalPhotoPickerPlan(listOf(OriginalPhotoSource.FILE, OriginalPhotoSource.GALLERY))
        assertEquals(listOf(AttachmentSource.FILE, AttachmentSource.GALLERY), plano.options)
    }

    @Test
    fun listaVaziaCaiNoPadraoNuncaNumBotaoMorto() {
        assertEquals(originalPhotoPickerPlan(OriginalPhotoSource.Standard), originalPhotoPickerPlan(emptyList()))
    }
}
