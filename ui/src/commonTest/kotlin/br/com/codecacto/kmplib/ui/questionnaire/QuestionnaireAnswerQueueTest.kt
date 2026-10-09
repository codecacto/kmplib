package br.com.codecacto.kmplib.ui.questionnaire

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** As quatro garantias da fila — as mesmas do `useAnswerQueue`/`useSaveQueue` da weblib. */
@OptIn(ExperimentalCoroutinesApi::class)
class QuestionnaireAnswerQueueTest {

    private fun item(id: String, value: Int?) = QuestionnaireAnswerItem(id, value?.let { QuestionnaireValue.of(it) })

    @Test
    fun `envia e zera a pendencia so depois da confirmacao`() = runTest {
        val sent = mutableListOf<List<QuestionnaireAnswerItem>>()
        val gate = CompletableDeferred<Unit>()
        val queue = QuestionnaireAnswerQueue(backgroundScope) { batch ->
            sent += batch
            gate.await()
        }
        queue.enqueue(item("q1", 2))
        assertEquals(1, queue.pending.value)
        runCurrent()
        assertTrue(queue.saving.value)
        assertEquals(1, queue.pending.value, "no ar ainda não é confirmado")
        gate.complete(Unit)
        runCurrent()
        assertFalse(queue.saving.value)
        assertEquals(0, queue.pending.value)
        assertEquals(listOf(listOf(item("q1", 2))), sent)
        assertNull(queue.error.value)
    }

    @Test
    fun `uma requisicao de cada vez e o que chega no meio se junta - mais novo vence`() = runTest {
        val sent = mutableListOf<List<QuestionnaireAnswerItem>>()
        var inFlight = 0
        var maxInFlight = 0
        val gates = ArrayDeque<CompletableDeferred<Unit>>()
        val queue = QuestionnaireAnswerQueue(backgroundScope) { batch ->
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            sent += batch
            val gate = CompletableDeferred<Unit>()
            gates.addLast(gate)
            gate.await()
            inFlight--
        }
        queue.enqueue(item("q1", 1))
        runCurrent()
        // Enquanto o primeiro lote está no ar, três toques: q2, e q1 duas vezes.
        queue.enqueue(item("q2", 5))
        queue.enqueue(item("q1", 2))
        queue.enqueue(item("q1", 3))
        runCurrent()
        assertEquals(1, sent.size, "o segundo lote espera o primeiro")
        assertEquals(2, queue.pending.value, "por pergunta: q1 (no ar e de novo na fila) conta uma vez, mais q2")
        gates.removeFirst().complete(Unit)
        runCurrent()
        assertEquals(listOf(item("q2", 5), item("q1", 3)), sent[1], "um lote, valor mais novo")
        gates.removeFirst().complete(Unit)
        runCurrent()
        assertEquals(2, sent.size)
        assertEquals(1, maxInFlight)
        assertEquals(0, queue.pending.value)
    }

    @Test
    fun `falha volta para a fila e tenta de novo sozinha com espera crescente`() = runTest {
        var attempts = 0
        val sent = mutableListOf<List<QuestionnaireAnswerItem>>()
        val failure = IllegalStateException("sem rede")
        val queue = QuestionnaireAnswerQueue(backgroundScope) { batch ->
            attempts++
            if (attempts <= 2) throw failure
            sent += batch
        }
        queue.enqueue(item("q1", 1))
        runCurrent()
        assertEquals(1, attempts)
        assertSame(failure, queue.error.value)
        assertEquals(1, queue.pending.value)
        advanceTimeBy(499)
        runCurrent()
        assertEquals(1, attempts, "a 1ª retentativa espera 500 ms")
        advanceTimeBy(2) // t = 501: a 2ª tentativa rodou em 500, a 3ª fica para 1500
        runCurrent()
        assertEquals(2, attempts)
        advanceTimeBy(998) // t = 1499
        runCurrent()
        assertEquals(2, attempts, "a 2ª espera o dobro")
        advanceTimeBy(2)
        runCurrent()
        assertEquals(3, attempts)
        assertEquals(listOf(listOf(item("q1", 1))), sent)
        assertNull(queue.error.value, "escoou: o aviso some")
        assertEquals(0, queue.pending.value)
    }

    @Test
    fun `devolver o lote nao sobrescreve o valor mais novo`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var first = true
        val sent = mutableListOf<List<QuestionnaireAnswerItem>>()
        val queue = QuestionnaireAnswerQueue(backgroundScope) { batch ->
            if (first) {
                first = false
                gate.await()
                throw IllegalStateException("caiu")
            }
            sent += batch
        }
        queue.enqueue(item("q1", 1))
        queue.enqueue(item("q2", 1))
        runCurrent()
        queue.enqueue(item("q1", 9)) // a pessoa mudou a resposta com o lote no ar
        gate.complete(Unit)
        runCurrent()
        runCurrent()
        assertEquals(listOf(listOf(item("q1", 9), item("q2", 1))), sent, "q1 = 9 vence o 1 que falhou")
    }

    @Test
    fun `flush diz se o servidor tem tudo`() = runTest {
        var fail = true
        val queue = QuestionnaireAnswerQueue(backgroundScope, retryInitialDelay = kotlin.time.Duration.parse("1h")) {
            if (fail) throw IllegalStateException("fora do ar")
        }
        queue.enqueue(item("q1", 1))
        runCurrent()
        assertFalse(queue.flush(), "falhou: concluir agora congelaria um resultado incompleto")
        fail = false
        assertTrue(queue.flush())
        assertEquals(0, queue.pending.value)
        assertTrue(queue.flush(), "fila vazia")
    }

    @Test
    fun `apagar vai como item nulo`() = runTest {
        val sent = mutableListOf<List<QuestionnaireAnswerItem>>()
        val queue = QuestionnaireAnswerQueue(backgroundScope) { sent += it }
        queue.enqueue(item("q1", 1))
        queue.enqueue(item("q1", null))
        runCurrent()
        assertEquals(listOf(item("q1", null)), sent.flatten().filter { it.questionId == "q1" }.takeLast(1))
    }

    @Test
    fun `espera da retentativa tem teto`() = runTest {
        var attempts = 0
        val queue = QuestionnaireAnswerQueue(backgroundScope) { attempts++; throw IllegalStateException("x") }
        queue.enqueue(item("q1", 1))
        runCurrent()
        // 500, 1000, 2000, 4000, 8000, 8000, 8000…
        advanceTimeBy(500 + 1000 + 2000 + 4000 + 8000 + 8000 + 1)
        runCurrent()
        assertEquals(7, attempts)
    }
}
