package br.com.codecacto.kmplib.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileDataTest {

    @Test
    fun derivesExtensionFromName() {
        val file = FileData(
            name = "contrato.pdf",
            mimeType = "application/pdf",
            data = byteArrayOf(1, 2, 3),
            size = 3
        )

        assertEquals("pdf", file.extension)
    }

    @Test
    fun classifiesCommonFileTypes() {
        assertTrue(file("foto.jpg", "image/jpeg").isImage)
        assertTrue(file("contrato.pdf", "application/pdf").isPdf)
        assertTrue(file("contrato.pdf", "application/pdf").isDocument)
        assertTrue(file("doc.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document").isDocument)
        assertFalse(file("video.mp4", "video/mp4").isDocument)
    }

    private fun file(name: String, mimeType: String): FileData {
        return FileData(
            name = name,
            mimeType = mimeType,
            data = byteArrayOf(1),
            size = 1
        )
    }
}
