package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.firebase.storage.UploadRequest
import br.com.codecacto.kmplib.firebase.storage.UploadStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RestUploadQueueTest {

    private val jsonHeader = headersOf("Content-Type", "application/json")

    private fun api(failFirst: Boolean = false): DomainApiClient {
        var attempt = 0
        val engine = MockEngine {
            attempt++
            if (failFirst && attempt == 1) throw RuntimeException("net")
            respond("""{"id":"a1"}""", HttpStatusCode.Created, jsonHeader)
        }
        return DomainApiClient(HttpClient(engine), DomainTokenProvider { "tok" }, "https://api.example.com")
    }

    private fun req(id: String) =
        UploadRequest(id = id, fileName = "$id.jpg", path = "/v1/lancamentos/1/anexos", bytes = byteArrayOf(1, 2, 3), mimeType = "image/jpeg")

    @Test
    fun `upload multipart bem-sucedido marca COMPLETED e chama onUploaded`() = runTest {
        var body: String? = null
        val q = RestUploadQueue(api()) { _, resp -> body = resp }
        q.enqueue(req("u1"))
        q.process()
        assertEquals(UploadStatus.COMPLETED, q.items.value.single().status)
        assertEquals("""{"id":"a1"}""", body)
        assertTrue(q.isAllDone)
    }

    @Test
    fun `falha de rede marca FAILED e retry conclui`() = runTest {
        val q = RestUploadQueue(api(failFirst = true))
        q.enqueue(req("u2"))
        q.process()
        assertEquals(UploadStatus.FAILED, q.items.value.single().status)
        q.retry("u2")
        assertEquals(UploadStatus.COMPLETED, q.items.value.single().status)
    }

    private fun partsReq(id: String) = MultipartUploadRequest(
        id = id,
        fileName = "$id-foto-prova",
        path = "/v1/inspections/1/photos",
        parts = listOf(
            MultipartPart("full", "$id-full.jpg", byteArrayOf(1, 2, 3), "image/jpeg"),
            MultipartPart("thumb", "$id-thumb.jpg", byteArrayOf(4, 5), "image/jpeg"),
        ),
    )

    @Test
    fun `enqueueParts sobe multipart de multiplas partes e marca COMPLETED`() = runTest {
        var body: String? = null
        val q = RestUploadQueue(api()) { _, resp -> body = resp }
        q.enqueueParts(partsReq("p1"))
        q.process()
        assertEquals(UploadStatus.COMPLETED, q.items.value.single().status)
        assertEquals("""{"id":"a1"}""", body)
        assertTrue(q.isAllDone)
    }

    @Test
    fun `enqueueParts com falha marca FAILED e retry conclui`() = runTest {
        val q = RestUploadQueue(api(failFirst = true))
        q.enqueueParts(partsReq("p2"))
        q.process()
        assertEquals(UploadStatus.FAILED, q.items.value.single().status)
        q.retry("p2")
        assertEquals(UploadStatus.COMPLETED, q.items.value.single().status)
    }
}
