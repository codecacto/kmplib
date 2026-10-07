package br.com.codecacto.kmplib.monetization.purchase

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.audience.ParentalGate
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesDelegate
import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.StoreTransaction
import com.revenuecat.purchases.kmp.models.CacheFetchPolicy
import com.revenuecat.purchases.kmp.models.DiscountPaymentMode
import com.revenuecat.purchases.kmp.models.IntroEligibilityStatus
import com.revenuecat.purchases.kmp.models.OfferPaymentMode
import com.revenuecat.purchases.kmp.models.Period
import com.revenuecat.purchases.kmp.models.PeriodUnit
import com.revenuecat.purchases.kmp.models.Package
import com.revenuecat.purchases.kmp.models.PackageType
import com.revenuecat.purchases.kmp.models.ProductCategory
import com.revenuecat.purchases.kmp.models.PurchasesError
import com.revenuecat.purchases.kmp.models.PurchasesErrorCode
import com.revenuecat.purchases.kmp.models.StoreProduct
import com.revenuecat.purchases.kmp.models.VerificationResult
import kotlin.concurrent.Volatile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

internal class RevenueCatPurchaseRepository(
    private val config: PurchaseConfig
) : PurchaseRepository {

    /**
     * Estado da assinatura + sinal de leitura + geração de identidade (2.250.0). O `isActive = false`
     * inicial é **marcador**, não resposta — ver [SubscriptionStateHolder].
     */
    private val holder = SubscriptionStateHolder()
    override val subscriptionState: Flow<SubscriptionInfo> = holder.subscriptionState
    override val subscriptionReadState: Flow<SubscriptionReadState> = holder.readState

    /** Estado vindo de um `CustomerInfo` do sujeito corrente (compra, restauração, listener). */
    private fun publish(info: SubscriptionInfo) {
        if (!detached) holder.publish(info)
    }

    /** `true` depois de [detach]: este repositório saiu de uso e não publica mais nada. */
    @Volatile
    private var detached = false

    /** O delegate que este repositório registrou, e o que existia antes dele. */
    private var installedDelegate: PurchasesDelegate? = null
    private var previousDelegate: PurchasesDelegate? = null

    /**
     * Tira este repositório de uso (2.251.0) — o `PurchaseManager` chama ao trocar de repositório
     * (`PurchaseTestHooks.instalar`/`limpar`). O listener para de publicar e, se o delegate da
     * RevenueCat ainda for o nosso, volta o anterior.
     */
    internal fun detach() {
        detached = true
        runCatching {
            if (Purchases.sharedInstance.delegate === installedDelegate) {
                Purchases.sharedInstance.delegate = previousDelegate
            }
        }
    }

    init {
        installCustomerInfoListener()
    }

    /**
     * **Listener de atualização do `CustomerInfo`** (2.250.0) — o caminho que a RevenueCat recomenda
     * para manter o estado vivo: o SDK avisa ao voltar ao primeiro plano, depois de transações (inclusive
     * renovação, expiração e compra feita em outro aparelho) e quando o cache muda. Sem ele, uma leitura
     * de abertura que falhou (sem rede, sem cache) deixava o assinante como grátis a sessão inteira.
     *
     * **Encadeia** o delegate que já existir (nenhum app da fábrica define um hoje): quem o registrou
     * antes continua recebendo tudo. App não deve sobrescrever `Purchases.sharedInstance.delegate`
     * depois da inicialização — isso desligaria este listener.
     *
     * **Compra promovida da App Store** (`onPurchasePromoProduct`, só iOS): com um delegate registrado
     * o SDK ADIA a compra até alguém chamar `startPurchase`. Sem delegate anterior, a lib inicia na hora
     * — o mesmo comportamento de quando não havia delegate nenhum — e publica o resultado. Validação
     * pendente no Mac: ver `references/monetization.md` §"Gate premium".
     */
    private fun installCustomerInfoListener() {
        runCatching {
            val previous = Purchases.sharedInstance.delegate
            previousDelegate = previous
            val delegate = object : PurchasesDelegate {
                override fun onCustomerInfoUpdated(customerInfo: CustomerInfo) {
                    publish(customerInfo.toSubscriptionInfo())
                    previous?.onCustomerInfoUpdated(customerInfo)
                }

                override fun onPurchasePromoProduct(
                    product: StoreProduct,
                    startPurchase: (
                        onError: (error: PurchasesError, userCancelled: Boolean) -> Unit,
                        onSuccess: (storeTransaction: StoreTransaction, customerInfo: CustomerInfo) -> Unit,
                    ) -> Unit,
                ) {
                    if (previous != null) {
                        previous.onPurchasePromoProduct(product, startPurchase)
                        return
                    }
                    startPurchase(
                        { error, userCancelled ->
                            if (!userCancelled) AppLogger.w(TAG, "Compra promovida falhou: ${error.message}")
                        },
                        { _, customerInfo -> publish(customerInfo.toSubscriptionInfo()) },
                    )
                }
            }
            installedDelegate = delegate
            Purchases.sharedInstance.delegate = delegate
        }.onFailure { AppLogger.e(TAG, "Listener de CustomerInfo nao registrado: ${it.message}", it) }
    }

    private var cachedProducts: List<StoreProduct> = emptyList()

    /** Cache dos `Package` do offering, por `identifier`, para o [purchasePackage]. */
    private var cachedPackages: Map<String, Package> = emptyMap()

    /** Offering de onde veio o [cachedPackages] (`null` = o configurado). */
    private var cachedOfferingId: String? = null

    override suspend fun isPremium(): Boolean {
        return getSubscriptionInfo().isActive
    }

    override suspend fun getOfferings(): Result<List<PurchasePackage>> = readOffering(null)

    override suspend fun getOfferings(offeringId: String): Result<List<PurchasePackage>> =
        readOffering(offeringId)

    /**
     * Lê o offering [offeringId] (ou o configurado, com fallback para o `current`, quando `null`) e
     * mapeia os pacotes **com o trial e a elegibilidade da loja** (2.231.0).
     *
     * O offering pedido por id NÃO cai no `current`: quem pede o offering "sem trial" e não o acha
     * precisa saber (lista vazia), e não receber em silêncio justamente o catálogo COM trial.
     */
    private suspend fun readOffering(offeringId: String?): Result<List<PurchasePackage>> {
        val lidos: Result<List<Package>> = suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.getOfferings(
                onError = { error ->
                    val code = error.code.toPurchaseErrorCode()
                    AppLogger.e(TAG, "Erro ao buscar offerings [$code]: ${error.message}")
                    continuation.resume(Result.failure(PurchaseException(code, error.message)))
                },
                onSuccess = { offerings ->
                    val offering = if (offeringId != null) {
                        offerings.all[offeringId]
                    } else {
                        // Offering configurado (config.offeringId) com fallback para o `current`.
                        offerings.all[config.offeringId] ?: offerings.current
                    }
                    if (offering == null) {
                        AppLogger.w(TAG, "Offering '${offeringId ?: config.offeringId}' ausente")
                        continuation.resume(Result.success(emptyList()))
                    } else {
                        continuation.resume(Result.success(offering.availablePackages))
                    }
                }
            )
        }
        val packages = lidos.getOrElse { return Result.failure(it) }
        // Offering pedido por id e VAZIO não substitui o cache: o paywall volta ao catálogo normal
        // (que continua na tela), e a compra recarregaria o offering vazio — PRODUCT_NOT_FOUND para
        // um plano que a pessoa está vendo.
        if (offeringId == null || packages.isNotEmpty()) {
            cachedPackages = packages.associateBy { it.identifier }
            cachedOfferingId = offeringId
        }
        val eligibility = trialEligibilityOf(packages.map { it.storeProduct })
        return Result.success(packages.map { it.toPurchasePackage(eligibility[it.storeProduct.id]) })
    }

    /**
     * Elegibilidade ao trial, por id de produto — **só para produtos que TÊM período grátis**.
     *
     * Caminho oficial da RevenueCat: `checkTrialOrIntroPriceEligibility`. No iOS ele pergunta ao
     * StoreKit (por Apple ID e grupo). No Android ele devolve `UNKNOWN` por desenho — o Play só
     * entrega ao app as ofertas para as quais a pessoa é elegível, então a fase grátis presente em
     * `subscriptionOptions` JÁ É a confirmação. Leitura que não volta em [ELIGIBILITY_TIMEOUT_MS]
     * vira `UNKNOWN` (sem promessa) — o catálogo não espera a loja para sempre.
     */
    private suspend fun trialEligibilityOf(products: List<StoreProduct>): Map<String, TrialEligibility> {
        val comTrial = products.filter { it.freeTrialPeriod() != null }
        if (comTrial.isEmpty()) return emptyMap()
        val status: Map<StoreProduct, IntroEligibilityStatus> = withTimeoutOrNull(ELIGIBILITY_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                Purchases.sharedInstance.checkTrialOrIntroPriceEligibility(comTrial) { mapa ->
                    if (continuation.isActive) continuation.resume(mapa)
                }
            }
        } ?: emptyMap<StoreProduct, IntroEligibilityStatus>().also {
            AppLogger.w(TAG, "elegibilidade ao trial nao respondeu — sem promessa de trial")
        }
        val porId = status.entries.associate { (produto, s) -> produto.id to s }
        return comTrial.associate { produto ->
            produto.id to resolveTrialEligibility(
                status = porId[produto.id],
                filteredByStore = produto.subscriptionOptions?.freeTrial != null,
            )
        }
    }

    override suspend fun purchasePackage(packageId: String): PurchaseResult =
        purchasePackage(packageId, withoutFreeTrial = false)

    override suspend fun purchasePackage(packageId: String, withoutFreeTrial: Boolean): PurchaseResult {
        // App infantil (2.259.0): compra só depois do portão de pais; negado = desistência.
        if (!ParentalGate.awaitPass()) return PurchaseResult.Cancelled
        // Recarrega o MESMO offering que estava na tela se o pacote nao esta em cache (ex.: primeira
        // compra sem getOfferings) — o identifier `$rc_monthly` existe nos dois offerings.
        val pkg = cachedPackages[packageId]
            ?: run {
                readOffering(cachedOfferingId)
                cachedPackages[packageId]
            }
            ?: return PurchaseResult.Error(
                message = "Pacote nao encontrado: $packageId",
                code = PurchaseErrorCode.PRODUCT_NOT_FOUND
            )

        // Trial ja usado na outra ponta: no Play, compra o PLANO BASE (sem a fase gratis). Na Apple
        // nao ha opcao sem a oferta no mesmo produto — compra o pacote (docs/43 §6).
        val planoBase = if (withoutFreeTrial) pkg.storeProduct.subscriptionOptions?.basePlan else null

        return suspendCancellableCoroutine { continuation ->
            val onError: (PurchasesError, Boolean) -> Unit = { error, userCancelled ->
                continuation.resume(error.toPurchaseResult(userCancelled))
            }
            val onSuccess: (com.revenuecat.purchases.kmp.models.StoreTransaction, com.revenuecat.purchases.kmp.models.CustomerInfo) -> Unit =
                { _, customerInfo ->
                    val subscriptionInfo = customerInfo.toSubscriptionInfo()
                    publish(subscriptionInfo)
                    continuation.resume(PurchaseResult.Success(subscriptionInfo))
                }
            if (planoBase != null) {
                Purchases.sharedInstance.purchase(
                    subscriptionOption = planoBase,
                    onError = onError,
                    onSuccess = onSuccess,
                )
            } else {
                Purchases.sharedInstance.purchase(
                    packageToPurchase = pkg,
                    onError = onError,
                    onSuccess = onSuccess,
                )
            }
        }
    }

    @Deprecated(
        "Assinaturas usam getOfferings() (Offerings/Packages). getProducts() so p/ consumiveis.",
        ReplaceWith("getOfferings()")
    )
    override suspend fun getProducts(): Result<List<PurchaseProduct>> {
        val productIds = config.products.map { it.id }
        if (productIds.isEmpty()) return Result.success(emptyList())

        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.getProducts(
                productIds = productIds,
                onError = { error ->
                    AppLogger.e(TAG, "Erro ao buscar produtos: ${error.message}")
                    continuation.resume(Result.failure(IllegalStateException(error.message)))
                },
                onSuccess = { products ->
                    cachedProducts = products
                    continuation.resume(Result.success(products.map { it.toPurchaseProduct() }))
                }
            )
        }
    }

    @Deprecated(
        "Assinaturas usam purchasePackage(packageId) via getOfferings() (Offerings/Packages)."
    )
    @Suppress("DEPRECATION")
    override suspend fun purchase(productId: String): PurchaseResult {
        // App infantil (2.259.0): compra só depois do portão de pais; negado = desistência.
        if (!ParentalGate.awaitPass()) return PurchaseResult.Cancelled
        if (cachedProducts.none { it.id == productId }) {
            getProducts()
        }

        val product = cachedProducts.find { it.id == productId }
            ?: return PurchaseResult.Error(
                message = "Produto nao encontrado: $productId",
                code = PurchaseErrorCode.PRODUCT_NOT_FOUND
            )

        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.purchase(
                storeProduct = product,
                onError = { error, userCancelled ->
                    continuation.resume(error.toPurchaseResult(userCancelled))
                },
                onSuccess = { _, customerInfo ->
                    val subscriptionInfo = customerInfo.toSubscriptionInfo()
                    publish(subscriptionInfo)
                    continuation.resume(PurchaseResult.Success(subscriptionInfo))
                }
            )
        }
    }

    @Suppress("DEPRECATION")
    override suspend fun purchaseConsumable(productId: String): ConsumablePurchaseResult {
        // App infantil (2.259.0): compra só depois do portão de pais; negado = desistência.
        if (!ParentalGate.awaitPass()) return ConsumablePurchaseResult.Cancelled
        if (cachedProducts.none { it.id == productId }) {
            getProducts()
        }

        val product = cachedProducts.find { it.id == productId }
            ?: return ConsumablePurchaseResult.Error(
                message = "Produto nao encontrado: $productId",
                code = PurchaseErrorCode.PRODUCT_NOT_FOUND
            )

        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.purchase(
                storeProduct = product,
                onError = { error, userCancelled ->
                    continuation.resume(
                        when (val failure = error.toPurchaseFailure(userCancelled)) {
                            is PurchaseFailure.Cancelled -> ConsumablePurchaseResult.Cancelled
                            is PurchaseFailure.Failed -> ConsumablePurchaseResult.Error(
                                message = failure.message,
                                code = failure.code,
                            )
                        }
                    )
                },
                onSuccess = { storeTransaction, _ ->
                    val transactionId = storeTransaction.transactionId
                    val resolvedProductId =
                        storeTransaction.productIds.firstOrNull() ?: productId

                    if (transactionId.isNullOrBlank()) {
                        continuation.resume(
                            ConsumablePurchaseResult.Error(
                                message = "transacao sem id",
                                code = PurchaseErrorCode.UNKNOWN
                            )
                        )
                    } else {
                        continuation.resume(
                            ConsumablePurchaseResult.Success(
                                transactionId = transactionId,
                                productId = resolvedProductId,
                                store = currentStore()
                            )
                        )
                    }
                }
            )
        }
    }

    override suspend fun restorePurchases(): RestoreResult {
        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.restorePurchases(
                onError = { error ->
                    // `restorePurchases` não informa "cancelou"; o código tipado é a única forma de
                    // não contar uma desistência como falha de restauração (que é alerta).
                    val code = error.code.toPurchaseErrorCode()
                    AppLogger.e(TAG, "Erro ao restaurar compras [$code]: ${error.message}")
                    continuation.resume(RestoreResult.Error(error.message, code))
                },
                onSuccess = { customerInfo ->
                    val subscriptionInfo = customerInfo.toSubscriptionInfo()
                    publish(subscriptionInfo)

                    if (subscriptionInfo.isActive) {
                        continuation.resume(RestoreResult.Success(subscriptionInfo))
                    } else {
                        continuation.resume(RestoreResult.NoPurchasesToRestore)
                    }
                }
            )
        }
    }


    // ---------------------------------------------------------------------------------------------
    // Venda AVULSA — item não-consumível (2.192.0).
    // ---------------------------------------------------------------------------------------------

    /** Cache dos `StoreProduct` NÃO-CONSUMÍVEIS lidos por id, para o [purchaseItem]. */
    private var cachedItems: Map<String, StoreProduct> = emptyMap()

    override suspend fun getStoreItems(productIds: List<String>): StoreItemsOutcome {
        val pedidos = productIds.filter { it.isNotBlank() }.distinct()
        if (pedidos.isEmpty()) return StoreItemsOutcome.Empty(emptyList())

        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.getProducts(
                productIds = pedidos,
                onError = { error ->
                    val code = error.code.toPurchaseErrorCode()
                    AppLogger.e(TAG, "Erro ao ler itens da loja [$code]: ${error.message}")
                    continuation.resume(StoreItemsOutcome.Failed(error.message, code))
                },
                onSuccess = { products ->
                    // `getProducts` devolve assinatura e não-assinatura na mesma lista. Vender uma
                    // assinatura por este caminho criaria cobrança recorrente enquanto o app acha
                    // que vendeu acesso vitalício — por isso ela é descartada aqui e reaparece em
                    // `missingProductIds`, que é onde a fábrica enxerga catálogo mal configurado.
                    val naoConsumiveis = products.filter { it.category == ProductCategory.NON_SUBSCRIPTION }
                    val descartados = products.size - naoConsumiveis.size
                    if (descartados > 0) {
                        AppLogger.w(
                            TAG,
                            "$descartados produto(s) de ASSINATURA ignorados na venda avulsa",
                        )
                    }
                    // Mescla, não substitui: o [purchaseItem] recarrega um id só quando erra o
                    // cache, e substituir ali despejaria o catálogo inteiro lido antes — a próxima
                    // compra pagaria outra ida à loja, e assim por diante.
                    cachedItems = cachedItems + naoConsumiveis.associateBy { it.id }
                    val resultado = StoreItemsOutcome.from(pedidos, naoConsumiveis.map { it.toStoreItem() })
                    if (resultado.missingProductIds.isNotEmpty()) {
                        AppLogger.w(
                            TAG,
                            "loja nao tem ${resultado.missingProductIds.size} produto(s) pedido(s)",
                        )
                    }
                    continuation.resume(resultado)
                }
            )
        }
    }

    override suspend fun purchaseItem(productId: String): ItemPurchaseResult {
        // App infantil (2.259.0): compra só depois do portão de pais; negado = desistência.
        if (!ParentalGate.awaitPass()) return ItemPurchaseResult.Cancelled
        val product = cachedItems[productId]
            ?: run {
                getStoreItems(listOf(productId))
                cachedItems[productId]
            }
            ?: return ItemPurchaseResult.Failed(
                PurchaseErrorCode.PRODUCT_NOT_FOUND,
                "item nao encontrado na loja: $productId",
            )

        if (PurchaseIdentity.isAnonymous(currentAppUserId())) {
            // Não bloqueia (recusar a venda seria pior que vendê-la), mas some do log é como a
            // compra vira irrecuperável: sem App User ID próprio, item avulso não volta em celular
            // novo — é o cenário que a documentação do fornecedor marca em vermelho.
            AppLogger.w(
                TAG,
                "compra avulsa com app user ANONIMO — chame identify() antes de vender",
            )
        }

        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.purchase(
                storeProduct = product,
                onError = { error, userCancelled ->
                    continuation.resume(
                        when (val failure = error.toPurchaseFailure(userCancelled)) {
                            is PurchaseFailure.Cancelled -> ItemPurchaseResult.Cancelled
                            is PurchaseFailure.Failed -> ItemPurchaseResult.fromFailure(
                                code = failure.code,
                                message = failure.message,
                                productId = productId,
                            )
                        }
                    )
                },
                onSuccess = { storeTransaction, customerInfo ->
                    val comprado = OwnedStoreItem(
                        productId = storeTransaction.productIds.firstOrNull() ?: productId,
                        transactionId = storeTransaction.transactionId,
                        purchasedAtMillis = storeTransaction.purchaseTime,
                    )
                    continuation.resume(
                        ItemPurchaseResult.Success(
                            item = comprado,
                            claim = customerInfo.toStorePurchaseClaim().incluindo(comprado),
                        )
                    )
                }
            )
        }
    }

    override suspend fun restoreItems(): ItemRestoreResult {
        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.restorePurchases(
                onError = { error ->
                    val code = error.code.toPurchaseErrorCode()
                    AppLogger.e(TAG, "Erro ao restaurar itens [$code]: ${error.message}")
                    continuation.resume(ItemRestoreResult.Failed(code, error.message))
                },
                onSuccess = { customerInfo ->
                    // Uma restauração, os dois modelos: a loja mostra diálogo do sistema a cada
                    // chamada, então este método também publica a assinatura em vez de obrigar o app
                    // a chamar `restorePurchases()` logo depois e pedir a senha duas vezes.
                    val subscription = customerInfo.toSubscriptionInfo()
                    publish(subscription)
                    val claim = customerInfo.toStorePurchaseClaim()
                    continuation.resume(
                        if (claim.isEmpty && !subscription.isActive) {
                            ItemRestoreResult.NothingToRestore
                        } else {
                            ItemRestoreResult.Restored(claim, subscription)
                        }
                    )
                }
            )
        }
    }

    override suspend fun ownedItems(): Result<StorePurchaseClaim> {
        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.getCustomerInfo(
                // CACHED_OR_FETCHED e não FETCH_CURRENT: este método roda na abertura do app e na
                // tela de catálogo, e o SDK já renova o cache depois de toda compra/restauração —
                // forçar rede aqui seria uma chamada por tela sem informação nova.
                fetchPolicy = CacheFetchPolicy.CACHED_OR_FETCHED,
                onError = { error ->
                    val code = error.code.toPurchaseErrorCode()
                    AppLogger.e(TAG, "Erro ao ler itens possuidos [$code]: ${error.message}")
                    continuation.resume(Result.failure(PurchaseException(code, error.message)))
                },
                onSuccess = { customerInfo ->
                    continuation.resume(Result.success(customerInfo.toStorePurchaseClaim()))
                }
            )
        }
    }

    /**
     * `customerInfo` → a alegação que o app manda ao servidor.
     *
     * Lê `nonSubscriptionTransactions`, que é onde o fornecedor guarda **toda compra única** (a
     * loja não devolve não-consumível em outro lugar). O `appUserId` vai junto porque é por ele que
     * o backend consulta o fornecedor — sem ele o claim não é conciliável, só uma lista de ids.
     */
    private fun com.revenuecat.purchases.kmp.models.CustomerInfo.toStorePurchaseClaim(): StorePurchaseClaim =
        StorePurchaseClaim(
            appUserId = currentAppUserId().orEmpty(),
            store = PurchaseStore.fromWire(currentStore()),
            items = nonSubscriptionTransactions.map { transacao ->
                OwnedStoreItem(
                    productId = transacao.productIdentifier,
                    transactionId = transacao.transactionIdentifier,
                    purchasedAtMillis = transacao.purchaseDateMillis,
                )
            },
            verification = entitlements.verification.toStoreVerification(),
        )

    /**
     * Garante que o item recém-comprado esteja no claim.
     *
     * O `customerInfo` devolvido pela compra normalmente já o traz, mas depender disso é apostar em
     * ordem de propagação do backend do fornecedor — e o item que falta aqui é exatamente o que
     * acabou de ser pago.
     */
    private fun StorePurchaseClaim.incluindo(item: OwnedStoreItem): StorePurchaseClaim =
        if (items.any { it.transactionId == item.transactionId && it.productId == item.productId }) {
            this
        } else {
            copy(items = items + item)
        }

    private fun VerificationResult.toStoreVerification(): StoreVerification = when (this) {
        VerificationResult.NOT_REQUESTED -> StoreVerification.NOT_REQUESTED
        VerificationResult.VERIFIED -> StoreVerification.VERIFIED
        VerificationResult.VERIFIED_ON_DEVICE -> StoreVerification.VERIFIED_ON_DEVICE
        VerificationResult.FAILED -> StoreVerification.FAILED
    }

    private fun StoreProduct.toStoreItem(): StoreItem = StoreItem(
        productId = id,
        title = title,
        description = localizedDescription ?: title,
        priceLabel = price.formatted,
        priceAmountMicros = price.amountMicros,
        currencyCode = price.currencyCode,
    )

    override suspend fun identify(appUserId: String): Result<Unit> {
        val id = when (val check = PurchaseIdentity.check(appUserId)) {
            is AppUserIdCheck.Invalid -> {
                // Contrato quebrado de quem chama: a partir daqui nenhuma compra cai no tenant certo.
                AppLogger.e(TAG, "identify recusado: ${check.reason}", null)
                return identityFailure(PurchaseIdentityError.INVALID_APP_USER_ID, check.reason)
            }

            is AppUserIdCheck.Valid -> check.appUserId
        }
        if (PurchaseIdentity.looksLikePersonalData(id)) {
            // Não bloqueia (sem identidade a compra iria para o tenant errado, que é pior), mas o id
            // trafega para webhook/dashboard de terceiro: dado pessoal aqui é vazamento evitável.
            AppLogger.w(TAG, "appUserId parece dado pessoal — use um id opaco e estavel (LGPD)")
        }
        if (!Purchases.isConfigured) {
            AppLogger.w(TAG, "identify ignorado: RevenueCat nao configurado")
            return identityFailure(PurchaseIdentityError.NOT_CONFIGURED, "RevenueCat nao configurado")
        }
        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.logIn(
                newAppUserID = id,
                onError = { error ->
                    AppLogger.e(TAG, "Erro ao identificar app user: ${error.message}", null)
                    continuation.resume(
                        identityFailure(error.code.toIdentityError(), error.message)
                    )
                },
                onSuccess = { customerInfo, created ->
                    onIdentityChanged(customerInfo)
                    AppLogger.d(TAG, "App user identificado no RevenueCat (novo=$created)")
                    continuation.resume(Result.success(Unit))
                }
            )
        }
    }

    override suspend fun resetIdentity(): Result<Unit> {
        if (!Purchases.isConfigured) return Result.success(Unit)
        // Anonimizar quem já é anônimo é o estado desejado — o SDK devolveria
        // `LogOutWithAnonymousUserError`, um falso incidente de pagamento no logout de todo usuário
        // que nunca chegou a ser identificado.
        if (Purchases.sharedInstance.isAnonymous) {
            AppLogger.d(TAG, "resetIdentity no-op: app user ja anonimo")
            return Result.success(Unit)
        }
        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.logOut(
                onError = { error ->
                    AppLogger.w(TAG, "Erro ao anonimizar app user: ${error.message}")
                    continuation.resume(
                        identityFailure(error.code.toIdentityError(), error.message)
                    )
                },
                onSuccess = { customerInfo ->
                    onIdentityChanged(customerInfo)
                    continuation.resume(Result.success(Unit))
                }
            )
        }
    }

    override fun currentAppUserId(): String? =
        if (Purchases.isConfigured) Purchases.sharedInstance.appUserID else null

    /**
     * Efeito colateral obrigatório de toda troca de sujeito (login/logout na loja):
     *
     * 1. **derruba o catálogo em cache** — a oferta do RevenueCat pode ser personalizada por app user
     *    (Targeting/Experiments), e cada `Package`/`StoreProduct` carrega o contexto de offering que
     *    atribui a compra; comprar um objeto buscado para o sujeito anterior atribui a receita errado;
     * 2. **republica o entitlement** do novo sujeito, para a UI não continuar mostrando o premium de
     *    quem saiu (nem esconder o de quem entrou).
     */
    private fun onIdentityChanged(
        customerInfo: com.revenuecat.purchases.kmp.models.CustomerInfo
    ) {
        cachedPackages = emptyMap()
        cachedProducts = emptyList()
        cachedItems = emptyMap()
        holder.identityChanged(customerInfo.toSubscriptionInfo())
    }

    private fun identityFailure(reason: PurchaseIdentityError, message: String): Result<Unit> =
        Result.failure(PurchaseIdentityException(reason, message))

    /**
     * Mapeia o código TIPADO do SDK (não a mensagem, que é localizada) para o motivo da lib. O caller
     * usa isso para decidir o que vira alerta de pagamento e o que é só transitório.
     */
    private fun PurchasesErrorCode.toIdentityError(): PurchaseIdentityError = when (this) {
        PurchasesErrorCode.NetworkError,
        PurchasesErrorCode.OfflineConnectionError -> PurchaseIdentityError.NETWORK

        PurchasesErrorCode.InvalidAppUserIdError -> PurchaseIdentityError.INVALID_APP_USER_ID

        PurchasesErrorCode.ConfigurationError,
        PurchasesErrorCode.InvalidCredentialsError -> PurchaseIdentityError.NOT_CONFIGURED

        PurchasesErrorCode.StoreProblemError,
        PurchasesErrorCode.UnknownBackendError,
        PurchasesErrorCode.UnexpectedBackendResponseError -> PurchaseIdentityError.STORE

        else -> PurchaseIdentityError.UNKNOWN
    }

    override suspend fun getSubscriptionInfo(): SubscriptionInfo =
        fetchSubscriptionInfo(CacheFetchPolicy.CACHED_OR_FETCHED) ?: SubscriptionInfo(isActive = false)

    /**
     * Lê o `CustomerInfo` com [policy] — `null` quando a leitura falha, para quem chama separar "não é
     * assinante" de "não consegui ler".
     */
    private suspend fun fetchSubscriptionInfo(policy: CacheFetchPolicy): SubscriptionInfo? =
        suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.getCustomerInfo(
                fetchPolicy = policy,
                onError = { error ->
                    AppLogger.e(TAG, "Erro ao buscar subscription info: ${error.message}")
                    continuation.resume(null)
                },
                onSuccess = { customerInfo ->
                    continuation.resume(customerInfo.toSubscriptionInfo())
                }
            )
        }

    /**
     * Lê e publica — **descartando** a resposta se o sujeito mudou enquanto ela estava em voo (2.250.0:
     * a leitura de boot que chegasse depois de um `logIn` publicava o premium do sujeito anterior).
     * Falha não rebaixa ninguém: preserva o último estado lido, ou marca `FAILED` se nada foi lido.
     */
    private suspend fun readAndPublish(policy: CacheFetchPolicy) {
        val token = holder.beginRead()
        val info = fetchSubscriptionInfo(policy)
        val applied = holder.completeRead(token, info)
        when {
            info == null -> AppLogger.w(TAG, "Subscription state NAO sincronizado: mantendo o ultimo estado lido")
            !applied -> AppLogger.d(TAG, "Leitura descartada: a identidade mudou durante a leitura")
            else -> AppLogger.d(TAG, "Subscription state synced ($policy): active=${info.isActive}")
        }
    }

    override suspend fun syncSubscriptionState() = readAndPublish(CacheFetchPolicy.CACHED_OR_FETCHED)

    /** Ignora o cache (`FETCH_CURRENT`) — para depois de uma compra feita fora da lib (ponte nativa iOS). */
    override suspend fun refreshSubscriptionState() = readAndPublish(CacheFetchPolicy.FETCH_CURRENT)

    private fun com.revenuecat.purchases.kmp.models.CustomerInfo.toSubscriptionInfo(): SubscriptionInfo {
        val premiumEntitlement = entitlements.active[config.entitlementId]

        return if (premiumEntitlement != null) {
            SubscriptionInfo(
                isActive = true,
                productId = premiumEntitlement.productIdentifier,
                expirationDate = null,
                willRenew = premiumEntitlement.willRenew
            )
        } else {
            SubscriptionInfo(isActive = false)
        }
    }

    /** Mapeia um `Package` do offering para o DTO uniforme [PurchasePackage] da lib. */
    private fun Package.toPurchasePackage(eligibility: TrialEligibility?): PurchasePackage {
        val product = storeProduct
        val type = packageType.toPurchasePackageType()
        val freeTrial = product.freeTrialPeriod()
        return PurchasePackage(
            packageId = identifier,
            packageType = type,
            storeProductId = product.id,
            priceLabel = product.price.formatted,
            priceAmountMicros = product.price.amountMicros,
            currencyCode = product.price.currencyCode,
            durationMonths = resolveDurationMonths(type, product),
            freeTrial = freeTrial,
            trialEligibility = if (freeTrial == null) TrialEligibility.NO_OFFER else eligibility ?: TrialEligibility.UNKNOWN,
            canSkipFreeTrial = product.subscriptionOptions?.basePlan != null,
        )
    }

    /**
     * Período grátis que a loja tem para o produto: no Play, a fase `FREE_TRIAL` da oferta de trial
     * (`subscriptionOptions.freeTrial`); na Apple, o `introductoryDiscount` em `FREE_TRIAL`
     * (`numberOfPeriods` × período). Oferta de preço reduzido (não grátis) não é trial.
     */
    private fun StoreProduct.freeTrialPeriod(): FreeTrialPeriod? {
        subscriptionOptions?.freeTrial?.pricingPhases
            ?.firstOrNull { it.offerPaymentMode == OfferPaymentMode.FREE_TRIAL }
            ?.let { fase -> return fase.billingPeriod.toFreeTrialPeriod(fase.billingCycleCount ?: 1) }
        val intro = introductoryDiscount?.takeIf { it.paymentMode == DiscountPaymentMode.FREE_TRIAL }
            ?: return null
        return intro.subscriptionPeriod.toFreeTrialPeriod(intro.numberOfPeriods.toInt().coerceAtLeast(1))
    }

    private fun Period.toFreeTrialPeriod(cycles: Int): FreeTrialPeriod? {
        val unidade = when (unit) {
            PeriodUnit.DAY -> FreeTrialUnit.DAY
            PeriodUnit.WEEK -> FreeTrialUnit.WEEK
            PeriodUnit.MONTH -> FreeTrialUnit.MONTH
            PeriodUnit.YEAR -> FreeTrialUnit.YEAR
            PeriodUnit.UNKNOWN -> return null
        }
        val total = value * cycles
        return if (total > 0) FreeTrialPeriod(total, unidade) else null
    }

    private fun PackageType.toPurchasePackageType(): PurchasePackageType = when (this) {
        PackageType.MONTHLY -> PurchasePackageType.MONTHLY
        PackageType.SIX_MONTH -> PurchasePackageType.SIX_MONTH
        PackageType.ANNUAL -> PurchasePackageType.ANNUAL
        PackageType.LIFETIME -> PurchasePackageType.LIFETIME
        // WEEKLY / TWO_MONTH / THREE_MONTH / CUSTOM / UNKNOWN
        else -> PurchasePackageType.OTHER
    }

    /**
     * Deriva a duracao em meses do pacote: direta para os tipos padronizados; para [OTHER]/CUSTOM
     * tenta o periodo de assinatura do produto (`period.valueInMonths`); vitalicio/indeterminado -> null.
     */
    private fun resolveDurationMonths(
        type: PurchasePackageType,
        product: StoreProduct
    ): Int? = when (type) {
        PurchasePackageType.MONTHLY -> 1
        PurchasePackageType.SIX_MONTH -> 6
        PurchasePackageType.ANNUAL -> 12
        PurchasePackageType.LIFETIME -> null
        PurchasePackageType.OTHER -> product.period?.valueInMonths?.toInt()?.takeIf { it > 0 }
    }

    private fun StoreProduct.toPurchaseProduct(): PurchaseProduct {
        val periodConfig = config.products.find { it.id == id }?.period
        return PurchaseProduct(
            id = id,
            title = title,
            description = localizedDescription ?: title,
            price = price.formatted,
            priceAmountMicros = price.amountMicros,
            currencyCode = price.currencyCode,
            subscriptionPeriod = periodConfig
        )
    }

    /**
     * Traduz o erro do SDK no resultado de compra da lib. Desistência vira [PurchaseResult.Cancelled]
     * (nunca `Error`), e o motivo da falha real sai do **código tipado** — ver [toPurchaseFailure].
     */
    private fun PurchasesError.toPurchaseResult(
        userCancelled: Boolean
    ): PurchaseResult = when (val failure = toPurchaseFailure(userCancelled)) {
        is PurchaseFailure.Cancelled -> PurchaseResult.Cancelled
        is PurchaseFailure.Failed -> {
            AppLogger.e(TAG, "Compra falhou [${failure.code}]: ${failure.message}")
            PurchaseResult.Error(message = failure.message, code = failure.code)
        }
    }

    companion object {
        private const val TAG = "RevenueCatPurchaseRepo"

        /** Teto da pergunta de elegibilidade ao StoreKit — sem resposta, a tela mostra o preço. */
        private const val ELIGIBILITY_TIMEOUT_MS = 5_000L
    }
}

/**
 * Status da RevenueCat → [TrialEligibility] da lib. Puro, para teste.
 *
 * [filteredByStore] = o produto trouxe a oferta com fase grátis em `subscriptionOptions` (Play): ali a
 * presença da oferta já é a confirmação, e o `UNKNOWN` que a RevenueCat devolve no Android vira
 * [TrialEligibility.ELIGIBLE]. Em qualquer outro caso `UNKNOWN` continua `UNKNOWN` — sem promessa.
 */
internal fun resolveTrialEligibility(
    status: IntroEligibilityStatus?,
    filteredByStore: Boolean,
): TrialEligibility = when (status) {
    IntroEligibilityStatus.ELIGIBLE -> TrialEligibility.ELIGIBLE
    IntroEligibilityStatus.INELIGIBLE -> TrialEligibility.INELIGIBLE
    IntroEligibilityStatus.NO_INTRO_OFFER_EXISTS ->
        if (filteredByStore) TrialEligibility.ELIGIBLE else TrialEligibility.NO_OFFER
    IntroEligibilityStatus.UNKNOWN, null ->
        if (filteredByStore) TrialEligibility.ELIGIBLE else TrialEligibility.UNKNOWN
}
