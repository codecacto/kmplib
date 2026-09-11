package br.com.codecacto.kmplib.ui.share

import br.com.codecacto.kmplib.platform.AppShareLink
import br.com.codecacto.kmplib.platform.ShareHandler
import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A entrada "Compartilhar app" tem de mandar ao share sheet: a mensagem, o título e o link do site
 * COM a UTM da tela que originou o toque.
 */
class ShareAppTest {

    private val link = AppShareLink(landingUrl = "https://meu-app.codecacto.com.br", campaign = "meu-app")
    private val texts = ShareAppTexts(
        label = "Compartilhar app",
        message = "Estou usando o app Meu App. Baixe também:",
        title = "Meu App",
    )

    @Test
    fun `shareApp manda mensagem, titulo e o link com UTM`() {
        val fake = RecordingShareHandler()
        fake.shareApp(link, texts, content = SHARE_APP_CONTENT_MENU)

        val chamada = fake.links.single()
        assertEquals("Estou usando o app Meu App. Baixe também:", chamada.message)
        assertEquals("Meu App", chamada.title)
        val p = Url(chamada.url).parameters
        assertEquals("app", p["utm_source"])
        assertEquals("share", p["utm_medium"])
        assertEquals("meu-app", p["utm_campaign"])
        assertEquals("menu", p["utm_content"])
    }

    @Test
    fun `sem content, o link nao leva utm_content`() {
        val fake = RecordingShareHandler()
        fake.shareApp(link, texts)
        assertNull(Url(fake.links.single().url).parameters["utm_content"])
    }

    @Test
    fun `defaults literais sao pt-BR e sem mensagem`() {
        val padrao = ShareAppTexts()
        assertEquals("Compartilhar app", padrao.label)
        assertEquals("", padrao.message)
    }

    @Test
    fun `content padrao do menu e menu`() {
        assertEquals("menu", SHARE_APP_CONTENT_MENU)
    }

    private data class LinkCall(val url: String, val message: String, val title: String)

    private class RecordingShareHandler : ShareHandler {
        val links = mutableListOf<LinkCall>()
        override fun shareText(text: String, title: String) = Unit
        override fun shareImage(imageBytes: ByteArray, fileName: String, title: String) = Unit
        override fun shareFile(fileBytes: ByteArray, fileName: String, mimeType: String, title: String) = Unit
        override fun shareLink(url: String, message: String, title: String) {
            links += LinkCall(url, message, title)
        }
    }
}
