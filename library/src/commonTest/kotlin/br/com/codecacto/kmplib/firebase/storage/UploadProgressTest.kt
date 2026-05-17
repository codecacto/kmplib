package br.com.codecacto.kmplib.firebase.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UploadProgressTest {

    // ====== Started ======

    @Test
    fun `Started carrega total de bytes`() {
        val s = UploadProgress.Started(totalBytes = 2048L)
        assertEquals(2048L, s.totalBytes)
    }

    // ====== Uploading.fraction ======

    @Test
    fun `Uploading fraction em zero progresso`() {
        val u = UploadProgress.Uploading(transferred = 0L, total = 100L)
        assertEquals(0f, u.fraction)
        assertEquals(0, u.percent)
    }

    @Test
    fun `Uploading fraction em progresso intermediario`() {
        val u = UploadProgress.Uploading(transferred = 50L, total = 100L)
        assertEquals(0.5f, u.fraction)
        assertEquals(50, u.percent)
    }

    @Test
    fun `Uploading fraction em 100 porcento`() {
        val u = UploadProgress.Uploading(transferred = 100L, total = 100L)
        assertEquals(1f, u.fraction)
        assertEquals(100, u.percent)
    }

    @Test
    fun `Uploading fraction clampa quando transferred maior que total`() {
        val u = UploadProgress.Uploading(transferred = 150L, total = 100L)
        assertEquals(1f, u.fraction)
        assertEquals(100, u.percent)
    }

    @Test
    fun `Uploading fraction zero quando total e zero`() {
        val u = UploadProgress.Uploading(transferred = 100L, total = 0L)
        assertEquals(0f, u.fraction)
        assertEquals(0, u.percent)
    }

    @Test
    fun `Uploading fraction zero quando total negativo`() {
        // edge case defensivo — total não deveria ser negativo, mas API
        // não impede; verificamos que não explode
        val u = UploadProgress.Uploading(transferred = 50L, total = -1L)
        assertEquals(0f, u.fraction)
        assertEquals(0, u.percent)
    }

    @Test
    fun `Uploading percent arredonda para baixo`() {
        // 33.333% -> 33
        val u = UploadProgress.Uploading(transferred = 33L, total = 99L)
        // 33 / 99 = 0.3333... -> 33
        assertEquals(33, u.percent)
    }

    @Test
    fun `Uploading fraction preserva igualdade entre instancias`() {
        val a = UploadProgress.Uploading(transferred = 25L, total = 100L)
        val b = UploadProgress.Uploading(transferred = 25L, total = 100L)
        assertEquals(a, b)
        assertEquals(a.fraction, b.fraction)
    }

    // ====== Completed ======

    @Test
    fun `Completed carrega downloadUrl e totalBytes`() {
        val c = UploadProgress.Completed(downloadUrl = "https://x/y.jpg", totalBytes = 4096L)
        assertEquals("https://x/y.jpg", c.downloadUrl)
        assertEquals(4096L, c.totalBytes)
    }

    // ====== Failed ======

    @Test
    fun `Failed carrega causa`() {
        val cause = RuntimeException("boom")
        val f = UploadProgress.Failed(cause = cause)
        assertEquals(cause, f.cause)
    }

    // ====== Discriminação por tipo ======

    @Test
    fun `sealed permite when exhaustive`() {
        val cases: List<UploadProgress> = listOf(
            UploadProgress.Started(10L),
            UploadProgress.Uploading(5L, 10L),
            UploadProgress.Completed("u", 10L),
            UploadProgress.Failed(RuntimeException())
        )
        val labels = cases.map {
            when (it) {
                is UploadProgress.Started -> "start"
                is UploadProgress.Uploading -> "up"
                is UploadProgress.Completed -> "done"
                is UploadProgress.Failed -> "err"
            }
        }
        assertEquals(listOf("start", "up", "done", "err"), labels)
    }

    @Test
    fun `cada subclasse e distinguivel`() {
        val p: UploadProgress = UploadProgress.Started(100L)
        assertIs<UploadProgress.Started>(p)
        assertTrue(p !is UploadProgress.Uploading)
        assertTrue(p !is UploadProgress.Completed)
        assertTrue(p !is UploadProgress.Failed)
    }
}
