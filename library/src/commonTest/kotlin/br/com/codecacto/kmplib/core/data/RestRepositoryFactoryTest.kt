package br.com.codecacto.kmplib.core.data

import br.com.codecacto.kmplib.core.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@Serializable
private data class Doc(val id: String = "", val nome: String = "")

class RestRepositoryFactoryTest {

    @Test
    fun `factory cria repo com serializer reified e atinge a rota`() = runTest {
        var seenPath: String? = null
        val engine = MockEngine { request ->
            seenPath = request.url.encodedPath
            respond(
                content = """{"id":"1","nome":"Doc"}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val config = RestConfig(
            httpClient = HttpClient(engine),
            baseUrl = "https://api.codecacto.com.br",
        )
        val factory = RestRepositoryFactory(config)
        val repo = factory.create<Doc, String>("/app/v1/docs")

        val res = repo.getById("1")
        assertIs<ApiResult.Success<Doc>>(res)
        assertEquals("Doc", res.data.nome)
        assertEquals("/app/v1/docs/1", seenPath)
    }

    @Test
    fun `factory aceita serializer explicito`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"data":[{"id":"1","nome":"A"}],"page":1,"pageSize":20,"total":1,"totalPages":1}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val config = RestConfig(httpClient = HttpClient(engine), baseUrl = "https://api.x")
        val factory = RestRepositoryFactory(config)
        val repo = factory.create<Doc, String>("/app/v1/docs", Doc.serializer())

        val res = repo.list()
        assertIs<ApiResult.Success<*>>(res)
    }
}
