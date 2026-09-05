package br.com.codecacto.kmplib.platform.privacy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakePrivacyScreen : PrivacyScreen {
    override val isSupported: Boolean = true
    var hidden: Boolean = false
        private set
    var calls: Int = 0
        private set

    override val isHidden: Boolean get() = hidden

    override fun setHidden(hidden: Boolean) {
        calls += 1
        this.hidden = hidden
    }
}

class HideFromRecentsRequestsTest {

    @Test
    fun primeiroPedidoEsconde() {
        val screen = FakePrivacyScreen()
        val requests = HideFromRecentsRequests()

        requests.acquire(screen)

        assertTrue(screen.hidden)
        assertEquals(1, screen.calls)
        assertEquals(1, requests.activeRequests)
    }

    @Test
    fun segundoPedidoNaoRepeteAChamada() {
        val screen = FakePrivacyScreen()
        val requests = HideFromRecentsRequests()

        requests.acquire(screen)
        requests.acquire(screen)

        assertEquals(1, screen.calls)
        assertEquals(2, requests.activeRequests)
    }

    /** O caso que a contagem existe para resolver: a tela de detalhe sai e a de lista continua. */
    @Test
    fun soDescobreQuandoOUltimoPedidoSai() {
        val screen = FakePrivacyScreen()
        val requests = HideFromRecentsRequests()

        requests.acquire(screen)
        requests.acquire(screen)
        requests.release(screen)

        assertTrue(screen.hidden)

        requests.release(screen)

        assertFalse(screen.hidden)
        assertEquals(0, requests.activeRequests)
    }

    @Test
    fun releaseSobrandoNaoDeixaContagemNegativaNemDescobreDeNovo() {
        val screen = FakePrivacyScreen()
        val requests = HideFromRecentsRequests()

        requests.acquire(screen)
        requests.release(screen)
        val callsDepoisDoCiclo = screen.calls

        requests.release(screen)
        requests.release(screen)

        assertEquals(0, requests.activeRequests)
        assertEquals(callsDepoisDoCiclo, screen.calls)
    }

    @Test
    fun novoCicloEscondeDeNovo() {
        val screen = FakePrivacyScreen()
        val requests = HideFromRecentsRequests()

        requests.acquire(screen)
        requests.release(screen)
        requests.acquire(screen)

        assertTrue(screen.hidden)
        assertEquals(3, screen.calls)
    }
}
