package br.com.codecacto.kmplib.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 2.218.0 — originais de câmera esquecidos (EXIF/GPS) saem no início do app e na limpeza da conta. */
class CameraCaptureFilesTest {

    private val agora = 1_700_000_000_000L

    @Test
    fun `no inicio do app so sai o original mais velho que a folga`() {
        val ttl = DEFAULT_CAMERA_CAPTURE_TTL_MILLIS
        assertTrue(shouldPurgeCameraCapture("camera_1.jpg", agora - ttl, agora, ttl))
        assertFalse(
            shouldPurgeCameraCapture("camera_2.jpg", agora - 1_000, agora, ttl),
            "captura em curso: o processo pode estar sendo recriado para recebê-la",
        )
    }

    @Test
    fun `na limpeza da conta sai todo original de qualquer idade`() {
        assertTrue(shouldPurgeCameraCapture("camera_2.jpg", agora, agora, 0L))
    }

    @Test
    fun `arquivo que nao e original da camera nunca sai`() {
        assertFalse(shouldPurgeCameraCapture("outra.jpg", 0L, agora, 0L))
    }
}
