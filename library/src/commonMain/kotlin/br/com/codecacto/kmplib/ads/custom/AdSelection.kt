package br.com.codecacto.kmplib.ads.custom

import kotlin.random.Random

/**
 * Sorteio ponderado entre [CustomAd]s candidatos.
 *
 * Regras:
 *  - Filtra `candidates` por [format], [placementId] (se nao-null) e `imageUrl`
 *    nao vazia.
 *  - Se nenhum sobra, retorna null.
 *  - Se a maior `priority` tem so um anuncio, retorna ele (priority manda).
 *  - Se a maior `priority` empata entre N anuncios, sorteia ponderado pelo
 *    `weight`. Anuncios com weight <= 0 sao excluidos do sorteio (mas se
 *    todos tiverem weight <= 0, retorna o primeiro pra nao mostrar Spacer
 *    quando ha conteudo disponivel).
 *
 * Determinismo de testes via [random].
 */
internal fun selectAd(
    candidates: List<CustomAd>,
    format: String,
    placementId: String?,
    random: Random = Random.Default,
): CustomAd? {
    val eligible = candidates.filter { ad ->
        ad.format == format &&
            (placementId == null || ad.placementId == placementId) &&
            ad.imageUrl.isNotBlank()
    }
    if (eligible.isEmpty()) return null

    val maxPriority = eligible.maxOf { it.priority }
    val topTier = eligible.filter { it.priority == maxPriority }
    if (topTier.size == 1) return topTier.first()

    // Sorteio ponderado no topo do priority tier
    val totalWeight = topTier.sumOf { it.weight.coerceAtLeast(0) }
    if (totalWeight <= 0) return topTier.first()

    var pick = random.nextInt(totalWeight)
    for (ad in topTier) {
        val w = ad.weight.coerceAtLeast(0)
        if (pick < w) return ad
        pick -= w
    }
    return topTier.last() // fallback teorico
}
