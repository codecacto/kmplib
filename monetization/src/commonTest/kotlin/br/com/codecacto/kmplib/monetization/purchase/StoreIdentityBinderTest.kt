package br.com.codecacto.kmplib.monetization.purchase

import br.com.codecacto.kmplib.firebase.auth.IAuthRepository
import br.com.codecacto.kmplib.firebase.auth.User
import br.com.codecacto.kmplib.monetization.MonetizationConfig
import br.com.codecacto.kmplib.monetization.MonetizationManager
import br.com.codecacto.kmplib.monetization.alert.PaymentAlertKind
import br.com.codecacto.kmplib.monetization.alert.PaymentAlertReporter
import br.com.codecacto.kmplib.observability.CrashLevel
import br.com.codecacto.kmplib.observability.CrashReporter
import br.com.codecacto.kmplib.observability.CrashReporterConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Loja de mentira que registra cada chamada e pode falhar sob comando. */
private class RecordingStore(
    var appUserId: String? = PurchaseIdentity.ANONYMOUS_ID_PREFIX + "a",
) : StoreIdentityGateway {
    val calls = mutableListOf<String>()
    var identifyFailure: PurchaseIdentityError? = null
    var resetFails = false
    /** Segura o `identify` até o teste soltar — para provar a serialização. */
    var identifyGate: CompletableDeferred<Unit>? = null

    override suspend fun identify(appUserId: String): Result<Unit> {
        calls += "identify:$appUserId"
        identifyGate?.await()
        identifyFailure?.let { return Result.failure(PurchaseIdentityException(it, "simulado")) }
        this.appUserId = appUserId
        return Result.success(Unit)
    }

    override suspend fun resetIdentity(): Result<Unit> {
        calls += "reset"
        if (resetFails) return Result.failure(PurchaseIdentityException(PurchaseIdentityError.STORE, "simulado"))
        appUserId = PurchaseIdentity.ANONYMOUS_ID_PREFIX + "b"
        return Result.success(Unit)
    }

    override fun currentAppUserId(): String? = appUserId
}

private class AlertSink : CrashReporter {
    val types = mutableListOf<String>()
    val details = mutableListOf<String>()
    override val isActive: Boolean = true
    override fun init(config: CrashReporterConfig) = Unit
    override fun captureException(throwable: Throwable, tags: Map<String, String>) = Unit
    override fun captureMessage(message: String, level: CrashLevel, tags: Map<String, String>, fingerprint: List<String>) {
        tags["tipo"]?.let(types::add)
        details += message
    }
    override fun addBreadcrumb(message: String, category: String?, level: CrashLevel) = Unit
    override fun setUser(id: String?) = Unit
    override fun clearUser() = Unit
    override fun setTag(key: String, value: String) = Unit
}

/**
 * A identidade da loja amarrada à sessão (GAP-MON-IDENT-01). O que se trava aqui é o que, errado,
 * faz a pessoa pagar e continuar no grátis — ou dá o premium de quem saiu ao próximo do aparelho.
 */
class StoreIdentityBinderTest {

    private val sink = AlertSink()
    private val alerts = PaymentAlertReporter(sink, projeto = "teste", umaVezPorSessao = false)

    // ------------------------------------------------------------------ segue a sessão

    @Test
    fun `sessao inteira - abre deslogado - entra - reemite - sai - entra outra`() = runTest {
        val store = RecordingStore()
        val binder = StoreIdentityBinder(store)
        binder.bind(flowOf(null, "u1", "u1", null, "u2"), alerts)
        assertEquals(listOf("identify:u1", "reset", "identify:u2"), store.calls)
        assertEquals("u2", binder.subject.value)
    }

    @Test
    fun `abrir sem sessao nao chama a loja — a sessao nasce nula e e restaurada depois`() = runTest {
        val store = RecordingStore(appUserId = "conta-antiga")
        val binder = StoreIdentityBinder(store)
        binder.sync(null)
        assertTrue(store.calls.isEmpty())
        assertTrue(binder.isManaged)
    }

    @Test
    fun `loja ja com a conta logada nao e chamada de novo`() = runTest {
        val store = RecordingStore(appUserId = "u1")
        StoreIdentityBinder(store).sync("u1")
        assertTrue(store.calls.isEmpty())
    }

    @Test
    fun `id em branco conta como ninguem logado`() = runTest {
        val store = RecordingStore()
        val binder = StoreIdentityBinder(store)
        binder.sync("u1")
        binder.sync("   ")
        assertEquals(listOf("identify:u1", "reset"), store.calls)
    }

    @Test
    fun `logout com a loja ja anonima nao chama reset`() = runTest {
        val store = RecordingStore()
        val binder = StoreIdentityBinder(store)
        store.identifyFailure = PurchaseIdentityError.NETWORK
        binder.sync("u1") // loja continua anônima
        binder.sync(null)
        assertEquals(listOf("identify:u1"), store.calls)
    }

    @Test
    fun `sem loja neste build nada e chamado e a porta responde NO_STORE`() = runTest {
        val store = RecordingStore(appUserId = null)
        val binder = StoreIdentityBinder(store)
        binder.sync("u1")
        binder.sync(null)
        assertTrue(store.calls.isEmpty())
        assertEquals(StoreIdentityStatus.NO_STORE, binder.ensureForPurchase())
    }

    // ------------------------------------------------------------------ porta da compra

    @Test
    fun `app que nao declarou identidade compra anonimo — e o desenho de app sem conta`() = runTest {
        val binder = StoreIdentityBinder(RecordingStore())
        assertEquals(StoreIdentityStatus.UNMANAGED, binder.ensureForPurchase())
        assertTrue(StoreIdentityStatus.UNMANAGED.allowsPurchase)
    }

    @Test
    fun `porta identifica de novo se o identify do login tinha falhado`() = runTest {
        val store = RecordingStore()
        val binder = StoreIdentityBinder(store)
        store.identifyFailure = PurchaseIdentityError.NETWORK
        binder.sync("u1")
        store.identifyFailure = null

        assertEquals(StoreIdentityStatus.BOUND, binder.ensureForPurchase())
        assertEquals(listOf("identify:u1", "identify:u1"), store.calls)
    }

    @Test
    fun `porta compara IGUALDADE — loja na conta anterior do aparelho nao vende`() = runTest {
        // reset do logout E identify do login falharam: a loja segue com u1, que NÃO é anônimo.
        val store = RecordingStore(appUserId = "u1")
        val binder = StoreIdentityBinder(store)
        binder.bind(flowOf("u1"), alerts)
        store.resetFails = true
        store.identifyFailure = PurchaseIdentityError.STORE
        binder.sync(null)
        binder.sync("u2")

        val status = binder.ensureForPurchase()

        assertEquals(StoreIdentityStatus.MISMATCH, status)
        assertFalse(status.allowsPurchase)
        assertEquals("u1", store.appUserId)
        assertTrue(PaymentAlertKind.CompraSemIdentidade.slug in sink.types)
        assertTrue(PaymentAlertKind.IdentificacaoNaLojaFalhou.slug in sink.types)
    }

    @Test
    fun `ninguem logado nao compra`() = runTest {
        val binder = StoreIdentityBinder(RecordingStore())
        binder.sync(null)
        assertEquals(StoreIdentityStatus.NO_SUBJECT, binder.ensureForPurchase())
        assertFalse(StoreIdentityStatus.NO_SUBJECT.allowsPurchase)
    }

    @Test
    fun `sem rede a porta recusa sem alertar — e o usuario e nao a loja`() = runTest {
        val store = RecordingStore()
        val binder = StoreIdentityBinder(store)
        binder.bind(flowOf("u1"), alerts)
        store.identifyFailure = PurchaseIdentityError.NETWORK
        // o bind já identificou; força a loja de volta ao anônimo
        store.appUserId = PurchaseIdentity.ANONYMOUS_ID_PREFIX + "c"

        assertEquals(StoreIdentityStatus.MISMATCH, binder.ensureForPurchase())
        assertTrue(sink.types.isEmpty())
    }

    @Test
    fun `alerta nao leva o id da conta`() = runTest {
        val store = RecordingStore()
        val binder = StoreIdentityBinder(store)
        store.identifyFailure = PurchaseIdentityError.INVALID_APP_USER_ID
        binder.bind(flowOf("conta-secreta-123"), alerts)
        binder.ensureForPurchase()
        assertTrue(sink.types.isNotEmpty())
        assertTrue(sink.details.none { "conta-secreta-123" in it })
    }

    @Test
    fun `sujeito anonimo do SDK nunca conta como identificado`() = runTest {
        val anon = PurchaseIdentity.ANONYMOUS_ID_PREFIX + "a"
        val store = RecordingStore(appUserId = anon)
        val binder = StoreIdentityBinder(store)
        binder.sync(anon)
        assertEquals(StoreIdentityStatus.MISMATCH, binder.ensureForPurchase())
    }

    // ------------------------------------------------------------------ serialização

    @Test
    fun `login e logout em sequencia rapida nao se cruzam na loja`() = runTest {
        val store = RecordingStore()
        val binder = StoreIdentityBinder(store)
        val gate = CompletableDeferred<Unit>()
        store.identifyGate = gate
        val subjects = MutableSharedFlow<String?>(replay = 2)

        val job = launch { binder.bind(subjects) }
        subjects.emit("u1")
        subjects.emit(null)
        val purchase = async { binder.ensureForPurchase() }
        testScheduler.runCurrent()
        // identify de u1 ainda preso: nem o reset nem a porta passaram na frente.
        assertEquals(listOf("identify:u1"), store.calls)

        store.identifyGate = null
        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        // Ordem de chegada à trava (FIFO): a porta entrou na fila antes do logout e viu u1 — a conta
        // logada no instante do toque —, e o reset só correu depois dela. Nada se cruzou.
        assertEquals(StoreIdentityStatus.BOUND, purchase.await())
        assertEquals(listOf("identify:u1", "reset"), store.calls)
        assertEquals(null, binder.subject.value)
        job.cancel()
    }

    // ------------------------------------------------------------------ MonetizationManager

    @BeforeTest
    @AfterTest
    fun limpar() {
        MonetizationManager.reset()
    }

    @Test
    fun `MonetizationManager - sujeito declarado antes da loja entra na configuracao e a porta funciona`() = runTest {
        MonetizationManager.syncIdentity("u1")
        assertEquals("u1", MonetizationManager.storeIdentity.subject.value)
        // Sem loja ainda: estado válido, a porta diz NO_STORE.
        assertEquals(StoreIdentityStatus.NO_STORE, MonetizationManager.ensureIdentityForPurchase())

        val repo = FakePurchaseRepository()
        PurchaseManager.initializeWith(repo)
        assertEquals(StoreIdentityStatus.BOUND, MonetizationManager.ensureIdentityForPurchase())
        assertEquals("u1", MonetizationManager.currentAppUserId())
    }

    @Test
    fun `MonetizationManager - bindIdentity pelo IAuthRepository usa o id da conta por default`() = runTest {
        val repo = FakePurchaseRepository()
        PurchaseManager.initializeWith(repo)
        val auth = FakeAuth(listOf(null, user("u7")))
        MonetizationManager.bindIdentity(auth)
        assertEquals("u7", MonetizationManager.currentAppUserId())
    }

    @Test
    fun `MonetizationManager - multi-tenant identifica pela organizacao`() = runTest {
        val repo = FakePurchaseRepository()
        PurchaseManager.initializeWith(repo)
        MonetizationManager.bindIdentity(FakeAuth(listOf(user("u7"))), subjectOf = { "org-${it.id}" })
        assertEquals("org-u7", MonetizationManager.currentAppUserId())
    }

    @Test
    fun `MonetizationManager - reset volta a UNMANAGED`() = runTest {
        MonetizationManager.syncIdentity("u1")
        MonetizationManager.reset()
        assertEquals(StoreIdentityStatus.UNMANAGED, MonetizationManager.ensureIdentityForPurchase())
        // e o modo de monetização volta a poder ser inicializado
        MonetizationManager.initialize(MonetizationConfig.AdsOnly)
    }
}

private fun user(id: String) = User(id = id, email = "$id@teste.test")

/** Só o `currentUser` importa aqui; o resto não é chamado. */
private class FakeAuth(private val users: List<User?>) : IAuthRepository {
    override val currentUser: Flow<User?> = flowOf(*users.toTypedArray())
    override val isLoggedIn: Flow<Boolean> = flowOf(users.lastOrNull() != null)
    override val currentUserSync: User? get() = users.lastOrNull()
    override val isLoggedInSync: Boolean get() = currentUserSync != null
    override suspend fun signInWithEmail(email: String, password: String): Result<User> = unused()
    override suspend fun signInWithGoogle(idToken: String, accessToken: String?): Result<User> = unused()
    override suspend fun signInWithApple(idToken: String, nonce: String): Result<User> = unused()
    override suspend fun signUpWithEmail(email: String, password: String, displayName: String?): Result<User> = unused()
    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = unused()
    override suspend fun updateProfile(displayName: String?, photoUrl: String?): Result<Unit> = unused()
    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = unused()
    override suspend fun deleteAccount(password: String?): Result<Unit> = unused()
    override suspend fun signOut() = Unit
    override suspend fun sendEmailVerification(): Result<Unit> = unused()
    override suspend fun getIdToken(forceRefresh: Boolean): Result<String> = unused()

    private fun <T> unused(): Result<T> = Result.failure(UnsupportedOperationException("não usado"))
}
