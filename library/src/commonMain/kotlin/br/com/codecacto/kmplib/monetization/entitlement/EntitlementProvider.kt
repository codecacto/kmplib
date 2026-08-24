package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.monetization.MonetizationConfig
import br.com.codecacto.kmplib.monetization.MonetizationManager
import br.com.codecacto.kmplib.monetization.purchase.PurchaseConfig
import br.com.codecacto.kmplib.monetization.purchase.PurchaseErrorCode
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
 * vazio ("em breve", [OfferingsOutcome.Indisponivel]) e recusa compra/restauração — nunca abre premium
 * grátis nem fabrica preço.
 *
 * **Leitura do catálogo: use [EntitlementProvider.loadOfferings]** (2.141.0) sempre que o resultado
 * puder virar alerta. [EntitlementProvider.offerings] devolve lista, e lista vazia não diz se a loja
 * está vazia ou se a leitura falhou.
 */
interface EntitlementProvider {
    /** Estado atual: `true` = premium vigente (loja). */
    val isPremium: StateFlow<Boolean>

    /** Re-sincroniza o estado com a loja (chamar no launch/ao retomar). */
    suspend fun refresh()

    /**
     * Pacotes de assinatura da camada uniforme (Offering configurado → Packages). Stub/sem
     * credenciais ⇒ lista vazia (o paywall mostra "planos em breve", sem preços fabricados).
     *
     * ⚠️ **Lista vazia aqui não tem causa.** "A loja não tem plano" e "não consegui falar com a
     * loja" chegam idênticas — então **nunca decida alerta de pagamento por este retorno**. Use
     * [loadOfferings], que devolve [OfferingsOutcome] e separa os dois casos (2.141.0). Este método
     * continua sendo o caminho certo para quem só **desenha a lista**.
     */
    suspend fun offerings(): List<PurchasePackage>

    /**
     * **Lê o catálogo dizendo o que aconteceu** — [OfferingsOutcome]: `Disponivel` · `Vazio` (a
     * loja respondeu sem pacote) · `Falha` (não deu para ler, com motivo tipado) · `Indisponivel`
     * (build sem billing). É o caminho recomendado desde a 2.141.0 para qualquer app que **alerte**
     * paywall vazio; ver a tabela de decisão em [OfferingsOutcome].
     *
     * **Implementação default (compatibilidade):** delega a [offerings] e mapeia lista vazia →
     * [OfferingsOutcome.Vazio]. Ou seja, um provider próprio do app que só implementa [offerings]
     * segue compilando e **mantém exatamente o comportamento que já tinha** — mas continua sem
     * distinguir falha de catálogo vazio. Para ganhar a distinção, sobrescreva ESTE método
     * (os dois providers da lib sobrescrevem) e faça [offerings] devolver `loadOfferings().pacotes`.
     */
    suspend fun loadOfferings(): OfferingsOutcome = OfferingsOutcome.dePacotes(offerings())

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

    /**
     * Falha real da compra/restauração.
     *
     * @param mensagem texto TÉCNICO do SDK (log). **Não exiba**: use `code.userMessage()`, que diz
     *   ao usuário o que fazer ("revise a forma de pagamento" ≠ "verifique sua internet").
     * @param code motivo TIPADO (2.90.0; default [PurchaseErrorCode.UNKNOWN] para não quebrar quem
     *   já construía `Falha(mensagem)`). Também decide o alerta:
     *   [br.com.codecacto.kmplib.monetization.purchase.isPaymentIncident].
     */
    data class Falha(
        val mensagem: String,
        val code: PurchaseErrorCode = PurchaseErrorCode.UNKNOWN,
    ) : PurchaseOutcome

    /** Billing indisponível (stub sem credenciais/pacote) — a UI mostra "em breve". */
    data object Indisponivel : PurchaseOutcome
}

private const val TAG = "EntitlementProvider"

/**
 * Provider real (RevenueCat via kmplib). Inicializa o [MonetizationManager] — se ainda ninguém o
 * inicializou — na camada Offerings/Packages a partir do [purchaseConfig] fornecido pelo app
 * (chaves/entitlement/offering), e reflete o estado da assinatura.
 *
 * O modo default é [MonetizationConfig.FreemiumQuota] (tier gratuito limitado + paywall, **sem
 * publicidade**), que é a postura real de quem usa esta fachada: ela nasceu para ser o gate de
 * premium de apps freemium com limite de uso (ChamadaFacil, CallRecorder, MundoBandeiras), sempre
 * pareada com `OfflineQuotaGate`. Até a 2.86.0 ela inicializava em `PremiumOnly`, que produz o mesmo
 * comportamento (`shouldShowAds = false`, assinatura ligada) mas **declara que não existe plano
 * gratuito** — mentira para todos esses apps. App realmente pay-to-use passa
 * [MonetizationConfig.PremiumOnly] explicitamente em [monetizationConfig].
 */
class RevenueCatEntitlementProvider(
    purchaseConfig: PurchaseConfig,
    monetizationConfig: MonetizationConfig = MonetizationConfig.FreemiumQuota(purchase = purchaseConfig),
) : EntitlementProvider {

    init {
        if (!MonetizationManager.hasPurchase) {
            MonetizationManager.initialize(monetizationConfig)
        }
    }

    /** Reflete o `isPremium` do MonetizationManager (atualizado pelo estado da assinatura). */
    override val isPremium: StateFlow<Boolean> get() = MonetizationManager.isPremium

    override suspend fun refresh() {
        runCatching { PurchaseManager.repository?.syncSubscriptionState() }
            .onFailure { AppLogger.w(TAG, "Falha ao sincronizar assinatura: ${it.message}") }
    }

    override suspend fun offerings(): List<PurchasePackage> = loadOfferings().pacotes

    /**
     * Até a 2.140.0 este caminho era `repo.getOfferings().getOrDefault(emptyList())`: a falha do
     * `Result` era **engolida** e chegava ao app como catálogo vazio. Agora ela chega como
     * [OfferingsOutcome.Falha], com o motivo tipado do SDK — e repositório ausente (build sem
     * billing) vira [OfferingsOutcome.Indisponivel], não "a loja não tem plano".
     */
    override suspend fun loadOfferings(): OfferingsOutcome {
        val repo = PurchaseManager.repository ?: return OfferingsOutcome.Indisponivel
        val outcome = OfferingsOutcome.deResultado(repo.getOfferings())
        if (outcome is OfferingsOutcome.Falha) {
            AppLogger.w(TAG, "Falha ao ler offerings (${outcome.code.name}): ${outcome.mensagem}")
        }
        return outcome
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
            is PurchaseResult.Error -> PurchaseOutcome.Falha(r.message, r.code)
        }
    }

    override suspend fun restore(): PurchaseOutcome {
        val repo = PurchaseManager.repository ?: return PurchaseOutcome.Indisponivel
        return when (val r = repo.restorePurchases()) {
            is RestoreResult.Success -> PurchaseOutcome.Ativado
            is RestoreResult.NoPurchasesToRestore -> PurchaseOutcome.NadaParaRestaurar
            // Desistir do fluxo da loja não é falha de restauração — vira `Cancelado`, para a UI não
            // mostrar erro e o app não alertar quem só mudou de ideia.
            is RestoreResult.Error ->
                if (r.code == PurchaseErrorCode.USER_CANCELLED) PurchaseOutcome.Cancelado
                else PurchaseOutcome.Falha(r.message, r.code)
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

    /** Sem billing neste build — **nunca** [OfferingsOutcome.Vazio] (a loja não foi consultada). */
    override suspend fun loadOfferings(): OfferingsOutcome = OfferingsOutcome.Indisponivel

    override suspend fun subscriptionInfo(): SubscriptionInfo? = null

    override suspend fun purchasePackage(packageId: String): PurchaseOutcome =
        PurchaseOutcome.Indisponivel

    override suspend fun restore(): PurchaseOutcome = PurchaseOutcome.Indisponivel
}

/**
 * Seleciona o provider conforme a presença de credenciais RevenueCat: [purchaseConfig] `null` ⇒ stub
 * fail-closed; não-nulo ⇒ provider real. O app decide passando (ou não) a config (ex.:
 * `if (RevenueCatConfig.temChave) purchaseConfig else null`).
 *
 * [monetizationConfig] declara a **postura** do app quando este provider for o primeiro a inicializar
 * o [MonetizationManager]; `null` (default) usa [MonetizationConfig.FreemiumQuota] — ver
 * [RevenueCatEntitlementProvider].
 */
fun createEntitlementProvider(
    purchaseConfig: PurchaseConfig?,
    monetizationConfig: MonetizationConfig? = null,
): EntitlementProvider =
    if (purchaseConfig != null) {
        RevenueCatEntitlementProvider(
            purchaseConfig = purchaseConfig,
            monetizationConfig = monetizationConfig
                ?: MonetizationConfig.FreemiumQuota(purchase = purchaseConfig),
        )
    } else {
        StubEntitlementProvider()
    }
