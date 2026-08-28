package br.com.codecacto.kmplib.ads.custom

import kotlinx.serialization.Serializable

/**
 * Anuncio "house" (proprio do app) servido pelo backend central **apps-api**.
 *
 * A partir da kmplib 2.38.0 os house ads sao por **projeto + superficie**: o app pede os anuncios do
 * seu `projectSlug` numa dada `surface` (default "app") e o servidor ja entrega APENAS os anuncios
 * ativos e segmentados. A lib nao filtra mais por `active`/janela/app/placement/priority/weight — ela
 * so renderiza o que vem e, quando ha mais de um ativo, escolhe um por rotacao simples
 * (ver [selectAd]).
 *
 * Schema de cada item na resposta `GET /public/ads`:
 * ```json
 * {
 *   "id": "ad1",
 *   "imageUrl": "https://.../banner.png",
 *   "targetUrl": "https://meusite.com/produto",
 *   "title": "",
 *   "ctaLabel": "Saiba mais",
 *   "format": "banner",
 *   "durationSeconds": 5
 * }
 * ```
 *
 * @property id identificador do anuncio (usado nas estatisticas).
 * @property imageUrl imagem do anuncio (banner ou criativo do interstitial).
 * @property targetUrl URL aberta ao clicar.
 * @property format "banner" para [CustomBannerAd], "interstitial" para [CustomInterstitialAd].
 *   Quando vier em branco (servidor nao envia), o anuncio casa com QUALQUER formato pedido — o
 *   composable (banner vs interstitial) decide onde aparece.
 * @property title titulo opcional exibido no interstitial.
 * @property ctaLabel rotulo opcional do botao de acao no interstitial.
 * @property durationSeconds duracao (em segundos) da contagem regressiva do interstitial no modo
 *   [InterstitialCloseMode.TIMED] — a barra de progresso no topo enche nesse tempo e so entao o "X"
 *   aparece. Configurado no cadastro do anuncio (painel de Anuncios); default [DEFAULT_DURATION_SECONDS]
 *   (5s) quando o servidor nao envia. Ignorado no modo [InterstitialCloseMode.IMMEDIATE].
 */
@Serializable
data class CustomAd(
    val id: String = "",
    val imageUrl: String = "",
    val targetUrl: String = "",
    val format: String = FORMAT_BANNER,
    val title: String = "",
    val ctaLabel: String = "",
    val durationSeconds: Int = DEFAULT_DURATION_SECONDS,
) {
    companion object {
        const val FORMAT_BANNER = "banner"
        const val FORMAT_INTERSTITIAL = "interstitial"

        /** Default da contagem regressiva do interstitial TIMED quando nao configurado. */
        const val DEFAULT_DURATION_SECONDS = 5
    }
}
