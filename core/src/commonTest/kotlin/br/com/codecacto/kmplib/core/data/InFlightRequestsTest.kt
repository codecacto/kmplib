package br.com.codecacto.kmplib.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.io.IOException
import kotlin.concurrent.Volatile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InFlightRequestsTest {

    /** O escopo do "repositório": onde a leitura compartilhada roda. */
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun fechar() = escopo.cancel()

    private class Contador {
        @Volatile var execucoes = 0
    }

    /** Espera, em tempo real, o bloco compartilhado (que roda no Default) chegar a [alvo]. */
    private suspend fun esperar(alvo: CompletableDeferred<Unit>) =
        withContext(Dispatchers.Default) { withTimeout(5_000) { alvo.await() } }

    @Test
    fun `chamadas iguais em voo executam o bloco UMA vez e recebem o mesmo valor`() = runTest {
        val voo = InFlightRequests<String, Int>(escopo)
        val entrou = CompletableDeferred<Unit>()
        val libera = CompletableDeferred<Unit>()
        val c = Contador()
        val chamadas = List(3) {
            async { voo.run("pagina-1") { c.execucoes++; entrou.complete(Unit); libera.await(); 42 } }
        }
        esperar(entrou)
        advanceUntilIdle()
        libera.complete(Unit)
        assertEquals(listOf(42, 42, 42), chamadas.map { it.await() })
        assertEquals(1, c.execucoes)
        assertEquals(0, voo.inFlightCount(), "não é cache: a chave sai quando termina")
    }

    @Test
    fun `chaves diferentes nao se misturam`() = runTest {
        val voo = InFlightRequests<String, String>(escopo)
        val c = Contador()
        val a = async { voo.run("p1") { c.execucoes++; "um" } }
        val b = async { voo.run("p2") { c.execucoes++; "dois" } }
        assertEquals("um", a.await())
        assertEquals("dois", b.await())
        assertEquals(2, c.execucoes)
    }

    @Test
    fun `depois de terminar a mesma chave executa de novo`() = runTest {
        val voo = InFlightRequests<String, Int>(escopo)
        val c = Contador()
        voo.run("k") { ++c.execucoes }
        assertEquals(2, voo.run("k") { ++c.execucoes })
    }

    @Test
    fun `falha real chega a TODOS com a excecao original`() = runTest {
        val voo = InFlightRequests<String, Int>(escopo)
        val entrou = CompletableDeferred<Unit>()
        val libera = CompletableDeferred<Unit>()
        val c = Contador()
        val chamadas = List(3) {
            async {
                runCatching {
                    voo.run("k") { c.execucoes++; entrou.complete(Unit); libera.await(); throw IOException("Connection reset") }
                }
            }
        }
        esperar(entrou)
        advanceUntilIdle()
        libera.complete(Unit)
        chamadas.forEach { chamada ->
            val erro = chamada.await().exceptionOrNull()
            assertIs<IOException>(erro, "veio $erro — nunca a CancellationException de outra corrotina")
            assertEquals("Connection reset", erro.message)
        }
        assertEquals(1, c.execucoes)
    }

    @Test
    fun `lider cancelado nao interrompe quem esperava — UMA requisicao so`() = runTest {
        val voo = InFlightRequests<String, Int>(escopo)
        val entrou = CompletableDeferred<Unit>()
        val libera = CompletableDeferred<Unit>()
        val c = Contador()
        val lider = async { voo.run("k") { c.execucoes++; entrou.complete(Unit); libera.await(); 7 } }
        esperar(entrou)
        val seguidor = async { voo.run("k") { c.execucoes++; 99 } }
        advanceUntilIdle()
        lider.cancel() // o `loadJob?.cancel()` da tela que pediu primeiro
        libera.complete(Unit)
        assertEquals(7, seguidor.await(), "quem pegou carona recebe o resultado da MESMA requisição")
        assertTrue(lider.isCancelled)
        assertEquals(1, c.execucoes)
    }

    @Test
    fun `quem esperava e foi cancelado sai sem afetar o lider`() = runTest {
        val voo = InFlightRequests<String, Int>(escopo)
        val entrou = CompletableDeferred<Unit>()
        val libera = CompletableDeferred<Unit>()
        val lider = async { voo.run("k") { entrou.complete(Unit); libera.await(); 5 } }
        esperar(entrou)
        val seguidor = async { voo.run("k") { 9 } }
        advanceUntilIdle()
        seguidor.cancel()
        libera.complete(Unit)
        assertEquals(5, lider.await())
        assertTrue(seguidor.isCancelled)
    }

    @Test
    fun `todos desistem — a requisicao e cancelada`() = runTest {
        val voo = InFlightRequests<String, Int>(escopo)
        val entrou = CompletableDeferred<Unit>()
        val requisicaoCancelada = CompletableDeferred<Unit>()
        val bloco: suspend () -> Int = {
            entrou.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                requisicaoCancelada.complete(Unit)
            }
        }
        val a = async { voo.run("k", bloco) }
        esperar(entrou)
        val b = async { voo.run("k", bloco) }
        advanceUntilIdle()
        a.cancel()
        advanceUntilIdle()
        assertTrue(!requisicaoCancelada.isCompleted, "ainda há um interessado")
        b.cancel()
        esperar(requisicaoCancelada)
        assertEquals(0, voo.inFlightCount())

        // E a chave volta a funcionar do zero.
        assertEquals(3, voo.run("k") { 3 })
    }

    @Test
    fun `dentro de um combine de 3 flows cancelar o lider externo nao derruba o combine`() = runTest {
        val voo = InFlightRequests<String, String>(escopo)
        val entrou = CompletableDeferred<Unit>()
        val libera = CompletableDeferred<Unit>()
        val c = Contador()
        val leitura: suspend () -> String = { c.execucoes++; entrou.complete(Unit); libera.await(); "clientes-p2" }

        // ClientesViewModel: inicia a página 2 (vira o líder)...
        val loadJobDosClientes = launch { voo.run("clientes?page=2", leitura) }
        esperar(entrou)

        // ...LocacoesViewModel combina locações + clientes (mesma página, pega carona) + equipamentos.
        val tela = async {
            combine(
                flowOf("locacoes"),
                flow { emit(voo.run("clientes?page=2", leitura)) },
                flowOf("equipamentos"),
            ) { l, cl, e -> listOf(l, cl, e) }.first()
        }
        advanceUntilIdle()

        // ...e o ClientesViewModel cancela a própria carga para recomeçar.
        loadJobDosClientes.cancel()
        libera.complete(Unit)

        assertEquals(listOf("locacoes", "clientes-p2", "equipamentos"), tela.await())
        assertEquals(1, c.execucoes)
    }

    @Test
    fun `cancelamento de quem chama nao vira o resultado de quem chega depois`() = runTest {
        val voo = InFlightRequests<String, Int>(escopo)
        val entrou = CompletableDeferred<Unit>()
        val a = async { voo.run("k") { entrou.complete(Unit); awaitCancellation() } }
        esperar(entrou)
        a.cancel()
        try { a.await() } catch (_: CancellationException) { }
        // O único interessado saiu: a chamada foi descartada; a próxima executa a sua.
        assertEquals(1, voo.run("k") { 1 })
    }
}
