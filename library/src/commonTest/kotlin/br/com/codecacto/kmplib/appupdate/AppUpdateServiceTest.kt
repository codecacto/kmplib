package br.com.codecacto.kmplib.appupdate

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppUpdateServiceTest {

    private val jsonHeader = headersOf("Content-Type", "application/json")

    private data class Captured(var url: String? = null, var method: String? = null)

    private fun service(
        captured: Captured = Captured(),
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = """{"action":"none"}""",
        currentVersionCode: Int = 10,
    ): AppUpdateService {
        val engine = MockEngine { request ->
            captured.url = request.url.toString()
            captured.method = request.method.value
            respond(content = body, status = status, headers = jsonHeader)
        }
        return AppUpdateService(
            AppUpdateConfig(
                projectSlug = "super-8",
                httpClient = HttpClient(engine),
                currentVersionCode = currentVersionCode,
                adminApiBaseUrl = "https://admin-api.codecacto.com.br",
                platform = "android",
            )
        )
    }

    @Test
    fun `check faz GET em public app-version com query params`() = runTest {
        val cap = Captured()
        val svc = service(captured = cap)
        svc.check()
        assertEquals("GET", cap.method)
        val url = cap.url!!
        assertTrue(url.contains("/public/app-version"))
        assertTrue(url.contains("project=super-8"))
        assertTrue(url.contains("platform=android"))
        assertTrue(url.contains("versionCode=10"))
    }

    @Test
    fun `action none mapeia para None`() = runTest {
        val status = service(body = """{"action":"none"}""").check()
        assertEquals(AppUpdateStatus.None, status)
    }

    @Test
    fun `action hard mapeia para Hard com storeUrl e message`() = runTest {
        val body = """{"action":"hard","storeUrl":"https://play/x","message":"atualize"}"""
        val status = service(body = body).check()
        assertTrue(status is AppUpdateStatus.Hard)
        status as AppUpdateStatus.Hard
        assertEquals("https://play/x", status.storeUrl)
        assertEquals("atualize", status.message)
    }

    @Test
    fun `action soft mapeia para Soft com latestVersionName`() = runTest {
        val body = """{"action":"soft","storeUrl":"https://play/x","latestVersionName":"1.4.0"}"""
        val status = service(body = body).check()
        assertTrue(status is AppUpdateStatus.Soft)
        status as AppUpdateStatus.Soft
        assertEquals("1.4.0", status.latestVersionName)
        assertEquals("https://play/x", status.storeUrl)
    }

    @Test
    fun `action desconhecida vira None fail-safe`() = runTest {
        val status = service(body = """{"action":"forcar-tudo"}""").check()
        assertEquals(AppUpdateStatus.None, status)
    }

    @Test
    fun `action maiuscula HARD ainda mapeia para Hard`() = runTest {
        val status = service(body = """{"action":"HARD","message":"x"}""").check()
        assertTrue(status is AppUpdateStatus.Hard)
    }

    @Test
    fun `corpo invalido nao lanca e vira None`() = runTest {
        val status = service(body = """nao e json""").check()
        assertEquals(AppUpdateStatus.None, status)
    }

    @Test
    fun `erro de rede 500 vira None best-effort`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
        val svc = AppUpdateService(
            AppUpdateConfig(
                projectSlug = "super-8",
                httpClient = HttpClient(engine),
                currentVersionCode = 10,
                platform = "android",
            )
        )
        assertEquals(AppUpdateStatus.None, svc.check())
    }
}
