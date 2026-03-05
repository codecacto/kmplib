package br.com.codecacto.kmplib.firebase.ads

/**
 * Controller para interstitial ads.
 *
 * Uso:
 * ```kotlin
 * val controller = AdManager.interstitial
 * controller?.load()
 * // Quando quiser mostrar:
 * controller?.show { /* onDismissed */ }
 * ```
 */
expect class InterstitialAdController() {
    /** Carrega um interstitial ad. */
    fun load()

    /**
     * Mostra o interstitial ad se estiver carregado.
     * Se não estiver carregado ou ads estiverem desabilitados, chama [onDismissed] imediatamente.
     */
    fun show(onDismissed: () -> Unit)

    /** Retorna true se há um ad carregado e pronto para exibir. */
    fun isLoaded(): Boolean
}
