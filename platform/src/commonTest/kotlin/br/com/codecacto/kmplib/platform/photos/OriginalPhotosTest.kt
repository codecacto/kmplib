package br.com.codecacto.kmplib.platform.photos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OriginalPhotosTest {

    @Test
    fun extensaoSaiDoTipoRealEHeicContinuaHeic() {
        assertEquals("heic", imageExtensionForMimeType("image/heic"))
        assertEquals("jpg", imageExtensionForMimeType("IMAGE/JPEG; charset=binary"))
        assertEquals("dng", imageExtensionForMimeType("image/x-adobe-dng"))
        assertNull(imageExtensionForMimeType("application/pdf"))
        assertNull(imageExtensionForMimeType(null))
    }

    @Test
    fun tipoPelaExtensaoIgnoraCaixa() {
        assertEquals("image/heic", imageMimeTypeForFileName("IMG_0042.HEIC"))
        assertEquals("image/jpeg", imageMimeTypeForFileName("a.jpeg"))
        assertEquals("image/tiff", imageMimeTypeForFileName("scan.tif"))
        assertNull(imageMimeTypeForFileName("sem-extensao"))
    }

    @Test
    fun nomeGanhaAExtensaoDoFormatoSoQuandoFalta() {
        assertEquals("IMG_0042.HEIC", originalPhotoFileName("IMG_0042.HEIC", "heic"))
        assertEquals("IMG_0042.heic", originalPhotoFileName("IMG_0042", "heic"))
        assertEquals("ferias.jpeg", originalPhotoFileName("ferias.jpeg", "jpg"), "jpeg e jpg são o mesmo formato")
        assertEquals("foto.png", originalPhotoFileName("  ", "png"))
        assertEquals("foto", originalPhotoFileName(null, null))
    }

    @Test
    fun nomeSugeridoNaoLevaCaminhoNemCaractereProibido() {
        assertEquals("x.jpg", originalPhotoFileName("../../etc/x.jpg", "jpg"))
        assertEquals("a_b_c.jpg", originalPhotoFileName("a:b?c", "jpg"))
        assertEquals("evil.jpg", originalPhotoFileName("C:\\fotos\\evil.jpg", "jpg"))
    }

    @Test
    fun resultadoVazioSemFalhaEDesistencia() {
        assertTrue(OriginalPhotoPickResult(emptyList()).isCancelled)
        assertFalse(OriginalPhotoPickResult(emptyList(), failedCount = 2).isCancelled)
    }

    @Test
    fun toStringNaoMostraNomeNemCaminho() {
        val f = OriginalPhoto("/cache/kmplib_original_photos/u.heic", "Joao-Silva-RG.heic", "image/heic", 10)
        assertFalse("Joao" in f.toString())
        assertFalse("/cache" in f.toString())
    }

    @Test
    fun galeriaSoGuardaImagemEVideo() {
        assertEquals(GalleryMediaKind.IMAGE, galleryMediaKindOf("image/heic"))
        assertEquals(GalleryMediaKind.VIDEO, galleryMediaKindOf(" Video/MP4 "))
        assertNull(galleryMediaKindOf("application/pdf"))
        assertNull(galleryMediaKindOf("image/"))
    }

    @Test
    fun albumNaoEscapaDaPastaDeImagens() {
        assertEquals("Lumio", sanitizeGalleryAlbum("Lumio"))
        assertEquals("_x", sanitizeGalleryAlbum("../x"))
        assertEquals("a_b", sanitizeGalleryAlbum("a/b"))
        assertNull(sanitizeGalleryAlbum("  "))
        assertNull(sanitizeGalleryAlbum(".."))
        assertNull(sanitizeGalleryAlbum(null))
    }
}
