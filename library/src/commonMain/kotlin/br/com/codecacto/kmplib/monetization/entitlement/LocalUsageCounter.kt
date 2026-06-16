package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.core.prefs.AppPreferences

/**
 * Contador de uso **local** (via [AppPreferences]) por feature.
 *
 * **ATENCAO:** isto e somente UX otimista (ex.: atualizar o "X de Y" imediatamente apos uma acao
 * antes do servidor responder). **NUNCA** e gate de negocio — o enforcement de quota e sempre
 * server-side no admin-api. Um valor local pode ser limpo/manipulado pelo usuario e jamais deve
 * liberar ou bloquear um recurso.
 *
 * As chaves sao isoladas por [projectSlug] para coexistencia segura entre projetos.
 */
class LocalUsageCounter(
    private val prefs: AppPreferences,
    private val projectSlug: String,
) {
    private fun key(feature: String): String = "usage_${projectSlug}_$feature"

    /** Incrementa o contador local da feature e retorna o novo valor. */
    suspend fun increment(feature: String, by: Long = 1L): Long {
        val next = (count(feature) + by).coerceAtLeast(0L)
        prefs.setLong(key(feature), next)
        return next
    }

    /** Contagem local atual da feature. */
    suspend fun count(feature: String): Long = prefs.getLong(key(feature), 0L)

    /** Zera o contador local da feature (ex.: ao virar a janela de quota). */
    suspend fun reset(feature: String) {
        prefs.setLong(key(feature), 0L)
    }
}
