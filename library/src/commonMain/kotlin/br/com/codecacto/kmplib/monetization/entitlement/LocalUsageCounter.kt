package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.core.prefs.AppPreferences

/**
 * Contador de uso LOCAL (offline) — camada de **UX apenas**.
 *
 * > A autoridade do limite e sempre o servidor (admin-api/`backlib-quota`, doc 03 §1). Este contador
 * > NAO concede acao consumivel; serve para exibir "X de Y" e abrir o paywall de forma otimista
 * > quando o app esta offline ou ainda nao consultou o admin-api. Quando o servidor responde, o
 * > valor do servidor prevalece.
 *
 * Persiste a contagem por feature via [AppPreferences] (chave isolada por projeto/feature). NAO
 * implementa reset de janela mensal por conta propria — o reset autoritativo vem do admin-api; aqui
 * o app pode, opcionalmente, zerar via [reset] no virar do mes detectado pela UI.
 */
class LocalUsageCounter(
    private val prefs: AppPreferences,
    private val projectSlug: String
) {
    private fun key(feature: String) = "quota_usage:${projectSlug}:${feature}"

    /** Le a contagem local atual da feature. */
    suspend fun count(feature: String): Int = prefs.getInt(key(feature), 0)

    /** Incrementa a contagem local (apos a acao otimista) e retorna o novo valor. */
    suspend fun increment(feature: String, amount: Int = 1): Int {
        val next = count(feature) + amount
        prefs.setInt(key(feature), next)
        return next
    }

    /** Zera a contagem local da feature (ex.: virada de janela detectada pela UI). */
    suspend fun reset(feature: String) {
        prefs.setInt(key(feature), 0)
    }

    /**
     * Monta um [UsageSnapshot] local "X de Y" para a UI, dado o limite do plano free.
     * `limite < 0` => ilimitado.
     */
    suspend fun snapshot(feature: String, limite: Int): UsageSnapshot =
        UsageSnapshot(feature = feature, contagem = count(feature), limite = limite)

    /**
     * Verifica de forma OTIMISTA (so UX) se ainda ha cota local. NUNCA use isto como gate de
     * negocio — confie no servidor (`assertWithinQuota` no admin-api).
     */
    suspend fun hasLocalQuota(feature: String, limite: Int): Boolean =
        limite < 0 || count(feature) < limite
}
