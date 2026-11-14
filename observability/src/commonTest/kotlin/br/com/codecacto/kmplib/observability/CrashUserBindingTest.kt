package br.com.codecacto.kmplib.observability

import br.com.codecacto.kmplib.core.identity.SignedInAccount
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CrashUserBindingTest {

    private fun config(follow: Boolean = true) = CrashReporterConfig(
        dsn = "https://k@errors.codecacto.com.br/1",
        environment = "test",
        release = "app@1.0+1",
        followSignedInAccount = follow,
    )

    @BeforeTest
    fun limpar() = SignedInAccount.publish(null)

    @Test
    fun segue_a_conta_publicada_pela_sessao() {
        val reporter = RecordingCrashReporter()
        reporter.init(config())
        assertNull(reporter.currentUserId) // sem login = anônimo

        SignedInAccount.publish("acc-1")
        assertEquals("acc-1", reporter.currentUserId)

        SignedInAccount.publish("acc-2") // troca de conta
        assertEquals("acc-2", reporter.currentUserId)

        SignedInAccount.publish(null) // logout limpa
        assertNull(reporter.currentUserId)
    }

    @Test
    fun sessao_restaurada_antes_do_init_vale_no_init() {
        SignedInAccount.publish("acc-cofre")
        val reporter = RecordingCrashReporter()
        reporter.init(config())
        assertEquals("acc-cofre", reporter.currentUserId)
        SignedInAccount.publish(null)
    }

    @Test
    fun setUser_antes_do_init_nao_se_perde() {
        val reporter = RecordingCrashReporter()
        reporter.setUser("acc-manual")
        assertNull(reporter.currentUserId)
        reporter.init(config())
        assertEquals("acc-manual", reporter.currentUserId)
    }

    @Test
    fun email_nunca_vira_usuario() {
        val reporter = RecordingCrashReporter()
        reporter.init(config())
        reporter.setUser("acc-1")
        reporter.setUser("ana@exemplo.com")
        assertNull(reporter.currentUserId)
        reporter.setUser("123.456.789-09")
        assertNull(reporter.currentUserId)
    }

    @Test
    fun sem_followSignedInAccount_ignora_a_sessao() {
        val reporter = RecordingCrashReporter()
        reporter.init(config(follow = false))
        SignedInAccount.publish("acc-1")
        assertNull(reporter.currentUserId)
        reporter.setUser("acc-manual")
        assertEquals("acc-manual", reporter.currentUserId)
        SignedInAccount.publish(null)
    }

    @Test
    fun reporter_inativo_nao_segue_a_sessao() {
        val reporter = RecordingCrashReporter()
        reporter.init(config().copy(enabled = false))
        SignedInAccount.publish("acc-1")
        assertNull(reporter.currentUserId)
        SignedInAccount.publish(null)
    }
}
