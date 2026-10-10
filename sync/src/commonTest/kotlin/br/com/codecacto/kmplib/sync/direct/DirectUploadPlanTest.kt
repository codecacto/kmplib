package br.com.codecacto.kmplib.sync.direct

import br.com.codecacto.kmplib.sync.rest.DomainApiClient
import br.com.codecacto.kmplib.sync.rest.DomainResult
import br.com.codecacto.kmplib.sync.rest.DomainTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DirectUploadPlanTest {

    private fun job(size: Long = 10, session: DirectUploadSession? = null, parts: Map<Int, String> = emptyMap()) = DirectUploadJob(
        id = "j", accountId = "a", kind = "k", targetId = "t", contentType = "video/mp4",
        sizeBytes = size, fileName = "f", createdAtMillis = 0, session = session, completedParts = parts,
    )

    private fun sessao(partSize: Long = 4, count: Int = 3, urls: Map<Int, String> = emptyMap(), expires: Long? = null) =
        DirectUploadSession("u", partSizeBytes = partSize, partCount = count, partUrls = urls, urlsExpireAtMillis = expires)

    @Test
    fun faixaDeCadaParteComAUltimaMenor() {
        assertEquals(0L until 4L, directUploadPartRange(1, 4, 10))
        assertEquals(4L until 8L, directUploadPartRange(2, 4, 10))
        assertEquals(8L until 10L, directUploadPartRange(3, 4, 10))
        assertNull(directUploadPartRange(4, 4, 10))
        assertNull(directUploadPartRange(0, 4, 10))
        assertNull(directUploadPartRange(1, 0, 10))
    }

    @Test
    fun sessaoTemDeCobrirOArquivoExatamente() {
        assertNull(directUploadSessionProblem(sessao(4, 3), 10))
        assertNull(directUploadSessionProblem(sessao(5, 2), 10))
        assertEquals("partes de menos para o arquivo", directUploadSessionProblem(sessao(4, 2), 10))
        assertEquals("partes demais para o arquivo", directUploadSessionProblem(sessao(5, 3), 10))
        assertEquals("uploadId em branco", directUploadSessionProblem(sessao().copy(uploadId = " "), 10))
        assertEquals("partCount fora de 1..10000", directUploadSessionProblem(sessao(count = 0), 10))
    }

    @Test
    fun faltamEProgressoPelaSomaRealDasPartes() {
        val j = job(10, sessao(), mapOf(1 to "a", 3 to "c"))
        assertEquals(listOf(2), directUploadMissingParts(j))
        assertEquals(6L, j.uploadedBytes) // 4 + 2
        assertEquals(0.6f, j.progress)
        assertEquals(emptyList(), directUploadMissingParts(job()))
        assertEquals(0f, job().progress)
    }

    @Test
    fun urlSoValeComFolgaAntesDoVencimento() {
        val s = sessao(urls = mapOf(1 to "https://x/1"), expires = 1_000_000)
        assertEquals("https://x/1", directUploadUsableUrl(s, 1, nowMillis = 1_000_000 - 3 * 60_000))
        assertNull(directUploadUsableUrl(s, 1, nowMillis = 1_000_000 - 60_000))
        assertNull(directUploadUsableUrl(s, 2, nowMillis = 0))
        assertEquals("https://x/1", directUploadUsableUrl(s.copy(urlsExpireAtMillis = null), 1, nowMillis = Long.MAX_VALUE / 2))
    }

    @Test
    fun completeEmOrdemEDue() {
        val j = job(10, sessao(), mapOf(3 to "c", 1 to "a", 2 to "b"))
        assertEquals(listOf(1, 2, 3), directUploadCompletedParts(j).map { it.partNumber })
        assertTrue(directUploadIsDue(j, "a", 0))
        assertFalse(directUploadIsDue(j, "b", 0))
        assertFalse(directUploadIsDue(j, null, 0))
        assertFalse(directUploadIsDue(j.copy(nextAttemptAtMillis = 10), "a", 5))
        assertFalse(directUploadIsDue(j.copy(status = DirectUploadStatus.FAILED), "a", 0))
    }

    @Test
    fun etagMantemAspasETiraEspaco() {
        assertEquals("\"abc\"", normalizeDirectUploadEtag(" \"abc\" "))
        assertNull(normalizeDirectUploadEtag("   "))
        assertNull(normalizeDirectUploadEtag("\"\""))
        assertNull(normalizeDirectUploadEtag(null))
    }

    @Test
    fun chaveDeParteIdaEVolta() {
        assertEquals("job-1.7", directUploadPartKey("job-1", 7))
        assertEquals("job.1" to 12, parseDirectUploadPartKey("job.1.12"))
        assertNull(parseDirectUploadPartKey("semponto"))
        assertNull(parseDirectUploadPartKey("j.0"))
        assertNull(parseDirectUploadPartKey("j.x"))
    }

    @Test
    fun respostaDoStorageViraDesfecho() {
        assertEquals(DirectUploadPartResult.Uploaded("\"e\""), classifyDirectUploadPartResponse(200, "\"e\""))
        assertEquals(DirectUploadPartResult.Failed(MISSING_ETAG_STATUS), classifyDirectUploadPartResponse(200, null))
        assertEquals(DirectUploadPartResult.Expired, classifyDirectUploadPartResponse(403, null))
        assertEquals(DirectUploadPartResult.Expired, classifyDirectUploadPartResponse(400, null))
        assertEquals(DirectUploadPartResult.UploadGone, classifyDirectUploadPartResponse(404, null))
        assertEquals(DirectUploadPartResult.Failed(503), classifyDirectUploadPartResponse(503, null))
        assertTrue(isRetryableDirectUploadPartStatus(503))
        assertTrue(isRetryableDirectUploadPartStatus(MISSING_ETAG_STATUS))
        assertFalse(isRetryableDirectUploadPartStatus(413))
    }

    @Test
    fun toStringNaoVazaUrlNemMetadado() {
        val s = sessao(urls = mapOf(1 to "https://s.example.com/o?X-Amz-Signature=SEGREDO"))
        assertFalse(s.toString().contains("SEGREDO"))
        val j = job(session = s).copy(metadata = mapOf("nota" to "dor no joelho"))
        assertFalse(j.toString().contains("SEGREDO"))
        assertFalse(j.toString().contains("joelho"))
        val req = DirectUploadPartRequest("j", 1, "https://s.example.com/o?X-Amz-Signature=SEGREDO", "/p", 0, 4, "/p1", false)
        assertFalse(req.toString().contains("SEGREDO"))
        assertTrue(req.toString().contains("s.example.com"))
        assertFalse(DirectUploadRequest("/data/user/0/app/v.mp4", "k", "t", "video/mp4").toString().contains("/data/"))
    }

    @Test
    fun idsValidosEPastaPorFila() {
        assertTrue(isValidDirectUploadId("018f3c2a-7b1e-7c3d-9a4b-000000000000"))
        assertFalse(isValidDirectUploadId("../x"))
        assertFalse(isValidDirectUploadId(".oculto"))
        assertFalse(isValidDirectUploadId(""))
        assertEquals(DEFAULT_DIRECT_UPLOAD_DIRECTORY, directUploadDirectoryFor(DEFAULT_DIRECT_UPLOAD_OUTBOX_NAME))
        assertEquals("kmplib_direct_uploads_resposta", directUploadDirectoryFor("resposta"))
    }

    @Test
    fun lerOVideoUploadDtoDoContrato() {
        val json = Json.parseToJsonElement(
            """{"uploadId":"mp-1","videoId":"v-9","partSizeBytes":8388608,"partCount":2,
               "parts":[{"partNumber":1,"url":"https://r2/1"},{"partNumber":2,"url":"https://r2/2"},{"partNumber":0,"url":"x"}],
               "expiresAt":"2026-10-10T12:00:00Z","extra":true}""",
        ).jsonObject
        val s = parseDirectUploadSession(json)!!
        assertEquals("mp-1", s.uploadId)
        assertEquals("v-9", s.remoteId)
        assertEquals(8_388_608L, s.partSizeBytes)
        assertEquals(mapOf(1 to "https://r2/1", 2 to "https://r2/2"), s.partUrls)
        assertEquals(1_791_633_600_000L, s.urlsExpireAtMillis)
        assertNull(parseDirectUploadSession(Json.parseToJsonElement("""{"uploadId":"x"}""").jsonObject))
        assertNull(parseDirectUploadSession(Json.parseToJsonElement("""{"uploadId":null,"partSizeBytes":1,"partCount":1}""").jsonObject))
        val p = parseDirectUploadPartUrls(Json.parseToJsonElement("""{"parts":[{"partNumber":3,"url":"u3"}],"expiresAt":"lixo"}""").jsonObject)!!
        assertEquals(mapOf(3 to "u3"), p.urls)
        assertNull(p.expiresAtMillis)
        assertNull(parseDirectUploadPartUrls(Json.parseToJsonElement("{}").jsonObject))
        assertEquals("a%2Fb%20c", encodePathSegment("a/b c"))
    }

    @Test
    fun backendRestFalaOContratoComBearer() = runTest {
        val vistos = mutableListOf<Triple<String, String?, String>>()
        val engine = MockEngine { req ->
            val corpo = (req.body as? TextContent)?.text ?: ""
            vistos += Triple(req.url.encodedPath, req.headers["Authorization"], corpo)
            val resposta = when {
                req.url.encodedPath.endsWith("/uploads") ->
                    """{"uploadId":"mp 1","videoId":"v","partSizeBytes":4,"partCount":3,"parts":[],"expiresAt":"2026-10-10T12:00:00Z"}"""
                req.url.encodedPath.endsWith("/parts") -> """{"parts":[{"partNumber":2,"url":"u2"}],"expiresAt":null}"""
                req.url.encodedPath.endsWith("/complete") -> """{"id":"req-1"}"""
                else -> ""
            }
            respond(resposta, if (req.url.encodedPath.endsWith("/abort")) HttpStatusCode.NoContent else HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val api = DomainApiClient(HttpClient(engine), DomainTokenProvider { "tok" }, "https://api.example.com")
        val backend = RestDirectUploadBackend(
            api = api,
            startPath = { "/v1/aluno/video-requests/${it.targetId}/uploads" },
            uploadBasePath = "/v1/aluno/videos/uploads/",
        )
        val j = job(10).copy(targetId = "req-1", durationSeconds = 12, metadata = mapOf("sessionSetId" to "s-1"))

        val s = assertIs<DomainResult.Success<DirectUploadSession>>(backend.start(j)).data
        assertEquals("mp 1", s.uploadId)
        val urls = assertIs<DomainResult.Success<DirectUploadPartUrls>>(backend.presignParts(j, s, listOf(2))).data
        assertEquals(mapOf(2 to "u2"), urls.urls)
        assertEquals("""{"id":"req-1"}""", (backend.complete(j, s, listOf(DirectUploadCompletedPart(1, "\"e\""))) as DomainResult.Success).data)
        assertIs<DomainResult.Success<Unit>>(backend.abort(j, s))

        assertEquals("/v1/aluno/video-requests/req-1/uploads", vistos[0].first)
        assertEquals("Bearer tok", vistos[0].second)
        val inicio = Json.parseToJsonElement(vistos[0].third).jsonObject
        assertEquals("video/mp4", inicio["contentType"].toString().trim('"'))
        assertEquals("10", inicio["sizeBytes"].toString())
        assertEquals("12", inicio["durationSeconds"].toString())
        assertEquals("\"s-1\"", inicio["sessionSetId"].toString())
        assertEquals("/v1/aluno/videos/uploads/mp%201/parts", vistos[1].first)
        assertEquals("""{"partNumbers":[2]}""", vistos[1].third)
        assertEquals("/v1/aluno/videos/uploads/mp%201/complete", vistos[2].first)
        assertEquals("""{"parts":[{"partNumber":1,"etag":"\"e\""}]}""", vistos[2].third)
        assertEquals("/v1/aluno/videos/uploads/mp%201/abort", vistos[3].first)
    }

    @Test
    fun backendComRespostaIlegivelEhTerminal() = runTest {
        val engine = MockEngine { respond("{}", HttpStatusCode.Created, headersOf("Content-Type", "application/json")) }
        val api = DomainApiClient(HttpClient(engine), DomainTokenProvider { "tok" }, "https://api.example.com")
        val backend = RestDirectUploadBackend(api, startPath = { "/x" }, uploadBasePath = "/y")
        val r = assertIs<DomainResult.Error>(backend.start(job()))
        assertEquals(RestDirectUploadBackend.INVALID_RESPONSE_CODE, r.code)
    }
}
