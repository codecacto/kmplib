package br.com.codecacto.kmplib.ads.custom

import kotlin.random.Random

/**
 * Escolhe um [CustomAd] entre os candidatos para um [format].
 *
 * O backend (apps-api) ja entrega apenas anuncios ativos e segmentados por projeto/superficie — entao
 * aqui nao ha mais filtro por placement/janela/app nem ordenacao por priority/weight. A escolha entre
 * os ativos e uma **rotacao simples** (sorteio uniforme), deixando o app decidir onde/quando exibir.
 *
 * Regras:
 *  - Filtra por [format] (anuncio com `format` em branco casa com qualquer formato pedido) e
 *    `imageUrl` nao vazia.
 *  - Se nenhum sobra, retorna null.
 *  - Caso contrario, sorteia um uniformemente entre os elegiveis.
 *
 * Determinismo de testes via [random].
 */
internal fun selectAd(
    candidates: List<CustomAd>,
    format: String,
    random: Random = Random.Default,
): CustomAd? {
    val eligible = candidates.filter { ad ->
        // Anuncio com `format` em branco casa com qualquer formato pedido. O backend REST entrega
        // house ads possivelmente sem `format`, deixando a superficie decidir onde aparece.
        (ad.format.isBlank() || ad.format == format) && ad.imageUrl.isNotBlank()
    }
    if (eligible.isEmpty()) return null
    if (eligible.size == 1) return eligible.first()
    return eligible[random.nextInt(eligible.size)]
}
