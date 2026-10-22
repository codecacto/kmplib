package br.com.codecacto.kmplib.inspection

import br.com.codecacto.kmplib.core.crypto.sha256Hex
import br.com.codecacto.kmplib.core.storage.BlobStore
import br.com.codecacto.kmplib.core.storage.InMemoryBlobStore
import br.com.codecacto.kmplib.inspection.capture.InspectionCaptureError
import br.com.codecacto.kmplib.inspection.capture.InspectionCaptureResult
import br.com.codecacto.kmplib.inspection.capture.InspectionCaptureWriter
import br.com.codecacto.kmplib.inspection.capture.InspectionGeo
import br.com.codecacto.kmplib.inspection.model.InspectionMediaSource
import br.com.codecacto.kmplib.ui.components.PickedImage
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InspectionCaptureWriterTest {

    private val foto = PickedImage(byteArrayOf(1, 2, 3, 4, 5), widthPx = 4, heightPx = 3)

    @Test
    fun grava_antes_de_devolver_com_hash_dos_bytes_gravados() = runTest {
        val store = InMemoryBlobStore()
        val writer = InspectionCaptureWriter(
            store = store,
            location = { InspectionGeo(-23.55, -46.63) },
            clock = { 1_760_000_000_000L },
            deviceModel = { "teste X1" },
            io = StandardTestDispatcher(testScheduler),
        )
        val r = assertIs<InspectionCaptureResult.Saved>(writer.persist(foto))
        val m = r.media
        assertEquals(sha256Hex(foto.bytes), m.sha256)
        assertContentEquals(foto.bytes, store.read(m.blobId))
        assertEquals("insp-${m.id}.jpg", m.blobId)
        assertEquals(1_760_000_000_000L, m.capturedAtDeviceMillis)
        assertEquals("teste X1", m.deviceModel)
        assertEquals(InspectionMediaSource.CAMERA, m.origin)
        assertEquals(4, m.widthPx)
        assertTrue(m.location != null)
        assertFalse("-23" in m.toString())
    }

    @Test
    fun gps_lento_nao_segura_a_foto() = runTest {
        val writer = InspectionCaptureWriter(
            store = InMemoryBlobStore(),
            location = { awaitCancellation() },
            locationTimeoutMillis = 500,
            io = StandardTestDispatcher(testScheduler),
        )
        val r = assertIs<InspectionCaptureResult.Saved>(writer.persist(foto))
        assertNull(r.media.location)
    }

    @Test
    fun gps_que_falha_vira_sem_localizacao() = runTest {
        val writer = InspectionCaptureWriter(InMemoryBlobStore(), location = { error("negado") }, io = StandardTestDispatcher(testScheduler))
        assertNull(assertIs<InspectionCaptureResult.Saved>(writer.persist(foto)).media.location)
    }

    @Test
    fun anexo_da_galeria_nao_ganha_gps() = runTest {
        var perguntou = false
        val writer = InspectionCaptureWriter(InMemoryBlobStore(), location = { perguntou = true; InspectionGeo(1.0, 2.0) }, io = StandardTestDispatcher(testScheduler))
        val r = assertIs<InspectionCaptureResult.Saved>(writer.persist(foto, InspectionMediaSource.GALLERY))
        assertNull(r.media.location)
        assertFalse(perguntou)
    }

    @Test
    fun disco_cheio_e_conteudo_vazio_viram_erro_sem_perder_a_tela() = runTest {
        val cheio = object : BlobStore by InMemoryBlobStore() {
            override suspend fun write(id: String, bytes: ByteArray): Boolean = false
        }
        val w = InspectionCaptureWriter(cheio, io = StandardTestDispatcher(testScheduler))
        assertEquals(InspectionCaptureError.STORAGE, assertIs<InspectionCaptureResult.Failed>(w.persist(foto)).error)
        val vazio = PickedImage(ByteArray(0), 0, 0)
        assertEquals(InspectionCaptureError.EMPTY, assertIs<InspectionCaptureResult.Failed>(w.persist(vazio)).error)
    }

    @Test
    fun ids_diferentes_a_cada_captura() = runTest {
        val w = InspectionCaptureWriter(InMemoryBlobStore(), io = StandardTestDispatcher(testScheduler))
        val a = assertIs<InspectionCaptureResult.Saved>(w.persist(foto)).media
        val b = assertIs<InspectionCaptureResult.Saved>(w.persist(foto)).media
        assertTrue(a.id != b.id)
        assertEquals(a.sha256, b.sha256)
    }
}
