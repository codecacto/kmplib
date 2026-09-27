package br.com.codecacto.kmplib.core.network

import br.com.codecacto.kmplib.core.locale.FactoryLocales
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceLocaleHeadersTest {

    private fun client(
        vistos: MutableList<Headers>,
        config: DeviceLocaleHeadersConfig.() -> Unit = {},
    ): HttpClient = HttpClient(MockEngine { request -> vistos += request.headers; respondOk() }) {
        install(DeviceLocaleHeaders, config)
    }

    @Test
    fun `manda idioma da tela e fuso do aparelho por default`() = runTest {
        val vistos = mutableListOf<Headers>()
        client(vistos).get("https://api.example.com/v1/x")
        val h = vistos.single()
        assertTrue(h[HttpHeaders.AcceptLanguage] in FactoryLocales.ALL)
        assertFalse(h[HEADER_TIME_ZONE].isNullOrBlank())
    }

    @Test
    fun `provedores sao chamados a cada requisicao`() = runTest {
        val vistos = mutableListOf<Headers>()
        var idioma = "en"
        val http = client(vistos) {
            languageTag = { idioma }
            timeZoneId = { "Europe/Lisbon" }
        }
        http.get("https://api.example.com/a")
        idioma = "es"
        http.get("https://api.example.com/b")
        assertEquals(listOf("en", "es"), vistos.map { it[HttpHeaders.AcceptLanguage] })
        assertEquals("Europe/Lisbon", vistos.first()[HEADER_TIME_ZONE])
    }

    @Test
    fun `cabecalho da chamada vence o do plugin`() = runTest {
        val vistos = mutableListOf<Headers>()
        client(vistos) { languageTag = { "en" } }.get("https://api.example.com/x") {
            header(HttpHeaders.AcceptLanguage, "pt-PT")
            header(HEADER_TIME_ZONE, "America/Cuiaba")
        }
        assertEquals("pt-PT", vistos.single()[HttpHeaders.AcceptLanguage])
        assertEquals("America/Cuiaba", vistos.single()[HEADER_TIME_ZONE])
        assertEquals(1, vistos.single().getAll(HttpHeaders.AcceptLanguage)!!.size)
    }

    @Test
    fun `provedor nulo, em branco ou que falha nao manda nada`() = runTest {
        val vistos = mutableListOf<Headers>()
        client(vistos) {
            languageTag = { error("boom") }
            timeZoneId = { " " }
        }.get("https://api.example.com/x")
        assertNull(vistos.single()[HttpHeaders.AcceptLanguage])
        assertNull(vistos.single()[HEADER_TIME_ZONE])
    }

    @Test
    fun `onlyHosts restringe a quem recebe`() = runTest {
        val vistos = mutableListOf<Headers>()
        val http = client(vistos) {
            languageTag = { "en" }
            onlyHosts = setOf("API.example.com")
        }
        http.get("https://api.example.com/x")
        http.get("https://cdn.terceiro.com/y")
        assertEquals("en", vistos[0][HttpHeaders.AcceptLanguage])
        assertNull(vistos[1][HttpHeaders.AcceptLanguage])
        assertNull(vistos[1][HEADER_TIME_ZONE])
    }

    @Test
    fun `opcao do factory vem desligada`() {
        assertFalse(HttpClientOptions().sendLocaleHeaders)
    }
}
