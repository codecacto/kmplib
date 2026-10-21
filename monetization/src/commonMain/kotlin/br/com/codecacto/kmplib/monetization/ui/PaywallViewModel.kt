package br.com.codecacto.kmplib.ui.screens.paywall

import androidx.lifecycle.viewModelScope
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.monetization.MonetizationManager
import br.com.codecacto.kmplib.monetization.alert.PaymentAlertKind
import br.com.codecacto.kmplib.monetization.alert.PaymentAlertReporter
import br.com.codecacto.kmplib.monetization.entitlement.EntitlementProvider
import br.com.codecacto.kmplib.monetization.entitlement.OfferingsOutcome
import br.com.codecacto.kmplib.monetization.entitlement.PlansResult
import br.com.codecacto.kmplib.monetization.entitlement.PurchaseOutcome
import br.com.codecacto.kmplib.monetization.purchase.PurchaseErrorCode
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackage
import br.com.codecacto.kmplib.monetization.purchase.PurchaseStore
import br.com.codecacto.kmplib.monetization.purchase.SubscriptionChange
import br.com.codecacto.kmplib.monetization.purchase.SubscriptionChangeDirection
import br.com.codecacto.kmplib.monetization.purchase.normalizeStoreProductId
import br.com.codecacto.kmplib.monetization.purchase.platformPurchaseStore
import br.com.codecacto.kmplib.monetization.purchase.StoreIdentityStatus
import br.com.codecacto.kmplib.monetization.purchase.TrialEligibility
import br.com.codecacto.kmplib.monetization.purchase.isPaymentIncident
import br.com.codecacto.kmplib.monetization.purchase.userMessage
import br.com.codecacto.kmplib.ui.mvi.BaseViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * **O ViewModel do paywall canônico** (2.224.0) — a lógica em volta da [PaywallScreen] que estava
 * copiada à mão em ~15 apps: ler a oferta, montar os planos, cair para a loja quando a oferta central
 * não vem, comprar, restaurar, abrir a gestão de assinatura, reler ao voltar ao primeiro plano e
 * decidir **quando** alertar o fundador.
 *
 * O app só configura ([PaywallConfig]) e injeta; a tela é o [PaywallHost]:
 * ```kotlin
 * // Koin
 * viewModel {
 *     PaywallViewModel(
 *         entitlementProvider = get(),
 *         config = PaywallConfig(termsUrl = AppConfig.termsUrl, privacyUrl = AppConfig.privacyUrl),
 *         paymentAlerts = PaymentAlertReporter(get(), projeto = "meu-app"),
 *     )
 * }
 * // NavHost
 * composable<Route.Premium> {
 *     PaywallHost(koinViewModel(), onClose = nav::popBackStack, onOpenDeveloper = { nav.navigate(Route.Developer) })
 * }
 * ```
 *
 * ## As regras que moram aqui (e não no app)
 *
 * - **Ordem Mensal → Semestral → Anual e selo no de maior duração** — por [toPaywallPlans] /
 *   [toPaywallPlansFromStore] (fonte única, [withDerivedHighlight]). Nada é decidido aqui.
 * - **Oferta central ilegível ≠ vazia** — ver [PaywallOfferSource.CentralWithStoreFallback].
 * - **Releitura que falha não apaga a vitrine** já desenhada; sem nada na tela, a falha vira a
 *   mensagem da lib ("sem conexão…") e dispensá-la tenta de novo.
 * - **Premium = loja**: `isPremium` do provider **ou** a assinatura ativa lida agora (o `StateFlow`
 *   pode chegar um instante atrasado depois do `refresh`). Quem assina vê "assinatura ativa" +
 *   "Gerenciar assinatura" na mesma tela.
 * - **Compra cancelada não é erro**; restaurar sem compra é mensagem, não erro.
 *
 * ## Régua de alertas (GlitchTip → Discord) — nem falso, nem perdido
 *
 * | Situação | Alerta |
 * |---|---|
 * | Oferta central ilegível (e não é o usuário sem rede) | `OfertaCentralIndisponivel` |
 * | Loja respondeu SEM pacote, ou nenhum plano vendável sobrou | `PaywallSemPlano` |
 * | Leitura da loja falhou por incidente (config, loja, desconhecido) | `LojaIndisponivel` |
 * | Compra / restauração falhou por incidente | `CompraFalhou` / `RestauracaoFalhou` |
 * | Sem rede · cancelamento · cartão recusado · build sem chave (`Indisponivel`) | **nenhum** |
 *
 * O `detalhe` do alerta é técnico (contagens, código), nunca texto do SDK nem dado pessoal.
 *
 * ## Degraus e troca de plano (2.316.0, `PaywallConfig.tiers`)
 *
 * Com uma [PaywallTierConfig], a mesma tela vende uma **escada** (N degraus × Mensal/Semestral/Anual):
 * - os pacotes são separados por degrau pela convenção do provisionador ([PaywallTierKeys]) e cada
 *   degrau tem a sua ordem e o seu selo ([toPaywallTierPlansFromStore]/[toPaywallTierPlans]);
 * - chips de degrau acima dos planos ([PaywallState.tiers]); abre no degrau de
 *   [PaywallTierConfig.initialTier] a partir do **degrau atual vindo de fora**
 *   ([PaywallTierConfig.currentTierId]) ou, sem ele, do produto ativo na loja;
 * - **quem já assina** vê a assinatura ativa E os planos para trocar ([PaywallState.canChangePlan]):
 *   o card do produto ativo diz "Plano atual", e o toque nos outros é a TROCA pela loja
 *   ([EntitlementProvider.changeSubscription]) — Play com o produto antigo e o modo de substituição
 *   ([PaywallTierChangeModes]); App Store no mesmo grupo. Troca que só vale na renovação avisa
 *   ([PaywallMessages.tierChangeScheduled]). Assinatura feita na loja da OUTRA plataforma não troca
 *   aqui ([PaywallMessages.tierChangeOtherStore]) — comprar criaria uma segunda assinatura;
 * - degrau declarado sem nenhum plano vendável na loja some da tela e alerta `PaywallSemPlano`.
 *
 * @param entitlementProvider a loja. App que inicializa o `MonetizationManager` sozinho (depois do
 *   login) usa `PurchaseManagerEntitlementProvider()`; os demais, `createEntitlementProvider(config)`.
 * @param loadMessages textos fora da composição; o default lê o idioma da tela. Teste passa um fixo.
 * @param identityGate a porta da compra (2.233.0): só segue para a loja se a loja está com a conta
 *   logada. O default é `MonetizationManager.ensureIdentityForPurchase()` — que só recusa quando o app
 *   declarou a identidade (`MonetizationManager.bindIdentity`); app sem conta compra como sempre.
 * @param platformStore a loja desta plataforma (2.316.0) — a troca de plano só existe dentro dela.
 *   Default: a do aparelho. Teste passa uma fixa.
 */
class PaywallViewModel(
    private val entitlementProvider: EntitlementProvider,
    private val config: PaywallConfig,
    private val paymentAlerts: PaymentAlertReporter,
    private val loadMessages: suspend () -> PaywallMessages = ::loadPaywallMessages,
    private val identityGate: suspend () -> StoreIdentityStatus = { MonetizationManager.ensureIdentityForPurchase() },
    private val platformStore: () -> PurchaseStore = ::platformPurchaseStore,
) : BaseViewModel<PaywallHostState, PaywallHostAction, PaywallHostEffect>(PaywallHostState()) {

    private var cachedMessages: PaywallMessages? = null
    private var loadJob: Job? = null

    /** A conta já usou o trial na outra ponta: compra sem a fase grátis onde a loja deixa. */
    private var withoutFreeTrial: Boolean = false

    /** Planos por degrau da última leitura bem-sucedida (só com `config.tiers`). */
    private var tierPlans: Map<String, List<PaywallPlan>> = emptyMap()

    /** Pacotes da última leitura, por `packageId` — o produto de cada card na troca de plano. */
    private var lastPackages: Map<String, PurchasePackage> = emptyMap()

    /** A pessoa trocou de degrau nos chips: a releitura não a devolve ao degrau inicial. */
    private var tierPickedByUser: Boolean = false

    private suspend fun messages(): PaywallMessages =
        cachedMessages ?: loadMessages().also { cachedMessages = it }

    override fun onAction(action: PaywallHostAction) {
        when (action) {
            PaywallHostAction.Load -> load(forceReload = false, fromGesture = false)
            PaywallHostAction.Refresh -> load(forceReload = true, fromGesture = true)
            is PaywallHostAction.ShowUsage -> setState { copy(paywall = paywall.copy(usage = action.usage)) }
            is PaywallHostAction.Paywall -> handlePaywall(action.action)
            is PaywallHostAction.SelectTier -> selectTier(action.tierId)
        }
    }

    private fun selectTier(tierId: String) {
        if (currentState.paywall.isPurchasing) return
        val key = tierPlans.keys.firstOrNull { it.equals(tierId, ignoreCase = true) } ?: return
        val plans = tierPlans.getValue(key)
        tierPickedByUser = true
        setState {
            copy(
                paywall = paywall.copy(
                    selectedTierId = key,
                    plans = plans,
                    selectedPlanId = selectedPlanFor(plans, null),
                ),
            )
        }
    }

    // ---------------------------------------------------------------- leitura da oferta

    private fun load(forceReload: Boolean, fromGesture: Boolean) {
        // Voltar da folha de compra da loja também é um ON_RESUME: reler no meio da compra
        // atropelaria o resultado dela.
        if (currentState.paywall.isPurchasing) return
        loadJob?.cancel()
        setState {
            copy(
                isRefreshing = fromGesture,
                // Esqueleto só quando não há nada na tela e não é o gesto (que tem indicador próprio).
                paywall = paywall.copy(
                    isLoadingPlans = !fromGesture && paywall.plans.isEmpty() && !paywall.isPremium,
                ),
            )
        }
        loadJob = viewModelScope.launch {
            try {
                readOffer(forceReload)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "leitura do paywall falhou: ${e.message}")
                val m = messages()
                setState {
                    copy(
                        isRefreshing = false,
                        paywall = paywall.copy(
                            isLoadingPlans = false,
                            error = if (paywall.plans.isEmpty()) PurchaseErrorCode.UNKNOWN.userMessage(m.purchaseErrors) else null,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun readOffer(forceReload: Boolean) {
        val m = messages()
        try {
            entitlementProvider.refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Estado da assinatura desatualizado não impede mostrar a oferta.
            AppLogger.w(TAG, "refresh da loja falhou: ${e.message}")
        }

        val central = config.offerSource as? PaywallOfferSource.CentralWithStoreFallback
        val tierConfig = config.tiers
        val (centralResult, storeRead, trialUsed, outsideTier) = coroutineScope {
            val c = async { central?.readPlans?.invoke(forceReload) }
            val s = async { entitlementProvider.loadOfferings() }
            val t = async { readTrialUsed() }
            val d = async { tierConfig?.let { readCurrentTier(it) } }
            OfferRead(c.await(), s.await(), t.await(), d.await())
        }
        withoutFreeTrial = trialUsed
        val store = if (trialUsed) offeringForUsedTrial(storeRead) else storeRead

        if (tierConfig != null) {
            applyTierOffer(tierConfig, central != null, centralResult, store, trialUsed, outsideTier, m)
            return
        }

        val plans = buildPlans(centralResult, packagesFor(store.pacotes, trialUsed), m)
        reportOffer(central != null, centralResult, store, plans)

        val subscription = entitlementProvider.subscriptionInfo()
        val isPremium = entitlementProvider.isPremium.value || subscription?.isActive == true

        setState {
            // Releitura que FALHOU não apaga a vitrine que já estava na tela.
            val shown = if (store is OfferingsOutcome.Falha && paywall.plans.isNotEmpty()) paywall.plans else plans
            val error = if (store is OfferingsOutcome.Falha && shown.isEmpty()) {
                store.code.userMessage(m.purchaseErrors)
            } else {
                null
            }
            copy(
                isRefreshing = false,
                paywall = paywall.copy(
                    plans = shown,
                    isLoadingPlans = false,
                    isPremium = isPremium,
                    subscription = if (isPremium) subscription else null,
                    error = error,
                    selectedPlanId = selectedPlanFor(shown, paywall.selectedPlanId),
                ),
            )
        }
    }

    // ---------------------------------------------------------------- degraus (2.316.0)

    private data class OfferRead(
        val central: PlansResult?,
        val store: OfferingsOutcome,
        val trialUsed: Boolean,
        val outsideTier: String?,
    )

    /** Degrau atual vindo de fora (backend). Falha de leitura = `null`: vale o da loja. */
    private suspend fun readCurrentTier(tiers: PaywallTierConfig): String? = try {
        tiers.currentTierId()?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.w(TAG, "leitura do degrau atual falhou (${e::class.simpleName}) — vale o da loja")
        null
    }

    /**
     * A vitrine por degrau: planos de cada degrau, chips, degrau selecionado, plano atual marcado e, para
     * quem já assina, a troca. Leitura da loja que FALHOU não apaga o que já estava na tela.
     */
    private suspend fun applyTierOffer(
        tierConfig: PaywallTierConfig,
        usesCentral: Boolean,
        centralResult: PlansResult?,
        store: OfferingsOutcome,
        trialUsed: Boolean,
        outsideTier: String?,
        m: PaywallMessages,
    ) {
        val packages = packagesFor(store.pacotes, trialUsed)
        val byTier = buildTierPlans(tierConfig, centralResult, packages, m)
        val allPlans = byTier.flatMap { it.plans }
        reportOffer(usesCentral, centralResult, store, allPlans)
        reportEmptyTiers(store, byTier, allPlans)

        val subscription = entitlementProvider.subscriptionInfo()
        val isPremium = entitlementProvider.isPremium.value || subscription?.isActive == true
        val activeProduct = subscription?.takeIf { isPremium }?.productId?.takeIf { it.isNotBlank() }
        val ids = tierConfig.tiers.map { it.id }
        val currentTierId = outsideTier
            ?.let { id -> tierConfig.tiers.firstOrNull { it.id.equals(id, ignoreCase = true) }?.id }
            ?: activeProduct?.let { PaywallTierKeys.fromProductId(it, ids)?.tierId }
        val byPackage = packages.associateBy { it.packageId }

        val failedWithShelf = store is OfferingsOutcome.Falha && tierPlans.isNotEmpty()
        if (!failedWithShelf) {
            lastPackages = byPackage
            tierPlans = byTier.filter { it.plans.isNotEmpty() }.associate { tp ->
                tp.tier.id to tp.plans.map { plan ->
                    val current = activeProduct != null && byPackage[plan.id]?.storeProductId
                        ?.let { normalizeStoreProductId(it).equals(normalizeStoreProductId(activeProduct), ignoreCase = true) } == true
                    // Quem já assina TROCA de plano: período grátis não se oferece na troca.
                    plan.copy(isCurrent = current, trial = if (isPremium) null else plan.trial)
                }
            }
        }
        val visible = tierConfig.tiers.filter { t -> tierPlans.containsKey(t.id) }.sortedBy { it.order }
        val previous = currentState.paywall.selectedTierId?.takeIf { tierPickedByUser && tierPlans.containsKey(it) }
        val selected = previous ?: initialPaywallTier(visible, currentTierId, tierConfig.initialTier, tierConfig.tiers)
        val shown = selected?.let { tierPlans[it] }.orEmpty()
        val options = visible.map { t ->
            PaywallTierOption(id = t.id, name = t.name, description = t.description, isCurrent = t.id == currentTierId)
        }

        setState {
            val error = if (store is OfferingsOutcome.Falha && shown.isEmpty()) {
                store.code.userMessage(m.purchaseErrors)
            } else {
                null
            }
            copy(
                isRefreshing = false,
                paywall = paywall.copy(
                    plans = shown,
                    isLoadingPlans = false,
                    isPremium = isPremium,
                    subscription = if (isPremium) subscription else null,
                    error = error,
                    selectedPlanId = selectedPlanFor(shown, paywall.selectedPlanId),
                    tiers = options,
                    selectedTierId = selected,
                    currentTierId = currentTierId,
                    canChangePlan = isPremium && shown.isNotEmpty(),
                ),
            )
        }
    }

    private fun buildTierPlans(
        tierConfig: PaywallTierConfig,
        central: PlansResult?,
        packages: List<PurchasePackage>,
        m: PaywallMessages,
    ): List<PaywallTierPlans> {
        val byTier = if (central is PlansResult.Available) {
            central.plans.toPaywallTierPlans(
                packages = packages,
                tiers = tierConfig.tiers,
                recommendedDurationMonths = config.recommendedDurationMonths,
                durationLabel = m.planLabels::durationLabel,
                trialLabel = m.planLabels::trialLabel,
            )
        } else {
            packages.toPaywallTierPlansFromStore(
                tiers = tierConfig.tiers,
                recommendedDurationMonths = config.recommendedDurationMonths,
                planName = m.planLabels::planName,
                durationLabel = m.planLabels::durationLabel,
                highlights = config.storeHighlights,
                trialLabel = m.planLabels::trialLabel,
            )
        }
        val savings = config.savings ?: return byTier
        return byTier.map { tp ->
            val tierPackages = packages.filter { pkg -> tp.plans.any { it.id == pkg.packageId } }
            tp.copy(
                plans = tp.plans.withStoreSavings(
                    packages = tierPackages,
                    badgeLabel = if (savings.badge) (savings.badgeLabel ?: m::savingsLabel) else null,
                    pricePerMonthLabel = savings.pricePerMonthLabel,
                ),
            )
        }
    }

    /**
     * Degrau declarado sem plano vendável, com a loja respondendo e os OUTROS degraus à venda: o catálogo
     * da loja está fora do manifesto (package ausente). Todos vazios já é o `PaywallSemPlano` geral.
     */
    private fun reportEmptyTiers(store: OfferingsOutcome, byTier: List<PaywallTierPlans>, allPlans: List<PaywallPlan>) {
        if (store !is OfferingsOutcome.Disponivel || allPlans.isEmpty()) return
        val empty = byTier.filter { it.plans.isEmpty() }.map { it.tier.id }
        if (empty.isEmpty()) return
        paymentAlerts.report(
            PaymentAlertKind.PaywallSemPlano,
            detalhe = "degraus-sem-plano=${empty.joinToString(",").take(MAX_DETAIL)} pacotes=${store.pacotes.size}",
        )
    }

    /** O que um toque num plano faz, com degraus: compra normal, troca pela loja, ou nada (outra loja). */
    private sealed interface TierPurchase {
        data object Normal : TierPurchase
        data object OtherStore : TierPurchase
        data class Change(val change: SubscriptionChange, val direction: SubscriptionChangeDirection?) : TierPurchase
    }

    private fun tierPurchaseFor(packageId: String): TierPurchase {
        val tiers = config.tiers ?: return TierPurchase.Normal
        val p = currentState.paywall
        if (!p.isPremium) return TierPurchase.Normal
        val sub = p.subscription ?: return TierPurchase.Normal
        val active = sub.productId?.takeIf { it.isNotBlank() } ?: return TierPurchase.Normal
        when (sub.store) {
            // Promocional, Stripe, Test Store: não há assinatura NESTA loja para substituir.
            PurchaseStore.UNKNOWN -> return TierPurchase.Normal
            null -> Unit
            else -> if (sub.store != platformStore()) return TierPurchase.OtherStore
        }
        val target = lastPackages[packageId]
            ?: return TierPurchase.Change(SubscriptionChange(active, tiers.changeModes.unknownCurrent), null)
        val ids = tiers.tiers.map { it.id }
        val targetRef = PaywallTierKeys.of(target, ids)
            ?: return TierPurchase.Change(SubscriptionChange(active, tiers.changeModes.unknownCurrent), null)
        val currentPackage = lastPackages.values.firstOrNull {
            normalizeStoreProductId(it.storeProductId).equals(normalizeStoreProductId(active), ignoreCase = true)
        }
        val currentRef = currentPackage?.let { PaywallTierKeys.of(it, ids) } ?: PaywallTierKeys.fromProductId(active, ids)
        val decision = decidePaywallTierChange(currentRef, targetRef, tiers.tiers, tiers.changeModes, currentPackage, target)
        return TierPurchase.Change(SubscriptionChange(active, decision.replacementMode), decision.direction)
    }

    // ---------------------------------------------------------------- uma conta = um trial

    /** Trial já usado na outra ponta (backend). Falha de leitura = `false`: a loja decide. */
    private suspend fun readTrialUsed(): Boolean = try {
        config.trialPolicy.alreadyUsed()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Só o tipo: a mensagem de uma falha de rede pode trazer URL/corpo.
        AppLogger.w(TAG, "leitura do trial usado falhou (${e::class.simpleName}) — a loja decide")
        false
    }

    /**
     * Opção B do iOS (docs/43 §6): se há pacote com trial que a loja NÃO deixa pular e o app
     * configurou o offering sem trial, a vitrine vem dele. Offering sem trial vazio ou ilegível volta
     * ao catálogo normal — com o card dizendo a verdade (a Apple dará o trial), nunca com tela vazia.
     */
    private suspend fun offeringForUsedTrial(store: OfferingsOutcome): OfferingsOutcome {
        val alternative = config.trialPolicy.offeringWithoutTrial ?: return store
        val needs = store.pacotes.any { it.offerableFreeTrial != null && !it.canSkipFreeTrial }
        if (!needs) return store
        val other = entitlementProvider.loadOfferings(alternative)
        return if (other is OfferingsOutcome.Disponivel && other.pacotes.isNotEmpty()) {
            other
        } else {
            AppLogger.w(TAG, "offering sem trial '$alternative' indisponível — catálogo normal")
            store
        }
    }

    /**
     * Com o trial já usado, o pacote que a loja deixa comprar SEM a fase grátis (Play) deixa de
     * mostrá-la — a compra vai pelo plano base ([withoutFreeTrial]). O que a loja não deixa pular
     * (iOS sem offering alternativo) fica como a loja disse: é o que vai acontecer na compra.
     */
    private fun packagesFor(packages: List<PurchasePackage>, trialUsed: Boolean): List<PurchasePackage> =
        if (!trialUsed) {
            packages
        } else {
            packages.map { if (it.canSkipFreeTrial) it.copy(trialEligibility = TrialEligibility.INELIGIBLE) else it }
        }

    private fun buildPlans(central: PlansResult?, packages: List<PurchasePackage>, m: PaywallMessages): List<PaywallPlan> {
        val plans = if (central is PlansResult.Available) {
            central.plans.toPaywallPlans(
                packages = packages,
                recommendedDurationMonths = config.recommendedDurationMonths,
                durationLabel = m.planLabels::durationLabel,
                trialLabel = m.planLabels::trialLabel,
            )
        } else {
            // Só loja (configurado) ou FALLBACK (central ilegível): mesma função, mais restritiva.
            packages.toPaywallPlansFromStore(
                recommendedDurationMonths = config.recommendedDurationMonths,
                planName = m.planLabels::planName,
                durationLabel = m.planLabels::durationLabel,
                highlights = config.storeHighlights,
                trialLabel = m.planLabels::trialLabel,
            )
        }
        val savings = config.savings ?: return plans
        return plans.withStoreSavings(
            packages = packages,
            badgeLabel = if (savings.badge) (savings.badgeLabel ?: m::savingsLabel) else null,
            pricePerMonthLabel = savings.pricePerMonthLabel,
        )
    }

    private fun reportOffer(
        usesCentral: Boolean,
        central: PlansResult?,
        store: OfferingsOutcome,
        plans: List<PaywallPlan>,
    ) {
        // Sem rede, a oferta central também não vem — e isso é o usuário, não um incidente.
        val userOffline = store is OfferingsOutcome.Falha && !store.incidente
        if (central is PlansResult.Unavailable && !userOffline) {
            AppLogger.w(TAG, "oferta central indisponível (${central.message ?: "sem detalhe"}) — paywall pela loja")
            paymentAlerts.report(
                PaymentAlertKind.OfertaCentralIndisponivel,
                detalhe = "pacotes=${store.pacotes.size} motivo=${central.message?.take(MAX_DETAIL) ?: "sem detalhe"}",
            )
        }
        val origin = when {
            central is PlansResult.Available -> "central"
            usesCentral -> "loja-fallback"
            else -> "loja"
        }
        when (store) {
            OfferingsOutcome.Vazio ->
                paymentAlerts.report(PaymentAlertKind.PaywallSemPlano, detalhe = "oferta=$origin pacotes=0")
            is OfferingsOutcome.Disponivel -> if (plans.isEmpty()) {
                // Há pacote, mas nada vendável: nenhum de duração canônica com preço, ou a oferta
                // central (lida com sucesso) não tem plano ativo que case com a loja.
                paymentAlerts.report(
                    PaymentAlertKind.PaywallSemPlano,
                    detalhe = "oferta=$origin pacotes=${store.pacotes.size} vendaveis=0",
                )
            }
            is OfferingsOutcome.Falha -> if (store.incidente) {
                paymentAlerts.report(PaymentAlertKind.LojaIndisponivel, detalhe = "code=${store.code}")
            }
            // Build sem chave da loja: defeito de build, pego no release — não é alerta de runtime.
            OfferingsOutcome.Indisponivel -> Unit
        }
    }

    // ---------------------------------------------------------------- ações da tela

    private fun handlePaywall(action: PaywallAction) {
        when (action) {
            is PaywallAction.SelectPlan -> purchase(action.planId)
            PaywallAction.Restore -> restore()
            PaywallAction.Privacy -> sendEffect(PaywallHostEffect.OpenUrl(config.privacyUrl))
            PaywallAction.Terms -> sendEffect(PaywallHostEffect.OpenUrl(config.termsUrl))
            PaywallAction.OpenDeveloper -> sendEffect(PaywallHostEffect.OpenDeveloper)
            PaywallAction.ManageSubscription -> sendEffect(PaywallHostEffect.OpenSubscriptionManagement)
            PaywallAction.Back -> sendEffect(PaywallHostEffect.Close)
            PaywallAction.DismissError -> {
                setState { copy(paywall = paywall.copy(error = null)) }
                // Sem plano na tela (abriu sem rede), dispensar o aviso é tentar de novo.
                if (currentState.paywall.plans.isEmpty() && !currentState.paywall.isPremium) {
                    load(forceReload = false, fromGesture = false)
                }
            }
        }
    }

    /** [packageId] = `PaywallPlan.id` (compra pela camada Offerings/Packages, nunca por id cru). */
    private fun purchase(packageId: String) {
        if (currentState.paywall.isPurchasing) return
        // O card do plano ativo não compra (o botão já vem desligado; isto cobre a tela própria do app).
        if (currentState.paywall.plans.any { it.id == packageId && it.isCurrent }) return
        setState {
            copy(
                paywall = paywall.copy(
                    selectedPlanId = packageId,
                    isPurchasing = true,
                    purchasingPlanId = packageId,
                    error = null,
                ),
            )
        }
        viewModelScope.launch {
            if (!identityAllowsPurchase()) return@launch
            when (val kind = tierPurchaseFor(packageId)) {
                TierPurchase.Normal ->
                    finish(entitlementProvider.purchasePackage(packageId, withoutFreeTrial), PaymentAlertKind.CompraFalhou)
                TierPurchase.OtherStore -> {
                    val m = messages()
                    setState {
                        copy(paywall = paywall.copy(isPurchasing = false, purchasingPlanId = null, error = m.tierChangeOtherStore))
                    }
                }
                is TierPurchase.Change -> finish(
                    entitlementProvider.changeSubscription(packageId, kind.change),
                    PaymentAlertKind.CompraFalhou,
                    scheduled = kind.direction == SubscriptionChangeDirection.DOWNGRADE ||
                        kind.direction == SubscriptionChangeDirection.CROSSGRADE,
                )
            }
        }
    }

    private fun restore() {
        if (currentState.paywall.isPurchasing) return
        setState {
            copy(paywall = paywall.copy(isPurchasing = true, purchasingPlanId = null, error = null))
        }
        viewModelScope.launch {
            if (!identityAllowsPurchase()) return@launch
            finish(entitlementProvider.restore(), PaymentAlertKind.RestauracaoFalhou)
        }
    }

    /**
     * A porta da identidade antes de compra e restauração: com a loja anônima ou na conta anterior do
     * aparelho, a assinatura iria para outra pessoa (ou para ninguém). Recusa com mensagem na tela; o
     * alerta (`CompraSemIdentidade`) já saiu da porta. Falha inesperada da porta também recusa — na
     * dúvida sobre de quem é a compra, não se vende.
     */
    private suspend fun identityAllowsPurchase(): Boolean {
        val status = try {
            identityGate()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "porta da identidade falhou (${e::class.simpleName})")
            StoreIdentityStatus.MISMATCH
        }
        if (status.allowsPurchase) return true
        val m = messages()
        setState {
            copy(paywall = paywall.copy(isPurchasing = false, purchasingPlanId = null, error = m.identityUnconfirmed))
        }
        return false
    }

    /**
     * @param scheduled troca que só vale na renovação (descer de degrau, trocar o ciclo): a loja conclui
     *   com sucesso e a assinatura ativa continua a antiga até a data — a tela avisa, não comemora.
     */
    private suspend fun finish(outcome: PurchaseOutcome, failureKind: PaymentAlertKind, scheduled: Boolean = false) {
        val m = messages()
        setState { copy(paywall = paywall.copy(isPurchasing = false, purchasingPlanId = null)) }
        when (outcome) {
            PurchaseOutcome.Ativado -> {
                val subscription = entitlementProvider.subscriptionInfo()
                setState { copy(paywall = paywall.copy(isPremium = true, subscription = subscription)) }
                if (scheduled) sendEffect(PaywallHostEffect.ShowMessage(m.tierChangeScheduled))
                try {
                    config.onPremiumActivated()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A compra já aconteceu na loja; um cache que não invalidou não a desfaz.
                    AppLogger.w(TAG, "onPremiumActivated falhou: ${e.message}")
                }
                if (config.afterActivation == PaywallAfterActivation.Close) {
                    sendEffect(PaywallHostEffect.Close)
                } else if (config.tiers != null) {
                    // Com degraus, a vitrine muda com a compra: o card do plano ativo e a seção de troca.
                    load(forceReload = false, fromGesture = false)
                }
            }
            PurchaseOutcome.NadaParaRestaurar -> sendEffect(PaywallHostEffect.ShowMessage(m.nothingToRestore))
            PurchaseOutcome.Cancelado -> Unit
            PurchaseOutcome.Indisponivel -> setState { copy(paywall = paywall.copy(error = m.unavailable)) }
            is PurchaseOutcome.Falha -> {
                if (outcome.code.isPaymentIncident) {
                    paymentAlerts.report(failureKind, detalhe = "code=${outcome.code}")
                }
                setState { copy(paywall = paywall.copy(error = outcome.code.userMessage(m.purchaseErrors))) }
            }
        }
    }

    private companion object {
        const val TAG = "PaywallViewModel"
        const val MAX_DETAIL = 120

        /** Mantém a escolha se o plano ainda existe; senão, o de destaque; senão, o primeiro. */
        fun selectedPlanFor(plans: List<PaywallPlan>, current: String?): String? =
            current?.takeIf { id -> plans.any { it.id == id } }
                ?: plans.firstOrNull { it.isRecommended }?.id
                ?: plans.firstOrNull()?.id
    }
}
