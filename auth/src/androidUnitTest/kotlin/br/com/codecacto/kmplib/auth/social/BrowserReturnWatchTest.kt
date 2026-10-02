package br.com.codecacto.kmplib.auth.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GAP-AUTH-SOCIAL-01: fechar a aba do navegador sem concluir tem de ENCERRAR o login (cancelado), e
 * o login que termina de verdade não pode ser cancelado pela volta ao aplicativo.
 */
class BrowserReturnWatchTest {

    /** Relógio de mentira: guarda o que foi agendado e dispara quando o teste manda. */
    private class FakeScheduler {
        private class Task(val at: Long, val action: () -> Unit, var cancelled: Boolean = false)

        private val tasks = mutableListOf<Task>()
        var now = 0L
            private set

        val armed: Int get() = tasks.count { !it.cancelled }

        val schedule: (Long, () -> Unit) -> (() -> Unit) = { delay, action ->
            val task = Task(now + delay, action)
            tasks += task
            val unschedule: () -> Unit = { task.cancelled = true }
            unschedule
        }

        fun advance(millis: Long) {
            now += millis
            val due = tasks.filter { !it.cancelled && it.at <= now }
            tasks.removeAll(due)
            due.forEach { it.action() }
        }
    }

    private val scheduler = FakeScheduler()
    private var pending = true
    private var abandoned = 0

    private fun watch() = BrowserReturnWatch(
        toleranceMillis = 750,
        schedule = scheduler.schedule,
        isPending = { pending },
        onAbandoned = { abandoned++; pending = false },
    )

    @Test
    fun `fechar a aba e voltar ao app cancela o login depois da folga`() {
        val watch = watch()
        watch.onActivityPaused() // o navegador abriu
        watch.onActivityResumed() // a pessoa fechou a aba

        scheduler.advance(749)
        assertEquals(0, abandoned, "antes da folga ainda pode chegar o deep link")
        scheduler.advance(1)
        assertEquals(1, abandoned)
    }

    @Test
    fun `o onResume da tela que vai abrir o navegador nao cancela nada`() {
        val watch = watch()
        watch.onActivityResumed() // sem ter saído da frente antes

        scheduler.advance(10_000)
        assertEquals(0, abandoned)
        assertEquals(0, scheduler.armed)
    }

    @Test
    fun `login concluido antes da volta nao e cancelado`() {
        val watch = watch()
        watch.onActivityPaused()
        pending = false // a AuthCallbackActivity entregou o deep link no onCreate
        watch.onActivityResumed()

        scheduler.advance(10_000)
        assertEquals(0, abandoned)
        assertEquals(0, scheduler.armed, "sem pedido pendente nem se agenda")
    }

    @Test
    fun `deep link que chega dentro da folga vence o cancelamento`() {
        val watch = watch()
        watch.onActivityPaused()
        watch.onActivityResumed() // a tela voltou ANTES do deep link (ordem invertida)
        scheduler.advance(300)
        pending = false // handleRedirect completou o login

        scheduler.advance(10_000)
        assertEquals(0, abandoned)
    }

    @Test
    fun `volta de um instante seguida de nova saida nao cancela`() {
        val watch = watch()
        watch.onActivityPaused() // seletor "abrir com"
        watch.onActivityResumed() // a tela aparece um instante…
        scheduler.advance(200)
        watch.onActivityPaused() // …e o navegador escolhido abre

        scheduler.advance(10_000)
        assertEquals(0, abandoned)
        assertEquals(0, scheduler.armed)

        watch.onActivityResumed() // agora sim a pessoa fechou a aba
        scheduler.advance(750)
        assertEquals(1, abandoned)
    }

    @Test
    fun `em tela dividida o retorno nao cancela sozinho`() {
        val watch = watch()
        watch.onActivityPaused()
        watch.onActivityResumed(inMultiWindow = true)

        scheduler.advance(10_000)
        assertEquals(0, abandoned)
        assertTrue(pending)
    }

    @Test
    fun `dois retornos seguidos cancelam uma vez so`() {
        val watch = watch()
        watch.onActivityPaused()
        watch.onActivityResumed()
        watch.onActivityResumed() // duas telas do app retomando na mesma volta

        assertEquals(1, scheduler.armed)
        scheduler.advance(750)
        scheduler.advance(750)
        assertEquals(1, abandoned)
    }

    @Test
    fun `login encerrado desarma o que estava agendado`() {
        val watch = watch()
        watch.onActivityPaused()
        watch.onActivityResumed()
        watch.dispose() // a corrotina do login terminou (ex.: cancelada pela tela)

        scheduler.advance(10_000)
        assertEquals(0, abandoned)
        assertEquals(0, scheduler.armed)
    }
}
