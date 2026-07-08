package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.monetization.MonetizationConfig
import br.com.codecacto.kmplib.monetization.MonetizationManager
import br.com.codecacto.kmplib.monetization.purchase.PurchaseConfig
import br.com.codecacto.kmplib.monetization.purchase.PurchaseManager
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackage
import br.com.codecacto.kmplib.monetization.purchase.PurchaseResult
import br.com.codecacto.kmplib.monetization.purchase.RestoreResult
import br.com.codecacto.kmplib.monetization.purchase.SubscriptionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fachada de **assinatura offline** — fonte do estado **premium/Free** do app (mapeia RevenueCat →
 * `isPremium`) e fachada de compra na camada **Offerings/Packages** (gold-standard RevenueCat,
 * kmplib 2.57+). Promovida por ser idêntica em ≥2 apps (ChamadaFacil + CallRecorder).
 *
 * **Enforcement local:** o app avalia o gate freemium localmente usando este `isPremium` (lido
 * **direto do RevenueCat**, validado pela loja — memória `premium-gate-revenuecat-direct`). Apps com
 * quota central combinam com o `EntitlementRepository`/admin-api; apps 100% offline usam só isto.
 *
 * **Compra por `packageId`** (nunca por ID cru de produto): o app lê o offering configurado via
 * [offerings] e compra via [purchasePackage]; os IDs divergentes por loja ficam absorvidos no
 * `Package`. O preço exibido é sempre o [PurchasePackage.priceLabel] já formatado pela loja.
 *
 * **Fail-closed:** sem credenciais RevenueCat, use o [StubEntitlementProvider] (via
 * [createEntitlementProvider] com `purchaseConfig = null`): reporta **sempre Free**, devolve offerings
 * vazio ("em breve") e recusa compra/restauração — nunca abre premium grátis nem fabrica preço.
 */
interface EntitlementProvider {
    /** Estado atual: `true` = premium vigente (loja). */
    val isPremium: StateFlow<Boolean>

    /** Re-sincroniza o estado com a loja (chamar no launch/ao retomar). */
    suspend fun refresh()

    /**
     * Pacotes de assinatura da camada uniforme (Offering configurado → Packages). Stub/sem
     * credenciais ⇒ lista vazia (o paywall mostra "planos em breve", sem preços fabricados).
     */
    suspend fun offerings(): List<PurchasePackage>

    /** Info da assinatura ativa (data de expiração/renovação) — `null`/inativa sem billing. */
    suspend fun subscriptionInfo(): SubscriptionInfo?

    /** Compra o pacote de assinatura [packageId]. Stub/sem pacote ⇒ [PurchaseOutcome.Indisponivel]. */
    suspend fun purchasePackage(packageId: String): PurchaseOutcome

    /** Restaura compras anteriores (novo device/reinstalação). */
    suspend fun restore(): PurchaseOutcome
}

/** Resultado simplificado de compra/restauração para a camada de UI. */
sealed interface PurchaseOutcome {
    /** Premium liberado. */
    data object Ativado : PurchaseOutcome

    /** Usuário cancelou o fluxo da loja. */
    data object Cancelado : PurchaseOutcome

    /** Nada para restaurar. */
    data object NadaParaRestaurar : PurchaseOutcome

    /** Falha (rede/loja). */
    data class Falha(val mensagem: String) : PurchaseOutcome

    /** Billing indisponível (stub sem credenciais/pacote) — a UI mostra "em breve". */
    data object Indisponivel : PurchaseOutcome
}

private const val TAG = "EntitlementProvider"

/**
 * Provider real (RevenueCat via kmplib). Inicializa o [MonetizationManager] em modo `PremiumOnly`
 * (assinatura, sem ads) na camada Offerings/Packages a partir do [purchaseConfig] fornecido pelo app
 * (chaves/entitlement/offering), e reflete o estado da assinatura.
 */
class RevenueCatEntitlementProvider(
    private val purchaseConfig: PurchaseConfig,
) : EntitlementProvider {

    init {
        if (!MonetizationManager.hasPurchase) {
            MonetizationManager.initialize(MonetizationConfig.PremiumOnly(purchase = purchaseConfig))
        }
    }

    /** Reflete o `isPremium` do MonetizationManager (atualizado pelo estado da assinatura). */
    override val isPremium: StateFlow<Boolean> get() = MonetizationManager.isPremium

    override suspend fun refresh() {
        runCatching { PurchaseManager.repository?.syncSubscriptionState() }
            .onFailure { AppLogger.w(TAG, "Falha ao sincronizar assinatura: ${it.message}") }
    }

    override suspend fun offerings(): List<PurchasePackage> {
        val repo = PurchaseManager.repository ?: return emptyList()
        return repo.getOfferings().getOrDefault(emptyList())
    }

    override suspend fun subscriptionInfo(): SubscriptionInfo? {
        val repo = PurchaseManager.repository ?: return null
        return runCatching { repo.getSubscriptionInfo() }.getOrNull()
    }

    override suspend fun purchasePackage(packageId: String): PurchaseOutcome {
        val repo = PurchaseManager.repository ?: return PurchaseOutcome.Indisponivel
        return when (val r = repo.purchasePackage(packageId)) {
            is PurchaseResult.Success -> PurchaseOutcome.Ativado
            is PurchaseResult.Cancelled -> PurchaseOutcome.Cancelado
            is PurchaseResult.Error -> PurchaseOutcome.Falha(r.message)
        }
    }

    override suspend fun restore(): PurchaseOutcome {
        val repo = PurchaseManager.repository ?: return PurchaseOutcome.Indisponivel
        return when (val r = repo.restorePurchases()) {
            is RestoreResult.Success -> PurchaseOutcome.Ativado
            is RestoreResult.NoPurchasesToRestore -> PurchaseOutcome.NadaParaRestaurar
            is RestoreResult.Error -> PurchaseOutcome.Falha(r.message)
        }
    }
}

/**
 * Provider stub (sem credenciais RevenueCat) — **fail-closed**: sempre Free; offerings vazio; compra/
 * restauração indisponíveis. Garante que o app nunca conceda premium grátis nem fabrique preço quando
 * não há billing real.
 */
class StubEntitlementProvider : EntitlementProvider {
    private val _isPremium = MutableStateFlow(false)
    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    override suspend fun refresh() {
        // Sem loja: permanece Free (fail-closed).
    }

    override suspend fun offerings(): List<PurchasePackage> = emptyList()

    override suspend fun subscriptionInfo(): SubscriptionInfo? = null

    override suspend fun purchasePackage(packageId: String): PurchaseOutcome =
        PurchaseOutcome.Indisponivel

    override suspend fun restore(): PurchaseOutcome = PurchaseOutcome.Indisponivel
}

/**
 * Seleciona o provider conforme a presença de credenciais RevenueCat: [purchaseConfig] `null` ⇒ stub
 * fail-closed; não-nulo ⇒ provider real. O app decide passando (ou não) a config (ex.:
 * `if (RevenueCatConfig.temChave) purchaseConfig else null`).
 */
fun createEntitlementProvider(purchaseConfig: PurchaseConfig?): EntitlementProvider =
    if (purchaseConfig != null) RevenueCatEntitlementProvider(purchaseConfig) else StubEntitlementProvider()
