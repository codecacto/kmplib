package br.com.codecacto.kmplib.ads.custom

import kotlinx.coroutines.flow.Flow

/**
 * Fonte de dados dos [CustomAd]s de um app. Injetavel nos testes (fake).
 *
 * A fonte ja e configurada com `projectSlug` + `surface` no construtor (ver [RestCustomAdSource]),
 * entao os metodos nao recebem mais filtros — o servidor faz a segmentacao por projeto/superficie.
 */
interface CustomAdSource {
    /** Emite a lista de anuncios ativos. No REST, emite uma vez (busca one-shot) e completa. */
    fun observeAds(): Flow<List<CustomAd>>

    /** Busca one-shot dos anuncios ativos. Best-effort: erro -> lista vazia. */
    suspend fun fetchAds(): Result<List<CustomAd>>
}
