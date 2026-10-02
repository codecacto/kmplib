package br.com.codecacto.kmplib.monetization.purchase

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.monetization.alert.PaymentAlertKind
import br.com.codecacto.kmplib.monetization.alert.PaymentAlertReporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Resposta da porta da compra ([StoreIdentityBinder.ensureForPurchase] /
 * `MonetizationManager.ensureIdentityForPurchase()`): a loja pode vender/restaurar **para esta conta**?
 */
enum class StoreIdentityStatus {
    /** A loja está identificada exatamente com o sujeito logado. Pode vender e restaurar. */
    BOUND,

    /**
     * O app **não declarou** quem assina (nunca chamou `bindIdentity`/`syncIdentity`). É o desenho de
     * app sem conta (arquétipo A, 100% local): a compra é do app user anônimo do SDK, e é assim que
     * deve ser. A lib não recusa nada aqui.
     */
    UNMANAGED,

    /** Sem loja neste build (sem chave): a compra já sai como "indisponível" por outro caminho. */
    NO_STORE,

    /** O app declarou a identidade, mas ninguém está logado: não há a quem atribuir a compra. */
    NO_SUBJECT,

    /**
     * A loja segue com OUTRO id — o anônimo do SDK ou a conta anterior do aparelho — mesmo depois de
     * tentar identificar de novo. Vender aqui entregaria a assinatura a outra pessoa.
     */
    MISMATCH,
    ;

    /** `true` quando a compra/restauração pode seguir para a loja. */
    val allowsPurchase: Boolean
        get() = this == BOUND || this == UNMANAGED || this == NO_STORE
}

/**
 * O que o [StoreIdentityBinder] precisa da loja — três chamadas, para o binder ser testável sem SDK.
 * Em produção é o `PurchaseManager` (via `MonetizationManager`).
 */
internal interface StoreIdentityGateway {
    suspend fun identify(appUserId: String): Result<Unit>
    suspend fun resetIdentity(): Result<Unit>

    /** `null` = sem loja neste build. */
    fun currentAppUserId(): String?
}

/**
 * **Amarra quem está logado a quem assina na loja** (2.233.0, GAP-MON-IDENT-01).
 *
 * Sem isto o SDK compra com o app user anônimo (`$RCAnonymousID:…`): o webhook grava o direito nesse
 * id, a central procura pela conta, não acha, e a pessoa paga e continua no grátis — sem erro em lugar
 * nenhum. E sem anonimizar no logout, o próximo usuário do aparelho herda o premium de quem saiu. A
 * cola estava escrita à mão em 8 apps e na casca, cada um com uma variação (e uma delas não pegava o
 * caso abaixo).
 *
 * ## As regras
 *
 * - **Segue a sessão** ([bind]/[sync]): sujeito presente → `identify`; sujeito some depois de ter
 *   existido (logout, conta excluída, refresh expirado) → `resetIdentity`. Abrir o app sem sessão
 *   (`null` desde o início) **não** chama a loja: a sessão costuma nascer `null` e ser restaurada um
 *   instante depois, e anonimizar ali seria um `logOut` + `logIn` na loja a cada abertura.
 * - **Tudo serializado por um [Mutex]**: login e logout em sequência rápida não se cruzam na loja.
 * - **A porta da compra compara IGUALDADE** ([ensureForPurchase]), não "não é anônimo": se o reset do
 *   logout e o identify do login seguinte falharem em sequência, a loja continua sendo a **conta
 *   anterior** do aparelho — não anônima —, e a compra (ou a restauração) iria para outra pessoa.
 * - **Nunca lança e nunca trava a tela.** Falha de `identify` vira alerta de pagamento
 *   ([PaymentAlertKind.IdentificacaoNaLojaFalhou]) — exceto rede (transitória, tenta de novo na porta
 *   da compra) e build sem loja (estado válido). O `detalhe` do alerta é só o motivo tipado: o id é do
 *   titular e não sai do aparelho.
 *
 * Uma instância por processo: `MonetizationManager` guarda a dele, e é por ela que o app fala.
 */
class StoreIdentityBinder internal constructor(
    private val store: StoreIdentityGateway,
) {
    private val lock = Mutex()

    private val _subject = MutableStateFlow<String?>(null)

    /** Sujeito (conta ou organização) que a loja deve refletir agora; `null` = ninguém logado. */
    val subject: StateFlow<String?> = _subject.asStateFlow()

    /** `true` depois que o app declarou a identidade ([bind] ou [sync] chamados ao menos uma vez). */
    var isManaged: Boolean = false
        private set

    private var alerts: PaymentAlertReporter? = null

    /**
     * Segue [subjectIds] enquanto ele emitir — suspende; chame **uma vez**, na raiz do app
     * (`LaunchedEffect(Unit)`) ou num escopo de aplicação.
     *
     * @param subjectIds o id de QUEM ASSINA: a conta (`User.id`) no caso normal; a **organização** em
     *   produto multi-tenant em que a assinatura é dela — senão o entitlement nasce no tenant errado.
     * @param alerts destino dos alertas de pagamento; `null` = só log.
     */
    suspend fun bind(subjectIds: Flow<String?>, alerts: PaymentAlertReporter? = null) {
        if (alerts != null) this.alerts = alerts
        subjectIds.distinctUntilChanged().collect { sync(it) }
    }

    /**
     * Aplica um sujeito pontualmente — para o app que já observa a sessão por conta própria.
     * Idempotente: repetir o estado corrente não chama a loja.
     */
    suspend fun sync(subjectId: String?) {
        lock.withLock {
            isManaged = true
            val next = subjectId?.trim()?.takeIf { it.isNotEmpty() }
            val previous = _subject.value
            _subject.value = next
            when {
                next != null -> identifyLocked(next)
                previous != null -> resetLocked()
                else -> Unit // null → null: nada a desfazer.
            }
        }
    }

    /**
     * **A porta da compra (e da restauração).** Tenta de novo o `identify` se a loja não está com o
     * sujeito logado e responde se dá para seguir. Ver [StoreIdentityStatus.allowsPurchase].
     *
     * Recusar é a resposta certa, não a conservadora: a pessoa tenta de novo em um minuto e compra de
     * verdade, em vez de pagar por algo que nunca chega a esta conta.
     */
    suspend fun ensureForPurchase(): StoreIdentityStatus = lock.withLock {
        if (!isManaged) return@withLock StoreIdentityStatus.UNMANAGED
        if (store.currentAppUserId() == null) return@withLock StoreIdentityStatus.NO_STORE
        val id = _subject.value ?: return@withLock StoreIdentityStatus.NO_SUBJECT
        val failure = identifyLocked(id)
        // A igualdade só vale para um id que identifica alguém: um sujeito reservado ou anônimo do
        // SDK "casaria" com a loja anônima e liberaria a venda para ninguém.
        val valid = PurchaseIdentity.check(id) is AppUserIdCheck.Valid
        if (valid && store.currentAppUserId() == id) return@withLock StoreIdentityStatus.BOUND

        // Sem rede a compra não aconteceria de qualquer forma: é o usuário, não um incidente.
        if (failure != PurchaseIdentityError.NETWORK) {
            val current = store.currentAppUserId()
            val where = if (PurchaseIdentity.isAnonymous(current)) "anonima" else "outra-conta"
            alerts?.report(
                PaymentAlertKind.CompraSemIdentidade,
                detalhe = "loja=$where motivo=${failure?.name ?: "sem-erro"}",
            )
        }
        StoreIdentityStatus.MISMATCH
    }

    /**
     * Reaplica o sujeito corrente — chamado quando a loja passa a existir (inicialização tardia,
     * depois do login): sem isto, o sujeito que chegou antes da loja só seria aplicado na compra.
     */
    internal suspend fun reconcile() {
        lock.withLock { _subject.value?.let { identifyLocked(it) } }
    }

    /** Volta ao estado de fábrica (testes / `MonetizationManager.reset()`). */
    internal fun reset() {
        _subject.value = null
        isManaged = false
        alerts = null
    }

    /** @return o motivo da falha, ou `null` se identificou (ou não havia o que fazer). */
    private suspend fun identifyLocked(id: String): PurchaseIdentityError? {
        val current = store.currentAppUserId() ?: return null // sem loja: nada a identificar
        if (current == id) return null
        val error = store.identify(id).exceptionOrNull() ?: return null
        val reason = (error as? PurchaseIdentityException)?.reason ?: PurchaseIdentityError.UNKNOWN
        AppLogger.w(TAG, "identify falhou (${reason.name})")
        if (reason != PurchaseIdentityError.NETWORK && reason != PurchaseIdentityError.NOT_CONFIGURED) {
            alerts?.report(PaymentAlertKind.IdentificacaoNaLojaFalhou, detalhe = "motivo=${reason.name}")
        }
        return reason
    }

    private suspend fun resetLocked() {
        val current = store.currentAppUserId() ?: return
        if (PurchaseIdentity.isAnonymous(current)) return
        store.resetIdentity().onFailure { e ->
            // Não-fatal: a porta da compra compara igualdade, então a conta anterior não vende.
            AppLogger.w(TAG, "resetIdentity falhou (${e::class.simpleName})")
        }
    }

    private companion object {
        const val TAG = "StoreIdentity"
    }
}
