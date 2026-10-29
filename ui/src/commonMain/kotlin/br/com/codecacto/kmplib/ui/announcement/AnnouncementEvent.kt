package br.com.codecacto.kmplib.ui.announcement

/**
 * O que aconteceu com um comunicado na tela (2.335.0) — para estatística. A lib de UI não manda nada a
 * servidor nenhum: quem escuta decide (o `RemoteAnnouncementHost` da `kmplib-central` envia ao apps-api
 * os eventos dos comunicados vindos do Nexus).
 */
enum class AnnouncementEvent {
    /**
     * Apareceu na tela (modal aberto ou cartão desenhado). Sai uma vez por entrada do comunicado no
     * host; se o host sair e voltar à composição (rotação, navegação), sai de novo — quem conta
     * "exibições" deduplica por id na sessão (o `RemoteAnnouncementHost` faz).
     */
    SHOWN,

    /** Tocou no botão de ação. */
    ACTION_CLICK,

    /** Fechou pelo "Entendi" (só o modal tem). */
    DISMISS_GOT_IT,

    /** Fechou pelo X, voltar ou toque fora. */
    DISMISS_CLOSE,
}

/** Ouvinte dos [AnnouncementEvent] — parâmetro `onEvent` dos hosts de comunicado. */
fun interface AnnouncementEventListener {
    fun onEvent(announcement: Announcement, event: AnnouncementEvent)
}
