package br.com.codecacto.kmplib.firebase.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UploadItemTest {

    @Test
    fun `percent deriva e clampa da fraction`() {
        assertEquals(0, UploadItem("1", "a.jpg", fraction = -0.5f).percent)
        assertEquals(50, UploadItem("1", "a.jpg", fraction = 0.5f).percent)
        assertEquals(100, UploadItem("1", "a.jpg", fraction = 2f).percent)
    }

    @Test
    fun `isTerminal cobre COMPLETED e FAILED`() {
        assertTrue(UploadItem("1", "a", status = UploadStatus.COMPLETED).isTerminal)
        assertTrue(UploadItem("1", "a", status = UploadStatus.FAILED).isTerminal)
        assertFalse(UploadItem("1", "a", status = UploadStatus.UPLOADING).isTerminal)
        assertFalse(UploadItem("1", "a", status = UploadStatus.PENDING).isTerminal)
    }

    @Test
    fun `flags de estado`() {
        assertTrue(UploadItem("1", "a", status = UploadStatus.FAILED).isFailed)
        assertTrue(UploadItem("1", "a", status = UploadStatus.UPLOADING).isUploading)
    }

    @Test
    fun `UploadRequest equals compara bytes`() {
        val a = UploadRequest("1", "a.jpg", "p/a", byteArrayOf(1, 2), "image/jpeg")
        val b = UploadRequest("1", "a.jpg", "p/a", byteArrayOf(1, 2), "image/jpeg")
        val c = UploadRequest("1", "a.jpg", "p/a", byteArrayOf(9), "image/jpeg")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a != c)
    }
}
