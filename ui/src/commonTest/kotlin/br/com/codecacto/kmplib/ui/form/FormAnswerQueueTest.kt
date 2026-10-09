package br.com.codecacto.kmplib.ui.form

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** As garantias da fila — as mesmas do `useFormAnswerQueue`/`useSaveQueue` da weblib. */
@OptIn(ExperimentalCoroutinesApi::class)
class FormAnswerQueueTest {

    private fun text(v: String) = FormAnswerValue.text(v)

    @Test
    fun `espera a pessoa parar de digitar e manda um patch so - com o valor mais novo`() = runTest {
        val sent = mutableListOf<Map<String, FormAnswerValue?>>()
        val queue = FormAnswerQueue(backgroundScope) { sent += it }
        queue.enqueue("queixa", text("D"))
        queue.enqueue("queixa", text("Do"))
        advanceTimeBy(599)
        runCurrent()
        queue.enqueue("queixa", text("Dor"))
        assertEquals(1, queue.pending.value)
        advanceTimeBy(599)
        runCurrent()
        assertTrue(sent.isEmpty(), "cada tecla reinicia a espera de 600 ms")
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(mapOf<String, FormAnswerValue?>("queixa" to text("Dor"))), sent)
        assertEquals(0, queue.pending.value)
    }

    @Test
    fun `apagada vai como null no patch`() = runTest {
        val sent = mutableListOf<Map<String, FormAnswerValue?>>()
        val queue = FormAnswerQueue(backgroundScope) { sent += it }
        queue.enqueue("a", text("x"))
        queue.enqueue("a", null)
        queue.enqueue("b", text("y"))
        assertTrue(queue.flush())
        assertEquals(listOf(mapOf("a" to null, "b" to text("y"))), sent)
        assertEquals(listOf("a", "b"), sent.single().keys.toList())
    }

    @Test
    fun `uma requisicao de cada vez e o que chega no meio vai no lote seguinte`() = runTest {
        val sent = mutableListOf<Map<String, FormAnswerValue?>>()
        val gates = ArrayDeque<CompletableDeferred<Unit>>()
        var inFlight = 0
        var maxInFlight = 0
        val queue = FormAnswerQueue(backgroundScope, debounce = kotlin.time.Duration.ZERO) { patch ->
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            sent += patch
            val gate = CompletableDeferred<Unit>()
            gates.addLast(gate)
            gate.await()
            inFlight--
        }
        queue.enqueue("a", text("1"))
        runCurrent()
        queue.enqueue("b", text("2"))
        queue.enqueue("a", text("3"))
        runCurrent()
        assertEquals(1, sent.size)
        assertEquals(2, queue.pending.value, "a (no ar e de novo na fila) conta uma vez, mais b")
        gates.removeFirst().complete(Unit)
        runCurrent()
        assertEquals(mapOf("b" to text("2"), "a" to text("3")), sent[1])
        gates.removeFirst().complete(Unit)
        runCurrent()
        assertEquals(1, maxInFlight)
        assertEquals(0, queue.pending.value)
    }

    @Test
    fun `devolver o lote que falhou nao sobrescreve o valor mais novo - nem o apagado`() = runTest {
        var attempts = 0
        val gate = CompletableDeferred<Unit>()
        val sent = mutableListOf<Map<String, FormAnswerValue?>>()
        val queue = FormAnswerQueue(backgroundScope, debounce = kotlin.time.Duration.ZERO) { patch ->
            attempts++
            if (attempts == 1) {
                gate.await()
                throw IllegalStateException("caiu")
            }
            sent += patch
        }
        queue.enqueue("a", text("1"))
        runCurrent()
        queue.enqueue("a", null) // apagou com o lote no ar
        gate.complete(Unit)
        runCurrent()
        advanceTimeBy(501)
        runCurrent()
        assertEquals(listOf(mapOf<String, FormAnswerValue?>("a" to null)), sent, "o null mais novo vence o 1 que falhou")
        assertNull(queue.error.value)
        assertEquals(0, queue.pending.value)
    }

    @Test
    fun `falha fica no aviso e a fila tenta de novo sozinha`() = runTest {
        var attempts = 0
        val failure = IllegalStateException("sem rede")
        val queue = FormAnswerQueue(backgroundScope, debounce = kotlin.time.Duration.ZERO) {
            attempts++
            if (attempts == 1) throw failure
        }
        queue.enqueue("a", text("1"))
        runCurrent()
        assertSame(failure, queue.error.value)
        assertEquals(1, queue.pending.value)
        advanceTimeBy(499)
        runCurrent()
        assertEquals(1, attempts, "a 1ª retentativa espera 500 ms")
        advanceTimeBy(2)
        runCurrent()
        assertEquals(2, attempts)
        assertNull(queue.error.value, "escoou: o aviso some")
        assertEquals(0, queue.pending.value)
    }

    @Test
    fun `flush atravessa a espera e diz se o servidor tem tudo`() = runTest {
        var fail = true
        val queue = FormAnswerQueue(backgroundScope, retryInitialDelay = 1.hours) { if (fail) throw IllegalStateException("fora") }
        queue.enqueue("a", text("1"))
        assertFalse(queue.flush())
        assertEquals(1, queue.pending.value)
        fail = false
        assertTrue(queue.flush())
        assertEquals(0, queue.pending.value)
    }

    @Test
    fun `sendNow manda o pendente mesmo com o escopo da tela encerrado`() = runTest {
        val sent = mutableListOf<Map<String, FormAnswerValue?>>()
        val screen = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val queue = FormAnswerQueue(screen) { sent += it }
        queue.enqueue("a", text("1"))
        runCurrent()
        assertTrue(sent.isEmpty(), "esperando a quietude")
        queue.sendNow()
        screen.cancel() // a tela fechou (o viewModelScope acaba)
        runCurrent()
        assertEquals(listOf(mapOf<String, FormAnswerValue?>("a" to text("1"))), sent)
    }
}
