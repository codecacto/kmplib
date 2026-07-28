package br.com.codecacto.kmplib.monetization.purchase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake reutilizável de [PurchaseRepository] para `commonTest`.
 *
 * O [RevenueCatPurchaseRepository] toca o SDK nativo e não roda em unit test; este fake **reproduz o
 * contrato de identidade** que a lib promete (validação prévia do id, anonimização idempotente,
 * publicação do entitlement do novo sujeito e invalidação do catálogo em cache), para que o
 * comportamento observável possa ser testado sem device.
 *
 * @param premiumFor sujeitos (app user ids) que têm assinatura ativa — permite testar "identificar a
 *   organização traz o premium dela" e "logout devolve o app a Free".
 */
class FakePurchaseRepository(
    private val premiumFor: Set<String> = emptySet(),
    initialAppUserId: String = PurchaseIdentity.ANONYMOUS_ID_PREFIX + "seed",
) : PurchaseRepository {

    private val _subscriptionState = MutableStateFlow(SubscriptionInfo(isActive = false))
    override val subscriptionState: Flow<SubscriptionInfo> = _subscriptionState.asStateFlow()

    private var appUserId: String = initialAppUserId

    /** Falha determinística a devolver na próxima troca de identidade (`null` = sucesso). */
    var nextIdentityFailure: PurchaseIdentityError? = null

    /** Catálogo em cache — o teste observa que a troca de sujeito o derruba. */
    var cachedOfferings: List<PurchasePackage> = emptyList()

    var identifyCalls: Int = 0
        private set
    var resetCalls: Int = 0
        private set

    override suspend fun isPremium(): Boolean = _subscriptionState.value.isActive

    override suspend fun getOfferings(): Result<List<PurchasePackage>> =
        Result.success(cachedOfferings)

    override suspend fun purchasePackage(packageId: String): PurchaseResult =
        PurchaseResult.Error("fake", PurchaseErrorCode.UNKNOWN)

    @Deprecated("Assinaturas usam getOfferings()", ReplaceWith("getOfferings()"))
    override suspend fun getProducts(): Result<List<PurchaseProduct>> = Result.success(emptyList())

    @Deprecated("Assinaturas usam purchasePackage(packageId)")
    override suspend fun purchase(productId: String): PurchaseResult =
        PurchaseResult.Error("fake", PurchaseErrorCode.UNKNOWN)

    override suspend fun purchaseConsumable(productId: String): ConsumablePurchaseResult =
        ConsumablePurchaseResult.Error("fake", PurchaseErrorCode.UNKNOWN)

    override suspend fun restorePurchases(): RestoreResult = RestoreResult.NoPurchasesToRestore

    override suspend fun identify(appUserId: String): Result<Unit> {
        identifyCalls++
        val id = when (val check = PurchaseIdentity.check(appUserId)) {
            is AppUserIdCheck.Invalid ->
                return Result.failure(
                    PurchaseIdentityException(PurchaseIdentityError.INVALID_APP_USER_ID, check.reason)
                )

            is AppUserIdCheck.Valid -> check.appUserId
        }
        nextIdentityFailure?.let { reason ->
            nextIdentityFailure = null
            return Result.failure(PurchaseIdentityException(reason, "falha simulada"))
        }
        this.appUserId = id
        onIdentityChanged(isActive = id in premiumFor)
        return Result.success(Unit)
    }

    override suspend fun resetIdentity(): Result<Unit> {
        resetCalls++
        if (PurchaseIdentity.isAnonymous(appUserId)) return Result.success(Unit)
        nextIdentityFailure?.let { reason ->
            nextIdentityFailure = null
            return Result.failure(PurchaseIdentityException(reason, "falha simulada"))
        }
        appUserId = PurchaseIdentity.ANONYMOUS_ID_PREFIX + "novo"
        onIdentityChanged(isActive = false)
        return Result.success(Unit)
    }

    override fun currentAppUserId(): String = appUserId

    override suspend fun getSubscriptionInfo(): SubscriptionInfo = _subscriptionState.value

    override suspend fun syncSubscriptionState() = Unit

    private fun onIdentityChanged(isActive: Boolean) {
        cachedOfferings = emptyList()
        _subscriptionState.value = SubscriptionInfo(isActive = isActive)
    }
}

/** Repositório mínimo que NÃO implementa identidade — exercita os defaults da interface. */
class IdentityUnawarePurchaseRepository : PurchaseRepository {
    override val subscriptionState: Flow<SubscriptionInfo> =
        MutableStateFlow(SubscriptionInfo(isActive = false)).asStateFlow()

    override suspend fun isPremium(): Boolean = false
    override suspend fun getOfferings(): Result<List<PurchasePackage>> = Result.success(emptyList())
    override suspend fun purchasePackage(packageId: String): PurchaseResult =
        PurchaseResult.Error("n/a", PurchaseErrorCode.UNKNOWN)

    @Deprecated("Assinaturas usam getOfferings()", ReplaceWith("getOfferings()"))
    override suspend fun getProducts(): Result<List<PurchaseProduct>> = Result.success(emptyList())

    @Deprecated("Assinaturas usam purchasePackage(packageId)")
    override suspend fun purchase(productId: String): PurchaseResult =
        PurchaseResult.Error("n/a", PurchaseErrorCode.UNKNOWN)

    override suspend fun purchaseConsumable(productId: String): ConsumablePurchaseResult =
        ConsumablePurchaseResult.Error("n/a", PurchaseErrorCode.UNKNOWN)

    override suspend fun restorePurchases(): RestoreResult = RestoreResult.NoPurchasesToRestore
    override suspend fun getSubscriptionInfo(): SubscriptionInfo = SubscriptionInfo(isActive = false)
    override suspend fun syncSubscriptionState() = Unit
}
