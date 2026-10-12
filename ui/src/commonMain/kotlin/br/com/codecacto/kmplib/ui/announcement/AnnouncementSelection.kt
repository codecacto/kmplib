@file:OptIn(ExperimentalTime::class)

package br.com.codecacto.kmplib.ui.announcement

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * Regras PURAS dos avisos — sem Compose, sem disco, sem relógio de parede. O controlador e o cartão
 * só chamam estas funções; é aqui que mora o que os testes travam.
 */

/**
 * Prioridade do tipo: maior aparece primeiro.
 * MAINTENANCE (5) > UPDATE (4) > CROSS_PLATFORM (3) > NEWS (2) > INFO (1).
 * Empate: a ordem da lista (a ordem do servidor).
 */
val AnnouncementKind.announcementPriority: Int
    get() = when (this) {
        AnnouncementKind.MAINTENANCE -> 5
        AnnouncementKind.UPDATE -> 4
        AnnouncementKind.CROSS_PLATFORM -> 3
        AnnouncementKind.NEWS -> 2
        AnnouncementKind.INFO -> 1
    }

/**
 * Até quando o aviso aparece: [Announcement.visibleUntil] se houver; senão, com [Announcement.window]
 * válida, o fim do evento (manutenção que já terminou não fica na tela); senão, `null` (sem fim).
 */
val Announcement.effectiveVisibleUntil: Instant?
    get() = visibleUntil ?: window?.takeIf { it.end > it.start }?.end

/** `true` se [now] está dentro da janela de visibilidade: `visibleFrom <= now < effectiveVisibleUntil`. */
fun Announcement.isVisibleAt(now: Instant): Boolean {
    val from = visibleFrom
    if (from != null && now < from) return false
    val until = effectiveVisibleUntil
    if (until != null && now >= until) return false
    return true
}

/**
 * O que o aparelho lembra de um aviso, por id.
 *
 * @property seenAt a primeira vez em que foi EXIBIDO (null = nunca).
 * @property dismissedAt quando a pessoa tocou no X (null = nunca). Não se grava para
 *   [AnnouncementFrequency.ALWAYS], cujo X vale só para a sessão.
 */
data class AnnouncementRecord(
    val seenAt: Instant? = null,
    val dismissedAt: Instant? = null,
) {
    companion object {
        val NONE = AnnouncementRecord()
    }
}

/**
 * `true` se, pela frequência e pelo histórico, o aviso ainda deve ser mostrado (independe do relógio).
 *
 * @param record o que o aparelho lembrava ANTES desta sessão (o "visto" gravado nesta sessão não conta
 *   contra ela — [shownThisSession] cobre isso).
 * @param shownThisSession ids exibidos nesta sessão (um ONCE continua na tela até o fim dela).
 * @param dismissedThisSession ids dispensados nesta sessão (vale para todas as frequências).
 */
fun Announcement.isPending(
    record: AnnouncementRecord,
    shownThisSession: Set<String> = emptySet(),
    dismissedThisSession: Set<String> = emptySet(),
): Boolean {
    if (dismissible && id in dismissedThisSession) return false
    return when (frequency) {
        AnnouncementFrequency.ONCE ->
            !(dismissible && record.dismissedAt != null) &&
                (record.seenAt == null || id in shownThisSession)
        AnnouncementFrequency.UNTIL_DISMISSED -> !(dismissible && record.dismissedAt != null)
        AnnouncementFrequency.ALWAYS -> true
    }
}

/**
 * Escolhe o aviso da vez: o primeiro PENDENTE ([isPending]) e VISÍVEL em [now] ([isVisibleAt]), pela
 * prioridade do tipo ([announcementPriority]); empate pela ordem da lista. Ids repetidos: vale o
 * primeiro. Título em branco não aparece.
 */
fun selectAnnouncement(
    announcements: List<Announcement>,
    now: Instant,
    records: Map<String, AnnouncementRecord> = emptyMap(),
    shownThisSession: Set<String> = emptySet(),
    dismissedThisSession: Set<String> = emptySet(),
): Announcement? =
    announcements
        .distinctBy { it.id }
        .withIndex()
        .filter { (_, a) ->
            a.title.isNotBlank() &&
                a.isVisibleAt(now) &&
                a.isPending(records[a.id] ?: AnnouncementRecord.NONE, shownThisSession, dismissedThisSession)
        }
        .sortedWith(compareByDescending<IndexedValue<Announcement>> { it.value.kind.announcementPriority }.thenBy { it.index })
        .firstOrNull()
        ?.value

/**
 * O próximo instante, depois de [now], em que a escolha ou o texto da janela pode mudar (um aviso
 * entra, sai, ou a manutenção começa). `null` = nada muda sozinho.
 */
fun nextAnnouncementChange(announcements: List<Announcement>, now: Instant): Instant? =
    announcements
        .flatMap { listOfNotNull(it.visibleFrom, it.effectiveVisibleUntil, it.window?.start, it.window?.end) }
        .filter { it > now }
        .minOrNull()

/** Em que ponto o evento está em relação a agora. */
enum class AnnouncementWindowPhase { UPCOMING, ONGOING, ENDED }

/**
 * A janela já convertida para a parede do aparelho — o que o cartão precisa para escrever o texto.
 *
 * @property sameDay início e fim no mesmo dia local ("sábado, 18 de outubro, das 22:00 às 23:30");
 *   senão o texto leva data e hora nas duas pontas.
 * @property sameYearAsNow as duas pontas no ano corrente — o ano não precisa aparecer.
 */
data class AnnouncementWindowDescription(
    val phase: AnnouncementWindowPhase,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val sameDay: Boolean,
    val sameYearAsNow: Boolean,
)

/** Descreve [window] no fuso [timeZone] (o do aparelho, no cartão) em relação a [now]. */
fun describeAnnouncementWindow(
    window: AnnouncementWindow,
    now: Instant,
    timeZone: TimeZone,
): AnnouncementWindowDescription {
    val start = window.start.toLocalDateTime(timeZone)
    val end = window.end.toLocalDateTime(timeZone)
    val phase = when {
        now < window.start -> AnnouncementWindowPhase.UPCOMING
        now < window.end -> AnnouncementWindowPhase.ONGOING
        else -> AnnouncementWindowPhase.ENDED
    }
    val nowYear = now.toLocalDateTime(timeZone).year
    return AnnouncementWindowDescription(
        phase = phase,
        start = start,
        end = end,
        sameDay = start.date == end.date,
        sameYearAsNow = start.year == nowYear && end.year == nowYear,
    )
}
