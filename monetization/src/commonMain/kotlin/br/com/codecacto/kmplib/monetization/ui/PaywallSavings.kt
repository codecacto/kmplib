package br.com.codecacto.kmplib.ui.screens.paywall

import br.com.codecacto.kmplib.monetization.purchase.PurchasePackage

/**
 * Quanto um plano longo economiza por mês sobre o mensal, em **pontos percentuais inteiros**
 * (arredondados para baixo — "Economize 16%" nunca promete o que não entrega).
 *
 * Aritmética em `Long` sobre micros por mês; `null` quando não há economia (plano igual ou mais caro)
 * ou quando a conta não tem base (mensal zerado).
 */
fun savingsPercent(monthlyPerMonthMicros: Long, planPerMonthMicros: Long): Int? {
    if (monthlyPerMonthMicros <= 0L || planPerMonthMicros < 0L) return null
    if (planPerMonthMicros >= monthlyPerMonthMicros) return null
    val pct = (monthlyPerMonthMicros - planPerMonthMicros) * 100L / monthlyPerMonthMicros
    return pct.toInt().takeIf { it > 0 }
}

/**
 * Acrescenta aos planos de **mais de um mês** o preço por mês e o selo de economia sobre o mensal —
 * sempre com os **números da loja** (o [PurchasePackage] de mesmo `packageId` que o `PaywallPlan.id`).
 *
 * Regras:
 * - Base = o pacote **mensal** (`durationMonths == 1`) entre os planos exibidos. Sem mensal, ninguém
 *   ganha selo (não há contra o que comparar), mas o preço por mês continua.
 * - Moeda diferente da do mensal não é comparada (a loja pode, em tese, devolver moedas misturadas).
 * - O selo ([badgeLabel]) vai em `PaywallPlan.badgeLabel` — a `PaywallScreen` o mostra **no lugar do
 *   "Recomendado"**, só no card de destaque. `null` do lambda = sem selo.
 * - Plano sem pacote correspondente, ou de 1 mês, passa intacto.
 *
 * O [PaywallViewModel] aplica isto quando `PaywallConfig.savings` está ligado; é público para quem
 * monta a vitrine por conta própria.
 */
fun List<PaywallPlan>.withStoreSavings(
    packages: List<PurchasePackage>,
    badgeLabel: ((percent: Int) -> String?)?,
    pricePerMonthLabel: ((perMonthMicros: Long, currencyCode: String) -> String?)? = null,
): List<PaywallPlan> {
    if (badgeLabel == null && pricePerMonthLabel == null) return this
    val byId = packages.associateBy { it.packageId }

    fun perMonthMicros(pkg: PurchasePackage): Long? {
        val months = pkg.durationMonths?.takeIf { it > 0 } ?: return null
        return pkg.priceAmountMicros.takeIf { it > 0L }?.div(months)
    }

    val monthly = firstNotNullOfOrNull { plan -> byId[plan.id]?.takeIf { it.durationMonths == 1 } }
    val monthlyPerMonth = monthly?.let(::perMonthMicros)

    return map { plan ->
        val pkg = byId[plan.id] ?: return@map plan
        val months = pkg.durationMonths ?: return@map plan
        if (months <= 1) return@map plan
        val perMonth = perMonthMicros(pkg) ?: return@map plan

        val perMonthText = pricePerMonthLabel?.invoke(perMonth, pkg.currencyCode)
        val badge = if (badgeLabel != null && monthly != null && monthlyPerMonth != null &&
            monthly.currencyCode == pkg.currencyCode
        ) {
            savingsPercent(monthlyPerMonth, perMonth)?.let(badgeLabel)
        } else {
            null
        }

        plan.copy(
            pricePerMonthLabel = perMonthText ?: plan.pricePerMonthLabel,
            badgeLabel = badge ?: plan.badgeLabel,
        )
    }
}
