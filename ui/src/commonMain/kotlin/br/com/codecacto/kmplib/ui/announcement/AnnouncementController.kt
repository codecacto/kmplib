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

/**
 * O estado de UMA sessão de avisos, fora do Compose (o [rememberAnnouncementState] embrulha este
 * objeto; ViewModel que prefira também pode usá-lo direto).
 *
 * - [submit] recebe a lista (inteira, a cada mudança), lê do [store] o histórico dos ids novos e
 *   escolhe o aviso da vez ([selectAnnouncement]).
 * - O aviso escolhido é gravado como VISTO na hora em que aparece; por isso um
 *   [AnnouncementFrequency.ONCE] continua na tela até o fim desta sessão e não volta na próxima.
 * - [dismiss] grava a dispensa (exceto [AnnouncementFrequency.ALWAYS], cujo X vale só para a sessão)
 *   e passa ao próximo pendente. Aviso não dispensável ignora o pedido.
 * - [reevaluate] reaplica o relógio (um aviso que entrou ou saiu da janela); [nextChangeAt] diz quando
 *   vale a pena chamar.
 */
class AnnouncementController(
    private val store: AnnouncementStore,
    private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()
    private var announcements: List<Announcement> = emptyList()
    private val records = HashMap<String, AnnouncementRecord>()
    private val shownThisSession = HashSet<String>()
    private val dismissedThisSession = HashSet<String>()

    private val _current = MutableStateFlow<Announcement?>(null)

    /** O aviso da vez (null = nenhum). */
    val current: StateFlow<Announcement?> = _current.asStateFlow()

    private val _now = MutableStateFlow(clock.now())

    /** O instante da última avaliação — o cartão escreve "em andamento"/"previsto" a partir dele. */
    val now: StateFlow<Instant> = _now.asStateFlow()

    /** Troca a lista de avisos e reavalia. */
    suspend fun submit(list: List<Announcement>) = mutex.withLock {
        announcements = list.distinctBy { it.id }
        for (a in announcements) {
            if (a.id !in records) records[a.id] = store.read(a.id)
        }
        reevaluateLocked()
    }

    /** Reaplica o relógio à lista atual. */
    suspend fun reevaluate() = mutex.withLock { reevaluateLocked() }

    /** Dispensa [id] (o X do cartão) e passa ao próximo pendente. */
    suspend fun dismiss(id: String) = mutex.withLock {
        val announcement = announcements.firstOrNull { it.id == id } ?: return@withLock
        if (!announcement.dismissible) return@withLock
        dismissedThisSession += id
        if (announcement.frequency != AnnouncementFrequency.ALWAYS) {
            val at = clock.now()
            store.markDismissed(id, at)
            records[id] = (records[id] ?: AnnouncementRecord.NONE).copy(dismissedAt = at)
        }
        reevaluateLocked()
    }

    /** O próximo instante em que a escolha ou o texto da janela muda sozinho (null = nunca). */
    fun nextChangeAt(): Instant? = nextAnnouncementChange(announcements, clock.now())

    private suspend fun reevaluateLocked() {
        val now = clock.now()
        val pick = selectAnnouncement(announcements, now, records, shownThisSession, dismissedThisSession)
        if (pick != null && shownThisSession.add(pick.id)) {
            store.markSeen(pick.id, now)
        }
        _now.value = now
        _current.value = pick
    }
}
