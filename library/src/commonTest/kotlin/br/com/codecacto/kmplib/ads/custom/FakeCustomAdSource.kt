package br.com.codecacto.kmplib.ads.custom

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Fake [CustomAdSource] in-memory para testes — sem Firebase.
 *
 * Aplica os mesmos filtros (`appId` + `placementId` + janela de tempo) que
 * [CustomAdRepository] pra fielmente representar o que o manager receberia.
 */
class FakeCustomAdSource(
    initialAds: List<CustomAd> = emptyList(),
    private val nowProvider: () -> Long = { 0L }
) : CustomAdSource {
    private val _state = MutableStateFlow(initialAds)

    fun emit(ads: List<CustomAd>) {
        _state.value = ads
    }

    override fun observeAds(placementId: String?, appId: String?): Flow<List<CustomAd>> =
        _state.asStateFlow().map { ads -> ads.filterFor(placementId, appId) }

    override suspend fun fetchAds(placementId: String?, appId: String?): Result<List<CustomAd>> =
        Result.success(_state.value.filterFor(placementId, appId))

    private fun List<CustomAd>.filterFor(placementId: String?, appId: String?): List<CustomAd> {
        val now = nowProvider()
        return filter {
            matchesAppId(it, appId) &&
                matchesPlacement(it, placementId) &&
                isInTimeWindow(it, now)
        }
    }
}
