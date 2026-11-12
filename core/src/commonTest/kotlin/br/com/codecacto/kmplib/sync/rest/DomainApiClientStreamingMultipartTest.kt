package br.com.codecacto.kmplib.sync.rest

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import kotlinx.io.write
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Multipart em fluxo (2.356.0): partes lidas em pedaços, reabertas no 401, sem `ByteArray` do arquivo. */
class DomainApiClientStreamingMultipartTest {

    private class Rec {
        val corpos = mutableListOf<ByteArray>()
        val metodos = mutableListOf<HttpMethod>()
        val auths = mutableListOf<String?>()
        var contentLength: Long? = null
        var contentType: String? = null
    }

    private suspend fun drenar(content: OutgoingContent): ByteArray = when (content) {
        is OutgoingContent.WriteChannelContent -> coroutineScope {
            val canal = ByteChannel(autoFlush = true)
            launch { content.writeTo(canal); canal.flushAndClose() }
            canal.readRemaining().readByteArray()
        }
        is OutgoingContent.ByteArrayContent -> content.bytes()
        else -> error("corpo inesperado: ${content::class}")
    }

    private fun cliente(rec: Rec, primeiro401: Boolean = false): DomainApiClient {
        var tentativa = 0
        val engine = MockEngine { request ->
            tentativa++
            rec.metodos += request.method
            rec.auths += request.headers[HttpHeaders.Authorization]
            rec.contentLength = request.body.contentLength
            rec.contentType = request.body.contentType?.toString()
            rec.corpos += drenar(request.body)
            val status = if (primeiro401 && tentativa == 1) HttpStatusCode.Unauthorized else HttpStatusCode.Created
            respond("""{"id":"m1"}""", status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        var renovado = false
        return DomainApiClient(
            HttpClient(engine),
            DomainTokenProvider { force -> if (force) { renovado = true; "fresh" } else if (renovado) "fresh" else "tok" },
            "https://api.example.com",
        )
    }

    private fun conteudo(n: Int) = Random(42).nextBytes(n)

    private fun parteEmMemoria(bytes: ByteArray, aberturas: IntArray? = null, size: Long? = bytes.size.toLong()) =
        StreamingMultipartPart("arquivo", "video.mp4", "video/mp4", size) {
            aberturas?.let { it[0]++ }
            Buffer().apply { write(bytes) }
        }

    private fun ByteArray.contem(trecho: ByteArray): Boolean {
        if (trecho.isEmpty()) return true
        outer@ for (i in 0..(size - trecho.size)) {
            for (j in trecho.indices) if (this[i + j] != trecho[j]) continue@outer
            return true
        }
        return false
    }

    @Test
    fun `parte em fluxo chega inteira no corpo multipart com os campos antes`() = runTest {
        val rec = Rec()
        val bytes = conteudo(300_000)
        val r = cliente(rec).postMultipartStreaming(
            "/v1/midias?finalidade=PUBLICACAO",
            listOf(parteEmMemoria(bytes)),
            formFields = mapOf("finalidade" to "PUBLICACAO"),
        )
        assertIs<DomainResult.Success<String>>(r)
        val corpo = rec.corpos.single()
        assertTrue(rec.contentType!!.startsWith("multipart/form-data"))
        assertTrue(corpo.contem(bytes), "o conteúdo do arquivo não chegou inteiro")
        val texto = corpo.decodeToString()
        assertTrue(texto.indexOf("finalidade") < texto.indexOf("filename=\"video.mp4\""), "campo de texto depois do arquivo")
        assertTrue(texto.contains("Content-Type: video/mp4"))
    }

    @Test
    fun `tamanho conhecido vira Content-Length exato do corpo`() = runTest {
        val rec = Rec()
        cliente(rec).postMultipartStreaming("/v1/midias", listOf(parteEmMemoria(conteudo(10_000))))
        assertEquals(rec.corpos.single().size.toLong(), rec.contentLength)
    }

    @Test
    fun `tamanho desconhecido sobe sem Content-Length`() = runTest {
        val rec = Rec()
        val bytes = conteudo(5_000)
        cliente(rec).postMultipartStreaming("/v1/midias", listOf(parteEmMemoria(bytes, size = null)))
        assertNull(rec.contentLength)
        assertTrue(rec.corpos.single().contem(bytes))
    }

    @Test
    fun `401 renova o token e reabre o arquivo do inicio`() = runTest {
        val rec = Rec()
        val aberturas = intArrayOf(0)
        val bytes = conteudo(50_000)
        val r = cliente(rec, primeiro401 = true)
            .postMultipartStreaming("/v1/midias", listOf(parteEmMemoria(bytes, aberturas)))
        assertIs<DomainResult.Success<String>>(r)
        assertEquals(2, aberturas[0])
        assertEquals(listOf<String?>("Bearer tok", "Bearer fresh"), rec.auths)
        assertTrue(rec.corpos[1].contem(bytes), "a repetição não mandou o arquivo inteiro")
    }

    @Test
    fun `mistura parte em fluxo e parte em bytes no mesmo request e PUT usa o verbo PUT`() = runTest {
        val rec = Rec()
        val video = conteudo(20_000)
        val capa = byteArrayOf(-1, -40, -1, 1, 2, 3)
        val r = cliente(rec).putMultipartStreaming(
            "/v1/midias/1",
            listOf(parteEmMemoria(video), MultipartPart("capa", "capa.jpg", capa, "image/jpeg")),
        )
        assertIs<DomainResult.Success<String>>(r)
        assertEquals(HttpMethod.Put, rec.metodos.single())
        assertTrue(rec.corpos.single().contem(video))
        assertTrue(rec.corpos.single().contem(capa))
    }

    @Test
    fun `falha ao abrir o arquivo vira erro de transporte e nao excecao`() = runTest {
        val rec = Rec()
        val quebrada = StreamingMultipartPart("arquivo", "a.mp4", "video/mp4", null) {
            throw kotlinx.io.IOException("sumiu")
        }
        val r = cliente(rec).postMultipartStreaming("/v1/midias", listOf(quebrada))
        assertIs<DomainResult.Error>(r)
        assertEquals(-1, r.code)
    }

    @Test
    fun `fromLocalFile le o arquivo do disco e descobre o tamanho`() = runTest {
        val bytes = conteudo(70_000)
        val caminho = Path(SystemTemporaryDirectory, "kmplib-stream-${Random.nextLong()}.bin")
        SystemFileSystem.sink(caminho).buffered().use { it.write(bytes) }
        try {
            val parte = StreamingMultipartPart.fromLocalFile("arquivo", caminho.toString(), "v.mp4", "video/mp4")
            assertEquals(bytes.size.toLong(), parte.sizeBytes)
            val lido = parte.openSource().buffered().use { it.readByteArray() }
            assertTrue(bytes.contentEquals(lido))
            val rec = Rec()
            cliente(rec).postMultipartStreaming("/v1/midias", listOf(parte))
            assertTrue(rec.corpos.single().contem(bytes))
            assertEquals(rec.corpos.single().size.toLong(), rec.contentLength)
        } finally {
            SystemFileSystem.delete(caminho, mustExist = false)
        }
    }

    @Test
    fun `fromLocalFile de arquivo inexistente nao lanca na construcao e falha so no envio`() = runTest {
        val parte = StreamingMultipartPart.fromLocalFile("arquivo", "/nao/existe/${Random.nextLong()}.mp4", "v.mp4", "video/mp4")
        assertNull(parte.sizeBytes)
        val r = cliente(Rec()).postMultipartStreaming("/v1/midias", listOf(parte))
        assertIs<DomainResult.Error>(r)
    }

    @Test
    fun `toString nao expoe o nome do arquivo e tamanho negativo e recusado`() {
        val p = StreamingMultipartPart("arquivo", "cnh-joao.pdf", "application/pdf", 10L) { Buffer() as RawSource }
        assertFalse(p.toString().contains("joao"))
        assertFailsWith<IllegalArgumentException> {
            StreamingMultipartPart("a", "b", "c", -1L) { Buffer() }
        }
    }
}
