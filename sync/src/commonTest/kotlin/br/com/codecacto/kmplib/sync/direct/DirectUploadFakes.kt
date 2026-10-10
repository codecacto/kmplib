package br.com.codecacto.kmplib.sync.direct

import br.com.codecacto.kmplib.sync.rest.DomainResult

/** Sistema de arquivos em memória — mesma semântica do `java.io`/`NSFileManager` que a fila usa. */
internal class MemoryDirectUploadFiles : DirectUploadFiles {
    val files = linkedMapOf<String, ByteArray>()
    val dirs = mutableSetOf<String>()
    var failMove = false
    var failWrite = false

    override fun size(path: String): Long = files[path]?.size?.toLong() ?: -1L

    override fun mkdirs(path: String): Boolean {
        var p = path.trimEnd('/')
        while (p.isNotEmpty()) {
            dirs += p
            p = p.substringBeforeLast('/', "")
        }
        return true
    }

    override fun list(dir: String): List<String> {
        val prefixo = dir.trimEnd('/') + "/"
        return (files.keys + dirs).filter { it.startsWith(prefixo) }
            .map { it.removePrefix(prefixo).substringBefore('/') }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    override fun readText(path: String): String? = files[path]?.decodeToString()

    override fun writeTextAtomic(path: String, text: String): Boolean {
        if (failWrite) return false
        mkdirs(path.substringBeforeLast('/'))
        files[path] = text.encodeToByteArray()
        return true
    }

    override fun move(from: String, to: String): Boolean {
        if (failMove) return false
        val bytes = files.remove(from) ?: return false
        files[to] = bytes
        return true
    }

    override fun copy(from: String, to: String): Boolean {
        val bytes = files[from] ?: return false
        files[to] = bytes.copyOf()
        return true
    }

    override fun deleteRecursively(path: String): Boolean {
        val p = path.trimEnd('/')
        files.keys.filter { it == p || it.startsWith("$p/") }.forEach { files.remove(it) }
        dirs.removeAll { it == p || it.startsWith("$p/") }
        return true
    }

    override fun readRange(path: String, offset: Long, length: Long): ByteArray? {
        val b = files[path] ?: return null
        if (offset < 0 || offset + length > b.size) return null
        return b.copyOfRange(offset.toInt(), (offset + length).toInt())
    }

    override fun writeRange(source: String, offset: Long, length: Long, dest: String): Boolean {
        files[dest] = readRange(source, offset, length) ?: return false
        return true
    }
}

/** Backend roteirizado: cada chamada consome a próxima resposta da fila (ou a padrão). */
internal class FakeDirectUploadBackend(
    var partSize: Long = 4,
    var partCount: Int = 3,
    var urlsExpireAt: Long? = null,
    var includeUrlsOnStart: Boolean = true,
) : DirectUploadBackend {
    val calls = mutableListOf<String>()
    val startResults = ArrayDeque<DomainResult<DirectUploadSession>>()
    val presignResults = ArrayDeque<DomainResult<DirectUploadPartUrls>>()
    val completeResults = ArrayDeque<DomainResult<String>>()
    var completedWith: List<DirectUploadCompletedPart>? = null
    var presignedAt = 0
    var startCount = 0

    private fun urls(parts: List<Int>, gen: Int) = parts.associateWith { "https://storage.example.com/obj?part=$it&X-Amz-Signature=g$gen" }

    override suspend fun start(job: DirectUploadJob): DomainResult<DirectUploadSession> {
        calls += "start"
        startCount++
        return startResults.removeFirstOrNull() ?: DomainResult.Success(
            DirectUploadSession(
                uploadId = "up-$startCount",
                remoteId = "video-1",
                partSizeBytes = partSize,
                partCount = partCount,
                partUrls = if (includeUrlsOnStart) urls((1..partCount).toList(), 0) else emptyMap(),
                urlsExpireAtMillis = urlsExpireAt,
            ),
        )
    }

    override suspend fun presignParts(job: DirectUploadJob, session: DirectUploadSession, partNumbers: List<Int>): DomainResult<DirectUploadPartUrls> {
        calls += "presign:${partNumbers.joinToString(",")}"
        presignedAt++
        return presignResults.removeFirstOrNull() ?: DomainResult.Success(DirectUploadPartUrls(urls(partNumbers, presignedAt), null))
    }

    override suspend fun complete(job: DirectUploadJob, session: DirectUploadSession, parts: List<DirectUploadCompletedPart>): DomainResult<String> {
        calls += "complete"
        completedWith = parts
        return completeResults.removeFirstOrNull() ?: DomainResult.Success("""{"id":"req-1","status":"SUBMITTED"}""")
    }

    override suspend fun abort(job: DirectUploadJob, session: DirectUploadSession): DomainResult<Unit> {
        calls += "abort"
        return DomainResult.Success(Unit)
    }
}

/** Transporte roteirizado por parte; o default aceita com ETag `"e<n>"`. */
internal class FakePartTransport(private val files: DirectUploadFiles) : DirectUploadPartTransport {
    val uploaded = mutableListOf<Int>()
    val bodies = mutableMapOf<Int, ByteArray>()
    val script = ArrayDeque<(DirectUploadPartRequest) -> DirectUploadPartResult?>()
    val cancelled = mutableListOf<String>()
    var onUpload: (suspend (DirectUploadPartRequest) -> Unit)? = null

    override suspend fun upload(request: DirectUploadPartRequest): DirectUploadPartResult {
        onUpload?.invoke(request)
        script.removeFirstOrNull()?.invoke(request)?.let { return it }
        val bytes = files.readRange(request.filePath, request.offset, request.length)
            ?: return DirectUploadPartResult.SourceUnreadable
        uploaded += request.partNumber
        bodies[request.partNumber] = bytes
        return DirectUploadPartResult.Uploaded("\"e${request.partNumber}\"")
    }

    override suspend fun cancelJob(jobId: String) {
        cancelled += jobId
    }
}

internal class RecordingScheduler : DirectUploadScheduler {
    val requests = mutableListOf<Pair<DirectUploadNetworkNeed, Boolean>>()
    override fun schedule(outbox: DirectUploadOutbox, need: DirectUploadNetworkNeed, urgent: Boolean) {
        requests += need to urgent
    }
}
