package br.com.codecacto.kmplib.platform.links

import kotlinx.coroutines.test.runTest
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

    // ------------------------------------------------------- pendente até confirmar (2.203.0)

    private class FakeStore : InstallReferrerLinkStore {
        override var consulted = false
        override var pendingUrl: String? = null
        override var pendingEpochSeconds = 0L
        override fun recordAnswer(pendingUrl: String?, epochSeconds: Long) {
            consulted = true
            this.pendingUrl = pendingUrl
            pendingEpochSeconds = epochSeconds
        }
        override fun clearPending() {
            pendingUrl = null
            pendingEpochSeconds = 0L
        }
        /** O que `markInstallReferrerLinkConsumed` faz no Android. */
        fun consume() = clearPending()
    }

    private val agora = 2_000_000L
    private val comLink = InstallReferrerAnswer.Ok(
        referrer = "cc_link=https%3A%2F%2Fa.com%2Fparceiros%2F1",
        clickEpochSeconds = agora - 60,
        installBeginEpochSeconds = agora - 30,
    )

    private suspend fun peek(
        store: FakeStore,
        now: Long = agora,
        onQuery: () -> Unit = {},
        answer: InstallReferrerAnswer = comLink,
    ) = peekInstallReferrerLinkWith(store, now, INSTALL_REFERRER_LINK_KEY, INSTALL_REFERRER_MAX_AGE_SECONDS) {
        onQuery()
        answer
    }

    @Test
    fun `o link continua pendente ate ser confirmado, e a Play e consultada uma vez so`() = runTest {
        val store = FakeStore()
        var consultas = 0

        // 1ª abertura: entrega; o processo morre antes de o destino abrir (login pelo Google).
        assertEquals("https://a.com/parceiros/1", peek(store, onQuery = { consultas++ }))
        // 2ª abertura: o link ainda está lá.
        assertEquals("https://a.com/parceiros/1", peek(store, onQuery = { consultas++ }))
        assertEquals(1, consultas)

        store.consume()
        assertNull(peek(store, onQuery = { consultas++ }))
        assertEquals(1, consultas, "confirmado não reconsulta a Play")
    }

    @Test
    fun `falha passageira nao marca como consultado`() = runTest {
        val store = FakeStore()
        assertNull(peek(store, answer = InstallReferrerAnswer.Transient))
        assertFalse(store.consulted)
        assertEquals("https://a.com/parceiros/1", peek(store))
    }

    @Test
    fun `resposta definitiva ou instalacao organica encerram sem link`() = runTest {
        val semServico = FakeStore()
        assertNull(peek(semServico, answer = InstallReferrerAnswer.Definitive))
        assertTrue(semServico.consulted)

        val organica = FakeStore()
        assertNull(peek(organica, answer = InstallReferrerAnswer.Ok("utm_source=google-play", agora, agora)))
        assertTrue(organica.consulted)
        assertNull(organica.pendingUrl)
    }

    @Test
    fun `link pendente vence e e descartado`() = runTest {
        val store = FakeStore()
        assertEquals("https://a.com/parceiros/1", peek(store))

        val umaSemanaDepois = agora + 7 * 24 * 60 * 60
        assertNull(peek(store, now = umaSemanaDepois))
        assertNull(store.pendingUrl)
        assertTrue(store.consulted)
    }

    @Test
    fun `sem instante da Play o link vence a partir da primeira leitura`() = runTest {
        val store = FakeStore()
        val semInstante = InstallReferrerAnswer.Ok(comLink.referrer, 0L, 0L)
        assertEquals("https://a.com/parceiros/1", peek(store, answer = semInstante))
        assertEquals(agora, store.pendingEpochSeconds)
        assertNull(peek(store, now = agora + INSTALL_REFERRER_MAX_AGE_SECONDS + 1))
    }

    @Test
    fun `clique velho ja na consulta nao fica pendente`() = runTest {
        val store = FakeStore()
        val velho = InstallReferrerAnswer.Ok(comLink.referrer, agora - INSTALL_REFERRER_MAX_AGE_SECONDS - 1, 0L)
        assertNull(peek(store, answer = velho))
        assertTrue(store.consulted)
        assertNull(store.pendingUrl)
    }
}
