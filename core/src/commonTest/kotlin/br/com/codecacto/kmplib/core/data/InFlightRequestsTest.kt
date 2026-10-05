package br.com.codecacto.kmplib.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InFlightRequestsTest {

    @Test
    fun `chamadas iguais em voo executam o bloco UMA vez e recebem o mesmo valor`() = runTest {
        val voo = InFlightRequests<String, Int>()
        val libera = CompletableDeferred<Unit>()
        var execucoes = 0
        val chamadas = List(3) {
            async { voo.run("pagina-1") { execucoes++; libera.await(); 42 } }
        }
        yield(); yield()
        libera.complete(Unit)
        assertEquals(listOf(42, 42, 42), chamadas.map { it.await() })
        assertEquals(1, execucoes)
        assertEquals(0, voo.inFlightCount(), "não é cache: a chave sai quando termina")
    }

    @Test
    fun `chaves diferentes nao se misturam`() = runTest {
        val voo = InFlightRequests<String, String>()
        val libera = CompletableDeferred<Unit>()
        var execucoes = 0
        val a = async { voo.run("p1") { execucoes++; libera.await(); "um" } }
        val b = async { voo.run("p2") { execucoes++; libera.await(); "dois" } }
        yield(); yield()
        libera.complete(Unit)
        assertEquals("um", a.await())
        assertEquals("dois", b.await())
        assertEquals(2, execucoes)
    }

    @Test
    fun `depois de terminar a mesma chave executa de novo`() = runTest {
        val voo = InFlightRequests<String, Int>()
        var execucoes = 0
        voo.run("k") { ++execucoes }
        assertEquals(2, voo.run("k") { ++execucoes })
    }

    @Test
    fun `erro do lider chega a quem esperava`() = runTest {
        val voo = InFlightRequests<String, Int>()
        val libera = CompletableDeferred<Unit>()
        val lider = async { runCatching { voo.run("k") { libera.await(); error("falhou") } } }
        val seguidor = async { runCatching { voo.run("k") { 7 } } }
        yield(); yield()
        libera.complete(Unit)
        assertTrue(lider.await().exceptionOrNull() is IllegalStateException)
        assertTrue(seguidor.await().exceptionOrNull() is IllegalStateException, "o seguidor não executa o próprio bloco")
    }

    @Test
    fun `lider cancelado nao cancela quem esperava — um deles assume`() = runTest {
        val voo = InFlightRequests<String, Int>()
        val nuncaLibera = CompletableDeferred<Unit>()
        var execucoes = 0
        val lider = async { voo.run("k") { execucoes++; nuncaLibera.await(); 1 } }
        val seguidor = async { voo.run("k") { execucoes++; 2 } }
        yield(); yield()
        lider.cancel()
        assertFailsWith<CancellationException> { lider.await() }
        assertEquals(2, seguidor.await())
        assertEquals(2, execucoes)
    }

    @Test
    fun `quem esperava e foi cancelado sai sem afetar o lider`() = runTest {
        val voo = InFlightRequests<String, Int>()
        val libera = CompletableDeferred<Unit>()
        val lider = async { voo.run("k") { libera.await(); 5 } }
        val seguidor = async { voo.run("k") { 9 } }
        yield(); yield()
        seguidor.cancel()
        libera.complete(Unit)
        assertEquals(5, lider.await())
        assertTrue(seguidor.isCancelled)
    }
}
