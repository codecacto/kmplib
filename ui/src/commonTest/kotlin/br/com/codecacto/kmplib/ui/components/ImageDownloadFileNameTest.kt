package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/** Nome do arquivo do "baixar" do `FullScreenImageViewer` (2.256.0) — par do `ImageLightbox` web. */
class ImageDownloadFileNameTest {

    private val firebase =
        "https://firebasestorage.googleapis.com/v0/b/bucket/o/products%2Fabc%2Ffoto-1?alt=media&token=t"

    @Test
    fun `sem nome usa o ultimo trecho do caminho e a extensao do tipo`() {
        assertEquals("foto-1.webp", imageDownloadFileName(firebase, "image/webp"))
    }

    @Test
    fun `nome dado vence o da url`() {
        assertEquals("Betoneira.png", imageDownloadFileName(firebase, "image/png", nome = "Betoneira"))
    }

    @Test
    fun `barra no nome vira hifen`() {
        assertEquals(
            "Calcalhadeira 6000 P- Adubos Secos.jpg",
            imageDownloadFileName(firebase, "image/jpeg", nome = "Calcalhadeira 6000 P/ Adubos Secos"),
        )
    }

    @Test
    fun `extensao existente e mantida`() {
        assertEquals("foto.jpg", imageDownloadFileName("https://x/a/foto.jpg?v=1", "image/png"))
    }

    @Test
    fun `tipo com parametro e maiusculas ainda resolve`() {
        assertEquals("foto.jpg", imageDownloadFileName("https://x/a/foto", "Image/JPEG; charset=binary"))
    }

    @Test
    fun `tipo desconhecido fica sem extensao e caminho vazio vira imagem`() {
        assertEquals("imagem", imageDownloadFileName("https://x/", "application/octet-stream"))
        assertEquals("imagem", imageDownloadFileName("https://x", null))
        assertEquals("imagem.png", imageDownloadFileName("https://x/", "image/png", nome = "   "))
    }
}
