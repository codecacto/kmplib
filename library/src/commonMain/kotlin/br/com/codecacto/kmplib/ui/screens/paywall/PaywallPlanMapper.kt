package br.com.codecacto.kmplib.ui.screens.paywall

import br.com.codecacto.kmplib.monetization.entitlement.Plan

/**
 * Converte a oferta de [Plan]s do catalogo do `admin-api` (`EntitlementController.plans()`) na lista
 * de [PaywallPlan] consumida pelo paywall canonico. Compartilhado porque >=2 apps (Super 8, LocAki)
 * fazem exatamente este mapeamento (skill `lib-evolution`).
 *
 * Regras (inegociaveis):
 * - **Ordem fixa** Mensal -> Semestral -> Anual: ordena por [Plan.durationMonths] ASC.
 * - **Preco SEMPRE da loja** (gold-standard): a lib NUNCA calcula preco. O [priceLabelProvider]
 *   resolve o preco ja formatado por `storeProductId` (o app le da RevenueCat/StoreKit/Play Billing).
 *   Plano sem preco resolvido (`null`) e **OMITIDO** — nada de "—" persistente no paywall.
 * - **`id` do PaywallPlan = `storeProductId`** (chave de selecao/compra).
 * - **Recomendado:** por default o plano de MAIOR duracao entre os EXIBIDOS (nao ha flag
 *   `isRecommended` no catalogo); o app pode forcar via [recommendedStoreProductId].
 * - **Seguranca:** planos inativos, sem `storeProductId` ou sem `durationMonths` sao OMITIDOS.
 * - **`durationLabel`** derivado de `durationMonths` via [durationLabel] (default pt-BR).
 *
 * @param priceLabelProvider resolve o preco formatado da loja por `storeProductId`; `null` => omite o plano.
 * @param recommendedStoreProductId forca o plano recomendado; `null` => maior duracao exibida.
 * @param durationLabel deriva o rotulo de duracao a partir de `durationMonths`; `null` => sem rotulo.
 */
fun List<Plan>.toPaywallPlans(
    priceLabelProvider: (storeProductId: String) -> String?,
    recommendedStoreProductId: String? = null,
    durationLabel: (durationMonths: Int) -> String? = ::defaultDurationLabel,
): List<PaywallPlan> {
    val resolved = this
        .asSequence()
        .filter { it.ativo && !it.isFree }
        .mapNotNull { plan ->
            val storeProductId = plan.storeProductId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val months = plan.durationMonths ?: return@mapNotNull null
            val price = priceLabelProvider(storeProductId) ?: return@mapNotNull null
            Triple(months, storeProductId, PaywallPlan(
                id = storeProductId,
                name = plan.nome,
                priceLabel = price,
                durationLabel = durationLabel(months),
                highlights = plan.destaques,
                isRecommended = false,
            ))
        }
        // Ordem fixa: Mensal -> Semestral -> Anual (durationMonths ASC). Estavel p/ empates.
        .sortedBy { it.first }
        .toList()

    // Default: o de MAIOR duracao exibido (ultimo apos ordenar ASC) e o recomendado.
    val recommendedId = recommendedStoreProductId ?: resolved.lastOrNull()?.second

    return resolved.map { (_, storeProductId, plan) ->
        plan.copy(isRecommended = storeProductId == recommendedId)
    }
}

/**
 * Rotulo de duracao default (pt-BR) derivado de `durationMonths`. Cobre os 3 tipos do ecossistema
 * (1/6/12) com fallback generico; SEM "trimestral".
 */
fun defaultDurationLabel(durationMonths: Int): String = when (durationMonths) {
    1 -> "1 mes"
    12 -> "1 ano"
    else -> "$durationMonths meses"
}
