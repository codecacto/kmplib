package br.com.codecacto.kmplib.platform

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O link de "compartilhar o app" é o que dá ORIGEM à visita que o compartilhamento gera. Se ele
 * sair sem UTM, com o slug numa grafia diferente por tela, ou carregando a UTM de quem mandou o
 * link antes, o relatório de aquisição mente — e ninguém percebe olhando o app.
 */
class AppShareLinkTest {

    private val link = AppShareLink(landingUrl = "https://meu-app.codecacto.com.br", campaign = "meu-app")

    private fun params(url: String) = Url(url).parameters

    @Test
    fun `link do app leva source app, medium share e o slug como campaign`() {
        val p = params(link.url())
        assertEquals("app", p["utm_source"])
        assertEquals("share", p["utm_medium"])
        assertEquals("meu-app", p["utm_campaign"])
        assertNull(p["utm_content"], "sem content informado, o parametro nao existe")
    }

    @Test
    fun `utm_content identifica a tela`() {
        assertEquals("menu", params(link.url(content = "menu"))["utm_content"])
    }

    @Test
    fun `content em branco e omitido, nao vira parametro vazio`() {
        assertFalse("utm_content" in link.url(content = "   "))
    }

    @Test
    fun `aponta para o site do produto`() {
        val url = Url(link.url("menu"))
        assertEquals("meu-app.codecacto.com.br", url.host)
        assertTrue(link.url("menu").startsWith("https://meu-app.codecacto.com.br"))
    }

    @Test
    fun `campaign e content saem normalizados - caixa e espaco nao abrem linha nova no relatorio`() {
        val sujo = AppShareLink("https://x.com.br", campaign = "  Meu App ")
        val p = params(sujo.url(content = " Tela  Resultado "))
        assertEquals("meu-app", p["utm_campaign"])
        assertEquals("tela-resultado", p["utm_content"])
    }

    @Test
    fun `preserva caminho, query existente e fragmento`() {
        val url = link.urlFor("https://meu-app.codecacto.com.br/receita/42?ref=abc#passo-2", content = "receita")
        val parsed = Url(url)
        assertEquals("/receita/42", parsed.encodedPath)
        assertEquals("abc", parsed.parameters["ref"])
        assertEquals("receita", parsed.parameters["utm_content"])
        assertEquals("passo-2", parsed.fragment)
    }

    @Test
    fun `utm que ja estava no link e SUBSTITUIDA, nunca duplicada`() {
        val recebido = "https://x.com.br/?utm_source=instagram&utm_medium=social&utm_campaign=outro&utm_content=bio"
        val url = appendShareUtm(recebido, campaign = "meu-app")
        val p = params(url)
        assertEquals(listOf("app"), p.getAll("utm_source"))
        assertEquals(listOf("share"), p.getAll("utm_medium"))
        assertEquals(listOf("meu-app"), p.getAll("utm_campaign"))
        assertNull(p["utm_content"], "o content de quem mandou antes nao pode sobreviver")
    }

    @Test
    fun `valor com caractere especial e codificado na query`() {
        val url = appendShareUtm("https://x.com.br", campaign = "app", content = "a&b=c")
        assertEquals("a&b=c", params(url)["utm_content"])
        assertFalse(url.contains("a&b=c"), "o & cru quebraria a query")
    }

    @Test
    fun `url sem esquema http(s) e recusada`() {
        assertFailsWith<IllegalArgumentException> { AppShareLink("meu-app.codecacto.com.br", "meu-app") }
        assertFailsWith<IllegalArgumentException> { AppShareLink("ftp://x.com.br", "meu-app") }
        assertFailsWith<IllegalArgumentException> { AppShareLink("", "meu-app") }
        assertFailsWith<IllegalArgumentException> { link.urlFor("/receita/42") }
    }

    @Test
    fun `campaign vazio e recusado - link sem origem nao existe`() {
        assertFailsWith<IllegalArgumentException> { AppShareLink("https://x.com.br", "  ") }
        assertFailsWith<IllegalArgumentException> { appendShareUtm("https://x.com.br", "") }
    }

    @Test
    fun `http tambem e aceito`() {
        assertEquals("share", params(appendShareUtm("http://localhost:3000", "app"))["utm_medium"])
    }

    @Test
    fun `texto do share - mensagem, quebra de linha e o link`() {
        assertEquals("Conheça o app\nhttps://x.com.br", composeShareText("  Conheça o app ", " https://x.com.br "))
    }

    @Test
    fun `texto do share sem mensagem e so o link`() {
        assertEquals("https://x.com.br", composeShareText("", "https://x.com.br"))
    }

    @Test
    fun `shareLink default manda o texto composto pelo shareText`() {
        val fake = RecordingShareHandler()
        fake.shareLink(url = "https://x.com.br", message = "Oi", title = "App")
        assertEquals("Oi\nhttps://x.com.br" to "App", fake.texts.single())
    }

    private class RecordingShareHandler : ShareHandler {
        val texts = mutableListOf<Pair<String, String>>()
        override fun shareText(text: String, title: String) {
            texts += text to title
        }
        override fun shareImage(imageBytes: ByteArray, fileName: String, title: String) = Unit
        override fun shareFile(fileBytes: ByteArray, fileName: String, mimeType: String, title: String) = Unit
    }
}
