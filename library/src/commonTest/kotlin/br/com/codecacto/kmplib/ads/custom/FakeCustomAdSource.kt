package br.com.codecacto.kmplib.ads.custom

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake [CustomAdSource] in-memory para testes — sem rede.
 *
 * O servidor (apps-api) ja entrega os anuncios segmentados por projeto/superficie, entao o fake
 * apenas devolve os anuncios fornecidos, sem filtro client-side.
 */
class FakeCustomAdSource(
    initialAds: List<CustomAd> = emptyList(),
) : CustomAdSource {
    private val _state = MutableStateFlow(initialAds)

    fun emit(ads: List<CustomAd>) {
        _state.value = ads
    }

    override fun observeAds(): Flow<List<CustomAd>> = _state.asStateFlow()

    override suspend fun fetchAds(): Result<List<CustomAd>> = Result.success(_state.value)
}
