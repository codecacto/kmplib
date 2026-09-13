package br.com.codecacto.kmplib.platform.links

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstallReferrerLinkTest {

    @Test
    fun `tira o link decodificado do referrer da Play`() {
        val bruto = "cc_link=https%3A%2F%2Fmirassolconectado.com.br%2Fparceiros%2F123&utm_source=site"
        assertEquals("https://mirassolconectado.com.br/parceiros/123", parseInstallReferrerLink(bruto))
    }

    @Test
    fun `instalacao organica nao tem link`() {
        assertNull(parseInstallReferrerLink("utm_source=google-play&utm_medium=organic"))
        assertNull(parseInstallReferrerLink(null))
        assertNull(parseInstallReferrerLink(""))
    }

    @Test
    fun `chave vazia ou parecida nao casa`() {
        assertNull(parseInstallReferrerLink("cc_link="))
        assertNull(parseInstallReferrerLink("xcc_link=https%3A%2F%2Fa.com"))
    }

    @Test
    fun `chave customizada`() {
        assertEquals("https://a.com/x", parseInstallReferrerLink("abrir=https%3A%2F%2Fa.com%2Fx", key = "abrir"))
    }

    @Test
    fun `clique velho demais nao vale, e clique desconhecido vale`() {
        val agora = 1_000_000L
        assertTrue(isInstallReferrerFresh(agora - 60, agora, INSTALL_REFERRER_MAX_AGE_SECONDS))
        assertFalse(isInstallReferrerFresh(agora - INSTALL_REFERRER_MAX_AGE_SECONDS - 1, agora, INSTALL_REFERRER_MAX_AGE_SECONDS))
        assertTrue(isInstallReferrerFresh(0L, agora, INSTALL_REFERRER_MAX_AGE_SECONDS))
        assertFalse(isInstallReferrerFresh(agora + 3600, agora, INSTALL_REFERRER_MAX_AGE_SECONDS))
    }
}
