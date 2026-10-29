package br.com.codecacto.kmplib.platform

import kotlinx.serialization.Serializable

/**
 * **Regra do pedido de avaliação** (2.335.0) — o que o Nexus ("Avaliação do app") controla por projeto,
 * servida por `GET /public/review-config?project=&platform=` do apps-api. Sem linha cadastrada o
 * servidor devolve os defaults abaixo, os mesmos que a lib usa sem rede e sem cache.
 *
 * Semântica (aplicada por [AppReviewManager], função pura [shouldPromptReview]): o pedido aparece
 * quando [enabled] ∧ conclusões ≥ [triggerCount] ∧ não concluído ∧ exibições < [maxPrompts] ∧ (nunca
 * exibido ∨ última exibição há ≥ [remindAfterDays] dias). "Sim" ou "Não" CONCLUI (nunca mais pede);
 * fechar sem responder volta depois de [remindAfterDays].
 *
 * @property enabled liga/desliga o pedido automático (o "Avaliar o app" do menu não depende disto).
 * @property triggerCount conclusões de fluxo relevantes antes do primeiro pedido (≥ 1).
 * @property remindAfterDays dias entre um pedido fechado sem resposta e o próximo (≥ 0).
 * @property maxPrompts teto de exibições automáticas por instalação (≥ 1).
 */
@Serializable
data class ReviewPromptConfig(
    val enabled: Boolean = true,
    val triggerCount: Int = 3,
    val remindAfterDays: Int = 30,
    val maxPrompts: Int = 3,
) {
    /** Os mesmos valores dentro das faixas válidas (servidor que mande 0 ou negativo não trava a regra). */
    fun sanitized(): ReviewPromptConfig = copy(
        triggerCount = triggerCount.coerceIn(1, 1_000),
        remindAfterDays = remindAfterDays.coerceIn(0, 3_650),
        maxPrompts = maxPrompts.coerceIn(1, 100),
    )

    companion object {
        /** Os defaults do contrato: `{true, 3, 30, 3}`. */
        val DEFAULT: ReviewPromptConfig = ReviewPromptConfig()
    }
}

/** A config da avaliação guardada no aparelho e quando foi lida do servidor. */
data class CachedReviewConfig(val config: ReviewPromptConfig, val fetchedAtMillis: Long)

/**
 * De onde vem a [ReviewPromptConfig] remota. A lib traz `RestReviewConfigSource` (apps-api, artefato
 * `kmplib-central`); teste passa um lambda.
 */
fun interface ReviewConfigSource {
    /** Lê do servidor. `null` = não conseguiu (sem rede, 5xx…) — o manager mantém a última conhecida. */
    suspend fun fetch(): ReviewPromptConfig?
}

/** Como terminou um pedido de avaliação exibido. */
enum class ReviewPromptOutcome {
    /** "Sim" → loja. Conclui. */
    POSITIVE,

    /** "Não" → formulário. Conclui. */
    NEGATIVE,

    /** Fechou sem responder. Volta depois de `remindAfterDays`. */
    DISMISSED,
}

private const val DAY_MILLIS: Long = 24L * 60 * 60 * 1000

/**
 * A regra do contrato, pura (testada em commonTest).
 *
 * @param lastPromptAtMillis instante da última exibição automática (0 = nunca).
 */
fun shouldPromptReview(
    config: ReviewPromptConfig,
    completions: Int,
    concluded: Boolean,
    promptCount: Int,
    lastPromptAtMillis: Long,
    nowMillis: Long,
): Boolean {
    val c = config.sanitized()
    if (!c.enabled || concluded) return false
    if (completions < c.triggerCount) return false
    if (promptCount >= c.maxPrompts) return false
    if (promptCount <= 0 || lastPromptAtMillis <= 0L) return true
    return nowMillis - lastPromptAtMillis >= c.remindAfterDays * DAY_MILLIS
}
