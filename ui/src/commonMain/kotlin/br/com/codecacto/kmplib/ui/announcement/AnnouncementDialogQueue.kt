@file:OptIn(ExperimentalTime::class)

package br.com.codecacto.kmplib.ui.announcement

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * ─────────────────────── Fila do comunicado em MODAL (2.296.0) ───────────────────────
 *
 * Regras PURAS (testadas em commonTest) + o controlador da sessão. Spec de design compartilhada com a
 * weblib: `Lib/weblib/docs/design/comunicado-modal.md` §3.3 e §4.3.
 *
 * - Um por vez. Ordem: o que BLOQUEIA agora → prioridade do tipo → `visibleFrom` mais recente → ordem
 *   da lista.
 * - Teto de [DEFAULT_ANNOUNCEMENT_DIALOGS_PER_SESSION] modais por sessão (processo). O excedente fica
 *   para a próxima abertura, respeitando a `frequency`. O que bloqueia não conta contra o teto.
 * - `dismissible = false` só bloqueia DURANTE a janela do evento; antes dela o modal é fechável por
 *   sessão (o "Entendi" não grava nada — volta na próxima abertura).
 */

/** Teto default de comunicados em modal numa sessão (decisão do fundador, 12/out/2026). */
const val DEFAULT_ANNOUNCEMENT_DIALOGS_PER_SESSION: Int = 3

/**
 * `true` se o comunicado BLOQUEIA o app em [now]: não dispensável **e** dentro do período do evento —
 * `[window.start, window.end)` se houver janela; sem janela, só se houver `visibleUntil` e [now] estiver
 * dentro da visibilidade (um não-fechável sem fim nenhum nunca bloqueia: travaria o app para sempre).
 */
fun Announcement.isBlockingAt(now: Instant): Boolean {
    if (dismissible) return false
    val w = window
    if (w != null && w.end > w.start) return now >= w.start && now < w.end
    return visibleUntil != null && isVisibleAt(now)
}

/** `true` se o modal oferece fechar (X, "Entendi", voltar, toque fora) em [now] — o oposto de [isBlockingAt]. */
fun Announcement.isDialogDismissibleAt(now: Instant): Boolean = !isBlockingAt(now)

/**
 * `true` se o comunicado ainda deve aparecer em modal, pelo histórico e pela sessão.
 *
 * - Bloqueando agora: sempre (a manutenção em andamento aparece mesmo já vista ou fechada antes).
 * - Fechado nesta sessão: não.
 * - Não dispensável fora da janela: sim (fechável por sessão, nada gravado).
 * - Demais: a regra de frequência do banner ([isPending]).
 */
fun Announcement.isDialogPending(
    record: AnnouncementRecord,
    now: Instant,
    shownThisSession: Set<String> = emptySet(),
    dismissedThisSession: Set<String> = emptySet(),
): Boolean {
    if (isBlockingAt(now)) return true
    if (id in dismissedThisSession) return false
    if (!dismissible) return true
    return isPending(record, shownThisSession, dismissedThisSession)
}

/**
 * A fila do modal em [now] — já ordenada e cortada no teto da sessão. O primeiro é o da vez.
 *
 * @param shownThisSession ids JÁ exibidos em modal nesta sessão — contam contra [maxPerSession] e
 *   continuam na fila enquanto pendentes (um ONCE aberto não some ao recompor).
 * @param maxPerSession teto de comunicados distintos por sessão; os que bloqueiam passam por cima.
 */
fun selectAnnouncementDialogQueue(
    announcements: List<Announcement>,
    now: Instant,
    records: Map<String, AnnouncementRecord> = emptyMap(),
    shownThisSession: Set<String> = emptySet(),
    dismissedThisSession: Set<String> = emptySet(),
    maxPerSession: Int = DEFAULT_ANNOUNCEMENT_DIALOGS_PER_SESSION,
): List<Announcement> {
    val ordered = announcements
        .distinctBy { it.id }
        .withIndex()
        .filter { (_, a) ->
            a.title.isNotBlank() &&
                a.isVisibleAt(now) &&
                a.isDialogPending(records[a.id] ?: AnnouncementRecord.NONE, now, shownThisSession, dismissedThisSession)
        }
        .sortedWith(
            compareByDescending<IndexedValue<Announcement>> { it.value.isBlockingAt(now) }
                .thenByDescending { it.value.kind.announcementPriority }
                .thenByDescending { it.value.visibleFrom?.toEpochMilliseconds() ?: Long.MIN_VALUE }
                .thenBy { it.index },
        )
        .map { it.value }
    var newSlots = (maxPerSession - shownThisSession.size).coerceAtLeast(0)
    return ordered.filter { a ->
        when {
            a.isBlockingAt(now) -> true
            a.id in shownThisSession -> true
            newSlots > 0 -> { newSlots--; true }
            else -> false
        }
    }
}

/**
 * O que a sessão (o PROCESSO) lembra dos modais: quais já apareceram e quais foram fechados. É
 * compartilhada por todas as telas — navegar não reabre o que a pessoa acabou de fechar nem fura o
 * teto. [Shared] é a do processo; teste e `@Preview` criam a sua.
 */
class AnnouncementDialogSession {
    internal val mutex = Mutex()
    internal val shown = LinkedHashSet<String>()
    internal val dismissed = LinkedHashSet<String>()

    /** Quantos comunicados distintos já apareceram em modal nesta sessão. */
    val shownCount: Int get() = shown.size

    companion object {
        /** A sessão do processo. */
        val Shared: AnnouncementDialogSession = AnnouncementDialogSession()
    }
}

/**
 * O estado visível do modal.
 *
 * @property current o comunicado da vez (null = nenhum).
 * @property position posição na fila da sessão, a partir de 1 ("1 de 3").
 * @property total tamanho da fila da sessão; o contador só aparece com `total > 1`.
 * @property dismissible se o modal oferece fechar agora ([isDialogDismissibleAt]).
 * @property now o instante da avaliação (texto da janela e o selo "Em andamento").
 */
data class AnnouncementDialogUiState(
    val current: Announcement? = null,
    val position: Int = 0,
    val total: Int = 0,
    val dismissible: Boolean = true,
    val now: Instant = Instant.fromEpochMilliseconds(0),
) {
    /** `true` se o contador "n de N" deve aparecer. */
    val showsCounter: Boolean get() = current != null && total > 1
}

/**
 * A fila do modal, fora do Compose (o [rememberAnnouncementDialogState] embrulha).
 *
 * - [submit] recebe a lista (só os de apresentação [AnnouncementPresentation.MODAL] entram), lê o
 *   histórico do [store] e monta a fila ([selectAnnouncementDialogQueue]).
 * - O da vez é marcado VISTO (aparelho) e EXIBIDO (sessão) na hora em que aparece.
 * - [dismiss] fecha o da vez: grava a dispensa se o aviso for dispensável e não `ALWAYS`; um não
 *   dispensável fora da janela fecha só na sessão; um que bloqueia ignora o pedido.
 */
class AnnouncementDialogController(
    private val store: AnnouncementStore,
    private val clock: Clock = Clock.System,
    private val session: AnnouncementDialogSession = AnnouncementDialogSession.Shared,
    private val maxPerSession: Int = DEFAULT_ANNOUNCEMENT_DIALOGS_PER_SESSION,
) {
    private val mutex = Mutex()
    private var announcements: List<Announcement> = emptyList()
    private val records = HashMap<String, AnnouncementRecord>()
    private var closedInQueue = 0

    private val _state = MutableStateFlow(AnnouncementDialogUiState(now = clock.now()))

    /** O estado do modal. */
    val state: StateFlow<AnnouncementDialogUiState> = _state.asStateFlow()

    /** Troca a lista e reavalia. Comunicados com apresentação BANNER são ignorados aqui. */
    suspend fun submit(list: List<Announcement>) = mutex.withLock {
        announcements = list.filter { it.presentation == AnnouncementPresentation.MODAL }.distinctBy { it.id }
        for (a in announcements) {
            if (a.id !in records) records[a.id] = store.read(a.id)
        }
        reevaluateLocked()
    }

    /** Reaplica o relógio (a manutenção começou ou terminou). */
    suspend fun reevaluate() = mutex.withLock { reevaluateLocked() }

    /** Fecha [id] (X, "Entendi" ou a ação) e passa ao próximo. */
    suspend fun dismiss(id: String) = mutex.withLock {
        val announcement = announcements.firstOrNull { it.id == id } ?: return@withLock
        val now = clock.now()
        if (announcement.isBlockingAt(now)) return@withLock
        session.mutex.withLock { session.dismissed += id }
        if (announcement.dismissible && announcement.frequency != AnnouncementFrequency.ALWAYS) {
            store.markDismissed(id, now)
            records[id] = (records[id] ?: AnnouncementRecord.NONE).copy(dismissedAt = now)
        }
        closedInQueue++
        reevaluateLocked()
    }

    /** O próximo instante em que a fila muda sozinha (null = nunca). */
    fun nextChangeAt(): Instant? = nextAnnouncementChange(announcements, clock.now())

    private suspend fun reevaluateLocked() {
        val now = clock.now()
        val queue = session.mutex.withLock {
            selectAnnouncementDialogQueue(
                announcements = announcements,
                now = now,
                records = records,
                shownThisSession = session.shown,
                dismissedThisSession = session.dismissed,
                maxPerSession = maxPerSession,
            )
        }
        val pick = queue.firstOrNull()
        if (pick != null) {
            val firstTime = session.mutex.withLock { session.shown.add(pick.id) }
            if (firstTime) store.markSeen(pick.id, now)
        } else {
            closedInQueue = 0
        }
        _state.value = AnnouncementDialogUiState(
            current = pick,
            position = if (pick == null) 0 else closedInQueue + 1,
            total = if (pick == null) 0 else closedInQueue + queue.size,
            dismissible = pick?.isDialogDismissibleAt(now) ?: true,
            now = now,
        )
    }
}
