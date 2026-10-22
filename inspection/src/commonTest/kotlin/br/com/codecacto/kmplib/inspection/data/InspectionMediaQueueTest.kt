package br.com.codecacto.kmplib.inspection.data

import br.com.codecacto.kmplib.core.crypto.sha256Hex
import br.com.codecacto.kmplib.core.storage.BlobStore
import br.com.codecacto.kmplib.inspection.capture.CapturedMedia
import br.com.codecacto.kmplib.inspection.capture.InspectionGeo
import br.com.codecacto.kmplib.inspection.model.InspectionMediaKind
import br.com.codecacto.kmplib.inspection.model.InspectionMediaSource
import br.com.codecacto.kmplib.sync.direct.DIRECT_UPLOAD_NO_ETAG
import br.com.codecacto.kmplib.sync.direct.DirectUploadJob
import br.com.codecacto.kmplib.sync.direct.DirectUploadMode
import br.com.codecacto.kmplib.sync.direct.DirectUploadNetworkNeed
import br.com.codecacto.kmplib.sync.direct.DirectUploadOutbox
import br.com.codecacto.kmplib.sync.direct.DirectUploadPartRequest
import br.com.codecacto.kmplib.sync.direct.DirectUploadPartResult
import br.com.codecacto.kmplib.sync.direct.DirectUploadPartTransport
import br.com.codecacto.kmplib.sync.direct.DirectUploadScheduler
import br.com.codecacto.kmplib.sync.direct.DirectUploadStatus
import br.com.codecacto.kmplib.sync.direct.DirectUploadStorage
import br.com.codecacto.kmplib.sync.rest.DomainResult
import br.com.codecacto.kmplib.sync.rest.UploadRetryPolicy
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Disco em memória: "caminho" → bytes. */
private class MemFiles {
    val files = mutableMapOf<String, ByteArray>()
}

private class MemBlobStore(private val fs: MemFiles) : BlobStore {
    override suspend fun write(id: String, bytes: ByteArray): Boolean { fs.files["/blobs/$id"] = bytes; return true }
    override suspend fun read(id: String): ByteArray? = fs.files["/blobs/$id"]
    override suspend fun exists(id: String): Boolean = "/blobs/$id" in fs.files
    override suspend fun sizeOf(id: String): Long = fs.files["/blobs/$id"]?.size?.toLong() ?: 0
    override suspend fun delete(id: String): Boolean = fs.files.remove("/blobs/$id") != null
    override suspend fun ids(): List<String> = fs.files.keys.filter { it.startsWith("/blobs/") }.map { it.removePrefix("/blobs/") }
    override suspend fun localFilePath(id: String): String? = "/blobs/$id".takeIf { it in fs.files }
}

private class MemStorage(private val fs: MemFiles) : DirectUploadStorage {
    val jobs = mutableMapOf<String, DirectUploadJob>()
    override suspend fun loadAll(): List<DirectUploadJob> = jobs.values.toList()
    override suspend fun save(job: DirectUploadJob): Boolean { jobs[job.id] = job; return true }
    override suspend fun importMedia(jobId: String, sourcePath: String, move: Boolean): Long? {
        val b = fs.files[sourcePath] ?: return null
        fs.files[mediaPath(jobId)] = b.copyOf()
        if (move) fs.files.remove(sourcePath)
        return b.size.toLong()
    }
    override fun mediaPath(jobId: String): String = "/fila/$jobId/media"
    override fun partPath(jobId: String, partNumber: Int): String = "/fila/$jobId/part-$partNumber"
    override suspend fun hasMedia(jobId: String, expectedSize: Long): Boolean = fs.files[mediaPath(jobId)]?.size?.toLong() == expectedSize
    override suspend fun delete(jobId: String): Boolean { jobs.remove(jobId); return fs.files.remove(mediaPath(jobId)) != null }
    override suspend fun sizeOf(path: String): Long = fs.files[path]?.size?.toLong() ?: -1
    override suspend fun orphanIds(): List<String> = emptyList()
    override suspend fun mediaSha256(jobId: String): String? = fs.files[mediaPath(jobId)]?.let { sha256Hex(it) }
}

/** Storage remoto falso: grava o PUT; responde o que for programado por parte. */
private class FakeStorageTransport(var reply: (DirectUploadPartRequest) -> DirectUploadPartResult = { DirectUploadPartResult.Uploaded("\"etag-${it.partNumber}\"") }) :
    DirectUploadPartTransport {
    val requests = mutableListOf<DirectUploadPartRequest>()
    override suspend fun upload(request: DirectUploadPartRequest): DirectUploadPartResult {
        requests += request
        return reply(request)
    }
}

private object NoScheduler : DirectUploadScheduler {
    override fun schedule(outbox: DirectUploadOutbox, need: DirectUploadNetworkNeed, urgent: Boolean) {}
}

class InspectionMediaQueueTest {

    private val id = InspectionFixtures.INSPECTION_ID
    private val bytes = ByteArray(2048) { (it % 251).toByte() }
    private val sha = sha256Hex(bytes)

    private fun captured(blobSha: String = sha) = CapturedMedia(
        id = "cap-1", kind = InspectionMediaKind.PHOTO, sha256 = blobSha, capturedAtDeviceMillis = 1_761_134_400_000,
        location = InspectionGeo(-15.6, -56.1, 8f), deviceModel = "samsung SM-A546E", origin = InspectionMediaSource.CAMERA,
        blobId = "insp-cap-1.jpg", sizeBytes = bytes.size.toLong(),
    )

    private class Montagem(val queue: InspectionMediaQueue, val server: FakeInspectionServer, val transport: FakeStorageTransport, val blobs: BlobStore)

    private suspend fun montar(conta: String? = "u1"): Montagem {
        val fs = MemFiles()
        val blobs = MemBlobStore(fs).also { it.write("insp-cap-1.jpg", bytes) }
        val server = FakeInspectionServer()
        val transport = FakeStorageTransport()
        val q = InspectionMediaQueue(
            api = server.api(), blobs = blobs, accountId = { conta }, name = "insp-teste-${kotlin.random.Random.nextInt(1_000_000)}",
            storage = MemStorage(fs), transport = transport, scheduler = NoScheduler,
            retry = UploadRetryPolicy(baseDelayMillis = 0, maxDelayMillis = 0, maxAttempts = 3),
            storageHttp = HttpClient(MockEngine { respond("", HttpStatusCode.OK) }),
        )
        return Montagem(q, server, transport, blobs)
    }

    @Test
    fun `intencao com o hash da captura, PUT com todos os cabecalhos assinados e conclusao`() = runTest {
        val m = montar()
        m.server.on("POST", "/inspections/$id/media", Reply(body = InspectionFixtures.mediaIntentSingle("m1", sha, bytes.size.toLong())))
        m.server.on("POST", "/media/m1/complete", Reply(body = InspectionFixtures.mediaConfirmed("m1", sha, bytes.size.toLong())))
        val r = m.queue.enqueue(id, captured(), InspectionMediaTarget.Item(InspectionFixtures.ITEM_PISO))
        assertIs<InspectionEnqueueResult.Queued>(r)
        assertEquals(DirectUploadStatus.QUEUED, r.media.status)

        val resumo = m.queue.outbox.drainNow()
        assertEquals(1, resumo.completed)

        val intent = InspectionJson.decodeFromString(MediaIntentInput.serializer(), m.server.receivedAt("POST", "/inspections/$id/media").single().body)
        assertEquals(sha, intent.sha256)
        assertEquals(MediaSource.CAMERA, intent.source)
        assertEquals(InspectionFixtures.ITEM_PISO, intent.itemId)
        assertEquals("2025-10-22T12:00:00Z", intent.capturedAtDevice)
        assertEquals(-15.6, intent.lat)
        assertEquals("samsung SM-A546E", intent.deviceModel)

        val put = m.transport.requests.single()
        assertEquals("PUT", put.method)
        assertEquals("image/jpeg", put.contentType)
        assertEquals("*", put.headers["if-none-match"], "o If-None-Match: * do R2 vai junto")
        assertEquals("abc=", put.headers["x-amz-checksum-sha256"])
        assertFalse(put.requireEtag)
        assertTrue(m.server.receivedAt("POST", "/media/m1/complete").isNotEmpty())
        assertTrue(m.queue.observe(id).first().isEmpty(), "saiu da fila")
        // O original da captura continua no aparelho (miniatura local até a pessoa liberar espaço).
        assertTrue(m.blobs.exists("insp-cap-1.jpg"))
    }

    @Test
    fun `objeto que ja existe no storage conta como enviado e o complete decide`() = runTest {
        val m = montar()
        m.transport.reply = { DirectUploadPartResult.Failed(412) }
        m.server.on("POST", "/inspections/$id/media", Reply(body = InspectionFixtures.mediaIntentSingle("m1", sha, bytes.size.toLong())))
        m.server.on("POST", "/media/m1/complete", Reply(body = InspectionFixtures.mediaConfirmed("m1", sha, bytes.size.toLong())))
        m.queue.enqueue(id, captured(), InspectionMediaTarget.Item(InspectionFixtures.ITEM_PISO))
        assertEquals(1, m.queue.outbox.drainNow().completed)
    }

    @Test
    fun `arquivo nao chegou ao storage reenvia`() = runTest {
        val m = montar()
        m.server.on("POST", "/inspections/$id/media", Reply(body = InspectionFixtures.mediaIntentSingle("m1", sha, bytes.size.toLong())))
        m.server.onSeq(
            "POST", "/media/m1/complete",
            Reply(409, """{"message":"O arquivo ainda não chegou","code":"MEDIA_NOT_UPLOADED"}"""),
            Reply(body = InspectionFixtures.mediaConfirmed("m1", sha, bytes.size.toLong())),
        )
        m.queue.enqueue(id, captured(), InspectionMediaTarget.Item(InspectionFixtures.ITEM_PISO))
        m.queue.outbox.drainNow()
        m.queue.outbox.drainNow()
        assertEquals(2, m.transport.requests.size, "o PUT foi refeito")
        assertTrue(m.queue.observe(id).first().isEmpty())
    }

    @Test
    fun `cota no registro da midia para a fila e marca isQuota`() = runTest {
        val m = montar()
        m.server.on("POST", "/inspections/$id/media", Reply(402, """{"message":"Limite","code":"QUOTA_EXCEEDED","details":{"feature":"vistorias","limite":"5","contagem":"5"}}"""))
        m.queue.enqueue(id, captured(), InspectionMediaTarget.Item(InspectionFixtures.ITEM_PISO))
        m.queue.outbox.drainNow()
        val q = m.queue.observe(id).first().single()
        assertTrue(q.isFailed)
        assertEquals(402, q.failure?.code)
    }

    @Test
    fun `copia que nao confere com a captura nao entra`() = runTest {
        val m = montar()
        val r = m.queue.enqueue(id, captured(blobSha = "f".repeat(64)), InspectionMediaTarget.Item(InspectionFixtures.ITEM_PISO))
        assertEquals(InspectionEnqueueResult.Rejected(InspectionEnqueueRejection.HASH_MISMATCH), r)
        assertTrue(m.queue.observe(id).first().isEmpty())
    }

    @Test
    fun `nota de voz sem conta ou sem arquivo sao recusadas`() = runTest {
        val m = montar()
        assertEquals(
            InspectionEnqueueResult.Rejected(InspectionEnqueueRejection.UNSUPPORTED_KIND),
            m.queue.enqueue(id, captured().copy(kind = InspectionMediaKind.VOICE), InspectionMediaTarget.Item("i")),
        )
        assertEquals(
            InspectionEnqueueResult.Rejected(InspectionEnqueueRejection.SOURCE_MISSING),
            m.queue.enqueue(id, captured().copy(blobId = "nao-existe.jpg"), InspectionMediaTarget.Item("i")),
        )
        assertEquals(
            InspectionEnqueueResult.Rejected(InspectionEnqueueRejection.INVALID),
            m.queue.enqueue(id, captured(), InspectionMediaTarget.Item(" ")),
        )
        val semConta = montar(conta = null)
        assertEquals(
            InspectionEnqueueResult.Rejected(InspectionEnqueueRejection.NO_ACCOUNT),
            semConta.queue.enqueue(id, captured(), InspectionMediaTarget.Item("i")),
        )
    }

    // ── Peças puras ──────────────────────────────────────────────────────────────────────────────

    private fun job(size: Long = 100) = DirectUploadJob(
        id = "j", accountId = "u", kind = InspectionMediaQueue.KIND, targetId = id, contentType = "video/mp4",
        sizeBytes = size, fileName = "f", createdAtMillis = 0,
    )

    @Test
    fun `sessao multipart com as partes e confirmada sem envio`() {
        val dto = InspectionJson.decodeFromString(
            MediaIntentDto.serializer(),
            """{"media":{"id":"mv","inspectionId":"$id","kind":"VIDEO","source":"CAMERA","sha256":"${"a".repeat(64)}","status":"QUEUED"},
               "upload":{"mode":"MULTIPART","partSizeBytes":60,"partCount":2,"parts":[
                 {"partNumber":1,"sizeBytes":60,"url":"https://r2/p1","headers":{},"expiresAt":"2099-01-01T00:00:00Z"},
                 {"partNumber":2,"sizeBytes":40,"url":"https://r2/p2","headers":{},"expiresAt":"2098-01-01T00:00:00Z"}]}}""",
        )
        val s = (sessionOf(job(), dto) as DomainResult.Success).data
        assertEquals(DirectUploadMode.MULTIPART, s.mode)
        assertEquals("mv", s.remoteId)
        assertEquals(2, s.partUrls.size)
        assertEquals(kotlin.time.Instant.parse("2098-01-01T00:00:00Z").toEpochMilliseconds(), s.urlsExpireAtMillis, "vence com a primeira")

        val confirmada = dto.copy(upload = null, media = dto.media.copy(status = MediaStatus.CONFIRMED))
        assertTrue((sessionOf(job(), confirmada) as DomainResult.Success).data.alreadyReceived)
    }

    @Test
    fun `transporte so tolera 412 e 409 no PUT unico`() = runTest {
        val t = ExistingObjectTolerantTransport(FakeStorageTransport { DirectUploadPartResult.Failed(412) })
        fun req(etag: Boolean) = DirectUploadPartRequest("j", 1, "https://x", "/f", 0, 1, "/p", false, requireEtag = etag)
        assertEquals(DirectUploadPartResult.Uploaded(DIRECT_UPLOAD_NO_ETAG), t.upload(req(etag = false)))
        assertEquals(DirectUploadPartResult.Failed(412), t.upload(req(etag = true)))
    }

    @Test
    fun `intencao da galeria nao leva GPS e video leva duracao`() {
        val galeria = captured().copy(origin = InspectionMediaSource.GALLERY, location = null)
        val i = buildMediaIntent(galeria, MediaKind.PHOTO, InspectionMediaTarget.Group("g"))
        assertEquals(MediaSource.GALLERY, i.source)
        assertNull(i.lat)
        assertEquals("g", i.groupId)
        assertNull(i.durationMs)
        val v = buildMediaIntent(captured(), MediaKind.VIDEO, InspectionMediaTarget.Item("i", taskId = "t"), durationMs = 4000)
        assertEquals(4000, v.durationMs)
        assertEquals("t", v.taskId)
        assertFalse("-15.6" in v.toString(), "GPS fora do log")
    }
}
