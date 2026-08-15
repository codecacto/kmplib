package br.com.codecacto.kmplib.ui.components.html

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes das regras puras do [HtmlDocumentView] — a política de navegação (que é o que separa um
 * visualizador de documento de um navegador embutido) e o zoom.
 *
 * Estas regras são consultadas pelos DOIS `actual` (Android e iOS): é o que impede as plataformas de
 * divergirem justamente na parte de segurança, onde a divergência não aparece em teste de tela.
 */
class HtmlDocumentTest {

    private val documento = "https://api.exemplo.com.br/v1/me/relatorios/42"

    // ---------------------------------------------------------------------------------------
    // Esquemas recusados
    // ---------------------------------------------------------------------------------------

    @Test
    fun `javascript e recusado mesmo com navegacao externa liberada`() {
        val decisao = htmlLinkDecision(
            url = "javascript:alert(document.cookie)",
            documentUrl = documento,
            allowExternalNavigation = true,
        )
        assertEquals(HtmlLinkDecision.Block(HtmlBlockReason.UnsupportedScheme), decisao)
    }

    @Test
    fun `esquemas que leem o aparelho ou forjam origem sao recusados`() {
        listOf(
            "file:///data/data/br.com.app/databases/app.db",
            "content://media/external/images/media/1",
            "data:text/html;base64,PHNjcmlwdD4=",
            "blob:https://api.exemplo.com.br/1",
        ).forEach { url ->
            val decisao = htmlLinkDecision(url, documento, allowExternalNavigation = true)
            assertEquals(
                HtmlLinkDecision.Block(HtmlBlockReason.UnsupportedScheme),
                decisao,
                "deveria recusar $url",
            )
        }
    }

    @Test
    fun `endereco vazio ou sem esquema nao vira link`() {
        assertEquals(
            HtmlLinkDecision.Block(HtmlBlockReason.InvalidUrl),
            htmlLinkDecision("   ", documento, allowExternalNavigation = false),
        )
        assertEquals(
            HtmlLinkDecision.Block(HtmlBlockReason.InvalidUrl),
            htmlLinkDecision("/secao/3", documento, allowExternalNavigation = false),
        )
    }

    // ---------------------------------------------------------------------------------------
    // Âncoras — o índice de seções do documento tem de continuar funcionando
    // ---------------------------------------------------------------------------------------

    @Test
    fun `ancora do proprio documento navega por dentro`() {
        val decisao = htmlLinkDecision(
            url = "$documento#dados-do-participante",
            documentUrl = documento,
            allowExternalNavigation = false,
        )
        assertEquals(HtmlLinkDecision.Allow, decisao)
    }

    @Test
    fun `pular de uma ancora para outra continua sendo o mesmo documento`() {
        assertTrue(htmlIsSameDocument("$documento#s2", "$documento#s1"))
        assertEquals(
            HtmlLinkDecision.Allow,
            htmlLinkDecision("$documento#s2", "$documento#s1", allowExternalNavigation = false),
        )
    }

    @Test
    fun `outro documento do mesmo servidor nao e ancora`() {
        // Mesmo host, caminho diferente: é navegação, não rolagem. Sem esta distinção o visualizador
        // vira um mini-navegador dentro do próprio backend.
        val decisao = htmlLinkDecision(
            url = "https://api.exemplo.com.br/v1/me/relatorios/43",
            documentUrl = documento,
            allowExternalNavigation = false,
        )
        assertIs<HtmlLinkDecision.OpenExternally>(decisao)
    }

    @Test
    fun `documento sem base trata todo link como externo`() {
        val decisao = htmlLinkDecision(
            url = "https://codecacto.com.br#topo",
            documentUrl = null,
            allowExternalNavigation = false,
        )
        assertIs<HtmlLinkDecision.OpenExternally>(decisao)
    }

    // ---------------------------------------------------------------------------------------
    // Link para fora
    // ---------------------------------------------------------------------------------------

    @Test
    fun `link para fora e devolvido ao app em vez de abrir por dentro`() {
        val decisao = htmlLinkDecision(
            url = "https://codecacto.com.br/privacidade",
            documentUrl = documento,
            allowExternalNavigation = false,
        )
        assertEquals(
            HtmlLinkDecision.OpenExternally("https://codecacto.com.br/privacidade"),
            decisao,
        )
    }

    @Test
    fun `mailto e tel sao entregues ao app mesmo com navegacao externa liberada`() {
        // Ligar `allowExternalNavigation` transforma o componente num mini-navegador de páginas web;
        // não faz o WebView virar discador nem cliente de e-mail.
        listOf("mailto:suporte@exemplo.com.br", "tel:+551140028922", "whatsapp://send?phone=55")
            .forEach { url ->
                assertIs<HtmlLinkDecision.OpenExternally>(
                    htmlLinkDecision(url, documento, allowExternalNavigation = true),
                    "deveria devolver $url ao app",
                )
            }
    }

    @Test
    fun `com navegacao externa liberada o site abre por dentro`() {
        assertEquals(
            HtmlLinkDecision.Allow,
            htmlLinkDecision(
                "https://codecacto.com.br",
                documento,
                allowExternalNavigation = true,
            ),
        )
    }

    @Test
    fun `carga do proprio documento nunca e tratada como link`() {
        val decisao = htmlLinkDecision(
            url = "https://outro-host.example/qualquer",
            documentUrl = documento,
            allowExternalNavigation = false,
            isDocumentLoad = true,
        )
        assertEquals(HtmlLinkDecision.Allow, decisao)
    }

    @Test
    fun `espacos em volta do endereco nao mudam a decisao`() {
        assertEquals(
            HtmlLinkDecision.Allow,
            htmlLinkDecision("  $documento#s1  ", documento, allowExternalNavigation = false),
        )
    }

    // ---------------------------------------------------------------------------------------
    // Esquema
    // ---------------------------------------------------------------------------------------

    @Test
    fun `esquema e normalizado e so aceita o formato do RFC`() {
        assertEquals("https", htmlUrlScheme("HTTPS://exemplo.com"))
        assertEquals("mailto", htmlUrlScheme("mailto:a@b.c"))
        assertNull(htmlUrlScheme("//exemplo.com"), "sem esquema")
        assertNull(htmlUrlScheme("relatorio.html"))
        assertNull(htmlUrlScheme("1abc:coisa"), "esquema não pode começar com dígito")
    }

    // ---------------------------------------------------------------------------------------
    // Fonte do documento
    // ---------------------------------------------------------------------------------------

    @Test
    fun `a fonte informa qual e o documento corrente`() {
        assertEquals(documento, HtmlDocumentSource.Url(documento).documentUrl)
        assertEquals(
            "https://api.exemplo.com.br/",
            HtmlDocumentSource.Html("<p>oi</p>", baseUrl = "https://api.exemplo.com.br/").documentUrl,
        )
        assertNull(HtmlDocumentSource.Html("<p>oi</p>").documentUrl)
    }

    @Test
    fun `url autenticada por header preserva os cabecalhos`() {
        val fonte = HtmlDocumentSource.Url(documento, mapOf("Authorization" to "Bearer abc"))
        assertEquals("Bearer abc", fonte.headers["Authorization"])
    }

    // ---------------------------------------------------------------------------------------
    // Zoom
    // ---------------------------------------------------------------------------------------

    @Test
    fun `zoom acompanha a escala de fonte do tema`() {
        // AppFontScale.Small/Medium/Large/ExtraLarge
        assertEquals(90, htmlDocumentZoomPercent(0.9f))
        assertEquals(100, htmlDocumentZoomPercent(1f))
        assertEquals(130, htmlDocumentZoomPercent(1.3f))
        assertEquals(160, htmlDocumentZoomPercent(1.6f))
    }

    @Test
    fun `zoom absurdo e limitado em vez de quebrar o documento`() {
        assertEquals(MIN_HTML_DOCUMENT_ZOOM, clampHtmlDocumentZoom(0.01f))
        assertEquals(MAX_HTML_DOCUMENT_ZOOM, clampHtmlDocumentZoom(50f))
        assertEquals(1f, clampHtmlDocumentZoom(Float.NaN), "valor inválido volta ao neutro")
        assertEquals(300, htmlDocumentZoomPercent(9f))
    }

    // ---------------------------------------------------------------------------------------
    // Estados
    // ---------------------------------------------------------------------------------------

    @Test
    fun `carregando sem fracao e indeterminado, nunca zero`() {
        // Uma barra parada em 0% diz à pessoa que nada está acontecendo.
        assertNull((HtmlDocumentState.Loading() as HtmlDocumentState.Loading).progress)
        assertEquals(0.4f, HtmlDocumentState.Loading(0.4f).progress)
    }

    @Test
    fun `falha carrega o diagnostico, nao o texto de tela`() {
        val estado = HtmlDocumentState.Failed(
            HtmlDocumentError(message = "net::ERR_TIMED_OUT", code = 504, url = documento),
        )
        assertEquals(504, estado.error.code)
        assertEquals(documento, estado.error.url)
    }
}
