package br.com.codecacto.kmplib.push.action

/**
 * O que fazer quando a pessoa toca numa notificação de campanha do Nexus (2.360.0). Vem dos dados do
 * push: `cc_acao` = `app` | `premium` | `comunicado` | `link`, mais `cc_campanha`, `cc_comunicado`,
 * `cc_url`. Leia com [PushActions.parse].
 */
sealed interface PushAction {
    /** Id da campanha (`cc_campanha`), quando veio. */
    val campaignId: String?

    /** Só abre o app (`app`, ou ação desconhecida/incompleta — nunca derruba nem ignora o toque). */
    data class OpenApp(override val campaignId: String?) : PushAction

    /** Abre a tela Premium do app (`premium`). */
    data class OpenPremium(override val campaignId: String?) : PushAction

    /** Abre o comunicado [announcementId] no mesmo modal dos Comunicados (`comunicado`). */
    data class OpenAnnouncement(val announcementId: String, override val campaignId: String?) : PushAction

    /** Abre [url] (só `http`/`https`) no navegador externo (`link`). */
    data class OpenLink(val url: String, override val campaignId: String?) : PushAction
}

/** Leitura dos dados do push das campanhas do Nexus — pura, testada. */
object PushActions {
    const val KEY_ACTION: String = "cc_acao"
    const val KEY_CAMPAIGN: String = "cc_campanha"
    const val KEY_ANNOUNCEMENT: String = "cc_comunicado"
    const val KEY_URL: String = "cc_url"

    const val ACTION_APP: String = "app"
    const val ACTION_PREMIUM: String = "premium"
    const val ACTION_ANNOUNCEMENT: String = "comunicado"
    const val ACTION_LINK: String = "link"

    /** `true` se a carga é de campanha do Nexus (tem `cc_acao`). */
    fun isCampaign(data: Map<String, String>): Boolean = !data[KEY_ACTION].isNullOrBlank()

    /**
     * A ação do toque, ou `null` quando a carga NÃO é de campanha (sem `cc_acao` — é push do próprio
     * app, que segue o caminho dele). Ação desconhecida, comunicado sem id ou link que não seja
     * `http(s)` viram [PushAction.OpenApp]: o toque abre o app e nada mais (nunca um esquema
     * arbitrário — `intent:`, `javascript:`, `file:` — vindo de fora).
     */
    fun parse(data: Map<String, String>): PushAction? {
        val action = data[KEY_ACTION]?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        val campaign = data[KEY_CAMPAIGN]?.trim()?.takeIf { it.isNotEmpty() }
        return when (action) {
            ACTION_PREMIUM -> PushAction.OpenPremium(campaign)
            ACTION_ANNOUNCEMENT -> data[KEY_ANNOUNCEMENT]?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { PushAction.OpenAnnouncement(it, campaign) }
                ?: PushAction.OpenApp(campaign)
            ACTION_LINK -> data[KEY_URL]?.trim()?.takeIf(::isSafeExternalUrl)
                ?.let { PushAction.OpenLink(it, campaign) }
                ?: PushAction.OpenApp(campaign)
            else -> PushAction.OpenApp(campaign)
        }
    }

    /** `http://` ou `https://` com host, sem espaço nem caractere de controle. */
    fun isSafeExternalUrl(url: String): Boolean {
        val lower = url.lowercase()
        val rest = when {
            lower.startsWith("https://") -> url.substring(8)
            lower.startsWith("http://") -> url.substring(7)
            else -> return false
        }
        if (url.any { it.isWhitespace() || it.code < 0x20 || it.code == 0x7f }) return false
        val host = rest.substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
        return host.isNotEmpty() && !host.startsWith(':')
    }
}
