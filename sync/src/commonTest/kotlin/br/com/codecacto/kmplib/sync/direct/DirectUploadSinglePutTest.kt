package br.com.codecacto.kmplib.sync.direct

import br.com.codecacto.kmplib.core.crypto.sha256Hex
import br.com.codecacto.kmplib.sync.rest.DomainApiClient
import br.com.codecacto.kmplib.sync.rest.DomainResult
import br.com.codecacto.kmplib.sync.rest.DomainTokenProvider
import br.com.codecacto.kmplib.sync.rest.UploadRetryPolicy
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PUT único pré-assinado (2.312.0): regras puras, backend REST, transporte Ktor e a fila inteira. */
class DirectUploadSinglePutTest {

    private val conteudo = "ÿØÿfoto-de-ronda".encodeToByteArray()
    private val tmp = "/cache/captura/f1.jpg"
    private val assinados = mapOf(
        "content-type" to "image/jpeg",
        "content-length" to conteudo.size.toString(),
        "x-amz-checksum-sha256" to "AAAA",
    )

    private fun job(size: Long = conteudo.size.toLong(), session: DirectUploadSession? = null) = DirectUploadJob(
        id = "j", accountId = "a", kind = "FOTO", targetId = "m-1", contentType = "image/jpeg",
        sizeBytes = size, fileName = "f", createdAtMillis = 0, session = session, sha256 = "ab".repeat(32),
    )

    // -- Regras puras ------------------------------------------------------------------------------

    @Test
    fun sessaoDePutUnicoEUmaParteDoTamanhoDoArquivo() {
        val s = DirectUploadSession.singlePut("m-1", 18, "https://r2/obj?X-Amz-Signature=x", method = "put", signedHeaders = assinados)
        assertEquals(DirectUploadMode.SINGLE_PUT, s.mode)
        assertEquals(1, s.partCount)
        assertEquals(18L, s.partSizeBytes)
        assertEquals("PUT", s.method)
        assertEquals("m-1", s.remoteId)
        assertEquals(mapOf(1 to "https://r2/obj?X-Amz-Signature=x"), s.partUrls)
        assertFalse("X-Amz-Signature" in s.toString(), "toString sem URL")
        assertFalse("AAAA" in s.toString(), "toString sem valor de cabeçalho assinado")
    }

    @Test
    fun problemaDaSessaoDePutUnico() {
        val n = conteudo.size.toLong()
        assertNull(directUploadSessionProblem(DirectUploadSession.singlePut("m", n, "u", signedHeaders = assinados), n))
        assertNotNull(directUploadSessionProblem(DirectUploadSession.singlePut("m", n + 1, "u"), n), "tamanho diferente")
        assertNotNull(directUploadSessionProblem(DirectUploadSession.singlePut("m", n, null), n), "sem URL")
        assertNull(directUploadSessionProblem(DirectUploadSession.singlePut("m", n, null, alreadyReceived = true), n), "já recebido dispensa URL")
        assertNotNull(
            directUploadSessionProblem(DirectUploadSession.singlePut("m", n, "u", signedHeaders = mapOf("Content-Length" to "3")), n),
            "content-length assinado diferente",
        )
        assertNotNull(directUploadSessionProblem(DirectUploadSession.singlePut("m", n, "u", method = "POST"), n), "método")
        assertNotNull(directUploadSessionProblem(DirectUploadSession.singlePut("m", n, "u").copy(partCount = 2), n))
    }

    @Test
    fun cabecalhosDoTransporteSemContentLengthTipoEHost() {
        val extra = directUploadExtraHeaders(assinados + ("Host" to "r2") + ("x-amz-meta-a" to "b"))
        assertEquals(mapOf("x-amz-checksum-sha256" to "AAAA", "x-amz-meta-a" to "b"), extra)
        assertEquals("image/jpeg", directUploadContentType(mapOf("Content-Type" to " image/jpeg ")))
        assertNull(directUploadContentType(emptyMap()))
    }

    @Test
    fun jaRecebidoNaoTemParteFaltandoEContaComoEnviado() {
        val j = job(session = DirectUploadSession.singlePut("m", conteudo.size.toLong(), null, alreadyReceived = true))
        assertTrue(directUploadMissingParts(j).isEmpty())
        assertEquals(1f, j.progress)
    }

    @Test
    fun putUnicoSemEtagNaoEFalha() {
        assertEquals(DirectUploadPartResult.Uploaded(DIRECT_UPLOAD_NO_ETAG), classifyDirectUploadPartResponse(200, null, requireEtag = false))
        assertEquals(DirectUploadPartResult.Failed(MISSING_ETAG_STATUS), classifyDirectUploadPartResponse(200, null))
        assertEquals(DirectUploadPartResult.Uploaded("\"e\""), classifyDirectUploadPartResponse(200, "\"e\"", requireEtag = false))
        assertEquals(DirectUploadPartResult.Expired, classifyDirectUploadPartResponse(403, null, requireEtag = false))
    }

    @Test
    fun leituraDoBilhete() {
        fun t(s: String) = parseDirectUploadPutTicket(Json.parseToJsonElement(s).jsonObject)
        val ok = assertNotNull(
            t(
                """{"id":"m-1","estado":"PENDENTE","upload":{"method":"put","url":"https://r2/o","headers":{"content-type":"image/jpeg","content-length":18,"x":{"y":1}},"expiraEm":1761094800000},"servidorEm":1}""",
            ),
        )
        assertEquals("m-1", ok.remoteId)
        assertFalse(ok.alreadyReceived)
        val alvo = assertNotNull(ok.target)
        assertEquals("PUT", alvo.method)
        assertEquals(mapOf("content-type" to "image/jpeg", "content-length" to "18"), alvo.headers)
        assertEquals(1761094800000L, alvo.expiresAtMillis)

        val semBilhete = assertNotNull(t("""{"id":"m-1","estado":"PENDENTE","upload":null}"""))
        assertNull(semBilhete.target)
        assertFalse(semBilhete.alreadyReceived)

        val recebido = assertNotNull(t("""{"id":"m-1","estado":"CONFERIDA","upload":null}"""))
        assertTrue(recebido.alreadyReceived)

        assertNull(t("""{"id":"m-1","upload":{"method":"PUT"}}"""), "bilhete sem URL e sem estado de recebido")
        assertNull(t("""{"qualquer":1}"""))
        assertEquals(
            1760097600000L,
            t("""{"status":"PENDING","upload":{"url":"u","expiresAt":"2025-10-10T12:00:00Z"}}""")?.target?.expiresAtMillis,
        )
        assertNull(t("""{"status":"PENDING","upload":{"url":"u","expiraEm":0}}""")?.target?.expiresAtMillis, "0 = não informado")
    }

    // -- Backend REST -----------------------------------------------------------------------------

    private class Servidor(var declaracao: String, var concluir: Pair<HttpStatusCode, String> = HttpStatusCode.OK to """{"id":"m-1","estado":"CONFERIDA"}""") {
        val chamadas = mutableListOf<Pair<String, String>>()
        val engine = MockEngine { req ->
            chamadas += req.url.encodedPath to ((req.body as? TextContent)?.text ?: "")
            if (req.url.encodedPath.endsWith("/concluir")) {
                respond(concluir.second, concluir.first, headersOf("Content-Type", "application/json"))
            } else {
                respond(declaracao, HttpStatusCode.Created, headersOf("Content-Type", "application/json"))
            }
        }
        val api = DomainApiClient(HttpClient(engine), DomainTokenProvider { "tok" }, "https://api.example.com")
        fun backend() = RestSinglePutUploadBackend(
            api = api,
            declarePath = { "/rondaki/midias" },
            completePath = { _, id -> "/rondaki/midias/$id/concluir" },
        )
    }

    private val bilhete =
        """{"id":"m-1","estado":"PENDENTE","upload":{"method":"PUT","url":"https://r2.example.com/incoming/k?X-Amz-Signature=s","headers":{"content-type":"image/jpeg","content-length":"19"},"expiraEm":4000000000000}}"""

    @Test
    fun backendDeclaraEAbreSessaoDePutUnico() = runTest {
        val srv = Servidor(bilhete)
        val s = assertIs<DomainResult.Success<DirectUploadSession>>(srv.backend().start(job())).data
        assertEquals(DirectUploadMode.SINGLE_PUT, s.mode)
        assertEquals("m-1", s.remoteId)
        assertEquals("https://r2.example.com/incoming/k?X-Amz-Signature=s", s.partUrls[1])
        assertEquals("image/jpeg", s.signedHeaders["content-type"])
        assertEquals(4000000000000L, s.urlsExpireAtMillis)
        val (caminho, corpo) = srv.chamadas.single()
        assertEquals("/rondaki/midias", caminho)
        val enviado = Json.parseToJsonElement(corpo).jsonObject
        assertEquals("\"m-1\"", enviado["id"].toString())
        assertEquals(conteudo.size.toString(), enviado["sizeBytes"].toString())
        assertEquals("\"${"ab".repeat(32)}\"", enviado["sha256"].toString())
    }

    @Test
    fun backendSemBilheteVira503RetentavelEJaRecebidoDispensaEnvio() = runTest {
        val srv = Servidor("""{"id":"m-1","estado":"PENDENTE","upload":null}""")
        val e = assertIs<DomainResult.Error>(srv.backend().start(job()))
        assertEquals(503, e.code)
        assertEquals(RestSinglePutUploadBackend.STORAGE_UNAVAILABLE_CODE, e.serverCode)

        srv.declaracao = """{"id":"m-1","estado":"CONFERIDA","upload":null}"""
        val s = assertIs<DomainResult.Success<DirectUploadSession>>(srv.backend().start(job())).data
        assertTrue(s.alreadyReceived)

        val urls = assertIs<DomainResult.Success<DirectUploadPartUrls>>(srv.backend().presignParts(job(), s, listOf(1))).data
        assertTrue(urls.alreadyReceived)
    }

    @Test
    fun backendReassinaDeclarandoDeNovo() = runTest {
        val srv = Servidor(bilhete)
        val s = DirectUploadSession.singlePut("m-1", 18, null)
        val urls = assertIs<DomainResult.Success<DirectUploadPartUrls>>(srv.backend().presignParts(job(), s, listOf(1))).data
        assertEquals(setOf(1), urls.urls.keys)
        assertEquals("image/jpeg", urls.signedHeaders?.get("content-type"))
        assertEquals(listOf("/rondaki/midias"), srv.chamadas.map { it.first })
    }

    @Test
    fun backendConcluirNaoEnviadaViraSentinelaDeReenvio() = runTest {
        val srv = Servidor(bilhete, HttpStatusCode.Conflict to """{"code":"MIDIA_NAO_ENVIADA","message":"ainda não"}""")
        val s = DirectUploadSession.singlePut("m-9", 18, "u")
        val e = assertIs<DomainResult.Error>(srv.backend().complete(job(), s, emptyList()))
        assertEquals(DirectUploadOutbox.NOT_RECEIVED_CODE, e.code)
        assertEquals("/rondaki/midias/m-9/concluir", srv.chamadas.single().first)

        srv.concluir = HttpStatusCode.Conflict to """{"code":"MIDIA_ID_EM_USO"}"""
        assertEquals(409, assertIs<DomainResult.Error>(srv.backend().complete(job(), s, emptyList())).code)
        assertIs<DomainResult.Success<Unit>>(srv.backend().abort(job(), s))
    }

    // -- Transporte Ktor (streaming) ---------------------------------------------------------------

    @Test
    fun transporteMandaOArquivoEmBlocosComOsCabecalhosAssinadosESemBearer() = runTest {
        val files = MemoryDirectUploadFiles()
        val grande = ByteArray(DIRECT_UPLOAD_STREAM_CHUNK_BYTES * 2 + 123) { (it % 251).toByte() }
        files.files["/q/j/media"] = grande
        var recebido: ByteArray? = null
        var metodo: HttpMethod? = null
        val cabecalhos = mutableMapOf<String, String?>()
        val engine = MockEngine { req ->
            metodo = req.method
            recebido = req.body.toByteArray()
            cabecalhos["tipo"] = req.body.contentType?.toString()
            cabecalhos["tamanho"] = req.body.contentLength?.toString()
            cabecalhos["checksum"] = req.headers["x-amz-checksum-sha256"]
            cabecalhos["auth"] = req.headers[HttpHeaders.Authorization]
            respond("", HttpStatusCode.OK)
        }
        val transporte = KtorDirectUploadPartTransport(HttpClient(engine), files)
        val r = transporte.upload(
            DirectUploadPartRequest(
                jobId = "j", partNumber = 1, url = "https://r2.example.com/o?X-Amz-Signature=s", filePath = "/q/j/media",
                offset = 0, length = grande.size.toLong(), partFilePath = "/q/j/part-1", wifiOnly = false,
                method = "PUT", contentType = "image/jpeg", headers = mapOf("x-amz-checksum-sha256" to "AAAA"), requireEtag = false,
            ),
        )
        assertEquals(DirectUploadPartResult.Uploaded(DIRECT_UPLOAD_NO_ETAG), r)
        assertEquals(HttpMethod.Put, metodo)
        assertContentEquals(grande, recebido)
        assertEquals("image/jpeg", cabecalhos["tipo"])
        assertEquals(grande.size.toString(), cabecalhos["tamanho"])
        assertEquals("AAAA", cabecalhos["checksum"])
        assertNull(cabecalhos["auth"])
    }

    @Test
    fun transporteDeParteMultipartContinuaExigindoEtagEMandaOTrecho() = runTest {
        val files = MemoryDirectUploadFiles()
        files.files["/q/j/media"] = "0123456789".encodeToByteArray()
        var recebido: ByteArray? = null
        val engine = MockEngine { req ->
            recebido = req.body.toByteArray()
            respond("", HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "\"e2\""))
        }
        val t = KtorDirectUploadPartTransport(HttpClient(engine), files)
        val pedido = DirectUploadPartRequest("j", 2, "https://r2/o", "/q/j/media", 4, 4, "/q/j/part-2", false)
        assertEquals(DirectUploadPartResult.Uploaded("\"e2\""), t.upload(pedido))
        assertContentEquals("4567".encodeToByteArray(), recebido)

        val semEtag = KtorDirectUploadPartTransport(HttpClient(MockEngine { respond("", HttpStatusCode.OK) }), files)
        assertEquals(DirectUploadPartResult.Failed(MISSING_ETAG_STATUS), semEtag.upload(pedido))
        assertEquals(
            DirectUploadPartResult.SourceUnreadable,
            semEtag.upload(DirectUploadPartRequest("j", 9, "https://r2/o", "/q/j/sumiu", 0, 4, "/q/j/part-9", false)),
        )
    }

    // -- A fila inteira ---------------------------------------------------------------------------

    /** Backend de PUT único roteirizado. */
    private class FakeSinglePutBackend(var size: Long) : DirectUploadBackend {
        val calls = mutableListOf<String>()
        val starts = ArrayDeque<DomainResult<DirectUploadSession>>()
        val presigns = ArrayDeque<DomainResult<DirectUploadPartUrls>>()
        val completes = ArrayDeque<DomainResult<String>>()
        var declarado: DirectUploadJob? = null

        override suspend fun start(job: DirectUploadJob): DomainResult<DirectUploadSession> {
            calls += "start"
            declarado = job
            return starts.removeFirstOrNull() ?: DomainResult.Success(
                DirectUploadSession.singlePut(
                    remoteId = job.targetId, sizeBytes = size, url = "https://r2/o?sig=1",
                    signedHeaders = mapOf("content-type" to "image/jpeg", "content-length" to size.toString(), "x-amz-checksum-sha256" to "H1"),
                ),
            )
        }

        override suspend fun presignParts(job: DirectUploadJob, session: DirectUploadSession, partNumbers: List<Int>): DomainResult<DirectUploadPartUrls> {
            calls += "declarar-de-novo"
            return presigns.removeFirstOrNull() ?: DomainResult.Success(
                DirectUploadPartUrls(mapOf(1 to "https://r2/o?sig=2"), null, signedHeaders = mapOf("content-type" to "image/jpeg", "x-amz-checksum-sha256" to "H2")),
            )
        }

        override suspend fun complete(job: DirectUploadJob, session: DirectUploadSession, parts: List<DirectUploadCompletedPart>): DomainResult<String> {
            calls += "concluir"
            return completes.removeFirstOrNull() ?: DomainResult.Success("""{"id":"${job.targetId}","estado":"CONFERIDA"}""")
        }

        override suspend fun abort(job: DirectUploadJob, session: DirectUploadSession): DomainResult<Unit> {
            calls += "abort"
            return DomainResult.Success(Unit)
        }
    }

    private class Cena(conteudo: ByteArray) {
        val files = MemoryDirectUploadFiles()
        val backend = FakeSinglePutBackend(conteudo.size.toLong())
        val transport = FakePartTransport(files)
        var agora = 1_000_000L
        val completos = mutableListOf<String>()
        fun outbox() = DirectUploadOutbox(
            name = "midias",
            backend = backend,
            accountId = { "vigia-1" },
            storage = FileDirectUploadStorage("/data/q", files),
            transport = transport,
            scheduler = RecordingScheduler(),
            retry = UploadRetryPolicy(baseDelayMillis = 1_000, maxDelayMillis = 8_000, maxAttempts = 4),
            isMetered = { false },
            nowMillis = { agora },
            idFactory = { "job-1" },
            onCompleted = { _, corpo -> completos += corpo },
        )
    }

    private suspend fun DirectUploadOutbox.enfileirarFoto(c: Cena): DirectUploadJob {
        c.files.files[tmp] = conteudo.copyOf()
        return assertIs<DirectUploadEnqueueResult.Queued>(
            enqueue(DirectUploadRequest(sourcePath = tmp, kind = "FOTO", targetId = "m-1", contentType = "image/jpeg", computeSha256 = true)),
        ).job
    }

    @Test
    fun filaCalculaOSha256DoArquivoNaFilaEOMandaNaDeclaracao() = runTest {
        val c = Cena(conteudo)
        val fila = c.outbox()
        val job = fila.enfileirarFoto(c)
        assertEquals(sha256Hex(conteudo), job.sha256)
        fila.drainNow()
        assertEquals(sha256Hex(conteudo), c.backend.declarado?.sha256)
    }

    @Test
    fun filaFazUmPutUnicoComOsCabecalhosAssinadosEConclui() = runTest {
        val c = Cena(conteudo)
        val pedidos = mutableListOf<DirectUploadPartRequest>()
        c.transport.onUpload = { pedidos += it }
        val fila = c.outbox()
        fila.enfileirarFoto(c)
        val resumo = fila.drainNow()

        assertEquals(1, resumo.completed)
        assertEquals(listOf("start", "concluir"), c.backend.calls)
        val p = pedidos.single()
        assertEquals(1, p.partNumber)
        assertEquals(0L, p.offset)
        assertEquals(conteudo.size.toLong(), p.length)
        assertEquals("PUT", p.method)
        assertEquals("image/jpeg", p.contentType)
        assertEquals(mapOf("x-amz-checksum-sha256" to "H1"), p.headers)
        assertFalse(p.requireEtag)
        assertContentEquals(conteudo, c.transport.bodies[1])
        assertEquals(listOf("""{"id":"m-1","estado":"CONFERIDA"}"""), c.completos)
        assertTrue(fila.jobs.value.isEmpty())
        assertTrue(c.files.files.keys.none { it.startsWith("/data/q/job-1/") }, "pasta do envio apagada")
    }

    @Test
    fun bilheteVencidoDeclaraDeNovoComOsCabecalhosNovos() = runTest {
        val c = Cena(conteudo)
        val pedidos = mutableListOf<DirectUploadPartRequest>()
        c.transport.onUpload = { pedidos += it }
        c.transport.script.addLast { DirectUploadPartResult.Expired }
        val fila = c.outbox()
        fila.enfileirarFoto(c)
        assertEquals(1, fila.drainNow().completed)
        assertEquals(listOf("start", "declarar-de-novo", "concluir"), c.backend.calls)
        assertEquals("https://r2/o?sig=2", pedidos.last().url)
        assertEquals(mapOf("x-amz-checksum-sha256" to "H2"), pedidos.last().headers)
    }

    @Test
    fun jaRecebidoNoServidorPulaOPutEVaiDiretoAoConcluir() = runTest {
        val c = Cena(conteudo)
        c.backend.starts.addLast(DomainResult.Success(DirectUploadSession.singlePut("m-1", conteudo.size.toLong(), null, alreadyReceived = true)))
        val fila = c.outbox()
        fila.enfileirarFoto(c)
        assertEquals(1, fila.drainNow().completed)
        assertTrue(c.transport.uploaded.isEmpty(), "nada subiu")
        assertEquals(listOf("start", "concluir"), c.backend.calls)
    }

    @Test
    fun storageIndisponivelNaDeclaracaoEsperaERepete() = runTest {
        val c = Cena(conteudo)
        c.backend.starts.addLast(DomainResult.Error(503, "x", serverCode = RestSinglePutUploadBackend.STORAGE_UNAVAILABLE_CODE))
        val fila = c.outbox()
        fila.enfileirarFoto(c)
        val r1 = fila.drainNow()
        assertEquals(1, r1.deferred)
        assertEquals(DirectUploadStatus.QUEUED, fila.jobs.value.single().status)
        c.agora += 60_000
        assertEquals(1, fila.drainNow().completed)
    }

    @Test
    fun servidorQueNaoRecebeuOArquivoFazAFilaReenviar() = runTest {
        val c = Cena(conteudo)
        c.backend.completes.addLast(DomainResult.Error(DirectUploadOutbox.NOT_RECEIVED_CODE, "x", serverCode = "MIDIA_NAO_ENVIADA"))
        val fila = c.outbox()
        fila.enfileirarFoto(c)
        val r1 = fila.drainNow()
        assertEquals(1, r1.deferred)
        val pendente = fila.jobs.value.single()
        assertTrue(pendente.completedParts.isEmpty(), "partes esquecidas")
        assertEquals(DirectUploadOutbox.NOT_RECEIVED_CODE, pendente.failure?.code)
        c.agora += 60_000
        assertEquals(1, fila.drainNow().completed)
        assertEquals(listOf(1, 1), c.transport.uploaded, "o PUT foi repetido")
    }

    @Test
    fun bilheteComTamanhoAssinadoErradoParaSemSubir() = runTest {
        val c = Cena(conteudo)
        c.backend.starts.addLast(
            DomainResult.Success(DirectUploadSession.singlePut("m-1", conteudo.size.toLong(), "u", signedHeaders = mapOf("content-length" to "1"))),
        )
        val fila = c.outbox()
        fila.enfileirarFoto(c)
        assertEquals(1, fila.drainNow().failed)
        assertTrue(c.transport.uploaded.isEmpty())
        assertEquals(DirectUploadOutbox.INVALID_SESSION_CODE, fila.jobs.value.single().failure?.code)
    }

    @Test
    fun estadoComPutUnicoSobreviveAoProcessoMorto() = runTest {
        val c = Cena(conteudo)
        c.transport.script.addLast { DirectUploadPartResult.Offline }
        val fila = c.outbox()
        fila.enfileirarFoto(c)
        assertTrue(fila.drainNow().paused)
        // "Processo novo": outra fila sobre o mesmo disco.
        val outra = c.outbox()
        outra.resume()
        val lido = outra.jobs.value.single()
        assertEquals(DirectUploadMode.SINGLE_PUT, lido.session?.mode)
        assertEquals("H1", lido.session?.signedHeaders?.get("x-amz-checksum-sha256"))
        assertEquals(sha256Hex(conteudo), lido.sha256)
        assertEquals(1, outra.drainNow().completed)
    }

    @Test
    fun estadoGravadoAntesDa2311ContinuaLegivelComoMultipart() {
        val antigo = """{"id":"j","accountId":"a","kind":"k","targetId":"t","contentType":"video/mp4","sizeBytes":12,
            "fileName":"f","createdAtMillis":0,"status":"UPLOADING",
            "session":{"uploadId":"u","partSizeBytes":4,"partCount":3,"partUrls":{"1":"x"}}}"""
        val j = DirectUploadJson.decodeFromString(DirectUploadJob.serializer(), antigo)
        assertEquals(DirectUploadMode.MULTIPART, j.session?.mode)
        assertNull(j.sha256)
        assertTrue(j.session?.signedHeaders?.isEmpty() == true)
    }
}
