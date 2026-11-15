package br.com.codecacto.kmplib.monetization.purchase

import br.com.codecacto.kmplib.monetization.MonetizationConfig
import br.com.codecacto.kmplib.monetization.MonetizationManager
import br.com.codecacto.kmplib.monetization.PremiumStatus
import br.com.codecacto.kmplib.monetization.consumableIdentityAllows
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Compra CONSUMÍVEL por id (2.365.0) — a regra do recibo, a porta da identidade e o modo
 * [MonetizationConfig.StoreItemsOnly].
 */
class ConsumableItemPurchaseTest {

    private val purchase = PurchaseConfig(androidApiKey = "goog_x", iosApiKey = "appl_x")
    private val compra = 1_700_000_000_000L

    @BeforeTest
    fun antes() = MonetizationManager.reset()

    @AfterTest
    fun depois() = MonetizationManager.reset()

    // ------------------------------------------------------------------ desfechos de falha

    @Test
    fun `pagamento pendente nao e erro e cancelamento nao e falha`() {
        assertEquals(
            ConsumableItemPurchaseResult.Pending("faixa_1"),
            ConsumableItemPurchaseResult.fromFailure(PurchaseErrorCode.PAYMENT_PENDING, "x", "faixa_1"),
        )
        assertEquals(
            ConsumableItemPurchaseResult.Cancelled,
            ConsumableItemPurchaseResult.fromFailure(PurchaseErrorCode.USER_CANCELLED, "x", "faixa_1"),
        )
        val falha = ConsumableItemPurchaseResult.fromFailure(PurchaseErrorCode.PAYMENT_DECLINED, "cartao", "faixa_1")
        assertIs<ConsumableItemPurchaseResult.Failed>(falha)
        assertEquals(PurchaseErrorCode.PAYMENT_DECLINED, falha.code)
        // Consumível não é "possuído": ALREADY_OWNED fica como falha, nunca como direito.
        assertIs<ConsumableItemPurchaseResult.Failed>(
            ConsumableItemPurchaseResult.fromFailure(PurchaseErrorCode.ALREADY_OWNED, "x", "faixa_1"),
        )
    }

    // ------------------------------------------------------------------ recibo

    private fun recibo(
        storeId: String?,
        rc: List<OwnedStoreItem> = emptyList(),
        storeTime: Long? = compra,
        productIds: List<String> = listOf("faixa_1"),
    ) = consumablePurchaseResultOf(
        requestedProductId = "faixa_1",
        storeProductIds = productIds,
        storeTransactionId = storeId,
        storePurchaseTimeMillis = storeTime,
        revenueCatTransactions = rc,
        store = PurchaseStore.PLAY_STORE,
        appUserId = "conta-1",
    )

    @Test
    fun `id da loja tem preferencia e o da RevenueCat vai junto`() {
        val r = recibo("GPA.1", rc = listOf(OwnedStoreItem("faixa_1", "rc_1", compra)))
        assertIs<ConsumableItemPurchaseResult.Success>(r)
        assertEquals("GPA.1", r.purchase.transactionId)
        assertEquals(ConsumableTransactionIdSource.STORE, r.purchase.transactionIdSource)
        assertEquals("rc_1", r.purchase.revenueCatTransactionId)
        assertEquals("conta-1", r.purchase.appUserId)
        assertEquals(PurchaseStore.PLAY_STORE, r.purchase.store)
        assertEquals(compra, r.purchase.purchasedAtMillis)
    }

    @Test
    fun `sem id da loja usa a transacao da RevenueCat desta compra`() {
        val r = recibo(
            storeId = " ",
            rc = listOf(
                OwnedStoreItem("faixa_1", "rc_antiga", compra - 86_400_000L),
                OwnedStoreItem("faixa_2", "rc_outro_produto", compra),
                OwnedStoreItem("faixa_1", "rc_nova", compra + 1_000),
            ),
        )
        assertIs<ConsumableItemPurchaseResult.Success>(r)
        assertEquals("rc_nova", r.purchase.transactionId)
        assertEquals(ConsumableTransactionIdSource.REVENUECAT, r.purchase.transactionIdSource)
        assertNull(r.purchase.storeTransactionId)
    }

    @Test
    fun `compra antiga do mesmo produto nunca vira recibo da nova`() {
        val r = recibo(null, rc = listOf(OwnedStoreItem("faixa_1", "rc_antiga", compra - 86_400_000L)))
        assertIs<ConsumableItemPurchaseResult.CompletedWithoutReceipt>(r)
        assertEquals("faixa_1", r.productId)
        assertEquals("conta-1", r.appUserId)
    }

    @Test
    fun `sem instante da loja so aceita a RevenueCat com transacao unica do produto`() {
        val unica = recibo(null, rc = listOf(OwnedStoreItem("faixa_1", "rc_1", null)), storeTime = 0)
        assertIs<ConsumableItemPurchaseResult.Success>(unica)
        assertEquals("rc_1", unica.purchase.transactionId)
        assertNull(unica.purchase.purchasedAtMillis)

        val ambigua = recibo(
            null,
            rc = listOf(OwnedStoreItem("faixa_1", "rc_1", 1), OwnedStoreItem("faixa_1", "rc_2", 2)),
            storeTime = null,
        )
        assertIs<ConsumableItemPurchaseResult.CompletedWithoutReceipt>(ambigua)
    }

    @Test
    fun `produto da transacao vence o pedido e lista vazia cai no pedido`() {
        val vazia = recibo("GPA.1", productIds = emptyList())
        assertIs<ConsumableItemPurchaseResult.Success>(vazia)
        assertEquals("faixa_1", vazia.purchase.productId)
    }

    // ------------------------------------------------------------------ porta da identidade

    @Test
    fun `so conta ligada compra e sem conta so quando o app declara`() {
        assertTrue(consumableIdentityAllows(StoreIdentityStatus.BOUND, allowUnmanaged = false))
        StoreIdentityStatus.entries.filter { it != StoreIdentityStatus.BOUND }.forEach {
            assertFalse(consumableIdentityAllows(it, allowUnmanaged = false), it.name)
        }
        assertTrue(consumableIdentityAllows(StoreIdentityStatus.UNMANAGED, allowUnmanaged = true))
        assertFalse(consumableIdentityAllows(StoreIdentityStatus.NO_STORE, allowUnmanaged = true))
        assertFalse(consumableIdentityAllows(StoreIdentityStatus.NO_SUBJECT, allowUnmanaged = true))
        assertFalse(consumableIdentityAllows(StoreIdentityStatus.MISMATCH, allowUnmanaged = true))
    }

    @Test
    fun `sem loja a compra e CONFIGURATION_ERROR`() = runBlocking<Unit> {
        val r = MonetizationManager.purchaseConsumableItem("faixa_1")
        assertIs<ConsumableItemPurchaseResult.Failed>(r)
        assertEquals(PurchaseErrorCode.CONFIGURATION_ERROR, r.code)
    }

    @Test
    fun `identidade nao declarada recusa sem abrir a loja`() = runBlocking<Unit> {
        val loja = FakePurchaseRepository()
        PurchaseManager.initializeWith(loja)

        val r = MonetizationManager.purchaseConsumableItem("faixa_1")
        assertEquals(ConsumableItemPurchaseResult.IdentityNotBound(StoreIdentityStatus.UNMANAGED), r)
        assertTrue(loja.consumableItemCalls.isEmpty())
    }

    @Test
    fun `sessao sem conta recusa sem abrir a loja`() = runBlocking<Unit> {
        val loja = FakePurchaseRepository()
        PurchaseManager.initializeWith(loja)
        MonetizationManager.declareIdentityManaged()

        val r = MonetizationManager.purchaseConsumableItem("faixa_1")
        assertEquals(ConsumableItemPurchaseResult.IdentityNotBound(StoreIdentityStatus.NO_SUBJECT), r)
        assertTrue(loja.consumableItemCalls.isEmpty())
    }

    @Test
    fun `loja que nao consegue identificar a conta recusa`() = runBlocking<Unit> {
        val loja = FakePurchaseRepository()
        PurchaseManager.initializeWith(loja)
        loja.nextIdentityFailure = PurchaseIdentityError.NETWORK
        MonetizationManager.syncIdentity("conta-1")
        loja.nextIdentityFailure = PurchaseIdentityError.NETWORK

        val r = MonetizationManager.purchaseConsumableItem("faixa_1")
        assertEquals(ConsumableItemPurchaseResult.IdentityNotBound(StoreIdentityStatus.MISMATCH), r)
        assertTrue(loja.consumableItemCalls.isEmpty())
    }

    @Test
    fun `conta ligada compra e devolve o recibo e o pendente`() = runBlocking<Unit> {
        val loja = FakePurchaseRepository()
        PurchaseManager.initializeWith(loja)
        MonetizationManager.syncIdentity("conta-1")
        loja.consumableItemResult = { id ->
            if (id == "faixa_pendente") {
                ConsumableItemPurchaseResult.Pending(id)
            } else {
                recibo("GPA.9")
            }
        }

        val ok = MonetizationManager.purchaseConsumableItem("faixa_1")
        assertIs<ConsumableItemPurchaseResult.Success>(ok)
        assertEquals("GPA.9", ok.purchase.transactionId)
        assertEquals(
            ConsumableItemPurchaseResult.Pending("faixa_pendente"),
            MonetizationManager.purchaseConsumableItem("faixa_pendente"),
        )
        assertEquals(listOf("faixa_1", "faixa_pendente"), loja.consumableItemCalls)
    }

    @Test
    fun `app sem conta compra quando declara allowUnmanagedIdentity`() = runBlocking<Unit> {
        val loja = FakePurchaseRepository()
        PurchaseManager.initializeWith(loja)
        loja.consumableItemResult = { recibo("GPA.1") }

        val r = MonetizationManager.purchaseConsumableItem("faixa_1", allowUnmanagedIdentity = true)
        assertIs<ConsumableItemPurchaseResult.Success>(r)
    }

    @Test
    fun `repositorio sem suporte responde CONFIGURATION_ERROR sem fingir sucesso`() = runBlocking<Unit> {
        val r = IdentityUnawarePurchaseRepository().purchaseConsumableItem("faixa_1")
        assertIs<ConsumableItemPurchaseResult.Failed>(r)
        assertEquals(PurchaseErrorCode.CONFIGURATION_ERROR, r.code)
    }

    // ------------------------------------------------------------------ modo StoreItemsOnly

    @Test
    fun `StoreItemsOnly configura a loja sem assinatura premium nem anuncio`() = runBlocking<Unit> {
        val loja = FakePurchaseRepository()
        PurchaseManager.initializeWith(loja)
        val config = MonetizationConfig.StoreItemsOnly(purchase)

        assertTrue(MonetizationManager.initializeWhenStoreAvailable(config, premiumResolutionTimeout = 30.seconds))
        assertEquals(PremiumStatus.Free(PremiumStatus.FreeReason.NOT_SOLD), MonetizationManager.premiumStatus.value)
        assertFalse(MonetizationManager.hasPurchase)
        assertFalse(MonetizationManager.hasAds)
        assertFalse(MonetizationManager.shouldShowAds.value)
        assertFalse(MonetizationManager.isPremium.value)
        assertTrue(config.usesStore)
    }

    @Test
    fun `StoreItemsOnly sem chave nem loja instalada segue gratuito`() {
        val semChave = PurchaseConfig(androidApiKey = "PLACEHOLDER_x", iosApiKey = "PLACEHOLDER_x")
        assertFalse(MonetizationManager.initializeWhenStoreAvailable(MonetizationConfig.StoreItemsOnly(semChave)))
        assertEquals(PremiumStatus.Free(PremiumStatus.FreeReason.NOT_SOLD), MonetizationManager.premiumStatus.value)
    }
}
