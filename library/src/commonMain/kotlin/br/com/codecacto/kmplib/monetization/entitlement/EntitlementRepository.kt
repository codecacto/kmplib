package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.core.network.ApiResult

/**
 * Contrato de leitura de monetizacao (plano/entitlement/uso) — sempre da FONTE DE VERDADE
 * (o `admin-api` central; doc 03 §1/§4). O cliente NUNCA decide o limite nem se autopromove.
 *
 * Injete a interface no ViewModel (Koin) e use fakes em `commonTest`. A implementacao canonica e
 * [AdminApiEntitlementRepository] (Ktor Client). Apps Firestore-only usam a mesma interface,
 * apontando para o mesmo admin-api.
 */
interface EntitlementRepository {

    /** Le o entitlement atual do tenant (plano + features liberadas). */
    suspend fun getEntitlement(): ApiResult<Entitlement>

    /** Le o medidor de uso de uma feature (para a UI "X de Y"). */
    suspend fun getUsage(feature: String): ApiResult<UsageSnapshot>

    /** Le o catalogo de planos do projeto (preco/limites) para o Paywall. */
    suspend fun getPlans(): ApiResult<List<Plan>>
}
