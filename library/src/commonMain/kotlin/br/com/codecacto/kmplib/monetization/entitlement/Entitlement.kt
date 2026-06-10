package br.com.codecacto.kmplib.monetization.entitlement

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Entitlement do tenant: o que ele tem direito AGORA (plano + features liberadas).
 *
 * Espelha o contrato do `admin-api` central (`backlib-quota`, doc 03 §2/§3.1) — fonte única de
 * verdade de monetizacao. O cliente apenas LE; nunca se autopromove. Apps Firestore-only tambem
 * leem este shape do admin-api.
 *
 * `validoAte` nulo = vitalicio (lifetime). `features` = conjunto de chaves liberadas pelo plano.
 */
@Serializable
data class Entitlement(
    @SerialName("plano") val plano: String,
    @SerialName("features") val features: Set<String> = emptySet(),
    @SerialName("validoAte") val validoAte: String? = null,
    @SerialName("fonte") val fonte: String = "manual",
    @SerialName("atualizadoEm") val atualizadoEm: String? = null
) {
    /** Plano gratuito padrao quando ainda nao se sabe o entitlement (estado degradado). */
    val isFree: Boolean get() = plano.equals("free", ignoreCase = true)

    /** Verifica se a feature esta liberada pelo plano atual. */
    fun hasFeature(feature: String): Boolean = features.contains(feature)

    companion object {
        /** Entitlement default (free, sem features) usado como estado inicial/offline. */
        val FREE = Entitlement(plano = "free")
    }
}

/**
 * Medidor de consumo de uma feature na janela corrente — alimenta a UI "X de Y".
 *
 * Espelha `getUsage` do admin-api (`{ contagem, limite, restante, janelaFim }`, doc 03 §3.1).
 * `limite == -1` = ilimitado. `restante` derivado quando ausente.
 */
@Serializable
data class UsageSnapshot(
    @SerialName("feature") val feature: String,
    @SerialName("contagem") val contagem: Int = 0,
    @SerialName("limite") val limite: Int = -1,
    @SerialName("restante") val restante: Int? = null,
    @SerialName("janelaFim") val janelaFim: String? = null
) {
    /** Cota ilimitada (plano pago/sem teto). */
    val isUnlimited: Boolean get() = limite < 0

    /** Quantos usos restam. Ilimitado => Int.MAX_VALUE. */
    val remaining: Int
        get() = when {
            isUnlimited -> Int.MAX_VALUE
            restante != null -> restante
            else -> (limite - contagem).coerceAtLeast(0)
        }

    /** Se ja atingiu o limite (apenas indicativo de UX; a autoridade e o servidor). */
    val isExhausted: Boolean get() = !isUnlimited && remaining <= 0

    /** Fracao consumida [0f..1f] para barra de progresso. Ilimitado => 0f. */
    val fraction: Float
        get() = if (isUnlimited || limite == 0) 0f
        else (contagem.toFloat() / limite.toFloat()).coerceIn(0f, 1f)
}

/**
 * Plano do catalogo de um projeto (preco/limites) — alimenta o Paywall.
 *
 * Espelha `getPlans` do admin-api (tabela `plans` keyed por `project_slug`, doc 03 §2/§3.1).
 * Preco e STRING (decimal canonico, ex.: "9.90") para nunca usar Double em dinheiro; nulo p/ free.
 */
@Serializable
data class Plan(
    @SerialName("plano") val plano: String,
    @SerialName("nome") val nome: String,
    @SerialName("preco") val preco: String? = null,
    @SerialName("moeda") val moeda: String = "BRL",
    @SerialName("intervalo") val intervalo: String = "monthly",
    @SerialName("ativo") val ativo: Boolean = true,
    /**
     * ID do produto na loja (RevenueCat) correspondente a este plano. Usado para disparar a compra
     * via PurchaseManager. Nulo no plano free.
     */
    @SerialName("storeProductId") val storeProductId: String? = null,
    /** Beneficios/features destacados na UI do paywall (texto livre). */
    @SerialName("destaques") val destaques: List<String> = emptyList()
) {
    val isFree: Boolean get() = plano.equals("free", ignoreCase = true)
}
