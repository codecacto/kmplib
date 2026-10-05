package br.com.codecacto.kmplib.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Coalescência de leituras idênticas **em voo** (*single-flight*): enquanto uma leitura com a chave
 * K está acontecendo, quem pedir a mesma K espera por ELA em vez de abrir outra requisição — e
 * recebe o mesmo resultado (valor ou a exceção ORIGINAL). Terminada e sem ninguém esperando, a chave
 * sai do mapa: não é cache; o cache com TTL continua sendo o do [RestRepository].
 *
 * ## A requisição roda no [scope] do dono, não na corrotina de quem pediu (2.252.2)
 *
 * Até a 2.252.1 o primeiro chamador executava o bloco **dentro da própria corrotina**. O padrão
 * comum de ViewModel — `loadJob?.cancel()` e recomeçar — cancelava então a leitura de TODO mundo que
 * estava esperando por ela; no LocAki, o `combine` de locações + clientes + equipamentos de outra
 * tela caiu junto ("Parent job is Cancelling") e a tela parou de carregar.
 *
 * Agora:
 * - o bloco roda em `scope.async(start = LAZY)` — escopo próprio do dono, com `SupervisorJob`;
 * - **todo** chamador, inclusive o primeiro, só faz `await()`: cancelar um chamador cancela só a
 *   espera DELE;
 * - a requisição só é cancelada quando o **último** interessado desiste (contagem de referências);
 * - falha real chega a todos como a exceção original, nunca como o cancelamento de outra corrotina.
 */
internal class InFlightRequests<K, V>(private val scope: CoroutineScope) {

    private class Call<V>(val deferred: Deferred<V>) {
        var interessados: Int = 0
    }

    private val mutex = Mutex()
    private val calls = mutableMapOf<K, Call<V>>()

    suspend fun run(key: K, block: suspend () -> V): V {
        val call = mutex.withLock {
            val existente = calls[key]
            // Uma chamada já cancelada (todos desistiram, mas ainda não saiu do mapa) não serve.
            val call = if (existente != null && !existente.deferred.isCancelled) {
                existente
            } else {
                Call(scope.async(start = CoroutineStart.LAZY) { block() }).also { calls[key] = it }
            }
            call.interessados++
            call
        }
        call.deferred.start()
        try {
            return call.deferred.await()
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    call.interessados--
                    if (call.interessados == 0) {
                        if (calls[key] === call) calls.remove(key)
                        // Ninguém mais quer o resultado: a requisição para. Já concluída, é no-op.
                        call.deferred.cancel()
                    }
                }
            }
        }
    }

    /** Quantas chaves estão em voo agora (para teste). */
    internal suspend fun inFlightCount(): Int = mutex.withLock { calls.size }
}
