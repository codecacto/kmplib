package br.com.codecacto.kmplib.ui.screens.paywall

import br.com.codecacto.kmplib.monetization.entitlement.Plan
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackage

/**
 * Converte a oferta de [Plan]s do catalogo do `admin-api` correlacionada com os [PurchasePackage]s da
 * camada de Offerings/Packages do RevenueCat (gold-standard) na lista de [PaywallPlan] do paywall
 * canonico. **Este e o overload preferencial** (compra por `packageId`, nunca por ID cru de produto).
 *
 * Regras (inegociaveis):
 * - **Correlacao por duracao:** cada [Plan] com `durationMonths` casa com o primeiro [PurchasePackage]
 *   de mesma `durationMonths`. Sem Package correspondente, o plano e **OMITIDO** (mesma regra do
 *   "sem preco = omite" — nada de "—" persistente).
 * - **Ordem fixa** Mensal(1) -> Semestral(6) -> Anual(12): ordena por `durationMonths` ASC.
 * - **Preco SEMPRE da loja** (gold-standard): `priceLabel` vem de [PurchasePackage.priceLabel] (ja
 *   formatado pela loja). A lib NUNCA calcula preco.
 * - **`id` do PaywallPlan = [PurchasePackage.packageId]** (chave de selecao/compra via `purchasePackage`).
 * - **Recomendado:** por default a MAIOR duracao entre os EXIBIDOS; o app pode forcar via
 *   [recommendedDurationMonths].
 * - **Seguranca:** planos inativos, free ou sem `durationMonths` sao OMITIDOS.
 * - **`durationLabel`** derivado de `durationMonths` via [durationLabel] (default pt-BR).
 *
 * @param packages pacotes lidos do RevenueCat (`getOfferings()`), com preco ja formatado.
 * @param recommendedDurationMonths forca o plano recomendado por duracao; `null` => maior duracao exibida.
 * @param durationLabel deriva o rotulo de duracao a partir de `durationMonths`; `null` => sem rotulo.
 */
fun List<Plan>.toPaywallPlans(
    packages: List<PurchasePackage>,
    recommendedDurationMonths: Int? = null,
    durationLabel: (durationMonths: Int) -> String? = ::defaultDurationLabel,
): List<PaywallPlan> {
    val resolved = this
        .asSequence()
        .filter { it.ativo && !it.isFree }
        .mapNotNull { plan ->
            val months = plan.durationMonths ?: return@mapNotNull null
            val pkg = packages.firstOrNull { it.durationMonths == months } ?: return@mapNotNull null
            months to PaywallPlan(
                id = pkg.packageId,
                name = plan.nome,
                priceLabel = pkg.priceLabel,
                durationLabel = durationLabel(months),
                highlights = plan.destaques,
                isRecommended = false,
            )
        }
        // Ordem fixa: Mensal -> Semestral -> Anual (durationMonths ASC). Estavel p/ empates.
        .sortedBy { it.first }
        .toList()

    // Default: a MAIOR duracao exibida (ultimo apos ordenar ASC) e a recomendada.
    val recommendedMonths = recommendedDurationMonths ?: resolved.lastOrNull()?.first

    return resolved.map { (months, plan) ->
        plan.copy(isRecommended = months == recommendedMonths)
    }
}

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
 * @deprecated Preferir o overload que recebe `List<PurchasePackage>` (camada Offerings/Packages do
 *   RevenueCat; compra por `packageId`, preco no proprio Package). Mantido para migracao incremental
 *   dos apps que ainda resolvem preco por `storeProductId`.
 * @param priceLabelProvider resolve o preco formatado da loja por `storeProductId`; `null` => omite o plano.
 * @param recommendedStoreProductId forca o plano recomendado; `null` => maior duracao exibida.
 * @param durationLabel deriva o rotulo de duracao a partir de `durationMonths`; `null` => sem rotulo.
 */
@Deprecated(
    "Preferir toPaywallPlans(packages: List<PurchasePackage>) — Offerings/Packages do RevenueCat.",
)
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
