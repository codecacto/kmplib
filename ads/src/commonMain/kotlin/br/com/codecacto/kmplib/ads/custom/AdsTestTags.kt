package br.com.codecacto.kmplib.ads.custom

/**
 * **Ids dos anúncios para automação de UI** — mesmo desenho de `LoginTestTags` e `PaywallTestTags`,
 * e pela mesma razão: quem renderiza é a lib, então um id plantado no app não alcançaria nada.
 *
 * O consumidor aqui não é teste, é a **captura dos prints de loja**. O intersticial de abertura
 * dispara uma vez por sessão e cobre exatamente a primeira tela que a vitrine precisa mostrar — e a
 * tela por baixo continua na hierarquia, então a espera do flow passa e a foto sai com o anúncio na
 * frente. A Apple recusa print com publicidade sobreposta, então a captura precisa saber fechar.
 *
 * Ancorar por texto ("Fechar", "X") não serve: o rótulo muda com o idioma, e a fábrica publica em
 * quatro. Com o id, o flow é o mesmo nos 55 apps de publicidade.
 *
 * Para o Maestro enxergar como `resource-id`, a raiz declara `testTagsAsResourceId` — o `AppTheme`
 * faz isso desde a 2.107.0, sem o app configurar nada.
 */
object AdsTestTags {

    /** "X" que fecha o intersticial. Só existe depois de `canClose` (imediato ou pós-contagem). */
    const val BTN_FECHAR_INTERSTITIAL: String = "ads-btn-fechar-interstitial"
}
