package br.com.codecacto.kmplib.pdf.viewer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Abre um recurso em [context] sem deixá-lo sem dono (2.280.0).
 *
 * `withContext` que termina o bloco depois de a corrotina ser cancelada **lança** na volta e descarta
 * o resultado — o recurso aberto (descritor, renderer, arquivo) fica vivo sem ninguém para fechá-lo.
 * Aqui o resultado é guardado antes da volta e, no cancelamento, [close] o recebe.
 */
internal suspend fun <T : Any> openOwned(
    context: CoroutineContext,
    close: (T) -> Unit,
    open: () -> T,
): T {
    var aberto: T? = null
    try {
        withContext(context) { aberto = open() }
    } catch (e: CancellationException) {
        aberto?.let { runCatching { close(it) } }
        throw e
    }
    return aberto!!
}
