package br.com.codecacto.kmplib.ads.custom

/**
 * **Ids dos anúncios para automação de UI** — mesmo desenho de `LoginTestTags` e `PaywallTestTags`,
 * e pela mesma razão: quem renderiza é a lib, então um id plantado no app não alcançaria nada.
 *
 * Dois consumidores:
 * - **a captura dos prints de loja**, que precisa FECHAR o intersticial de abertura antes de
 *   fotografar (a Apple recusa print com publicidade sobreposta) — [BTN_FECHAR_INTERSTITIAL];
 * - **o teste de publicação** (Maestro, pela fila do Mac), que precisa PROVAR que o anúncio
 *   apareceu antes da 1ª publicação de um app com publicidade — os demais.
 *
 * ## "O espaço existe" × "o anúncio apareceu"
 *
 * Cada formato tem DOIS ids, e o teste ancora no segundo:
 *
 * | id | presente quando |
 * |---|---|
 * | [BANNER] / [INTERSTITIAL] | há um criativo escolhido e o contêiner dele está montado — a imagem pode estar baixando ou ter falhado |
 * | [BANNER_CARREGADO] / [INTERSTITIAL_CARREGADO] | a imagem do criativo **foi decodificada e pintada** (Coil devolveu `Success`) |
 *
 * Contêiner sem `-carregado` é diagnóstico, não sucesso: o anúncio foi escolhido mas a arte não
 * chegou (URL quebrada, CDN fora, sem rede). Nenhum dos dois = não há anúncio para mostrar
 * (usuário premium, roteamento `off` no apps-api, nenhum criativo cadastrado para o formato).
 *
 * ## Só house ad — não há AdMob na lib
 *
 * A publicidade da fábrica é só house ad servido pelo apps-api desde a 2.38.0 (ver `AdProvider`:
 * `CUSTOM`/`OFF`). Todo anúncio é um nó Compose desenhado pela lib, nos dois sistemas — não existe
 * janela nativa de SDK de terceiro a detectar por fora. Se um provedor de rede voltar a existir, ele
 * entra atrás destes MESMOS ids (o flow não muda).
 *
 * ## Como o Maestro enxerga
 *
 * Android: como `resource-id`, porque a raiz declara `testTagsAsResourceId` (o `AppTheme` faz isso
 * desde a 2.107.0). ⚠️ O intersticial é um `Dialog` — outra janela, com árvore de semântica própria,
 * que NÃO herda a flag da raiz; por isso o conteúdo dele se embrulha de novo (2.232.0). iOS: o
 * Compose publica a `testTag` como `accessibilityIdentifier`, sem flag.
 *
 * Ancorar por texto ("Fechar", "Anúncio") não serve: o rótulo muda com o idioma, e a fábrica publica
 * em quatro. Com o id, o flow é o mesmo em todos os apps de publicidade.
 */
object AdsTestTags {

    /** Contêiner do banner (qualquer `BannerSize`). Ver o quadro acima: NÃO prova que apareceu. */
    const val BANNER: String = "ads-banner"

    /** O criativo do banner, só depois de a imagem carregar. **É este que o teste afirma.** */
    const val BANNER_CARREGADO: String = "ads-banner-carregado"

    /**
     * Lugar do banner GRANDE dentro do estado vazio de uma lista (`EmptyStateWithBannerAd`, 2.254.0).
     * Sempre montado no estado vazio — prova o LUGAR; o anúncio em si continua sendo
     * [BANNER_CARREGADO]. Lista vazia: afirme os dois. Prefixo próprio (não `ads-banner-`) para o regex `ads-banner.*` não casar com ele mesmo sem anúncio.
     */
    const val BANNER_ESTADO_VAZIO: String = "ads-estado-vazio-banner"

    /** Contêiner em tela cheia do intersticial, montado assim que ele abre. */
    const val INTERSTITIAL: String = "ads-interstitial"

    /** O criativo do intersticial, só depois de a imagem carregar. **É este que o teste afirma.** */
    const val INTERSTITIAL_CARREGADO: String = "ads-interstitial-carregado"

    /** "X" que fecha o intersticial. Só existe depois de `canClose` (imediato ou pós-contagem). */
    const val BTN_FECHAR_INTERSTITIAL: String = "ads-btn-fechar-interstitial"

    /** Todos os ids, para conferência (unicidade, vocabulário) e para quem gera flows. */
    val all: List<String> = listOf(
        BANNER,
        BANNER_CARREGADO,
        BANNER_ESTADO_VAZIO,
        INTERSTITIAL,
        INTERSTITIAL_CARREGADO,
        BTN_FECHAR_INTERSTITIAL,
    )
}
