package br.com.codecacto.kmplib.monetization.access

import br.com.codecacto.kmplib.sync.rest.parseServerErrorEnvelope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Por que o servidor recusou o acesso à área logada (2.300.0) — o `code` do **402** que o portão
 * de assinatura da backlib (`backlib-billing`, plugin `SubscriptionGuard`) devolve:
 *
 * ```json
 * { "code": "TRIAL_EXPIRED", "message": "..." }
 * ```
 *
 * Não confundir com o 402 de **cota** (`QUOTA_EXCEEDED`, com `details.feature`), que barra UMA ação
 * e abre o paywall; este barra a conta inteira.
 */
enum class SubscriptionBlockReason(val code: String) {
    /** Teste grátis de 7 dias vencido e nenhum pagamento — "Seu teste grátis terminou". */
    TRIAL_EXPIRED("TRIAL_EXPIRED"),

    /** Assinatura vencida/cancelada (já pagou um dia) — "Sua assinatura não está ativa". */
    SUBSCRIPTION_REQUIRED("SUBSCRIPTION_REQUIRED");

    companion object {
        /** O motivo do [code] do servidor (sem diferenciar caixa); `null` se não for um destes. */
        fun fromCode(code: String?): SubscriptionBlockReason? {
            val normalized = code?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { it.code.equals(normalized, ignoreCase = true) }
        }
    }
}

/**
 * A conta está bloqueada por falta de assinatura (2.300.0).
 *
 * @property reason o motivo — decide o título da tela.
 * @property serverMessage a `message` do corpo, só para log/diagnóstico: a tela usa os textos da lib
 *   (4 idiomas), não a frase do servidor.
 */
data class SubscriptionBlock(
    val reason: SubscriptionBlockReason,
    val serverMessage: String? = null,
)

/** Status HTTP do bloqueio por assinatura. */
const val SUBSCRIPTION_BLOCK_STATUS: Int = 402

/**
 * Lê o bloqueio de uma resposta (2.300.0): só **402** com `code` `TRIAL_EXPIRED` ou
 * `SUBSCRIPTION_REQUIRED` — na raiz (`{code, message}`, o `ErrorResponse` da backlib) ou aninhado em
 * `error` (`{ok:false, error:{code}}`). Qualquer outro status, corpo vazio, não-JSON ou outro `code`
 * (inclusive o 402 de cota) ⇒ `null`. Nunca lança.
 */
fun parseSubscriptionBlock(status: Int, body: String?): SubscriptionBlock? {
    if (status != SUBSCRIPTION_BLOCK_STATUS) return null
    val envelope = parseServerErrorEnvelope(body) ?: return null
    val reason = SubscriptionBlockReason.fromCode(envelope.code) ?: return null
    return SubscriptionBlock(reason = reason, serverMessage = envelope.message)
}

/**
 * **O estado único "a conta está bloqueada por assinatura"** (2.300.0) — o ponto comum entre a rede
 * (que vê o 402) e a tela (que troca a área logada pela `SubscriptionBlockedScreen`).
 *
 * Quem alimenta: o plugin [SubscriptionBlockDetector] do `HttpClient` (opt-in no `createHttpClient`
 * por `HttpClientOptions(subscriptionBlock = SubscriptionBlockState.Shared)`), ou o próprio app com
 * [report] quando recebe o 402 por outro caminho. Quem lê: o `SubscriptionBlockGate` (`kmplib-monetization`).
 *
 * Quem limpa: o detector, quando uma rota do MESMO servidor volta 2xx (o acesso voltou — pagou na
 * web, a central marcou "por contrato"); o gate, no "Sair", na troca de conta e quando a loja
 * confirma o premium. **O app limpa no logout** ([clear]) — senão a próxima conta nasce bloqueada.
 *
 * Não é a autoridade: quem decide é o servidor. Este estado só reflete a última resposta dele, e o
 * app nunca o usa para liberar nada.
 */
class SubscriptionBlockState {
    private val _block = MutableStateFlow<SubscriptionBlock?>(null)

    /** O bloqueio vigente, ou `null` com a conta liberada (ou ainda sem resposta do servidor). */
    val block: StateFlow<SubscriptionBlock?> = _block.asStateFlow()

    /** `true` enquanto houver bloqueio. */
    val isBlocked: Boolean get() = _block.value != null

    /** Registra o bloqueio (a resposta mais recente vence — o motivo pode mudar). */
    fun report(block: SubscriptionBlock) {
        _block.value = block
    }

    /** Registra o bloqueio a partir de uma resposta crua; devolve `true` se era um bloqueio. */
    fun reportIfBlocked(status: Int, body: String?): Boolean {
        val block = parseSubscriptionBlock(status, body) ?: return false
        report(block)
        return true
    }

    /** Tira o bloqueio (logout, troca de conta, premium confirmado). */
    fun clear() {
        _block.value = null
    }

    companion object {
        /**
         * A instância do processo — o default do `SubscriptionBlockGate`. Use a mesma no
         * `HttpClientOptions(subscriptionBlock = …)`; instância própria só em teste.
         */
        val Shared: SubscriptionBlockState = SubscriptionBlockState()
    }
}
