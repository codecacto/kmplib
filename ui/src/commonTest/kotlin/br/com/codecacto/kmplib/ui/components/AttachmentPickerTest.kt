package br.com.codecacto.kmplib.ui.components

import br.com.codecacto.kmplib.platform.FilePickFailure
import br.com.codecacto.kmplib.platform.FilePickResult
import br.com.codecacto.kmplib.platform.PickedFile
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttachmentPickerTest {

    private val todas = listOf(AttachmentSource.CAMERA, AttachmentSource.GALLERY, AttachmentSource.FILE)

    // ---- forma derivada dos MIME ------------------------------------------------------------

    @Test
    fun soImagensViraImagesOnly() {
        assertEquals(AttachmentAcceptKind.IMAGES_ONLY, AttachmentAccept.forMimeTypes(listOf("image/*")).kind)
        assertEquals(
            AttachmentAcceptKind.IMAGES_ONLY,
            AttachmentAccept.forMimeTypes(listOf("image/jpeg", " IMAGE/PNG ")).kind,
        )
        assertEquals(AttachmentAcceptKind.IMAGES_ONLY, AttachmentAccept.ImagesOnly.kind)
    }

    @Test
    fun qualquerTipoOuVazioAceitaFoto() {
        assertEquals(AttachmentAccept.AnyFile, AttachmentAccept.forMimeTypes(listOf("*/*")))
        assertEquals(AttachmentAccept.AnyFile, AttachmentAccept.forMimeTypes(emptyList()))
        assertEquals(AttachmentAccept.AnyFile, AttachmentAccept.forMimeTypes(listOf(" ", "")))
        assertEquals(AttachmentAccept.AnyFile, AttachmentAccept.forMimeTypes(listOf("application/pdf", "*/*")))
        assertTrue(AttachmentAccept.AnyFile.acceptsPhotos)
    }

    @Test
    fun documentoMaisImagemViraImagesAndFiles() {
        val accept = AttachmentAccept.forMimeTypes(listOf("application/pdf", "image/*"))
        assertEquals(AttachmentAcceptKind.IMAGES_AND_FILES, accept.kind)
        assertEquals(listOf("application/pdf", "image/*"), accept.fileMimeTypes)
    }

    @Test
    fun semImagemViraFilesOnly() {
        val accept = AttachmentAccept.forMimeTypes(listOf("application/pdf", "application/pdf"))
        assertEquals(AttachmentAcceptKind.FILES_ONLY, accept.kind)
        assertEquals(listOf("application/pdf"), accept.fileMimeTypes)
        assertFalse(accept.acceptsPhotos)
    }

    // ---- qual folha aparece --------------------------------------------------------------------

    @Test
    fun aceitaFotoSempreMostraAsTresOrigensNaOrdem() {
        listOf(
            AttachmentAccept.ImagesOnly,
            AttachmentAccept.AnyFile,
            AttachmentAccept.forMimeTypes(listOf("application/pdf", "image/png")),
        ).forEach { accept ->
            val plan = attachmentPickerPlan(accept)
            assertTrue(plan.showsSheet, "$accept")
            assertEquals(todas, plan.options, "$accept")
        }
    }

    @Test
    fun soImagemOArquivoPassaPeloTratamentoDeFoto() {
        val plan = attachmentPickerPlan(AttachmentAccept.ImagesOnly)
        assertTrue(plan.fileDeliversPhoto)
        assertEquals(listOf("image/*"), plan.fileMimeTypes)
        assertFalse(attachmentPickerPlan(AttachmentAccept.AnyFile).fileDeliversPhoto)
    }

    @Test
    fun naoAceitaFotoAbreSeletorDiretoSemFolha() {
        val plan = attachmentPickerPlan(AttachmentAccept.forMimeTypes(listOf("application/pdf")))
        assertFalse(plan.showsSheet)
        assertTrue(plan.options.isEmpty())
        assertFalse(plan.fileDeliversPhoto)
        assertEquals(listOf("application/pdf"), plan.fileMimeTypes)
    }

    @Test
    fun testTagsSaoEstaveis() {
        assertEquals("anexo-opcao-camera", AttachmentPickerTestTags.of(AttachmentSource.CAMERA))
        assertEquals("anexo-opcao-galeria", AttachmentPickerTestTags.of(AttachmentSource.GALLERY))
        assertEquals("anexo-opcao-arquivo", AttachmentPickerTestTags.of(AttachmentSource.FILE))
        assertEquals("anexo-folha", AttachmentPickerTestTags.FOLHA)
    }

    // ---- nomes ------------------------------------------------------------------------------

    @Test
    fun nomeDaFotoPelaHoraLocal() {
        val hora = LocalDateTime(2026, 10, 7, 9, 5, 3)
        assertEquals("foto-20261007-090503.jpg", photoAttachmentFileName(hora))
        assertEquals("foto-20261007-090503-2.jpg", photoAttachmentFileName(hora, sequence = 2))
        assertEquals("foto-20261007-090503.jpg", photoAttachmentFileName(hora, sequence = 0))
    }

    @Test
    fun arquivoDeImagemRecodificadoGanhaJpg() {
        assertEquals("IMG_0042.jpg", jpegFileNameFor("IMG_0042.HEIC", "f.jpg"))
        assertEquals("casa.frente.jpg", jpegFileNameFor("casa.frente.png", "f.jpg"))
        assertEquals("semextensao.jpg", jpegFileNameFor("semextensao", "f.jpg"))
        assertEquals("f.jpg", jpegFileNameFor(".heic", "f.jpg"))
        assertEquals("f.jpg", jpegFileNameFor("  ", "f.jpg"))
    }

    // ---- mapeamento de resultados --------------------------------------------------------------

    @Test
    fun errosDoSeletorDeImagemMapeiamUmAUm() {
        assertEquals(AttachmentPickerError.CAMERA_PERMISSION_DENIED, ImagePickerError.CAMERA_PERMISSION_DENIED.toAttachmentError())
        assertEquals(AttachmentPickerError.CAMERA_UNAVAILABLE, ImagePickerError.CAMERA_UNAVAILABLE.toAttachmentError())
        assertEquals(AttachmentPickerError.IMAGE_UNREADABLE, ImagePickerError.IMAGE_UNREADABLE.toAttachmentError())
    }

    @Test
    fun errosDeLeituraDoArquivoDeImagem() {
        assertEquals(AttachmentPickerError.FILE_TOO_LARGE, FilePickResult.TooLarge("a", 10, 5).toAttachmentError())
        assertEquals(AttachmentPickerError.FILE_UNREADABLE, FilePickResult.Failed("a", FilePickFailure.Unreadable).toAttachmentError())
        assertEquals(AttachmentPickerError.FILE_TOO_LARGE, FilePickResult.Failed("a", FilePickFailure.OutOfMemory).toAttachmentError())
        assertNull(FilePickResult.Cancelled.toAttachmentError())
    }

    private fun foto(bytes: Int = 4) = PickedAttachment.Photo(
        image = PickedImage(ByteArray(bytes) { 1 }, widthPx = 30, heightPx = 40),
        origin = AttachmentSource.CAMERA,
        fileName = "foto-20261007-090503.jpg",
    )

    @Test
    fun fotoViraAnexoJpegComNome() = runTest {
        val r = assertIs<FilePickResult.Picked>(foto().readForUpload(maxBytes = 100))
        assertEquals("foto-20261007-090503.jpg", r.file.name)
        assertEquals("image/jpeg", r.file.mimeType)
        assertEquals(4L, r.file.size)
    }

    @Test
    fun fotoAcimaDoTetoNaoViraAnexo() = runTest {
        val r = assertIs<FilePickResult.TooLarge>(foto(bytes = 10).readForUpload(maxBytes = 5))
        assertEquals(10L, r.sizeBytes)
    }

    @Test
    fun arquivoLeComOProprioTeto() = runTest {
        var tetoPedido = 0L
        val arquivo = PickedFile("contrato.pdf", "application/pdf", 3) { teto ->
            tetoPedido = teto
            FilePickResult.Failed("contrato.pdf", FilePickFailure.Unreadable)
        }
        val anexo = PickedAttachment.File(arquivo)
        assertEquals(AttachmentSource.FILE, anexo.origin)
        assertEquals("application/pdf", anexo.mimeType)
        assertIs<FilePickResult.Failed>(anexo.readForUpload(maxBytes = 77))
        assertEquals(77L, tetoPedido)
    }

    @Test
    fun fotosFiltraSoAsFotos() {
        val arquivo = PickedAttachment.File(PickedFile("a.pdf", "application/pdf", 1) { FilePickResult.Cancelled })
        assertEquals(1, listOf(foto(), arquivo).photos().size)
    }

    @Test
    fun toStringNaoExpoeNomeNemBytes() {
        val texto = foto().toString()
        assertFalse("foto-2026" in texto)
        assertTrue("30x40" in texto)
        val arquivo = PickedAttachment.File(PickedFile("cnh-joao.pdf", "application/pdf", 1) { FilePickResult.Cancelled })
        assertFalse("joao" in arquivo.toString())
    }
}
