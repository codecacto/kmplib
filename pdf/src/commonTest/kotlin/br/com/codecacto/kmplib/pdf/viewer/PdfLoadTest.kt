package br.com.codecacto.kmplib.pdf.viewer

import br.com.codecacto.kmplib.core.storage.BlobStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfLoadTest {

    private val pdf = "%PDF-1.7\nconteúdo".encodeToByteArray()
    private val url = "https://cdn.exemplo/aulas/apostila.pdf?token=abc"

    /** `BlobStore` em memória — o mesmo contrato, sem disco. */
    private class BlobStoreFake(inicial: Map<String, ByteArray> = emptyMap()) : BlobStore {
        val conteudo = inicial.toMutableMap()
        var escritas = 0
        override suspend fun write(id: String, bytes: ByteArray): Boolean {
            escritas++
            conteudo[id] = bytes
            return true
        }
        override suspend fun read(id: String): ByteArray? = conteudo[id]
        override suspend fun exists(id: String): Boolean = conteudo.containsKey(id)
        override suspend fun sizeOf(id: String): Long = conteudo[id]?.size?.toLong() ?: 0L
        override suspend fun delete(id: String): Boolean = conteudo.remove(id) != null
        override suspend fun ids(): List<String> = conteudo.keys.toList()
    }

    private fun clienteQueResponde(bytes: ByteArray, status: HttpStatusCode = HttpStatusCode.OK) =
        HttpClient(MockEngine { respond(content = bytes, status = status) })

    @Test
    fun `bytes em maos passam direto`() = runTest {
        val cache = BlobStoreFake()
        val resultado = loadPdfBytes(PdfSource.Bytes(pdf, "x"), cache, clienteQueResponde(pdf))
        assertContentEquals(pdf, resultado.getOrNull())
        assertEquals(0, cache.escritas, "bytes já em mãos não vão para o cache")
    }

    @Test
    fun `url baixa uma vez e guarda`() = runTest {
        val cache = BlobStoreFake()
        val baixado = loadPdfBytes(PdfSource.Url(url), cache, clienteQueResponde(pdf))
        assertContentEquals(pdf, baixado.getOrNull())
        assertEquals(1, cache.escritas)
        assertTrue(cache.conteudo.containsKey(pdfCacheIdFor(url)))
    }

    @Test
    fun `a segunda abertura sai do disco, sem rede`() = runTest {
        val cache = BlobStoreFake(mapOf(pdfCacheIdFor(url) to pdf))
        // Um cliente que devolve 500 em qualquer chamada: se o cache não for consultado primeiro,
        // este teste falha — é o que prova "abre no avião".
        val clienteQueSempreFalha = HttpClient(MockEngine { respondError(HttpStatusCode.InternalServerError) })
        val resultado = loadPdfBytes(PdfSource.Url(url), cache, clienteQueSempreFalha)
        assertContentEquals(pdf, resultado.getOrNull())
    }

    @Test
    fun `URL assinada nova continua achando o que ja foi baixado`() = runTest {
        val cache = BlobStoreFake(mapOf(pdfCacheIdFor(url) to pdf))
        val outroToken = "https://cdn.exemplo/aulas/apostila.pdf?token=zzz&expires=999"
        val clienteQueSempreFalha = HttpClient(MockEngine { respondError(HttpStatusCode.InternalServerError) })
        assertContentEquals(pdf, loadPdfBytes(PdfSource.Url(outroToken), cache, clienteQueSempreFalha).getOrNull())
    }

    @Test
    fun `cacheId do app vence a derivacao pela URL`() = runTest {
        val cache = BlobStoreFake(mapOf("material-7" to pdf))
        val clienteQueSempreFalha = HttpClient(MockEngine { respondError(HttpStatusCode.InternalServerError) })
        val fonte = PdfSource.Url(url, cacheId = "material-7")
        assertContentEquals(pdf, loadPdfBytes(fonte, cache, clienteQueSempreFalha).getOrNull())
    }

    @Test
    fun `404 e documento inexistente, nao falha de rede`() = runTest {
        val cache = BlobStoreFake()
        val cliente = HttpClient(MockEngine { respondError(HttpStatusCode.NotFound) })
        val erro = loadPdfBytes(PdfSource.Url(url), cache, cliente).exceptionOrNull()
        assertEquals(PdfViewerError.NotFound, (erro as PdfLoadException).kind)
    }

    @Test
    fun `5xx e falha de rede`() = runTest {
        val cache = BlobStoreFake()
        val cliente = HttpClient(MockEngine { respondError(HttpStatusCode.BadGateway) })
        val erro = loadPdfBytes(PdfSource.Url(url), cache, cliente).exceptionOrNull()
        assertEquals(PdfViewerError.Network, (erro as PdfLoadException).kind)
    }

    @Test
    fun `HTML devolvido com 200 nao entra no cache`() = runTest {
        // O bug que isto mata: a página de erro do CDN gravada com o nome do arquivo faz o
        // documento não abrir NUNCA mais, mesmo com rede boa — só desinstalando o app.
        val cache = BlobStoreFake()
        val html = "<!DOCTYPE html><html>403</html>".encodeToByteArray()
        val erro = loadPdfBytes(PdfSource.Url(url), cache, clienteQueResponde(html)).exceptionOrNull()
        assertEquals(PdfViewerError.Corrupted, (erro as PdfLoadException).kind)
        assertEquals(0, cache.escritas)
        assertTrue(cache.conteudo.isEmpty())
    }

    @Test
    fun `arquivo local inexistente vira NotFound`() = runTest {
        val cache = BlobStoreFake()
        val erro = loadPdfBytes(PdfSource.LocalFile("/nao/existe/x.pdf"), cache, clienteQueResponde(pdf))
            .exceptionOrNull()
        assertEquals(PdfViewerError.NotFound, (erro as PdfLoadException).kind)
    }
}
