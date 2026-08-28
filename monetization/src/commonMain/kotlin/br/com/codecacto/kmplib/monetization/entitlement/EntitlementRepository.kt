package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.core.network.ApiResult

/**
 * Contrato de leitura/verificacao de monetizacao (plano/entitlement/uso/assert) — sempre da FONTE
 * DE VERDADE (o `admin-api` central; contrato `/monet/{slug}/...`). O cliente NUNCA decide o limite
 * nem se autopromove.
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

    /**
     * Verifica server-side (abordagem B) se o consumo de [feature] pode prosseguir, antes de
     * efetiva-lo no app. Centraliza o cliente de `POST /monet/{slug}/assert` que cada app freemium
     * recriava — trate o retorno [AssertResult.Denied] abrindo o Paywall.
     *
     * @param feature chave da feature consumida.
     * @param currentCount contagem atual reportada pelo cliente (gauge).
     * @param amount quanto se pretende consumir agora (default 1).
     */
    suspend fun assertUsage(
        feature: String,
        currentCount: Int,
        amount: Int = 1
    ): AssertResult
}

/**
 * Resultado de [EntitlementRepository.assertUsage] (mapeia o contrato `assert` do admin-api):
 * - [Allowed]: 200 `{ ok, data: { allowed: true } }` — pode prosseguir.
 * - [Denied]: 402 paywall — cota estourada; carrega o [QuotaExceeded] (de `error.details`) para a UI.
 * - [Failed]: erro de rede/HTTP nao-paywall (rede caiu, 401, 5xx...) — decisao de UX do app
 *   (ex.: liberar otimista offline ou bloquear).
 */
sealed interface AssertResult {
    /** Consumo liberado pelo servidor. */
    data object Allowed : AssertResult

    /** Cota estourada (402) — abrir Paywall com [quota]. */
    data class Denied(val quota: QuotaExceeded) : AssertResult

    /** Falha de verificacao (nao foi possivel confirmar a cota). */
    data class Failed(val code: Int = -1, val message: String) : AssertResult

    val isAllowed: Boolean get() = this is Allowed
}
