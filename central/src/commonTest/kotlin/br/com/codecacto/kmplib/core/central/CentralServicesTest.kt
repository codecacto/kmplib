package br.com.codecacto.kmplib.core.central

import br.com.codecacto.kmplib.contact.ContactService
import br.com.codecacto.kmplib.developer.DeveloperInfoService
import br.com.codecacto.kmplib.feedback.FeedbackService
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CentralServicesTest {

    private fun client() = HttpClient(MockEngine { respond("{}", HttpStatusCode.OK) })

    @BeforeTest
    fun reset() {
        CentralServices.resetForTesting()
    }

    @AfterTest
    fun cleanup() {
        CentralServices.resetForTesting()
    }

    @Test
    fun initialize_wiresAllThreeCentralServices() {
        CentralServices.initialize(
            CentralServicesConfig(
                projectSlug = "numeros-da-sorte",
                httpClient = client(),
                appVersion = "1.2.3",
                appsApiBaseUrl = "https://apps-api.example",
            ),
        )

        assertTrue(CentralServices.isInitialized)

        val fb = FeedbackService.config
        assertNotNull(fb)
        assertEquals("numeros-da-sorte", fb.projectSlug)
        assertEquals("1.2.3", fb.appVersion)
        assertEquals("https://apps-api.example", fb.appsApiBaseUrl)

        val ct = ContactService.config
        assertNotNull(ct)
        assertEquals("numeros-da-sorte", ct.projectSlug)
        // contactSource default derivado do slug.
        assertEquals("numeros-da-sorte-app", ct.source)

        assertNotNull(DeveloperInfoService.config)
    }

    @Test
    fun initialize_isIdempotent() {
        CentralServices.initialize(
            CentralServicesConfig(projectSlug = "a", httpClient = client()),
        )
        // Segunda chamada com outro slug deve ser no-op (mantém a primeira config).
        CentralServices.initialize(
            CentralServicesConfig(projectSlug = "b", httpClient = client()),
        )
        assertEquals("a", FeedbackService.config?.projectSlug)
    }

    @Test
    fun initialize_respectsCustomContactSource() {
        CentralServices.initialize(
            CentralServicesConfig(
                projectSlug = "x",
                httpClient = client(),
                contactSource = "x-site",
            ),
        )
        assertEquals("x-site", ContactService.config?.source)
    }

    @Test
    fun initialize_canDisableAServiceGate() {
        // Gate desliga developer; feedback/contato continuam configurados e nada lança.
        CentralServices.initialize(
            CentralServicesConfig(
                projectSlug = "y",
                httpClient = client(),
                enableDeveloper = false,
            ),
        )
        assertNotNull(FeedbackService.config)
        assertNotNull(ContactService.config)
        assertTrue(CentralServices.isInitialized)
    }
}
