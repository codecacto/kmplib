package br.com.codecacto.kmplib.ui.components

import br.com.codecacto.kmplib.sync.rest.DomainApiClient
import br.com.codecacto.kmplib.sync.rest.DomainTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoSourceTest {

    @Test
    fun `item so com URL continua como antes`() {
        val subindo = PhotoStripItem(id = "a")
        assertTrue(subindo.uploading)
        assertFalse(subindo.ready)
        assertNull(subindo.imageSource)

        val pronta = PhotoStripItem(id = "a", url = "https://x/a.jpg")
        assertFalse(pronta.uploading)
        assertTrue(pronta.ready)
        assertEquals(PhotoSource.Url("https://x/a.jpg"), pronta.imageSource)

        val falhou = PhotoStripItem(id = "a", failed = true)
        assertFalse(falhou.uploading)
        assertFalse(falhou.ready)
    }

    @Test
    fun `fonte autenticada deixa o item pronto e vence a URL`() {
        val fonte = PhotoSource.Loader("/v1/fotos/1/bytes") { byteArrayOf(1) }
        val item = PhotoStripItem(id = "1", url = "https://publica", source = fonte)
        assertEquals(fonte, item.imageSource)
        assertTrue(item.ready)
    }

    @Test
    fun `previa local pode aparecer enquanto sobe`() {
        val item = PhotoStripItem(
            id = "1",
            source = PhotoSource.Bytes("1", byteArrayOf(9)),
            uploadingOverride = true,
        )
        assertTrue(item.uploading)
        assertFalse(item.ready, "foto subindo não vira capa")
    }

    @Test
    fun `igualdade de Loader e Bytes e pela chave`() {
        assertEquals(PhotoSource.Loader("k") { null }, PhotoSource.Loader("k") { byteArrayOf(1) })
        assertEquals(PhotoSource.Bytes("k", byteArrayOf(1)), PhotoSource.Bytes("k", byteArrayOf(2)))
        assertFalse(PhotoSource.Loader("k") { null } == PhotoSource.Loader("j") { null })
    }

    @Test
    fun `authenticated busca os bytes com o Bearer e devolve null em erro`() = runTest {
        val auths = mutableListOf<String?>()
        var status = HttpStatusCode.OK
        val engine = MockEngine { request ->
            auths += request.headers["Authorization"]
            respond(byteArrayOf(7, 8, 9), status)
        }
        val api = DomainApiClient(HttpClient(engine), DomainTokenProvider { "tok" }, "https://api.example.com")
        val fonte = PhotoSource.authenticated(api, "/v1/fotos/1/bytes")

        assertEquals("/v1/fotos/1/bytes", fonte.key)
        assertContentEquals(byteArrayOf(7, 8, 9), fonte.load())
        assertEquals("Bearer tok", auths.first())

        status = HttpStatusCode.NotFound
        assertNull(fonte.load(), "falha vira null, e a miniatura mostra a marca de falha")
    }
}
