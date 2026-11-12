package br.com.codecacto.kmplib.push.action

import br.com.codecacto.kmplib.announcement.AnnouncementClient
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.ui.announcement.Announcement
import kotlin.time.Duration

/**
 * O roteador do toque das campanhas do Nexus, sem tela (2.360.0) — o [br.com.codecacto.kmplib.push.ui.PushTapHost]
 * o usa; app com navegação própria pode usá-lo direto.
 *
 * @param onOpenPremium abre a tela Premium do app (`cc_acao = premium`).
 * @param openUrl abre `cc_url` no navegador externo (`link`; já conferido `http(s)`).
 * @param findAnnouncement acha o comunicado pelo id (`comunicado`); `null` = não achou (o toque só
 *   abre o app). Default do host: [findAnnouncementById].
 * @param showAnnouncement mostra o comunicado achado (o host abre o `AnnouncementDialog`).
 * @param onOtherTap toque de push que NÃO é de campanha (sem `cc_acao`) — o deep link do próprio app.
 */
class PushTapRouter(
    private val onOpenPremium: () -> Unit,
    private val openUrl: (String) -> Unit,
    private val findAnnouncement: suspend (String) -> Announcement?,
    private val showAnnouncement: (Announcement) -> Unit,
    private val onOtherTap: (Map<String, String>) -> Unit = {},
) {
    /** Roteia [data]; devolve a ação de campanha tratada, ou `null` se foi para [onOtherTap]. */
    suspend fun route(data: Map<String, String>): PushAction? {
        val action = PushActions.parse(data) ?: run {
            onOtherTap(data)
            return null
        }
        when (action) {
            is PushAction.OpenApp -> Unit
            is PushAction.OpenPremium -> onOpenPremium()
            is PushAction.OpenLink -> openUrl(action.url)
            is PushAction.OpenAnnouncement -> {
                val found = runCatching { findAnnouncement(action.announcementId) }.getOrNull()
                if (found != null) {
                    showAnnouncement(found)
                } else {
                    AppLogger.w(TAG, "Comunicado do push não encontrado (fora da janela ou removido) — só abre o app.")
                }
            }
        }
        return action
    }

    private companion object {
        const val TAG = "PushTapRouter"
    }
}

/**
 * Acha o comunicado [id] entre os avisos do app no apps-api.
 *
 * ⚠️ O apps-api **não tem busca de comunicado por id** (só `GET /public/announcements?project&platform&locale`,
 * que devolve os ativos daquele app/plataforma/idioma). O caminho mais próximo, e o usado aqui: força
 * uma leitura nova da lista ([AnnouncementClient.refresh] sem intervalo mínimo) e procura o id nela;
 * sem rede, procura na última lista boa do processo. Consequência: comunicado inativo, fora da
 * visibilidade ou de outra plataforma não abre — a campanha deve apontar para um comunicado ATIVO
 * daquele app (o Nexus já oferece só esses).
 */
suspend fun findAnnouncementById(client: AnnouncementClient?, id: String): Announcement? {
    if (client == null) return null
    client.refresh(minInterval = Duration.ZERO)
    return client.latest.value.firstOrNull { it.id == id }
}
