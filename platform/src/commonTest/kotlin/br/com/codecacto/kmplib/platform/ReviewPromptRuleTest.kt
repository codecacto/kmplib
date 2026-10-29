package br.com.codecacto.kmplib.platform

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regra remota do pedido de avaliação (2.335.0) — contrato §5. */
class ReviewPromptRuleTest {

    private val day = 24L * 60 * 60 * 1000

    private fun rule(
        config: ReviewPromptConfig = ReviewPromptConfig.DEFAULT,
        completions: Int = 3,
        concluded: Boolean = false,
        promptCount: Int = 0,
        lastAt: Long = 0L,
        now: Long = 100 * day,
    ) = shouldPromptReview(config, completions, concluded, promptCount, lastAt, now)

    @Test
    fun defaultsDoContrato() {
        assertEquals(ReviewPromptConfig(true, 3, 30, 3), ReviewPromptConfig.DEFAULT)
    }

    @Test
    fun apareceNoGatilhoNuncaExibido() {
        assertTrue(rule())
        assertFalse(rule(completions = 2))
    }

    @Test
    fun desligadoNaoAparece() {
        assertFalse(rule(config = ReviewPromptConfig(enabled = false)))
    }

    @Test
    fun concluidoNuncaMais() {
        assertFalse(rule(concluded = true))
    }

    @Test
    fun lembreteSoDepoisDeRemindAfterDays() {
        val now = 100 * day
        assertFalse(rule(promptCount = 1, lastAt = now - 29 * day, now = now))
        assertTrue(rule(promptCount = 1, lastAt = now - 30 * day, now = now))
    }

    @Test
    fun tetoDeExibicoes() {
        assertFalse(rule(promptCount = 3, lastAt = 1L))
        assertTrue(rule(config = ReviewPromptConfig(maxPrompts = 4), promptCount = 3, lastAt = 1L))
    }

    @Test
    fun valoresForaDaFaixaSaoAjustados() {
        val c = ReviewPromptConfig(triggerCount = 0, remindAfterDays = -5, maxPrompts = 0).sanitized()
        assertEquals(1, c.triggerCount)
        assertEquals(0, c.remindAfterDays)
        assertEquals(1, c.maxPrompts)
    }

    // ====== AppReviewManager com regra remota ======

    private class Clock(var now: Long)

    private fun manager(
        store: FakeReviewStore,
        clock: Clock,
        source: ReviewConfigSource? = ReviewConfigSource { ReviewPromptConfig.DEFAULT },
    ) = AppReviewManager(store = store, suppressed = { false }, configSource = source, clock = { clock.now })

    @Test
    fun dispensadoVoltaDepoisDoLembreteENaoPassaDoTeto() = runTest {
        val store = FakeReviewStore()
        val clock = Clock(10 * day)
        val m = manager(store, clock, ReviewConfigSource { ReviewPromptConfig(triggerCount = 1, remindAfterDays = 2, maxPrompts = 2) })
        m.refreshConfig()

        assertTrue(m.onCompletion())
        m.onPromptShown()
        m.onPromptOutcome(ReviewPromptOutcome.DISMISSED)
        assertFalse(m.onCompletion())

        clock.now += 2 * day
        assertTrue(m.onCompletion())
        m.onPromptShown()
        m.onPromptOutcome(ReviewPromptOutcome.DISMISSED)

        clock.now += 10 * day
        assertFalse(m.onCompletion(), "maxPrompts = 2 atingido")
        assertEquals(2, m.promptCount())
    }

    @Test
    fun positivoOuNegativoConcluem() = runTest {
        for (outcome in listOf(ReviewPromptOutcome.POSITIVE, ReviewPromptOutcome.NEGATIVE)) {
            val store = FakeReviewStore()
            val m = manager(store, Clock(day), ReviewConfigSource { ReviewPromptConfig(triggerCount = 1) })
            m.refreshConfig()
            assertTrue(m.onCompletion())
            m.onPromptShown()
            m.onPromptOutcome(outcome)
            assertTrue(m.hasReviewed())
            assertFalse(m.onCompletion())
        }
    }

    @Test
    fun configRemotaDesligadaSilencia() = runTest {
        val store = FakeReviewStore()
        val m = manager(store, Clock(day), ReviewConfigSource { ReviewPromptConfig(enabled = false, triggerCount = 1) })
        m.refreshConfig()
        assertFalse(m.onCompletion())
    }

    @Test
    fun semRedeUsaCacheSenaoDefaults() = runTest {
        val store = FakeReviewStore()
        val clock = Clock(day)
        val semRede = ReviewConfigSource { null }
        val m = manager(store, clock, semRede)
        assertEquals(ReviewPromptConfig.DEFAULT, m.refreshConfig())

        store.cachedConfig = CachedReviewConfig(ReviewPromptConfig(triggerCount = 7), fetchedAtMillis = 0L)
        val m2 = manager(store, clock, semRede)
        assertEquals(7, m2.refreshConfig().triggerCount)
    }

    @Test
    fun cacheFrescoNaoVaiARede() = runTest {
        val store = FakeReviewStore()
        val clock = Clock(day)
        var chamadas = 0
        val m = manager(store, clock, ReviewConfigSource { chamadas++; ReviewPromptConfig(triggerCount = 5) })
        m.refreshConfig()
        m.refreshConfig()
        assertEquals(1, chamadas)
        assertEquals(5, store.cachedConfig?.config?.triggerCount)

        clock.now += AppReviewManager.DEFAULT_CONFIG_MAX_AGE_MILLIS
        m.refreshConfig()
        assertEquals(2, chamadas)
        m.refreshConfig(force = true)
        assertEquals(3, chamadas)
    }

    @Test
    fun fonteQueLancaNaoQuebra() = runTest {
        val m = manager(FakeReviewStore(), Clock(day), ReviewConfigSource { error("boom") })
        assertEquals(ReviewPromptConfig.DEFAULT, m.refreshConfig())
    }

    @Test
    fun formaAntigaContinuaIgual() {
        val store = FakeReviewStore()
        val m = AppReviewManager(triggerCount = 2, store = store, suppressed = { false })
        assertFalse(m.onCompletion())
        assertTrue(m.onCompletion())
        assertTrue(m.onCompletion())
        m.markShown()
        assertFalse(m.onCompletion())
    }
}
