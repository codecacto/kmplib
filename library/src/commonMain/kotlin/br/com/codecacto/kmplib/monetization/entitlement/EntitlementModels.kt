package br.com.codecacto.kmplib.monetization.entitlement

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Entitlement (direito de acesso) do usuario para um projeto.
 *
 * **Fonte de verdade = admin-api/backlib-quota (server-side).** O cliente apenas LE este
 * objeto e o exibe; NUNCA decide localmente se um recurso esta liberado nem se autopromove.
 *
 * @property plano Identificador do plano vigente (ex.: "free", "pro", "premium").
 * @property features Conjunto de feature-flags liberadas pelo plano. Verifique com [hasFeature].
 * @property validoAte Timestamp ISO-8601 ate quando o entitlement e valido (null = sem expiracao).
 * @property fonte Origem do entitlement (ex.: "store", "abacatepay", "manual", "free").
 */
@Serializable
data class Entitlement(
    val plano: String,
    val features: Set<String> = emptySet(),
    @SerialName("valido_ate") val validoAte: String? = null,
    val fonte: String? = null,
) {
    /** Se a feature [feature] esta liberada por este entitlement. */
    fun hasFeature(feature: String): Boolean = feature in features

    /** Se o usuario esta no plano gratuito (sem assinatura paga). */
    val isFree: Boolean get() = plano.equals(FREE_PLAN, ignoreCase = true)

    companion object {
        const val FREE_PLAN = "free"

        /** Entitlement padrao para usuario nao autenticado / sem assinatura. */
        val FREE: Entitlement = Entitlement(plano = FREE_PLAN, fonte = FREE_PLAN)
    }
}

/**
 * Snapshot de uso de uma feature consumivel (quota), conforme reportado pelo servidor.
 *
 * O enforcement e SEMPRE server-side; este snapshot existe so para exibir "X de Y" e
 * decidir quando mostrar avisos/paywall na UI.
 *
 * @property feature Identificador da feature consumivel.
 * @property contagem Quantidade ja consumida na janela atual.
 * @property limite Limite da janela. **-1 = ilimitado.**
 * @property restante Restante informado pelo servidor (opcional; se ausente, derivado de [remaining]).
 * @property janelaFim Timestamp ISO-8601 do fim da janela de quota (null = sem janela definida).
 */
@Serializable
data class UsageSnapshot(
    val feature: String,
    val contagem: Long = 0L,
    val limite: Long = -1L,
    val restante: Long? = null,
    @SerialName("janela_fim") val janelaFim: String? = null,
) {
    /** Se a feature e ilimitada (limite negativo). */
    val isUnlimited: Boolean get() = limite < 0L

    /**
     * Restante de usos. Prefere o valor do servidor ([restante]) quando presente; senao deriva de
     * [limite] - [contagem] (piso 0). `null` quando ilimitado.
     */
    val remaining: Long?
        get() = when {
            isUnlimited -> null
            restante != null -> restante.coerceAtLeast(0L)
            else -> (limite - contagem).coerceAtLeast(0L)
        }

    /** Se a quota esta esgotada (nao ilimitada e nao ha restante). */
    val isExhausted: Boolean
        get() = !isUnlimited && (remaining ?: 0L) <= 0L

    /**
     * Fracao consumida no intervalo [0f, 1f] para barra de progresso. `0f` quando ilimitada ou
     * com limite zero (nada a representar como proporcao crescente).
     */
    val fraction: Float
        get() {
            if (isUnlimited || limite <= 0L) return 0f
            return (contagem.toFloat() / limite.toFloat()).coerceIn(0f, 1f)
        }
}

/**
 * Plano disponivel para upgrade, conforme o admin-api.
 *
 * @property plano Identificador do plano.
 * @property nome Nome de exibicao (ex.: "Pro mensal").
 * @property preco Preco como **string decimal canonica** ("19.90") — NUNCA Double. Null quando
 *   o preco vier exclusivamente da loja (RevenueCat/AbacatePay).
 * @property moeda Codigo ISO da moeda (ex.: "BRL").
 * @property intervalo Intervalo de cobranca (ex.: "month", "year", "once").
 * @property storeProductId Id do produto na loja para disparar a compra via RevenueCat (opcional).
 * @property destaques Lista de beneficios para exibir no card do paywall.
 */
@Serializable
data class Plan(
    val plano: String,
    val nome: String,
    val preco: String? = null,
    val moeda: String = "BRL",
    val intervalo: String? = null,
    @SerialName("store_product_id") val storeProductId: String? = null,
    val destaques: List<String> = emptyList(),
)
