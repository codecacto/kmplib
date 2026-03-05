package br.com.codecacto.kmplib.firebase.ads

/**
 * Controller para app open ads.
 *
 * Uso:
 * ```kotlin
 * val controller = AdManager.appOpen
 * controller?.load()
 * controller?.show { /* onDismissed */ }
 * ```
 */
expect class AppOpenAdController() {
    /** Carrega um app open ad. */
    fun load()

    /**
     * Mostra o app open ad se estiver carregado.
     * Se não estiver carregado ou ads estiverem desabilitados, chama [onDismissed] imediatamente.
     */
    fun show(onDismissed: () -> Unit)

    /** Retorna true se há um ad carregado e pronto para exibir. */
    fun isLoaded(): Boolean
}
