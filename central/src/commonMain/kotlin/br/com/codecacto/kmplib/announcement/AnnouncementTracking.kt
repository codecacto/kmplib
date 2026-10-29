package br.com.codecacto.kmplib.announcement

import br.com.codecacto.kmplib.modalevents.ModalEventType
import br.com.codecacto.kmplib.modalevents.ModalEvents
import br.com.codecacto.kmplib.ui.announcement.Announcement
import br.com.codecacto.kmplib.ui.announcement.AnnouncementEvent
import br.com.codecacto.kmplib.ui.announcement.AnnouncementEventListener

/**
 * Estatística dos comunicados do Nexus (2.335.0): transforma os [AnnouncementEvent] da tela em eventos
 * do `POST /public/modal-events` (`surface = announcement`, `refId` = id do comunicado).
 *
 * **Só o que veio do servidor é enviado** ([isRemote]): comunicado LOCAL do app não existe em
 * `nexus.announcements` — não tem título no painel nem CTR que faça sentido, e o id dele é escolhido
 * pelo app (colidiria entre projetos). Decisão registrada no CHANGELOG da 2.335.0.
 *
 * `shown` sai **uma vez por comunicado por sessão** (processo): rotação, navegar e voltar ou trocar do
 * cartão para outra tela não contam de novo — é a "exibição real" do contrato. Chamado pelos hosts na
 * thread da composição (principal).
 */
class AnnouncementEventsReporter(
    private val record: (ModalEventType, String) -> Unit = { type, id -> ModalEvents.record(type, refId = id) },
) {
    private val shownIds = HashSet<String>()

    /** Registra [event] de [announcement] se [isRemote] disser que veio do servidor. */
    fun report(announcement: Announcement, event: AnnouncementEvent, isRemote: Boolean) {
        if (!isRemote) return
        val id = announcement.id.trim()
        if (id.isEmpty()) return
        val type = when (event) {
            AnnouncementEvent.SHOWN -> {
                val first = shownIds.add(id)
                if (!first) return
                ModalEventType.ANNOUNCEMENT_SHOWN
            }
            AnnouncementEvent.ACTION_CLICK -> ModalEventType.ANNOUNCEMENT_ACTION_CLICK
            AnnouncementEvent.DISMISS_GOT_IT -> ModalEventType.ANNOUNCEMENT_DISMISS_GOTIT
            AnnouncementEvent.DISMISS_CLOSE -> ModalEventType.ANNOUNCEMENT_DISMISS_CLOSE
        }
        record(type, id)
    }

    /** O ouvinte para um host: envia os de [remoteIds] e repassa tudo a [downstream]. */
    fun listener(remoteIds: Set<String>, downstream: AnnouncementEventListener? = null): AnnouncementEventListener =
        AnnouncementEventListener { a, e ->
            report(a, e, isRemote = a.id in remoteIds)
            downstream?.onEvent(a, e)
        }

    companion object {
        /** O do processo. */
        val Shared: AnnouncementEventsReporter = AnnouncementEventsReporter()
    }
}
