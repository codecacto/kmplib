package br.com.codecacto.kmplib.platform.audience

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume

/**
 * Um pedido de passagem pelo portão de pais, aguardando a resposta do adulto.
 *
 * Quem desenha o portão (o `ParentalGateHost` da `kmplib-ui`) lê [ParentalGate.pending] e responde
 * com [ParentalGate.resolve]. [id] muda a cada pedido — use como chave para sortear um desafio novo.
 */
class ParentalGateRequest internal constructor(
    val id: Long,
    internal val onResult: (Boolean) -> Unit,
)

/**
 * **Portão de pais** (2.259.0) — o desafio que só adulto resolve, exigido pelas lojas em app
 * infantil antes de qualquer saída do app (Apple Guideline 1.3 / Kids; Google Families Policy).
 *
 * Fora do modo infantil ([KmpLibAudience] em [AppAudience.GENERAL]) o portão **não existe**: [guard]
 * executa a ação na hora, na mesma thread, e [awaitPass] devolve `true` sem suspender.
 *
 * No modo infantil:
 * - o pedido fica em [pending] até o host resolver; a ação roda **só** se o adulto acertar;
 * - resposta errada, cancelar ou fechar o portão = a ação **não** roda (e não há erro — é a criança
 *   que tocou);
 * - um segundo pedido enquanto o primeiro está aberto é **negado** (a criança martelando o banner
 *   não empilha portões);
 * - **sem host registrado o pedido é NEGADO** — fecha em segurança e avisa no log.
 *
 * A lib já passa por aqui em todas as saídas dela (ver [KmpLibAudience]). Saída PRÓPRIA do app
 * (um botão que abre o navegador por outro caminho, uma tela "Para os pais"):
 *
 * ```kotlin
 * Button(onClick = { ParentalGate.guard { navController.navigate(ParaOsPais) } }) { … }
 * // ou, em corrotina
 * if (ParentalGate.awaitPass()) abrirAlgo()
 * ```
 */
object ParentalGate {
    private const val TAG = "ParentalGate"

    private val _pending = MutableStateFlow<ParentalGateRequest?>(null)
    private val _hosts = MutableStateFlow<List<Any>>(emptyList())

    @Volatile
    private var nextId = 1L

    /** O portão está ligado (app infantil)? */
    val isRequired: Boolean
        get() = KmpLibAudience.isKids

    /** O pedido aberto, ou `null`. O host desenha o portão enquanto houver um. */
    val pending: StateFlow<ParentalGateRequest?> = _pending.asStateFlow()

    /**
     * O host que DESENHA o portão — o primeiro registrado. Com dois `AppTheme` aninhados, só um
     * mostra o diálogo.
     */
    val primaryHost: StateFlow<Any?>
        get() = primaryHostFlow

    private val primaryHostFlow = MutableStateFlow<Any?>(null)

    /** Executa [action] se o adulto passar pelo portão (ou na hora, fora do modo infantil). */
    fun guard(action: () -> Unit) {
        if (!isRequired) {
            action()
            return
        }
        request { passed -> if (passed) action() }
    }

    /**
     * Abre o portão e entrega o resultado em [onResult] (`true` = adulto passou). Fora do modo
     * infantil responde `true` na hora.
     */
    fun request(onResult: (Boolean) -> Unit) {
        open(onResult)
    }

    /** Abre o pedido; devolve-o se ficou ABERTO (e não respondido na hora). */
    private fun open(onResult: (Boolean) -> Unit): ParentalGateRequest? {
        if (!isRequired) {
            onResult(true)
            return null
        }
        if (_hosts.value.isEmpty()) {
            AppLogger.w(
                TAG,
                "Portão de pais pedido sem host na tela — NEGADO. Use o AppTheme da kmplib ou " +
                    "chame ParentalGateHost() na raiz do app.",
            )
            onResult(false)
            return null
        }
        val request = ParentalGateRequest(nextId++, onResult)
        if (!_pending.compareAndSet(null, request)) {
            // Já há um portão aberto: este toque não empilha outro.
            onResult(false)
            return null
        }
        return request
    }

    /** Versão suspensa de [request]. Cancelar a corrotina fecha o portão aberto por ela. */
    suspend fun awaitPass(): Boolean {
        if (!isRequired) return true
        return suspendCancellableCoroutine { cont ->
            val mine = open { passed -> if (cont.isActive) cont.resume(passed) }
            if (mine != null) {
                cont.invokeOnCancellation { _pending.compareAndSet(mine, null) }
            }
        }
    }

    /** Resposta do host: [passed] = o adulto acertou o desafio. Ignora pedido que não é o aberto. */
    fun resolve(request: ParentalGateRequest, passed: Boolean) {
        if (_pending.compareAndSet(request, null)) {
            request.onResult(passed)
        }
    }

    /** Registra um host (o `ParentalGateHost`). Interno à lib; [token] identifica o host. */
    fun attachHost(token: Any) {
        _hosts.update { if (token in it) it else it + token }
        primaryHostFlow.value = _hosts.value.firstOrNull()
    }

    /**
     * Retira um host. Se era o último e havia portão aberto, o pedido é NEGADO — sem quem o
     * desenhe, ele ficaria pendurado para sempre.
     */
    fun detachHost(token: Any) {
        _hosts.update { it - token }
        primaryHostFlow.value = _hosts.value.firstOrNull()
        if (_hosts.value.isEmpty()) {
            _pending.value?.let { resolve(it, passed = false) }
        }
    }

    /** Só para teste: volta ao estado inicial. */
    internal fun resetForTest() {
        _pending.value = null
        _hosts.value = emptyList()
        primaryHostFlow.value = null
    }
}
