package br.com.codecacto.kmplib.core.central

import br.com.codecacto.kmplib.announcement.AnnouncementService
import br.com.codecacto.kmplib.contact.ContactService
import br.com.codecacto.kmplib.developer.DeveloperInfoService
import br.com.codecacto.kmplib.feedback.FeedbackService
import br.com.codecacto.kmplib.presence.AppPresence
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CentralServicesTest {

    private fun client() = HttpClient(MockEngine { respond("{}", HttpStatusCode.OK) })

    @BeforeTest
    fun reset() {
        CentralServices.resetForTesting()
        AnnouncementService.resetForTesting()
        AppPresence.resetForTesting()
        br.com.codecacto.kmplib.modalevents.ModalEvents.resetForTesting()
        br.com.codecacto.kmplib.feedback.AppReviewService.resetForTesting()
        CentralServices.listenToForegroundForPresence = false
    }

    @AfterTest
    fun cleanup() {
        CentralServices.resetForTesting()
        AnnouncementService.resetForTesting()
        AppPresence.resetForTesting()
        br.com.codecacto.kmplib.modalevents.ModalEvents.resetForTesting()
        br.com.codecacto.kmplib.feedback.AppReviewService.resetForTesting()
        CentralServices.listenToForegroundForPresence = true
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

        val av = AnnouncementService.client?.config
        assertNotNull(av)
        assertEquals("numeros-da-sorte", av.projectSlug)
        assertEquals("https://apps-api.example", av.appsApiBaseUrl)
    }

    @Test
    fun initialize_ligaEstatisticaDosModaisERegraDaAvaliacao() {
        CentralServices.initialize(
            CentralServicesConfig(
                projectSlug = "locaki",
                httpClient = client(),
                appVersion = "1.4.0",
                appsApiBaseUrl = "https://apps-api.example",
            ),
        )
        val me = br.com.codecacto.kmplib.modalevents.ModalEvents.client?.config
        assertNotNull(me)
        assertEquals("locaki", me.projectSlug)
        assertEquals("1.4.0", me.appVersion)
        assertEquals("https://apps-api.example", me.appsApiBaseUrl)
        val rv = br.com.codecacto.kmplib.feedback.AppReviewService.config
        assertNotNull(rv)
        assertEquals("locaki", rv.projectSlug)
        assertNotNull(br.com.codecacto.kmplib.feedback.AppReviewService.configSource())
    }

    @Test
    fun initialize_podeDesligarEstatisticaEAvaliacao() {
        CentralServices.initialize(
            CentralServicesConfig(
                projectSlug = "z",
                httpClient = client(),
                enableModalEvents = false,
                enableReviewConfig = false,
            ),
        )
        assertFalse(br.com.codecacto.kmplib.modalevents.ModalEvents.isInitialized)
        assertFalse(br.com.codecacto.kmplib.feedback.AppReviewService.isInitialized)
    }

    @Test
    fun initialize_canDisableAnnouncements() {
        CentralServices.initialize(
            CentralServicesConfig(projectSlug = "z", httpClient = client(), enableAnnouncements = false),
        )
        assertFalse(AnnouncementService.isInitialized)
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
