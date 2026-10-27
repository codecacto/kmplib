package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.core.storage.InMemoryBlobStore
import br.com.codecacto.kmplib.sync.FakeSyncStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * `RestUploadOutbox` (2.328.0): "só no Wi-Fi" (lacuna 4 do estudo offline-first) e a ORDEM dos PUT
 * de substituição — o PUT mais novo não passa na frente do mais antigo que está no recuo.
 */
class RestUploadOutboxNetworkTest {

    private class Servidor {
        val chamadas = mutableListOf<String>()
        val respostas = ArrayDeque<HttpStatusCode>()
    }

    private class Relogio(var agora: Long = 1_000L) : () -> Long {
        override fun invoke(): Long = agora
    }

    private fun outbox(s: Servidor, relogio: Relogio = Relogio(), rede: () -> Boolean?) = RestUploadOutbox(
        api = DomainApiClient(
            HttpClient(
                MockEngine { req ->
                    s.chamadas += "${req.method.value} ${req.url.encodedPath}"
                    val st = s.respostas.removeFirstOrNull() ?: HttpStatusCode.Created
                    if (st.value >= 400) respondError(st)
                    else respond("""{"id":"f"}""", st, headersOf("Content-Type", "application/json"))
                },
            ),
            DomainTokenProvider { "tok" },
            "https://api.example.com",
        ),
        store = FakeSyncStore(),
        blobs = InMemoryBlobStore(),
        nowMillis = relogio,
        isMetered = rede,
    )

    private suspend fun RestUploadOutbox.foto(id: String, wifiOnly: Boolean = false, method: UploadMethod = UploadMethod.POST, path: String = "/v1/fotos") =
        assertIs<UploadEnqueueResult.Queued>(
            enqueue(byteArrayOf(1, 2, 3), "$id.jpg", "image/jpeg", path, id = id, method = method, wifiOnly = wifiOnly),
        )

    @Test
    fun `so no Wi-Fi espera em rede tarifada e sobe na nao tarifada`() = runTest {
        val s = Servidor()
        var tarifada: Boolean? = true
        val fila = outbox(s) { tarifada }
        fila.foto("a", wifiOnly = true)
        fila.foto("b")

        val r1 = fila.drainNow()
        assertEquals(listOf("POST /v1/fotos"), s.chamadas, "só a foto sem restrição sobe no 4G")
        assertEquals(1, r1.waitingUnmetered)
        assertFalse(r1.isDrained)
        assertEquals(0, fila.failed().size, "esperar o Wi-Fi não é falha")

        tarifada = false
        val r2 = fila.drainNow()
        assertEquals(2, s.chamadas.size)
        assertTrue(r2.isDrained)
    }

    @Test
    fun `rede desconhecida segura o so no Wi-Fi`() = runTest {
        val s = Servidor()
        val fila = outbox(s) { null }
        fila.foto("a", wifiOnly = true)
        assertEquals(1, fila.drainNow().waitingUnmetered)
        assertTrue(s.chamadas.isEmpty())
    }

    @Test
    fun `setWifiOnly libera um envio que estava esperando`() = runTest {
        val s = Servidor()
        val fila = outbox(s) { true }
        fila.foto("a", wifiOnly = true)
        fila.drainNow()
        assertTrue(fila.setWifiOnly("a", false))
        fila.drainNow()
        assertEquals(1, s.chamadas.size)
        assertFalse(fila.setWifiOnly("inexistente", true))
    }

    @Test
    fun `PUT mais novo no mesmo alvo espera o antigo que esta no recuo`() = runTest {
        val s = Servidor()
        val relogio = Relogio()
        val fila = outbox(s, relogio) { false }
        fila.foto("avatar-velho", method = UploadMethod.PUT, path = "/v1/me/avatar")
        s.respostas += HttpStatusCode.ServiceUnavailable // o antigo falha e entra no recuo
        fila.drainNow()
        relogio.agora += 10
        fila.foto("avatar-novo", method = UploadMethod.PUT, path = "/v1/me/avatar")
        fila.foto("outra", method = UploadMethod.PUT, path = "/v1/me/capa")

        fila.drainNow()
        assertEquals(listOf("PUT /v1/me/avatar", "PUT /v1/me/capa"), s.chamadas, "o novo avatar NÃO passa na frente")

        relogio.agora += 60 * 60_000L // passa o recuo
        fila.drainNow()
        assertEquals(
            listOf("PUT /v1/me/avatar", "PUT /v1/me/capa", "PUT /v1/me/avatar", "PUT /v1/me/avatar"),
            s.chamadas,
            "antigo primeiro, novo depois: o servidor fica com a escolha mais recente",
        )
        assertTrue(fila.pending().isEmpty())
    }
}
