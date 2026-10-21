package br.com.codecacto.kmplib.ui.screens.paywall

import br.com.codecacto.kmplib.monetization.entitlement.Plan
import br.com.codecacto.kmplib.monetization.purchase.FreeTrialPeriod
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackage
import br.com.codecacto.kmplib.monetization.purchase.StoreReplacementMode
import br.com.codecacto.kmplib.monetization.purchase.SubscriptionChangeDirection
import br.com.codecacto.kmplib.monetization.purchase.normalizeStoreProductId
import br.com.codecacto.kmplib.monetization.purchase.resolveUpgradeMode

/**
 * **Um degrau da escada de oferta** (2.316.0) — o `degraus:` do `monetizacao.yaml` (`plan_tiers` do
 * admin-api, backlib-quota 0.155.0): "Até 3 vigias / Até 10 / Até 25", "Essencial / Pro / Marca".
 *
 * Cada degrau vende os ciclos de sempre (Mensal → Semestral → Anual), e o provisionador grava os
 * produtos com a convenção que a lib lê ([PaywallTierKeys]):
 * - package da RevenueCat `<degrau>_<ciclo>` (`v3_mensal`);
 * - `productId` nas lojas `premium_<degrau>_<ciclo>_<app>` (`premium_v3_mensal_rondaki`);
 * - plano do admin-api `<degrau>_<ciclo>` (`v3_mensal`).
 *
 * @param id o id do degrau no admin-api e no manifesto (`v3`, `pro`). Sem `_` no começo ou no fim.
 * @param name o rótulo do chip ("Até 3 vigias") — texto do app, no idioma da tela.
 * @param order a régua do degrau: **maior = superior** (a mesma de `plan_tiers.ordem`). Decide a
 *   ordem dos chips, o "próximo acima" e se uma troca sobe ou desce.
 * @param highlights benefícios do card — iguais nos ciclos do degrau. Vazio = os da oferta central
 *   (`Plan.destaques`) ou o `PaywallConfig.storeHighlights`.
 * @param description frase curta debaixo dos chips quando o degrau está escolhido; `null` = nada.
 */
data class PaywallTier(
    val id: String,
    val name: String,
    val order: Int,
    val highlights: List<String> = emptyList(),
    val description: String? = null,
)

/** Ciclo de cobrança na convenção do provisionador (`TipoPlano.basePlanId`): o sufixo do package. */
enum class PaywallTierCycle(val key: String, val durationMonths: Int) {
    MONTHLY("mensal", 1),
    SEMIANNUAL("semestral", 6),
    YEARLY("anual", 12),
}

/** "Este produto é o degrau X no ciclo Y" — o que [PaywallTierKeys] lê de um id. */
data class PaywallTierRef(val tierId: String, val cycle: PaywallTierCycle)

/**
 * **Leitura dos ids da escada** — a mesma convenção que o provisionador grava (ver [PaywallTier]).
 *
 * A comparação é por id INTEIRO, nunca por prefixo solto: `v3` não casa `v30_mensal`, e o produto
 * `premium_v3_mensal_rondaki` só é do `v3` porque depois de `premium_v3_mensal` vem `_` ou o fim. No
 * Play a RevenueCat escreve o produto do pacote como `assinatura:plano-base` — o `:…` é cortado.
 * Caixa é ignorada (o provisionador grava minúsculo; o app pode declarar `V3`).
 */
object PaywallTierKeys {

    /** `v3_mensal` → (`v3`, mensal). `null` se não for `<degrau>_<ciclo>` de um degrau de [tierIds]. */
    fun fromPackageId(packageId: String, tierIds: Collection<String>): PaywallTierRef? {
        val id = packageId.trim().lowercase()
        return refs(tierIds).firstOrNull { (prefix, _) -> id == prefix }?.second
    }

    /** `premium_v3_mensal_rondaki[:mensal]` → (`v3`, mensal). */
    fun fromProductId(productId: String, tierIds: Collection<String>): PaywallTierRef? {
        val id = normalizeStoreProductId(productId).lowercase()
        if (!id.startsWith(PRODUCT_PREFIX)) return null
        val resto = id.removePrefix(PRODUCT_PREFIX)
        return refs(tierIds).firstOrNull { (prefix, _) ->
            resto == prefix || resto.startsWith(prefix + "_")
        }?.second
    }

    /** O degrau de um pacote da loja: pelo `packageId` e, se não casar, pelo `storeProductId`. */
    fun of(pkg: PurchasePackage, tierIds: Collection<String>): PaywallTierRef? =
        fromPackageId(pkg.packageId, tierIds) ?: fromProductId(pkg.storeProductId, tierIds)

    /** O degrau de um plano da oferta central: pela chave `plano` (`v3_mensal`) ou pelo `storeProductId`. */
    fun of(plan: Plan, tierIds: Collection<String>): PaywallTierRef? =
        fromPackageId(plan.plano, tierIds) ?: plan.storeProductId?.let { fromProductId(it, tierIds) }

    private fun refs(tierIds: Collection<String>): List<Pair<String, PaywallTierRef>> =
        tierIds.flatMap { tier ->
            val t = tier.trim().lowercase()
            if (t.isEmpty()) emptyList() else PaywallTierCycle.entries.map { c -> "${t}_${c.key}" to PaywallTierRef(tier, c) }
        }

    private const val PRODUCT_PREFIX = "premium_"
}

/** Os planos de UM degrau, já na ordem Mensal → Semestral → Anual e com o selo do próprio degrau. */
data class PaywallTierPlans(val tier: PaywallTier, val plans: List<PaywallPlan>)

/**
 * **A vitrine por degrau, só com a loja** — o [toPaywallPlansFromStore] aplicado a cada degrau.
 *
 * Os pacotes são separados por degrau ([PaywallTierKeys]); pacote que não é de degrau nenhum fica de
 * fora (não há como dizer em que chip ele cabe). Dentro de cada degrau valem as regras de sempre: só
 * duração canônica, preço > 0, **ordem Mensal → Semestral → Anual e o selo no de maior duração DAQUELE
 * degrau** — degrau sem semestral leva o selo no anual, degrau só com mensal leva no mensal. Cada
 * [PaywallPlan] sai com [PaywallPlan.tierId]. Degraus na ordem crescente de [PaywallTier.order]; degrau
 * sem plano vendável vem com a lista vazia (quem chama decide escondê-lo e alertar).
 *
 * Os benefícios do card: [PaywallTier.highlights] quando o degrau declara; senão [highlights] por duração.
 */
fun List<PurchasePackage>.toPaywallTierPlansFromStore(
    tiers: List<PaywallTier>,
    recommendedDurationMonths: Int? = null,
    planName: (durationMonths: Int) -> String = ::defaultPlanName,
    durationLabel: (durationMonths: Int) -> String? = ::defaultDurationLabel,
    highlights: (durationMonths: Int) -> List<String> = { emptyList() },
    trialLabel: (FreeTrialPeriod) -> String = ::defaultTrialLabel,
): List<PaywallTierPlans> {
    val ids = tiers.map { it.id }
    val porDegrau = groupBy { PaywallTierKeys.of(it, ids)?.tierId?.lowercase() }
    return tiers.sortedBy { it.order }.map { tier ->
        val pacotes = porDegrau[tier.id.lowercase()].orEmpty()
        val plans = pacotes.toPaywallPlansFromStore(
            recommendedDurationMonths = recommendedDurationMonths,
            planName = planName,
            durationLabel = durationLabel,
            highlights = { months -> tier.highlights.ifEmpty { highlights(months) } },
            trialLabel = trialLabel,
        ).map { it.copy(tierId = tier.id) }
        PaywallTierPlans(tier, plans)
    }
}

/**
 * **A vitrine por degrau com a oferta central** (admin-api ∩ loja) — o [toPaywallPlans] aplicado a cada
 * degrau: os planos centrais do degrau (pela chave `<degrau>_<ciclo>` ou pelo `storeProductId`) cruzados
 * com os pacotes da loja do MESMO degrau. Sem a separação, a correlação por duração casaria o "mensal"
 * do degrau de cima com o pacote mensal do degrau de baixo — o preço errado no card.
 */
fun List<Plan>.toPaywallTierPlans(
    packages: List<PurchasePackage>,
    tiers: List<PaywallTier>,
    recommendedDurationMonths: Int? = null,
    durationLabel: (durationMonths: Int) -> String? = ::defaultDurationLabel,
    trialLabel: (FreeTrialPeriod) -> String = ::defaultTrialLabel,
): List<PaywallTierPlans> {
    val ids = tiers.map { it.id }
    val pacotes = packages.groupBy { PaywallTierKeys.of(it, ids)?.tierId?.lowercase() }
    val planos = groupBy { PaywallTierKeys.of(it, ids)?.tierId?.lowercase() }
    return tiers.sortedBy { it.order }.map { tier ->
        val chave = tier.id.lowercase()
        val plans = planos[chave].orEmpty().toPaywallPlans(
            packages = pacotes[chave].orEmpty(),
            recommendedDurationMonths = recommendedDurationMonths,
            durationLabel = durationLabel,
            trialLabel = trialLabel,
        ).map { plan ->
            plan.copy(tierId = tier.id, highlights = tier.highlights.ifEmpty { plan.highlights })
        }
        PaywallTierPlans(tier, plans)
    }
}

/** Qual degrau a tela abre selecionado. */
enum class PaywallInitialTier {
    /**
     * O **próximo acima** do atual — quem chega ao Premium pelo limite quer mais do que tem. Sem degrau
     * atual (grátis, desconhecido): o mais baixo. No topo: o próprio topo. Default.
     */
    NEXT_ABOVE_CURRENT,

    /** O degrau atual (sem atual: o mais baixo). */
    CURRENT,

    /** Sempre o mais baixo. */
    LOWEST,
}

/**
 * Modos do **Google Play** para a troca de plano (a App Store tem regra própria e ignora isto). Os
 * defaults são a recomendação do Google, e o texto da tela ([PaywallTexts.tierChangeNotice]) os descreve:
 * subir vale na hora; descer ou trocar o ciclo vale na próxima renovação — que é exatamente o que a
 * Apple faz no grupo de assinatura. Mudar um modo aqui exige trocar aquela frase junto.
 *
 * @param upgrade degrau acima. [StoreReplacementMode.CHARGE_PRORATED_PRICE] cai sozinho para
 *   [StoreReplacementMode.WITH_TIME_PRORATION] quando o preço por mês não sobe ([resolveUpgradeMode]).
 * @param downgrade degrau abaixo.
 * @param crossgrade mesmo degrau, outro ciclo.
 * @param unknownCurrent o produto ativo não é de degrau nenhum (plano antigo) — não dá para dizer o
 *   sentido; vai o modo que o Play aceita em qualquer troca.
 */
class PaywallTierChangeModes(
    val upgrade: StoreReplacementMode = StoreReplacementMode.CHARGE_PRORATED_PRICE,
    val downgrade: StoreReplacementMode = StoreReplacementMode.DEFERRED,
    val crossgrade: StoreReplacementMode = StoreReplacementMode.DEFERRED,
    val unknownCurrent: StoreReplacementMode = StoreReplacementMode.WITH_TIME_PRORATION,
)

/**
 * **Escada de degraus no paywall** (2.316.0, GAP-PT-M09) — `PaywallConfig.tiers`.
 *
 * Com ela, o [PaywallViewModel] lê os pacotes da offering (os N degraus × 3 ciclos), mostra os chips
 * dos degraus acima dos planos, abre no degrau certo e, **para quem já assina**, vende a TROCA de plano
 * pela loja (subir/descer/trocar o ciclo, ver [br.com.codecacto.kmplib.monetization.purchase.SubscriptionChange]).
 * Sem ela (`null`, o default) nada muda: a vitrine é a de um degrau só.
 *
 * @param tiers os degraus à venda (ordem e rótulo do app). Degrau declarado que a loja não tem some
 *   da tela e alerta `PaywallSemPlano` — é o catálogo fora do manifesto.
 * @param currentTierId **o degrau atual, vindo de fora** — normalmente do backend (`GET /plano` do
 *   projeto, a central), relido a cada `ON_RESUME` junto com a oferta. `null` (ou falha) = a lib usa o
 *   degrau do produto ativo na loja; sem nenhum dos dois, não há degrau atual.
 * @param initialTier qual degrau a tela abre selecionado. A escolha da pessoa vale até sair da tela.
 * @param changeModes os modos do Play na troca de plano.
 */
class PaywallTierConfig(
    val tiers: List<PaywallTier>,
    val currentTierId: suspend () -> String? = { null },
    val initialTier: PaywallInitialTier = PaywallInitialTier.NEXT_ABOVE_CURRENT,
    val changeModes: PaywallTierChangeModes = PaywallTierChangeModes(),
)

/**
 * Um chip de degrau na tela.
 *
 * @property isCurrent o degrau em que a pessoa está (ganha o rótulo "atual").
 */
data class PaywallTierOption(
    val id: String,
    val name: String,
    val description: String? = null,
    val isCurrent: Boolean = false,
)

/**
 * O degrau que a tela abre selecionado, entre os [available] (ordem crescente). Puro, para teste.
 * Degrau atual que não está à venda conta pela `order`: quem está num degrau que saiu da vitrine abre
 * no primeiro acima dele.
 */
fun initialPaywallTier(
    available: List<PaywallTier>,
    currentTierId: String?,
    rule: PaywallInitialTier,
    allTiers: List<PaywallTier> = available,
): String? {
    val ordered = available.sortedBy { it.order }
    if (ordered.isEmpty()) return null
    val current = currentTierId?.let { id -> allTiers.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) } }
    return when (rule) {
        PaywallInitialTier.LOWEST -> ordered.first().id
        PaywallInitialTier.CURRENT ->
            current?.let { c -> ordered.firstOrNull { it.order >= c.order } ?: ordered.last() }?.id ?: ordered.first().id
        PaywallInitialTier.NEXT_ABOVE_CURRENT ->
            current?.let { c -> ordered.firstOrNull { it.order > c.order } ?: ordered.last() }?.id ?: ordered.first().id
    }
}

/** Sentido e modo do Play de uma troca de plano. `direction == null` = o produto atual não é de degrau. */
data class PaywallTierChangeDecision(
    val direction: SubscriptionChangeDirection?,
    val replacementMode: StoreReplacementMode,
)

/**
 * Decide a troca: compara a `order` do degrau atual com a do alvo (mesmo degrau = outro ciclo) e escolhe
 * o modo do Play em [modes]. Puro, para teste.
 *
 * @param currentPackage o pacote do produto ativo, se estiver na oferta (para o preço por mês do upgrade).
 */
fun decidePaywallTierChange(
    current: PaywallTierRef?,
    target: PaywallTierRef,
    tiers: List<PaywallTier>,
    modes: PaywallTierChangeModes,
    currentPackage: PurchasePackage?,
    targetPackage: PurchasePackage,
): PaywallTierChangeDecision {
    fun order(id: String) = tiers.firstOrNull { it.id.equals(id, ignoreCase = true) }?.order
    val atual = current?.let { order(it.tierId) }
    val alvo = order(target.tierId)
    if (atual == null || alvo == null) return PaywallTierChangeDecision(null, modes.unknownCurrent)
    return when {
        alvo > atual -> PaywallTierChangeDecision(
            SubscriptionChangeDirection.UPGRADE,
            resolveUpgradeMode(modes.upgrade, currentPackage, targetPackage),
        )
        alvo < atual -> PaywallTierChangeDecision(SubscriptionChangeDirection.DOWNGRADE, modes.downgrade)
        else -> PaywallTierChangeDecision(SubscriptionChangeDirection.CROSSGRADE, modes.crossgrade)
    }
}
