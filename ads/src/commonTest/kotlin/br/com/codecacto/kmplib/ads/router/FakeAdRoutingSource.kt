package br.com.codecacto.kmplib.ads.router

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Fake [AdRoutingSource] in-memory para testes — sem rede.
 *
 * `null` no estado simula config nao publicada no servidor — o observer devolve
 * o `default` que [AdRouter] passou no observe.
 */
class FakeAdRoutingSource(
    initialRouting: AdRouting? = null,
) : AdRoutingSource {
    private val _state = MutableStateFlow(initialRouting)

    fun set(routing: AdRouting?) {
        _state.value = routing
    }

    override fun observeRouting(appId: String, default: AdRouting): Flow<AdRouting> =
        _state.asStateFlow().map { it ?: default }

    override suspend fun fetchRouting(appId: String, default: AdRouting): Result<AdRouting> =
        Result.success(_state.value ?: default)
}
