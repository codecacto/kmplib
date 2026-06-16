package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.core.network.ApiResult

/**
 * Contrato de **leitura** de entitlement/uso/planos a partir da fonte de verdade central
 * (admin-api / backlib-quota).
 *
 * Apenas LE — o cliente nunca decide nem incrementa quota; o enforcement e server-side.
 * Implementacao padrao: [AdminApiEntitlementRepository]. Em testes, use um fake.
 */
interface EntitlementRepository {

    /** Entitlement vigente do usuario para o projeto. */
    suspend fun getEntitlement(): ApiResult<Entitlement>

    /** Snapshot de uso de uma feature consumivel especifica. */
    suspend fun getUsage(feature: String): ApiResult<UsageSnapshot>

    /** Planos disponiveis para upgrade. */
    suspend fun getPlans(): ApiResult<List<Plan>>
}
