package br.com.codecacto.kmplib.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Coalescência de leituras idênticas **em voo** (*single-flight*, 2.252.0): enquanto uma leitura
 * com a chave K está acontecendo, quem pedir a mesma K espera por ELA em vez de abrir outra
 * requisição — e recebe o mesmo resultado (sucesso ou erro). Terminada, a chave sai do mapa: não é
 * cache; o cache com TTL continua sendo o do [RestRepository].
 *
 * **Sem escopo próprio, com concorrência estruturada:** quem lidera executa o bloco na PRÓPRIA
 * corrotina. Se ele for cancelado (a tela saiu), quem estava esperando não herda o cancelamento: a
 * espera é refeita e um deles passa a liderar. Quem espera e é cancelado sai sozinho, sem afetar o
 * líder.
 */
internal class InFlightRequests<K, V> {

    private val mutex = Mutex()
    private val calls = mutableMapOf<K, CompletableDeferred<V>>()

    suspend fun run(key: K, block: suspend () -> V): V {
        while (true) {
            var lider = false
            val emVoo = mutex.withLock {
                calls[key] ?: CompletableDeferred<V>().also {
                    calls[key] = it
                    lider = true
                }
            }

            if (lider) {
                val valor = try {
                    block()
                } catch (t: Throwable) {
                    release(key, emVoo)
                    emVoo.completeExceptionally(t)
                    throw t
                }
                release(key, emVoo)
                emVoo.complete(valor)
                return valor
            }

            try {
                return emVoo.await()
            } catch (e: CancellationException) {
                // Fui EU o cancelado? Então propaga. Senão quem foi cancelado é o líder: tenta de novo.
                currentCoroutineContext().ensureActive()
            }
        }
    }

    /** Quantas chaves estão em voo agora (para teste). */
    internal suspend fun inFlightCount(): Int = mutex.withLock { calls.size }

    /** Sai do mapa ANTES de completar, para quem refizer a espera não reencontrar o já encerrado. */
    private suspend fun release(key: K, deferred: CompletableDeferred<V>) {
        withContext(NonCancellable) {
            mutex.withLock { if (calls[key] === deferred) calls.remove(key) }
        }
    }
}
