package br.com.codecacto.kmplib.ui.screens.paywall

import br.com.codecacto.kmplib.monetization.entitlement.Plan
import br.com.codecacto.kmplib.monetization.entitlement.PlanInterval
import br.com.codecacto.kmplib.monetization.entitlement.isPaidPlan
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackage

/**
 * Um plano pode receber o selo de **"Recomendado"/"Melhor valor"**?
 *
 * Elegibilidade (2.69.0 — espelha o `isHighlightEligible` da weblib 0.58.0):
 * 1. **pago** (`!isFree`) — o Grátis nunca leva selo, nem empatando em duração com o mensal;
 * 2. **preço da loja resolvido** (`priceLabel` não-branco) — nada de destacar card sem preço;
 * 3. **intervalo canônico** (1/6/12 — ver [PlanInterval]) — trimestral, `lifetime` residual
 *    (`durationMonths = 1200`) ou duração desconhecida (`null`) **não concorrem**.
 *
 * Inelegível **não** significa escondido: o plano continua visível e assinável, só não ganha selo e
 * ordena por último.
 */
val PaywallPlan.isHighlightEligible: Boolean
    get() = !isFree && priceLabel.isNotBlank() && PlanInterval.isCanonical(durationMonths)

/**
 * **Fonte única da ordem e do selo do paywall.** Ordena Mensal → Semestral → Anual e deriva o
 * "Recomendado"; qualquer `isRecommended` que venha na entrada (do backend, do app, de um literal
 * hardcoded) é **descartado** antes de recalcular.
 *
 * Regras (inegociáveis):
 * - **Ordem:** canônicos por `durationMonths` ASC (1 → 6 → 12); **não-canônicos por último**, em
 *   ordem estável. A duração desconhecida é `null`, **não** um número grande — nunca deixe um
 *   `Int.MAX_VALUE` sintético concorrer ao selo (foi o bug da weblib).
 * - **Selo = maior duração entre os ELEGÍVEIS** ([isHighlightEligible]). Desligar o anual no admin
 *   migra o selo para o semestral **sozinho** — o selo é derivado, nunca configurado.
 * - **Nenhum elegível ⇒ nenhum selo.** Melhor nenhum selo que o selo errado.
 * - [forcedPlanId] só é honrado se aquele plano for elegível; caso contrário, cai no default (um app
 *   não "rouba" o selo para um plano grátis/desconhecido).
 *
 * Use direto quando o app monta os `PaywallPlan` à mão:
 * ```kotlin
 * val plans = withDerivedHighlight(
 *     listOf(
 *         PaywallPlan(id = "mensal", name = "Mensal", priceLabel = mensalPrice, durationMonths = 1),
 *         PaywallPlan(id = "anual",  name = "Anual",  priceLabel = anualPrice,  durationMonths = 12),
 *     )
 * ) // -> anual.isRecommended == true, sem hardcode
 * ```
 */
fun withDerivedHighlight(
    plans: List<PaywallPlan>,
    forcedPlanId: String? = null,
): List<PaywallPlan> {
    // O selo que veio de fora NUNCA é confiável: é sempre recalculado aqui.
    val cleared = plans.map { it.copy(isRecommended = false) }

    val (canonical, nonCanonical) = cleared.partition { PlanInterval.isCanonical(it.durationMonths) }
    // `!!` seguro: o particionamento garante durationMonths canônico (1/6/12).
    val ordered = canonical.sortedBy { it.durationMonths!! } + nonCanonical

    val forcedIndex = forcedPlanId
        ?.let { id -> ordered.indexOfFirst { it.id == id && it.isHighlightEligible } }
        ?.takeIf { it >= 0 }

    // Como os canônicos vêm ordenados ASC e os não-canônicos (inelegíveis) depois, o ÚLTIMO
    // elegível é o de maior duração. Nenhum elegível => indexOfLast == -1 => nenhum selo.
    val winnerIndex = forcedIndex ?: ordered.indexOfLast { it.isHighlightEligible }

    if (winnerIndex < 0) return ordered

    return ordered.mapIndexed { index, plan ->
        if (index == winnerIndex) plan.copy(isRecommended = true) else plan
    }
}

/**
 * Converte a oferta de [Plan]s do catalogo do `admin-api` correlacionada com os [PurchasePackage]s da
 * camada de Offerings/Packages do RevenueCat (gold-standard) na lista de [PaywallPlan] do paywall
 * canonico. **Este e o overload preferencial** (compra por `packageId`, nunca por ID cru de produto).
 *
 * Regras (inegociaveis):
 * - **Correlacao:** primeiro por **duracao canonica** (`Plan.durationMonths` == `PurchasePackage
 *   .durationMonths`); se nao casar, por **`storeProductId`**. Sem Package correspondente o plano e
 *   **OMITIDO** (regra "sem preco = omite" — nada de "—" persistente); nao ha como vender sem preco.
 * - **A loja manda na duracao:** `durationMonths` do [PurchasePackage] tem prioridade sobre o do
 *   catalogo (a loja e a fonte de verdade do que esta sendo vendido). Um `lifetime` (Package sem
 *   duracao) que traga `durationMonths = 1200` no catalogo vira **duracao desconhecida** — o plano
 *   aparece, ordena por ultimo e **nao rouba o selo do anual**.
 * - **Ordem fixa e selo derivado:** delegados a [withDerivedHighlight] (fonte unica).
 * - **Preco SEMPRE da loja** (gold-standard): `priceLabel` vem de [PurchasePackage.priceLabel] (ja
 *   formatado pela loja). A lib NUNCA calcula preco.
 * - **`id` do PaywallPlan = [PurchasePackage.packageId]** (chave de selecao/compra via `purchasePackage`).
 * - **Seguranca:** planos inativos ou nao-pagos (free / preco zero) sao OMITIDOS.
 * - **`durationLabel`** derivado da duracao resolvida via [durationLabel]; duracao desconhecida => sem rotulo.
 *
 * `Plan.intervalo` (string do backend) e **ignorado** de proposito: ordem, rotulo e selo saem de
 * `durationMonths`. Assim um intervalo nao-canonico jamais e "rebaixado" para mensal (o que faria o
 * paywall mentir o preco, como acontecia na weblib).
 *
 * @param packages pacotes lidos do RevenueCat (`getOfferings()`), com preco ja formatado.
 * @param recommendedDurationMonths forca o plano recomendado por duracao; `null` => maior duracao
 *   elegivel. So e honrado se o plano daquela duracao for elegivel ao selo.
 * @param durationLabel deriva o rotulo de duracao a partir de `durationMonths`; `null` => sem rotulo.
 */
fun List<Plan>.toPaywallPlans(
    packages: List<PurchasePackage>,
    recommendedDurationMonths: Int? = null,
    durationLabel: (durationMonths: Int) -> String? = ::defaultDurationLabel,
): List<PaywallPlan> {
    val resolved = this
        .filter { it.ativo && it.isPaidPlan }
        .mapNotNull { plan ->
            val pkg = plan.matchPackage(packages) ?: return@mapNotNull null
            // Loja > catalogo. Do catalogo so aceitamos duracao CANONICA (bloqueia o 1200 do lifetime).
            val months = pkg.durationMonths
                ?: plan.durationMonths?.takeIf { PlanInterval.isCanonical(it) }
            PaywallPlan(
                id = pkg.packageId,
                name = plan.nome,
                priceLabel = pkg.priceLabel,
                durationLabel = months?.let(durationLabel),
                highlights = plan.destaques,
                durationMonths = months,
                isFree = false,
            )
        }

    val forcedPlanId = recommendedDurationMonths
        ?.let { months -> resolved.firstOrNull { it.durationMonths == months }?.id }

    return withDerivedHighlight(resolved, forcedPlanId)
}

/**
 * **FALLBACK do paywall — monta a vitrine SÓ com os Packages da loja**, quando a oferta central
 * (`admin-api /me/plans`) não pôde ser lida.
 *
 * Por que existe: o paywall canônico é a **interseção** `oferta central × Packages da loja`, e lista
 * vazia de um lado zera a tela **sem erro visível**. A leitura central é a mais frágil das duas (exige
 * identidade Firebase — em 26/07 ela caiu por dois motivos ambientais no mesmo dia, ver docs/16 §A-24
 * do Super 8) enquanto a loja tinha tudo para vender. Sem este caminho, uma falha de identidade vira
 * **receita zero** com a loja funcionando.
 *
 * Não é "inventar oferta": os Packages são provisionados do MESMO `monetizacao.yaml` que alimenta o
 * catálogo central (`Ferramentas/provisioner`, 4 pontas). O que se perde no fallback é a confirmação
 * de **quais planos estão ativos** — e é justamente por isso que ele é mais restritivo:
 *
 * - **Só duração canônica (1/6/12).** Um Package fora do padrão (o `$rc_three_month` residual, um
 *   `lifetime`) é **OMITIDO** — sem a oferta central não há como afirmar que aquilo está à venda, e
 *   trimestral não existe no padrão da fábrica. No caminho normal o não-canônico aparece por último;
 *   aqui, não aparece.
 * - **Preço obrigatório e > 0** (`priceLabel` não-branco e `priceAmountMicros > 0`): Package sem preço
 *   resolvido não é vendável, e preço zero como "premium" seria um bug de cobrança.
 * - **Ordem fixa e selo derivado** por [withDerivedHighlight] (mesma fonte única do caminho normal).
 *
 * **Quem usa isto DEVE alertar** (`PaymentAlertKind.OfertaCentralIndisponivel`): funcionar em fallback
 * é funcionar meio-cego, e o fundador precisa saber no Discord.
 *
 * ```kotlin
 * val planos = if (ofertaCentralOk) offer.toPaywallPlans(packages, durationLabel = ::rotulo)
 *              else packages.toPaywallPlansFromStore(planName = ::nomeI18n, durationLabel = ::rotulo)
 * ```
 *
 * @param recommendedDurationMonths força o selo numa duração; `null` => maior duração elegível.
 * @param planName nome do card por duração canônica (i18n do app). Default: [defaultPlanName].
 * @param durationLabel rótulo de duração; `null` => sem rótulo.
 * @param highlights destaques por duração — no fallback não há `Plan.destaques` do catálogo, então os
 *   benefícios vêm do app (`composeResources`). Default: nenhum.
 */
fun List<PurchasePackage>.toPaywallPlansFromStore(
    recommendedDurationMonths: Int? = null,
    planName: (durationMonths: Int) -> String = ::defaultPlanName,
    durationLabel: (durationMonths: Int) -> String? = ::defaultDurationLabel,
    highlights: (durationMonths: Int) -> List<String> = { emptyList() },
): List<PaywallPlan> {
    val resolved = this.mapNotNull { pkg ->
        val months = pkg.durationMonths?.takeIf { PlanInterval.isCanonical(it) } ?: return@mapNotNull null
        if (pkg.priceLabel.isBlank() || pkg.priceAmountMicros <= 0L) return@mapNotNull null
        PaywallPlan(
            id = pkg.packageId,
            name = planName(months),
            priceLabel = pkg.priceLabel,
            durationLabel = durationLabel(months),
            highlights = highlights(months),
            durationMonths = months,
            isFree = false,
        )
    }

    val forcedPlanId = recommendedDurationMonths
        ?.let { months -> resolved.firstOrNull { it.durationMonths == months }?.id }

    return withDerivedHighlight(resolved, forcedPlanId)
}

/** Correlacao Plan x Package: duracao canonica primeiro, `storeProductId` como fallback. */
private fun Plan.matchPackage(packages: List<PurchasePackage>): PurchasePackage? {
    val byDuration = durationMonths
        ?.takeIf { PlanInterval.isCanonical(it) }
        ?.let { months -> packages.firstOrNull { it.durationMonths == months } }
    if (byDuration != null) return byDuration

    val productId = storeProductId?.takeIf { it.isNotBlank() } ?: return null
    return packages.firstOrNull { it.storeProductId == productId }
}

/**
 * Converte a oferta de [Plan]s do catalogo do `admin-api` (`EntitlementController.plans()`) na lista
 * de [PaywallPlan] consumida pelo paywall canonico. Compartilhado porque >=2 apps (Super 8, LocAki)
 * fazem exatamente este mapeamento (skill `lib-evolution`).
 *
 * Mesmas regras de ordem/selo do overload preferencial (delegadas a [withDerivedHighlight]); muda so
 * a origem do preco e do `id`:
 * - **Preco SEMPRE da loja** (gold-standard): o [priceLabelProvider] resolve o preco ja formatado por
 *   `storeProductId`. Plano sem preco resolvido (`null`) e **OMITIDO** — nada de "—" persistente.
 * - **`id` do PaywallPlan = `storeProductId`** (chave de selecao/compra).
 * - **Seguranca:** planos inativos, nao-pagos (free/zero) ou sem `storeProductId` sao OMITIDOS. Plano
 *   com `durationMonths` desconhecido/nao-canonico **NAO** e omitido: aparece por ultimo, sem selo.
 *
 * @deprecated Preferir o overload que recebe `List<PurchasePackage>` (camada Offerings/Packages do
 *   RevenueCat; compra por `packageId`, preco no proprio Package). Mantido para migracao incremental
 *   dos apps que ainda resolvem preco por `storeProductId`.
 * @param priceLabelProvider resolve o preco formatado da loja por `storeProductId`; `null` => omite o plano.
 * @param recommendedStoreProductId forca o plano recomendado; so honrado se ele for elegivel ao selo.
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
        .filter { it.ativo && it.isPaidPlan }
        .mapNotNull { plan ->
            val storeProductId = plan.storeProductId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val price = priceLabelProvider(storeProductId) ?: return@mapNotNull null
            // Sem Package aqui: a duracao vem do catalogo. Nao-canonica => desconhecida p/ o selo,
            // mas o rotulo ainda diz a verdade ("3 meses"), nunca "mensal".
            val months = plan.durationMonths
            PaywallPlan(
                id = storeProductId,
                name = plan.nome,
                priceLabel = price,
                durationLabel = months?.let(durationLabel),
                highlights = plan.destaques,
                durationMonths = months,
                isFree = false,
            )
        }

    return withDerivedHighlight(resolved, recommendedStoreProductId)
}

/**
 * Rotulo de duracao default (pt-BR) derivado de `durationMonths`. Cobre os 3 tipos do ecossistema
 * (1/6/12) com fallback generico e honesto; SEM "trimestral".
 */
fun defaultDurationLabel(durationMonths: Int): String = when (durationMonths) {
    1 -> "1 mes"
    12 -> "1 ano"
    else -> "$durationMonths meses"
}

/**
 * Nome canonico do plano (pt-BR) derivado de `durationMonths` — os **3 tipos** do ecossistema, nesta
 * grafia: **Mensal / Semestral / Anual**. Sem "Premium" no rotulo do card, sem trimestral.
 *
 * Usado pelo fallback ([toPaywallPlansFromStore]), onde nao existe `Plan.nome` do catalogo central.
 * App com i18n deve passar o proprio lambda (`composeResources`) em vez deste default.
 */
fun defaultPlanName(durationMonths: Int): String = when (PlanInterval.fromDurationMonths(durationMonths)) {
    PlanInterval.Monthly -> "Mensal"
    PlanInterval.SemiAnnual -> "Semestral"
    PlanInterval.Yearly -> "Anual"
    null -> "$durationMonths meses"
}
