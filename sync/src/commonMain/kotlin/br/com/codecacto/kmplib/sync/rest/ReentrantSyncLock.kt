package br.com.codecacto.kmplib.sync.rest

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Trava de sync **reentrante na mesma corrotina** (2.218.0).
 *
 * O `Mutex` do kotlinx não é reentrante: quem já o segura e pede de novo espera a si mesmo para
 * sempre. Isso impedia a pausa da exclusão de conta — ela segura o motor desde antes do `DELETE` e,
 * lá dentro, a limpeza local pede a mesma trava. Aqui a posse viaja no `CoroutineContext` (uma
 * chave **por instância**), e o pedido feito de dentro de quem já segura roda direto.
 *
 * Só a corrotina que adquiriu (e as filhas estruturadas dela) herda a posse; o ciclo lançado no
 * escopo próprio do motor não herda — continua esperando, que é o ponto.
 */
internal class ReentrantSyncLock {
    private val mutex = Mutex()
    private val key = object : CoroutineContext.Key<Held> {}

    private inner class Held : CoroutineContext.Element {
        override val key: CoroutineContext.Key<*> get() = this@ReentrantSyncLock.key
    }

    /** `true` se a corrotina corrente já segura esta trava. */
    suspend fun isHeldByCurrent(): Boolean = currentCoroutineContext()[key] != null

    suspend fun <T> withLock(block: suspend () -> T): T {
        if (isHeldByCurrent()) return block()
        return mutex.withLock { withContext(Held()) { block() } }
    }
}
