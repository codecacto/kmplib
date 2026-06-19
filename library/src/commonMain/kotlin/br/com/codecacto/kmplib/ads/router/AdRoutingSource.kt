package br.com.codecacto.kmplib.ads.router

import kotlinx.coroutines.flow.Flow

/**
 * Fonte generica do [AdRouting] de um app. Injetavel nos testes (fake).
 */
interface AdRoutingSource {
    fun observeRouting(appId: String, default: AdRouting): Flow<AdRouting>
    suspend fun fetchRouting(appId: String, default: AdRouting): Result<AdRouting>
}
