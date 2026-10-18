package br.com.codecacto.kmplib.monetization.purchase

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.monetization.alert.PaymentAlertKind
import br.com.codecacto.kmplib.monetization.alert.PaymentAlertReporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    /**
     * O app declarou a identidade, mas não há a quem atribuir a compra: ninguém logado, sessão ainda
     * não emitida (o binder nasce gerenciado no `bind`, 2.305.0) ou sessão que **não compra por regra**
     * ([StoreSubject.Anonymous] — ex.: paciente que não assina).
     */
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
 * **Quem a loja deve refletir agora** (2.305.0) — o estado que o app emite para o [StoreIdentityBinder]
 * quando precisa distinguir "ninguém logado" de "logado, mas sem quem assine".
 *
 * - [Account] — a conta (ou organização) que assina: a loja é identificada com ela.
 * - [SignedOut] — **sem sessão** (o `null` de sempre). Abrir o app assim **não** chama a loja: a sessão
 *   costuma nascer nula e ser restaurada um instante depois. Só anonimiza se havia um sujeito antes
 *   (logout).
 * - [Anonymous] — **sessão ativa SEM sujeito por regra** (ex.: paciente do Meu Fisio, que não compra).
 *   **Força a loja anônima** sempre que ela não estiver anônima — inclusive em processo novo, quando o
 *   SDK da loja ainda guarda o último app user id do aparelho (o de quem assinou antes). Sem isto, o
 *   aparelho em que um assinante ficou identificado mostraria o premium dele durante a sessão de
 *   outra pessoa. Compra e restauração respondem [StoreIdentityStatus.NO_SUBJECT].
 */
sealed interface StoreSubject {
    /** A conta/organização que assina. Id em branco é tratado como [SignedOut]. */
    data class Account(val id: String) : StoreSubject {
        // O id é do titular: não vai a log por acidente.
        override fun toString(): String = "StoreSubject.Account(…)"
    }

    /** Ninguém logado. */
    data object SignedOut : StoreSubject

    /** Logado, mas esta sessão não compra por regra do produto: a loja fica anônima. */
    data object Anonymous : StoreSubject

    companion object {
        /** `null`/branco → [SignedOut]; senão [Account]. A ponte do contrato antigo (`String?`). */
        fun of(id: String?): StoreSubject =
            id?.trim()?.takeIf { it.isNotEmpty() }?.let(::Account) ?: SignedOut
    }
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
 * - **Sessão sem sujeito por regra** ([StoreSubject.Anonymous], 2.305.0) é outra coisa: força a loja
 *   anônima mesmo sem sujeito anterior neste processo — o SDK guarda o último id entre aberturas.
 * - **Gerenciado desde a declaração** (2.305.0): [bind] marca o binder como gerenciado ANTES da primeira
 *   emissão, e [declareManaged] permite fazê-lo já no boot. Até a sessão chegar, a porta responde
 *   [StoreIdentityStatus.NO_SUBJECT] — nunca `UNMANAGED`, que liberaria a compra anônima.
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

    private val _managed = MutableStateFlow(false)

    /**
     * `true` depois que o app declarou a identidade ([declareManaged], [bind] ou [sync]). Gerenciado,
     * a compra exige sujeito: antes da primeira emissão a porta responde `NO_SUBJECT`.
     */
    val isManaged: Boolean get() = _managed.value

    /** `true` enquanto o estado corrente for [StoreSubject.Anonymous] (sessão que não compra). */
    private var anonymousByRule = false

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
        bindSubjects(subjectIds.map { StoreSubject.of(it) }, alerts)
    }

    /**
     * Como [bind], mas pelo estado completo ([StoreSubject]) — a forma do app em que uma sessão pode
     * existir **sem** quem assine (emita [StoreSubject.Anonymous] para ela). Gerenciado desde já.
     */
    suspend fun bindSubjects(subjects: Flow<StoreSubject>, alerts: PaymentAlertReporter? = null) {
        if (alerts != null) this.alerts = alerts
        declareManaged()
        subjects.distinctUntilChanged().collect { sync(it) }
    }

    /**
     * Declara, **já no boot**, que este app amarra a loja à sessão — antes de o `bind` começar a coletar
     * (ex.: no módulo Koin ou no `Application.onCreate`). A partir daqui, compra/restauração sem
     * sujeito responde [StoreIdentityStatus.NO_SUBJECT]. Idempotente; não chama a loja.
     */
    fun declareManaged() {
        _managed.value = true
    }

    /**
     * Aplica um sujeito pontualmente — para o app que já observa a sessão por conta própria.
     * Idempotente: repetir o estado corrente não chama a loja.
     */
    suspend fun sync(subjectId: String?) = sync(StoreSubject.of(subjectId))

    /**
     * Aplica o estado completo pontualmente. [StoreSubject.Anonymous] anonimiza a loja **sempre que ela
     * não estiver anônima** (reemitir é seguro: com a loja já anônima não há chamada).
     */
    suspend fun sync(subject: StoreSubject) {
        lock.withLock {
            _managed.value = true
            val previous = _subject.value
            when (val next = normalize(subject)) {
                is StoreSubject.Account -> {
                    anonymousByRule = false
                    _subject.value = next.id
                    identifyLocked(next.id)
                }
                StoreSubject.Anonymous -> {
                    anonymousByRule = true
                    _subject.value = null
                    resetLocked()
                }
                StoreSubject.SignedOut -> {
                    anonymousByRule = false
                    _subject.value = null
                    if (previous != null) resetLocked() // null → null: nada a desfazer.
                }
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
        lock.withLock {
            val id = _subject.value
            when {
                id != null -> identifyLocked(id)
                // Loja que nasceu depois do estado "sem sujeito por regra": o SDK pode ter configurado
                // com o id guardado do aparelho (processo novo). Anonimiza.
                anonymousByRule -> resetLocked()
            }
        }
    }

    /** Volta ao estado de fábrica (testes / `MonetizationManager.reset()`). */
    internal fun reset() {
        _subject.value = null
        _managed.value = false
        anonymousByRule = false
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

    private fun normalize(subject: StoreSubject): StoreSubject =
        if (subject is StoreSubject.Account) StoreSubject.of(subject.id) else subject

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
